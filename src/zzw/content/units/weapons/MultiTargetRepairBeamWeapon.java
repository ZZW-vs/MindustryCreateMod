package zzw.content.units.weapons;

import mindustry.entities.Units;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.type.weapons.RepairBeamWeapon;

/**
 * 多目标维修光束 (seraphim 专用)。
 *
 * <p>原版 {@link RepairBeamWeapon} 的每个 mount 都独立寻找"最近的受伤友军",
 * 多臂单位的所有光束会同时锁到同一个目标, 与"多臂同时照顾多个目标"的定位不符。
 * 本类在选择目标时排除其它 mount 已经锁定的单位; 当可用目标不足时
 * 回退到最近的受伤友军, 保证只有一个伤员时全部光束仍能叠加治疗。</p>
 */
public class MultiTargetRepairBeamWeapon extends RepairBeamWeapon{
    /** 当前正在更新的 mount (findTarget 由 super.update 调用, 需要知道是哪一个) */
    private WeaponMount currentMount;

    public MultiTargetRepairBeamWeapon(String name){
        super(name);
    }

    @Override
    public void update(Unit unit, WeaponMount mount){
        currentMount = mount;
        super.update(unit, mount);
    }

    @Override
    protected Teamc findTarget(Unit unit, float x, float y, float range, boolean air, boolean ground){
        Teamc out = targetUnits ? Units.closest(unit.team, x, y, range,
                u -> u != unit && u.damaged() && !targetedByOther(unit, u)) : null;

        // 目标不足时允许重叠, 避免单个伤员只有一条光束生效
        if(out == null && targetUnits){
            out = Units.closest(unit.team, x, y, range, u -> u != unit && u.damaged());
        }

        if(out != null || !targetBuildings) return out;
        return Units.findAllyTile(unit.team, x, y, range, Building::damaged);
    }

    /** 该目标是否已被本单位的其它维修臂锁定 */
    protected boolean targetedByOther(Unit unit, Unit target){
        for(WeaponMount m : unit.mounts){
            if(m != currentMount && m.weapon instanceof RepairBeamWeapon && m.target == target) return true;
        }
        return false;
    }
}
