package zzw.content.mechanics.torque.graph;

import zzw.content.mechanics.torque.modules.GraphHeatModule;

/**
 * 热量网络 (PU132 unity.world.graph.HeatGraph 移植)
 * <p>
 * 管理连接的热量模块之间的热量传递。
 * <p>
 * 每帧将各模块的 {@code heatBuffer} (来自邻居传导和环境辐射) 加到 {@code heat} 上,
 * 并累计 {@code lastHeatFlow} 用于统计。
 */
public class HeatGraph extends BaseGraph<GraphHeatModule, HeatGraph>{
    float lastHeatFlow;

    @Override
    public HeatGraph create(){
        return new HeatGraph();
    }

    @Override
    void copyGraphStatsFrom(HeatGraph graph){}

    @Override
    void updateOnGraphChanged(){}

    @Override
    void updateGraph(){
        lastHeatFlow = 0f;
        for(int i = 0, len = connected.size; i < len; i++){
            GraphHeatModule module = connected.orderedItems().get(i);
            module.heat += module.heatBuffer;
            // ★ 边界兜底: 任何异常 (容量为 0 / 外部直接改 heat) 都可能产生负热量或 NaN,
            //   负开尔文会让热色叠加常亮且无法自行恢复, 这里统一钳到 0K 并清理非有限值
            if(!Float.isFinite(module.heat) || module.heat < 0f) module.heat = 0f;
            lastHeatFlow += module.heatBuffer;
        }
    }

    @Override
    void updateDirect(){}

    @Override
    void addMergeStats(GraphHeatModule module){}

    @Override
    void mergeStats(HeatGraph graph){
        lastHeatFlow += graph.lastHeatFlow;
    }
}
