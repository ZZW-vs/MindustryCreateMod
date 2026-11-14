package zzw.content.units.bullets;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Fill.circle;
import static arc.graphics.g2d.Lines.stroke;
import static arc.math.Angles.randLenVectors;

/**
 * WavefrontLaser - wavefront 炮台武器子弹 (PU_V8 移植)
 *
 * <p>配置: 粗激光 + 加速延伸, 颜色浅蓝 (advance) 渐变到白色。</p>
 *
 * <p>★ 打击感重做: 原版只有一条逐渐变长的线, 命中时没有反馈, 玩家反馈"没力气"。
 * 现在光束顶端每 5 tick 生成一次蓝色冲击火花 (环 + 放射状电弧), 配合炮台的
 * 重后坐/震动与专属炮口冲击环, 形成"蓄力→炮口炸开→光束贯出→顶端持续爆点"的节奏。</p>
 *
 * 参考: PU_V8 main/src/unity/entities/bullet/laser/WavefrontLaser.java
 */
public class WavefrontLaserBulletType extends AcceleratingLaserBulletType {
    // PU_V8 UnityPal 颜色
    private static final Color ADVANCE = Color.valueOf("a3e3ff");
    private static final Color ADVANCE_DARK = Color.valueOf("59a7ff");

    /** 专属炮口冲击环 (蓝色重炮口)。 */
    public static final Effect muzzle = new Effect(30f, 220f, e -> {
        Draw.blend(Blending.additive);

        color(Color.white, ADVANCE, e.fin());
        // 贯穿炮管的白芯
        Drawf.tri(e.x, e.y, 16f * e.fout(), 110f * e.fout(), e.rotation);
        // 反向泄压
        Drawf.tri(e.x, e.y, 26f * e.fout(), 52f * e.fout(), e.rotation + 180f);

        // 冲击环
        stroke(3.5f * e.fout() + 0.2f);
        color(ADVANCE);
        Lines.circle(e.x, e.y, e.finpow() * 52f);

        // 8 向蓝色电弧
        for(int i = 0; i < 8; i++){
            float a = e.rotation + i * 45f + e.fin() * 22f;
            float len = e.finpow() * 46f;
            stroke(1.6f * e.fout());
            color(Color.white, ADVANCE, e.fin());
            Lines.line(e.x + Angles.trnsx(a, 12f), e.y + Angles.trnsy(a, 12f),
                    e.x + Angles.trnsx(a, len), e.y + Angles.trnsy(a, len), false);
        }

        Drawf.light(e.x, e.y, e.finpow() * 70f, ADVANCE, 0.8f);
        Draw.blend();
        Draw.reset();
    });

    /** 光束顶端命中火花。 */
    public static final Effect tipImpact = new Effect(18f, 160f, e -> {
        Draw.blend(Blending.additive);

        Draw.color(Color.white, ADVANCE, e.fin());
        Fill.circle(e.x, e.y, 7f * e.fout());

        stroke(2.4f * e.fout() + 0.2f);
        color(ADVANCE);
        Lines.circle(e.x, e.y, 5f + e.finpow() * 26f);

        randLenVectors(e.id, 6, e.fin() * 30f, e.rotation + 180f, 75f, (x, y) -> {
            stroke(1.8f * e.fout() + 0.1f);
            color(Color.white, ADVANCE_DARK, e.fin());
            Lines.line(e.x + x * 0.35f, e.y + y * 0.35f, e.x + x, e.y + y, false);
        });

        Drawf.light(e.x, e.y, e.finpow() * 36f, ADVANCE, 0.6f);
        Draw.blend();
        Draw.reset();
    });

    public WavefrontLaserBulletType(float damage) {
        super(damage);
        lifetime = 90f;
        collisionWidth = 30f;
        width = 64f;
        maxLength = 450f;
        // ★ 打击感: 更快贯出、更粗、更强击退
        accel = 60f;
        laserSpeed = 55f;
        knockback = 4f;
        colors = new Color[]{
            ADVANCE_DARK.cpy().mul(0.9f, 1f, 1f, 0.4f),
            ADVANCE_DARK.cpy(),
            ADVANCE.cpy(),
            Color.white
        };
    }

    @Override
    public void update(mindustry.gen.Bullet b) {
        super.update(b);

        if(!b.isAdded()) return;

        // 光束顶端的持续爆点 (每 5 tick 一次, 与碰撞检测同频, 开销可控)
        if(b.timer(1, 5f)){
            float len = Math.min(b.fdata, maxLength);
            float x = b.x + Angles.trnsx(b.rotation(), len);
            float y = b.y + Angles.trnsy(b.rotation(), len);
            tipImpact.at(x, y, b.rotation());
        }
    }
}
