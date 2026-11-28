package zzw.content.mechanics.torque.modules;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import zzw.content.mechanics.torque.graph.HeatGraph;
import zzw.content.mechanics.torque.graphs.GraphHeat;
import zzw.content.mechanics.torque.meta.GraphType;

/**
 * 热量模块 (PU132 unity.world.modules.GraphHeatModule 移植)
 * <p>
 * 每个方块的热量组件, 记录当前热量 {@code heat} 和缓冲 {@code heatBuffer}。
 * <p>
 * 温度计算: {@code temp = heat / baseHeatCapacity}
 * <p>
 * 热量传递: 每帧从邻居和环境吸收/释放热量到 {@code heatBuffer},
 * 由 {@link HeatGraph#updateGraph()} 统一加到 {@code heat} 上。
 */
public class GraphHeatModule extends GraphModule<GraphHeat, GraphHeatModule, HeatGraph>{
    public float heat, heatBuffer;

    /**
     * 环境温度 (K), 默认 293.15 (20℃)。
     * <p>散热器等主动冷却方块会把此值调低, 让所在热网向冷却液对应的"温度池"散热。
     * 默认值即原有的固定环境温度, 不影响其它热学方块。
     */
    public float ambientTemp = 293.15f;

    /**
     * 热辐射率倍率 (默认 1)。主动冷却方块通入冷却液时提高此值以加快散热速度。
     */
    public float radiativityMul = 1f;

    @Override
    void applySaveState(HeatGraph graph, int index){}

    @Override
    void updateExtension(){}

    @Override
    void updateProps(HeatGraph graph, int index){
        float temp = getTemp();
        float capacity = this.graph.baseHeatCapacity;
        float cond = this.graph.baseHeatConductivity;
        heatBuffer = 0f;

        // 防呆: 热容量<=0 时 getTemp 会得到 Inf/NaN, 一旦写回 heat 就永久污染整张热网
        if(!Float.isFinite(temp) || !Float.isFinite(cond) || !Float.isFinite(capacity) || capacity <= 0f){
            heat = Math.max(0f, ambientTemp) * Math.max(0.0001f, capacity);
            return;
        }

        float clampedDelta = Mathf.clamp(Time.delta, 0, 1f / cond);
        // 记录所有热源的温度区间, 用于最后把缓冲能量钳制在"不越过任何热源"的范围内
        float minInfluence = ambientTemp, maxInfluence = ambientTemp;
        for(var n : neighbours.keys()){
            float nt = n.getTemp();
            if(!Float.isFinite(nt)) continue;
            heatBuffer += (nt - temp) * cond * clampedDelta;
            minInfluence = Math.min(minInfluence, nt);
            maxInfluence = Math.max(maxInfluence, nt);
        }
        heatBuffer += (ambientTemp - temp) * this.graph.baseHeatRadiativity * radiativityMul * clampedDelta;

        // ★ 边界修复: 热辐射倍率 (散热器通水 4 倍 / 冷冻液 10 倍) 可能让单帧换热量超过温差所需,
        //   直接把温度冲过目标温度 (如 -100℃ 被冲到 -150℃) 并因 ambient=min(floor,temp) 卡死.
        //   将本帧热量限制在 [min(temp,最低热源), max(temp,最高热源)] 对应的能量区间内,
        //   既不会越界, 也不影响正常的渐进升降温速度.
        float low = Math.min(minInfluence, temp);
        float high = Math.max(maxInfluence, temp);
        heatBuffer = Mathf.clamp(heatBuffer, (low - temp) * capacity, (high - temp) * capacity);
    }

    @Override
    void proximityUpdateCustom(){}

    @Override
    void display(Table table){
        if(networks.get(0) == null) return;
        String ps = Core.bundle.get("stat.unity.tempunit", "°C");
        table.row();
        table.table(sub -> {
            sub.clearChildren();
            sub.left();
            sub.label(() -> Strings.fixed(getTemp() - 273.15f, 2) + ps).color(Color.lightGray);
        }).left();
    }

    @Override
    void initStats(){
        setTemp(293.15f);
    }

    @Override
    void displayBars(Table table){}

    @Override
    HeatGraph newNetwork(){
        return new HeatGraph();
    }

    @Override
    void writeGlobal(Writes write){
        write.f(heat);
    }

    @Override
    void readGlobal(Reads reads, byte revision){
        heat = reads.f();
        heatBuffer = 0f;
    }

    @Override
    void writeLocal(Writes write, HeatGraph graph){}

    @Override
    Object[] readLocal(Reads read, byte revision){
        return null;
    }

    @Override
    public GraphType type(){
        return GraphType.heat;
    }

    /** 获取当前温度 (K) */
    @Override
    public float getTemp(){
        return heat / graph.baseHeatCapacity;
    }

    /** 设置温度 (K) */
    @Override
    void setTemp(float t){
        heat = t * graph.baseHeatCapacity;
    }

    /**
     * 直接增减热量 (供坩埚等系统做相变能量交换)。
     * <p>正值吸热升温, 负值放热降温; 温度由 {@link #getTemp()} 反映。
     */
    public void addHeatEnergy(float e){
        heat += e;
    }
}
