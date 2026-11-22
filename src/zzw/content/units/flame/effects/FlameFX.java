package zzw.content.units.flame.effects;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import zzw.content.units.flame.FlamePal;
import zzw.content.units.flame.FlameUtils;
import zzw.content.units.flame.graphics.GraphicUtils;

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Draw.z;
import static arc.math.Interp.pow2;
import static arc.math.Interp.pow2In;
import static arc.math.Interp.pow2Out;
import static arc.math.Interp.pow3Out;
import static arc.math.Interp.pow4In;
import static arc.math.Interp.pow4Out;
import static arc.math.Interp.pow5In;
import static arc.math.Interp.pow5Out;
import static arc.math.Interp.pow10In;
import static arc.math.Interp.sineOut;

/**
 * 消沉相关特效 (裁自 flame.effects.FlameFX，仅保留子弹/处决实际引用的部分)。
 *
 * <p>★ 与 FO 原版的差异: FO 用全局 {@code Utils.rand} 并逐次 setSeed，
 * 本移植改为每个特效内新建 {@link Rand}，避免全局随机状态互相覆盖。</p>
 */
public class FlameFX{

    public static Effect fragmentExplosion = new Effect(40f, 300f, e -> {
        Rand r = new Rand(e.id);
        float size = e.rotation;
        e.lifetime = size / 1.5f + 10f;

        int iter = ((int)(size / 7f)) + 12;
        int iter3 = ((int)(size / 14.5f)) + 12;
        color(Color.gray);
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

        float sfin = Mathf.curve(e.fin(), 0f, 0.65f);
        if(sfin < 1f){
            int iter2 = ((int)(size / 10f)) + 4;
            float sfout = 1f - sfin;

            color(Pal.lighterOrange, Pal.lightOrange, Color.gray, e.fin());
            Lines.stroke((1.7f * sfout) * (1f + size / 60f));

            Draw.z(Layer.effect + 0.001f);

            for(int i = 0; i < iter2; i++){
                Vec2 v = Tmp.v1.trns(r.random(360f), r.random(0.001f, size / 2f) + (r.random(0.4f, 2.2f) * size) * pow2Out.apply(sfin));
                Lines.lineAngle(e.x + v.x, e.y + v.y, Mathf.angle(v.x, v.y), 1f + sfout * 3 * (1f + size / 50f));
            }
        }
    });

    public static Effect destroySparks = new Effect(40f, 1200f, e -> {
        Rand r = new Rand(e.id + 64331);
        float size = (float)e.data;
        int isize = (int)(size * 1.75f) + 12;
        int isize2 = (int)(size * 1.5f) + 9;

        float fin1 = Mathf.clamp(e.time / 20f);
        float fin2 = Mathf.clamp(e.time / 10f);

        Lines.stroke(Math.max(2f, Mathf.sqrt(size) / 8f));
        for(int i = 0; i < isize2; i++){
            float f = Mathf.curve(fin1, 0f, r.random(0.8f, 1f));
            Vec2 v = Tmp.v1.trns(r.random(360f), 1f + (size * r.nextFloat() + 10f) * 1.5f * pow3Out.apply(f));
            float rsize = r.random(0.5f, 1.5f);
            if(f < 1){
                color(FlamePal.paleYellow, Pal.lightOrange, Color.gray, f);
                Lines.lineAngle(v.x + e.x, v.y + e.y, v.angle(), (size / 5f) * rsize * (1 - f));
            }
        }
        for(int i = 0; i < isize; i++){
            float f = Mathf.curve(e.fin(), 0f, r.random(0.5f, 1f));
            float re = Mathf.pow(r.nextFloat(), 1.5f);
            float ang = re * 90f * (r.nextFloat() > 0.5f ? 1 : -1);
            float dst = (50f + ((size * 3f) / (1f + re / 5f)) * Mathf.pow(r.nextFloat(), (1f + re / 2f))) * Interp.pow3Out.apply(f);
            Vec2 v = Tmp.v1.trns(e.rotation + ang, 1f + dst);
            float rsize = r.random(0.75f, 1.5f);

            if(f < 1){
                color(FlamePal.paleYellow, Pal.lightOrange, Color.gray, pow2In.apply(f));
                Lines.lineAngle(v.x + e.x, v.y + e.y, v.angle(), (size / 3f) * rsize * (1 - f));
            }
        }

        color(FlamePal.paleYellow);
        for(int i = 0; i < 4; i++){
            float rot = i * 90f;
            Drawf.tri(e.x, e.y, (size / 2.5f) * (1 - fin2), size + size * fin2 * 1f, rot);
        }
    }).layer(Layer.effect + 0.005f);

