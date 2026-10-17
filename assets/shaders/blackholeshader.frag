#define MAX_HOLES 8

uniform sampler2D u_texture;
uniform vec2 u_texsize;
uniform vec4 u_holes[MAX_HOLES];
uniform int u_holes_count;
uniform vec2 u_invsize;
uniform float u_camscl;
uniform float u_time;

varying vec2 v_texCoords;

vec2 trns(vec2 pos, float deg){
    float rad = deg * 0.0174533;
    float c = cos(rad);
    float s = sin(rad);
    return vec2(pos.x * c - pos.y * s, pos.x * s + pos.y * c);
}

void main(){
    vec2 tp = v_texCoords;
    float core = 0.0;
    float ring = 0.0;
    float halo = 0.0;
    float disk = 0.0;
    float parts = 0.0;

    for(int i = 0; i < MAX_HOLES; i++){
        vec2 pos = u_holes[i].xy;
        float intensity = u_holes[i].z;
        float swirl = u_holes[i].w;

        // 影响半径: 收窄 (intensity 1.2~3.4 -> 约 36~85 px)
        float range = (10.0 + intensity * 22.0) * u_camscl;

        vec2 vp = (v_texCoords * u_texsize) - pos;
        float dist = length(vp);

        // 事件视界半径: 本体缩小 (intensity -> 0 时同步收缩到 0, 自然淡出)
        float coreR = intensity * 3.0 * u_camscl;

        // 不透明黑核
        core = max(core, 1.0 - smoothstep(coreR * 0.92, coreR * 1.08, dist));
        // 外侧光子环
        ring = max(ring, smoothstep(coreR * 1.02, coreR * 1.12, dist)
                        * (1.0 - smoothstep(coreR * 1.12, coreR * 1.40, dist)));

        // ★ 程序化引力透镜光晕 (冷色, 大范围渐隐, 尺寸锚定 coreR 同步收缩)
        halo += 0.16 * (1.0 - smoothstep(coreR * 2.2, coreR * 6.0, dist));
        halo += 0.10 * (1.0 - smoothstep(coreR * 3.0, coreR * 8.5, dist));

        // ★ 程序化吸积盘: 内亮橙环 + 外宽橙环, 角向亮纹随时间旋转
        float ang = atan(vp.y, vp.x);
        float shimmer = 0.55 + 0.45 * cos(ang * 3.0 - u_time * 2.4);
        float d1 = smoothstep(coreR * 1.5, coreR * 1.9, dist) * (1.0 - smoothstep(coreR * 2.4, coreR * 3.1, dist));
        float d2 = smoothstep(coreR * 2.6, coreR * 3.1, dist) * (1.0 - smoothstep(coreR * 4.4, coreR * 5.4, dist));
        disk += d1 * (0.85 + 0.15 * sin(u_time * 6.0)) * shimmer * 0.9;
        disk += d2 * 0.45 * shimmer;

        // ★ 程序化内向汇聚粒子: 8 颗黄金角螺旋内落粒子 (椭圆轨道, 两向交替漂移)
        for(int p = 0; p < 8; p++){
            float seed = float(p) * 1.37;
            float t = fract(u_time * 0.4 + seed);
            float r = mix(coreR * 5.2, coreR * 1.35, t);
            float base = seed * 137.5;
            float drift = (1.0 - t) * 60.0 * (mod(float(p), 2.0) * 2.0 - 1.0);
            vec2 pp = vec2(cos(radians(base + drift)) * r, sin(radians(base + drift)) * r * 0.55);
            float pd = length(vp - pp);
            parts += sin(t * 3.14159) * (1.0 - smoothstep(0.0, 2.2 + coreR * 0.14, pd)) * 0.85;
        }

        if(dist < range){
            float f = (range - dist) / range;          // 1 = 中心, 0 = 边缘
            vec2 dir = normalize(vp + vec2(0.0001));

            // 向内吸: 采样点向外拉, 视觉上空间被吸进去
            vec2 warped = vp + dir * (intensity * 13.0 * f * f);

            // 螺旋: f 二次衰减, 越靠中心扭得越狠, 外缘几乎不扭
            float angW = intensity * swirl * 3.0 * f * f;
            vec2 sw = trns(warped, angW);

            tp += (sw - vp) * u_invsize;
        }

        if(i >= u_holes_count - 1){
            break;
        }
    }

    vec4 c = texture2D(u_texture, tp);

    // 引力透镜冷色光晕
    c.rgb += vec3(0.42, 0.65, 1.0) * clamp(halo, 0.0, 1.0);
    // 吸积盘 (暖橙)
    c.rgb += vec3(1.0, 0.62, 0.18) * clamp(disk, 0.0, 1.2);
    // 汇聚粒子 (暖白)
    c.rgb += vec3(1.0, 0.91, 0.63) * clamp(parts, 0.0, 1.0);

    // 黑核: 强制不透明, 不会再透出背景
    c = mix(c, vec4(0.0, 0.0, 0.0, 1.0), clamp(core, 0.0, 1.0));

    // 光子环
    c.rgb += vec3(0.55, 0.78, 1.0) * ring;
    c.a = max(c.a, max(ring * 0.9, clamp(halo * 0.6 + disk * 0.4, 0.0, 1.0)));

    gl_FragColor = c;
}
