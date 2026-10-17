package zzw.content.units.effects;

import arc.graphics.Color;
import arc.math.Mathf;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Fill.circle;
import static arc.graphics.g2d.Fill.poly;
import static arc.graphics.g2d.Fill.square;
import static arc.graphics.g2d.Fill.tri;
import static arc.graphics.g2d.Lines.lineAngle;
import static arc.graphics.g2d.Lines.stroke;
import static arc.math.Angles.randLenVectors;

/**
 * 通用战斗特效集合 (颜色参数化, 替代原版 mindustry.content.Fx 系列)。
 *
 * <p>本类收录炮口开火 / 弹道命中 / 爆炸三大类共 20 个特效,
 * 全部使用 {@code e.color} (弹药颜色) 参数化配色 —— 子弹类命中时
 * 自动传入弹体颜色, 炮台开火时传入热度颜色, 因此同一个特效
 * 可以无差别地套用在任何武器上。</p>
 *
 * <p>绘制手法来源:</p>
 * <ul>
 *   <li>PU132 {@code unity.content.effects.ShootFx} —— 三角火焰
 *       ({@code Drawf.tri}) + 白芯 + 扩散环的分层画法;</li>
 *   <li>饱和火力 SFFx / FlameOut FlameFX —— {@code Drawf.light}
 *       动态光源、随机向量火花 ({@code Angles.randLenVectors});</li>
 *   <li>Vanilla Expansion CustomFx —— 冲击环先扩后收的
 *       slope 曲线控制。</li>
 * </ul>
 *
 * <p>★ 设计约束: 特效尺寸与被替代的原版特效接近 (可直接替换
 * 不改变战斗观感), 但层数更多、带动态光源, 视觉密度更高。</p>
 *
 * @author zzw
 */
public class CombatFx{
    /* ==================== 炮口开火类 ==================== */

    /**
     * 炮口闪光 (18f) —— 替代原版 {@code Fx.shootBig}。
     *
     * <p>绘制顺序: 前向火焰三角 + 后坐小火焰 → 白色核心圆 →
     * 扩散圆环 (finpow 曲线) → 扇形火花粒子 → 动态光源。</p>
     */
    public static final Effect

    muzzleFlash = new Effect(18f, 120f, e -> {
        color(e.color, Color.white, e.fout());
        Drawf.tri(e.x, e.y, 9f * e.fout(), 28f * e.fout(), e.rotation);
        Drawf.tri(e.x, e.y, 7f * e.fout(), 16f * e.fout(), e.rotation + 180f);

        color(Color.white);
        circle(e.x, e.y, 3f * e.fout());

        randLenVectors(e.id, 6, e.finpow() * 16f, e.rotation, 55f, (x, y) -> {
            float a = Mathf.angle(x, y);
            Drawf.tri(e.x + x, e.y + y, 2f * e.fout(), 5f * e.fout(), a);
        });

        Drawf.light(e.x, e.y, e.finpow() * 34f, e.color, 0.65f);
    }),

    /**
     * 炮口闪光 + 硝烟 (26f) —— 替代原版 {@code Fx.shootBigSmoke}。
     *
     * <p>前半段为火光三角与白芯, 后半段为灰烟团 (由
     * {@code Mathf.curve(e.fin(), 0.4f)} 延迟出现) 与光源。</p>
     */
    muzzleFlashSmoke = new Effect(26f, 110f, e -> {
        color(e.color, Color.white, e.fout());
        Drawf.tri(e.x, e.y, 8f * e.fout(), 24f * e.fout(), e.rotation);
        color(Color.white);
        circle(e.x, e.y, 2.5f * e.fout());

        randLenVectors(e.id, 8, e.finpow() * 20f, e.rotation, 40f, (x, y) -> {
            float a = Mathf.angle(x, y);
            color(Color.lightGray, Color.darkGray, e.fin());
            circle(e.x + x, e.y + y, (1.2f + e.fout() * 3.2f) * Mathf.curve(e.fin(), 0.35f) + 0.4f);
            color(e.color, Color.white, e.fout());
            lineAngle(e.x + x, e.y + y, a, e.fout() * 4f);
        });

        Drawf.light(e.x, e.y, e.finpow() * 30f, e.color, 0.6f);
    }),

