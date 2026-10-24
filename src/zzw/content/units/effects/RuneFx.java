package zzw.content.units.effects;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.Effect;
import mindustry.graphics.Drawf;
import zzw.content.graphics.UnityPal;

/**
 * 魔法阵 / 符文特效集合 (End 阵营高级攻击视觉)。
 *
 * <p>本类提供两类内容:</p>
 * <ol>
 *   <li><b>静态绘制工具</b>: {@link #seal} / {@link #triangleSeal} / {@link #clockFace}
 *       等, 供各类弹体在自己的 {@code draw()} 里组合出各不相同的符文图案;</li>
 *   <li><b>炮口特效</b>: {@link #railSeal} / {@link #sweepSeal} / {@link #portalSeal}
 *       以及 {@link #timeField}, 只在开火瞬间生成一次, 不随弹体逐帧绘制,
 *       因此 oppression 这种 55 段大体积单位也不会产生额外负担。</li>
 * </ol>
 *
 * <p>★ 性能约定: 所有图案只用 {@code Lines} / {@code Fill} 基础图元,
 * 单个图案不超过 30 个图元, 不使用 {@code Draw.vert} 与纹理采样。</p>
 *
 * @author zzw
 */
public class RuneFx{

    /** End 猩红主色 / 符文亮色 */
    private static final Color runeRed = UnityPal.scarColor;
    private static final Color runeWhite = Color.valueOf("ffd8d2");

    /* ==================== 静态绘制工具 ==================== */

    /**
     * 环形魔法阵: 双圆环 + 外圈刻度 + 内部三角 (卡巴拉风格)。
     *
     * @param radius   外圈半径
     * @param rotation 整体旋转角
     * @param alpha    透明度
     * @param ticks    外圈刻度数量 (8~12 观感最佳)
     */
    public static void seal(float x, float y, float radius, float rotation, float alpha, Color color, int ticks){
        Draw.color(color);
        Draw.alpha(alpha);

        Lines.stroke(radius * 0.05f);
        Lines.circle(x, y, radius);
        Lines.stroke(radius * 0.022f);
        Lines.circle(x, y, radius * 0.66f);

        // 外圈刻度
        for(int i = 0; i < ticks; i++){
            float a = rotation + i * 360f / ticks;
            float c = Mathf.cosDeg(a), s = Mathf.sinDeg(a);
            Lines.line(x + c * radius * 0.74f, y + s * radius * 0.74f,
                x + c * radius * 0.96f, y + s * radius * 0.96f, false);
        }

        // 内部三角形 (三条弦)
        float r = radius * 0.50f;
        float a0 = rotation * -1.35f;
        float x1 = x + Mathf.cosDeg(a0 + 90f) * r, y1 = y + Mathf.sinDeg(a0 + 90f) * r;
        float x2 = x + Mathf.cosDeg(a0 + 210f) * r, y2 = y + Mathf.sinDeg(a0 + 210f) * r;
        float x3 = x + Mathf.cosDeg(a0 + 330f) * r, y3 = y + Mathf.sinDeg(a0 + 330f) * r;
        Lines.line(x1, y1, x2, y2, false);
        Lines.line(x2, y2, x3, y3, false);
        Lines.line(x3, y3, x1, y1, false);
    }

    /** 星形魔法阵: 两个错位方形 (六芒星变体), 适合虚空/菱形主题。 */
    public static void starSeal(float x, float y, float radius, float rotation, float alpha, Color color){
        Draw.color(color);
        Draw.alpha(alpha);
        Lines.stroke(radius * 0.045f);
        for(int k = 0; k < 2; k++){
            float a = rotation + k * 45f;
            float c = Mathf.cosDeg(a), s = Mathf.sinDeg(a);
            Drawf.tri(x, y, radius * 0.10f, radius * 1.05f, a + 90f);
            Drawf.tri(x, y, radius * 0.10f, radius * 1.05f, a + 270f);
            // 外侧短弧强调轮廓
            Drawf.tri(x + c * radius * 0.9f, y + s * radius * 0.9f, radius * 0.2f, radius * 0.2f, a);
        }
        Lines.circle(x, y, radius * 0.72f);
        Lines.circle(x, y, radius);
    }