    public static Effect heavyDebris = new Effect(4f * 60f, 1200f, e -> {
        Rand r = new Rand(e.id + 644331);
        float size = (float)e.data;
        float sizeTime = size + 15f;
        int isize = (int)(size * 1.75f) + 12;

        float fin = Mathf.clamp(e.time / sizeTime);
        float fout = Mathf.clamp((e.lifetime - e.time) / 60f);
        Lines.stroke(3f);
        for(int i = 0; i < isize; i++){
            Vec2 v = Tmp.v1.trns(r.random(360f), Mathf.sqrt(r.nextFloat()) * size * 0.75f).add(e.x, e.y);
            float f = Mathf.curve(fin, 0f, r.random(0.5f, 1f));
            float angle = Mathf.pow(r.nextFloat(), 1.25f) * (r.random(1f) < 0.5f ? -1f : 1f) * 60f;
            float dst = r.random((220f + size * 4.5f) * pow3Out.apply(f)) * (1 - Math.abs(angle / 60f) / 1.5f);
            float s = r.chance(0.25f) ? (size / 3f) * r.random(0.5f, 1f) : Math.min(r.random(5f, 9f), size / 4f);
            float rrot = r.random(360f);
            int sides = r.random(3, 6);
            Vec2 v2 = Tmp.v2.trns(angle + e.rotation, dst);

            Draw.color(Tmp.c1.set(e.color).mul(r.random(0.9f, 1.2f)).a(fout));

            if(r.chance(0.75f)){
                Fill.poly(v.x + v2.x, v.y + v2.y, sides, s, rrot);
            }else{
                Lines.poly(v.x + v2.x, v.y + v2.y, sides, s, rrot);
            }
        }
    }).layer(Layer.debris - 0.01f);

    public static Effect simpleFragmentation = new Effect(30f, e -> {
        if(!(e.data instanceof TextureRegion region)) return;
        float bounds = Math.min(region.width, region.height);
        float b2 = bounds / 4f;
        float bw = b2 / region.texture.width;
        float bh = b2 / region.texture.height;
        float bscl = bounds * Draw.scl;
        int ib = (int)(bscl * 1.5f) + 8;
        Rand r = new Rand(e.id + 46241);

        Draw.color(e.color);
        for(int i = 0; i < ib; i++){
            float u = Mathf.lerp(region.u, (region.u2 - bw), r.nextFloat());
            float v = Mathf.lerp(region.v, (region.v2 - bh), r.nextFloat());
            float u2 = u + bw;
            float v2 = v + bh;

            TextureRegion tr = Tmp.tr1;
            tr.texture = region.texture;
            tr.set(u, v, u2, v2);

            float f = Mathf.curve(e.fin(), 0f, r.random(0.8f, 1f));

            Vec2 base = Tmp.v1.trns(r.random(360f), bscl / 2f).add(e.x, e.y);
            Vec2 off = Tmp.v2.trns(e.rotation + r.range(30f), 120f * r.nextFloat() * pow2Out.apply(f));

            float rrot = r.random(360f) + r.range(180f) * f;

            if(f < 1){
                Draw.alpha(1f - Mathf.curve(f, 0.8f, 1f));
                Draw.rect(tr, base.x + off.x, base.y + off.y, rrot);
            }
        }
    }).layer(Layer.flyingUnitLow);

