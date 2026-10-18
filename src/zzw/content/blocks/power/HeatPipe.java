package zzw.content.blocks.power;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.util.Time;
import arc.util.Eachable;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Unit;
import zzw.content.mechanics.torque.Utils;
import zzw.content.mechanics.torque.blocks.GraphBlock;
import zzw.content.util.GraphicUtils;

import static arc.Core.atlas;
import static mindustry.Vars.tilesize;

/**
 * 热管 (PU132 unity.world.blocks.distribution.HeatPipe 移植)
 *
 * <p>连接热量网络的导热管道: 贴图按 4 邻居连接位掩码自动拼接 (8x2 切片 16 变体)。
 * 高温时发光, 单位踩上会受到灼烧伤害。管道温度低于 0°C 或高于 225°C 时显示热色。</p>
 *
 * <p>★ 与 PU132 完全一致 (2026-10 回退): rotate=false, 位掩码直接按本地端口索引查表,
 * 贴图不随旋转绘制 —— 曾经为"保留放置大箭头"开过 rotate=true 并在掩码里做
 * (端口 + rotation) 换算, 但 draw() 不旋转贴图 / drawPlanRegion 旋转贴图 /
 * 掩码换算三者互相不一致, 表现为连接贴图错乱; 回退原版方案后三者天然统一。</p>
 *
 * <p>适配说明: unity.util.GraphicUtils → zzw.content.util.GraphicUtils;
 * onNeighboursChanged 为项目 GraphBlockBase 接口方法;
 * 每帧重算掩码为端口特有的兜底 (图重建过程可能漏发邻居变化通知)。</p>
 */
public class HeatPipe extends GraphBlock{
    final static Color baseColor = Color.valueOf("6e7080");
    /** 本地端口方向到位掩码位的映射 (右→bit0, 上→bit3, 左→bit2, 下→bit1, 与 PU132 一致) */
    final static int[] shift = new int[]{0, 3, 2, 1};
    /** 热色贴图 16 变体 (8x2 切片) */
    TextureRegion[] heatRegions, regions;

    /** 单位灼烧判定的计时器槽位。
     * <p>★ 与 PU132 差异: 原版写的是 {@code timer(dumpTime, 20f)}, 在 PU132 的旧版
     * Building.timer 语义下第一个参数是间隔; 但 Mindustry v160 的
     * {@link mindustry.gen.Building#timer(int, float)} 第一个参数是计时器槽位索引,
     * 索引 0 已被 Block.timerDump 占用, 直接传 dumpTime 会越界崩溃
     * ("Out of bounds! Max timer size is 1!")。因此这里单独申请一个槽位。</p> */
    protected final int timerUnitBurn = timers++;

    public HeatPipe(String name){
        super(name);
    }

    @Override
    public void load(){
        super.load();
        heatRegions = GraphicUtils.getRegions(heatRegion, 8, 2);
        regions = GraphicUtils.getRegions(atlas.find(name + "-tiles"), 8, 2);
    }

    @Override
    public void drawPlanRegion(BuildPlan req, Eachable<BuildPlan> list){
        // ★ PU132 原样复刻: 放置预览也显示与已建成/队列中热管的连接,
        //   预览只连接 HeatPipeBuild (不含其他热力方块), 贴图不随旋转绘制
        float scl = tilesize * req.animScale;
        int spriteIndex = 0;
        for(int i = 0; i < 4; i++){
            // i → 物理方向 (4-i)%4 = 右/下/左/上, 与掩码位序一致 (PU132 原样)
            var pt = Geometry.d4((4 - i) % 4).cpy().add(req.x, req.y);
            if(Vars.world.build(pt.x, pt.y) instanceof HeatPipeBuild){
                spriteIndex += 1 << i;
            }else{
                // 拖拽放置队列中同位置的热管也视为已连接
                final boolean[] found = {false};
                list.each(plan -> {
                    if(!found[0] && plan.x == pt.x && plan.y == pt.y){
                        found[0] = true;
                    }
                });
                if(found[0]){
                    spriteIndex += 1 << i;
                }
            }
        }
        Draw.rect(regions[spriteIndex], req.drawx(), req.drawy(), scl, scl);
    }

    public class HeatPipeBuild extends GraphBuild{
        /** 邻居连接位掩码 (贴图变体索引) */
        int spriteIndex;

        @Override
        public void onNeighboursChanged(){
            updateSpriteIndex();
        }

        /**
         * 按当前邻居连接位掩码重算贴图变体索引。
         *
         * <p>除 {@link #onNeighboursChanged()} 外, {@link #draw()} 每帧也会重算一次。
         * 原因: 图重建过程中的增量注册 / 提前 return 可能漏发"邻居变化"通知, 只在
         * onNeighboursChanged 里算一次会残留旧贴图, 表现为"实际已连上但贴图仍显示未连接"。
         * 每帧按真实邻居重算可彻底避免该残留。</p>
         */
        void updateSpriteIndex(){
            if(heat() == null) return;
            spriteIndex = 0;
            // rotate=false, 本地端口索引即物理方向, 直接查 shift 表 (PU132 原样;
            // 曾经为 rotate=true 做过 (n + rotation) % 4 换算, 已随 rotate 回退一并移除)
            heat().eachNeighbourValue(n -> spriteIndex += 1 << shift[n]);
        }

        @Override
        public void unitOn(Unit unit){
            if(timer(timerUnitBurn, 20f)){
                float intensity = Mathf.clamp(Mathf.map(heat().getTemp(), 400f, 1000f, 0f, 1f));
                unit.apply(StatusEffects.burning, intensity * 20f + 5f);
                unit.damage(intensity * 10f);
            }
        }

        @Override
        public void draw(){
            // ★ 每帧按真实邻居重算贴图掩码, 修复"连接了但贴图没变"的残留
            updateSpriteIndex();
            float temp = heat().getTemp();
            Draw.rect(regions[spriteIndex], x, y);
            if(temp < 273f || temp > 498f){
                Draw.color(Utils.tempColor(temp).add(baseColor));
                Draw.rect(heatRegions[spriteIndex], x, y);
                Draw.color();
            }
            drawTeamTop();
        }
    }
}
