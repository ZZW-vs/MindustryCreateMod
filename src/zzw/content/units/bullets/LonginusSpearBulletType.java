package zzw.content.units.bullets;

import arc.files.Fi;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.Pixmaps;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.UnitController;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.mod.Mods;

/**
 * 朗基努斯之枪弹体 (Longinus Spear BulletType)。
 *
 * <p>整张 {@code longinus-spear.png} 就是飞行中的子弹: 枪尖朝速度方向,
 * 身后拖红色光带。命中单位时:</p>
 * <ol>
 *   <li>把该单位的 AI 替换成空控制器 (永久不再移动 / 开火 / 逃跑);</li>
 *   <li>在命中点留下一杆"插在目标上"的枪影, 15 秒后淡出;</li>
 *   <li>播放血色冲击环 + 放射电弧 + 屏幕震动 (参考 FlameOut 消沉的大招);</li>
 *   <li>造成 100 万伤害 (外部配置), 不穿透, 不碰建筑。</li>
 * </ol>
 *
 * <p>★ 贴图来源: 枪图是 {@code assets/sprites/blocks/fend/turrets/longinus-spear.png}
 * (与 {@code assets/sprites/despondency-spear.png} 同一张图, 896x96, 枪尖朝右)。
 * 这张图<b>不能</b>用 {@code Core.atlas.find} 去拿: 模组内容在 {@code Mods.loadAsync}
 * 里会先在"临时图集"阶段被 {@code load()} 一次, 那时查到的 region 只是占位结果
 * (坐标属于新图集、纹理却还是旧图集), 一旦缓存下来就永远是一块纯色矩形。
 * 因此 {@link #spear()} 直接读模组内的 png 自己建纹理, 与图集完全解耦。</p>
 */
public class LonginusSpearBulletType extends BulletType {

    /** 枪贴图在模组文件里的候选路径 (assets/ 会被打进 jar 根部)。 */
    private static final String[] spearPaths = {
        "sprites/blocks/fend/turrets/longinus-spear.png",
        "sprites/despondency-spear.png"
    };

    /** 解析结果缓存: 炮台和子弹共用同一份贴图。 */
    private static TextureRegion spearCache;
    /** 自己创建的纹理, 必须持有引用, 否则会被当成无主纹理回收。 */
    private static Texture spearDirect;
    /** 只尝试解析一次, 失败后不再每帧读盘。 */
    private static boolean spearTried;

    /**
     * 获取枪贴图。
     *
     * <p>直接读模组内的 png 解码成纹理, <b>不经过图集</b>。原因见类注释:
     * 模组内容第一次 {@code load()} 时图集还是临时状态, 缓存下来的 region 会是坏的。</p>
     *
     * <p>★ 纹理创建必须发生在绘制线程上, 所以这里只在 {@code draw()} 里被首次调用,
     * 不要在 {@code load()} 里调用 (那时可能不在 GL 线程上)。</p>
     *
     * <p>返回 null 表示彻底失败 (此时调用方画一根光矛兜底)。</p>
     */
    public static TextureRegion spear() {
        if (spearCache != null) return spearCache;
        if (spearTried) return null;
        spearTried = true;

        for (String path : spearPaths) {
            byte[] data = readBytes(path);
            if (data == null) continue;

            try {
                Pixmap pix = new Pixmap(data);
                // 线性过滤下给透明边缘补色, 避免枪身边缘发黑
                Pixmaps.bleed(pix, 2);

                spearDirect = new Texture(pix);
                spearDirect.setFilter(Texture.TextureFilter.linear);
                spearCache = new TextureRegion(spearDirect);
                pix.dispose();

                Log.info("[Longinus] 枪贴图直接读 png: @ (@x@)", path, spearCache.width, spearCache.height);
                return spearCache;
            } catch (Throwable e) {
                Log.err("[Longinus] 解码枪贴图失败: @", path, e);
            }
        }

        Log.err("[Longinus] 枪贴图加载失败, 模组里找不到: @", String.join(" / ", spearPaths));
        return null;
    }

