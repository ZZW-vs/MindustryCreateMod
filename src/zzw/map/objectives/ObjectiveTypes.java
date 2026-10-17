package zzw.map.objectives;

import arc.func.*;
import arc.struct.*;
import arc.util.serialization.JsonValue;
import mindustry.game.*;
import mindustry.io.JsonIO;
import mindustry.type.*;
import zzw.map.cinematic.StoryNode;

/**
 * 目标类型注册表 (替代 PU132 ObjectiveModel 的 JSON 构造部分)。
 *
 * <p>PU132 用 Rhino 脚本 + 剧情编辑器来实例化目标; 本移植版改为:
 * 地图标签 (map tags) 里的 JSON 声明 → 按类型名查这里的工厂 → 构造 Objective 实例。
 * Java 代码也可以随时 {@link #register} 新类型 (如带自定义 completer 的 {@link CustomObj})。</p>
 *
 * <p>地图标签 JSON 格式 (map.tags / state.rules.tags 里的 "create-nodes"):
 * <pre>[{
 *   name: "intro",
 *   objectives: [
 *     {type: "resourceAmount", name: "gather-copper", items: [[copper, 200]]},
 *     {type: "unitPos", name: "defend", count: 3, pos: [100, 200], radius: 120}
 *   ],
 *   children: [...]
 * }]</pre></p>
 *
 * @author 移植: zzw
 */
public class ObjectiveTypes{
    private static final ObjectMap<String, Func2<StoryNode, JsonValue, Objective>> factories = new ObjectMap<>();

    private ObjectiveTypes(){}

    /** 注册一个目标类型; factory 收到 (所属节点, JSON 声明) 并返回目标实例。 */
    public static void register(String type, Func2<StoryNode, JsonValue, Objective> factory){
        factories.put(type, factory);
    }

    /** 从 JSON 数组读取目标列表并挂到节点上。 */
    public static void readObjectives(StoryNode node, JsonValue array){
        if(array == null) return;
        for(JsonValue value = array.child; value != null; value = value.next){
            String type = value.getString("type", null);
            if(type == null) throw new IllegalArgumentException("Objective missing 'type' field");

            var factory = factories.get(type);
            if(factory == null) throw new IllegalArgumentException("No objective type registered: '" + type + "'");

            node.objectives.add(factory.get(node, value));
        }
    }

    // ===== 内置 JSON 工厂 =====

    /** {@link ResourceAmountObj}: items (ItemStack[]), team (队伍 id, 默认玩家队 0)。 */
    public static void registerResourceAmount(){
        register("resourceAmount", (node, json) -> {
            ResourceAmountObj obj = new ResourceAmountObj(node, json.getString("name", "resource"), o -> {});
            obj.items = json.has("items")
                ? JsonIO.json.readValue(ItemStack[].class, json.get("items"))
                : new ItemStack[0];
            obj.team = Team.get(json.getInt("team", 0));
            return obj;
        });
    }

    /** {@link UnitPosObj}: count / continuous / team / pos([x,y]) / radius。 */
    public static void registerUnitPos(){
        register("unitPos", (node, json) -> {
            UnitPosObj obj = new UnitPosObj(node, json.getString("name", "unitPos"), o -> {});
            obj.count = json.getInt("count", 1);
            obj.continuous = json.getBoolean("continuous", false);
            obj.team = Team.get(json.getInt("team", 0));
            JsonValue pos = json.get("pos");
            if(pos != null && pos.size >= 2) obj.pos.set(pos.getFloat(0), pos.getFloat(1));
            obj.radius = json.getFloat("radius", obj.radius);
            return obj;
        });
    }
}
