package zzw.content.optics;

import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import zzw.content.units.entities.WorldUnitEntity;

/**
 * 光束渲染器: 每 DrawLayer 绘制全部活跃光束。
 * <p>由 LightProcess.register() 注册到 Trigger.draw;
 * PU132 原版 Light 是 Drawc 实体自动渲染, 本移植为普通对象手动驱动。</p>
 */
public final class LightRenderer {
    private LightRenderer() {}

    /** 子世界坐标变换用的临时向量。 */
    private static final Vec2 tmp = new Vec2();

    static void draw() {
        for (Light l : Light.active) {
            // ★ 搭在子世界甲板上的光源: 由甲板在自身投影里补绘 (见 drawDeck),
            //   这里必须跳过 —— Trigger.draw 早于 Groups.draw, 层级低于甲板,
            //   直接画会被甲板贴图完全遮住
            if (!l.removed && !onDeck(l)) l.draw();
        }
    }

    /**
     * 在子世界投影内重绘属于该甲板的所有光束。
     *
     * <p>光束坐标是主世界坐标 (建筑 updateTile 时子世界建筑被临时映射到主世界),
     * 而子世界渲染用的是一套临时旋转/平移过的投影, 因此这里先把两个端点做
     * "主世界 → 子世界" 逆变换, 再以 {@code emitLight=false} 绘制 (甲板下方的
     * 原版光圈本来就被甲板遮住)。</p>
     *
     * @param buildings 甲板携带的子世界建筑表
     * @param ux        甲板单位主世界 x
     * @param uy        甲板单位主世界 y
     * @param r         子世界 → 主世界的旋转角 (rotation - 90°)
     * @param cx        子世界中心 x
     * @param cy        子世界中心 y
     */
    public static void drawDeck(Seq<Building> buildings, float ux, float uy, float r, float cx, float cy) {
        if (buildings == null || buildings.isEmpty()) return;

        for (int i = 0; i < Light.active.size; i++) {
            Light l = Light.active.get(i);
            if (l.removed || l.source == null) continue;

            Building b = l.source.building();
            if (b == null || !buildings.contains(b, true)) continue;

            // 主世界 → 子世界: (p - 单位坐标).rotate(-r) + 子世界中心
            tmp.set(l.x - ux, l.y - uy).rotate(-r).add(cx, cy);
            float sx = tmp.x, sy = tmp.y;
            tmp.set(l.endX - ux, l.endY - uy).rotate(-r).add(cx, cy);

            l.drawAt(sx, sy, tmp.x, tmp.y, false);
        }
    }

    /** 光源是否搭在某个子世界甲板上。 */
    private static boolean onDeck(Light l) {
        if (l.source == null) return false;
        Building b = l.source.building();
        if (b == null) return false;

        // v160: EntityGroup 不再暴露 size/get(int), 直接用迭代器遍历
        for(Unit u : Groups.unit){
            if (u instanceof WorldUnitEntity w && w.buildings.contains(b, true)) return true;
        }
        return false;
    }
}
