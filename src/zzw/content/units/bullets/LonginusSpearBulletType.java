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

            imageWidth = pix.width;
            imageHeight = pix.height;
            measureVisibleBounds(pix);

            // ★ 关键: 把不透明像素的 alpha 全部压成 255 (alpha 二值化)。
            //   原图边缘带一圈抗锯齿的半透明像素, 缩小绘制时线性过滤会把整条细枪身
            //   稀释成半透明 —— 也就是玩家看到的"飞出去偏透明"。
            //   这里在加载期直接改贴图数据 (不改文件), 保证枪身任何位置都是实心像素。
            makeOpaque(pix);

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
     * alpha 二值化: 凡是"算得上枪身"的像素一律改成完全不透明。
     *
     * <p>阈值取 8 (与包围盒测量用的一致, 低于它的视为完全透明/噪声), 高于阈值的像素 alpha 直接置 255。</p>
     *
     * <p>为什么必须这么做: 枪身在贴图里只有 8~14 像素宽, 画到世界里要缩小约 4 倍,
     * 线性过滤会把这圈半透明边缘与内部像素混合, 整条细枪身都会变成半透明;
     * 二值化后边缘最差也是 0/1 的硬边, 不会再出现"整条看穿"的效果。</p>
     */
    private static void makeOpaque(Pixmap pix) {
        for (int y = 0; y < pix.height; y++) {
            for (int x = 0; x < pix.width; x++) {
                int c = pix.get(x, y);
                if ((c & 0xff) > 8) {
                    // RGBA8888: 保留 RGB, 把最低字节 (alpha) 置为 255
                    pix.set(x, y, (c & 0xffffff00) | 0xff);
                }
            }
        }
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
     * 飞行中枪的<b>可见枪身</b>长度 (世界单位, 1 格 = 8 单位)。224 ≈ 28 格。
     *
     * <p>★ 必须与炮台的 {@code spearLength} 保持一致: 用户要求"子弹尺寸和炮台上的朗枪一样大"。
     * 绘制时按贴图里不透明区域的实测宽度换算缩放, 宽高比跟随图片本身。</p>
     */
    public float drawLength = 224f;
    /**
     * 追踪锁定目标的转向速度 (度/帧)。
     *
     * <p>枪是从炮口直线飞出去的, 这里只做小幅修正, 让目标移动后仍然会被枪尖咬住;
     * 数值太大会让长枪在空中画出明显的弧线。</p>
     */
    public float homingTurnRate = 3f;
    /** 枪尖判定余量 (世界单位), 与目标自身半径相加后作为命中距离。 */
    public float hitRadius = 14f;
    /**
     * 枪插在目标身上的总时长 (tick)。12 秒 = 前 10 秒保持不透明, 最后 2 秒淡出。
     *
     * <p>见 {@code stickEffect} 里的 alpha 计算: 600 tick 开始变透明, 720 tick 完全消失。</p>
     */
    public float stickTime = 12f * 60f;

    /** 枪口焰 / 插枪余烬使用的深红色 (与朗枪枪身的暗红一致)。 */
    public Color spearRed = Color.valueOf("8c1010");

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

        // ★ 飞行中不带任何额外光效: 关掉子弹自带的动态光源
        lightOpacity = 0f;

        // 命中表现全部自定义
        hitEffect = Fx.none;
        despawnEffect = Fx.none;
        smokeEffect = Fx.none;
        // 枪口焰在 load() 里构建: 深红色, 不要原版 Fx.shootBig 的白色闪光
        shootEffect = null;
        hitColor = spearRed;
    }

    @Override
    public void load() {
        super.load();

        // ★ 这里不要调 spear(): 模组内容第一次 load() 发生在非绘制线程的临时图集阶段,
        //   建纹理可能拿不到 GL 上下文。贴图在第一帧 draw 时再延迟解析。

        // ---------- 枪口焰: 深红色, 不使用原版的白色闪光 ----------
        // 原版 Fx.shootBig 是一团白色爆闪, 出膛那一瞬会把枪身洗成半透明观感;
        // 这里换成与朗枪枪身一致的深红: 三片顺着枪管喷出的暗红焰 + 一点红晕。
        shootEffect = new Effect(13f, 120f, e -> {
            float f = e.fout();

            // 主焰: 顺着枪口方向的三角, 实心深红
            Draw.color(spearRed, 0.95f * f);
            for (int i = -1; i <= 1; i++) {
                float a = e.rotation + i * 8f;
                Drawf.tri(e.x, e.y, (8f + (i == 0 ? 3f : 0f)) * f, 28f * f, a);
            }

            // 枪口的暗红余晕
            Draw.blend(Blending.additive);
            Draw.color(spearRed, 0.45f * f);
            Fill.circle(e.x, e.y, 9f * (1f - f * 0.35f));
            Draw.blend();
            Draw.reset();
        });

        // ---------- 命中碎片: 从目标身上崩出来的小碎块 ----------
        // 要的是"单位被打穿、碎片飞出来"的手感, 不是一圈巨大的爆裂光环:
        // 从命中点向四周抛出十几块小块 (带自转 / 减速 / 轻微下坠),
        // 再补几根很短的白色火花和一圈很小的冲击环。
        hitBurst = new Effect(75f, 320f, e -> {
            Rand r = new Rand(e.id);
            float scl = e.data instanceof Float f ? f : 1f;

            // 单位碎片
            for (int i = 0; i < 16; i++) {
                float life = r.random(0.45f, 1f);
                float fin = Mathf.clamp(e.fin() / life);
                if (fin >= 1f) continue;

                float a = r.random(360f);
                float speed = r.random(1.4f, 4.2f);
                // 越飞越慢
                float dist = speed * e.time * (1f - 0.35f * fin);

                float px = e.x + Angles.trnsx(a, dist);
                float py = e.y + Angles.trnsy(a, dist) - 0.01f * e.time * e.time;

                float wid = r.random(3f, 6.5f) * scl;
                float hei = r.random(6f, 14f) * scl;
                float rot = a + e.time * r.random(-8f, 8f);

                Draw.color(i % 4 == 0 ? Color.white : glowColor, Mathf.clamp(1f - fin) * 0.95f);
                Drawf.tri(px, py, wid, hei, rot);
            }

            // 白色火花
            Draw.color(Color.white, 0.85f * e.fout());
            Lines.stroke(1.5f);
            for (int i = 0; i < 6; i++) {
                float a = r.random(360f);
                float dist = (8f + e.time * r.random(3.5f, 6.5f)) * scl;
                float px = e.x + Angles.trnsx(a, dist);
                float py = e.y + Angles.trnsy(a, dist);
                Lines.lineAngle(px, py, a, (5f + r.random(6f)) * Mathf.clamp(1f - e.fin()));
            }

            // 小冲击环 (半径只有几十单位, 不再铺满半个屏幕)
            Draw.color(glowColor, 0.45f * e.fout());
            Lines.stroke(2f * e.fout());
            Lines.circle(e.x, e.y, (8f + Interp.pow2Out.apply(e.fin()) * 26f) * scl);

            Draw.reset();
        });

        // ---------- 钉在目标身上的枪 ----------
        // data 传入被命中的单位: 单位还活着就每帧跟着它画 (枪真的"插在身上"),
        // 单位已经消失则停在命中点, 作为插在残骸上的枪。
        stickEffect = new Effect(stickTime, 600f, e -> {
            TextureRegion region = spear();
            if (region == null || !region.found()) return;

            StickData data = e.data instanceof StickData d ? d : null;
            Unit unit = data != null && data.unit.isAdded() && !data.unit.dead() ? data.unit : null;
            float px = unit != null ? unit.x : e.x;
            float py = unit != null ? unit.y : e.y;
            // 插入深度在命中瞬间就算好, 单位消失后也不会跳变
            float depth = data != null ? data.depth : 24f;

            // ★ 前 10 秒保持完全不透明, 之后 2 秒线性淡出, 到 stickTime (12 秒) 完全消失
            float alpha = Mathf.clamp((stickTime - e.time) / 120f);
            float ang = e.rotation;

            // 插入瞬间的"钉入"回弹: 极短时间往前多扎一点, 之后回位
            float fin = e.fin();
            float punch = fin < 0.02f ? (1f - fin / 0.02f) * 16f : 0f;
            // ★ 枪尖越过单位中心 depth, 整杆枪看起来是扎穿目标, 而不是搭在身上
            float tipX = px + Angles.trnsx(ang, depth + punch);
            float tipY = py + Angles.trnsy(ang, depth + punch);

            Draw.z(Layer.effect);
            Draw.color(Color.white);
            Draw.alpha(alpha);
            // 以可见枪尖为锚 (1f), 枪尖扎在目标身上, 枪身向后方延伸
            drawSpear(region, drawLength, 1f, tipX, tipY, ang);

            // 插入点的深红余烬, 与枪一起淡出
            Draw.blend(Blending.additive);
            Draw.color(spearRed, 0.5f * alpha);
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

        // 2. 枪钉在目标身上
        //    锚点用单位中心 (而不是子弹碰撞点, 后者在单位边缘), 这样单位死亡后
        //    特效停在 e.x/e.y 也不会突然往后跳半个身位
        if (stickEffect != null) {
            // 插入深度: 枪尖越过单位中心 60% 半径, 看起来是整杆枪扎穿目标
            float depth = Mathf.clamp(unit.hitSize * 0.6f, 14f, 60f);
            stickEffect.at(unit.x, unit.y, rot, new StickData(unit, depth));
        }

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

        // ★ 绘制前把状态彻底复位: 混合模式 / alpha / mixcol 一个都不留。
        //   之前炮台会临时打开 additive 并改 alpha, 残留会让刚出膛的枪发虚。
        Draw.reset();
        Draw.blend(Blending.normal);
        Draw.mixcol();
        Draw.z(Layer.bullet);
        Draw.color(Color.white);
        Draw.alpha(1f);
        // 只画一遍, 与炮台上的枪完全一致 (同一贴图 / 同一长度 / 同一朝向)
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
     * 插枪特效的数据: 目标单位 + 命中瞬间算好的插入深度。
     *
     * <p>单位死亡/被移除后, 特效仍然知道该用多深, 不会突然回缩。</p>
     */
    public static class StickData {
        public final Unit unit;
        /** 枪尖越过单位中心的距离 (世界单位)。 */
        public final float depth;

        public StickData(Unit unit, float depth) {
            this.unit = unit;
            this.depth = depth;
        }
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
