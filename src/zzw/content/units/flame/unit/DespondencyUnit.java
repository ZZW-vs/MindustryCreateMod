package zzw.content.units.flame.unit;

import mindustry.gen.Unit;
import zzw.content.units.ZEntityRegister;
import zzw.content.units.entities.EndGroundUnit;

/**
 * 消沉单位实体 (移植自 flame.unit.DespondencyUnit)。
 *
 * <p>★ 与 FO 原版的关键差异 (按项目约定):</p>
 * <ul>
 *   <li>FO 自带 {@code trueHealth} 台账与 {@code rawDamage} 防作弊；
 *       本项目改为继承 {@link EndGroundUnit}，复用 EndHealthLedger，
 *       避免两套台账叠加导致实际血量翻倍;</li>
 *   <li>FO {@code serialize=false} 不存档；本项目保持默认序列化，单位可正常存读档;</li>
 *   <li>FO {@code solidity()==null} 穿墙；本项目不覆写，保持正常碰撞;</li>
 *   <li>FO 在 updateValues 中强制 {@code elevation=1f}；这里不强制，
 *       仅保留 UnitType 的 lowAltitude/hovering 配置，避免与地面/甲板逻辑冲突。</li>
 * </ul>
 */
public class DespondencyUnit extends EndGroundUnit{

    public static DespondencyUnit create(){
        return new DespondencyUnit();
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