    /**
     * 钟面魔法阵 (chronos 专用): 12 个刻度 + 时针/分针 + 外圈。
     *
     * @param handAngle 指针角度 (通常跟随弹体朝向)
     */
    public static void clockFace(float x, float y, float radius, float rotation, float handAngle, float alpha, Color color){
        Draw.color(color);
        Draw.alpha(alpha);

        Lines.stroke(radius * 0.06f);
        Lines.circle(x, y, radius);
        Lines.stroke(radius * 0.03f);

        for(int i = 0; i < 12; i++){
            float a = rotation + i * 30f;
            float len = (i % 3 == 0) ? 0.80f : 0.90f;
            float c = Mathf.cosDeg(a), s = Mathf.sinDeg(a);
            Lines.line(x + c * radius * len, y + s * radius * len,
                x + c * radius, y + s * radius, false);
        }

        // 指针
        Lines.stroke(radius * 0.08f);
        Lines.lineAngle(x, y, handAngle, radius * 0.62f);
        Lines.stroke(radius * 0.05f);
        Lines.lineAngle(x, y, handAngle + 120f, radius * 0.38f);

        Fill.circle(x, y, radius * 0.10f);
    }

    /** 微弱引力透镜: 两条被拉弯的细弧 + 外侧暗环 (enigma 专用)。 */
    public static void lens(float x, float y, float rotation, float radius, float alpha, Color color){
        float px = -Mathf.sinDeg(rotation), py = Mathf.cosDeg(rotation);

        Draw.blend(Blending.additive);
        Draw.color(color);
        Draw.alpha(alpha);

        // 两侧向内弯折的短弧: 用两段折线近似
        for(int s : Mathf.signs){
            float ox = x + px * radius * s, oy = y + py * radius * s;
            float bx = x + px * radius * 0.45f * s + Mathf.cosDeg(rotation) * radius * 0.9f;
            float by = y + py * radius * 0.45f * s + Mathf.sinDeg(rotation) * radius * 0.9f;
            Lines.stroke(radius * 0.09f);
            Lines.line(x - Mathf.cosDeg(rotation) * radius * 0.9f + px * radius * s,
                y - Mathf.sinDeg(rotation) * radius * 0.9f + py * radius * s, ox, oy, false);
            Lines.line(ox, oy, bx, by, false);
        }

        // 中心压暗, 制造"光线被弯折"的错觉
        Draw.blend();
        Draw.color(Color.black);
        Draw.alpha(alpha * 0.5f);
        Fill.circle(x, y, radius * 0.55f);
    }

    /* ==================== 炮口特效 (只在开火瞬间生成一次) ==================== */

    /**
     * 轨道穿透激光炮口符文 (oppression soul-destroyer):
     * 快速旋转的 10 刻度魔法阵 + 向外扩散的能量环。
     */
    public static final Effect railSeal = new Effect(38f, 260f, e -> {
        float fin = e.fin();
        float alpha = (1f - fin) * 0.9f;
        float radius = 16f + 30f * Mathf.pow(fin, 0.45f);

        Draw.blend(Blending.additive);
        seal(e.x, e.y, radius, e.rotation + Time.time * 3.4f, alpha, runeRed, 10);

        Draw.color(runeWhite);
        Draw.alpha(alpha * 0.8f);
        Lines.stroke(2.2f * (1f - fin));
        Lines.circle(e.x, e.y, radius * 1.25f);
        Draw.blend();
        Draw.reset();

        Drawf.light(e.x, e.y, radius * 1.8f, runeRed, 0.45f * alpha);
    });

