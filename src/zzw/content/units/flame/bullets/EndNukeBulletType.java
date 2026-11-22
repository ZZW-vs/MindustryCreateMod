package zzw.content.units.flame.bullets;

import arc.Core;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Fires;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.world.blocks.defense.Wall;
import zzw.content.units.flame.FlameBatches;
import zzw.content.units.flame.FlamePal;
import zzw.content.units.flame.FlameSounds;
import zzw.content.units.flame.FlameUtils;
import zzw.content.units.flame.effects.FlameFX;
import zzw.content.units.flame.effects.SpecialDeathEffects;
import zzw.content.units.flame.unit.empathy.EmpathyDamage;

/**
 * 消沉裂变核弹 (移植自 flame.bullets.EndNukeBulletType)。
 *
 * <p>沿 480 半径做 360 条射线遮挡计算: 地形/墙体阻挡爆炸范围，
 * 命中的单位与建筑按遮挡比例结算伤害并触发核爆视觉。近处目标被
 * VaporizeBatch 气化，外围建筑由 DevastationBatch 拆成残骸。</p>
 */
public class EndNukeBulletType extends BasicBulletType{
    public EndNukeBulletType(){
        super(17f, 50000f, "missile-large");
        backColor = trailColor = hitColor = FlamePal.red;
        frontColor = FlamePal.red.cpy().mul(2f);

        shrinkY = 0f;
        width = 15f;
        height = 34f;

        trailLength = 5;
        trailWidth = 5f;

        lifetime = 60f;

        despawnHit = true;
        collidesTiles = false;
        scaleLife = true;

        hitEffect = Fx.none;
        despawnEffect = Fx.none;

        shootEffect = FlameFX.desNukeShoot;
        smokeEffect = Fx.none;
    }

