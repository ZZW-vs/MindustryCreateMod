package zzw.content.units.flame.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Intersector;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Liquids;
import mindustry.entities.Effect;
import mindustry.entities.Sized;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import zzw.content.units.flame.FlamePal;
import zzw.content.units.flame.FlameUtils;
import zzw.content.units.flame.effects.FlameFX;
import zzw.content.units.flame.effects.SpecialDeathEffects;
import zzw.content.units.flame.graphics.GraphicUtils;
import zzw.content.units.flame.unit.empathy.EmpathyDamage;
import zzw.content.units.flame.unit.weapons.LaserWeapon.LaserRange;

/**
 * 消沉主激光 (移植自 flame.bullets.EndCreepLaserBulletType)。
 *
 * <p>持续 5 秒的蠕行长激光: 长度逐步增长、对地面目标熔穿，末端每 10~18 帧
 * 触发一次 230 范围重击。飞行单位被末端扫中时会直接裂解。</p>
 */
public class EndCreepLaserBulletType extends ContinuousBulletType implements LaserRange{
    float creepTime = 140f;
    float baseLength = 300f;
    float width = 12f;

    Color[] colors = {FlamePal.red.cpy().a(0.5f), FlamePal.red, FlamePal.red.cpy().mul(2f), Color.white};

    /** ★ FO 为 static 共享表，这里改为 BulletType 成员，避免多激光互相覆盖。 */
    Seq<Building> buildings = new Seq<>();

    public EndCreepLaserBulletType(){
        super();

        damage = 9000f;
        length = 500 * 8f;
        hitColor = FlamePal.red;
        hitEffect = FlameFX.desCreepHit;
        shootEffect = Fx.none;
        smokeEffect = Fx.none;

        lifetime = 5f * 60f;
    }

    @Override
    protected float calculateRange(){
        return length / 2f;
    }

    @Override
    public void init(Bullet b){
        super.init(b);
        b.fdata = baseLength;
        CreepLaserData data = new CreepLaserData();

        Vec2 v = Tmp.v1.trns(b.rotation(), baseLength).add(b.x, b.y);
        FlameFX.desGroundHitMain.at(v.x, v.y, b.rotation());

        data.lastX = v.x;
        data.lastY = v.y;
        b.data = data;
    }

    @Override
    public void update(Bullet b){
        super.update(b);

        if(!(b.data instanceof CreepLaserData data)) return;

        float len = getLength(b.time);
        float grnd = getGroundScl(b);
        float scl = (1f + Mathf.clamp(1f - (b.time / 16f)) * 1.1f) * grnd;

        if(Mathf.chanceDelta(0.75f) && grnd > 0){
            Vec2 v = Tmp.v1.trns(b.rotation(), len).add(b.x, b.y).lerp(data.lastX, data.lastY, Mathf.random());

            Tile ground = Vars.world.tileWorld(v.x, v.y);
            if(ground != null){
                Floor floor = ground.floor();
                Color color = Tmp.c1.set(floor.mapColor).mul(1.2f);
                if(floor.isLiquid && floor.liquidDrop == Liquids.water){
                    color = Liquids.water.gasColor;
                }

                FlameFX.desGroundHit.at(v.x, v.y, scl, color);
            }
        }

        Vec2 v = Tmp.v1.trns(b.rotation(), len).add(b.x, b.y);

        if(scl > 0){
            float dst = Mathf.dst(b.x, b.y, data.lastX, data.lastY);
            int melt = Math.min((int)((len - dst) / 5f) + 1, 15);
            for(int i = 0; i < melt; i++){
                float f = i / (float)melt;

                Vec2 m = Tmp.v2.set(v).lerp(data.lastX, data.lastY, f).add(Tmp.v3.rnd(Mathf.random(5f * scl)));
                Tile tile = Vars.world.tileWorld(v.x, v.y);

                if(tile != null && !tile.floor().isLiquid){
                    FlameFX.desGroundMelt.at(m.x, m.y, Mathf.random(12f, 24f) * scl);
                }
            }
        }
        data.lastX = v.x;
        data.lastY = v.y;
        data.hitTime -= Time.delta;
    }

