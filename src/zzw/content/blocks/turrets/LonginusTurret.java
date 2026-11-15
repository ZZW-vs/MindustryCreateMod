package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Eachable;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.BuildPlan;
import mindustry.graphics.Layer;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import zzw.content.units.bullets.LonginusSpearBulletType;

/**
 * 朗基努斯炮台 (Longinus Turret)。
 *
 * <p>炮台贴图本身就是那杆枪: 底座 + 一杆固定架在枪座上的朗枪。
 * 开火时投出的枪由 {@link LonginusSpearBulletType} 用<b>同一张贴图、同一长度、同一朝向</b>绘制,
 * 生成点正好对齐炮台上那杆枪的枪尖 —— 于是看起来就是"炮台上那杆枪飞了出去"。</p>
 *
 * <p>★ 枪在炮台上<b>完全静止</b>: 没有飞回、没有后拉蓄力、没有拖影、没有开火位移。
 * 这与"枪离体后炮台只剩空枪座"的旧表现是两种设计, 现在固定为静态。</p>
 *
 * <p>为什么要自己重写 {@code draw()} 而不是用默认的 {@code DrawTurret}?
 * 默认绘制按贴图原始像素画, 而这里的底座原图只有 6x6 的分辨率, 需要按 15x15 的 footprint 拉伸;
 * 枪又需要按握持点 (而不是贴图中心) 对齐到炮台中心。</p>
 */
public class LonginusTurret extends ItemTurret {

    /** 底座贴图: assets/sprites/blocks/fend/turrets/longinus-base.png (192x192, 原图按 6x6 绘制)。 */
    public TextureRegion baseRegion;

    /**
     * 枪的<b>可见枪身</b>长度 (世界单位, 1 格 = 8 单位)。224 ≈ 28 格。
     *
     * <p>★ 必须与弹体的 {@code drawLength} 保持一致 (子弹尺寸 = 炮台上的朗枪尺寸)。</p>
     *
     * <p>长度与缩放按贴图里不透明区域的实际像素宽度换算 (见
     * {@link LonginusSpearBulletType#spearScale}), 宽高比由图片自身决定;
     * 换图后无论四周留白怎么变, 屏幕上枪身的长度都固定是这个值。</p>
     */
    public float spearLength = 224f;

    /**
     * 握持点距枪尾的比例 (0 = 枪尾, 1 = 枪尖), 该点对准炮台中心。
     *
     * <p>0.30 = 枪尾刚好收在底座后缘附近 (0.30 × 224 ≈ 67 单位, 底座半宽 60)。
     * ★ 贴图本身枪尖朝右 (+x), 代码里不再额外旋转 180°。</p>
     */
    public float spearPivot = 0.30f;

    public LonginusTurret(String name) {
        super(name);
    }

    @Override
    public void load() {
        super.load();
        baseRegion = Core.atlas.find(name + "-base");

        // 显式加载弹药贴图: 部分版本不会自动调用 BulletType.load()
        if (ammoTypes != null) {
            for (BulletType type : ammoTypes.values()) {
                type.load();
            }
        }

        // ★ 不在这里解析枪贴图: 模组内容第一次 load() 还处在临时图集阶段,
        //   此时拿到的 region 是坏坐标, 而且建纹理也可能不在 GL 线程上。
        arc.util.Log.info("[Longinus] base found=@", baseRegion.found());
    }

    @Override
    public void init() {
        super.init();

        // ★ 子弹从枪尖生成, 而不是炮台中心:
        //   炮台上那杆枪的枪尖位于中心前方 (1 - spearPivot) * spearLength 处;
        //   开火那一帧子弹出现在同一个点、同一角度、同一尺寸, 看起来就是这杆枪飞了出去。
        //   Turret 的 shootY 正是沿枪管方向的炮口偏移。
        shootY = (1f - spearPivot) * spearLength;
        shootX = 0f;
    }

    /**
     * 放置预览。
     *
     * <p>默认实现 {@code drawDefaultPlanRegion} 是按贴图原始像素画的 (底座原图只有 6x6 的分辨率),
     * 于是预览里永远是旧的 6x6 小底座, 放下之后才是 15x15 —— 这里改成和建成后完全相同的画法:
     * 底座按方块 footprint 拉伸, 枪按握持点画在中心。</p>
     *
     * <p>颜色/透明度由外层 {@code drawPlan} 的 {@code Draw.mixcol/alpha} 决定, 这里不再改。</p>
     */
    @Override
    public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list) {
        TextureRegion base = baseRegion != null && baseRegion.found() ? baseRegion : region;
        float scl = Mathf.clamp(plan.animScale, 0.01f, 1f);
        float drawSize = size * 8f * scl;

        Draw.rect(base, plan.drawx(), plan.drawy(), drawSize, drawSize);

        TextureRegion spear = LonginusSpearBulletType.spear();
        if (spear != null && spear.found()) {
            LonginusSpearBulletType.drawSpear(spear, spearLength * scl, spearPivot,
                plan.drawx(), plan.drawy(), plan.rotation * 90f);
        }
    }

    public class LonginusBuild extends ItemTurretBuild {

        /**
         * 完全接管炮台绘制: 底座 (按 footprint 拉伸) → 固定在枪座上的枪。
         *
         * <p>枪的位置、角度、透明度都是常量, 不随装填进度变化, 也不在开火时位移。</p>
         */
        @Override
        public void draw() {
            // ---------- 底座 ----------
            Draw.z(Layer.block);
            Draw.rect(baseRegion, x, y, size * 8f, size * 8f);

            // ---------- 枪 ----------
            TextureRegion spear = LonginusSpearBulletType.spear();
            if (spear != null && spear.found()) {
                Draw.z(Layer.turret);
                Draw.color(Color.white);
                Draw.alpha(1f);
                LonginusSpearBulletType.drawSpear(spear, spearLength, spearPivot, x, y, rotation);
            }

            Draw.reset();
        }
    }
}