    public static Effect coloredHit = new Effect(15f, e -> {
        Rand r = new Rand(e.id);

        color(Color.white, FlamePal.red, e.fin());
        Lines.stroke(0.5f + e.fout());

        for(int i = 0; i < 8; i++){
            float ang = r.range(12f) + e.rotation;
            float len = r.random(40f) * e.fin();
            Vec2 v = Tmp.v1.trns(ang, len).add(e.x, e.y);

            Lines.lineAngle(v.x, v.y, ang, e.fout() * 8f + 1f);
        }
    });

    public static Effect desGroundHit = new Effect(30f, 250f, e -> {
        Rand r = new Rand(e.id);

        int amount = r.random(4, 12);
        int amount2 = r.random(7, 14);
        float c = r.random(0.1f, 0.6f);
        float c2 = r.random(0.1f, 0.3f);

        z(Layer.groundUnit);
        color(Color.gray);
        for(int i = 0; i < amount2; i++){
            float l = (i / (amount2 - 1f)) * c2;
            float f = Mathf.curve(e.fin(), l, (1f - c2) + l);
            float ang = r.random(360f);
            float len = r.random(80f) * e.rotation;
            float scl = r.random(8.5f, 19f) * e.rotation;
            if(f > 0f && f < 1f){
                float f2 = pow2Out.apply(f) * 0.6f + f * 0.4f;
                Vec2 v = Tmp.v1.trns(ang, len * f2).add(e.x, e.y);
                Fill.circle(v.x, v.y, scl * (1f - f));
            }
        }

        z(Layer.groundUnit + 0.02f);
        color(FlamePal.melt, e.color, pow3Out.apply(e.fin()));
        for(int i = 0; i < amount; i++){
            float l = (i / (amount - 1f)) * c;
            float f = Mathf.curve(e.fin(), l, (1f - c) + l);
            float ang = r.random(360f);
            float len = r.random(100f) * e.rotation;
            float scl = r.random(3f, 13f) * e.rotation;
            if(f > 0f && f < 1f){
                float f2 = pow2Out.apply(f) * 0.4f + f * 0.6f;
                Vec2 v = Tmp.v1.trns(ang, len * f2).add(e.x, e.y);
                Fill.circle(v.x, v.y, scl * (1f - f));
            }
        }
    }).layer(Layer.groundUnit);

    public static Effect desGroundHitMain = new Effect(90f, 900f, e -> {
        Rand r = new Rand(e.id);

        float arange = 25f;
        float scl = 1f;
        float range = 300f;

        color(Color.gray, 0.8f);
        for(int i = 0; i < 4; i++){
            int count = r.random(15, 23);
            for(int k = 0; k < count; k++){
                float f = Mathf.curve(e.fin(), 0f, 1f - r.random(0.2f));
                float rr = r.range(arange) + e.rotation;
                float len = r.random(range) * pow4Out.apply(e.fin());
                float sscl = r.random(21f, 43f) * scl * pow2.apply(1f - f) * Mathf.clamp(e.time / 8f);

                if(f < 1){
                    Vec2 v = Tmp.v1.trns(rr, len).add(e.x, e.y);
                    Fill.circle(v.x, v.y, sscl);
                }
            }

            arange *= 2f;
            scl *= 1.12f;
            range *= 0.6f;
        }
        float fin2 = Mathf.clamp(e.time / 18f);

        if(fin2 < 1){
            int count = 20;
            color(Pal.lighterOrange);
            for(int i = 0; i < count; i++){
                float f = Mathf.curve(fin2, 0f, 1f - r.random(0.2f));
                float ang = r.range(40f) + e.rotation;
                float off = r.random(70f) + r.random(15f) * f;
                float len = r.random(190f, 450f);

                if(f < 1){
                    Vec2 v = Tmp.v1.trns(ang, off).add(e.x, e.y);
                    Lines.stroke(0.5f + (1f - f) * 3f);
                    Lines.lineAngle(v.x, v.y, ang, len * f, false);
                }
            }
        }
    });

