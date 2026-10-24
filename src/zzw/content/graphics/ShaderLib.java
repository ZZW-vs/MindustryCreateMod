package zzw.content.graphics;

import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.geom.Vec2;
import arc.struct.FloatSeq;

import static mindustry.Vars.headless;
import static mindustry.Vars.tree;

/**
 * 模组统一着色器注册中心 (Shader Registry)。
 *
 * <p>设计参考 Vanilla-Expansion 的 {@code VEShaders} 与 PU132 的
 * {@code UnityShaders}: 把全模组的着色器集中到一个类里统一管理, 解决此前
 * 着色器散落在各 package、资源路径写法混乱、生命周期无人负责的问题。</p>
 *
 * <p>本类职责:</p>
 * <ul>
 *   <li><b>集中持有</b> 所有着色器实例 (黑洞 / 碎裂 / 汽化 / 切割模板 / GPU 直通);</li>
 *   <li><b>统一生命周期</b> {@link #load()} / {@link #dispose()}, 重复调用安全;</li>
 *   <li><b>统一资源访问</b> 一律走 {@link #file(String)} (mod 目录 tree.get),
 *       不再依赖游戏内置 assets (Core.files.internal) —— 后者跨版本不稳定;</li>
 *   <li><b>共享顶点着色器</b> {@link #defaultVert}, 不再为每个 shader 单独读取 .vert 文件。</li>
 * </ul>
 *
 * <p>迁移说明: 所有 uniform 名称与 {@code apply()} 计算逻辑与原实现完全一致,
 * 仅改变"放哪里"和"怎么取资源", 视觉表现不变。</p>
 *
 * @author zzw
 */
public class ShaderLib{

    /**
     * 全模组共享的顶点着色器。
     *
     * <p>与游戏内置 {@code shaders/screenspace.vert} 逐字节等价: <b>不乘任何投影矩阵</b>,
     * 直接输出顶点位置 —— ScreenQuad 的顶点范围是 (-1..1) 裁剪空间, 因此 blit 时
     * 无论当前相机投影是什么, 输出始终铺满整个屏幕。</p>
     *
     * <p>★ 修复记录: 此前误用了 {@code gl_Position = u_projTrans * a_position} 的版本,
     * 但 {@code u_projTrans} 从未被上传 (GLSL uniform 默认为零矩阵), 导致全屏 quad
     * 塌缩成一个点 —— 所有 blit 后处理 (切割/碎裂/汽化/黑洞扭曲) 全部静默输出空白。</p>
     */
    public static final String defaultVert = """
        attribute vec4 a_position;
        attribute vec2 a_texCoord0;

        varying vec2 v_texCoords;

        void main(){
            v_texCoords = a_texCoord0;
            gl_Position = a_position;
        }""";

    // ===== 着色器实例 (集中登记) =====

    /** 碎裂消散着色器 (End 系列 "单位碎裂剥离", 见 {@link FragmentationShader})。 */
    public static FragmentationShader fragmentation;
    /** 汽化消散着色器 (End 系列 "单位化为尘埃", 见 {@link VapourizeShader})。 */
    public static VapourizeShader vapourize;
    /** 切割模板着色器 (tenmeikiri 单位切割, 见 {@link StencilShader})。 */
    public static StencilShader stencil;
    /** 黑洞/引力扭曲后处理着色器 (见 {@link BlackHoleShader})。 */
    public static BlackHoleShader blackHole;
    /** 线性引力透镜着色器 (thalassophobia 奇异点光束, 见 {@link LaserDistortionShader})。 */
    public static LaserDistortionShader laserDistortion;

    /** 公用帧缓冲: 碎裂 / 汽化特效共用 (仅当 begin() 与 end() 在同一函数内时使用)。 */
    public static FrameBuffer bufferAlt;

    /** 是否已加载过 (幂等保护)。 */
    protected static boolean loaded;

    /**
     * 加载全部着色器 (仅客户端)。
     *
     * <p>重复调用时先 {@link #dispose()} 释放旧实例, 防止 GL 资源泄漏。
     * headless (服务端) 下直接跳过 —— 服务端不绘制, 也不需要编译着色器。</p>
     */
    public static void load(){
        if(headless) return;
        if(loaded) return;

        bufferAlt = new FrameBuffer();

        fragmentation = new FragmentationShader();
        vapourize = new VapourizeShader();
        stencil = new StencilShader();
        blackHole = new BlackHoleShader();
        laserDistortion = new LaserDistortionShader();

        loaded = true;
    }

