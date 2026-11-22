package zzw.content.units.flame.unit;

import zzw.content.units.ZEntityRegister;
import zzw.content.units.entities.EndGroundUnit;

/**
 * 消沉单位实体 (移植自 flame.unit.DespondencyUnit)。
 *
 * <p>★ 血量体系: 接入 End 的 {@code EndHealthLedger} 哈希台账与防作弊扣减
 * (单次伤害上限、抗性累积、伤害曲线)，生命值以台账为准。</p>
 *
 * <p>★ 复活: {@link #useDeathRevival()} 返回 false，台账耗尽即真死，
 * 不走死亡拒绝/复活路径。</p>
 *
 * <p>★ 与 FO 的差异: FO 的 {@code serialize=false} 不存档，本项目保持默认序列化；
 * 不强制 {@code elevation=1f}，仅保留 UnitType 的 lowAltitude/hovering 配置。</p>
 */
public class DespondencyUnit extends EndGroundUnit{

    public static DespondencyUnit create(){
        return new DespondencyUnit();
    }

    @Override
    public int classId(){
        return ZEntityRegister.classId(DespondencyUnit.class);
    }

    /** 使用台账扣血，但不启用死亡拒绝与复活。 */
    @Override
    protected boolean useDeathRevival(){
        return false;
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
