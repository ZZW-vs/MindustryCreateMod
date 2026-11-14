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
 * <p>整张 {@code longinus-spear.png} 就是飞行中的子弹, 除此之外不额外画任何光效。
 * 关键行为:</p>
 * <ol>
 *   <li><b>中途不碰撞</b>: {@code collides = false}, 飞行途中穿过单位、建筑、地形;</li>
 *   <li><b>只打击锁定目标</b>: 开火瞬间把炮台的 {@code target} 记到 {@code b.data},
 *       每帧朝该单位转向, 只有真正碰到它才结算;</li>
 *   <li>命中后把枪钉在目标身上, 并播放参考 FlameOut 消沉大招的猩红碎片粒子。</li>
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
     * 枪在世界里的实际长度 (世界单位, 1 格 = 8 单位)。160 ≈ 20 格。
     *
     * <p>★ 必须与炮台的 {@code spearLength} 保持一致: 用户要求"子弹尺寸和炮台上的朗枪一样大"。</p>
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

            float scale = scaleFor(region, drawLength);
            float w = region.width * scale;
            float h = region.height * scale;
            float ang = e.rotation;

            // 插入瞬间的"钉入"回弹: 极短时间往前多扎一点, 之后回位
            float punch = out < 0.02f ? (1f - out / 0.02f) * 16f : 0f;

            // 枪尖落在目标身上, 枪身向后方延伸
            float cx = px - Angles.trnsx(ang, 0.5f * w - punch);
            float cy = py - Angles.trnsy(ang, 0.5f * w - punch);

            Draw.z(Layer.effect);
            Draw.color(Color.white);
            Draw.alpha(alpha);
            Draw.rect(region, cx, cy, w, h, ang);

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

        float scale = scaleFor(region, drawLength);
        float w = region.width * scale;
        float h = region.height * scale;

        Draw.z(Layer.bullet);
        Draw.color(Color.white);
        // 贴图枪尖朝右 (+x): 图片中心后退半个枪长, 枪尖正好落在子弹位置上
        Draw.rect(region,
            b.x - Angles.trnsx(b.rotation(), w * 0.5f),
            b.y - Angles.trnsy(b.rotation(), w * 0.5f),
            w, h, b.rotation());
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
