package zzw.content.units.flame.entities;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.units.WeaponMount;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import zzw.content.units.flame.FlameSounds;
import zzw.content.units.flame.FlameUtils;
import zzw.content.units.flame.effects.FlameFX;
import zzw.content.units.flame.effects.SpecialDeathEffects;
import zzw.content.units.flame.unit.empathy.EmpathyDamage;

/**
 * 消沉处决长矛 (移植自 flame.entities.DesSpearEntity)。
 *
 * <p>长矛飞向目标，命中后把目标钉在原地并持续把主单位拖向命中点；
 * 命中瞬间用 {@link ShrapnelEntity} 从目标贴图炸出碎片。</p>
 *
 * <p>★ 与 FO 的差异: 贴图改为项目内的 {@code despondency-spear}；
 * 临时向量改用 {@link Tmp}，不再使用全局 {@code Utils.v/vv}。</p>
 */
public class DesSpearEntity extends DrawEntity{
    public static final float lifetime = 15f * 60f;
    public static final float offset = -40f;

    public boolean collided;
    public float cx, cy, cr;
    Unit unit;

    float time;
    public float rotation;
    public float size;
    float targetSize;

    float vx, vy;
    public float dx, dy;
    public float tx, ty;

    public boolean main = true;
    public float forceScl = 1f, damageScl = 1f;
    public DesSpearEntity last;
    public boolean crySound = true;
    boolean draw;

    static TextureRegion region(){
        return FlameUtils.atlasRegion("despondency-spear");
    }

    public static DesSpearEntity create(Unit unit, float x, float y, float rotation, boolean draw){
        TextureRegion region = region();
        float ts = Math.max(unit.hitSize / 2f, ((Math.min(unit.type.region.width, unit.type.region.height) * Draw.scl) / 2f) * 0.8f);
        float ts2 = Math.max(unit.hitSize / 2f, ((Math.min(unit.type.region.width, unit.type.region.height) * Draw.scl) / 2f)) * 1.6f;
        float scl = Math.max(Mathf.clamp(ts2 / (region.height * Draw.scl)), (ts / (region.height * Draw.scl)) / 3.5f) * (1f + ts / 70f);

        DesSpearEntity e = new DesSpearEntity();
        e.x = x;
        e.y = y;
        e.rotation = rotation;
        e.size = scl;
        e.targetSize = ts;
        e.unit = unit;
        e.draw = draw;

        e.add();

        return e;
    }

