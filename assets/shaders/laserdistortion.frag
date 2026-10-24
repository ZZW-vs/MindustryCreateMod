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

        // 影响带宽: 与线段的垂直距离
        float range = strength * 30.0 * u_camscl;
        if(dist < range){
            float f = 1.0 - dist / range;
            // 两端渐弱, 避免枪口/命中点出现硬边
            // (注意: smoothstep 的 edge0 必须小于 edge1, 反向写法在 GLSL 里是未定义行为)
            float ends = smoothstep(0.0, 0.10, t) * (1.0 - smoothstep(0.90, 1.0, t));

            vec2 dir = normalize(d + vec2(0.0001));

            // 采样点沿法线向外偏移 → 画面内容被吸向中线 (两侧空间挤压到线上)
            vec2 offset = dir * (strength * 18.0 * u_camscl * f * f * ends);
            p = closest + d + offset;
        }
    }

    gl_FragColor = texture2D(u_texture, p / u_texsize);
}