    public static Effect desCreepHit = new Effect(20f, e -> {
        float angr = 90f;
        float len = 1f;
        Rand r = new Rand(e.id);

        Draw.color(FlamePal.red);
        Lines.stroke(1.75f);
        for(int i = 0; i < 4; i++){
            for(int j = 0; j < 10; j++){
                float f = Mathf.curve(e.fin(), 0f, 1f - r.random(0.2f));
                float tlen = r.random(32f) * len * f + r.random(15f);
                float rot = r.range(angr) + e.rotation;
                float slope = pow2Out.apply(Mathf.slope(f)) * 24f * len;
                Vec2 v = Tmp.v1.trns(rot, tlen).add(e.x, e.y);
                Lines.lineAngle(v.x, v.y, rot, slope, false);
            }

            angr *= 0.7f;
            len *= 1.7f;
        }
        Draw.reset();
    });

    public static Effect desCreepHeavyHit = new Effect(300f, 1200f, e -> {
        float sizeScl = e.data instanceof Float ? (float)e.data : 1f;

        Rand r = new Rand(e.id);

        float scl = Mathf.clamp(e.time / 8f);
        float range = 32f;
        float countScl = 1f;
        float z = z();
        Tmp.c2.set(Color.gray).a(0.8f);
        for(int i = 0; i < 5; i++){
            color(Pal.lightOrange, Tmp.c2, i / 4f);
            float arange = 180f;
            float range2 = 1f;
            for(int j = 0; j < 5; j++){
                int count = (int)(r.random(12, 15) * countScl);
                for(int k = 0; k < count; k++){
                    float f = Mathf.curve(e.fin(), 0f, 1f - r.random(0.3f));
                    float ang = r.range(arange) + e.rotation;
                    float len = r.random(range * range2) * sizeScl * 0.5f;
                    float size = r.random(10f, 24f) * scl * sizeScl * 0.5f;

                    z(z - r.random(0.002f));
                    if(f < 1f){
                        Vec2 v = Tmp.v1.trns(ang, len * pow5Out.apply(f)).add(e.x, e.y);
                        Fill.circle(v.x, v.y, size * (1f - pow10In.apply(f)));
                    }
                }

                arange *= 0.6f;
                range2 *= 1.75f;
            }
            scl *= 1.5f;
            range *= 1.6f;
            countScl *= 1.4f;
        }
        z(z);

        float shock = 230f * sizeScl * (1f + e.fin() * 2f) + (e.fin() * 50f);
        color(Pal.lighterOrange);
        if(e.time < 5f){
            Fill.circle(e.x, e.y, shock);
        }

        Lines.stroke(3f * e.fout());
        Lines.circle(e.x, e.y, shock);

        for(int i = 0; i < 16; i++){
            float ang = r.random(360f);
            Vec2 v = Tmp.v1.trns(ang, shock).add(e.x, e.y);
            Drawf.tri(v.x, v.y, 8f * e.fout() * sizeScl, (70f + 25f * e.fin()) * sizeScl, ang + 180f);
        }

        color(Pal.lighterOrange, Pal.lightOrange, e.fin());
        float arange = 180f;
        float range2 = 1f;
        Lines.stroke(3f);
        for(int i = 0; i < 6; i++){
            int count = r.random(8, 12);
            for(int k = 0; k < count; k++){
                float f = Mathf.curve(e.fin(), 0f, 1f - r.random(0.3f));
                float f2 = pow5Out.apply(f);
                float rot = e.rotation + r.range(arange);
                float len = range2 * r.random(120f) * sizeScl * f2 + r.random(50f * sizeScl);
                float str = r.random(34f, 60f) * range2 * sizeScl * pow2Out.apply(Mathf.slope(f2));
                if(f < 1f){
                    Vec2 v = Tmp.v1.trns(rot, len).add(e.x, e.y);
                    Lines.lineAngle(v.x, v.y, rot, str);
                }
            }

            arange *= 0.65f;
            range2 *= 1.6f;
        }
    });