    @Override
    public void update(){
        time += Time.delta;

        if(!collided) rotation = Angles.moveToward(rotation, Angles.angle(x, y, unit.x + tx, unit.y + ty), 180f * Mathf.clamp(time / 120f));

        if(time > 20f && unit.isAdded() && !collided){
            float speed = (time - 20f) / 3f + Mathf.pow(Mathf.clamp((time - 20f) / 30f), 2f) * 12f + 20f;
            float lx = x, ly = y;

            Vec2 v = Tmp.v1.trns(rotation, speed * Time.delta);
            x += v.x;
            y += v.y;

            Vec2 col = FlameUtils.intersectCircle(lx, ly, x, y, unit.x, unit.y, targetSize);
            if(col != null){
                x = col.x;
                y = col.y;

                collided = true;
                cx = unit.x;
                cy = unit.y;
                cr = unit.rotation;

                vx = (v.x / (1f + targetSize / 15f)) * forceScl;
                vy = (v.y / (1f + targetSize / 15f)) * forceScl;
                if(last != null){
                    vx += last.vx / 3f;
                    vy += last.vy / 3f;
                    last.main = false;
                }

                dx = x - unit.x;
                dy = y - unit.y;

                SpecialDeathEffects eff = SpecialDeathEffects.get(unit.type);
                eff.hit(unit, col.x, col.y, rotation, 0.25f);
                FlameSounds.desSpearHit.at(col.x, col.y, Mathf.random(0.93f, 1.07f), 1.5f);
                if(eff.solid){
                    int count = 3 + (int)(unit.hitSize / 20f);
                    float forceScl = 1f;
                    float angRange = 80f;

                    for(int c = 0; c < 5; c++){
                        for(int i = 0; i < count; i++){
                            Vec2 vel = Tmp.v1.trns(rotation + Mathf.range(angRange), Mathf.random(unit.hitSize / 12f));
                            float len = vel.len();
                            float force = (len / 2f) * forceScl;
                            float vx2 = (vel.x / len) * force, vy2 = (vel.y / len) * force;

                            ShrapnelEntity shr = ShrapnelEntity.create(col.x + vel.x, col.y + vel.y, Mathf.random(360f), unit.hitSize / 32f, unit.type.region);
                            shr.lifetime = Mathf.random(25f, 45f) * (0.5f + (unit.hitSize / 90f) * 0.5f);

                            shr.vx = vx2 + vx / 8f;
                            shr.vy = vy2 + vy / 8f;
                            shr.vr = Mathf.range(4f) * force;
                            shr.z = Layer.flyingUnit;
                            shr.team = Team.derelict;
                            shr.collided.add(unit.id);
                            shr.explosion = eff.explosionEffect != Fx.none ? eff.explosionEffect : FlameFX.fragmentExplosion;

                            shr.damage = 0f;
                        }

                        forceScl *= 1.4f;
                        angRange *= 0.5f;
                    }
                    if(crySound){
                        FlameSounds.desSpearCry.at(col.x, col.y, 1f, 1f);
                    }
                }else{
                    FlameFX.desRailHit.at(col.x, col.y, rotation, (targetSize / 40f) * 0.3f);
                }

                EmpathyDamage.damageUnit(unit, (unit.maxHealth / 4f) * damageScl, false, null);
            }
        }
        if(collided){
            cx += vx;
            cy += vy;
            vx *= 1f - 0.075f * Time.delta;
            vy *= 1f - 0.075f * Time.delta;

            if(main){
                unit.x = cx;
                unit.y = cy;
                unit.rotation = cr;
            }

            x = unit.x + dx;
            y = unit.y + dy;

            if(main && !EmpathyDamage.containsExclude(unit.id)){
                for(WeaponMount m : unit.mounts){
                    m.reload = Mathf.lerpDelta(m.reload, m.weapon.reload, 0.4f);
                    m.shoot = false;
                }
            }
        }
        if(!unit.isAdded() && time < (lifetime - 15f)){
            time = lifetime - 15f;
        }

        if(time >= lifetime){
            remove();
        }
    }

    public boolean fading(){
        return time > (lifetime - 15f);
    }

    @Override
    public float clipSize(){
        return 1000f * size;
    }

    @Override
    protected void addGroup(){
        Groups.all.add(this);
        if(draw) Groups.draw.add(this);
    }

    @Override
    protected void removeGroup(){
        Groups.all.remove(this);
        if(draw) Groups.draw.remove(this);
    }

    @Override
    public void draw(){
        Draw.z(getZ());
        Draw.color();

        drawRaw();
    }

    public void drawRaw(){
        TextureRegion region = region();
        float fin = Mathf.clamp(time / 15f) * Mathf.clamp((lifetime - time) / 15f);
        Vec2 v = Tmp.v2.trns(rotation, offset * size).add(x, y);

        TextureRegion r = Tmp.tr1;
        r.set(region);
        r.setU2(Mathf.lerp(region.u, region.u2, fin));
        Draw.rect(r, v.x, v.y, r.width * Draw.scl * size, r.height * Draw.scl * size, rotation);
    }

    public float getZ(){
        UnitType type = unit.type;
        return (unit.elevation > 0.5f ? (type.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : type.groundLayer + Mathf.clamp(type.hitSize / 4000f, 0f, 0.01f)) - 0.01f;
    }
}