    /** 依次尝试从模组根目录和类加载器资源读取文件内容。 */
    private static byte[] readBytes(String path) {
        try {
            Mods.LoadedMod mod = Vars.mods == null ? null : Vars.mods.getMod("create");
            if (mod != null) {
                Fi file = mod.root.child(path);
                if (file.exists()) return file.readBytes();
            }
        } catch (Throwable ignored) {
        }

        try (java.io.InputStream is = LonginusSpearBulletType.class.getClassLoader().getResourceAsStream(path)) {
            return is == null ? null : is.readAllBytes();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /**
     * 按"世界长度"反算贴图缩放比例。
     *
     * <p>不再用固定像素系数: region.width 可能是图集缩水后的值, 也可能是原始像素,
     * 用固定系数会让同一份贴图在不同加载路径下相差 4 倍。这里只认最终画到世界里的长度。</p>
     *
     * @param region      枪贴图
     * @param worldLength 期望的世界长度 (1 格 = 8 单位)
     */
    public static float scaleFor(TextureRegion region, float worldLength) {
        return region.width <= 0 ? 1f : worldLength / region.width;
    }

    /** 光效主色。 */
    public Color glowColor = Color.valueOf("f53036");
    /**
     * 枪在世界里的实际长度 (世界单位, 1 格 = 8 单位)。128 ≈ 16 格。
     *
     * <p>★ 这里不要用"像素 × 固定系数": 贴图宽度可能来自图集缩水值, 也可能来自原始 896,
     * 固定系数会让同一张图在不同来源下相差数倍。改成按世界长度反算缩放比例
     * {@link #scaleFor}, 屏幕上的枪就始终是这个长度。</p>
     */
    public float drawLength = 128f;
    /** 身后光带长度 (世界单位)。 */
    public float trailLength = 110f;
    /** 动态光源半径。 */
    public float lightRadius = 90f;
    /** 枪插在目标上的残留时长 (tick)。 */
    public float stickTime = 15f * 60f;

    /** 命中冲击特效 (在 load() 中创建)。 */
    public Effect hitSpark;
    /** 插枪残留特效 (在 load() 中创建)。 */
    public Effect stickEffect;

    public LonginusSpearBulletType(float speed, float damage) {
        super(speed, damage);

        // 不穿透: 命中第一个单位就停下
        pierce = false;
        pierceBuilding = false;

        // 只和单位碰撞, 直接穿过建筑
        // 注意: BulletType.targetBlocks 只用于面板统计, 真正决定"是否撞建筑"的是 collidesTiles
        targetBlocks = false;
        collidesTiles = false;
        collidesGround = true;
        collidesAir = true;
        collidesTeam = false;

        // 命中判定半径 (世界单位, 1 格 = 32)
        hitSize = 24f;

        // 命中的表现全部由 hitEntity 自定义, 关掉默认的
        hitEffect = Fx.none;
        despawnEffect = Fx.none;
        shootEffect = Fx.shootBig;
        smokeEffect = Fx.none;
    }

    @Override
    public void load() {
        super.load();

        // ★ 这里不要调 spear(): 模组内容第一次 load() 发生在非绘制线程的临时图集阶段,
        //   建纹理可能拿不到 GL 上下文。贴图在第一帧 draw 时再延迟解析。

        // ---------- 命中冲击: 白闪 + 双环 + 放射电弧 + 顺向撕裂痕 ----------
        // 参考 FlameOut 消沉大招的 rail-hit 表现: 起手一帧白闪, 然后是扩散的双环,
        // 再加沿命中方向拉长的撕裂痕, 让"枪钉进单位"的冲击感更强。
        hitSpark = new Effect(38f, 260f, e -> {
            Draw.blend(Blending.additive);

            // 起手白闪 (只存在前 20% 时间)
            if (e.fin() < 0.2f) {
                Draw.color(Color.white, 0.9f * (1f - e.fin() / 0.2f));
                Fill.circle(e.x, e.y, 26f * (1f - e.fin() / 0.2f) + 8f);
            }

            // 外层白环
            Draw.color(Color.white, e.color, e.fin());
            Lines.stroke(4f * e.fout());
            Lines.circle(e.x, e.y, 6f + e.fin(Interp.pow2Out) * 90f);

            // 内层红环
            Draw.color(e.color, 0.85f * e.fout());
            Lines.stroke(2f * e.fout());
            Lines.circle(e.x, e.y, 4f + e.fin(Interp.pow2Out) * 130f);

            // 10 条放射电弧, 跟随命中方向旋转
            for (int i = 0; i < 10; i++) {
                float a = i * 36f + e.rotation;
                Lines.stroke(1.5f * e.fout());
                Lines.lineAngle(e.x, e.y, a, e.fin(Interp.pow2Out) * 70f);
            }

            // 顺向撕裂痕: 沿飞行方向拉长, 侧向随机扰动
            Lines.stroke(3f * e.fout());
            Draw.color(Color.white, 0.9f * e.fout());
            Lines.lineAngle(e.x, e.y, e.rotation, e.fin(Interp.pow3Out) * 150f);
            Draw.color(e.color, 0.7f * e.fout());
            Lines.lineAngle(e.x, e.y, e.rotation + 180f, e.fin(Interp.pow3Out) * 70f);

            Draw.blend();
            Draw.reset();
        });

        // ---------- 插在目标上的枪 ----------
        stickEffect = new Effect(stickTime, 420f, e -> {
            TextureRegion region = spear();
            if (region == null || !region.found()) return;

            float out = e.fin();
            // 最后 15% 时间淡出
            float alpha = out > 0.85f ? (1f - out) / 0.15f : 1f;

            float scale = scaleFor(region, drawLength);
            float w = region.width * scale;
            float h = region.height * scale;
            float ang = e.rotation;

            // 插入瞬间的"钉入"回弹: 前 10 帧枪身往前多扎一点, 之后回位
            float punch = out < 0.02f ? (1f - out / 0.02f) * 14f : 0f;

            // 枪尖正好落在命中点: 图片中心沿枪身方向后退半个枪长
            float cx = e.x - Angles.trnsx(ang, 0.5f * w - punch);
            float cy = e.y - Angles.trnsy(ang, 0.5f * w - punch);

            Draw.z(Layer.effect);
            Draw.color(Color.white);
            Draw.alpha(alpha);
            Draw.rect(region, cx, cy, w, h, ang);
            Draw.reset();

            // 枪插在单位身上: 插入点一小团红色余烬, 随时间缓慢熄灭
            Draw.blend(Blending.additive);
            Draw.color(glowColor, 0.55f * alpha * Mathf.clamp(1f - out * 1.2f));
            Fill.circle(e.x, e.y, 9f);
            Draw.blend();
            Draw.reset();
        });
    }

    /**
     * 命中单位: 先冻结 AI 并插枪, 再交给基类结算伤害。
     *
     * <p>注意顺序: 如果先结算 100 万伤害, 单位会立刻死亡,
     * 之后就没办法再读取它来替换控制器了。</p>
     */
    @Override
    public void hitEntity(Bullet b, Hitboxc entity, float health) {
        float hx = b.x, hy = b.y, rot = b.rotation();

        if (entity instanceof Unit unit && unit.isAdded()) {
            // 1. 删除 AI (玩家单位除外, 否则会剥夺玩家控制)
            if (!unit.isPlayer()) {
                FrozenController controller = new FrozenController();
                controller.unit(unit);
                unit.controller(controller);
            }

            // 2. 枪插在目标上
            if (stickEffect != null) stickEffect.at(hx, hy, rot, glowColor);

            // 3. 冲击特效 + 震屏
            if (hitSpark != null) hitSpark.at(hx, hy, rot, glowColor);
            Effect.shake(7f, 8f, hx, hy);
        }

        super.hitEntity(b, entity, health);
    }

    @Override
    public void draw(Bullet b) {
        // 贴图缺失时退化成一根光矛 (不是圆点), 保证缺图也能看出是"枪"
        TextureRegion region = spear();
        if (region == null || !region.found()) {
            drawFallbackSpear(b);
            return;
        }

        float scale = scaleFor(region, drawLength);
        float w = region.width * scale;
        float h = region.height * scale;

        Draw.z(Layer.bullet);

        // ===== 1. 拖尾: 沿轴向收束的两层光带 =====
        // 之前这里是一团 Fill.circle, 半径比枪身还粗, 视觉上会盖住细长的枪,
        // 看起来就成了"圆子弹"。改成纯粹的轴向光带, 枪的形状才透得出来。
        Draw.blend(Blending.additive);
        float tx = b.x - Angles.trnsx(b.rotation(), trailLength);
        float ty = b.y - Angles.trnsy(b.rotation(), trailLength);
        Draw.color(glowColor, 0.28f);
        Lines.stroke(h * 0.85f);
        Lines.line(tx, ty, b.x, b.y);
        Draw.color(glowColor, 0.6f);
        Lines.stroke(h * 0.3f);
        Lines.line(tx, ty, b.x, b.y);
        Draw.blend();
        Draw.reset();

        // ===== 2. 枪体本体 =====
        // ★ 贴图本身枪尖朝右 (+x), 所以直接用 b.rotation() 就能让枪尖指向飞行方向;
        //   不能再加 180°, 否则整杆枪是倒着飞的。
        // 图片中心整体后退 0.35 个枪长, 让枪尖略微领先碰撞点 (看起来是枪尖扎中目标)。
        float cx = b.x - Angles.trnsx(b.rotation(), w * 0.35f);
        float cy = b.y - Angles.trnsy(b.rotation(), w * 0.35f);
        Draw.color(Color.white);
        Draw.rect(region, cx, cy, w, h, b.rotation());
        Draw.reset();

        // ===== 3. 枪尖高光: 一小点, 不再是一大团 =====
        float tipX = b.x + Angles.trnsx(b.rotation(), w * 0.15f);
        float tipY = b.y + Angles.trnsy(b.rotation(), w * 0.15f);
        Draw.blend(Blending.additive);
        Draw.color(Color.white, 0.9f);
        Fill.circle(tipX, tipY, h * 0.14f);
        Draw.color(glowColor, 0.55f);
        Fill.circle(tipX, tipY, h * 0.32f);
        Draw.blend();
        Draw.reset();

        // ===== 4. 动态光源 =====
        Drawf.light(b.x, b.y, lightRadius, glowColor, 0.75f);
    }

    /** 缺图兜底: 一根细长光矛 + 白色内芯, 形状明确不是圆。 */
    protected void drawFallbackSpear(Bullet b) {
        float len = 64f;
        Draw.z(Layer.bullet);
        Draw.blend(Blending.additive);

        float bx = b.x - Angles.trnsx(b.rotation(), len);
        float by = b.y - Angles.trnsy(b.rotation(), len);

        Draw.color(glowColor, 0.7f);
        Lines.stroke(6f);
        Lines.line(bx, by, b.x, b.y);

        Draw.color(Color.white, 0.9f);
        Lines.stroke(2f);
        Lines.line(bx, by, b.x, b.y);

        Draw.blend();
        Draw.reset();
    }

    /**
     * 空控制器: 不移动、不瞄准、不开火。
     *
     * <p>Mindustry 的单位行为全部由 {@link UnitController#updateUnit()} 驱动,
     * 换成一个什么都不做的控制器, 单位就变成"活靶子"。接口里的其他方法
     * 都有默认实现, 因此只需要接管 {@code unit} 的读写。</p>
     */
    public static class FrozenController implements UnitController {
        private Unit unit;

        @Override
        public void unit(Unit unit) {
            this.unit = unit;
        }

        @Override
        public Unit unit() {
            return unit;
        }
    }
}
