package zzw.content.units.type.decal;

import arc.Core;
import arc.func.Func;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;

/**
 * 鞭毛尾巴装饰 (移植自 PU132 type/decal/FlagellaDecorationType 原版,
 * thalassophobia 的 4 段贴图 × 15 节长尾)
 *
 * <p>动画原理 (对照 PU132 原版):</p>
 * <p>1. 尾巴由 segments 节组成, 每节用 Lines.line 沿贴图拉伸绘制
 *    (贴图从尾根 tail-0 过渡到尾尖 tail-end);
 * <br>2. 两遍模拟:
 *    <br>- 第一遍 (update): 目标位置约束 — 每节朝上一节方向转动 (angleLimit 限幅),
 *      节间距 = cosDeg(swayAngle) × segmentLength (摆动时自然压缩);
 *    <br>- 第二遍: 实际绘制位置 = 加上 swayAngle 摆动角后的固定间距,
 *      摆动角 = sin(progress - idx×swayOffset, swayScl, 强度渐变)。
 *      强度从根部 startIntensity 渐变到末端 endIntensity (末端甩得最厉害);
 * <br>3. progress 随单位移动距离累加 (deltaLen, PU132 原版设计);
 * <br>4. 每节独立绘制阴影 (Drawf.shadow)。</p>
 */
public class FlagellaDecorationType extends UnitDecorationType{
    /** 尾巴根部的挂载偏移 (单位局部坐标) */
    public float x, y;
    /** 根部/末端摆动强度 (角度) */
    public float startIntensity = 15f, endIntensity = 40f;
    /** 摆动周期缩放 (越大摆得越慢) 与相位偏移 */
    public float swayScl = 40f, swayOffset;
    /** 每节相对上一节的最大转角 (限幅, 防止尾巴打折) */
    public float angleLimit = 25f;
    /** 贴图名 (含 create- 前缀) */
    public String name;
    /**
     * ★ ribbon 拖尾鞭击: 在尾巴外侧叠加一条 additive 发光飘带。
     *
     * <p>方案 A (2.5D 叠加投影) 的延伸: 不引入真 3D 管线, 沿现有 15 节链条
     * 用逐顶点着色的三角带 (Draw.vert) 画出"软边 + 炽白核心"两层飘带,
     * 亮度按链条移动距离做脉冲滚动, 形成鞭击的残影感。</p>
     */
    public boolean ribbon = false;
    /** 飘带宽度倍率 (相对尾巴贴图高度): 外层光晕 / 内层核心 */
    public float ribbonWidthScl = 2.1f, ribbonCoreScl = 0.62f;
    /** 飘带颜色 (根部 → 尖端) */
    public Color ribbonColor = Color.valueOf("f53036"), ribbonTipColor = Color.valueOf("ffd8d0");
    /** 飘带整体强度 */
    public float ribbonIntensity = 0.85f;
    /** 鞭击脉冲的空间间隔 (越大, 明暗节距越长) */
    public float ribbonPulseSpacing = 18f;
    /** 鞭击脉冲的周期缩放 (越大越慢) */
    public float ribbonPulseScl = 30f;

    // ★ 绘制用临时数组 (绘制线程单线程, 同类装饰共享)
    private float[] ribbonXs = new float[32], ribbonYs = new float[32];
    private final float[] ribbonVerts = new float[24];
    private final Color ribbonTmp = new Color();
    /** 每节长度 / 总节数 */
    float segmentLength;
    int segments;
    TextureRegion[] regions;
    TextureRegion end;

    public FlagellaDecorationType(String name, int textures, int segments, float length){
        this.name = name;
        this.segments = segments;
        segmentLength = length;
        regions = new TextureRegion[textures];

        decalType = FlagellaDecoration::new;
    }

    @Override
    public void load(){
        for(int i = 0; i < regions.length; i++){
            regions[i] = Core.atlas.find(name + "-" + i);
        }
        end = Core.atlas.find(name + "-end");
    }

