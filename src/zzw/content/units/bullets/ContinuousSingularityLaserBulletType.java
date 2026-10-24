package zzw.content.units.bullets;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import zzw.content.graphics.UnityPal;

/**
 * 连续奇异点激光 (移植自 PU132 ContinuousSingularityLaserBulletType 完整版,
 * thalassophobia 主炮武器)
 *
 * <p>机制 (对照 PU132 原版):</p>
 * <p>1. 激光加速延伸: 每 tick 速度 velocity 随 velocityTime/accel 增长 (上限 laserSpeed),
 *     命中建筑/单位后 velocity 归零、restartTime 重新计时, 激光缩短到命中点 + pierceAmount,
 *     5 tick 内用 fastUpdateLength 快速逼近目标 (激光"咬住"目标的视觉效果);
 * <br>2. 每 5 tick 碰撞检测: 建筑健康 &gt; damage/100 或吸激光方块会阻挡光束;
 *     大型单位 (hitSize &gt; width×3 且血量 &gt; damage) 同样阻挡;
 *     所有沿线敌人受到防作弊伤害 (比例/超量/流血);
 * <br>3. 引力场: 光束两侧 gravityRange 范围内的单位被拉向光束
 *     (force = gravityStrength×距离衰减, 通过 impulse 拉扯, 小单位直接被吸进光束);
 * <br>4. 绘制: 5 层颜色 (红→黑) 嵌套光束, 每层带 oscScl 呼吸宽度与 laserInstability 抖动,
 *     根部圆球 + 根部三角翼 + 末端三角。</p>
 *
 * <p>碰撞检测: PU132 Utils.collideLineRawEnemyRatio 已全量移植为
 * {@link zzw.util.LineCollide}, 本类直接使用精确线扫描。</p>
 */
public class ContinuousSingularityLaserBulletType extends AntiCheatBulletTypeBase {
    public float maxLength = 1000f;
    public float laserSpeed = 15f;
    public float accel = 25f;
    public float width = 12f, widthReduction = 2f, collisionWidth = 8f;
    /** 引力场半径/强度 (thalassophobia: 260/20×80) */
    public float gravityRange = 260f, gravityStrength = 20f;
    public float fadeTime = 60f;
    public float fadeInTime = 8f;
    /** 各层宽度呼吸: 相位偏移 oscOffset / 周期 oscScl */
    public float oscOffset = 1.4f, oscScl = 1.1f;
    /** 激光抖动幅度 (像素) */
    public float laserInstability = 1f;
    /** 命中后光束末端超出命中点的距离 (激光"包住"目标) */
    public float pierceAmount = 4f;
    /** 根部圆球尺寸倍率 / 根部三角长度 */
    public float baseScl = 3f, baseTriangleSize = 60f;
    /** 命中后 5 tick 内快速逼近目标 (true = PU132 原版行为) */
    public boolean fastUpdateLength = true;
    /** 穿透计分上限 (0 = 不穿透, 光束碰到大型目标即停) */
    public float pierceCap = 0f;
    public Color[] colors = {
        UnityPal.scarColor.cpy().a(0.4f), UnityPal.scarColor, UnityPal.endColor,
        Color.white, Color.black
    };

    public ContinuousSingularityLaserBulletType(float damage) {
        super(0f, damage);
        despawnEffect = Fx.none;
        collides = false;
        pierce = true;
        impact = true;
        keepVelocity = false;
        hittable = false;
        absorbable = false;
        // 黑色激光画在单位图层之上 (模组既有约定, 防止被单位身体遮挡)
        layer = Layer.flyingUnit + 0.5f;
    }

    @Override
    public void init() {
        super.init();
        drawSize = maxLength * 2f;
        despawnHit = false;
    }

    @Override
    public float estimateDPS() {
        return damage * (lifetime / 2f) / 5f * 3f;
    }

    @Override
    public float continuousDamage() {
        return damage / 5f * 60f;
    }

