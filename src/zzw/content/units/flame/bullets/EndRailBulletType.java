package zzw.content.units.flame.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Sized;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import zzw.content.units.flame.FlamePal;
import zzw.content.units.flame.FlameSounds;
import zzw.content.units.flame.FlameUtils;
import zzw.content.units.flame.effects.FlameFX;
import zzw.content.units.flame.effects.SpecialDeathEffects;
import zzw.content.units.flame.entities.ShrapnelEntity;
import zzw.content.units.flame.unit.empathy.EmpathyDamage;

/**
 * 消沉轨道炮 (移植自 flame.bullets.EndRailBulletType)。
 *
 * <p>沿 2400 长度做一次瞬时贯穿判定，命中单位会被强制削甲/削盾并击退；
 * 血量足够的目标会被 {@link ShrapnelEntity} 切碎。</p>
 */
public class EndRailBulletType extends BulletType{
    float length = 2400f;

    Effect pointEffect = new Effect(15f, e -> {
        Rand r = new Rand(e.id);

        Draw.color(FlamePal.red, Color.white, e.fin());
        Lines.stroke(1.25f);
        for(int i = 0; i < 2; i++){
            float rot = r.range(12f) + e.rotation;
            float dst = r.random(12f) + r.random(60f) * e.fin();
            float len = r.random(10f, 15f) * Mathf.slope(e.fin());

            Vec2 v = Tmp.v1.trns(rot, dst).add(e.x, e.y);
            Lines.lineAngle(v.x, v.y, rot, len);
        }
    });

    Effect trailEffect2 = new Effect(40f, e -> {
        Draw.color(FlamePal.red, Color.white, e.fin());

        for(int s : Mathf.signs){
            Vec2 v1 = Tmp.v1.trns(e.rotation, 22f * e.fout()).add(e.x, e.y);
            Vec2 v2 = Tmp.v2.trns(e.rotation, -22f, 12f * s * e.fout()).add(e.x, e.y);
            Fill.tri(e.x, e.y, v1.x, v1.y, v2.x, v2.y);
        }
    });

    public EndRailBulletType(){
        super(0f, 22000f);
        pierceBuilding = true;
        pierce = true;
        reflectable = false;
        collides = false;
        keepVelocity = false;
        lifetime = 35f;

        hitColor = FlamePal.red;
        hitEffect = FlameFX.coloredHit;

        shootEffect = new Effect(12f, 600f, e -> {
            float ang = 90f;
            float scl = 1f;

            Draw.color(FlamePal.red, Color.white, e.fin());
            for(int i = 0; i < 3; i++){
                for(int s : Mathf.signs){
                    float r = e.rotation + ang * s;
                    Drawf.tri(e.x, e.y, 10f * scl * e.fout(), 40f * Mathf.pow(scl, 2f) * (0.5f + e.fin() * 0.5f), r);
                }

                ang *= 0.5f;
                scl *= 1.5f;
            }

            for(int s : Mathf.signs){
                float r = e.rotation + 90f * s;
                Drawf.tri(e.x, e.y, 12f * e.fout(), 90f * e.fin(), r);
            }

            Drawf.tri(e.x, e.y, 15f * e.fout(), 70f * e.fout(), e.rotation + 180f);
            Drawf.tri(e.x, e.y, 15f * e.fout(), 70f * e.fout(), e.rotation);
        });
        smokeEffect = Fx.none;
    }

    @Override
    protected float calculateRange(){
        return length;
    }

    @Override
    public void init(){
        super.init();
        drawSize = length * 2f;
    }

