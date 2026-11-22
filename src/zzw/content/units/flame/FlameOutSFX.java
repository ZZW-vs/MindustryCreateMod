package zzw.content.units.flame;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.entities.bullet.BulletType;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

/**
 * 单位/子弹 DPS 估算表 (裁自 flame.FlameOutSFX)。
 *
 * <p>消沉 AI 用 DPS + 血量决定"处决优先级"与"清杂阈值"。
 * FO 原版在 ContentInitEvent 时预计算；这里改为首次访问时惰性构建，
 * 避免依赖 FlameOut 的事件生命周期。数组按内容 id 索引。</p>
 */
public class FlameOutSFX{
    public static final FlameOutSFX inst = new FlameOutSFX();

    private float[] unitDps;
    private float[] bulletDps;
    private int builtUnits = -1, builtBullets = -1;

    public float getUnitDps(UnitType unit){
        ensure();
        if(unit == null || unit.id >= unitDps.length) return 0f;
        return unitDps[unit.id];
    }

    public float getBulletDps(BulletType bullet){
        ensure();
        if(bullet == null || bullet.id >= bulletDps.length) return 0f;
        return bulletDps[bullet.id];
    }

    private void ensure(){
        Seq<UnitType> units = Vars.content.units();
        Seq<BulletType> bullets = Vars.content.bullets();
        if(builtUnits != units.size || builtBullets != bullets.size){
            unitDps = new float[units.size];
            bulletDps = new float[bullets.size];
            builtUnits = units.size;
            builtBullets = bullets.size;
            for(BulletType b : bullets) updateBullet(b);
            for(UnitType u : units) updateUnit(u);
        }
    }

    private float updateUnit(UnitType unit){
        if(unitDps[unit.id] == 0f){
            unitDps[unit.id] = 0.000001f;
            float damage = 0f;
            for(Weapon w : unit.weapons){
                if(w.bullet == null) continue;
                int shots = w.shoot != null ? w.shoot.shots : 1;
                float d;
                if(!w.shootOnDeath && !w.bullet.killShooter){
                    d = (updateBullet(w.bullet) * shots * (w.continuous ? w.bullet.lifetime / 5f : 1f)) / Math.max(w.reload, 1f);
                }else{
                    d = updateBullet(w.bullet) * shots;
                }
                damage += d + (Mathf.pow(unit.hitSize, 0.75f) * unit.crashDamageMultiplier);
            }
            unitDps[unit.id] = damage;
        }
        return unitDps[unit.id];
    }

    private float updateBullet(BulletType type){
        if(bulletDps[type.id] == 0f){
            // 先占位, 防止 fragBullet/spawnUnit 自引用导致无限递归
            bulletDps[type.id] = Math.max(type.damage, 0.00001f);
            float damage = type.damage + type.splashDamage;

            if(type.fragBullet != null){
                damage += type.fragBullets * updateBullet(type.fragBullet);
            }
            if(type.lightning > 0){
                damage += type.lightning * Mathf.pow(type.lightningLength, 0.75f)
                        * Math.max(type.lightningType != null ? updateBullet(type.lightningType) : 0f, type.lightningDamage);
            }
            if(type.intervalBullet != null){
                damage += (updateBullet(type.intervalBullet) * type.intervalBullets) / Math.max(type.bulletInterval, 0.0001f);
            }
            if(type.spawnUnit != null){
                damage += updateUnit(type.spawnUnit);
            }
            if(type.despawnUnit != null){
                damage += updateUnit(type.despawnUnit) * type.despawnUnitCount;
            }
            bulletDps[type.id] = Math.max(0.00001f, damage);
        }
        return bulletDps[type.id];
    }
}
