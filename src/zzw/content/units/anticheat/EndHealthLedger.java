package zzw.content.units.anticheat;

import arc.struct.IntFloatMap;
import mindustry.gen.Unit;

/**
 * End 系列内部血量台账 (哈希血量)。
 *
 * <p>把单位血量额外存放在模组自己的 {@link IntFloatMap} 里, 以实体 id 为键。
 * 即使外部代码/作弊直接把 Unit.health、lastHealth、trueHealth 等字段清零,
 * 只要这里的内部血量仍大于 0, 死亡判定就会被拒绝并把字段回溯到台账值。</p>
 *
 * <p>性能: 只在初始化/受伤/治疗/死亡判定这些事件点读写哈希表,
 * 正常帧的 update 仍然使用实体上的本地缓存字段, 不做每帧查表或回溯。</p>
 */
public class EndHealthLedger {

    private static final IntFloatMap health = new IntFloatMap();

    private EndHealthLedger() {}

    /** 登记/覆盖内部血量 */
    public static void put(Unit unit, float value) {
        if (unit == null) return;
        health.put(unit.id, value);
    }

    /** 读取内部血量; 未登记时返回回退值 (通常是实体上的本地缓存) */
    public static float get(Unit unit, float fallback) {
        if (unit == null) return fallback;
        return health.containsKey(unit.id) ? health.get(unit.id) : fallback;
    }

    /** 是否仍有内部血量记录 */
    public static boolean has(Unit unit) {
        return unit != null && health.containsKey(unit.id);
    }

    /** 真实死亡/销毁后注销, 避免哈希表泄漏 */
    public static void remove(Unit unit) {
        // Arc 的 IntFloatMap 只提供 remove(key, value): 取当前值再按值删除
        if (unit != null && health.containsKey(unit.id)) {
            health.remove(unit.id, health.get(unit.id));
        }
    }

    /** 世界重置时清空 */
    public static void clear() {
        health.clear();
    }
}