    public static Effect desGroundMelt = new Effect(15f * 60, e -> {
        z(Layer.debris);
        color(Color.red);
        float fout = Mathf.curve(e.fout(), 0f, 0.333f);

        Fill.circle(e.x, e.y, e.rotation * Mathf.clamp(e.time / 6f) * fout);

        z(Layer.debris + 0.05f);

        color(FlamePal.melt);
        Draw.blend(Blending.additive);
        Fill.circle(e.x, e.y, e.rotation * Mathf.clamp(e.time / 6f) * fout);
        Draw.blend();
    }).layer(Layer.debris);

    public static Effect desRailHit = new Effect(80f, 900f, e -> {
        float sizeScl = e.data instanceof Float ? (float)e.data : 1f;

        Rand r = new Rand(e.id);

        float ang = 180f;
        float rscl = 0.7f * sizeScl;
        Draw.color(FlamePal.red);
        for(int i = 0; i < 5; i++){
            int count = (int)(10 * rscl);
            for(int j = 0; j < count; j++){
                float fin = Mathf.curve(e.fin(), 0f, 1f - r.random(0.2f));
                float rot = r.range(ang) + e.rotation;
                float off = r.random(22f * rscl) + r.random(50f * Mathf.pow(rscl, 1.5f)) * pow4Out.apply(fin);
                float sscl = r.random(0.7f, 1.2f);

                float wid = 12f * sscl * rscl * (1f - pow4In.apply(fin));
                float hei = 52f * sscl * Mathf.pow(rscl, 1.5f) * pow5Out.apply(fin);

                Vec2 v = Tmp.v1.trns(rot, off).add(e.x, e.y);
                Drawf.tri(v.x, v.y, wid, hei, rot);
                Drawf.tri(v.x, v.y, wid, wid * 2.2f, rot + 180f);
            }

            ang *= 0.6f;
            rscl *= 1.5f;
        }

        ang = 180f;
        rscl = 0.5f * sizeScl;
        Draw.color(FlamePal.red, Color.white, e.fin());
        Lines.stroke(3f);
        for(int i = 0; i < 7; i++){
            int count = 12;
            for(int j = 0; j < count; j++){
                float fin = Mathf.curve(e.fin(), 0f, 1f - r.random(0.2f));
                float rot = r.range(ang) + e.rotation;
                float off = r.random(30f * rscl) + r.random(40f * Mathf.pow(rscl, 1.6f)) * pow5Out.apply(fin);

                float len = r.random(20f, 40f) * Mathf.pow(rscl, 1.6f) * sineOut.apply(Mathf.slope(pow5Out.apply(fin)));

                Vec2 v = Tmp.v1.trns(rot, off).add(e.x, e.y);
                Lines.lineAngle(v.x, v.y, rot, len, false);
            }

            ang *= 0.5f;
            rscl *= 1.5f;
        }

        if(sizeScl < 0.75f) return;
        Draw.color(Color.white, 0.666f * e.fout());

        GraphicUtils.drawShockWave(e.x, e.y, -105f, 0f, -e.rotation - 90f, 400f * sizeScl * pow2Out.apply(e.fin()) + 70f, 30f * Mathf.pow(sizeScl, 1f / 1.5f) * pow2Out.apply(e.fin()) + 4f, 16, 0.015f);
    });

    public static Effect desNukeShockwave = new Effect(190f, 1900f * 2f, e -> {
        float size = e.rotation;

        Draw.color(Color.white, 0.333f * e.fout());
        Lines.stroke((size / 15f) + (size / 5f) * e.fin());
        Lines.circle(e.x, e.y, size / 3f + size * pow2Out.apply(e.fin()) * 2f);
    }).layer(Layer.groundUnit + 1f);

