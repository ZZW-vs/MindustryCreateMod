package zzw.content.units.flame.entities;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.IntSet;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.pooling.Pool.Poolable;
import arc.util.pooling.Pools;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Sized;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.graphics.Layer;
import zzw.content.units.flame.FlameUtils;
import zzw.content.units.flame.graphics.GraphicUtils;

/**
 * 高速飞散的贴图碎片 (移植自 flame.entities.ShrapnelEntity)。
 *
 * <p>用目标单位贴图切出的四边形做物理碎片，飞行中命中敌人造成
 * "速度比例" 伤害，寿命结束或完全静止时消失。</p>
 *
 * <p>★ 与 FO 原版的差异: 建造时的临时向量改用 {@link Tmp} 槽位，
 * 不再依赖 flame.Utils 的全局 {@code v}。</p>
 */
public class ShrapnelEntity extends DrawEntity implements Poolable{
    public IntSet collided = new IntSet();
    TextureRegion region = new TextureRegion();
    float[] verts = new float[4 * 4];
    public float damage;
    float hitSize;

    public float vx, vy, vr;
    public float rotation;
    public float z = Layer.flyingUnitLow;
    public Team team = Team.derelict;
    public Effect explosion = Fx.none;

    public float time, lifetime;

    public static ShrapnelEntity create(float x, float y, float rotation, float size, TextureRegion region){
        ShrapnelEntity s = Pools.obtain(ShrapnelEntity.class, ShrapnelEntity::new);
        s.x = x;
        s.y = y;
        s.rotation = rotation;
        s.region.set(region);
        s.lifetime = 60f;
        s.hitSize = 0f;

        float rangeX = Mathf.range((region.width / 2f) / 2f);
        float rangeY = Mathf.range((region.height / 2f) / 2f);

        for(int i = 0; i < 4; i++){
            Vec2 v = Tmp.v1.trns(i * 90f + Mathf.range(45f), (s.hitSize += size * Mathf.random(0.5f, 1f)));
            float tu = v.x / Draw.scl;
            float tv = v.y / Draw.scl;

            int idx = i * 4;

            s.verts[idx] = v.x;
            s.verts[idx + 1] = v.y;
            s.verts[idx + 2] = Mathf.lerp(region.u, region.u2, (tu + rangeX + region.width / 2f) / region.width);
            s.verts[idx + 3] = Mathf.lerp(region.v, region.v2, (tv + rangeY + region.height / 2f) / region.height);
        }
        s.hitSize /= 4f;
        s.add();

        return s;
    }

    @Override
    public void update(){
        x += vx * Time.delta;
        y += vy * Time.delta;
        rotation += vr * Time.delta;

        float len = Mathf.sqrt(vx * vx + vy * vy);

        time += Time.delta;

        if(damage > 0){
            FlameUtils.scanEnemies(team, x, y, hitSize / 1.5f, true, true, t -> {
                if(collided.add(t.id())){
                    Healthc h = (Healthc)t;
                    Sized size = (Sized)t;

                    float scl = len / 5f;

                    h.damage(damage * scl);
                    explosion.at(x, y, hitSize / 2f);

                    float drag = 1f - Mathf.clamp(((size.hitSize() / 2f) / hitSize) / 30f);

                    vx *= drag;
                    vy *= drag;
                    vr *= drag;
                }
            });
        }

        float nlen = vx * vx + vy * vy;

        if(time >= lifetime || nlen < 0.01f){
            explosion.at(x, y, hitSize);
            remove();
        }
    }

    @Override
    public void draw(){
        float[] vert = GraphicUtils.verts;
        float color = Color.whiteFloatBits, mix = Color.clearFloatBits;

        float sin = Mathf.sinDeg(rotation), cos = Mathf.cosDeg(rotation);

        int seg = 0;
        for(int i = 0; i < 16; i += 4){
            float dx = verts[i], dy = verts[i + 1];

            vert[seg] = dx * cos - dy * sin + x;
            vert[seg + 1] = dx * sin + dy * cos + y;
            vert[seg + 2] = color;
            vert[seg + 3] = verts[i + 2];
            vert[seg + 4] = verts[i + 3];
            vert[seg + 5] = mix;

            seg += 6;
        }

        Draw.z(z);
        Draw.vert(region.texture, vert, 0, 24);
    }

    @Override
    public float clipSize(){
        return 60f;
    }

    @Override
    public void reset(){
        collided.clear();
        damage = vx = vy = vr = rotation = 0f;
        time = lifetime = 0f;
        hitSize = 0f;
        z = Layer.flyingUnitLow;
        region.set(Core.atlas.white());

        team = Team.derelict;
        explosion = Fx.none;
    }

    @Override
    protected void removeGroup(){
        super.removeGroup();
        Groups.queueFree(this);
    }
}