    /**
     * 第一遍模拟: 目标位置 (tx/ty/tr) 约束求解 (PU132 update 逐步移植)。
     *
     * <p>逐步解释:</p>
     * <p>1. progress += 单位本帧移动距离 (deltaLen), 作为摆动时间源;
     * <br>2. 根节方向 = 指向目标位置的角度, 相对"单位正后方"限制在 ±angleLimit;
     * <br>3. 后续节 = 指向自身目标点的角度, 相对上一节限制在 ±angleLimit;
     * <br>4. 节间距用 cosDeg(swayAngle)×segmentLength: 摆动角越大, 沿轴距离越短。</p>
     */
    @Override
    public void update(Unit unit, UnitDecoration deco){
        FlagellaDecoration d = (FlagellaDecoration)deco;
        float dLen = unit.deltaLen();

        d.progress += dLen;
        Tmp.v1.trns(unit.rotation - 90f, x, y).add(unit);

        FlagellaSegment c = d.root;
        int idx = 0;
        while(c != null){
            // 目标点跟随单位移动
            Tmp.v2.trns(c.tr, dLen);
            c.tx += Tmp.v2.x;
            c.ty += Tmp.v2.y;
            c.length = offset(d, idx);
            if(c.prev == null){
                // 根节: 相对"单位正后方"限幅
                c.tr = clampedAngle(Tmp.v1.angleTo(c.tx, c.ty), unit.rotation + 180f, angleLimit);
                Tmp.v2.trns(c.tr, c.length).add(Tmp.v1);
            }else{
                FlagellaSegment p = c.prev;
                c.tr = clampedAngle(Angles.angle(p.tx, p.ty, c.tx, c.ty), p.tr, angleLimit);
                Tmp.v2.trns(c.tr, c.length).add(p.tx, p.ty);
            }
            c.tx = Tmp.v2.x;
            c.ty = Tmp.v2.y;

            c = c.next;
            idx++;
        }
        // 第二遍: 从目标位置 + 摆动角推算实际绘制位置 (x/y)
        idx = 0;
        c = d.root;
        while(c != null){
            float rot = c.tr + swayAngle(d, idx);

            if(c.prev == null){
                Tmp.v2.trns(rot, segmentLength).add(Tmp.v1);
            }else{
                FlagellaSegment p = c.prev;
                Tmp.v2.trns(rot, segmentLength).add(p.x, p.y);
            }
            c.x = Tmp.v2.x;
            c.y = Tmp.v2.y;

            c = c.next;
            idx++;
        }
    }

    @Override
    public void draw(Unit unit, UnitDecoration deco){
        FlagellaDecoration d = (FlagellaDecoration)deco;
        FlagellaSegment cur = d.end;

        Tmp.v1.trns(unit.rotation - 90f, x, y).add(unit);
        int regL = regions.length - 1;
        int idx = 0;
        UnitType t = unit.type;
        float z = Draw.z();
        // 绘制层级: 跟随单位所处图层 (飞行/地面), 并压到单位本体之下 (Layer.bullet - 1f 上限)
        float sz = unit.elevation > 0.5f ? (t.lowAltitude ? Layer.flyingUnitLow : Layer.flyingUnit) : t.groundLayer + Mathf.clamp(t.hitSize / 4000f, 0, 0.01f);
        sz = Math.min(sz - 0.01f, Layer.bullet - 1f);

        // ★ ribbon 拖尾鞭击 (在尾巴贴图之下, 阴影之上; 尾巴摆动时可见发光残影)
        if (ribbon) drawRibbon(Tmp.v1.x, Tmp.v1.y, d, sz);

        // 从尾尖 (end) 往回画到尾根, 贴图按节数比例从 tail-0 过渡到 tail-end
        while(cur != null){
            int reg = Mathf.clamp(regL - Mathf.round((idx / (float)segments) * regL), 0, regL);
            TextureRegion region = cur == d.end ? end : regions[reg];
            float ssize = Math.max(region.width, region.height) * Draw.scl * 1.6f;

            unit.type.applyColor(unit);
            Lines.stroke(region.height * Draw.scl);
            if(cur.prev == null){
                Tmp.v2.set(cur.x, cur.y).sub(Tmp.v1).setLength(region.width * Draw.scl).add(Tmp.v1);

                Draw.z(sz);
                Drawf.shadow((Tmp.v1.x + Tmp.v2.x) / 2f, (Tmp.v1.y + Tmp.v2.y) / 2f, ssize, 0.6f);
                Draw.z(z);

                Lines.line(region, Tmp.v1.x, Tmp.v1.y, Tmp.v2.x, Tmp.v2.y, false);
            }else{
                FlagellaSegment pr = cur.prev;
                Tmp.v2.set(cur.x, cur.y).sub(pr.x, pr.y).setLength(region.width * Draw.scl).add(pr.x, pr.y);

                Draw.z(sz);
                Drawf.shadow((pr.x + Tmp.v2.x) / 2f, (pr.y + Tmp.v2.y) / 2f, ssize, 0.6f);
                Draw.z(z);

                Lines.line(region, pr.x, pr.y, Tmp.v2.x, Tmp.v2.y, false);
            }

            idx++;
            cur = cur.prev;
        }
        Draw.reset();
    }