    /**
     * 大口径炮口闪光 + 硝烟 (30f) —— 替代原版 {@code Fx.shootBigSmoke2}。
     *
     * <p>{@link #muzzleFlashSmoke} 的加强版: 火焰更长、烟团更多、
     * 环更粗, 附加一道后坐冲击环。</p>
     */
    muzzleFlashSmokeBig = new Effect(30f, 140f, e -> {
        color(e.color, Color.white, e.fout());
        Drawf.tri(e.x, e.y, 12f * e.fout(), 36f * e.fout(), e.rotation);
        Drawf.tri(e.x, e.y, 9f * e.fout(), 22f * e.fout(), e.rotation + 180f);

        color(Color.white);
        circle(e.x, e.y, 3.5f * e.fout());

        stroke(e.fout() * 2.2f + 0.3f);
        color(e.color, Color.white, e.fout());
        arc.graphics.g2d.Lines.circle(e.x, e.y, e.finpow() * 22f);

        randLenVectors(e.id, 11, e.finpow() * 26f, e.rotation, 42f, (x, y) -> {
            float a = Mathf.angle(x, y);
            color(Color.lightGray, Color.darkGray, e.fin());
            circle(e.x + x, e.y + y, (1.5f + e.fout() * 4f) * Mathf.curve(e.fin(), 0.3f) + 0.5f);
            color(e.color, Color.white, e.fout());
            lineAngle(e.x + x, e.y + y, a, e.fout() * 5f);
        });

        Drawf.light(e.x, e.y, e.finpow() * 44f, e.color, 0.65f);
    }),

    /**
     * 激光炮口 (21f) —— 替代原版 {@code Fx.lancerLaserShoot}。
     *
     * <p>PU132 {@code ShootFx.laserChargeShoot} 移植: 四向旋转
     * 白芯三角 (按 finpow 展开 112°), 中心白圆收尾。</p>
     */
    laserShoot = new Effect(21f, e -> {
        color(e.color, Color.white, e.fout());
        for(int i = 0; i < 4; i++){
            Drawf.tri(e.x, e.y, 4f * e.fout(), 29f, e.rotation + 90f * i + e.finpow() * 112f);
        }
        color(Color.white);
        circle(e.x, e.y, e.fout() * 5f);
    }),

    /**
     * 磁轨炮口 (26f) —— 替代原版 {@code Fx.instShoot}。
     *
     * <p>PU132 {@code ShootFx.scarRailShoot} 的颜色参数化改版:
     * 白色冲击环 + 双侧巨大对开三角 (白芯叠色), 磁轨炮标志性视觉。</p>
     */
    railShoot = new Effect(26f, 160f, e -> {
        e.scaled(12f, b -> {
            color(Color.white, Color.lightGray, b.fin());
            stroke(b.fout() * 3f + 0.2f);
            arc.graphics.g2d.Lines.circle(b.x, b.y, b.finpow() * 50f);
        });
        for(int i = 0; i < 2; i++){
            int sign = Mathf.signs[i];
            color(e.color);
            Drawf.tri(e.x, e.y, 13f * e.fout(), 80f, e.rotation + 90f * sign);
            color(Color.white);
            Drawf.tri(e.x, e.y, Math.max(13f * e.fout() - 4f, 0f), 76f, e.rotation + 90f * sign);
        }
        Drawf.light(e.x, e.y, e.finpow() * 40f, e.color, 0.7f);
    }),

