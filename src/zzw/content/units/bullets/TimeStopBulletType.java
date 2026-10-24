package zzw.content.units.bullets;

import zzw.content.units.anticheat.AntiCheatBulletModule;
import zzw.content.units.anticheat.ArmorDamageModule;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import mindustry.content.Fx;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;
import zzw.content.units.effects.RuneFx;

/**
 * 时间停止子弹 (移植自 PU132 TimeStopBulletType, 简化版)
 * - 标准子弹, 跟踪目标, 命中后给目标一个减速/失效效果
 * - 简化为只触发 effect (不实现真正的时间停止机制, 因为没有 Unity.TimeStop 系统)
 * - lifetime=110f, pierceCap=3
 */
public class TimeStopBulletType extends AntiCheatBulletTypeBase {
    public float duration = 45f;

    public TimeStopBulletType(float speed, float damage) {
        super(speed, damage);
        despawnEffect = zzw.content.units.effects.CombatFx.laserHit;
        hitEffect = zzw.content.units.effects.CombatFx.laserHit;
        trailColor = Color.valueOf("f53036");
        trailLength = 10;
        trailWidth = 4f;
        pierce = true;
        pierceCap = 3;
        lifetime = 110f;
        // 装备削甲模块
        modules = new AntiCheatBulletModule[]{
            new ArmorDamageModule(1f / 100f, 2f, 8f, 3f)
        };
    }

    /**
     * ★ 美化版绘制: 弹体本体变成一枚旋转的"钟面"符文 ——
     * 12 刻度 + 时针/分针 + 中心白芯, 与 chronos 的时停主题呼应。
     */
    @Override
    public void draw(Bullet b) {
        drawTrail(b);

        float rot = b.rotation();

        Draw.blend(Blending.additive);
        // 钟面随弹体前进缓慢旋转, 指针始终指向飞行方向
        RuneFx.clockFace(b.x(), b.y(), 7.5f, b.time * 3.2f, rot, 0.9f, trailColor);

        // 前向尖刺 (保留原版的"突进"轮廓)
        Draw.color(Color.white);
        Draw.alpha(0.85f);
        Drawf.tri(b.x(), b.y(), 3.2f, 13f, rot);
        Draw.blend();
        Draw.reset();
    }

    @Override
    public void drawLight(Bullet b) {
    }
}
