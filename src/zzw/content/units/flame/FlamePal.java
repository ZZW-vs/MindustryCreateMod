package zzw.content.units.flame;

import arc.graphics.Color;

/**
 * FlameOut 配色常量 (移植自 flame.FlamePal)。
 *
 * <p>消沉的激光、冲击波、处决演出都使用这里的红色系。
 * 仅保留消沉实际引用到的颜色，避免引入无关全局配色。</p>
 */
public class FlamePal{
    public static Color primary = new Color(0x9a75ffff);
    public static Color blood = new Color(0.95f, 0.95f, 0.95f);

    public static Color red = new Color(0xf53036ff);
    public static Color redLight = red.cpy().mul(2f);
    public static Color darkRed = new Color(0.5f, 0f, 0f);

    public static Color melt = new Color(0xffa20aff);
}
