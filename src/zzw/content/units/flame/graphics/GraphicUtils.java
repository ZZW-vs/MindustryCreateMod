package zzw.content.units.flame.graphics;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.geom.Vec2;
import arc.math.geom.Vec3;
import arc.struct.FloatSeq;

/**
 * 消沉用到的图形工具 (裁自 flame.graphics.GraphicUtils)。
 *
 * <p>原版类包含 3D 缓存批处理/着色器，与消沉无关；这里只保留
 * {@link #verts} 共享顶点数组、{@link #diamond} 菱形绘制与
 * {@link #drawShockWave} 冲击波。</p>
 *
 * <p>★ 与 FO 原版的差异: 原版 drawShockWave 复用静态 {@code Vec3/FloatSeq}，
 * 本移植改为方法内局部对象，避免全局可变状态被并发/重入覆盖。</p>
 */
public class GraphicUtils{
    /** 碎片/线段绘制复用的顶点数组。 */
    public static float[] verts = new float[4 * 6];

    /** 以 (x,y) 为中心绘制一个旋转的菱形（四顶点 quad）。 */
    public static void diamond(float x, float y, float width, float length, float rotation){
        float tx1 = Angles.trnsx(rotation + 90f, width), ty1 = Angles.trnsy(rotation + 90f, width),
                tx2 = Angles.trnsx(rotation, length), ty2 = Angles.trnsy(rotation, length);
        Fill.quad(x + tx1, y + ty1,
                x + tx2, y + ty2,
                x - tx1, y - ty1,
                x - tx2, y - ty2);
    }

    /**
     * 透视环形冲击波 (FO 的 3D 版本, 用顶点切片 + z 排序近似)。
     *
     * @param rx/ry/rz 三个轴的倾斜角度
     * @param size     环半径
     * @param width    环带宽
     * @param iter     切片数
     * @param zRange   每个切片相对当前 z 的偏移
     */
    public static void drawShockWave(float x, float y, float rx, float ry, float rz, float size, float width, int iter, float zRange){
        float off = 360f / iter;
        float zz = Draw.z();

        Vec2 v2 = new Vec2();
        Vec3 v = new Vec3();
        FloatSeq tf = new FloatSeq(4 * 2);

        for(int i = 0; i < iter; i++){
            float angle1 = off * i;
            float angle2 = off * (i + 1);
            float z = 0f;

            tf.clear();
            for(int j = 0; j < 4; j++){
                float w = j == 0 || j == 3 ? width : -width;
                float a = j <= 1 ? angle1 : angle2;

                v2.trns(a, size + w);
                v.set(v2.x, v2.y, 0f).rotate(Vec3.X, rx).rotate(Vec3.Y, ry).rotate(Vec3.Z, rz);
                float sz = 700f / (700f - v.z);
                v.x *= sz;
                v.y *= sz;

                z += v.z;
                tf.add(v.x + x, v.y + y);
            }

            Draw.z((z < 0f ? -zRange : zRange) + zz);
            Fill.polyBegin();
            for(int j = 0; j < 4; j++){
                Fill.polyPoint(tf.items[j * 2], tf.items[j * 2 + 1]);
            }
            Fill.polyEnd();
        }
        Draw.z(zz);
    }
}