    /**
     * ★ ribbon 拖尾鞭击绘制 (方案 A: 逐顶点着色的 2.5D 三角带)。
     *
     * <p>步骤:</p>
     * <ol>
     *   <li>从尾根锚点到尾尖收集节点 (root → end, 共 segments+1 个点);</li>
     *   <li>相邻两点按"中心差分法线"向两侧展开成四边形, 用 {@link Draw#vert} 提交,
     *       每个顶点单独写颜色/透明度, 所以飘带有平滑的宽度与亮度渐变;</li>
     *   <li>画两层: 外层宽而暗的光晕 + 内层窄而亮的炽白核心;</li>
     *   <li>亮度用 {@code d.progress} 做脉冲, 每节相位错开, 形成沿尾巴流动的鞭击能量。</li>
     * </ol>
     *
     * <p>性能: 15 节 × 2 层 × 4 条尾巴 = 120 次 quad 提交, 全部是一次性顶点数组,
     * 无纹理采样压力, 也不做历史点缓存, 不会随移动距离增长。</p>
     */
    void drawRibbon(float rootX, float rootY, FlagellaDecoration d, float z){
        if (regions.length == 0 || segments < 1) return;

        int n = segments + 1;
        if (ribbonXs.length < n) {
            ribbonXs = new float[n];
            ribbonYs = new float[n];
        }
        ribbonXs[0] = rootX;
        ribbonYs[0] = rootY;
        int count = 1;
        for (FlagellaSegment c = d.root; c != null && count < n; c = c.next) {
            ribbonXs[count] = c.x;
            ribbonYs[count] = c.y;
            count++;
        }
        if (count < 2) return;

        // 基准宽度取尾巴贴图高度, 保证飘带比贴图宽 (露出光晕)
        float baseW = Math.max(regions[0].height, end.height) * Draw.scl;
        TextureRegion white = Core.atlas.white();
        Texture tex = white.texture;
        float u = white.u, v = white.v;
        float mix = Color.white.toFloatBits();

        Draw.z(z + 0.001f);
        Draw.blend(Blending.additive);

        // pass 0: 外层光晕; pass 1: 内层炽白核心
        for (int pass = 0; pass < 2; pass++) {
            float wMul = pass == 0 ? 1f : ribbonCoreScl;
            float aMul = pass == 0 ? 0.34f : 1f;

            for (int k = 0; k < count - 1; k++) {
                float f0 = k / (float) (count - 1), f1 = (k + 1f) / (count - 1);

                // 中心差分法线 (首尾退化为相邻段方向)
                float dx0 = ribbonXs[Math.min(k + 1, count - 1)] - ribbonXs[Math.max(k - 1, 0)];
                float dy0 = ribbonYs[Math.min(k + 1, count - 1)] - ribbonYs[Math.max(k - 1, 0)];
                float dx1 = ribbonXs[Math.min(k + 2, count - 1)] - ribbonXs[k];
                float dy1 = ribbonYs[Math.min(k + 2, count - 1)] - ribbonYs[k];

                float inv0 = 1f / Math.max(0.0001f, Mathf.len(dx0, dy0));
                float inv1 = 1f / Math.max(0.0001f, Mathf.len(dx1, dy1));
                float nx0 = -dy0 * inv0, ny0 = dx0 * inv0;
                float nx1 = -dy1 * inv1, ny1 = dx1 * inv1;

                // 宽度: 根部最宽 → 尖端收束; 核心层再乘 ribbonCoreScl
                float w0 = baseW * ribbonWidthScl * Mathf.lerp(1f, 0.22f, f0) * wMul;
                float w1 = baseW * ribbonWidthScl * Mathf.lerp(1f, 0.22f, f1) * wMul;

                // 鞭击脉冲: 相位随节数错开, 亮度沿尾巴流动
                float pulse0 = Mathf.absin(d.progress - k * ribbonPulseSpacing, ribbonPulseScl, 1f);
                float pulse1 = Mathf.absin(d.progress - (k + 1) * ribbonPulseSpacing, ribbonPulseScl, 1f);
                float a0 = ribbonIntensity * aMul * (0.30f + 0.70f * pulse0) * (1f - 0.35f * f0);
                float a1 = ribbonIntensity * aMul * (0.30f + 0.70f * pulse1) * (1f - 0.35f * f1);

                float c0 = ribbonColorAt(f0, a0, pass == 1);
                float c1 = ribbonColorAt(f1, a1, pass == 1);

                float ax0 = ribbonXs[k] + nx0 * w0 * 0.5f, ay0 = ribbonYs[k] + ny0 * w0 * 0.5f;
                float bx0 = ribbonXs[k] - nx0 * w0 * 0.5f, by0 = ribbonYs[k] - ny0 * w0 * 0.5f;
                float ax1 = ribbonXs[k + 1] + nx1 * w1 * 0.5f, ay1 = ribbonYs[k + 1] + ny1 * w1 * 0.5f;
                float bx1 = ribbonXs[k + 1] - nx1 * w1 * 0.5f, by1 = ribbonYs[k + 1] - ny1 * w1 * 0.5f;

                // 4 顶点 = 1 quad (A0 → B0 → B1 → A1)
                ribbonVerts[0] = ax0;  ribbonVerts[1] = ay0;  ribbonVerts[2] = c0; ribbonVerts[3] = u; ribbonVerts[4] = v; ribbonVerts[5] = mix;
                ribbonVerts[6] = bx0;  ribbonVerts[7] = by0;  ribbonVerts[8] = c0; ribbonVerts[9] = u; ribbonVerts[10] = v; ribbonVerts[11] = mix;
                ribbonVerts[12] = bx1; ribbonVerts[13] = by1; ribbonVerts[14] = c1; ribbonVerts[15] = u; ribbonVerts[16] = v; ribbonVerts[17] = mix;
                ribbonVerts[18] = ax1; ribbonVerts[19] = ay1; ribbonVerts[20] = c1; ribbonVerts[21] = u; ribbonVerts[22] = v; ribbonVerts[23] = mix;

                Draw.vert(tex, ribbonVerts, 0, ribbonVerts.length);
            }
        }

        // 还原混合模式与层级
        Draw.blend();
        Draw.z(z);
    }

