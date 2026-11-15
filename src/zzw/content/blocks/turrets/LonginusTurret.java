package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Eachable;
import arc.util.Time;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.BuildPlan;
import mindustry.graphics.Layer;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import zzw.content.units.bullets.LonginusSpearBulletType;

/**
 * 朗基努斯炮台 (Longinus Turret)。
 *
 * <p>设计核心: <b>炮塔贴图本身就是那杆枪</b>。开火时整杆枪离开底座飞出去
 * (由 {@link zzw.content.units.bullets.LonginusSpearBulletType} 绘制成飞行中的枪),
 * 炮台上只剩一个空枪座; 直到装填完成, 枪才重新出现在底座上。</p>
 *
 * <p>为什么要自己重写 {@code draw()} 而不是用默认的 {@code DrawTurret}?
 * 默认绘制只在固定位置画一张炮塔贴图, 无法表达"枪离体"这个状态,
 * 也无法把长条形的枪按握持点对齐到炮台中心。</p>
 *
 * <p>★ 一个装填周期里的三个状态 (全部由同步的 {@code reloadCounter} 推导, 不加本地计时器):</p>
 * <ul>
 *   <li>开火后 0 ~ 35%: 枪在外面飞, 底座只剩空枪座光效;</li>
 *   <li>35% ~ 85%: 枪从前方飞回枪座, 带红色残影并淡入;</li>
 *   <li>85% ~ 100%: 蓄力, 枪沿枪管向后拉, 枪口红光变亮, 然后投出。</li>
 * </ul>
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
     * <p>0.35 = 枪尾略微露出底座后方, 大部分枪身朝前伸出, 更像"架在炮座上瞄准"。
     * ★ 贴图本身枪尖朝右 (+x), 代码里不再额外旋转 180°。</p>
     */
    public float spearPivot = 0.35f;
    /** 空枪座 / 能量环 / 拖影的主色。 */
    public Color scarColor = Color.valueOf("f53036");

    // ---------- 装填 / 蓄力动画参数 (全部按"装填进度"的比例, 不依赖具体 reload 数值) ----------

    /** 枪开始从前方飞回的装填进度。 */
    public float returnStart = 0.35f;
    /** 枪落回枪座的装填进度; 之后进入蓄力阶段。 */
    public float returnEnd = 0.85f;
    /** 蓄力(向后拉)开始的装填进度。 */
    public float chargeStart = 0.85f;
    /** 飞回动画的起点距离 (世界单位)。 */
    public float returnDistance = 260f;
    /** 蓄力时枪向后拉的最大距离 (世界单位)。 */
    public float chargeDistance = 34f;

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

        /** 枪的"在场度": 1 = 插在底座上, 0 = 已经发射出去。 */
        public float spearPresence = 0f;
        /** 飞回动画的进度 0..1 (1 = 已回到枪座)。 */
        public float returnT;
        /** 飞回动画的当前距离偏移 (世界单位, 正方向 = 枪口方向)。 */
        public float returnOffset;
        /** 蓄力进度 0..1 (1 = 已拉到最底, 马上投出)。 */
        public float chargeT;

        @Override
        protected void shoot(BulletType type) {
            super.shoot(type);
            // 枪离体的瞬间直接隐藏, 不留拖影
            spearPresence = 0f;
            returnT = 0f;
            returnOffset = returnDistance;
            chargeT = 0f;
        }

        @Override
        public void updateTile() {
            super.updateTile();
            updateSpear();
        }

        /**
         * 装填 / 蓄力动画, 完全由同步的 {@code reloadCounter} 推导 (多人一致)。
         *
         * <p>{@code reloadCounter} 从 0 涨到 {@code reload}, 刚开火时为 0, 可以再开火时为 1,
         * 令 {@code t = reloadCounter / reload}:</p>
         * <ul>
         *   <li>{@code 0 ~ 0.35}: 枪还在外面飞;</li>
         *   <li>{@code 0.35 ~ 0.85}: 枪从前方 260 单位处飞回枪座, 同时淡入并拖一条红色残影;</li>
         *   <li>{@code 0.85 ~ 1}: 蓄力, 枪沿着枪管向后拉 34 单位, 枪口红晕逐渐变亮。</li>
         * </ul>
         */
        protected void updateSpear() {
            float t = reload <= 0f ? 1f : Mathf.clamp(reloadCounter / reload);

            // 1. 飞回
            returnT = Mathf.clamp((t - returnStart) / (returnEnd - returnStart));
            returnOffset = returnT <= 0f ? returnDistance
                : (1f - Interp.pow2Out.apply(returnT)) * returnDistance;
            // 前 40% 的返回行程里淡入, 避免刚开火时在远处出现一个半透明的枪影
            spearPresence = Mathf.clamp(returnT * 2.5f);

            // 2. 蓄力: 最后 15% 装填向后拉枪
            chargeT = Mathf.clamp((t - chargeStart) / Math.max(1f - chargeStart, 0.001f));
        }

        /**
         * 完全接管炮台绘制: 底座 → 空枪座 (枪不在时) → 枪。
         */
        @Override
        public void draw() {
            // ---------- 底座 ----------
            // 底座贴图是按 6x6 画的, 这里按当前方块尺寸拉伸铺满, 与 create-longinus 同款
            Draw.z(Layer.block);
            Draw.rect(baseRegion, x, y, size * 8f, size * 8f);

            // ---------- 炮塔层 ----------
            Draw.z(Layer.turret);

            if (spearPresence < 0.995f) {
                drawEmptyMount();
            }

            drawSpear();

            Draw.reset();
        }

        /**
         * 绘制枪。
         *
         * <p>位置由两部分叠加: 飞回动画的 {@code returnOffset} (正值 = 偏前) 与
         * 蓄力的 {@code -chargeDistance} (向后拉)。整体仍以 {@code spearPivot} 为握持点。</p>
         */
        protected void drawSpear() {
            TextureRegion region = LonginusSpearBulletType.spear();
            if (region == null || !region.found() || spearPresence <= 0.01f) return;

            float ang = rotation;
            float offset = returnOffset - chargeDistance * Interp.pow2In.apply(chargeT);
            float px = x + Angles.trnsx(ang, offset);
            float py = y + Angles.trnsy(ang, offset);

            // ---------- 飞回途中的红色残影 ----------
            if (returnT > 0.01f && returnT < 0.99f) {
                Draw.z(Layer.turret - 1f);
                Draw.blend(Blending.additive);
                Draw.color(scarColor, 0.45f * (1f - returnT));
                Lines.stroke(5f);
                Lines.line(px, py, x, y);
                Draw.blend();
                Draw.reset();
            }

            Draw.z(Layer.turret);
            Draw.color(Color.white);
            Draw.alpha(spearPresence);
            // 连画两遍: 枪身很细, 单遍缩小绘制时边缘会被插值稀释, 叠一遍保证实心
            LonginusSpearBulletType.drawSpear(region, spearLength, spearPivot, px, py, ang);
            LonginusSpearBulletType.drawSpear(region, spearLength, spearPivot, px, py, ang);
            Draw.alpha(1f);

            // ---------- 蓄力时枪口的暗红光晕 ----------
            if (chargeT > 0.01f) {
                Draw.z(Layer.turret - 1f);
                Draw.blend(Blending.additive);
                Draw.color(scarColor, 0.55f * chargeT);
                Fill.circle(x, y, 12f + 22f * Interp.pow2In.apply(chargeT));
                Draw.blend();
            }

            Draw.reset();
        }

        /**
         * 空枪座: 中心一团脉动红光 + 4 条旋转的能量导轨。
         *
         * <p>只有枪不在时可见, 强度由 {@code 1 - spearPresence} 驱动。</p>
         */
        protected void drawEmptyMount() {
            float glow = 1f - spearPresence;
            float pulse = Mathf.absin(Time.time, 6f, 0.35f) + 0.65f;
            // 按方块尺寸等比放大 (原始数值是给 6x6 用的)
            float scl = size / 6f;

            Draw.z(Layer.turret - 1f);
            Draw.blend(Blending.additive);

            Draw.color(scarColor, 0.45f * glow);
            Fill.circle(x, y, (10f + 4f * pulse) * scl * glow);

            Draw.color(scarColor, 0.75f * glow);
            Lines.stroke(2f * scl);
            for (int i = 0; i < 4; i++) {
                float a = Time.time * 8f + i * 90f;
                Lines.lineAngle(x, y, a, 24f * scl);
            }

            Draw.blend();
            Draw.reset();
        }
    }
}