    /**
     * 释放全部着色器与帧缓冲, 并把静态引用置空。
     *
     * <p>可重复调用 (空判断保护), 与 {@link #load()} 配对使用。</p>
     */
    public static void dispose(){
        if(fragmentation != null){ fragmentation.dispose(); fragmentation = null; }
        if(vapourize != null){ vapourize.dispose(); vapourize = null; }
        if(stencil != null){ stencil.dispose(); stencil = null; }
        if(blackHole != null){ blackHole.dispose(); blackHole = null; }
        if(laserDistortion != null){ laserDistortion.dispose(); laserDistortion = null; }
        if(bufferAlt != null){ bufferAlt.dispose(); bufferAlt = null; }

        loaded = false;
    }

    /**
     * 统一资源访问: 取 mod 自身 {@code shaders/} 目录下的文件。
     *
     * <p>与 PU/VE 不同, 本模组不引用游戏内置 assets, 因此不需要
     * {@code Core.files.internal} 这种"指向游戏目录"的写法。</p>
     *
     * @param path 相对 {@code shaders/} 的路径, 例如 {@code "fragmentation.frag"}
     */
    public static Fi file(String path){
        return tree.get("shaders/" + path);
    }

    /**
     * 惰性获取切割模板着色器 —— 无论 {@link #load()} 是否被调用都能拿到实例。
     *
     * <p>切割特效绘制前调用; headless 下不会被触发 (只画不跑)。</p>
     */
    public static StencilShader stencilShader(){
        if(stencil == null) stencil = new StencilShader();
        return stencil;
    }

    // =====================================================================
    //  具体着色器实现
    // =====================================================================

    /**
     * 碎裂消散着色器 (PU132 UnityShaders.FragmentationShader 移植)。
     *
     * <p>uniform 含义:</p>
     * <ul>
     *   <li>u_noise: 碎裂噪声纹理 (fragmentnoise.png, 重复平铺);</li>
     *   <li>u_blastpos / u_blastforce: 碎裂爆心与风向 (风把碎片吹离);</li>
     *   <li>heatcolor / heatprogress: 灼烧颜色与进度 (lightFlame → darkFlame);</li>
     *   <li>fragprogress: 碎裂进度 (0 → 1 全部剥离);</li>
     *   <li>size: 碎片尺寸基准。</li>
     * </ul>
     */
    public static class FragmentationShader extends Shader{
        /** 碎裂噪声纹理。 */
        public Texture noise;
        /** 碎裂爆心 (世界坐标)。 */
        public Vec2 source = new Vec2(), direction = new Vec2();
        /** 灼烧颜色。 */
        public Color heatColor = new Color();
        /** 灼烧进度 / 碎裂进度 / 碎片尺寸。 */
        public float heatProgress, fragProgress, size;

        public FragmentationShader(){
            super(defaultVert, file("fragmentation.frag").readString());
            noise = new Texture(file("fragmentnoise.png"));
            noise.setFilter(Texture.TextureFilter.linear);
            noise.setWrap(Texture.TextureWrap.repeat);
        }

        @Override
        public void apply(){
            // 步骤 1: 绑定噪声纹理到纹理单元 1, 屏幕缓冲到单元 0
            noise.bind(1);
            bufferAlt.getTexture().bind(0);

            // 步骤 2: 传递相机与屏幕参数
            setUniformi("u_noise", 1);

            setUniformf("u_texsize", arc.Core.camera.width, arc.Core.camera.height);
            setUniformf("u_invsize", 1f / arc.Core.camera.width, 1f / arc.Core.camera.height);
            setUniformf("u_campos",
                arc.Core.camera.position.x - arc.Core.camera.width / 2,
                arc.Core.camera.position.y - arc.Core.camera.height / 2);

            // 步骤 3: 传递碎裂参数
            setUniformf("u_blastpos", source);
            setUniformf("u_blastforce", direction);
            setUniformf("heatcolor", heatColor);

            setUniformf("heatprogress", heatProgress);
            setUniformf("fragprogress", fragProgress);
            setUniformf("size", size);
        }
    }

    /**
     * 汽化消散着色器 (PU132 UnityShaders.VapourizeShader 移植)。
     *
     * <p>uniform 含义:</p>
     * <ul>
     *   <li>u_noise: 汽化噪声纹理 (vapourizenoise.png, 镜像平铺);</li>
     *   <li>position: 风源位置 (碎片向远离风源方向飘散);</li>
     *   <li>progress / fragprogress: 整体进度与碎片剥离进度;</li>
     *   <li>tocolor / colorprog: 汽化颜色过渡 (→ Pal.rubble) 及进度;</li>
     *   <li>size: 碎片尺寸基准。</li>
     * </ul>
     */
    public static class VapourizeShader extends Shader{
        /** 汽化噪声纹理。 */
        public Texture noise;
        /** 风源位置。 */
        public Vec2 windSource = new Vec2();
        /** 汽化目标颜色。 */
        public Color toColor = new Color();
        /** 整体进度 / 颜色进度 / 碎片进度 / 尺寸。 */
        public float progress, colorProgress, fragProgress, size;