    /** 飘带顶点颜色: 根部猩红 → 尖端珊瑚; 核心层再混入白色 */
    float ribbonColorAt(float f, float alpha, boolean core){
        ribbonTmp.set(ribbonColor).lerp(ribbonTipColor, f);
        if (core) ribbonTmp.lerp(Color.white, 0.45f);
        ribbonTmp.a = Mathf.clamp(alpha);
        return ribbonTmp.toFloatBits();
    }

    /** 沿轴节间距 = cosDeg(摆动角) × segmentLength (PU132 offset) */
    float offset(FlagellaDecoration d, int idx){
        return Mathf.cosDeg(swayAngle(d, idx)) * segmentLength;
    }

    /** 摆动角: sin 波形, 相位按节偏移 swayOffset, 强度从根部渐变到末端 (PU132 swayAngle) */
    float swayAngle(FlagellaDecoration d, int idx){
        return Mathf.sin(d.progress - (idx * swayOffset), swayScl, Mathf.lerp(startIntensity, endIntensity, idx / (segments - 1f)));
    }

    /**
     * 初始化: 在单位正后方排成一列直线 (间隔 segmentLength), 方向 = 单位正后方 (PU132 added)。
     */
    @Override
    public void added(Unit unit, UnitDecoration deco){
        FlagellaDecoration d = (FlagellaDecoration)deco;

        float ox = Angles.trnsx(unit.rotation + 180f, segmentLength),
        oy = Angles.trnsy(unit.rotation + 180f, segmentLength);

        Tmp.v1.trns(unit.rotation - 90f, x, y).add(unit);

        FlagellaSegment last = null;
        for(int i = 0; i < segments; i++){
            FlagellaSegment c = new FlagellaSegment();
            c.tx = (ox * (i + 1f)) + Tmp.v1.x;
            c.ty = (oy * (i + 1f)) + Tmp.v1.y;
            c.tr = unit.rotation + 180f;
            c.length = segmentLength;
            if(last == null){
                d.root = c;
            }else{
                c.prev = last;
                last.next = c;
            }
            d.end = c;

            last = c;
        }
    }

