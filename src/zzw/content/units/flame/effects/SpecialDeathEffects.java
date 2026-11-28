package zzw.content.units.flame.effects;

import arc.audio.Sound;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.ctype.MappableContent;
import mindustry.entities.Effect;
import mindustry.entities.Sized;
import mindustry.gen.Building;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import zzw.content.units.flame.FlameBatches;
import zzw.content.units.flame.FlamePal;
import zzw.content.units.flame.FlameSounds;
import zzw.content.units.flame.FlameUtils;
import zzw.content.units.flame.effects.Fragmentation.FragmentEntity;
import zzw.content.units.flame.graphics.FragmentationBatch;
import zzw.content.units.flame.graphics.VaporizeBatch.VaporizeHandler;
import zzw.content.units.flame.unit.empathy.EmpathyDamage;

/**
 * 单位/建筑死亡演出配置 (移植自 flame.effects.SpecialDeathEffects)。
 *
 * <p>死亡演出由 {@link FlameBatches#fragBatch} 把目标贴图拆成实体碎片，
 * 并可附带血液飞溅、蒸汽气化与残骸等效果。按内容名注册到 {@link #nameMap}。</p>
 */
public class SpecialDeathEffects{
    public static ObjectMap<String, SpecialDeathEffects> nameMap = new ObjectMap<>();
    public static SpecialDeathEffects def, blood;
    public static Seq<SpecialDeathEffects> all = new Seq<>();

    public Effect debrisEffect = FlameFX.heavyDebris, sparkEffect = FlameFX.destroySparks, explosionEffect = Fx.none;
    public Effect fragmentTrailEffect = null;
    public Cons<FragmentEntity> fragDeath = null;
    public Color disintegrateColor = Pal.rubble, internalColor = Pal.darkerMetal;
    public boolean solid = true, hasInternal = false;
    public Sound deathSound = Sounds.wind, primaryDeathSound = Sounds.none;

    public static void load(){
        def = new SpecialDeathEffects();
        blood = new OrganicDeath(){{
            color.set(FlamePal.blood);
            internalColor = FlamePal.blood;
            colorVariation = 0.2f;

            debrisEffect = Fx.none;
            explosionEffect = new Effect(30f, 400f, e -> {
                Rand r = new Rand(e.id);
                float size = e.rotation;

                int iter = ((int)(size / 7f)) + 12;
                int iter3 = ((int)(size / 14.5f)) + 12;
                Draw.color(color);
                for(int i = 0; i < iter3; i++){
                    Vec2 v = Tmp.v1.trns(r.random(360f), r.random(size / 2f) * e.finpow());
                    float s = r.random(size / 2.75f, size / 2f) * e.fout();
                    Fill.circle(v.x + e.x, v.y + e.y, s);
                }
                for(int i = 0; i < iter; i++){
                    Vec2 v = Tmp.v1.trns(r.random(360f), r.random(size) + (r.random(0.25f, 2f) * size) * e.finpow());
                    float s = r.random(size / 3.5f, size / 2.5f) * e.fout();
                    Fill.circle(v.x + e.x, v.y + e.y, s);
                    Fill.circle(v.x / 2 + e.x, v.y / 2 + e.y, s * 0.5f);
                }

                e.lifetime = size / 1.5f + 10f;
            });
        }};

        OrganicDeath end = new OrganicDeath(){{
            color.set(FlamePal.red);
            fragmentTrailEffect = null;
            liquidAmount = 0.15f;
            liquidRange = 0.3f;
            liquidSize = 0.7f;
            colorVariation = 0f;

            explosionEffect = FlameFX.fragmentExplosion;
            deathSound = FlameSounds.expExotic;
        }};
        // 本项目 mod 内容名在运行期带 "create-" 前缀, 两种名字都注册以兼容
        nameMap.put("despondency", end);
        nameMap.put("create-despondency", end);

        for(SpecialDeathEffects eff : all){
            eff.postLoad();
        }
    }

    public static SpecialDeathEffects get(MappableContent content){
        SpecialDeathEffects ef = get(content.name);
        if(ef == def && content instanceof UnitType u && (u.health >= 4000000f || EmpathyDamage.isNaNInfinite(u.health))){
            return blood;
        }

        return ef;
    }

    public static SpecialDeathEffects get(String name){
        SpecialDeathEffects g = nameMap.get(name);
        if(g != null){
            return g;
        }

        return def;
    }

