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
import arc.math.Rand;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Mover;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.UnitController;
import mindustry.game.Team;
import mindustry.gen.Bullet;
import mindustry.gen.Entityc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.mod.Mods;
import mindustry.world.blocks.defense.turrets.Turret;

/**
 * 朗基努斯之枪弹体 (Longinus Spear BulletType)。
 *
 * <p>整张枪贴图就是飞行中的子弹, 除此之外不额外画任何光效。
 * 关键行为:</p>
 * <ol>
 *   <li><b>中途不碰撞</b>: {@code collides = false}, 飞行途中穿过单位、建筑、地形;</li>
 *   <li><b>只打击锁定目标</b>: 开火瞬间把炮台的 {@code target} 记到 {@code b.data},
 *       每帧朝该单位转向, 只有真正碰到它才结算;</li>
 *   <li>命中后把枪钉在目标身上, 并播放参考 FlameOut 消沉大招的猩红碎片粒子。</li>
 * </ol>
 *
 * <p>★ 贴图来源: 只认 {@code assets/sprites/despondency-spear.png} (枪尖朝右, 尺寸不写死在代码里)。
 * 炮台与子弹共用这一张图、同一套绘制逻辑。</p>
 *
 * <p>★ 尺寸不写死: 解析时实测图片中<b>不透明区域</b>的包围盒, 长度按这块真实枪身换算,
 * 宽高比由图片自身决定。换一张枪图 (哪怕留白、宽高比不同) 也不需要改常数。</p>
 *
 * <p>★ 这张图<b>不能</b>用 {@code Core.atlas.find} 去拿: 模组内容在 {@code Mods.loadAsync}
 * 里会先在"临时图集"阶段被 {@code load()} 一次, 那时查到的 region 只是占位结果
 * (坐标属于新图集、纹理却还是旧图集), 一旦缓存下来就永远是一块纯色矩形。
 * 因此 {@link #spear()} 直接读模组内的 png 自己建纹理, 与图集完全解耦。</p>
 */
public class LonginusSpearBulletType extends BulletType {

    /**
     * 枪贴图: 唯一来源 {@code assets/sprites/despondency-spear.png}
     * (打包后位于 jar 根部的 {@code sprites/despondency-spear.png})。
     *
     * <p>★ 只认这一个文件: 之前还有一份 {@code longinus-spear.png} 副本作为候选,
     * 两份一旦不同步就会出现"炮台与子弹不是同一张图"的问题, 现已统一。</p>
     */
    private static final String spearPath = "sprites/despondency-spear.png";

    /** 解析结果缓存: 炮台和子弹共用同一份贴图。 */
    private static TextureRegion spearCache;
    /** 自己创建的纹理, 必须持有引用, 否则会被当成无主纹理回收。 */
    private static Texture spearDirect;
    /** 只尝试解析一次, 失败后不再每帧读盘。 */
    private static boolean spearTried;

    /**
     * 不透明区域 (真实枪身) 在图片里的像素范围, 解析贴图时测量。
     *
     * <p>★ 所有长度/缩放/对齐都以这块区域为准, 不再假设"枪身铺满整张画布"。
     * 换图后即使四周留白不同、宽高比变化, 炮台与子弹也会自动重新适配。</p>
     */
    private static float visibleMinX = 0f, visibleWidth = 1f, visibleCenterY = 0f;
    /** 解析出的图片宽高, 仅用于日志输出。 */
    private static int imageWidth = 1, imageHeight = 1;

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

        byte[] data = readBytes(spearPath);
        if (data == null) {
            Log.err("[Longinus] 枪贴图加载失败, 模组里找不到: @", spearPath);
            return null;
        }

        try {
            Pixmap pix = new Pixmap(data);

            // ★ 先量出真实枪身的范围, 再 bleed。
            //   bleed 只是给透明像素补 RGB, 不会透明/不透明, 所以先后顺序不影响测量结果。
            imageWidth = pix.width;
            imageHeight = pix.height;
            measureVisibleBounds(pix);

            // 线性过滤下给透明边缘补色, 避免枪身边缘发黑
            Pixmaps.bleed(pix, 2);

            spearDirect = new Texture(pix);
            spearDirect.setFilter(Texture.TextureFilter.linear);
            spearCache = new TextureRegion(spearDirect);
            pix.dispose();

            Log.info("[Longinus] 枪贴图直读 png: @ (@x@), 可见枪身宽 @ / @ 像素",
                spearPath, imageWidth, imageHeight, (int) visibleWidth, imageWidth);
            return spearCache;
        } catch (Throwable e) {
            Log.err("[Longinus] 解码枪贴图失败: @", spearPath, e);
        }