    /**
     * 射程 (PU132 range(): maxRange > 0 ? maxRange : maxLength / 1.5f)。
     *
     * <p>★ v160 适配: BulletType 已无 range() 方法, 引擎在 init()/afterPatch() 里
     * 调用 {@code calculateRange()} 把结果写入 {@code range} 字段。若只写一个自定义
     * {@code range()} 方法, 引擎根本不会调用 → speed=0 的激光射程被算成 0 →
     * thalassophobia 主炮永远不开火 (大激光与其引力场都看不到)。故必须覆写此钩子。</p>
     */
    @Override
    protected float calculateRange() {
        return maxRange > 0f ? maxRange : maxLength / 1.5f;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.data = new VoidLaserData();
    }

    /**
     * 激光延伸 + 碰撞 + 引力场更新 (PU132 update 逐步移植)。
     */
    @Override
    public void update(Bullet b) {
        boolean timer = b.timer(0, 5f);

        if (b.data instanceof VoidLaserData) {
            VoidLaserData data = (VoidLaserData)b.data;

            if (data.restartTime >= 5f) {
                // ===== 自由延伸阶段: 速度随时间累积 (加速启动) =====
                if (accel > 0.01f) {
                    data.velocity = Mathf.clamp((data.velocityTime / accel) + data.velocity, 0f, laserSpeed);
                    b.fdata = Mathf.clamp(b.fdata + (data.velocity * Time.delta), 0f, maxLength);
                    data.velocityTime += Time.delta;
                } else if (timer) {
                    // 无加速参数时直接满长度
                    b.fdata = maxLength;
                }
            } else {
                // ===== 命中重启阶段: restartTime 计时, 快速逼近被咬住的目标 =====
                data.restartTime += Time.delta;
                if (fastUpdateLength && data.target != null) {
                    data.pierceOffsetSmooth = Mathf.lerpDelta(data.pierceOffsetSmooth, data.pierceOffset, 0.2f);
                    Tmp.v2.trns(b.rotation(), maxLength * 1.5f).add(b);
                    float dst = Intersector.distanceLinePoint(b.x, b.y, Tmp.v2.x, Tmp.v2.y, data.target.getX(), data.target.getY());
                    b.fdata = ((b.dst(data.target) - data.targetSize) + dst) + pierceAmount + (data.pierceOffsetSmooth * data.targetSize);
                }
            }

            if (timer) {
                // ★ 碰撞 (PU132 update L104-166 逐行移植, 走 LineCollide 精确线扫描)
                boolean p = pierceCap > 0;
                if (p) {
                    data.pierceScore = 0f;
                    data.pierceOffset = 0f;
                }
                Tmp.v1.trns(b.rotation(), b.fdata).add(b);
                zzw.util.LineCollide.collideLineRawEnemyRatio(b.team, b.x, b.y, Tmp.v1.x, Tmp.v1.y, collisionWidth,
                (building, ratio, direct) -> {
                    // 建筑: 健康 > damage/100 或吸激光方块才阻挡光束
                    boolean h = (building.health > damage / 100f) || building.block.absorbLasers;
                    if (direct) {
                        if (h) {
                            if (p) data.pierceScore += building.block.size * (building.block.absorbLasers ? 10f : 1f) * ratio;
                            if (!p || data.pierceScore >= pierceCap) {
                                Tmp.v2.trns(b.rotation(), maxLength * 1.5f).add(b);
                                float dst = Intersector.distanceLinePoint(b.x, b.y, Tmp.v2.x, Tmp.v2.y, building.x, building.y);
                                data.velocity = 0f;
                                data.restartTime = 0f;
                                data.velocityTime = 0f;
                                data.pierceOffset = 1f - Mathf.clamp(data.pierceScore - pierceCap);
                                if (fastUpdateLength) {
                                    if (building != data.target) data.pierceOffsetSmooth = data.pierceOffset;
                                    data.target = building;
                                    data.targetSize = building.block.size * Vars.tilesize / 2f;
                                }
                                b.fdata = ((b.dst(building) - (building.block.size * Vars.tilesize / 2f)) + dst) + pierceAmount + (data.pierceOffsetSmooth * data.targetSize);
                            }
                        }
                        hitBuildingAntiCheat(b, building);
                    }
                    return !p ? h : data.pierceScore >= pierceCap;
                },
                (unit, ratio) -> {
                    // 单位: 大型单位 (hitSize > width×3 且血量 > damage) 阻挡光束
                    boolean h = unit.hitSize > width * 3f && unit.health > damage;
                    if (h) {
                        if (p) data.pierceScore += (((unit.hitSize / Vars.tilesize) / 2f) + (unit.health / 4000f)) * ratio;
                        if (!p || data.pierceScore >= pierceCap) {
                            Tmp.v2.trns(b.rotation(), maxLength * 1.5f).add(b);
                            float dst = Intersector.distanceLinePoint(b.x, b.y, Tmp.v2.x, Tmp.v2.y, unit.x, unit.y);
                            data.velocity = 0f;
                            data.restartTime = 0f;
                            data.velocityTime = 0f;
                            data.pierceOffset = 1f - Mathf.clamp(data.pierceScore - pierceCap);
                            if (fastUpdateLength) {
                                if (unit != data.target) data.pierceOffsetSmooth = data.pierceOffset;
                                data.target = unit;
                                data.targetSize = unit.hitSize / 2f;
                            }
                            b.fdata = ((b.dst(unit) - (unit.hitSize / 2f)) + dst) + pierceAmount + (data.pierceOffsetSmooth * data.targetSize);
                        }
                    }
                    hitUnitAntiCheat(b, unit);
                    return !p ? h : data.pierceScore >= pierceCap;
                },
                (ex, ey) -> hit(b, ex, ey));
            }

            // ===== 引力场: 拉扯光束两侧 gravityRange 内的敌人 =====
            Tmp.v1.trns(b.rotation(), b.fdata).add(b);
            if (timer) {
                data.units.clear();
                float ex = Tmp.v1.x, ey = Tmp.v1.y;
                float minX = Math.min(b.x, ex) - gravityRange, minY = Math.min(b.y, ey) - gravityRange;
                float sizeX = Math.abs(ex - b.x) + gravityRange * 2f, sizeY = Math.abs(ey - b.y) + gravityRange * 2f;
                Groups.unit.intersect(minX, minY, sizeX, sizeY, unit -> {
                    if (unit.team != b.team && unit.isValid()) {
                        if (Intersector.distanceSegmentPoint(b.x, b.y, ex, ey, unit.x, unit.y) <= gravityRange + (unit.hitSize / 2f)) {
                            data.units.add(unit);
                        }
                    }
                });
            }

            for (Unit u : data.units) {
                if (u.isAdded()) {
                    // 拉向光束上离自己最近的点: 距离越近力越大
                    Vec2 p = Intersector.nearestSegmentPoint(b.x, b.y, Tmp.v1.x, Tmp.v1.y, u.x, u.y, Tmp.v2);
                    float force = (1f - Mathf.clamp((p.dst(u) - (u.hitSize / 2f)) / gravityRange)) * gravityStrength;
                    Vec2 m = Tmp.v3.set(p).sub(u).setLength2(Math.min(p.dst2(u) * u.mass() * u.mass(), force * force));
                    if (m.isNaN()) m.setZero();

                    u.impulse(m);
                }
            }
        }
    }

