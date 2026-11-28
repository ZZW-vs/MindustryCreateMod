package zzw.content.units.flame.unit;

import arc.util.Time;
import mindustry.gen.Unit;
import zzw.content.units.ZEntityRegister;
import zzw.content.units.anticheat.EndCheatVars;
import zzw.content.units.anticheat.EndHealthLedger;
import zzw.content.units.entities.EndGroundUnit;
import zzw.content.units.flame.FlamePal;

/**
 * 消沉单位实体 (移植自 flame.unit.DespondencyUnit)。
 *
 * <p>★ 血量体系 (FO 原版 + End 台账融合):</p>
 * <ul>
 *   <li>生命值 17,500,000 / 护甲 200 按 FO 原值;</li>
 *   <li>单次受击对"显示血量"的扣减上限为 {@code maxHealth / 220}
 *       (FO 原版 {@code amount = min(amount, type.health / 220)}), 因此不会被任何
 *       单发高伤子弹秒杀 —— 这是"一打就死"的直接修复;</li>
 *   <li>真实血量走 {@link EndHealthLedger} 哈希台账, 按 End 防作弊的
 *       单次上限 / 抗性 / 伤害曲线扣减;</li>
 *   <li>台账耗尽才真死; 台账未耗尽时按 End 体系复活 (最多 10 次)。</li>
 * </ul>
 *
 * <p>★ 与 FO 的差异: FO 使用 {@code invFrames + lastDamage} 的 "只吃更大伤害"
 * 规则并强制 {@code elevation = 1f}; 本项目改用 End 的无敌帧槽位体系,
 * 但保留 FO 的单次伤害硬上限, 并补上 elevation 以修正悬浮渲染。</p>
 */
public class DespondencyUnit extends EndGroundUnit{
    /** FO 原版单次伤害上限系数: 每次最多扣 {@code health / 220}。 */
    private static final float HIT_CAP_DIVISOR = 220f;

    public static DespondencyUnit create(){
        return new DespondencyUnit();
    }

    @Override
    public int classId(){
        return ZEntityRegister.classId(DespondencyUnit.class);
    }

    /**
     * 单次伤害硬上限 (FO 原版机制)。
     *
     * <p>把入参先钳到 {@code maxHealth / 220}, 再交给 End 防作弊扣血:
     * 既保留 End 的台账/抗性/曲线, 又保证任何单发子弹都不可能直接清空血条。</p>
     */
    @Override
    public void damage(float amount){
        if(Float.isNaN(amount) || Float.isInfinite(amount) || amount <= 0f) return;

        float cap = maxHealth / HIT_CAP_DIVISOR;
        if(cap > 0f) amount = Math.min(amount, cap);

        super.damage(amount);
    }

    @Override
    public void update(){
        super.update();

        // FO 原版: trueHealth > 0 时保持悬浮 (elevation = 1), 台账耗尽才落地。
        float ledger = EndHealthLedger.get(this, health);
        elevation = ledger > 0f ? 1f : 0f;

        // ★ 移速钳制: 悬浮状态下 LegsUnit 的 speed() 会随 elevation 放大,
        //   导致实际移速高于 FO 原值。这里把速度硬钳回 UnitType.speed。
        if(vel.len() > type.speed){
            vel.setLength(type.speed);
        }
    }

    /** 消沉始终视作地面单位 (可被地面武器命中, 不吃"空中目标"过滤)。 */
    @Override
    public boolean isGrounded(){
        return true;
    }

    @Override
    public boolean isFlying(){
        return false;
    }

    /** 消沉是巨型步行单位，不参与"可被打断"的飞行判定。 */
    @Override
    public boolean canDrown(){
        return false;
    }
}
