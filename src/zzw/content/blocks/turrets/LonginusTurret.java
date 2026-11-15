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
 * <p>★ 一个装填周期被切成五段, 全部由同步的 {@code reloadCounter} 推导 (不加本地计时器),
 * 位移是一条连续曲线, 绘制阶段不做任何跳变处理:</p>
 * <ul>
 *   <li>0 ~ 35%: 枪在外飞, 底座只剩空枪座光效;</li>
 *   <li>35% ~ 62%: 枪从前方 340 单位处缓出滑回 (前 12% 淡入, 带加色拖影);</li>
 *   <li>62% ~ 71%: 落座回弹, 枪往后顿一下再归位, 同时一圈红色冲击环扩散;</li>
 *   <li>86% ~ 96.5%: 蓄力后拉 22 单位, 枪座红光与内收能量环渐亮;</li>
 *   <li>96.5% ~ 100%: 快速回冲到基准位 —— 到位的同一帧开火, 子弹从枪尖位置生成,
 *       与炮台上的枪完全重合, 因此看不出是两段动画拼起来的。</li>
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
     * <p>0.30 = 枪尾刚好收在底座后缘附近 (0.30 × 224 ≈ 67 单位, 底座半宽 60),
     * 蓄力后拉时枪尾也不会甩出底座太远, 避免看起来像戳穿了炮座。
     * ★ 贴图本身枪尖朝右 (+x), 代码里不再额外旋转 180°。</p>
     */
    public float spearPivot = 0.30f;
    /** 空枪座 / 能量环 / 拖影的主色。 */
    public Color scarColor = Color.valueOf("f53036");

    // ---------- 动画分段参数 (全部按"装填进度"的比例, 不依赖具体 reload 数值) ----------
    // 一个装填周期被切成五段, 每段都首尾相接 (上一段的终点就是下一段起点), 不会跳变:
    //   0 ~ returnStart        枪在外
    //   returnStart ~ returnEnd 飞回       (缓出减速, 前 12% 淡入)
    //   returnEnd ~ dockTime    落座回弹   (往后顿一下再回到位)
    //   chargeStart ~ chargeEnd 蓄力后拉   (加速后拉)
    //   chargeEnd ~ 1           投出前突   (快速回冲到基准位, 该帧正好开火)

    /** 枪开始从前方飞回的装填进度。 */
    public float returnStart = 0.35f;
    /** 枪落回枪座的装填进度。 */
    public float returnEnd = 0.62f;
    /** 蓄力(向后拉)开始的装填进度。 */
    public float chargeStart = 0.86f;
    /** 蓄力结束、开始向前投出的装填进度。 */
    public float chargeEnd = 0.965f;
    /** 飞回动画的起点距离 (世界单位)。 */
    public float returnDistance = 340f;
    /** 蓄力时枪向后拉的最大距离 (世界单位)。 */
    public float chargeDistance = 22f;
    /** 落座回弹向后的最大偏移 (世界单位)。 */
    public float dockSettle = 7f;
    /** 落座回弹占装填进度的比例。 */
    public float dockTime = 0.09f;

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

        // ★ 子弹从"枪尖"生成, 而不是炮台中心。
        //   炮台上画的枪, 枪尖位于中心前方 (1 - spearPivot) * spearLength 处;
        //   投出那一帧炮台上的枪消失、子弹出现在同一个点上, 中间没有任何跳变。
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

        /** 枪的"在场度": 1 = 完全插在枪座上, 0 = 不在底座上。 */
        public float spearPresence;
        /** 枪相对基准位置沿枪管的滑动量 (正 = 偏枪口方向)。 */
        public float spearOffset = returnDistance;
        /** 飞回进度 0..1 (1 = 已到位)。 */
        public float returnT;
        /** 落座回弹进度 0..1。 */
        public float dockT;
        /** 蓄力进度 0..1, 同时驱动枪口蓄力光效。 */
        public float chargeT;

        @Override
        protected void shoot(BulletType type) {
            super.shoot(type);
            // 枪离体的这一帧直接切到"在外"状态: 子弹已经在同一帧、同一位置 (枪尖) 出现,
            // 这里如果再做淡出, 反而会看到两杆枪叠在一起。
            spearPresence = 0f;
            spearOffset = returnDistance;
            returnT = 0f;
            dockT = 0f;
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
         * <p>令 {@code t = reloadCounter / reload} (刚开火 = 0, 可以再开火 = 1), 五段严格首尾相接:</p>
         * <ol>
         *   <li>{@code 0 ~ returnStart}: 枪在外, 只剩空枪座;</li>
         *   <li>{@code returnStart ~ returnEnd}: 从 {@code returnDistance} 处缓出滑回, 前 12% 淡入;</li>
         *   <li>{@code returnEnd ~ returnEnd+dockTime}: 落座回弹 (先往后顿 {@code dockSettle} 再归位);</li>
         *   <li>{@code chargeStart ~ chargeEnd}: 蓄力后拉 (加速), 枪口光晕变亮;</li>
         *   <li>{@code chargeEnd ~ 1}: 快速回冲到基准位 —— 到位的同一帧正好开火,
         *       枪尖位置与子弹生成点重合, 因此看不出切换。</li>
         * </ol>
         */
        protected void updateSpear() {
            float t = reload <= 0f ? 1f : Mathf.clamp(reloadCounter / reload);

            // ---------- 1. 枪还在外面 ----------
            if (t < returnStart) {
                spearPresence = 0f;
                spearOffset = returnDistance;
                returnT = 0f;
                dockT = 0f;
                chargeT = 0f;
                return;
            }

            // ---------- 2. 飞回 ----------
            if (t < returnEnd) {
                returnT = Mathf.clamp((t - returnStart) / Math.max(returnEnd - returnStart, 0.001f));
                spearOffset = returnDistance * (1f - Interp.pow3Out.apply(returnT));
                // 前 12% 行程淡入: 避免在远处凭空冒出一杆实心长枪
                spearPresence = Mathf.clamp(returnT / 0.12f);
                dockT = 0f;
                chargeT = 0f;
                return;
            }

            // ---------- 已经落座 ----------
            returnT = 1f;
            spearPresence = 1f;

            // 3. 落座回弹: 往后轻轻一顿再回到基准位
            dockT = Mathf.clamp((t - returnEnd) / Math.max(dockTime, 0.001f));
            float settle = dockT < 1f ? -dockSettle * Mathf.sin(dockT * Mathf.PI) : 0f;

            if (t < chargeEnd) {
                // 4. 蓄力后拉
                chargeT = Mathf.clamp((t - chargeStart) / Math.max(chargeEnd - chargeStart, 0.001f));
                spearOffset = settle - chargeDistance * Interp.pow2In.apply(chargeT);
            } else {
                // 5. 投出前突: 从拉到底的位置快速回到基准位, t = 1 时正好为 0
                chargeT = 1f;
                float thrust = Mathf.clamp((t - chargeEnd) / Math.max(1f - chargeEnd, 0.001f));
                spearOffset = settle - chargeDistance * (1f - Interp.pow3Out.apply(thrust));
            }
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
         * <p>位置只由 {@code spearOffset} 决定 (飞回 / 回弹 / 蓄力 / 前突都在 {@link #updateSpear} 里
         * 合成成一条连续的位移曲线), 绘制阶段不再做任何跳变处理, 避免出现"瞬移"。</p>
         */
        protected void drawSpear() {
            TextureRegion region = LonginusSpearBulletType.spear();
            if (region == null || !region.found() || spearPresence <= 0.01f) return;

            float ang = rotation;
            float px = x + Angles.trnsx(ang, spearOffset);
            float py = y + Angles.trnsy(ang, spearOffset);

            // ---------- 飞回途中的能量拖影 (加色, 明确是"冲回来"的运动感) ----------
            if (returnT > 0.02f && returnT < 0.98f) {
                Draw.z(Layer.turret - 1f);
                Draw.blend(Blending.additive);
                for (int i = 0; i < 3; i++) {
                    float a = ang + (i - 1) * 2.4f;
                    float len = 46f + i * 30f;
                    Draw.color(scarColor, (0.34f - i * 0.09f) * (1f - returnT));
                    Lines.stroke(5f - i);
                    Lines.line(px, py, px + Angles.trnsx(a, len), py + Angles.trnsy(a, len));
                }
                Draw.blend();
                Draw.reset();
            }

            // ---------- 落座冲击环 ----------
            if (dockT > 0f && dockT < 1f) {
                float f = 1f - dockT;
                Draw.z(Layer.turret - 1f);
                Draw.blend(Blending.additive);
                Draw.color(scarColor, 0.55f * f);
                Lines.stroke(3f * f + 1f);
                Lines.circle(x, y, 14f + 44f * Interp.pow2Out.apply(dockT));
                Draw.blend();
                Draw.reset();
            }

            // ---------- 枪本体 ----------
            Draw.z(Layer.turret);
            Draw.color(Color.white);
            Draw.alpha(spearPresence);
            // 连画两遍: 枪身很细, 单遍缩小绘制时边缘会被插值稀释, 叠一遍保证实心
            LonginusSpearBulletType.drawSpear(region, spearLength, spearPivot, px, py, ang);
            LonginusSpearBulletType.drawSpear(region, spearLength, spearPivot, px, py, ang);
            Draw.alpha(1f);

            // ---------- 蓄力: 枪座红光渐亮 + 一圈向内收束的能量环 ----------
            if (chargeT > 0.01f) {
                float in = Interp.pow2In.apply(chargeT);
                Draw.z(Layer.turret - 1f);
                Draw.blend(Blending.additive);

                Draw.color(scarColor, 0.5f * chargeT);
                Fill.circle(x, y, 10f + 16f * in);

                Draw.color(scarColor, 0.35f * chargeT);
                Lines.stroke(2f + 2f * chargeT);
                Lines.circle(x, y, 78f - 52f * in);

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