    public static Effect desNuke = new Effect(80f, 500f * 2, e -> {
        if(!(e.data instanceof float[] arr)) return;
        float size = e.rotation;

        Rand r = new Rand(e.id);

        float scl = 1f;
        Tmp.c2.set(Color.gray).a(0.8f);
        for(int k = 0; k < 6; k++){
            float cf = k / 5f;
            color(Tmp.c2, Pal.lightOrange, cf);
            for(int i = 0; i < 40; i++){
                float f = Mathf.curve(e.fin(), 0f, 1f - r.random(0.2f));
                float len = r.random(size * scl * 0.75f) * pow5Out.apply(f) + r.random(size / 5f);
                float ang = r.random(360f);
                float psize = size / 5f;
                float rad = r.random(psize * (scl * 0.5f + 0.5f) * 0.87f, psize) * scl * (1f - pow5In.apply(f));
                if(f < 1f){
                    Tmp.v1.trns(ang, len).add(e.x, e.y);
                    Fill.circle(Tmp.v1.x, Tmp.v1.y, rad);
                }
            }
            scl *= 0.75f;
        }
        scl = 1f;
        color(Pal.lighterOrange);
        Lines.stroke(3f);
        for(int i = 0; i < 4; i++){
            for(int j = 0; j < 20; j++){
                float f = Mathf.curve(e.fin(), 0f, 1f - r.random(0.2f));
                float ang = r.random(360f);
                float len = r.random(size * scl * 0.5f) * pow5Out.apply(f) + r.random(size / 5f);
                float line = r.random(22f, 45f) * Mathf.pow(scl, 1.1f) * pow2Out.apply(Mathf.slope(pow5Out.apply(f)));

                if(f < 1f){
                    Tmp.v1.trns(ang, len).add(e.x, e.y);
                    Lines.lineAngle(Tmp.v1.x, Tmp.v1.y, ang, line, false);
                }
            }
            scl *= 1.4f;
        }

        float fin = Mathf.clamp(e.time / 10f);
        if(fin < 1){
            Tmp.c2.set(Pal.lightOrange).a(0f);
            color(Pal.lighterOrange, Tmp.c2, fin);
            for(int i = 0; i < arr.length; i++){
                float len1 = arr[i], len2 = arr[(i + 1) % arr.length];
                float ang1 = (i / (float)arr.length) * 360f;
                float ang2 = ((i + 1f) / arr.length) * 360f;

                if(len1 >= size){
                    len1 += (size / 1.5f) * fin;
                }
                if(len2 >= size){
                    len2 += (size / 1.5f) * fin;
                }

                float x1 = Mathf.cosDeg(ang1) * len1, y1 = Mathf.sinDeg(ang1) * len1;
                float x2 = Mathf.cosDeg(ang2) * len2, y2 = Mathf.sinDeg(ang2) * len2;

                Fill.tri(e.x, e.y, e.x + x1, e.y + y1, e.x + x2, e.y + y2);
            }
        }
    });

    public static Effect desNukeShoot = new Effect(35f, e -> {
        float ang = 90f, len = 1f;
        Rand r = new Rand(e.id);

        Lines.stroke(2f);
        for(int i = 0; i < 5; i++){
            for(int j = 0; j < 7; j++){
                float f = Mathf.curve(e.fin(), 0f, 1f - r.random(0.2f));
                float rot = e.rotation + r.range(ang);
                Draw.color(FlamePal.red, Color.white, f);
                Vec2 v = Tmp.v1.trns(rot, r.random(40f) * pow2Out.apply(f) * len).add(e.x, e.y);
                Lines.lineAngle(v.x, v.y, rot, f * 40f * r.random(0.75f, 1f) * len * pow2Out.apply(Mathf.slope(f)), false);
            }
            ang *= 0.5f;
            len *= 1.4f;
        }
    });