    public SpecialDeathEffects(){
        all.add(this);
    }

    protected void postLoad(){
        //
    }

    public void cutAlt(Unit u){
        explosionEffect.at(u.x, u.y, u.hitSize / 2f);
        deathSound.at(u.x, u.y, 1f, 1f);
    }

    public void hit(Sized ent, float x, float y, float rotation, float scl){
        explosionEffect.at(x, y, (ent.hitSize() / 2f) * scl);
    }

    public void disintegrateUnit(Unit u, float x1, float y1, float x2, float y2, float width){
        Vec2 vec = Tmp.v1, vec2 = Tmp.v2;
        float rotation = Angles.angle(x1, y1, x2, y2);

        disintegrateUnit(u, x1, y1, x2, y2, width, (d, within) -> {
            if(within){
                d.disintegrating = true;

                Vec2 n = Intersector.nearestSegmentPoint(x1, y1, x2, y2, d.x, d.y, vec);
                float dst = 1f - Mathf.clamp(n.dst(d.x, d.y) / (width / 2f));
                float force = Interp.pow2.apply(dst) * Mathf.random(0.9f, 1.5f);

                d.lifetime = Mathf.lerp(Mathf.random(90f, 130f), Mathf.random(42f, 60f), Interp.pow2.apply(dst));

                vec2.trns(rotation, force, Mathf.range(0.07f * dst));
                d.vx = vec2.x;
                d.vy = vec2.y;
                d.vr = Mathf.range(5f) * dst;
                d.drag = -Mathf.random(0.035f, 0.05f) * dst;
                d.zOverride = Layer.flyingUnit;
            }
        });
    }

    public void disintegrateUnit(Unit u, float x1, float y1, float x2, float y2, float width, VaporizeHandler handler){
        FlameBatches.vaporBatch.discon = d -> d.scorchColor.set(disintegrateColor);
        FlameBatches.vaporBatch.switchBatch(x1, y1, x2, y2, width, u::draw, handler);
    }

    public void deathUnit(Unit u, float x, float y, float rotation){
        deathUnit(u, x, y, rotation, null);
    }

    public void deathUnit(Unit u, float x, float y, float rotation, Cons<FragmentEntity> alt){
        debrisEffect.at(u.x, u.y, rotation, u.type.outlineColor, u.hitSize / 2f);
        explosionEffect.at(u.x, u.y, u.hitSize / 2f);
        Sound sound = primaryDeathSound != Sounds.none ? primaryDeathSound : deathSound;
        sound.at(u.x, u.y, 1f, 1.5f);

        Tmp.v1.trns(rotation - 180f, u.hitSize / 2f).add(u.x, u.y);
        sparkEffect.at(Tmp.v1.x, Tmp.v1.y, rotation, u.hitSize / 2f);

        FragmentationBatch batch = FlameBatches.fragBatch;
        Tmp.v1.trns(rotation - 180f, 80f).add(x, y);
        float sx = Tmp.v1.x, sy = Tmp.v1.y;
        batch.baseElevation = u.type.shadowElevationScl * (u.isFlying() ? 1f : 0.25f);
        if(alt != null){
            batch.fragFunc = alt;
        }else{
            batch.fragFunc = e -> {
                float dx = (e.x - u.x) / 65f + u.vel.x;
                float dy = (e.y - u.y) / 65f + u.vel.y;
                float dx2 = (e.x - sx) / 15f;
                float dy2 = (e.y - sy) / 15f;

                float adst = FlameUtils.angleDistSigned(Angles.angle(sx, sy, e.x, e.y), rotation);
                float scl = Mathf.pow(Mathf.clamp((90 - Math.abs(adst)) / 90f), 2.5f) + Mathf.pow(Mathf.clamp((30 - Math.abs(adst)) / 30f), 3f);
                e.vx = dx + dx2 * scl;
                e.vy = dy + dy2 * scl;
                e.vr = (Mathf.pow(Math.abs(adst), 0.5f) * (adst > 0 ? 1 : -1)) / 15 + Mathf.range(1f);
                e.vz = Mathf.random(-0.01f, 0.1f);
            };
        }
        batch.onDeathFunc = fragDeath;
        batch.altFunc = (hx, hy, tex) -> FlameFX.simpleFragmentation.at(hx, hy, Angles.angle(x, y, hx, hy), Draw.getColor(), tex);
        batch.trailEffect = fragmentTrailEffect;
        batch.explosionEffect = explosionEffect != Fx.none ? explosionEffect : null;
        batch.fragColor = Color.white;
        batch.sound = deathSound;

        batch.switchBatch(u::draw);
    }

