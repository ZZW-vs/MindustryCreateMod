package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
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
 * <p>★ 状态逻辑非常简单, 只有两态:</p>
 * <ul>
 *   <li>装填中 ({@code reloadCounter < reload}) → 枪隐藏, 只留空枪座光效;</li>
 *   <li>装填完成 ({@code reloadCounter >= reload}) → 枪正常显示。</li>
 * </ul>
 * <p>不使用任何本地布尔状态, 完全由同步的 {@code reloadCounter} 推导,
 * 因此多人客户端表现一致。</p>
 */
public class LonginusTurret extends ItemTurret {

    /** 底座贴图: assets/sprites/blocks/fend/turrets/longinus-base.png (192x192, 原图按 6x6 绘制)。 */
    public TextureRegion baseRegion;

    /**
     * 枪的<b>可见枪身</b>长度 (世界单位, 1 格 = 8 单位)。192 ≈ 24 格。
     *
     * <p>★ 必须与弹体的 {@code drawLength} 保持一致 (子弹尺寸 = 炮台上的朗枪尺寸)。</p>
     *
     * <p>长度与缩放按贴图里不透明区域的实际像素宽度换算 (见
     * {@link LonginusSpearBulletType#spearScale}), 宽高比由图片自身决定;
     * 换图后无论四周留白怎么变, 屏幕上枪身的长度都固定是这个值。</p>
     */
    public float spearLength = 192f;
    /**
     * 握持点距枪尾的比例 (0 = 枪尾, 1 = 枪尖), 该点对准炮台中心。
     *
     * <p>0.35 = 枪尾略微露出底座后方, 大部分枪身朝前伸出, 更像"架在炮座上瞄准"。
     * ★ 贴图本身枪尖朝右 (+x), 代码里不再额外旋转 180°。</p>
     */
    public float spearPivot = 0.35f;
    /** 空枪座 / 能量环的主色。 */
    public Color scarColor = Color.valueOf("f53036");

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
        public float spearPresence = 1f;
        /** 枪是否已经射出去了 (true = 底座上只剩空枪座)。 */
        public boolean spearOut;
        /** 枪飞出去之后经过的时间 (tick)。 */
        public float spearTimer;

        @Override
        protected void shoot(BulletType type) {
            super.shoot(type);
            // 枪离体的瞬间直接隐藏, 不留拖影
            spearPresence = 0f;
            spearOut = true;
            spearTimer = 0f;
        }

        @Override
        public void updateTile() {
            super.updateTile();
            updateSpear();
        }

        /**
         * ★ 这里千万不能用 {@code reloadCounter >= reload} 判断!
         *
         * <p>基类 {@code ItemTurretBuild.updateTile()} 一旦装填完成又有目标,
         * 会立刻开火并把 {@code reloadCounter} 清零; 而本方法是在 {@code super} 之后调用的,
         * 所以永远读不到"装填完成"的那一帧 —— 枪会永久消失在底座上。</p>
         *
         * <p>改用本地计时器: 开火时清零, 每帧累加, 累加满 {@code reload} 枪才回到座上。</p>
         */
        protected void updateSpear() {
            if (spearOut) {
                spearTimer += Time.delta;
                if (spearTimer >= reload) {
                    spearOut = false;
                    spearTimer = 0f;
                }
            }

            // 0.25 的插值速度 ≈ 0.06 秒淡入/淡出, 干脆但不生硬
            spearPresence = Mathf.lerpDelta(spearPresence, spearOut ? 0f : 1f, 0.25f);
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
         * <p>贴图本身枪尖朝右 (+x), 绘制角度直接用 {@code rotation};
         * {@code spearPivot} 表示握持点在"可见枪身由尾到尖"上的比例,
         * 长度与粗细全部由 {@link LonginusSpearBulletType#drawSpear} 按贴图实际尺寸换算,
         * 与子弹使用同一套逻辑。</p>
         */
        protected void drawSpear() {
            TextureRegion region = LonginusSpearBulletType.spear();
            if (region == null || !region.found() || spearPresence <= 0.01f) return;

            Draw.color(Color.white);
            Draw.alpha(spearPresence);
            LonginusSpearBulletType.drawSpear(region, spearLength, spearPivot, x, y, rotation);
            Draw.alpha(1f);
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