    @Override
    public void hit(Bullet b, float x, float y){
        super.hit(b, x, y);
        float bx = b.x, by = b.y;
        Team team = b.team;

        int sid1 = FlameSounds.desNukeHit.at(bx, by, 1f, 2f);
        Core.audio.protect(sid1, true);
        float fall = Mathf.pow(Mathf.clamp(1f - FlameSounds.desNukeHit.calcFalloff(bx, by) * 1.1f), 1.5f);
        int sid2 = FlameSounds.desNukeHitFar.play(fall * 2f, 1f, FlameSounds.desNukeHit.calcPan(bx, by));
        Core.audio.protect(sid2, true);

        float[] arr = new float[360 * 3];
        FlameUtils.rayCastCircle(b.x, b.y, 480f, t -> (t.block().isStatic() || t.block() instanceof Wall) && !Mathf.within(b.x, b.y, t.worldx(), t.worldy(), 150f), t -> {
            float dst = 1f - Mathf.clamp(Mathf.dst(bx, by, t.x * Vars.tilesize, t.y * Vars.tilesize) / 480f);
            if(Mathf.chance(Mathf.pow(dst, 2f) * 0.75f)) Fires.create(t);
        }, t -> {
            float nx = t.x * Vars.tilesize, ny = t.y * Vars.tilesize;
            float ang = Angles.angle(bx, by, nx, ny);

            FlameFX.desNukeShockSmoke.at(nx, ny, ang);
        }, bl -> {
            float d = 21000f + bl.maxHealth / 5f;

            EmpathyDamage.damageBuildingRaw(bl, d, true, () -> {
                SpecialDeathEffects eff = SpecialDeathEffects.get(bl.block);

                bl.block.destroySound.at(bl);

                if(eff.explosionEffect == Fx.none){
                    Fx.dynamicExplosion.at(bl.x, bl.y, (Vars.tilesize * bl.block.size) / 2f / 8f);
                }else{
                    eff.explosionEffect.at(bl.x, bl.y, bl.hitSize() / 2f);
                }

                float shake = bl.hitSize() / 3f;
                Effect.shake(shake, shake, bl);

                if(bl.block.createRubble && !bl.floor().solid && !bl.floor().isLiquid){
                    Effect.rubble(bl.x, bl.y, bl.block.size);
                }

                float healthBase = (bl.block.size * Vars.tilesize);
                FlameBatches.devasBatch.baseZ = Layer.block;
                FlameBatches.devasBatch.switchBatch(bl::draw, dev -> {
                    dev.lifetime = Mathf.random(1f, 2f) * 60f;
                    dev.health = (Math.min(dev.width, dev.height) / healthBase) * bl.maxHealth * 1.5f;
                    dev.team = team;
                    dev.explosion = eff.explosionEffect != Fx.none ? eff.explosionEffect : FlameFX.fragmentExplosion;
                    dev.collides = true;
                    dev.contagiousChance = 0.85f;
                    dev.slowDownAmount = 0.5f;

                    float dx = dev.x - x;
                    float dy = dev.y - y;
                    float len = Mathf.sqrt(dx * dx + dy * dy);
                    float force = 1f / (1f + (len - 150f) / 500f);

                    Vec2 v = Tmp.v3.set(dx, dy).nor().setLength(force * 18f);
                    if(!v.isNaN()){
                        dev.vx = v.x;
                        dev.vy = v.y;
                        dev.vr = Mathf.range(25f * force);
                    }
                });
            });
        }, arr);

        FlameUtils.scanEnemies(b.team, b.x, b.y, 480f, true, true, t -> {
            if(t instanceof Unit u){
                float damageScl = FlameUtils.inRayCastCircle(bx, by, arr, u);

                if(damageScl > 0){
                    Tmp.v2.trns(Angles.angle(bx, by, u.x, u.y), (16f + 5f / u.mass()) * damageScl);
                    u.vel.add(Tmp.v2);

                    EmpathyDamage.damageUnit(u, (u.maxHealth / 10f + 10000f) * damageScl, true, () -> {
                        FlameBatches.vaporBatch.discon = null;
                        FlameBatches.vaporBatch.switchBatch(u::draw, null, (d, w) -> {
                            float with = FlameUtils.inRayCastCircle(bx, by, arr, d);
                            if(with > 0.5f){
                                d.disintegrating = true;
                                float dx = d.x - bx, dy = d.y - by;
                                float len = Mathf.sqrt(dx * dx + dy * dy);
                                float force = (6f / (1f + len / 90f) + (len / 480f) * 1.01f);

                                Vec2 v = Tmp.v1.set(dx, dy).nor().setLength(force * Mathf.random(0.9f, 1f));

                                d.lifetime = Mathf.random(60f, 90f) * Mathf.lerp(1f, 0.5f, Mathf.clamp(len / 480f));
                                d.drag = -0.015f;

                                d.vx = v.x;
                                d.vy = v.y;
                                d.vr = Mathf.range((force / 3f) * 5f);
                                d.zOverride = Layer.flyingUnit;
                            }
                        });

                        FlameFX.desNukeVaporize.at(u.x, u.y, u.angleTo(bx, by) + 180f, u.hitSize / 2f);
                    });
                }
            }else if(t instanceof Building bl){
                float damageScl = FlameUtils.inRayCastCircle(bx, by, arr, bl);
                if(damageScl > 0){
                    Runnable death = t.within(bx, by, 150f + bl.hitSize() / 2f) ? () -> {
                        FlameBatches.vaporBatch.discon = null;
                        FlameBatches.vaporBatch.switchBatch(bl::draw, null, (d, w) -> {
                            d.disintegrating = true;
                            float dx = d.x - bx, dy = d.y - by;
                            float len = Mathf.sqrt(dx * dx + dy * dy);
                            float force = (3f / (1f + len / 50f) + (len / 150f) * 0.9f);

                            Vec2 v = Tmp.v1.set(dx, dy).nor().setLength(force * Mathf.random(0.9f, 1f));

                            d.lifetime = Mathf.random(60f, 90f) * Mathf.lerp(1f, 0.5f, Mathf.clamp(len / 150f));
                            d.drag = -0.03f;

                            d.vx = v.x;
                            d.vy = v.y;
                            d.vr = Mathf.range((force / 3f) * 5f);
                            d.zOverride = Layer.turret + 1f;
                        });
                        FlameFX.desNukeVaporize.at(bl.x, bl.y, bl.angleTo(bx, by) + 180f, bl.hitSize() / 2f);
                    } : () -> {
                        SpecialDeathEffects eff = SpecialDeathEffects.get(bl.block);

                        bl.block.destroySound.at(bl);

                        if(eff.explosionEffect == Fx.none){
                            Fx.dynamicExplosion.at(bl.x, bl.y, (Vars.tilesize * bl.block.size) / 2f / 8f);
                        }else{
                            eff.explosionEffect.at(bl.x, bl.y, bl.hitSize() / 2f);
                        }

                        float shake = bl.hitSize() / 3f;
                        Effect.shake(shake, shake, bl);

                        if(bl.block.createRubble && !bl.floor().solid && !bl.floor().isLiquid){
                            Effect.rubble(bl.x, bl.y, bl.block.size);
                        }

                        float healthBase = (bl.block.size * Vars.tilesize);
                        FlameBatches.devasBatch.baseZ = Layer.block;
                        FlameBatches.devasBatch.switchBatch(bl::draw, dev -> {
                            dev.lifetime = Mathf.random(1f, 2f) * 60f;
                            dev.health = (Math.min(dev.width, dev.height) / healthBase) * bl.maxHealth * 1.5f;
                            dev.team = team;
                            dev.explosion = eff.explosionEffect != Fx.none ? eff.explosionEffect : FlameFX.fragmentExplosion;
                            dev.collides = true;
                            dev.contagiousChance = 0.85f;
                            dev.slowDownAmount = 0.5f;

                            float dx = dev.x - x;
                            float dy = dev.y - y;
                            float len = Mathf.sqrt(dx * dx + dy * dy);
                            float force = 1f / (1f + (len - 150f) / 500f);

                            Vec2 v = Tmp.v3.set(dx, dy).nor().setLength(force * 15f);
                            if(!v.isNaN()){
                                dev.vx = v.x;
                                dev.vy = v.y;
                                dev.vr = Mathf.range(25f * force);
                            }
                        });
                    };

                    EmpathyDamage.damageBuildingRaw(bl, (bl.maxHealth / 10f + 10000f) * damageScl, true, death);
                }
            }
        });

        Effect.shake(60f, 120f, b.x, b.y);
        FlameFX.desNukeShockwave.at(b.x, b.y, 480f);
        FlameFX.desNuke.at(b.x, b.y, 479f, arr);
    }
}
