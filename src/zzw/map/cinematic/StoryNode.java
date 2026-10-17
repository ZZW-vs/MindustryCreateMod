package zzw.map.cinematic;

import arc.struct.*;
import arc.util.serialization.*;
import arc.util.serialization.Json.*;
import mindustry.io.JsonIO;
import zzw.map.objectives.Objective;
import zzw.map.objectives.ObjectiveTypes;

/**
 * 剧情节点 (PU132 StoryNode 移植, 裁剪版)。
 *
 * <p>剧情以节点树组织: 节点持有若干 {@link Objective 目标}, 全部达成后节点完成,
 * 推进到子节点 (可分支)。整棵树由 {@link Cinematics} 驱动更新并持久化到存档。</p>
 *
 * <p>★ 与 PU132 的差异: 裁掉 Rhino dataScript、剧情编辑器 UI (position/elem) 与
 * JsonSerializable 编辑器序列化; 节点来源改为地图标签 JSON
 * (见 {@link ObjectiveTypes} 顶部格式说明), 存档持久化保留 StringMap 机制。</p>
 *
 * @author PU132 原作 (GlennFolker), 移植: zzw
 */
public class StoryNode{
    public StoryNode parent;
    public final Seq<StoryNode> children = new Seq<>(2);

    public String name = "node";

    public final Seq<Objective> objectives = new Seq<>(2);

    protected boolean initialized = false, completed = false;

    /** 从地图标签 JSON 声明构造 (递归 children)。 */
    public StoryNode read(Json json, JsonValue data){
        name = data.getString("name", name);
        objectives.clear();
        ObjectiveTypes.readObjectives(this, data.get("objectives"));

        children.clear();
        JsonValue childs = data.get("children");
        if(childs != null){
            for(JsonValue child = childs.child; child != null; child = child.next){
                StoryNode n = new StoryNode();
                n.parent = this;
                n.read(json, child);
                children.add(n);
            }
        }
        return this;
    }

    /** 当前演出上下文是否仍有效 (地图未退出/切换)。 */
    public boolean valid(){
        return Cinematics.current != null && Cinematics.current.valid();
    }

    public void init(){
        if(initialized) return;
        initialized = true;

        objectives.each(Objective::init);
    }

    public void reset(){
        initialized = false;
        completed = false;
        objectives.each(Objective::reset);
        children.each(StoryNode::reset);
    }

    /** 本节点是否全部完成 (空目标视为完成)。 */
    public boolean completed(){
        if(completed || (completed = objectives.isEmpty())) return true;

        completed = true;
        for(Objective o : objectives){
            if(!o.executed()) return completed = false;
        }

        return true;
    }

    public void update(){
        if(!initialized) init();

        if(completed()){
            children.each(StoryNode::update);
            return;
        }

        for(Objective o : objectives){
            if(o.executed()) continue;
            if(o.shouldUpdate()) o.update();
            if(o.qualified()) o.execute();
        }
    }

    public void draw(){
        if(completed()){
            children.each(StoryNode::draw);
            return;
        }

        for(Objective o : objectives) if(o.shouldDraw()) o.draw();
    }

    public void child(StoryNode other){
        if(children.contains(other)) return;
        if(other.parent != null) other.parent.children.remove(other);

        other.parent = this;
        children.add(other);
    }

    /** 存档: 把本节点 (含子孙) 的完成状态序列化到 map。 */
    public void save(StringMap map){
        map.put("name", name);
        map.put("completed", String.valueOf(completed));

        StringMap objMap = new StringMap();
        for(Objective obj : objectives){
            StringMap child = new StringMap();
            obj.save(child);

            objMap.put(obj.name, JsonIO.json.toJson(child, StringMap.class, String.class));
        }

        map.put("objectives", JsonIO.json.toJson(objMap, StringMap.class, String.class));

        StringMap childMap = new StringMap();
        for(StoryNode child : children){
            StringMap m = new StringMap();
            child.save(m);

            childMap.put(child.name, JsonIO.json.toJson(m, StringMap.class, String.class));
        }

        map.put("children", JsonIO.json.toJson(childMap, StringMap.class, String.class));
    }

    /** 读档: 按 name 对应把完成状态写回本节点 (含子孙); 声明已改的条目忽略。 */
    public void load(StringMap map){
        completed = map.getBool("completed");

        StringMap objMap = JsonIO.json.fromJson(StringMap.class, String.class, map.get("objectives", "{}"));
        for(ObjectMap.Entry<String, String> e : objMap.entries()){
            Objective obj = objectives.find(o -> o.name.equals(e.key));
            if(obj == null) continue;

            obj.load(JsonIO.json.fromJson(StringMap.class, String.class, e.value));
        }

        StringMap nodes = JsonIO.json.fromJson(StringMap.class, String.class, map.get("children", "{}"));
        for(ObjectMap.Entry<String, String> e : nodes.entries()){
            StoryNode node = children.find(n -> n.name.equals(e.key));
            if(node == null) continue;

            node.load(JsonIO.json.fromJson(StringMap.class, String.class, e.value));
        }
    }
}
