package zzw.content.units.flame.graphics;

import arc.graphics.g2d.Fill;

/**
 * 消沉用到的图形工具 (裁自 flame.graphics.GraphicUtils)。
 *
 * <p>原版类包含 3D 缓存批处理/着色器，与消沉无关；这里只保留
 * {@link #verts} 共享顶点数组与 {@link #diamond} 菱形绘制。</p>
 */
public class GraphicUtils{
    /** 碎片/线段绘制复用的顶点数组。 */
    public static float[] verts = new float[4 * 6];

    /** 以 (x,y) 为中心绘制一个旋转的菱形（四个三角）。 */
    public static void diamond(float x, float y, float width, float height, float rotation){
        float w = width / 2f, h = height / 2f;
        float cos = arc.math.Mathf.cosDeg(rotation), sin = arc.math.Mathf.sinDeg(rotation);

        float x1 = x - w * cos + h * sin, y1 = y - w * sin - h * cos;
        float x2 = x + w * cos + h * sin, y2 = y + w * sin - h * cos;
        float x3 = x + w * cos - h * sin, y3 = y + w * sin + h * cos;
        float x4 = x - w * cos - h * sin, y4 = y - w * sin + h * cos;

        Fill.tri(x1, y1, x2, y2, x3, y3);
        Fill.tri(x1, y1, x3, y3, x4, y4);
    }
}