    @Override
    public void init(Bullet b){
        super.init(b);

        // ★ FO 用 static 变量累计穿透血量，多枚轨道炮会互相污染；
        //   这里改为局部数组 (lambda 内可变) 保证每发独立。
        float[] bulletHealth = {120000f};

        Tmp.v1.trns(b.rotation(), length).add(b.x, b.y);
        float vx = Tmp.v1.x, vy = Tmp.v1.y;
        float rot = b.rotation();

        float len = b.fdata = FlameUtils.hitLaser(b.team, 4f, b.x, b.y, vx, vy, null, h -> (bulletHealth[0] -= h.maxHealth()) <= 0, (h, x, y) -> {
            hit(b, x, y);
            hitEnd(b, h, x, y, Mathf.clamp(h.maxHealth() / 40000f) + Mathf.sqrt((h.maxHealth() / 120000f) / 2f) / 1000f);

            if(h instanceof Unit u){
                u.armor -= 100 + Math.abs(u.armor / 10);
                u.shield -= 900;
                if(u.shield < -2000f) u.shield = -2000f;
                Tmp.v2.trns(b.rotation(), 6f + 5f / u.mass());
                u.vel.add(Tmp.v2);

                EmpathyDamage.damageUnit(u, 32000f + u.maxHealth / 40f, true, () -> {
                    SpecialDeathEffects.get(u.type).deathUnit(u, x, y, rot);
                });
            }else if(h instanceof Building bl){
                EmpathyDamage.damageBuildingRaw(bl, 32000f + bl.maxHealth / 40f, true, null);
            }
        });

        Vec2 nor = Tmp.v1.trns(b.rotation(), 1f).nor();
        for(float i = 0; i < len; i += Mathf.random(12f, 20f)){
            pointEffect.at(b.x + nor.x * i, b.y + nor.y * i, b.rotation());
        }
        for(float i = 0; i < len; i += 48f){
            trailEffect2.at(b.x + nor.x * i, b.y + nor.y * i, b.rotation());
        }
    }

    void hitEnd(Bullet b, Healthc h, float x, float y, float scl){
        if(scl <= 0.1f) return;

        FlameFX.desRailHit.at(x, y, b.rotation(), Mathf.pow(scl, 1.5f));

        TextureRegion cur = (h instanceof Unit u ? u.type.region : (h instanceof Building bl ? bl.block.region : null));
        int count = (int)(scl * 20);
        if(cur != null && count > 5){
            Sized s = (Sized)h;
            float size = Math.min(s.hitSize() / 15f, 10f) * Mathf.random(0.75f, 1f);

            SpecialDeathEffects death = (h instanceof Unit u ? SpecialDeathEffects.get(u.type) : SpecialDeathEffects.get(((Building)h).block));

            if(death.solid){
                float forceScl = 1f;
                float angRange = 90f;
                for(int c = 0; c < 5; c++){
                    for(int i = 0; i < count; i++){
                        Vec2 v = Tmp.v1.trns(b.rotation() + Mathf.range(angRange), Mathf.random(s.hitSize() / 3f));
                        float len = v.len();
                        float force = (len / 5f) * forceScl;
                        float vx = (v.x / len) * force, vy = (v.y / len) * force;

                        ShrapnelEntity shr = ShrapnelEntity.create(v.x + x, v.y + y, Mathf.random(360f), size, cur);
                        shr.lifetime = Mathf.random(90f, 190f) * (0.5f + scl * 0.5f);

                        shr.vx = vx;
                        shr.vy = vy;
                        shr.vr = Mathf.range(4f) * force;
                        shr.z = Layer.flyingUnit;
                        shr.team = b.team;
                        shr.collided.add(h.id());
                        shr.explosion = death.explosionEffect != Fx.none ? death.explosionEffect : FlameFX.fragmentExplosion;

                        shr.damage = h.maxHealth() / 300f + 45f * scl;
                    }

                    forceScl *= 1.4f;
                    angRange *= 0.5f;
                }
            }else{
                death.hit(s, x, y, b.rotation(), 0.5f);
            }
            FlameSounds.desRailHit.at(x, y, Mathf.random(0.95f, 1.05f), scl);
        }
    }

    @Override
    public void draw(Bullet b){
        float len = b.fdata;
        int count = (int)(len / 24f) + 2;
        Draw.color(FlamePal.red, Color.white, b.fin());

        Rand r = new Rand(b.id + (int)(b.time / 3f));

        Lines.stroke(4f * b.fout());
        Lines.beginLine();
        for(int i = 0; i < count; i++){
            float fin = i / (count - 1f);
            Vec2 v = Tmp.v1.trns(b.rotation(), (fin * fin * fin) * len, r.range(4f)).add(b.x, b.y);
            Lines.linePoint(v.x, v.y);
        }
        Lines.endLine(false);
    }

    @Override
    public void drawLight(Bullet b){
    }
}
