package zzw.content.units.flame.unit.empathy;

import arc.graphics.g2d.Lines;
import arc.math.Mathf;

/**
 * 消沉魔法阵绘制辅助 (只从 flame.unit.empathy.EmpathySpawner 抽取两个静态方法)。
 *
 * <p>原类 354 行，是共鸣 BOSS 的生成器；消沉只用到"渐进圆 / 渐进星"两种
 * 线条绘制，因此这里不引入整个 EmpathySpawner。</p>
 */
public class EmpathySpawner{

    /** 渐进多边形星 (按 fin 逐段画出)。 */
    public static void progressiveStar(float x, float y, float rad, float rot, int count, int stellation, float fin){
        int c = Mathf.ceil(count * fin);
        for(int i = 0; i < c; i++){
            float r1 = 360f * (1f / count) * stellation * i + rot;
            float r2 = 360f * (1f / count) * stellation * (i + 1f) + rot;

            float rx1 = Mathf.cosDeg(r1) * rad + x, ry1 = Mathf.sinDeg(r1) * rad + y;
            float rx2 = Mathf.cosDeg(r2) * rad + x, ry2 = Mathf.sinDeg(r2) * rad + y;

            if(fin >= 1f || i < (c - 1)){
                Lines.line(rx1, ry1, rx2, ry2);
            }else{
                float f = fin < 1f ? (fin * count) % 1f : 1f;
                float lx = Mathf.lerp(rx1, rx2, f), ly = Mathf.lerp(ry1, ry2, f);
                Lines.line(rx1, ry1, lx, ly);
            }
        }
    }

    /** 渐进圆 (fin<1 时只画已展开的圆弧)。 */
    public static void progressiveCircle(float x, float y, float rad, float rot, float fin){
        if(fin < 0.9999f){
            if(fin < 0.001f) return;
            int r = Lines.circleVertices(rad * fin);
            Lines.beginLine();
            for(int i = 0; i < r; i++){
                float ang = (360f / (r - 1)) * fin * i + rot;
                float sx = Mathf.cosDeg(ang) * rad, sy = Mathf.sinDeg(ang) * rad;
                Lines.linePoint(sx + x, sy + y);
            }
            Lines.endLine();
        }else{
            Lines.circle(x, y, rad);
        }
    }

    private EmpathySpawner(){
    }
}