    /**
     * ★ 美化版奇异点激光绘制 (PU132 五层嵌套光束 + 方案 A 的体积/引力层)。
     *
     * <p>层级 (从外到内, 保证黑色核心可见):</p>
     * <ol>
     *   <li><b>引力透镜外壳</b> (additive): 沿截面 7 层切片 + 两侧向内弯折的透镜弧,
     *       让光束周围像被引力扭曲的空间</li>
     *   <li><b>PU132 原版 5 层嵌套光束</b>: 保留红→黑、呼吸宽度与抖动</li>
     *   <li><b>滚动引力环</b>: 每 72px 一个黑色横向环 + 猩红细边 + 两端光点,
     *       环随光束向前滚动, 像被奇点吸入的物质环</li>
     *   <li><b>炮口奇点</b>: 黑色球体 + 红环 + 两段对向旋转的吸积盘弧</li>
     *   <li><b>末端接触点</b>: 黑芯 + 红环 + 白点</li>
     * </ol>
     *
     * <p>全部为矢量绘制, 单条光束每帧约 10 条线 + 6 个圆 + 14 个滚动环,
     * 无纹理采样与历史缓存。</p>
     */
    @Override
    public void draw(Bullet b) {
        float fadeIn = fadeInTime <= 0f ? 1f : Mathf.clamp(b.time / fadeInTime);
        float fade = Mathf.clamp(b.time > b.lifetime - fadeTime ? 1f - (b.time - (b.lifetime - fadeTime)) / fadeTime : 1f) * fadeIn;
        float tipHeight = width / 2f;
        float rot = b.rotation();
        float dx = Mathf.cosDeg(rot), dy = Mathf.sinDeg(rot);
        float px = -dy, py = dx;              // 截面方向
        float len = b.fdata;
        float baseZ = Draw.z();

        // ---------- 1. 引力透镜外壳 (additive) ----------
        Draw.blend(Blending.additive);
        if (len > 0.01f) {
            // 截面 7 层切片: 中心亮、两边暗, 叠加后形成圆柱体积感
            for (int i = -3; i <= 3; i++) {
                float o = i * width * 0.42f;
                float a = (1f - Math.abs(i) / 4f) * 0.09f * fade;
                Draw.color(colors[1]);
                Draw.alpha(a);
                Lines.stroke(width * 1.15f);
                Lines.line(b.x + px * o, b.y + py * o,
                    b.x + dx * len + px * o, b.y + dy * len + py * o, false);
            }

            // 两侧引力透镜弧: 中段向光束收拢的折线 (近似弧线)
            Draw.color(colors[2]);
            Draw.alpha(0.22f * fade);
            Lines.stroke(width * 0.10f);
            for (int s : Mathf.signs) {
                float off = width * 2.1f * s;
                float x1 = b.x + px * off, y1 = b.y + py * off;
                float x2 = b.x + dx * len * 0.5f + px * off * 0.72f,
                      y2 = b.y + dy * len * 0.5f + py * off * 0.72f;
                float x3 = b.x + dx * len + px * off, y3 = b.y + dy * len + py * off;
                Lines.line(x1, y1, x2, y2, false);
                Lines.line(x2, y2, x3, y3, false);
            }
        }
        Draw.blend();

        // ---------- 2. PU132 原版 5 层嵌套光束 (红 → 黑) ----------
        for (int i = 0; i < colors.length; i++) {
            float rx = Mathf.range(laserInstability),
            ry = Mathf.range(laserInstability);
            float f = 1f - ((widthReduction * i) / width);
            float w = f * (width + Mathf.absin(Time.time + (i * oscOffset), oscScl, width / 8)) * fade;

            Tmp.v2.trns(rot, len - tipHeight).add(b);
            Tmp.v1.trns(rot, width * 2f).add(Tmp.v2);
            Draw.color(colors[i]);
            Fill.circle(b.x + rx, b.y + ry, (w / 2f) * baseScl);
            Lines.stroke(w);
            Lines.line(b.x + rx, b.y + ry, Tmp.v2.x + rx, Tmp.v2.y + ry, false);
            for (int s : Mathf.signs) {
                Drawf.tri(b.x + rx, b.y + ry, w, baseTriangleSize + w, rot + 90f * s);

                Tmp.v3.trns(rot, w * -0.7f, w * s);
                Fill.tri(Tmp.v2.x + rx, Tmp.v2.y + ry,
                Tmp.v1.x + rx, Tmp.v1.y + ry,
                Tmp.v2.x + Tmp.v3.x + rx, Tmp.v2.y + Tmp.v3.y + ry);
            }
        }

        // ---------- 3. 滚动引力环 (黑环 + 猩红细边 + 两端光点) ----------
        if (len > 0.01f) {
            float spacing = 72f;
            float travel = (b.time * 2.4f) % spacing;
            for (float d = travel; d < len; d += spacing) {
                float f = d / Math.max(len, 1f);
                float rr = width * (1.45f - 0.40f * f) * fade;
                float cx = b.x + dx * d, cy = b.y + dy * d;

                Draw.color(Color.black);
                Draw.alpha(0.62f * fade);
                Lines.stroke(width * 0.30f * fade);
                Lines.line(cx + px * rr, cy + py * rr, cx - px * rr, cy - py * rr, false);

                Draw.color(colors[2]);
                Draw.alpha((1f - f) * 0.55f * fade);
                Lines.stroke(width * 0.08f * fade);
                Lines.line(cx + px * rr * 1.16f, cy + py * rr * 1.16f,
                    cx - px * rr * 1.16f, cy - py * rr * 1.16f, false);

                Draw.color(colors[3]);
                Draw.alpha((1f - f) * 0.55f * fade);
                Fill.circle(cx + px * rr, cy + py * rr, width * 0.09f * fade);
                Fill.circle(cx - px * rr, cy - py * rr, width * 0.09f * fade);
            }
        }

        // ---------- 4. 炮口奇点: 黑球 + 红环 + 两段旋转吸积盘 ----------
        float pulse = 1f + Mathf.absin(0.7f, 0.15f);
        Draw.color(Color.black);
        Draw.alpha(0.88f * fade);
        Fill.circle(b.x, b.y, width * 1.5f * pulse * fade);
        Draw.color(colors[2]);
        Draw.alpha(0.95f * fade);
        Lines.stroke(width * 0.14f);
        Lines.circle(b.x, b.y, width * 1.72f * pulse * fade);
        Draw.color(colors[3]);
        Draw.alpha(0.90f * fade);
        Fill.circle(b.x, b.y, width * 0.30f * fade);

        Draw.color(colors[2]);
        Draw.alpha(0.55f * fade);
        Lines.stroke(width * 0.20f);
        Lines.arc(b.x, b.y, width * 2.35f * pulse * fade, 0.5f, Time.time * 2.2f);
        Lines.arc(b.x, b.y, width * 2.35f * pulse * fade, 0.5f, Time.time * 2.2f + 180f);

        // ---------- 5. 末端接触点: 黑芯 + 红环 + 白点 ----------
        if (len > 0.01f) {
            float ex = b.x + dx * len, ey = b.y + dy * len;
            Draw.color(Color.black);
            Draw.alpha(0.75f * fade);
            Fill.circle(ex, ey, width * 1.05f * fade);
            Draw.color(colors[2]);
            Draw.alpha(0.85f * fade);
            Lines.stroke(width * 0.12f);
            Lines.circle(ex, ey, width * 1.30f * pulse * fade);
            Draw.color(colors[3]);
            Draw.alpha(0.95f * fade);
            Fill.circle(ex, ey, width * 0.24f * fade);
        }

        Tmp.v2.trns(rot, len + tipHeight).add(b);
        // v155.4 无 light(Team,x,y,x2,y2,radius,...) 8参重载, 用 7 参线段光照等价替代
        Drawf.light(b.x, b.y, Tmp.v2.x, Tmp.v2.y, width * 2f, colors[0], 0.5f * fade);
        Draw.blend();
        Draw.z(baseZ);
        Draw.reset();
    }

    @Override
    public void drawLight(Bullet b) {
        // 光照已在 draw() 内手绘 (PU132 原版为空实现)
    }

    /**
     * 奇异点激光数据 (PU132 VoidLaserData 扩展 LaserData):
     * target/targetSize/pierceOffset 用于命中后快速逼近目标,
     * units 为当前被引力场捕获的单位列表。
     */
    static class VoidLaserData {
        public float lastLength, velocity, velocityTime, targetSize, pierceOffset, pierceOffsetSmooth, pierceScore, restartTime = 5f;
        public Position target;
        public Seq<Unit> units = new Seq<>();
    }
}
