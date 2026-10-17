package zzw.map.cinematic;

import arc.Events;
import arc.func.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.world.*;

import static mindustry.Vars.*;

/**
 * 剧情驱动核心 (PU132 Cinematics 移植, 裁剪版): 绑定当前地图, 每帧更新/绘制
 * 剧情节点树, 并提供 tag→对象 的标注系统 (目标系统用 tag 找到"剧情相关的建筑/单位位置")。
 *
 * <p>★ 与 PU132 的差异: PU 用自研 Triggers + ScriptedSector 绑定战役区块;
 * 本移植版用原版 {@code Events.run(Trigger.update/drawOver)} 驱动 (静态只注册一次),
 * 绑定条件改为"当前地图标签里含 create-nodes 剧情声明", 因此自定义地图即可演剧情。
 * 存档持久化同样写入 state.rules.tags。</p>
 *
 * @author PU132 原作 (GlennFolker), 移植: zzw
 */
@SuppressWarnings("unchecked")
public class Cinematics{
    /** 当前绑定的剧情核心 (全局唯一)。 */
    public static Cinematics current;

    public final Boolp validator;
    /** 剧情树的根节点 (无 parent)。 */
    public final Seq<StoryNode> nodes = new Seq<>();

    private boolean bound = false;
    private static boolean staticHooked;
    public final ObjectMap<String, Object> tagToObject = new ObjectMap<>();
    public final ObjectMap<Object, ObjectSet<String>> objectToTag = new ObjectMap<>();

    public Cinematics(Boolp validator){
        this.validator = validator;
    }

    /** 绑定: 接管 update/draw 事件并初始化全部节点 (事件监听静态只注册一次, 由 current 门禁)。 */
    public void bind(){
        if(current != null) throw new IllegalStateException("There is already a cinematic core ran!");
        current = this;

        bound = true;
        if(!staticHooked){
            staticHooked = true;
            Events.run(Trigger.update, () -> { if(current != null) current.update(); });
            Events.run(Trigger.drawOver, () -> { if(current != null) current.draw(); });
        }

        nodes.each(StoryNode::init);
    }

    public void detach(){
        if(current == this) current = null;

        bound = false;
        nodes.each(StoryNode::reset);
    }

    public void update(){
        if(bound && !valid()){
            detach();
            return;
        }
        if(!bound) return;

        nodes.each(StoryNode::update);
    }

    public void draw(){
        if(!bound) return;
        nodes.each(StoryNode::draw);
    }

    public boolean valid(){
        return validator.get();
    }

    public boolean bound(){
        return bound;
    }

    // ===== 存档持久化 (写入 state.rules.tags, 随地图存档走) =====

    public void save(StringMap map){
        map.put("nodes", JsonIO.json.toJson(saveNodes(), StringMap.class, String.class));
        map.put("object-tags", JsonIO.json.toJson(saveTags(), Seq.class, String.class));
    }

    public StringMap saveNodes(){
        StringMap map = new StringMap();
        for(var node : nodes){
            StringMap child = new StringMap();
            node.save(child);

            map.put(node.name, JsonIO.json.toJson(child, StringMap.class, String.class));
        }

        return map;
    }

    public Seq<String> saveTags(){
        Seq<String> tagArray = new Seq<>();
        StringMap valueMap = StringMap.of("type", "0", "value", null, "tags", "[]");

        for(var e : objectToTag.entries()){
            if(e.value.isEmpty()) continue;

            Object obj = e.key;
            if(obj instanceof Building build){
                valueMap.put("type", "1");
                valueMap.put("value", String.valueOf(build.pos()));
            }else if(obj instanceof Tile tile){
                valueMap.put("type", "2");
                valueMap.put("value", String.valueOf(tile.pos()));
            }else if(obj instanceof Posc pos){
                valueMap.put("type", "3");
                valueMap.put("value", JsonIO.json.toJson(new float[]{pos.getX(), pos.getY()}, float[].class, float.class));
            }else{
                throw new IllegalArgumentException("Un-serializable tagged object: " + obj);
            }

            valueMap.put("tags", JsonIO.json.toJson(e.value, ObjectSet.class, String.class));
            tagArray.add(JsonIO.json.toJson(valueMap, StringMap.class, String.class));
        }

        return tagArray;
    }

    /** 读档: 节点树已由调用方按地图声明重建, 这里只把完成状态/标签写回。 */
    public void load(StringMap map){
        loadNodes(JsonIO.json.fromJson(StringMap.class, String.class, map.get("nodes", "{}")));
        loadTags(JsonIO.json.fromJson(Seq.class, String.class, map.get("object-tags", "[]")));
    }

    public void loadNodes(StringMap map){
        for(var e : map.entries()){
            StoryNode node = nodes.find(n -> n.name.equals(e.key));
            if(node == null) continue; // 剧情声明已改, 忽略旧存档节点

            node.load(JsonIO.json.fromJson(StringMap.class, String.class, e.value));
        }
    }

    public void loadTags(Seq<String> array){
        objectToTag.clear();
        tagToObject.clear();
        for(var e : array){
            StringMap valueMap = JsonIO.json.fromJson(StringMap.class, String.class, e);
            ObjectSet<String> tagsArray = JsonIO.json.fromJson(ObjectSet.class, String.class, valueMap.get("tags", "[]"));

            int type = valueMap.getInt("type");
            Object obj = switch(type){
                case 1 -> world.build(valueMap.getInt("value"));
                case 2 -> world.tile(valueMap.getInt("value"));
                case 3 -> {
                    float[] pos = JsonIO.json.fromJson(float[].class, valueMap.get("value"));
                    yield Groups.all.find(ent -> ent instanceof Posc p && Mathf.equal(p.getX(), pos[0]) && Mathf.equal(p.getY(), pos[1]));
                }
                default -> throw new IllegalArgumentException("Unknown tagged object type: " + type);
            };

            for(var tag : tagsArray){
                tag(obj, tag);
            }
        }
    }

    // ===== 标注系统: 给建筑/地块/单位打 tag, 目标系统按 tag 查询 =====

    public void tag(Object object, String tag){
        if(object == null) return;
        if(byTag(tag) != null && byTag(tag) != object) throw new IllegalArgumentException("'" + tag + "' tag is already taken!");

        objectToTag.get(object, ObjectSet::new).add(tag);
        tagToObject.put(tag, object);
    }

    public void untag(Object object, String tag){
        if(tagToObject.get(tag) == object) tagToObject.remove(tag);

        var set = objectToTag.get(object);
        if(set != null) set.remove(tag);
    }

    public ObjectSet<String> toTag(Object object){
        return objectToTag.get(object);
    }

    public Object byTag(String tag){
        return tagToObject.get(tag);
    }

    public void clearTags(){
        objectToTag.clear();
        tagToObject.clear();
    }
}
