package zzw.content.units.flame.unit;

import mindustry.gen.LegsUnit;
import zzw.content.units.ZEntityRegister;

/**
 * 消沉单位实体 (移植自 flame.unit.DespondencyUnit)。
 *
 * <p>★ 按 FO 原版机制:</p>
 * <ul>
 *   <li>单条血量 + 200 甲结算，不接入 End 的隐藏血量台账/防作弊伤害曲线；</li>
 *   <li>不复活（无 death deny、无 revivesUsed）；</li>
 *   <li>保留正常存档与正常碰撞，不穿墙。</li>
 * </ul>
 *
 * <p>★ 与 FO 的差异: FO 的 {@code serialize=false} 不存档，本项目保持默认序列化；
 * 不强制 {@code elevation=1f}，仅保留 UnitType 的 lowAltitude/hovering 配置。</p>
 */
public class DespondencyUnit extends LegsUnit implements zzw.content.Factionc{

    public static DespondencyUnit create(){
        return new DespondencyUnit();
    }

    /** 派系 (FactionMeta 按单位类型登记, End 单位统一为 end 派系)。 */
    @Override
    public zzw.content.Faction faction(){
        return zzw.content.FactionMeta.map(type);
    }

    @Override
    public int classId(){
        return ZEntityRegister.classId(DespondencyUnit.class);
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
