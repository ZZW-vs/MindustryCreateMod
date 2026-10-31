package zzw.content.optics;

import arc.math.geom.Vec2;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import zzw.content.units.entities.WorldUnitEntity;

/**
 * 光束渲染器: 每 DrawLayer 绘制全部活跃光束。
 * <p>由 LightProcess.register() 注册到 Trigger.draw;
 * PU132 原版 Light 是 Drawc 实体自动渲染, 本移植为普通对象手动驱动。</p>
 *
 * <p>★ 甲板归属按"根光源"判定: 反射/续接段产生的子光自身没有 source,
 * 只有父光关系 (Light.cast 里 create → parent), 因此必须沿 parents 链回溯,
 * 否则这些子光既不被全局 pass 跳过, 也不会被甲板补绘 —— 直接被甲板贴图遮住。</p>
 */
public final class LightRenderer {
    private LightRenderer() {}

    /** 子世界坐标变换用的临时向量。 */
    private static final Vec2 tmp = new Vec2();

    /** 当前帧全部甲板建筑 (每帧只遍历一次单位组, 避免每条光都全量遍历)。 */
    private static final ObjectSet<Building> deckBuildings = new ObjectSet<>();

    static void draw() {
        deckBuildings.clear();
        // v160: EntityGroup 不再暴露 size/get(int), 直接用迭代器遍历
        for (Unit u : Groups.unit) {
            if (!(u instanceof WorldUnitEntity w)) continue;
            for (int i = 0; i < w.buildings.size; i++) deckBuildings.add(w.buildings.get(i));
        }

        for (Light l : Light.active) {
            // ★ 搭在子世界甲板上的光束: 由甲板在自身投影里补绘 (见 drawDeck),
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
     * <p>子光 (反射/续接段) 的起点就是父光终点, 与父光同处一个坐标系,
     * 因此同一套逆变换即可; 归属判定同样走根光源 (见 {@link #rootBuilding(Light)}),
     * 保证每一段都恰好画一次。</p>
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
            if (l.removed) continue;

            Building b = rootBuilding(l);
            if (b == null || !buildings.contains(b, true)) continue;

            // 主世界 → 子世界: (p - 单位坐标).rotate(-r) + 子世界中心
            tmp.set(l.x - ux, l.y - uy).rotate(-r).add(cx, cy);
            float sx = tmp.x, sy = tmp.y;
            tmp.set(l.endX - ux, l.endY - uy).rotate(-r).add(cx, cy);

            l.drawAt(sx, sy, tmp.x, tmp.y, false);
        }
    }

    /** 光束是否搭在某个子世界甲板上。 */
    private static boolean onDeck(Light l) {
        Building b = rootBuilding(l);
        return b != null && deckBuildings.contains(b);
    }

    /**
     * 光束的"根"来源建筑。
     *
     * <p>父光链上的子光没有 source (只有 parents), 因此向上回溯到第一个
     * 带光源的光; 取第一个父光即可 (合并光只影响亮度, 归属不分叉)。
     * 深度上限 8 仅作防御 (正常链长 = 光路反射段数)。</p>
     */
    private static Building rootBuilding(Light l) {
        for (int depth = 0; depth < 8 && l != null; depth++) {
            if (l.source != null) return l.source.building();
            l = l.firstParent();
        }
        return null;
    }
}