    /**
     * 电弧炮口 (18f) —— 替代原版 {@code Fx.lightningShoot}。
     *
     * <p>三向火焰三角 + 白芯 + 环绕电火花 (randLenVectors 全向)。</p>
     */
    lightningShoot = new Effect(18f, e -> {
        color(e.color, Color.white, e.fout());
        for(int i = 0; i < 3; i++){
            Drawf.tri(e.x, e.y, 5f * e.fout(), 22f * e.fout(), e.rotation + 120f * i + e.finpow() * 60f);
        }
        color(Color.white);
        circle(e.x, e.y, 3f * e.fout());

        stroke(e.fout() * 1.2f + 0.3f);
        color(e.color, Color.white, e.fout());
        randLenVectors(e.id, 8, e.finpow() * 18f, (x, y) ->
            lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout() * 5f + 1f)
        );

        Drawf.light(e.x, e.y, e.finpow() * 28f, e.color, 0.7f);
    }),

    /* ==================== 弹道命中类 ==================== */

    /**
     * 激光命中 (14f) —— 替代原版 {@code Fx.hitLancer}。
     *
     * <p>★ 重写为十字星形态: 四向旋转主光线 + 斜向菱形碎片散射 +
     * 极小白芯, 告别"扩散圆环"。</p>
     */
    laserHit = new Effect(14f, e -> {
        color(e.color, Color.white, e.fin());
        for(int i = 0; i < 4; i++){
            stroke(e.fout() * 1.5f + 0.3f);
            lineAngle(e.x, e.y, e.rotation + 90f * i + e.finpow() * 80f, e.finpow() * 14f);
        }

        randLenVectors(e.id, 4, e.finpow() * 15f, (x, y) -> {
            float a = Mathf.angle(x, y);
            color(e.color, Color.white, e.fout());
            poly(e.x + x, e.y + y, 4, e.fout() * 2.2f + 0.5f, a + e.fin() * 60f);
        });

        color(Color.white);
        circle(e.x, e.y, e.fout() * 2f);

        Drawf.light(e.x, e.y, e.fout() * 15f, e.color, 0.7f);
    }),

    /**
     * 激光爆裂命中 (20f) —— 替代原版 {@code Fx.hitLaser} /
     * {@code Fx.hitLaserBlast}。
     *
     * <p>★ 重写为六芒星形态: 两个填充三角形反向旋转展开成六芒星 +
     * 放射三角碎片 + 弹色芯, 取代原"双层扩散圆环"。</p>
     */
    laserHitBlast = new Effect(20f, e -> {
        color(e.color, Color.white, e.fin());
        poly(e.x, e.y, 3, e.finpow() * 18f + 1f, e.rotation + e.finpow() * 120f);
        poly(e.x, e.y, 3, e.finpow() * 18f + 1f, e.rotation + 180f - e.finpow() * 120f);

        randLenVectors(e.id, 7, e.finpow() * 20f, (x, y) -> {
            float a = Mathf.angle(x, y);
            color(e.color, Color.white, e.fout());
            Drawf.tri(e.x + x, e.y + y, 2.4f * e.fout(), 5f * e.fout(), a);
        });

        color(e.color);
        circle(e.x, e.y, e.fout() * 3f);

        Drawf.light(e.x, e.y, e.fout() * 24f, e.color, 0.75f);
    }),

    /**
     * 子弹命中 (14f) —— 替代原版 {@code Fx.hitBulletColor}。
     *
     * <p>★ 重写: 沿弹道方向锥形散射的旋转方形碎片 + 弹色迎面斜切线,
     * 白→弹色渐变, 无扩散环。</p>
     */
    bulletHit = new Effect(14f, e -> {
        randLenVectors(e.id, 6, e.finpow() * 17f, e.rotation, 55f, (x, y) -> {
            float a = Mathf.angle(x, y);
            color(Color.white, e.color, e.fin());
            square(e.x + x, e.y + y, e.fout() * 2f + 0.4f, a + e.fin() * 90f);
        });
        color(e.color, Color.white, e.fout());
        stroke(e.fout() * 1.3f + 0.3f);
        lineAngle(e.x, e.y, e.rotation + 90f, e.fout() * 7f);
    }),

    /**
     * 小口径子弹命中 (10f) —— 替代原版 {@code Fx.hitBulletSmall}。
     *
     * <p>★ 重写: 三条短线火花 + 弹色方形小芯 (替代圆芯)。</p>
     */
    bulletHitSmall = new Effect(10f, e -> {
        color(Color.white, e.color, e.fin());
        stroke(e.fout() * 0.9f + 0.3f);
        randLenVectors(e.id, 3, e.finpow() * 9f, (x, y) ->
            lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fin() * 3f + 0.8f)
        );
        color(e.color);
        square(e.x, e.y, e.fout() * 1.6f, e.rotation);
    }),

    /**
     * 大口径子弹命中 (16f) —— 替代原版 {@code Fx.hitBulletBig}。
     *
     * <p>★ 重写: 方形碎片全向旋转飞散 + 四向冲击三角 + 光源,
     * 取代原"圆环 + 线状火花 + 圆芯"。</p>
     */
    bulletHitBig = new Effect(16f, e -> {
        randLenVectors(e.id, 7, e.finpow() * 22f, (x, y) -> {
            float a = Mathf.angle(x, y);
            color(Color.white, e.color, e.fin());
            square(e.x + x, e.y + y, e.fout() * 2.4f + 0.5f, a + e.fin() * 120f);
        });
        color(e.color, Color.white, e.fout());
        for(int i = 0; i < 4; i++){
            Drawf.tri(e.x, e.y, 3f * e.fout(), 10f * e.finpow(), e.rotation + 90f * i + e.finpow() * 50f);
        }
        Drawf.light(e.x, e.y, e.fout() * 18f, e.color, 0.6f);
    }),

    /**
     * 燃烧命中 (14f) —— 替代原版 {@code Fx.fireHit}。
     *
     * <p>橙红火舌 + 灰烟团 + 火光光源, 配合 MultiEffect 使用。</p>
     */
    fireHitSpark = new Effect(14f, e -> {
        color(Pal.lightFlame, Pal.darkFlame, e.fin());
        randLenVectors(e.id, 6, e.finpow() * 14f, (x, y) ->
            circle(e.x + x, e.y + y, 0.8f + e.fout() * 2.6f)
        );
        color(Color.gray, Color.darkGray, e.fin());
        randLenVectors(e.id + 1, 3, e.finpow() * 9f, (x, y) ->
            circle(e.x + x, e.y + y, 1.5f + e.fout() * 3f)
        );
        Drawf.light(e.x, e.y, e.fout() * 16f, Pal.lightFlame, 0.6f);
    }),

    /**
     * 液体命中 (18f) —— 替代原版 {@code Fx.hitLiquid}。
     *
     * <p>弹色液滴四溅 (大小圆双层) + 白色高光芯, 表现液体泼溅感。</p>
     */
    liquidHit = new Effect(18f, e -> {
        color(Color.white, e.color, e.fin());
        randLenVectors(e.id, 7, e.finpow() * 16f, (x, y) -> {
            float s = 0.9f + e.fout() * 2.4f;
            circle(e.x + x, e.y + y, s);
            circle(e.x + x * 0.6f, e.y + y * 0.6f, s * 0.5f);
        });
        color(e.color);
        circle(e.x, e.y, e.fout() * 2.5f);
    }),

    /**
     * 射线命中 (14f) —— 替代原版 {@code Fx.hitBeam}。
     *
     * <p>★ 重写: 呼吸菱形 (absin 脉动) + 短放射线, 适合持续光束
     * 的命中点, 无白圆芯。</p>
     */
    beamHit = new Effect(14f, e -> {
        color(e.color, Color.white, e.fout());
        poly(e.x, e.y, 4, (0.6f + e.fout() * 3.4f) * (1f + Mathf.absin(4f, 0.25f)), 45f + e.fin() * 30f);
        stroke(e.fout() * 1f + 0.3f);
        randLenVectors(e.id, 4, e.fout() * 10f, (x, y) ->
            lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout() * 4f)
        );
    }),

    /**
     * EMP 电花 (12f) —— 替代原版 {@code Fx.hitEmpSpark}。
     *
     * <p>高频折线电弧 (双向短刺) + 白芯, 表现电磁脉冲的噼啪感。</p>
     */
    empSpark = new Effect(12f, e -> {
        color(Color.white, e.color, e.fin());
        stroke(e.fout() * 1.1f + 0.3f);
        randLenVectors(e.id, 5, e.finpow() * 13f, (x, y) -> {
            float a = Mathf.angle(x, y);
            lineAngle(e.x + x, e.y + y, a, e.fout() * 4f + 1f);
            lineAngle(e.x + x, e.y + y, a + 180f, e.fout() * 2.5f);
        });
        color(e.color);
        square(e.x, e.y, e.fout() * 2.2f, 45f);
        Drawf.light(e.x, e.y, e.fout() * 12f, e.color, 0.7f);
    }),

    /* ==================== 爆炸类 ==================== */

    /**
     * 榴弹爆炸 (18f) —— 替代原版 {@code Fx.flakExplosion}。
     *
     * <p>★ 白闪改为旋转方形闪爆 + 八向三角火花, 空爆观感更锋利。</p>
     */
    flakBlast = new Effect(18f, e -> {
        color(Color.white, e.color, e.fout());
        square(e.x, e.y, 2f + e.fout() * 9f, e.fin() * 45f);

        randLenVectors(e.id, 8, e.finpow() * 18f, (x, y) -> {
            float a = Mathf.angle(x, y);
            color(e.color, Color.white, e.fout());
            Drawf.tri(e.x + x, e.y + y, 2.4f * e.fout(), 4.5f * e.fout(), a);
        });

        Drawf.light(e.x, e.y, e.finpow() * 26f, e.color, 0.7f);
    }),

    /**
     * 标准爆炸 (28f) —— 替代原版 {@code Fx.blastExplosion}。
     *
     * <p>★ 冲击波重写为六边形轮廓 (旋转扩散) 取代圆形环:
     * 白闪子特效 → 弹色火球 + 暗边 → 六边形冲击波 + 十向火花 → 光源。</p>
     */
    blastExplosion = new Effect(28f, 140f, e -> {
        e.scaled(14f, s -> {
            color(Color.white, s.fout());
            circle(s.x, s.y, 3f + s.fout() * 14f);
        });

        color(e.color);
        circle(e.x, e.y, e.foutpowdown() * 17f);
        color(Color.darkGray);
        circle(e.x, e.y, e.foutpowdown() * 17f * 0.55f);

        color(e.color, Color.white, e.fout());
        stroke(e.fout() * 2.4f + 0.3f);
        arc.graphics.g2d.Lines.poly(e.x, e.y, 6, e.finpow() * 25f + 1f, 30f + e.fin() * 20f);

        randLenVectors(e.id, 10, e.finpow() * 26f, (x, y) -> {
            float a = Mathf.angle(x, y);
            color(e.color, Color.white, e.fout());
            Drawf.tri(e.x + x, e.y + y, 2.6f * e.fout(), 5f * e.fout(), a);
        });

        Drawf.light(e.x, e.y, e.finpow() * 45f, e.color, 0.7f);
    }),

    /**
     * 巨型爆炸 (40f) —— 替代原版 {@code Fx.massiveExplosion}。
     *
     * <p>★ 冲击波重写: 四段 90° 弧线 (旋转扩散, 方向交替) +
     * 六边形副冲击波, 取代原"双圆形环"。保留火球 + 碎片方块 + 强光源。</p>
     */
    massiveExplosion = new Effect(40f, 220f, e -> {
        e.scaled(20f, s -> {
            color(Color.white, s.fout());
            circle(s.x, s.y, 6f + s.fout() * 26f);
        });

        color(e.color);
        circle(e.x, e.y, e.foutpowdown() * 30f);
        color(Color.darkGray);
        circle(e.x, e.y, e.foutpowdown() * 30f * 0.55f);

        color(e.color, Color.white, e.fout());
        stroke(e.fout() * 3f + 0.4f);
        for(int i = 0; i < 4; i++){
            arc.graphics.g2d.Lines.arc(e.x, e.y, e.finpow() * 42f + 1f, 0.35f, 90f * i + e.fin() * 30f);
        }
        stroke(e.fout() * 1.6f);
        arc.graphics.g2d.Lines.poly(e.x, e.y, 6, e.finpow() * 30f + 1f, 45f - e.fin() * 25f);

        randLenVectors(e.id, 14, e.finpow() * 40f, (x, y) -> {
            float a = Mathf.angle(x, y);
            color(e.color, Color.white, e.fout());
            Drawf.tri(e.x + x, e.y + y, 3.2f * e.fout(), 6.5f * e.fout(), a);
        });
        color(Color.lightGray, Color.darkGray, e.fin());
        randLenVectors(e.id + 1, 6, e.finpow() * 30f, (x, y) ->
            square(e.x + x, e.y + y, e.fout() * 3f + 0.5f, 45f)
        );

        Drawf.light(e.x, e.y, e.finpow() * 70f, e.color, 0.75f);
    }),

    /**
     * 暗蚀爆炸 (24f) —— 替代原版 {@code Fx.sapExplosion}。
     *
     * <p>★ 重写: 黑芯圆 + 弹色描边保留 (Sap 标志性), 外圈扩散环改为
     * 旋转六边形轮廓, 下坠暗滴改为菱形滴, 形态与圆形系区分。</p>
     */
    sapExplosion = new Effect(24f, e -> {
        color(Color.black);
        circle(e.x, e.y, e.foutpowdown() * 15f);
        color(e.color);
        stroke(e.fout() * 1.8f + 0.3f);
        arc.graphics.g2d.Lines.circle(e.x, e.y, e.foutpowdown() * 15f);

        color(Color.black, e.color, e.fin());
        stroke(e.fout() * 1.2f);
        arc.graphics.g2d.Lines.poly(e.x, e.y, 6, e.finpow() * 22f + 1f, e.fin() * 45f);

        randLenVectors(e.id, 8, e.finpow() * 18f, (x, y) -> {
            color(Color.black, e.color, e.fout());
            poly(e.x + x, e.y + y, 4, 1.2f + e.fout() * 3f, Mathf.angle(x, y));
        });

        Drawf.light(e.x, e.y, e.finpow() * 30f, e.color, 0.65f);
    }).layer(Layer.bullet + 0.02f),

    /**
     * 定点光束 (16f) —— 替代原版 {@code Fx.pointBeam}。
     *
     * <p>短促的定向光束线 + 端点白芯, 表现点防御武器的瞬时射线。</p>
     */
    pointBeam = new Effect(16f, e -> {
        color(e.color, Color.white, e.fout());
        stroke(e.fout() * 1.6f + 0.3f);
        lineAngle(e.x, e.y, e.rotation, 3f + e.fout() * 24f);
        color(Color.white);
        circle(e.x, e.y, e.fout() * 3f);
    }),

    /**
     * 能量拖尾 (12f) —— 环绕能量球 / 高频拖尾专用。
     *
     * <p>★ 无白色大圆芯: 旋转收缩的菱形碎片 (poly 4 边) + 微型火花,
     * 与"扩散圆环"系特效形成形态区分。尺寸取自传入的 rotation
     * (调用方把能量球半径作为 size 传入), 颜色完全跟随 e.color。</p>
     */
    energyTrail = new Effect(12f, 80f, e -> {
        color(e.color, e.fout());
        poly(e.x, e.y, 4, e.fout() * e.rotation * 0.22f + 1f, e.rotation * 0.7f + e.fin() * 90f);

        color(e.color, Color.white, e.fin());
        randLenVectors(e.id, 2, e.finpow() * e.rotation * 0.3f, (x, y) ->
            lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout() * 3f + 0.5f)
        );
    }),

    /**
     * 星芒命中 (16f) —— 能量球 / 电系命中的十字星形态。
     *
     * <p>★ 无白色大圆芯: 四向十字主光线 (finpow 展开) + 斜向对角
     * 短刺 + 弹色小芯, 与圆形命中特效形成形态区分。</p>
     */
    starHit = new Effect(16f, e -> {
        color(e.color, Color.white, e.fin());
        for(int i = 0; i < 4; i++){
            stroke(e.fout() * 1.6f + 0.3f);
            lineAngle(e.x, e.y, e.rotation + 90f * i + e.finpow() * 70f, e.finpow() * 16f);
        }
        for(int i = 0; i < 4; i++){
            stroke(e.fout() * 1.1f + 0.2f);
            lineAngle(e.x, e.y, e.rotation + 45f + 90f * i - e.finpow() * 60f, e.finpow() * 10f);
        }
        color(e.color);
        circle(e.x, e.y, e.fout() * 2.2f);

        Drawf.light(e.x, e.y, e.fout() * 18f, e.color, 0.7f);
    });

    private CombatFx(){
    }
}
