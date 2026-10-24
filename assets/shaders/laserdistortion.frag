#define MAX_LINES 4

// 线性引力透镜: 把画面内容沿法线压向一条线段 (两侧空间向中间挤压)
uniform sampler2D u_texture;
uniform vec2 u_texsize;
uniform vec4 u_lines[MAX_LINES];   // 每条线段的屏幕端点: (x1, y1, x2, y2)
uniform int u_lines_count;
uniform float u_camscl;

uniform float u_s0;
uniform float u_s1;
uniform float u_s2;
uniform float u_s3;

varying vec2 v_texCoords;

// 取第 i 条线的强度 (GLSL ES 2.0 不支持对 vec4 做动态下标, 直接展开)
float strengthOf(int i){
    if(i == 0) return u_s0;
    if(i == 1) return u_s1;
    if(i == 2) return u_s2;
    return u_s3;
}

void main(){
    vec2 p = v_texCoords * u_texsize;

    for(int i = 0; i < MAX_LINES; i++){
        if(i >= u_lines_count){
            break;
        }

        float strength = strengthOf(i);
        vec2 a = u_lines[i].xy;
        vec2 b = u_lines[i].zw;

        vec2 ab = b - a;
        float l2 = max(dot(ab, ab), 1.0);
        float t = clamp(dot(p - a, ab) / l2, 0.0, 1.0);
        vec2 closest = a + ab * t;

        vec2 d = p - closest;
        float dist = length(d);

        // 核心区(光束本体)不扭曲: 光束必须保持清晰, 扭曲只发生在它周围的空间
        float core = 30.0 * u_camscl;
        // 蔓延范围: 比之前宽很多, 让扭曲从光束向外扩散开来
        float range = strength * 150.0 * u_camscl;
        if(dist > core && dist < range){
            float u = clamp((dist - core) / max(range - core, 1.0), 0.0, 1.0);
            // 两端为 0、中间最强: 光束处不动, 远处平滑收束, 不会出现硬边
            float f = sin(u * 3.14159265);
            // 两端渐弱, 避免枪口/命中点出现硬边
            float ends = smoothstep(0.0, 0.10, t) * (1.0 - smoothstep(0.90, 1.0, t));

            vec2 dir = normalize(d + vec2(0.0001));

            // 采样点沿法线向外偏移 → 画面内容被吸向中线 (两侧空间挤压到线上)
            vec2 offset = dir * (strength * 34.0 * u_camscl * f * ends);
            p += offset;
        }
    }

    gl_FragColor = texture2D(u_texture, p / u_texsize);
}
