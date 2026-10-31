package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.bullet.BulletType;
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

    /** 底座贴图: assets/sprites/blocks/fend/turrets/longinus-base.png (192x192, 6x6)。 */
    public TextureRegion baseRegion;

    /** 枪的绘制缩放 (1f = 原始像素)。0.34 → 约 9.5 格长。 */
    public float spearScale = 0.34f;
    /**
     * 握持点距枪尾的比例 (0 = 枪尾, 1 = 枪尖), 该点对准炮台中心。
     *
     * <p>0.18 表示炮台从靠近枪尾的地方握着它, 所以整杆枪几乎都朝前伸出。
     * ★ 贴图本身枪尖朝右 (+x), 代码里不再额外旋转 180°。</p>
     */
    public float spearPivot = 0.18f;
    /** 空枪座 / 能量环的主色。 */
    public Color scarColor = Color.valueOf("f53036");

    public LonginusTurret(String name) {
        super(name);
    }

    @Override
    public void load() {
        super.load();
        baseRegion = Core.atlas.find(name + "-base");

        // 枪贴图统一交给弹体的解析器: 这张图实测进不了图集, 解析器会自动退回直接读 png
        TextureRegion spearRegion = LonginusSpearBulletType.spear();

        // 显式加载弹药贴图: 部分版本不会自动调用 BulletType.load()
        if (ammoTypes != null) {
            for (BulletType type : ammoTypes.values()) {
                type.load();
            }
        }

        arc.util.Log.info("[Longinus] base found=@ spear=@", baseRegion.found(), spearRegion != null && spearRegion.found());
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
            Draw.z(Layer.block);
            Draw.rect(baseRegion, x, y);

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
         * <p>贴图本身枪尖朝右 (+x), 所以绘制角度直接用 {@code rotation};
         * {@code w * (0.5f - spearPivot)} 把图片中心从握持点推开,
         * 使旋转围绕握持点而不是图片几何中心。</p>
         */
        protected void drawSpear() {
            TextureRegion region = LonginusSpearBulletType.spear();
            if (region == null || !region.found() || spearPresence <= 0.01f) return;

            float w = region.width * spearScale;
            float h = region.height * spearScale;

            float ang = rotation;
            float off = w * (0.5f - spearPivot);
            float cx = x + Angles.trnsx(ang, off);
            float cy = y + Angles.trnsy(ang, off);

            Draw.color(Color.white);
            Draw.alpha(spearPresence);
            Draw.rect(region, cx, cy, w, h, ang);
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

            Draw.z(Layer.turret - 1f);
            Draw.blend(Blending.additive);

            Draw.color(scarColor, 0.45f * glow);
            Fill.circle(x, y, (10f + 4f * pulse) * glow);

            Draw.color(scarColor, 0.75f * glow);
            Lines.stroke(2f);
            for (int i = 0; i < 4; i++) {
                float a = Time.time * 8f + i * 90f;
                Lines.lineAngle(x, y, a, 24f);
            }

            Draw.blend();
            Draw.reset();
        }
    }
}