    float getGroundScl(Bullet b){
        return 1f - Mathf.clamp((b.time - creepTime / 1.4f) / (creepTime / 4f));
    }

    float getFlyingScl(Bullet b){
        return Mathf.clamp((b.time - creepTime / 1.7f) / (creepTime / 3f));
    }

    float getLength(float time){
        return baseLength / Math.max(1f - Interp.sineIn.apply(Mathf.clamp(time / creepTime)), baseLength / length);
    }

    @Override
    public float getLength(Bullet b){
        return getLength(b.time);
    }

    @Override
    public void applyDamage(Bullet b){
        if(!(b.data instanceof CreepLaserData data)) return;

        float len = getLength(b.time);
        Vec2 v = new Vec2().trns(b.rotation(), len).add(b.x, b.y), v2 = new Vec2().trns(b.rotation(), length + 100f).add(b.x, b.y);
        float vx = v.x, vy = v.y;
        float v2x = v2.x, v2y = v2.y;
        float bx = b.x, by = b.y;
        float ground = getGroundScl(b);
        float fly = getFlyingScl(b);
        float rot = b.rotation();

        FlameUtils.hitLaser(b.team, 4f, b.x, b.y, v.x, v.y, null, h -> false, (h, x, y) -> {
            boolean near = (data.hitTime <= 0f) && Mathf.within(x, y, vx, vy, 140f + (len - b.fdata));
            boolean groundHit = false;
            boolean hitt = false;

            if(h instanceof Unit u){
                boolean near2 = Intersector.distanceLinePoint(bx, by, vx, vy, u.x, u.y) < (u.hitSize / 2f) / 2.5f;
                float dam = u.isFlying() ? fly : ground;

                Runnable death = (u.isFlying() && near2) ? () -> {
                    SpecialDeathEffects eff = SpecialDeathEffects.get(u.type);
                    if(!eff.solid){
                        eff.cutAlt(u);
                        return;
                    }

                    // P1 观感回退: FO 这里用 CutBatch 沿激光切开单位, P3 恢复。
                    eff.deathUnit(u, x, y, rot);
                } : (u.isGrounded() ? () -> {
                    SpecialDeathEffects eff = SpecialDeathEffects.get(u.type);
                    eff.deathUnit(u, x, y, rot);
                } : null);

                if(dam > 0){
                    EmpathyDamage.damageUnit(u, (damage + u.maxHealth / 390f) * dam, true, death);
                    hitt = true;
                    if(u.isGrounded()) groundHit = true;
                }
            }else if(h instanceof Building bl){
                if(ground > 0){
                    EmpathyDamage.damageBuildingRaw(bl, (damage + bl.maxHealth / 390f) * ground, true, null);
                    hitt = true;
                    if((bl.health / bl.maxHealth) > 0.68f) groundHit = true;
                }
            }

            if(hitt) hit(b, x, y);

            if(near && groundHit){
                hitEnd(b, x, y);
                data.hitTime = Mathf.random(10f, 18f) * (0.3f + ground * 0.7f);
            }
        });

        b.fdata = len;
    }

    void hitEnd(Bullet b, float x, float y){
        float scl = Mathf.random(0.75f, 1.3f) * Interp.pow2InInverse.apply(getGroundScl(b));
        float range = 230f * scl;
        FlameFX.desCreepHeavyHit.at(x, y, b.rotation(), scl);

        Rect r = new Rect().setCentered(x, y, range * 2f);

        buildings.clear();
        Groups.unit.intersect(r.x, r.y, r.width, r.height, u -> {
            if(u.team != b.team && Mathf.within(x, y, u.x, u.y, range + u.hitSize / 2f) && u.checkTarget(false, true)){
                EmpathyDamage.damageUnit(u, 12000f + u.maxHealth / 50f, true, () -> {
                    SpecialDeathEffects eff = SpecialDeathEffects.get(u.type);
                    float rot = u.angleTo(x, y) + 180f;
                    eff.deathUnit(u, x, y, rot);
                });
            }
        });

        Team team = b.team;

        for(TeamData data : Vars.state.teams.present){
            if(data.team != b.team && data.buildingTree != null){
                data.buildingTree.intersect(r, bl -> {
                    if(Mathf.within(x, y, bl.x, bl.y, range + bl.hitSize() / 2f)){
                        buildings.add(bl);
                    }
                });
            }
        }

        for(Building bl : buildings){
            boolean lethal = Mathf.chance(0.3f);
            float d = lethal ? 12000f + bl.maxHealth / 20f : bl.health / 1.5f;
            EmpathyDamage.damageBuildingRaw(bl, d, lethal, () -> {
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

                // P1 观感回退: FO 这里用 DevastationBatch 拆分建筑, P3 恢复。
            });
        }
        buildings.clear();
    }