    /** 图标合成: 把所有尾节贴图送入描边器 (PU132 drawIcon) */
    @Override
    public void drawIcon(Func<TextureRegion, Pixmap> prov, Pixmap icon, Func<TextureRegion, TextureRegion> outliner){
        for(TextureRegion region : regions){
            outliner.get(region);
        }
        outliner.get(end);
    }

    /**
     * PU132 Utils.angleDistSigned: 带符号角度差, 返回 -180 ~ 180。
     *
     * <p>必须用带符号版本! arc 自带的 {@link Angles#angleDist(float, float)} 是
     * <b>无符号</b>距离 (0~180), 用它做"角度限幅"会丢失方向, 导致尾巴永远只往
     * 单侧弯 (转向时瞬间反向卷起并抽搐)。</p>
     */
    static float angleDistSigned(float a, float b){
        a += 360f;
        a %= 360f;
        b += 360f;
        b %= 360f;
        float d = Math.abs(a - b) % 360f;
        int sign = (a - b >= 0f && a - b <= 180f) || (a - b <= -180f && a - b >= -360f) ? 1 : -1;
        return (d > 180f ? 360f - d : d) * sign;
    }

    /**
     * PU132 clampedAngle: 把 angle 相对 relative 限制在 ±limit 内 (带方向)。
     *
     * <p>算法 (PU132 Utils.clampedAngle 原版):
     * <br>1. limit >= 180 → 不限制, 直接返回 angle;
     * <br>2. limit <= 0 → 完全锁死, 返回 relative;
     * <br>3. 求带符号差值 dst = angleDistSigned(angle, relative);
     * <br>4. |dst| <= limit → 在范围内, 返回原 angle;
     * <br>5. 超出 → 夹到 relative ± limit (按 dst 的正负决定方向)。</p>
     */
    static float clampedAngle(float angle, float relative, float limit){
        if(limit >= 180f) return angle;
        if(limit <= 0f) return relative;
        float dst = angleDistSigned(angle, relative);
        if(Math.abs(dst) > limit){
            float val = dst > 0f ? dst - limit : dst + limit;
            return (angle - val) % 360f;
        }
        return angle;
    }

    /** 鞭毛尾巴状态实例 (每单位独立) */
    static class FlagellaDecoration extends UnitDecoration{
        float progress;
        FlagellaSegment root, end;

        public FlagellaDecoration(UnitDecorationType type){
            super(type);
        }
    }

    /** 单节数据: 目标位置 (tx/ty/tr) 与绘制位置 (x/y), 双向链表 */
    static class FlagellaSegment{
        float tx, ty, tr, length;
        float x, y;

        FlagellaSegment next, prev;
    }
}
