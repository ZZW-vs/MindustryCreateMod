package zzw.content.units.flame.unit;

import arc.graphics.g2d.Draw;
import mindustry.Vars;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Weapon;
import mindustry.world.meta.Env;
import zzw.content.units.flame.FlameSounds;
import zzw.content.units.flame.bullets.EndCreepLaserBulletType;
import zzw.content.units.flame.bullets.EndNukeBulletType;
import zzw.content.units.flame.bullets.EndRailBulletType;
import zzw.content.units.flame.unit.weapons.EndAntiAirWeapon;
import zzw.content.units.flame.unit.weapons.EndDespondencyWeapon;
import zzw.content.units.flame.unit.weapons.EndLauncherWeapon;
import zzw.content.units.flame.unit.weapons.LaserWeapon;
import zzw.content.units.types.DesolationUnitType;

/**
 * 消沉 UnitType (移植自 flame.unit.DespondencyUnitType)。
 *
 * <p>继承 {@link DesolationUnitType} 以复用其 FO 腿阴影绘制
 * ({@code -leg-shadow} / {@code -leg-base-shadow})，再补上：
 * 主武器索引、FO 的 {@code drawShadow} 行为与完整武器组。</p>
 *
 * <p>★ 数值按 FO 原值落地: 1750 万血 / 200 甲 / 速度 2 / hitSize 217。
 * 按项目约定仍继承 EndGroundUnit，因此防作弊参数由
 * {@code antiCheatType} 控制，并在 Z_EndUnits 注册时配置。</p>
 */
public class DespondencyUnitType extends DesolationUnitType{
    public int mainWeaponIdx = -1;

    public DespondencyUnitType(String name){
        super(name);

        envDisabled = 0;
        envEnabled = Env.any;

        outlines = false;
        flying = true;
        drawCell = false;
        hovering = true;

        lowAltitude = true;
        canDrown = false;

        shadowElevation = 8f;
        groundLayer = Layer.darkness + 1f;

        health = 17500000f;
        speed = 2f;
        drag = 0.16f;
        armor = 200f;
        hitSize = 217f;
        rotateSpeed = 0.5f;

        constructor = DespondencyUnit::create;
        aiController = DespondencyAI::new;
        controller = u -> aiController.get();

        legForwardScl = 0.75f;
        legLength = 672f;
        legExtension = -48f;
        legCount = 8;
        legGroupSize = 2;
        legPairOffset = 1f;
        legMoveSpace = 0.33f;
        legBaseOffset = 51.25f / 4f;
        legLengthScl = 0.9f;

        legStraightness = 0.01f;
        legStraightLength = 4f;

        legSplashRange = 100f;
        legSplashDamage = 5400f;

        // ★ 与 FO 的 9999999 不同: 按项目性能约定收紧裁剪盒
        clipSize = 2000f;

        weapons.addAll(
                new EndAntiAirWeapon("create-" + this.name + "-anti-air"){{
                    x = 45.75f;
                    y = 15.5f;
                    mirror = true;

                    reload = 3f * 60f;

                    bullet = new BulletType(0f, 9000f);
                }},

                new LaserWeapon("create-" + this.name + "-laser"){{
                    x = 54.5f;
                    y = -15f;
                    mirror = true;

                    continuous = true;
                    rotate = true;
                    alternate = false;
                    reload = 8f * 60f;

                    rotationLimit = 190f;
                    rotateSpeed = 3.5f;

                    shootCone = 2f;
                    shootSound = FlameSounds.desLaser;
                    laserShootSound = FlameSounds.desLaserShoot;

                    bullet = new EndCreepLaserBulletType();
                }},

                new Weapon("create-" + this.name + "-railgun"){{
                    x = 63f;
                    y = -34f;
                    shootY = 12f;
                    mirror = true;

                    rotate = true;
                    alternate = true;
                    reload = 110f;

                    rotateSpeed = 1.6f;

                    shootCone = 2.5f;
                    shootSound = FlameSounds.desRailgun;

                    bullet = new EndRailBulletType();
                }},

                new Weapon("create-" + this.name + "-cannon"){{
                    x = 56f;
                    y = -62.75f;
                    shootY = 12f;
                    mirror = true;

                    rotate = true;
                    alternate = true;
                    reload = 8.5f * 60 * 2f;

                    rotateSpeed = 1.2f;

                    shootCone = 5f;
                    shootSound = FlameSounds.desNukeShoot;

                    bullet = new EndNukeBulletType();
                }},

                // 与 FO 相同: 主处决武器排在常规武器之后，保证手臂绘制层级
                new EndDespondencyWeapon("create-" + this.name + "-main"),

                new EndLauncherWeapon("create-" + this.name + "-missile")
        );
    }

    @Override
    public void init(){
        super.init();

        // 消沉免疫所有状态效果 (FO 原行为)
        immunities.addAll(Vars.content.statusEffects());

        int idx = 0;
        for(Weapon w : weapons){
            if(w instanceof EndDespondencyWeapon){
                mainWeaponIdx = idx;
                break;
            }
            idx++;
        }
    }

    /** FO 固定的投影位置: 不跟随 elevation 缩放，始终使用配置的阴影偏移。 */
    @Override
    public void drawShadow(Unit unit){
        float e = shadowElevation * (1f - unit.drownTime);
        Draw.color(Pal.shadow);

        Draw.rect(shadowRegion, unit.x + shadowTX * e, unit.y + shadowTY * e, unit.rotation - 90);
        Draw.color();
    }
}
