package zzw.content.blocks.turrets;

import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import zzw.content.Z_Bullets.BeamBulletType;
import zzw.content.Z_Bullets.ShieldBulletType;
import zzw.content.units.bullets.AcceleratingLaserBulletType;
import zzw.content.units.bullets.EndCutterLaserBulletType;
import zzw.content.units.bullets.PointBlastLaserBulletType;

/**
 * 炮台射程统一校准工具。
 *
 * <p>Mindustry 中炮台的显示圈与索敌使用 {@code Turret.range}（外加弹药的
 * {@code rangeChange}），而子弹实际命中距离由以下三者之一决定：</p>
 * <ul>
 *     <li>普通弹：{@code speed * lifetime}（受 {@code drag} 衰减影响）</li>
 *     <li>激光弹：{@code length} / {@code maxLength}</li>
 *     <li>{@code scaleLife} 弹：由炮台按目标距离自动缩放，无需处理</li>
 * </ul>
 *
 * <p>本工具把三类距离统一到同一个口径，避免“圈内打不到、圈外反而能打到”。
 * 这是原版 {@code Turret.limitRange()} 的增强版：额外处理激光长度与 drag 衰减。</p>
 */
public final class TurretRangeUtil{
    /** 统一在射程末端预留的余量，和原版 limitRange 的固定余量保持一致。 */
    public static final float MARGIN = 10f;

    private TurretRangeUtil(){
    }

    /** 按倍率放大并校准射程。 */
    public static void scale(Turret turret, float multiplier){
        if(turret == null) return;
        set(turret, turret.range * multiplier);
    }

    /** 设置射程，并把该炮台所有弹药的实际上限同步到同一值。 */
    public static void set(Turret turret, float range){
        if(turret == null) return;
        turret.range = range;

        if(turret instanceof ItemTurret itemTurret){
            for(BulletType type : itemTurret.ammoTypes.values()){
                sync(type, range);
            }
        }else if(turret instanceof PowerTurret powerTurret){
            sync(powerTurret.shootType, range);
        }
    }

    /** 把单个子弹类型的实际可达距离同步到炮台射程。 */
    public static void sync(BulletType type, float range){
        if(type == null) return;

        // 弹药自带的射程延伸（如 banshee 的 terminum）继续保留，显示圈会同步变大
        float target = range + type.rangeChange + MARGIN;

        // 按目标距离缩放的子弹由炮台在发射时自动处理
        if(type.scaleLife) return;

        // 护盾弹：靠 drag 减速停驻，停驻点由 drag 决定（见 ShieldBulletType 的半径逻辑）
        if(type instanceof ShieldBulletType) return;

        if(type instanceof AcceleratingLaserBulletType laser){
            laser.maxLength = target;
            return;
        }
        if(type instanceof EndCutterLaserBulletType laser){
            laser.maxLength = target;
            return;
        }
        if(type instanceof PointBlastLaserBulletType laser){
            laser.length = target;
            return;
        }
        if(type instanceof BeamBulletType beam){
            beam.length = target;
            beam.range = target;
            return;
        }
        if(type instanceof LaserBulletType laser){
            laser.length = target;
            return;
        }
        if(type instanceof ContinuousBulletType continuous){
            continuous.length = target;
            return;
        }

        // 瞬发/无速度弹（闪电、即时命中）直接用炮台 range 索敌
        if(type.speed <= 0.001f) return;

        if(type.drag > 0.0001f){
            // 带阻尼时距离收敛于 speed/drag，先保证能够到目标距离，再反算寿命
            if(type.speed / type.drag < target * 1.25f){
                type.drag = type.speed / (target * 1.6f);
            }
            type.lifetime = (float)(Math.log(1f - target * type.drag / type.speed) / Math.log(1f - type.drag));
        }else{
            type.lifetime = target / type.speed;
        }
    }

    /**
     * 估算子弹的实际最大飞行距离（用于单位面板/调试）。
     *
     * <p>无法从弹体判断的瞬发武器返回 {@code type.range}。</p>
     */
    public static float reach(BulletType type){
        if(type == null) return 0f;

        if(type instanceof AcceleratingLaserBulletType laser) return laser.maxLength;
        if(type instanceof EndCutterLaserBulletType laser) return laser.maxLength;
        if(type instanceof PointBlastLaserBulletType laser) return laser.length;
        if(type instanceof BeamBulletType beam) return beam.length;
        if(type instanceof LaserBulletType laser) return laser.length;
        if(type instanceof ContinuousBulletType continuous) return continuous.length;

        if(type.speed <= 0.001f) return type.range;
        if(type.drag > 0.0001f){
            return type.speed / type.drag * (1f - (float)Math.pow(1f - type.drag, type.lifetime));
        }
        return type.speed * type.lifetime;
    }
}
