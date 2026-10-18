package zzw.content.blocks.distribution;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.world.blocks.distribution.Conveyor;

/**
 * ShadowedConveyor 带阴影传送带 (PU_V8 机械传送带原版形态)。
 *
 * <p>参考: PU132 main/src/unity/world/blocks/distribution/ShadowedConveyor.java。
 * 在原版 Conveyor 的基础上补画两处阴影贴图:</p>
 * <p>- 前方没有同种传送带时, 画正向阴影 (物品"进入"边缘的暗部);</p>
 * <p>- 后/左/右三向没有同种同朝向邻居时, 画反向阴影 (物品"离开"边缘的暗部)。</p>
 *
 * <p>完全保持原版 Conveyor 的运输机制 (单件运输), 仅视觉上增加立体感。</p>
 */
public class ShadowedConveyor extends Conveyor{
    /** 阴影贴图 (@-shadow) */
    TextureRegion shadowRegion;

    public ShadowedConveyor(String name){
        super(name);
    }

    @Override
    public void load(){
        super.load();
        shadowRegion = arc.Core.atlas.find(name + "-shadow");
    }

    /**
     * 阴影传送带建筑体 —— 继承原版 ConveyorBuild, 只扩展绘制与邻居判定。
     */
    public class ShadowedConveyorBuild extends ConveyorBuild{
        /** 后/左/右三向是否存在同种同朝向传送带 (决定是否画反向阴影) */
        boolean looking;

        @Override
        public void draw(){
            super.draw();

            // PU_V8 影子渲染: 覆盖在方块层之上
            Draw.z(Layer.block);
            if(shadowRegion.found()){
                // 前方无同类传送带 → 正向阴影
                if(nextc == null || block != nextc.block) Draw.rect(shadowRegion, x, y, rotdeg());
                // 三向无同向同类邻居 → 反向阴影
                if(!looking) Draw.rect(shadowRegion, x, y, rotdeg() + 180f);
            }
        }

        @Override
        public void onProximityUpdate(){
            super.onProximityUpdate();
            // 三向任一邻居与本方块同种且面朝本格 → 已被"阴影带"覆盖
            looking = sameOriented(back()) || sameOriented(left()) || sameOriented(right());
        }

        /** 邻居是否为同种方块且朝向本格 */
        private boolean sameOriented(Building other){
            return other != null && other.block == block
                && other.tile.relativeTo(tile) == other.rotation;
        }
    }
}