    /**
     * 扫射激光炮口虚空阵 (oppression oppressor):
     * 由外向内的黑色收束环 + 6 条短符线 (冷色高光避免全黑看不见)。
     */
    public static final Effect sweepSeal = new Effect(50f, 300f, e -> {
        float fin = e.fin();
        float alpha = 1f - fin;
        float radius = 62f - 32f * fin;

        Draw.color(Color.black);
        Draw.alpha(alpha * 0.85f);
        Fill.circle(e.x, e.y, radius * 0.72f);

        Draw.blend(Blending.additive);
        Draw.color(runeRed);
        Draw.alpha(alpha * 0.75f);
        Lines.stroke(3f);
        Lines.circle(e.x, e.y, radius);
        Lines.stroke(1.4f);
        for(int i = 0; i < 6; i++){
            float a = e.rotation + i * 60f + Time.time * 1.8f;
            float c = Mathf.cosDeg(a), s = Mathf.sinDeg(a);
            Lines.line(e.x + c * radius * 0.8f, e.y + s * radius * 0.8f,
                e.x + c * radius * 1.35f, e.y + s * radius * 1.35f, false);
        }
        Draw.color(runeWhite);
        Draw.alpha(alpha * 0.9f);
        Fill.circle(e.x, e.y, radius * 0.16f);
        Draw.blend();
        Draw.reset();
    });

    /**
     * 虚空门户炮口符文 (oppression void):
     * 双层菱形 + 四角短刺, 与门户的菱形轮廓呼应。
     */
    public static final Effect portalSeal = new Effect(64f, 360f, e -> {
        float fin = e.fin();
        float alpha = 1f - fin;
        float radius = 20f + 46f * Mathf.pow(fin, 0.55f);

        Draw.blend(Blending.additive);
        starSeal(e.x, e.y, radius, Time.time * 1.5f, alpha * 0.85f, runeRed);

        Draw.color(runeWhite);
        Draw.alpha(alpha * 0.7f);
        Lines.stroke(1.8f);
        Lines.circle(e.x, e.y, radius * 0.55f);
        Draw.blend();
        Draw.reset();

        Drawf.light(e.x, e.y, radius * 2f, runeRed, 0.4f * alpha);
    });

    /**
     * 时间停止力场 (chronos): 覆盖大范围、持续整个时停时长。
     *
     * <p>首 1.2 秒快速扩张到 600px, 随后保持并缓慢旋转, 末段渐隐。
     * 只绘制一层大圆 + 12 刻度 + 3 段弧, 即使持续 900 tick 也只是常量开销。</p>
     */
    public static final Effect timeField = new Effect(15f * 60f, 1400f, e -> {
        float grow = Mathf.clamp(e.time / 70f);
        float fade = Mathf.clamp((e.lifetime - e.time) / 60f);
        float radius = 600f * grow;
        float alpha = 0.42f * fade;

        Draw.blend(Blending.additive);
        Draw.color(runeRed);
        Draw.alpha(alpha);
        Lines.stroke(4f * fade);
        Lines.circle(e.x, e.y, radius);
        Lines.stroke(1.6f * fade);
        Lines.circle(e.x, e.y, radius * 0.965f);

        // 外圈刻度: 12 个, 缓慢旋转
        for(int i = 0; i < 12; i++){
            float a = Time.time * 0.35f + i * 30f;
            float c = Mathf.cosDeg(a), s = Mathf.sinDeg(a);
            Lines.line(e.x + c * radius * 0.93f, e.y + s * radius * 0.93f,
                e.x + c * radius, e.y + s * radius, false);
        }

        // 三段大弧强调"时间环"
        Draw.color(runeWhite);
        Draw.alpha(alpha * 0.55f);
        Lines.stroke(6f * fade);
        for(int i = 0; i < 3; i++){
            Lines.arc(e.x, e.y, radius * 0.88f, 0.16f, -Time.time * 0.5f + i * 120f);
        }
        Draw.blend();
        Draw.reset();
    });
}