    public void deathBuilding(Building b, float x, float y, float rotation){
        debrisEffect.at(b.x, b.y, b.angleTo(x, y) + 180f, b.block.outlineColor, b.hitSize() / 2f);

        Tmp.v1.trns(rotation - 180f, b.hitSize() / 2f).add(b.x, b.y);
        sparkEffect.at(Tmp.v1.x, Tmp.v1.y, rotation, b.hitSize() / 2f);

        Sound sound = primaryDeathSound != Sounds.none ? primaryDeathSound : deathSound;
        sound.at(b.x, b.y, 1f, 1.5f);

        Tmp.v1.trns(rotation - 180f, 250f).add(x, y);
        float sx = Tmp.v1.x, sy = Tmp.v1.y;

        FragmentationBatch batch = FlameBatches.fragBatch;
        batch.baseElevation = 0.1f;
        batch.fragFunc = e -> {
            float dx = (e.x - b.x) / 65f;
            float dy = (e.y - b.y) / 65f;
            float dx2 = (e.x - sx) / 15f;
            float dy2 = (e.y - sy) / 15f;

            float adst = FlameUtils.angleDistSigned(Angles.angle(sx, sy, e.x, e.y), rotation);
            float scl = Mathf.pow(Mathf.clamp((90 - Math.abs(adst)) / 90f), 2.5f) + Mathf.pow(Mathf.clamp((30 - Math.abs(adst)) / 30f), 3f);
            e.vx = dx + dx2 * scl * 0.3f;
            e.vy = dy + dy2 * scl * 0.3f;
            e.vr = (Mathf.pow(Math.abs(adst), 0.5f) * (adst > 0 ? 1 : -1)) / 15 + Mathf.range(1f);
            e.vz = Mathf.random(-0.01f, 0.1f);
        };
        batch.altFunc = (hx, hy, tex) -> FlameFX.simpleFragmentation.at(hx, hy, Angles.angle(x, y, hx, hy), tex);
        batch.explosionEffect = batch.trailEffect = null;
        batch.fragColor = Color.white;
        batch.sound = deathSound;

        batch.switchBatch(b::draw);
    }

    public static class OrganicDeath extends SpecialDeathEffects{
        Color color = new Color(0x780b24d9);
        float colorVariation = 0.4f;
        float liquidAmount = 1f;
        float liquidRange = 1f;
        float liquidSize = 1f;

        OrganicDeath(){
            fragmentTrailEffect = new Effect(20f, e -> {
                float fin = FlameUtils.biasSlope(e.fin(), 0.25f);
                Draw.color(color);
                Fill.circle(e.x, e.y, e.rotation * fin * 1.25f);
            }).layer(Layer.flyingUnitLow);

            fragDeath = e -> {
                float realSize = e.area * 0.35f;

                int size = 6 + (int)((realSize / 2f) * 0.75f * liquidAmount);
                float hscl = (realSize / 2f) / 30f;
                BloodSplatter.explosion(size, e.x, e.y, e.area / 2f, 60f * liquidRange * hscl + 15f, 25f * liquidSize * hscl + 5f, 35f, color, colorVariation);
            };
            deathSound = FlameSounds.expOrganic;
            hasInternal = true;
        }

        @Override
        protected void postLoad(){
            internalColor = color;
        }

        @Override
        public void deathUnit(Unit u, float x, float y, float rotation, Cons<FragmentEntity> alt){
            int size1 = 8 + (int)((u.hitSize / 2f) * 0.75f * liquidAmount);
            int size2 = 13 + (int)((u.hitSize / 2f) * 1.5f * liquidAmount);

            float hscl = (u.hitSize / 2f) / 30f;

            BloodSplatter.directionalExplosion(size2, u.x, u.y, rotation, 70f, u.hitSize / 2f, 300f * liquidRange * hscl + 12f, 25f * liquidSize * hscl + 5f, color, colorVariation);
            BloodSplatter.directionalExplosion(size1, u.x, u.y, rotation, 50f, u.hitSize / 2f, 420f * liquidRange * hscl + 24f, 40f * liquidSize * hscl + 5f, color, colorVariation);

            super.deathUnit(u, x, y, rotation, alt);
        }
    }
}
