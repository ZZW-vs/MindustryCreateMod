package zzw.content.units.bullets;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Bullet;
import mindustry.graphics.Drawf;
import zzw.content.units.effects.RuneFx;

/**
 * 符文激光突刺 (opticaecus 主炮)。
 *
 * <p>保留原版 {@link LaserBulletType} 的多层光束与命中逻辑, 额外叠加:</p>
 * <ul>
 *   <li>枪口六刻度魔法阵 (与其它单位的 8/10/12 刻度区分, 形成"六边"辨识度);</li>
 *   <li>光束上 3 个向前滚动的符文环, 环径随推进逐渐收束 —— 强化"突刺"方向感;</li>
 *   <li>尖端十字星芒 + 白点。</li>
 * </ul>
 *
 * <p>激光本体只有 16 tick 存活, 单发绘制开销约 40 个基础图元。</p>
 *
 * @author zzw
 */
public class RunicLaserBulletType extends LaserBulletType{

    public RunicLaserBulletType(float damage){
        super(damage);
    }

    @Override
    public void draw(Bullet b){
        super.draw(b);

        float rot = b.rotation();
        float len = b.fdata;
        float fade = b.fout();
        float cx = Mathf.cosDeg(rot), cy = Mathf.sinDeg(rot);
        float px = -cy, py = cx;

        Draw.blend(Blending.additive);

        // 枪口魔法阵 (六个刻度)
        RuneFx.seal(b.x, b.y, 22f * fade, b.time * 4.2f, 0.85f * fade, colors[1], 6);

        // 束内滚动符文环: 从枪口向尖端推进并收束
        if(len > 24f){
            for(int i = 0; i < 3; i++){
                float d = (b.time * 6f + i * len / 3f) % len;
                float k = 1f - d / len * 0.55f;
                RuneFx.seal(b.x + cx * d, b.y + cy * d, 11f * k * fade,
                    rot + b.time * 3f, 0.38f * fade, colors[0], 4);
            }
        }

        // 尖端十字星芒
        float ex = b.x + cx * len, ey = b.y + cy * len;
        Draw.color(Color.white);
        Draw.alpha(0.85f * fade);
        Lines.stroke(1.6f * fade);
        Lines.line(ex - cx * 16f, ey - cy * 16f, ex + cx * 16f, ey + cy * 16f, false);
        Lines.line(ex - px * 9f, ey - py * 9f, ex + px * 9f, ey + py * 9f, false);
        Fill.circle(ex, ey, 3.2f * fade);

        Draw.blend();
        Draw.reset();

        Drawf.light(ex, ey, 26f * fade, colors[1], 0.4f * fade);
    }
}