        return null;
    }

    /**
     * 测量不透明像素的包围盒 (真实枪身)。
     *
     * <p>alpha 阈值取 8, 避免半透明描边被算进来。整张图全透明时退化为整张画布,
     * 保证后续除零/越界不会发生。</p>
     */
    private static void measureVisibleBounds(Pixmap pix) {
        int minX = pix.width, maxX = -1, minY = pix.height, maxY = -1;

        for (int y = 0; y < pix.height; y++) {
            for (int x = 0; x < pix.width; x++) {
                // RGBA8888: 最低字节是 alpha
                if ((pix.get(x, y) & 0xff) > 8) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX < minX) {
            minX = 0;
            maxX = pix.width - 1;
            minY = 0;
            maxY = pix.height - 1;
        }

        visibleMinX = minX;
        visibleWidth = Math.max(maxX - minX + 1, 1);
        visibleCenterY = (minY + maxY) * 0.5f;
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
     * "可见枪身长度" → 画布缩放 (世界单位 / 像素)。
     *
     * <p>不再用"整张画布宽度"换算: 图片四周可能有留白, 直接按画布宽度算会让实际枪身
     * 比目标长度短一截。这里以实测的不透明区域宽度为基准, 宽高比则由图片自身决定。</p>
     *
     * @param visibleLength 期望的真实枪身长度 (世界单位, 1 格 = 8 单位)
     */
    public static float spearScale(float visibleLength) {
        return visibleLength / Math.max(visibleWidth, 1f);
    }

    /**
     * 按可见枪身锚点绘制整张枪贴图。
     *
     * <p>三个绘制点 (炮台握持、飞行中的子弹、钉在目标身上) 共用这一份逻辑,
     * 保证枪尖、枪尾、粗细在任何图片下都一致:</p>
     *
     * @param visibleLength 真实枪身长度 (世界单位)
     * @param anchorFrac    锚点在可见枪身上的位置: 0 = 枪尾, 1 = 枪尖
     * @param px,py         锚点要落到的世界坐标
     * @param ang           绘制角度 (弧度), 贴图枪尖朝 +x
     */
    public static void drawSpear(TextureRegion region, float visibleLength, float anchorFrac,
                                 float px, float py, float ang) {
        if (region == null || !region.found()) return;

        float scale = spearScale(visibleLength);
        float w = region.width * scale;
        float h = region.height * scale;

        // 锚点在图片中的像素位置
        float anchorPx = visibleMinX + anchorFrac * visibleWidth;
        // 画布中心相对锚点的偏移 (图片坐标, +y 向下)
        float dx = (region.width * 0.5f - anchorPx) * scale;
        float dy = (region.height * 0.5f - visibleCenterY) * scale;

        // 图片 +y 向下、世界 +y 向上, 所以 y 取反后再按角度旋转
        float ox = Angles.trnsx(ang, dx, -dy);
        float oy = Angles.trnsy(ang, dx, -dy);

        Draw.rect(region, px + ox, py + oy, w, h, ang);
    }

    /** 光效主色。 */
    public Color glowColor = Color.valueOf("f53036");
    /**
     * 飞行中枪的<b>可见枪身</b>长度 (世界单位, 1 格 = 8 单位)。160 ≈ 20 格。
     *
     * <p>★ 必须与炮台的 {@code spearLength} 保持一致: 用户要求"子弹尺寸和炮台上的朗枪一样大"。
     * 绘制时按贴图里不透明区域的实测宽度换算缩放, 宽高比跟随图片本身。</p>
     */
    public float drawLength = 160f;
    /**
     * 追踪锁定目标的转向速度 (度/帧)。
     *
     * <p>枪是从炮口直线飞出去的, 这里只做小幅修正, 让目标移动后仍然会被枪尖咬住;
     * 数值太大会让长枪在空中画出明显的弧线。</p>
     */
    public float homingTurnRate = 3f;
    /** 枪尖判定余量 (世界单位), 与目标自身半径相加后作为命中距离。 */
    public float hitRadius = 14f;
    /** 枪插在目标身上的残留时长 (tick)。 */
    public float stickTime = 15f * 60f;

    /** 命中爆发粒子 (参考 FlameOut 消沉的 desRailHit)。 */
    public Effect hitBurst;
    /** 插枪残留特效。 */
    public Effect stickEffect;

    public LonginusSpearBulletType(float speed, float damage) {
        super(speed, damage);

        // ★ 中途不碰撞: 穿过途中所有单位/建筑/地形, 只由 update() 手动结算锁定目标
        collides = false;
        collidesTiles = false;
        collidesGround = false;
        collidesAir = false;
        collidesTeam = false;
        pierce = true;
        pierceBuilding = true;

        targetBlocks = false;

        // 只在手动命中时才用得上, 不代表枪身粗细
        hitSize = 32f;

        // 命中表现全部自定义
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

        // ---------- 命中爆发: 猩红碎片 + 白色射线 + 冲击环 ----------
        // 参考 FlameOut 的 FlameFX.desRailHit (消沉大招命中时的粒子):
        // 每轮以命中方向为中心, 在 180° 扇形内喷出三角碎片, 扇角逐轮收窄、
        // 尺寸逐轮放大; 再叠一层白色细射线和一圈扩散冲击环。
        hitBurst = new Effect(80f, 1000f, e -> {
            Rand r = new Rand(e.id);
            float scl = e.data instanceof Float f ? f : 1f;

            Draw.blend(Blending.additive);

            // 第一层: 猩红三角碎片
            float ang = 180f, rscl = 0.7f * scl;
            Draw.color(glowColor);
            for (int i = 0; i < 5; i++) {
                int count = (int) (10 * rscl);
                for (int j = 0; j < count; j++) {
                    float fin = Mathf.curve(e.fin(), 0f, 1f - r.random(0.2f));
                    float rot = r.range(ang) + e.rotation;
                    float off = r.random(22f * rscl) + r.random(50f * Mathf.pow(rscl, 1.5f)) * Interp.pow4Out.apply(fin);
                    float sscl = r.random(0.7f, 1.2f);

                    float wid = 12f * sscl * rscl * (1f - Interp.pow4In.apply(fin));
                    float hei = 52f * sscl * Mathf.pow(rscl, 1.5f) * Interp.pow5Out.apply(fin);

                    float vx = Angles.trnsx(rot, off) + e.x;
                    float vy = Angles.trnsy(rot, off) + e.y;
                    Drawf.tri(vx, vy, wid, hei, rot);
                    Drawf.tri(vx, vy, wid, wid * 2.2f, rot + 180f);
                }

                ang *= 0.6f;
                rscl *= 1.5f;
            }

            // 第二层: 白色细射线
            ang = 180f;
            rscl = 0.5f * scl;
            Draw.color(glowColor, Color.white, e.fin());
            Lines.stroke(3f);
            for (int i = 0; i < 7; i++) {
                for (int j = 0; j < 12; j++) {
                    float fin = Mathf.curve(e.fin(), 0f, 1f - r.random(0.2f));
                    float rot = r.range(ang) + e.rotation;
                    float off = r.random(30f * rscl) + r.random(40f * Mathf.pow(rscl, 1.6f)) * Interp.pow5Out.apply(fin);
                    float len = r.random(20f, 40f) * Mathf.pow(rscl, 1.6f)
                        * Interp.sineOut.apply(Mathf.slope(Interp.pow5Out.apply(fin)));

                    float vx = Angles.trnsx(rot, off) + e.x;
                    float vy = Angles.trnsy(rot, off) + e.y;
                    Lines.lineAngle(vx, vy, rot, len, false);
                }

                ang *= 0.5f;
                rscl *= 1.5f;
            }

            // 第三层: 白环冲击波
            Draw.color(Color.white, 0.6f * e.fout());
            Lines.stroke(4f * e.fout());
            Lines.circle(e.x, e.y, 18f + Interp.pow2Out.apply(e.fin()) * (120f * scl + 30f));

            Draw.blend();
            Draw.reset();
        });

        // ---------- 钉在目标身上的枪 ----------
        // data 传入被命中的单位: 单位还活着就每帧跟着它画 (枪真的"插在身上"),
        // 单位已经消失则停在命中点, 作为插在残骸上的枪。
        stickEffect = new Effect(stickTime, 600f, e -> {
            TextureRegion region = spear();
            if (region == null || !region.found()) return;

            Unit unit = e.data instanceof Unit u && u.isAdded() && !u.dead() ? u : null;
            float px = unit != null ? unit.x : e.x;
            float py = unit != null ? unit.y : e.y;

            float out = e.fin();
            // 最后 15% 时间淡出
            float alpha = out > 0.85f ? (1f - out) / 0.15f : 1f;
            float ang = e.rotation;

            // 插入瞬间的"钉入"回弹: 极短时间往前多扎一点, 之后回位
            float punch = out < 0.02f ? (1f - out / 0.02f) * 16f : 0f;
            float tipX = px + Angles.trnsx(ang, punch);
            float tipY = py + Angles.trnsy(ang, punch);

            Draw.z(Layer.effect);
            Draw.color(Color.white);
            Draw.alpha(alpha);
            // 以可见枪尖为锚 (1f), 枪尖扎在目标身上, 枪身向后方延伸
            drawSpear(region, drawLength, 1f, tipX, tipY, ang);

            // 插入点的猩红余烬, 随时间熄灭
            Draw.blend(Blending.additive);
            Draw.color(glowColor, 0.55f * alpha * Mathf.clamp(1f - out * 1.2f));
            Fill.circle(px, py, 10f + 5f * Mathf.absin(Time.time, 5f, 1f));
            Draw.blend();
            Draw.reset();
        });
    }

    /**
     * 创建子弹时把炮台当前锁定的目标记到 {@code b.data}。
     *
     * <p>原版 {@code TurretBuild.shoot()} 只把目标<b>坐标</b>传给子弹, 不会传目标实体,
     * 所以这里从 owner (炮台建筑) 上把 {@code target} 取出来, 之后 {@link #update} 用它追踪。</p>
     */
    @Override
    public Bullet create(Entityc owner, Entityc shooter, Team team, float x, float y, float angle, float damage,
                         float velocityScl, float lifetimeScl, Object data, Mover mover, float aimX, float aimY,
                         Teamc target) {
        Bullet b = super.create(owner, shooter, team, x, y, angle, damage, velocityScl, lifetimeScl, data, mover, aimX, aimY, target);

        if (b != null && owner instanceof Turret.TurretBuild build && build.target instanceof Unit unit) {
            b.data = unit;
        }

        return b;
    }

    /**
     * 追踪 + 命中结算。
     *
     * <p>因为 {@code collides = false}, 原版不会替我们做任何碰撞; 这里每帧:</p>
     * <ol>
     *   <li>目标还活着 → 小幅修正速度方向;</li>
     *   <li>枪尖进入目标判定范围 → 结算命中并移除子弹。</li>
     * </ol>
     */
    @Override
    public void update(Bullet b) {
        super.update(b);

        Unit target = b.data instanceof Unit u && u.isAdded() && !u.dead() ? u : null;
        if (target == null) return;

        float speed = b.vel.len();
        b.vel.setAngle(Angles.moveToward(b.rotation(), b.angleTo(target), homingTurnRate * Time.delta));
        b.vel.setLength(speed);

        if (b.dst(target) <= hitRadius + target.hitSize / 2f) {
            hitTarget(b, target);
            b.remove();
        }
    }

    /** 命中结算: 冻结目标 → 插枪 → 粒子 → 伤害。 */
    protected void hitTarget(Bullet b, Unit unit) {
        float hx = b.x, hy = b.y, rot = b.rotation();

        // 1. 抹除行动能力 (玩家单位不剥夺控制权)
        if (!unit.isPlayer()) {
            FrozenController controller = new FrozenController();
            controller.unit(unit);
            unit.controller(controller);
            unit.vel.setZero();
        }

        // 2. 枪钉在目标身上 (data 传单位, 之后每帧跟着它画)
        if (stickEffect != null) stickEffect.at(hx, hy, rot, unit);

        // 3. 命中粒子 + 震屏 (粒子尺寸随目标体积小幅放大)
        if (hitBurst != null) {
            hitBurst.at(hx, hy, rot, Mathf.clamp(unit.hitSize / 40f, 1f, 2f));
        }
        Effect.shake(9f, 8f, hx, hy);

        // 4. 伤害: 走原版 hitEntity, 结算护盾/护甲/状态与事件
        hitEntity(b, unit, unit.health);
    }

    /** 只画朗枪贴图本身: 没有拖尾、没有枪尖高光、没有动态光源。 */
    @Override
    public void draw(Bullet b) {
        TextureRegion region = spear();
        if (region == null || !region.found()) {
            drawFallbackSpear(b);
            return;
        }

        Draw.z(Layer.bullet);
        Draw.color(Color.white);
        // 以可见枪尖为锚: 枪尖正好落在子弹位置上, 枪身随后方延伸
        drawSpear(region, drawLength, 1f, b.x, b.y, b.rotation());
        Draw.reset();
    }

    /** 缺图兜底: 一根细长光矛, 保证缺图也能看出是"枪"而不是圆点。 */
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
