package zzw.content.units.entities;

import mindustry.entities.EntityCollisions.SolidPred;

/**
 * desolation 专用实体: 陆地腿单位 + 低空飞行特性。
 *
 * <p>对照 FlameOut 的 {@code DespondencyUnit} (flameout-despondency):</p>
 * <ul>
 *   <li>{@link #solidity()} 返回 null → 不再与墙体/建筑发生碰撞, 可以直接跨过高墙
 *       (腿单位仍会正常摆腿, 视觉上就是"贴着地面低空掠过障碍");</li>
 *   <li>{@link #isGrounded()} 恒为 true → 引擎仍按地面单位处理, 地面武器照常命中,
 *       不会因为 flying=true 变成只能被对空武器攻击的空中目标。</li>
 * </ul>
 *
 * <p>其余行为 (防作弊伤害、死亡拒绝、腿渲染) 全部继承 {@link EndGroundUnit}。</p>
 */
public class DesolationUnit extends EndGroundUnit{

    /** 工厂方法 (UnitType.constructor 用)。 */
    public static DesolationUnit create(){
        return new DesolationUnit();
    }

    @Override
    public int classId(){
        return zzw.content.units.ZEntityRegister.classId(DesolationUnit.class);
    }

    @Override
    public boolean isGrounded(){
        return true;
    }

    @Override
    public SolidPred solidity(){
        return null;
    }
}