    @Override
    public void draw(Bullet b){
        Rand rand = new Rand(b.id);
        Rand rand2 = new Rand();

        float len = getLength(b.time);
        float fade = Mathf.clamp(b.time / 15f) * Mathf.clamp((b.lifetime - b.time) / (2.5f * 60));

        float base = baseLength;
        float tipHeight = (width / 2f) * (1f + ((len - base) / (length - base)) * 10f);
        float flare = Mathf.clamp(b.time / 80f);
        float w2 = 8f * fade;
        float sclen = (len - base) / (length - base);

        for(int i = 0; i < colors.length; i++){
            float f = ((float)(colors.length - i) / colors.length);
            float w = f * (width + Mathf.absin(Time.time + (i * 0.6f), 1.1f, width / 4)) * fade;

            Tmp.v2.trns(b.rotation(), len - tipHeight).add(b);
            Tmp.v1.trns(b.rotation(), tipHeight * 4f).add(Tmp.v2);

            Draw.color(colors[i]);

            if(flare < 1f){
                for(int j = 0; j < 4; j++){
                    float r = j * 90f + Time.time * 2.5f;
                    Drawf.tri(b.x, b.y, w2, (40f + w2 * 8f) * Interp.sine.apply(Mathf.slope(flare)), r);
                }
            }

            Fill.circle(b.x, b.y, w / 1.25f);
            Lines.stroke(w);
            Lines.line(b.x, b.y, Tmp.v2.x, Tmp.v2.y, false);
            for(int s : Mathf.signs){
                Tmp.v3.trns(b.rotation(), w * -0.7f, w * s);
                Fill.tri(Tmp.v2.x, Tmp.v2.y, Tmp.v1.x, Tmp.v1.y, Tmp.v2.x + Tmp.v3.x, Tmp.v2.y + Tmp.v3.y);
            }
            w2 *= 0.666f;
        }
        for(int i = 0; i < 80; i++){
            float dur = rand.random(8f, 12f);
            float time = (b.time + rand.random(dur)) / dur;
            float f = time % 1f;
            int seed = (int)(time) + b.id * 5231;
            rand2.setSeed(seed);

            float l = (len / (rand2.random(8f, 12f) * (1f + sclen * 2f))) * (f * 0.25f + 0.75f);
            float w = (width / 4f) * FlameUtils.biasSlope(0.3f, f) * fade;
            float off = ((len - l * 2f) * f) + l;
            Vec2 v = Tmp.v2.trns(b.rotation(), off, rand2.range(width * (1 - 1f / 4f) * fade - w) / 2f).add(b.x, b.y);

            Draw.color(rand2.chance(0.5f) ? Tmp.c2.set(FlamePal.red).lerp(Color.white, Mathf.pow(rand2.nextFloat(), 2f)) : Color.black);
            GraphicUtils.diamond(v.x, v.y, w, l, b.rotation());
        }

        Tmp.v2.trns(b.rotation(), b.fdata + tipHeight).add(b);
        Drawf.light(b.x, b.y, Tmp.v2.x, Tmp.v2.y, width * 2f, colors[0], 0.5f);

        Draw.reset();
    }

    static class CreepLaserData{
        float lastX, lastY;
        float hitTime;
    }
}
