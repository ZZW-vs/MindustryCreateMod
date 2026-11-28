package zzw.content.blocks.power;

import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.mechanics.torque.modules.GraphHeatModule;

/**
 * 热量发生器基类 (PU132 unity.world.blocks.power.HeatGenerator 移植)
 *
 * <p>向热量图注入热量的方块基类: 每帧向所在网络增加
 * {@code (maxTemp - 当前温度) * mulCoeff * mul} 的热量 (带 mul 上限版本)。</p>
 *
 * <p>适配说明: 继承 zzw.content.mechanics.torque.blocks.GraphBlock (完整版图方块)。</p>
 */
public class HeatGenerator extends GraphBlock{
    /** 目标最高温度 (K) */
    protected float maxTemp = 9999f;
    /** 热量系数 (每帧升温比例) */
    protected float mulCoeff = 0.5f;

    public HeatGenerator(String name){
        super(name);
    }

    public class HeatGeneratorBuild extends GraphBuild{
        /**
         * 向热量图注入热量.
         * <p>mul 为产能系数 (如燃烧效率/热液量), 网络温度越接近 maxTemp 增量越小 (渐近收敛)。</p>
         * <p>★ 单帧注入量钳制到"恰好升到 maxTemp"所需热量: infi-heater (mulCoeff=5) 或
         * 多反射镜集热器 (mul&gt;1) 时, 原公式单帧注入可超过温差数倍, 温度会先冲过
         * maxTemp 再缓慢回落, 表现为数值剧烈跳动与热色闪烁。</p>
         */
        protected void generateHeat(float mul){
            GraphHeatModule hgraph = heat();
            if(hgraph == null || !Float.isFinite(mul) || mul <= 0f) return;

            float gap = maxTemp - hgraph.getTemp();
            if(!Float.isFinite(gap) || gap <= 0f) return;

            float add = gap * mulCoeff * mul;
            hgraph.heat += Math.min(add, gap * hgraph.graph.baseHeatCapacity);
        }

        /** 带注入上限版本: 每帧最多注入 limit 热量 (太阳能集热器用) */
        protected void generateHeat(float limit, float mul){
            GraphHeatModule hgraph = heat();
            if(hgraph == null || !Float.isFinite(limit) || limit <= 0f || !Float.isFinite(mul) || mul <= 0f) return;

            float gap = maxTemp - hgraph.getTemp();
            if(!Float.isFinite(gap) || gap <= 0f) return;

            float add = Math.min(limit, gap * mulCoeff * mul);
            if(!Float.isFinite(add) || add <= 0f) return;
            hgraph.heat += Math.min(add, gap * hgraph.graph.baseHeatCapacity);
        }
    }
}
