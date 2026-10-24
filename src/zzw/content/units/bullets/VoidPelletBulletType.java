package zzw.content.units.bullets;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.gen.Bullet;
import mindustry.graphics.Layer;
import zzw.content.graphics.BlackHoleSFX;

/**
 * 虚空弹丸 (完全复刻 PU132 VoidPelletBulletType)
 *
 * PU132 原版逻辑:
 * - 黑色方块子弹, 软跟踪目标
 * - init 时: 记录初始角度到 fdata, 然后 rotation 随机偏转±120°
 * - update 时: 用 angleDistSigned(rotation, fdata) 计算带符号角度差, 平滑转向回初始角度
 * - 当角度差<=0.06° 时停止转向 (fdata=-361f 标记完成)
 *
 * ★ 完全按 PU132 原版, 纯黑色绘制, 不加描边
 */
public class VoidPelletBulletType extends AntiCheatBulletTypeBase {
    public VoidPelletBulletType(float speed, float damage) {
        super(speed, damage);
        lifetime = 90f;
        trailColor = Color.black;
        trailLength = 16;
        trailWidth = 2f;
        despawnEffect = zzw.content.units.effects.HitEffect.voidHit;  // PU132: HitFx.voidHit (搬回原版特效)
        hitEffect = zzw.content.units.effects.HitEffect.voidHit;      // PU132: HitFx.voidHit (搬回原版特效)
        homingPower = 0.01f;
        homingRange = 50f;
        homingDelay = 20f;
        hitSize = 3f;
        keepVelocity = false;
        // ★ v158 兼容: 默认 layer=Layer.bullet(65f) 在飞行单位(75f)之下会被盖住
        //   设置 layer=Layer.flyingUnit+1f 确保黑色子弹在单位之上渲染可见
        layer = Layer.flyingUnit + 1f;
        // ★ PU132 原版没有 drag
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        // 记录初始角度, 然后随机偏转±120° (PU132 L27-30)
        b.fdata(b.rotation());
        b.rotation(b.rotation() + Mathf.range(120f));
    }

    @Override
    public void update(Bullet b) {
        super.update(b);
        // 平滑转向回初始角度 (PU132 L34-42)
        // fdata=-361f 表示转向完成, 不再处理
        if (b.fdata() != -361f) {
            float ang = angleDistSigned(b.rotation(), b.fdata());
            b.vel().rotate(-ang * Mathf.clamp(0.2f * Time.delta));
            if (Math.abs(ang) <= 0.06f) {
                b.fdata(-361f);
            }
        }

        // ★ 引力扭曲改由黑洞后处理着色器完成 (与黑洞炮共用同一条 GPU 管线)。
        //   不再在 draw() 里画透镜: 着色器直接弯折屏幕内容, 才有真实的"空间被吸进去"感。
        //   强度 0.45 (黑洞炮 1.5~4.0): 影响半径约 18px、中心位移约 7px,
        //   对 3px 弹体是"轻微扭曲"但已经能看清。
        if (BlackHoleSFX.inst != null) {
            BlackHoleSFX.inst.blackHole(b.x(), b.y(), 0.45f * b.fout(), -(b.time / 6f));
        }
    }

    /**
     * ★ 完全按 PU132 原版 draw (L46-50): 纯黑方块 + 原版拖尾。
     *
     * <p>空间扭曲已移到 {@link #update} 交给黑洞着色器, 这里不再有任何 draw 特效。</p>
     */
    @Override
    public void draw(Bullet b) {
        drawTrail(b);
        Draw.color(Color.black);
        Draw.alpha(1f);
        Fill.square(b.x(), b.y(), 2f, b.rotation() + 45f);
    }

    @Override
    public void drawLight(Bullet b) {
    }

    /**
     * 带符号角度差: 从 b 到 a 需要旋转的角度, 归一化到 [-180, 180]
     * (复刻 PU132 Utils.angleDistSigned)
     */
    private static float angleDistSigned(float a, float b) {
        float d = (a - b) % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }
}
