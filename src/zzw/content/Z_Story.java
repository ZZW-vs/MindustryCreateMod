package zzw.content;

import arc.Events;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.game.EventType.*;
import mindustry.io.JsonIO;
import zzw.map.cinematic.*;
import zzw.map.objectives.ObjectiveTypes;

import static mindustry.Vars.*;

/**
 * 剧情系统装配 (对应 PU132 的 unity.mod + ScriptedSector 装配层)。
 *
 * <p>职责:
 * <ul>
 *   <li>注册 JSON 目标类型工厂;</li>
 *   <li>客户端加载完成后初始化过场演出 (Cutscene) 与对话条 (Speeches);</li>
 *   <li>进入地图时若地图标签含 "create-nodes" 剧情声明 → 解析节点树并绑定 Cinematics;</li>
 *   <li>存/读档时把剧情进度持久化到 state.rules.tags["create-cinematic"]。</li>
 * </ul>
 *
 * <p>★ 剧情作者指南: 给自定义地图加标签 "create-nodes" (JSON 数组, 格式见
 * {@link ObjectiveTypes}), 再配合 {@link Cutscene}/{@link Speeches} 的 Java 演出代码
 * (在 Objective executor 里触发) 即可编排剧情。</p>
 *
 * @author 移植: zzw
 */
public class Z_Story{
    /** 存档里剧情进度写入 state.rules.tags 的键。 */
    public static final String saveKey = "create-cinematic";
    /** 地图标签里剧情声明的键。 */
    public static final String nodesKey = "create-nodes";

    public static void load(){
        // 注册 JSON 可声明的目标类型 (custom 目标由模组代码用 register 扩展)
        ObjectiveTypes.registerResourceAmount();
        ObjectiveTypes.registerUnitPos();

        // 演出/对话的 UI 初始化必须在客户端 UI 建好之后 (headless 服务器无 UI, 跳过)
        if(!headless){
            Events.on(ClientLoadEvent.class, e -> {
                Cutscene.init();
                Speeches.init();
            });
        }

        // 进入游戏 (切到 playing) 时: 地图带剧情声明则绑定剧情核心
        Events.on(StateChangeEvent.class, e -> {
            if(e.to == mindustry.core.GameState.State.playing){
                tryBind();
            }
        });

        // 存档: 把剧情进度写进 rules.tags (随地图存档持久化)
        Events.on(SaveWriteEvent.class, e -> {
            if(Cinematics.current != null && Cinematics.current.bound() && validStoryMap()){
                StringMap map = new StringMap();
                Cinematics.current.save(map);

                state.rules.tags.put(saveKey, JsonIO.json.toJson(map, StringMap.class, String.class));
            }
        });

        // 读档: rules.tags 已恢复, 进 playing 后 tryBind 里会读取并写回进度
    }

    /** 当前地图是否携带剧情声明 (绑定 + 有效性的依据)。 */
    public static boolean validStoryMap(){
        return state.rules.tags.containsKey(nodesKey)
            || (state.map != null && state.map.tags != null && state.map.tags.containsKey(nodesKey));
    }

    private static void tryBind(){
        if(Cinematics.current != null) Cinematics.current.detach();
        if(!validStoryMap()) return;

        String raw = state.rules.tags.get(nodesKey);
        if(raw == null && state.map != null && state.map.tags != null) raw = state.map.tags.get(nodesKey);
        if(raw == null) return;

        try{
            // 手工解析 (不能用反射反序列化: objectives 需要走 ObjectiveTypes 工厂构造)
            JsonValue data = new JsonReader().parse(raw);
            Seq<StoryNode> roots = new Seq<>();
            for(JsonValue value = data.child; value != null; value = value.next){
                roots.add(new StoryNode().read(null, value));
            }

            Cinematics cinematic = new Cinematics(Z_Story::validStoryMap);
            cinematic.nodes.addAll(roots);
            cinematic.bind();

            // 读档: 若有旧存档进度, 按 name 写回
            String saved = state.rules.tags.get(saveKey);
            if(saved != null){
                cinematic.load(JsonIO.json.fromJson(StringMap.class, String.class, saved));
                cinematic.nodes.each(StoryNode::init);
            }

            Log.info("[story] 剧情核心已绑定: @ 个根节点", roots.size);
        }catch(Throwable t){
            Log.err("[story] 剧情声明解析失败", t);
        }
    }
}
