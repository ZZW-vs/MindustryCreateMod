package zzw.content.units.bullets;

import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.gen.Bullet;

/**
 * 符文导弹 (opticaecus 两侧 doeg 发射器)。
 *
 * <p>在原版导弹贴图之外, 弹尾叠加一对"撕裂"符线 (左右各一条斜向短划),
 * 像空间被弹体划开的口子; 与 opticaecus 主炮的六刻度魔法阵、chronos 的
 * 钟面、ravager 的菱形符文刻意区分开。</p>
 *
 * <p>每帧只增加 4 条线段, 即使两侧 20 发齐射也只多 80 个图元。</p>
 *
 * @author zzw
 */
public class RunicMissileBulletType extends MissileBulletType{

    public RunicMissileBulletType(float speed, float damage){
        super(speed, damage);
    }

    @Override
    public void draw(Bullet b){
        super.draw(b);

        float rot = b.rotation();
        float cx = Mathf.cosDeg(rot), cy = Mathf.sinDeg(rot);
        float px = -cy, py = cx;
        float fade = b.fout();

        Draw.blend(Blending.additive);
        Draw.color(backColor);
        Draw.alpha(0.55f * fade);
        Lines.stroke(1.5f * fade);

        // 尾部撕裂符: 左右各一条, 长度随机感由弹体 ID 提供
        float jitter = Mathf.randomSeed(b.id, -0.35f, 0.35f);
        for(int s : Mathf.signs){
            float ox = b.x - cx * 9f + px * 3.5f * s;
            float oy = b.y - cy * 9f + py * 3.5f * s;
            Lines.line(ox, oy,
                b.x - cx * (19f + jitter * 6f) + px * 8f * s,
                b.y - cy * (19f + jitter * 6f) + py * 8f * s, false);
        }

        Draw.blend();
        Draw.reset();
    }
}
