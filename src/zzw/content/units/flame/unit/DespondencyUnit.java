package zzw.content.units.flame.unit;

import mindustry.gen.Unit;
import zzw.content.units.ZEntityRegister;
import zzw.content.units.anticheat.EndHealthLedger;
import zzw.content.units.entities.EndGroundUnit;

/**
 * 消沉单位实体 (移植自 flame.unit.DespondencyUnit)。
 *
 * <p>★ 形态: 与 FO 原版一致 —— 八足蜘蛛形态 + 低空飞行的空军单位
 * (UnitType 侧 flying/hovering/lowAltitude/legCount=8)。</p>
 *
 * <p>★ 血量体系: 生命值走 {@link EndHealthLedger} 哈希台账, 按 End 防作弊的
 * 单次上限 / 抗性 / 曲线扣减; 另外保留 FO 原版单次受击硬上限
 * {@code maxHealth / 220}, 避免被单发高伤子弹秒杀。</p>
 *
 * <p>★ 复活: 按用户要求不再复活 (reviveLimit = 0)。</p>
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
}