        public VapourizeShader(){
            super(defaultVert, file("vapourize.frag").readString());
            noise = new Texture(file("vapourizenoise.png"));
            noise.setWrap(Texture.TextureWrap.mirroredRepeat);
        }

        @Override
        public void apply(){
            // 步骤 1: 绑定噪声纹理到纹理单元 1, 屏幕缓冲到单元 0
            noise.bind(1);
            bufferAlt.getTexture().bind(0);

            setUniformi("u_noise", 1);

            // 步骤 2: 传递风源与进度参数
            setUniformf("position", windSource);

            setUniformf("progress", progress);
            setUniformf("fragprogress", fragProgress);

            setUniformf("tocolor", toColor);
            setUniformf("colorprog", colorProgress);
            setUniformf("size", size);

            // 步骤 3: 传递相机与屏幕参数
            setUniformf("u_texsize", arc.Core.camera.width, arc.Core.camera.height);
            setUniformf("u_invsize", 1f / arc.Core.camera.width, 1f / arc.Core.camera.height);
            setUniformf("u_offset",
                arc.Core.camera.position.x - arc.Core.camera.width / 2,
                arc.Core.camera.position.y - arc.Core.camera.height / 2);
        }
    }

    /**
     * 切割模板着色器 (PU132 UnityShaders.StencilShader 移植)。
     *
     * <p>配合 {@code shaders/unitystencil.frag} 使用: 帧缓冲里用指定颜色
     * ({@link #stencilColor}, 通常纯绿) 画出"要切掉的那一半"区域, 片元着色器
     * 把这些像素的 alpha 归零 (擦除), 并给紧贴擦除边界的像素叠加
     * {@link #heatColor} —— 形成"一刀切开 + 切口灼烧发亮"的观感。</p>
     *
     * <p>uniform 含义:</p>
     * <ul>
     *   <li>stencilcolor: 模板色 (被擦除区域的标记色);</li>
     *   <li>heatcolor: 切口高光色 (lightFlame → darkFlame 随进度变化);</li>
     *   <li>u_invsize: 屏幕单位尺寸的倒数 (用于采样相邻像素判定边界)。</li>
     * </ul>
     */
    public static class StencilShader extends Shader{
        /** 模板色 (被擦除区域的标记色)。 */
        public Color stencilColor = new Color();
        /** 切口高光色。 */
        public Color heatColor = new Color();

        public StencilShader(){
            super(defaultVert, file("unitystencil.frag").readString());
        }

        @Override
        public void apply(){
            setUniformf("stencilcolor", stencilColor);
            setUniformf("heatcolor", heatColor);
            setUniformf("u_invsize", 1f / arc.Core.camera.width, 1f / arc.Core.camera.height);
        }
    }

    /**
     * 黑洞/引力扭曲后处理着色器 (原 {@code BlackHoleShader} 迁入)。
     *
     * <p>把屏幕上若干"黑洞点" ({@link #holes}, 每 4 个 float 一组:
     * x, y, intensity, swirl) 送入 GPU, 由 {@code blackholeshader.frag}
     * 对背景做向内吸收 + 螺旋扭曲 + 黑核/光子环渲染。</p>
     *
     * <p>uniform 含义:</p>
     * <ul>
     *   <li>u_texsize: 帧缓冲尺寸; u_invsize: 屏幕尺寸倒数; u_camscl: 显示缩放;</li>
     *   <li>u_holes_count / u_holes: 黑洞点数量与数据 (投影到屏幕坐标后传入)。</li>
     * </ul>
     */
    public static class BlackHoleShader extends Shader{
        /** 最大黑洞点数据量 (8 个黑洞 × 4 个 float)。 */
        static final int maxHoles = 8 * 4;
        /** 待处理黑洞点 (世界坐标 + 强度 + 漩涡系数)。 */
        public FloatSeq holes = new FloatSeq(maxHoles);
        /** 投影后送 GPU 的 uniform 数据。 */
        public FloatSeq uniforms = new FloatSeq(maxHoles);

        public BlackHoleShader(){
            super(defaultVert, file("blackholeshader.frag").readString());
        }

        /** 追加一个黑洞点 (超过上限或强度非正时忽略)。 */
        public void add(float x, float y, float intensity, float swirl){
            if(holes.size >= maxHoles || intensity <= 0) return;
            holes.add(x, y, intensity, swirl);
        }