    public static Effect desNukeVaporize = new Effect(40f, 1200f, e -> {
        float size = e.data instanceof Float ? (float)e.data : 10f;

        Rand r = new Rand(e.id);

        int count = 20 + (int)(size * size * 0.5f);
        float c = 0.25f;
        for(int i = 0; i < count; i++){
            float l = r.nextFloat() * c;
            float f = Mathf.curve(e.fin(), l, ((1f - c) + l) * r.random(0.8f, 1f));
            float len = r.random(0.5f, 1f) * (80f + size * 10f) * pow2In.apply(f);
            float off = Mathf.sqrt(r.nextFloat()) * size, ang = r.random(360f), rng = r.range(10f);
            float scl = (size / 2f) * r.random(0.9f, 1.1f) * FlameUtils.biasSlope(f, 0.1f);

            if(f > 0 && f < 1){
                Vec2 v1 = Tmp.v1.trns(ang, off).add(e.x, e.y).add(Tmp.v2.trns(e.rotation + rng, len));
                Draw.color(Pal.lightOrange, Pal.rubble, pow3Out.apply(f));
                Fill.circle(v1.x, v1.y, scl);
            }
        }
    }).layer(Layer.flyingUnit);

    public static Effect desNukeShockSmoke = new Effect(40f, 800f, e -> {
        Rand r = new Rand(e.id);

        int count = 10;
        float c = 0.4f;
        for(int i = 0; i < count; i++){
            float l = r.nextFloat() * c;
            float f = Mathf.curve(e.fin(), l, ((1f - c) + l) * r.random(0.8f, 1f));
            float len = r.random(0.75f, 1f) * 160f * pow2In.apply(f);
            float off = Mathf.sqrt(r.nextFloat()) * Vars.tilesize / 2f, ang = r.random(360f), rng = r.range(10f);
            float scl = r.random(4f, 6f) * (1f - pow2In.apply(f));

            if(f > 0 && f < 1){
                Vec2 v1 = Tmp.v1.trns(ang, off).add(e.x, e.y).add(Tmp.v2.trns(e.rotation + rng, len));
                color(Pal.rubble, Color.gray, f);
                Fill.circle(v1.x, v1.y, scl);
            }
        }
    });

    public static Effect desMissileHit = new Effect(50f, 800f, e -> {
        Rand r = new Rand(e.id);

        Tmp.c2.set(Color.gray).a(0.8f);
        float scl1 = Mathf.clamp(e.time / 3f);
        float scl3 = 1.1f;
        float angScl = 0.6f;
        for(int i = 0; i < 4; i++){
            float scl2 = 1f;
            float len = 1f;
            float ang = 180f;

            color(Tmp.c2, FlamePal.red, i / 3f);
            for(int j = 0; j < 5; j++){
                for(int k = 0; k < 9; k++){
                    float f = Mathf.curve(e.fin(), 0f, 1f - r.random(0.3f));
                    float rot = e.rotation + r.range(ang);
                    float ll = r.random(45f) * len * pow5Out.apply(f);
                    float scl = r.random(0.666f, 1f) * scl2 * scl1 * 18f * (1f - pow10In.apply(f));

                    Vec2 v = Tmp.v1.trns(rot, ll).add(e.x, e.y);
                    Fill.circle(v.x, v.y, scl);
                }

                ang *= angScl;
                len *= 1.5f;
                scl2 *= scl3;
            }
            scl1 *= 0.9f;
            angScl *= 0.8f;
            scl3 *= 0.9f;
        }
        color(FlamePal.red);
        scl1 = 1f;
        scl3 = 1f;
        angScl = 180f;
        for(int i = 0; i < 5; i++){
            for(int j = 0; j < 6; j++){
                float f = Mathf.curve(e.fin(), 0f, 1f - r.random(0.3f));
                float rot = e.rotation + r.range(angScl);
                float ll = r.random(20f) * scl3 * pow2Out.apply(f);
                float size = r.random(5f, 10f);
                float wid = size * scl1 * FlameUtils.biasSlope(f, 0.2f);
                float len = wid * 3f + size * 7f * Mathf.pow(scl1, 1.2f) * pow5Out.apply(f);

                Vec2 v = Tmp.v1.trns(rot, wid * 2f + ll).add(e.x, e.y);
                Drawf.tri(v.x, v.y, wid, len, rot);
                Drawf.tri(v.x, v.y, wid, wid * 3f, rot + 180f);
            }

            scl1 *= 1.2f;
            scl3 *= 1.5f;
            angScl *= 0.5f;
        }

        Draw.reset();
    });
}
