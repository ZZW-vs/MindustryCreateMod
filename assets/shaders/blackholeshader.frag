#define MAX_HOLES 8

uniform sampler2D u_texture;
uniform vec2 u_texsize;
uniform vec4 u_holes[MAX_HOLES];
uniform int u_holes_count;
uniform vec2 u_invsize;
uniform float u_camscl;

varying vec2 v_texCoords;

vec2 trns(vec2 pos, float deg){
    float rad = deg * 0.0174533;
    float c = cos(rad);
    float s = sin(rad);
    return vec2(pos.x * c - pos.y * s, pos.x * s + pos.y * c);
}

void main(){
    vec2 tp = v_texCoords;

    for(int i = 0; i < MAX_HOLES; i++){
        // ★ 修复"整个地图变黑": 必须在处理槽位之前判空 ——
        //   u_holes_count 为 0 时旧写法仍处理未上传的 u_holes[0] (值未定义),
        //   smoothstep(0.0, 0.0, dist) 除零产生 NaN, core=1 导致全屏 mix 成黑色
        if(i >= u_holes_count){
            break;
        }

        vec2 pos = u_holes[i].xy;
        float intensity = u_holes[i].z;
        float swirl = u_holes[i].w;

        // 影响半径: ★ 用户指定较上一版 (14+32i) 减小 35% → (9.1+20.8i), intensity 1.5~4.0 → 约 40~92 px
        float range = (9.1 + intensity * 20.8) * u_camscl;

        vec2 vp = (v_texCoords * u_texsize) - pos;
        float dist = length(vp);

        if(dist < range){
            float f = (range - dist) / range;          // 1 = 中心, 0 = 边缘
            vec2 dir = normalize(vp + vec2(0.0001));

            // 向内吸: 采样点向外拉, 视觉上空间被吸进去
            vec2 warped = vp + dir * (intensity * 13.0 * f * f);

            // 螺旋: f 二次衰减, 越靠中心扭得越狠, 外缘几乎不扭
            float ang = intensity * swirl * 3.0 * f * f;
            vec2 sw = trns(warped, ang);

            tp += (sw - vp) * u_invsize;
        }
    }

    // ★ 纯扭曲输出: 黑核 / 光子环的绘制全部移除 ——
    //   黑洞本体改由 SingularityBulletType.draw() 按 FlameOut 原版手绘
    //   (随机彩色光环 + 尖刺 + 内落方块 + 不透明黑芯), shader 只负责空间扭曲,
    //   不再向画面添加任何颜色, 杜绝全屏发黑
    gl_FragColor = texture2D(u_texture, tp);
}