        @Override
        public void apply(){
            FrameBuffer b = BlackHoleSFX.inst.buffer;
            setUniformf("u_texsize", b.getWidth(), b.getHeight());
            setUniformf("u_invsize", 1f / arc.Core.camera.width, 1f / arc.Core.camera.height);
            setUniformf("u_camscl", mindustry.Vars.renderer.getDisplayScale());

            uniforms.clear();
            float[] items = holes.items;
            for(int i = 0; i < holes.size; i += 4){
                Vec2 v = arc.Core.camera.project(items[i], items[i + 1]);
                uniforms.add(v.x, v.y, items[i + 2], items[i + 3]);
            }

            setUniformi("u_holes_count", holes.size / 4);
            setUniform4fv("u_holes", uniforms.items, 0, uniforms.size);
        }
    }

    /**
     * 线性引力透镜着色器 (laserdistortion.frag)。
     *
     * <p>与黑洞的"圆形漩涡"不同, 这里把屏幕上一条线段两侧的内容沿法线压向该线段,
     * 用于 thalassophobia 的奇异点光束: 看起来像光束把左右两侧的空间挤向中线。</p>
     *
     * <p>每条线 5 个 float: x1, y1, x2, y2, strength; 最多同时 4 条。</p>
     */
    public static class LaserDistortionShader extends Shader{
        public static final int maxLines = 4;

        /** 待投影的线段世界坐标。 */
        public FloatSeq lines = new FloatSeq(maxLines * 5);
        /** 投影后送 GPU 的端点数据 (每条 4 个 float)。 */
        public FloatSeq uniforms = new FloatSeq(maxLines * 4);

        public LaserDistortionShader(){
            super(defaultVert, file("laserdistortion.frag").readString());
        }

        /** 追加一条线 (超过上限或强度非正时忽略)。 */
        public void add(float x1, float y1, float x2, float y2, float strength){
            if(lines.size >= maxLines * 5 || strength <= 0) return;
            lines.add(x1, y1, x2, y2);
            lines.add(strength);
        }

        @Override
        public void apply(){
            FrameBuffer b = BlackHoleSFX.inst.buffer;
            setUniformf("u_texsize", b.getWidth(), b.getHeight());
            setUniformf("u_camscl", mindustry.Vars.renderer.getDisplayScale());

            uniforms.clear();
            float[] items = lines.items;
            float s0 = 0f, s1 = 0f, s2 = 0f, s3 = 0f;
            int count = lines.size / 5;

            for(int i = 0; i < count; i++){
                int o = i * 5;
                Vec2 a = arc.Core.camera.project(items[o], items[o + 1]);
                Vec2 c = arc.Core.camera.project(items[o + 2], items[o + 3]);
                uniforms.add(a.x, a.y, c.x, c.y);
                float s = items[o + 4];
                if(i == 0) s0 = s;
                else if(i == 1) s1 = s;
                else if(i == 2) s2 = s;
                else s3 = s;
            }

            setUniformi("u_lines_count", count);
            setUniform4fv("u_lines", uniforms.items, 0, uniforms.size);
            setUniformf("u_s0", s0);
            setUniformf("u_s1", s1);
            setUniformf("u_s2", s2);
            setUniformf("u_s3", s3);
        }
    }

    /**
     * WavefrontObject GPU Mesh 用的直通着色器 (原 WavefrontObject 内联 shader 迁入)。
     *
     * <p>兼容 GLES 2.0 (不写 {@code #version}, 由 arc 自动处理): 位置属性 + 颜色 + UV,
     * 片元阶段直接 {@code v_color * texture2D(...)}, 用于高面数 3D 模型的批量绘制。</p>
     */
    public static class PassThroughShader extends Shader{
        public PassThroughShader(){
            super(
                "attribute vec2 a_position;\n" +
                "attribute vec4 a_color;\n" +
                "attribute vec2 a_texCoord0;\n" +
                "uniform mat4 u_projTrans;\n" +
                "varying vec4 v_color;\n" +
                "varying vec2 v_texCoord0;\n" +
                "void main(){\n" +
                "  v_color = a_color;\n" +
                "  v_texCoord0 = a_texCoord0;\n" +
                "  gl_Position = u_projTrans * vec4(a_position, 0.0, 1.0);\n" +
                "}",
                "uniform sampler2D u_texture;\n" +
                "varying vec4 v_color;\n" +
                "varying vec2 v_texCoord0;\n" +
                "void main(){\n" +
                "  gl_FragColor = v_color * texture2D(u_texture, v_texCoord0);\n" +
                "}"
            );
        }
    }
}
