package zzw.content.units.flame.unit.empathy;

import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import zzw.content.units.anticheat.EndHealthLedger;
import zzw.content.units.entities.EndGroundUnit;
import zzw.content.units.entities.EndInvisibleUnit;

/**
 * FO {@code EmpathyDamage} 的轻量适配层 (仅保留消沉用到的绝对伤害语义)。
 *
 * <p>★ 为什么不整包移植 FO 的 EmpathyDamage:
 * 原类 941 行，包含共鸣 BOSS 的绝对血量台账、复活、排除列表与
 * {@code AbsoluteDamage} 管理器。本项目已有 {@link EndHealthLedger}，
 * 两套台账同时启用会让 End 单位血量翻倍。因此这里只保留消沉需要的:</p>
 * <ul>
 *   <li>{@link #isNaNInfinite(float)}：FO 用来过滤异常数值;</li>
 *   <li>{@link #damageUnit}：处决伤害 + 死亡回调;</li>
 *   <li>{@link #damageBuildingRaw}：建筑抹除;</li>
 *   <li>{@code exclude/containsExclude/removeExclude}：空实现
 *       (本项目没有 EmpathySpawner 的清理器)。</li>
 * </ul>
 *
 * <p>★ 对 End 自家单位的特殊处理:
 * FO 的绝对伤害会绕过一切防御，直接抹掉目标。为了不破坏本项目
 * "End 单位必须被多次打击才能击杀"的防作弊体系，当目标是
 * {@link EndGroundUnit} / {@link EndInvisibleUnit} 时改走普通
 * {@code damage()}，让台账/复活正常生效；其他目标才使用绝对伤害。</p>
 */
public class EmpathyDamage{

    public static boolean isNaNInfinite(float v){
        return Float.isNaN(v) || Float.isInfinite(v);
    }

    /** FO 的排除表在本项目不存在，返回 false 即可。 */
    public static boolean containsExclude(int id){
        return false;
    }

    public static void exclude(Unit unit){
        // no-op
    }

    /** 返回 true 表示该单位仍在排除表中 (FO 的 remove 流程会据此跳过真正移除)。 */
    public static boolean removeExclude(Unit unit){
        return false;
    }

    /**
     * 对单位造成"绝对伤害"。
     *
     * @param unit    目标
     * @param amount  伤害值
     * @param lethal  是否强制处决 (即便伤害不足也直接击杀)
     * @param onDeath 死亡后的回调 (碎片化/切割演出)
     */
    public static void damageUnit(Unit unit, float amount, boolean lethal, Runnable onDeath){
        if(unit == null || !unit.isAdded()) return;

        // End 自家单位: 交回项目防作弊体系，避免被处决直接抹掉
        if(unit instanceof EndGroundUnit || unit instanceof EndInvisibleUnit){
            unit.damage(amount);
            if(lethal && unit.isAdded()){
                unit.kill();
            }
            if(!unit.isAdded() && onDeath != null) onDeath.run();
            return;
        }

        float applied = Math.max(amount, 0f);
        if(lethal) applied = Math.max(applied, unit.health + 1f);

        unit.health -= applied;
        unit.hitTime = 1f;

        if(unit.health <= 0f || lethal){
            unit.kill();
            // 某些自定义单位会拒绝死亡: 处决语义下强制移除
            if(unit.isAdded()) unit.remove();
        }

        if(!unit.isAdded() && onDeath != null){
            onDeath.run();
        }
    }

    /**
     * 对建筑造成"绝对伤害"。
     *
     * @param building 目标建筑
     * @param amount   伤害值
     * @param lethal   是否强制摧毁
     * @param onDeath  摧毁后的回调
     */
    public static void damageBuildingRaw(Building building, float amount, boolean lethal, Runnable onDeath){
        if(building == null || building.dead) return;

        float applied = Math.max(amount, 0f);
        if(lethal) applied = Math.max(applied, building.health + 1f);

        building.health -= applied;
        building.hitTime = 1f;

        if(building.health <= 0f || lethal){
            building.kill();
        }
        if(building.dead && onDeath != null){
            onDeath.run();
        }
    }

    private EmpathyDamage(){
    }
}
