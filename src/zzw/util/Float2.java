package zzw.util;

/**
 * 两个 float 打包进一个 long 的工具 (PU132 unity.gen.Float2 等价移植,
 * PU 原版由注解处理器生成, 本项目直接手写)。
 *
 * <p>剧情系统用它在 Cutscene.Pos / 存档 StringMap 里以单值传递坐标。</p>
 *
 * @author PU132 原作, 移植: zzw
 */
public final class Float2{
    private Float2(){}

    public static float x(long float2){
        return Float.intBitsToFloat((int)(float2 >>> 0 & 0xFFFFFFFFL));
    }

    public static long x(long float2, float value){
        return float2 & 0xFFFFFFFF00000000L | (long)Float.floatToIntBits(value) << 0;
    }

    public static float y(long float2){
        return Float.intBitsToFloat((int)(float2 >>> 32 & 0xFFFFFFFFL));
    }

    public static long y(long float2, float value){
        return float2 & 0xFFFFFFFFL | (long)Float.floatToIntBits(value) << 32;
    }

    public static long construct(float x, float y){
        return (long)Float.floatToIntBits(x) << 0 | (long)Float.floatToIntBits(y) << 32;
    }
}
