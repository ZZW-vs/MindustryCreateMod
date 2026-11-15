package zzw.content.exp;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.storage.*;
import mindustry.graphics.*;
import mindustry.world.meta.*;
import zzw.content.exp.ExpOrbs;
import zzw.content.exp.ExpOrbs.ExpOrb;

import static arc.Core.atlas;
import static mindustry.Vars.*;

/**
 * 经验卸载器 - 从相邻的经验储罐/经验塔主动抽取经验, 沿朝向发射经验球到传送带
 *
 * 参考 vanilla v158.1 Unloader 的"从相邻方块抽取"机制
 * 与 exp-output (ExpHub) 的区别:
 *   - exp-output: 链接经验炮塔, 抽取炮塔击杀经验的 30% 分成 (原版 PU_V8 行为)
 *   - exp-unloader: 从相邻经验储罐/塔直接抽取经验, 不需要链接配置, 朝向决定发射方向
 */
public class ExpUnloader extends ExpTank {
    public float reloadTime = 30f;
    public Effect transferEffect = UnityFx.expLaser;
    public TextureRegion laser, laserEnd;
    /** 输出方向指示帧: exp-unloader-0..3 分别对应右/上/左/下四个输出方向 */
    public final TextureRegion[] frames = new TextureRegion[4];
    private final Seq<Building> tmp = new Seq<>();

    public ExpUnloader(String name){
        super(name);
        rotate = true;
        solid = false;
        schematicPriority = -16;
    }

    @Override
    public void init(){
        super.init();
        clipSize = Math.max(clipSize, 60f);
    }

    @Override
    public void load(){
        super.load();
        laser = atlas.find("create-exp-laser");
        laserEnd = atlas.find("create-exp-laser-end");
        // PU 原版 exp-unloader-0..3: 四个朝向的输出方向指示贴图
        for(int i = 0; i < frames.length; i++){
            frames[i] = atlas.find(name + "-" + i);
        }
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.speed, 60f / reloadTime, StatUnit.itemsSecond);
    }

    public class ExpUnloaderBuild extends ExpTankBuild {
        public float reload = reloadTime;

        @Override
        public void updateTile(){
            reload += edelta();

            // 主动从相邻的 ExpHolder (储罐/经验塔) 抽取经验
            // 不从炮台 (ExpTurret) 抽取 - 炮台需要经验升级, 由其 incExp 主动 push
            if(exp < expCapacity){
                int need = expCapacity - exp;
                for(int i = 0; i < proximity.size; i++){
                    Building b = proximity.get(i);
                    // 跳过炮台 (ExpTurretBuild), 只从储罐/塔抽取
                    if(b instanceof ExpHolder e && !(b instanceof ExpTurret.ExpTurretBuild)){
                        int got = e.unloadExp(need);
                        if(got > 0){
                            exp += got;
                            need -= got;
                            transferEffect.at(x, y, 0f, Color.white, b);
                            if(need <= 0) break;
                        }
                    }
                }
            }

            // 每 reloadTime tick 输出一颗经验 (朝向方向)
            // ★ 输出优先级 (应用户要求):
            //   1. 前方方块是 ExpHolder 且有剩余空间 → 直接注入经验空间 (不吐颗粒)
            //   2. 否则朝向前方吐出经验颗粒 (前方颗粒堆叠过多时暂停, 防止堆积)
            if(reload >= reloadTime && ExpOrbs.orbs(exp) > 0){
                int one = ExpOrbs.oneOrb(exp);
                int sent = 0;

                // 检查输出方向的相邻方块 (proximity 中朝向匹配的)
                Building front = front();
                if(front instanceof ExpHolder e && !(front instanceof ExpTurret.ExpTurretBuild)
                    && front.team == team && e.acceptOrb() && e.handleOrb(one)){
                    sent = one;
                }

                // 前方不是可注入的经验方块 → 吐经验颗粒;
                // 前方颗粒堆叠过多 (≥16) 时暂停输出, 等待拾取
                if(sent == 0){
                    if(frontExpOrbCount() < 16){
                        int a = handleExp(-one);
                        if(a < 0) ExpOrbs.dropExp(x, y, rotation * 90f, 4f, -a);
                    }
                }else{
                    handleExp(-sent);
                    // 注入成功时在输出端也播放一次传输特效
                    transferEffect.at(x, y, 0f, Color.white, front);
                }

                reload = 0f;
            }
        }

        @Override
        public void draw(){
            Draw.rect(region, x, y);

            // 经验存量填充层 (exp-unloader-exp)
            Draw.color(UnityPal.exp, Color.white, Mathf.absin(20, 0.6f));
            Draw.alpha(expf() * 0.6f);
            if(expRegion.found()){
                Draw.rect(expRegion, x, y);
            }
            Draw.color();

            // 输出方向指示: PU 原版 exp-unloader-0..3 对应右/上/左/下四个朝向,
            // 朝哪边输出就显示哪一侧的箭头; 有经验时箭头沿输出方向来回流动
            int dirFrame = rotation & 3;
            TextureRegion dir = frames[dirFrame];
            if(exp > 0 && dir.found()){
                float flow = Mathf.absin(Time.time * 3f, 1f);
                float ox = Geometry.d4x(dirFrame) * flow * 2f;
                float oy = Geometry.d4y(dirFrame) * flow * 2f;
                Draw.alpha(0.25f + 0.5f * flow);
                Draw.rect(dir, x + ox, y + oy);
                Draw.alpha(0.12f + 0.25f * (1f - flow));
                Draw.rect(dir, x + ox * 0.35f, y + oy * 0.35f);
                Draw.alpha(1f);
            }

            if(topRegion.found()){
                // ★ 顶盘是纯白蒙版 (PU 原版靠颜色调制), 之前直接画白色会把中心盖成白块,
                //   既遮住了下面的经验填充, 也让叠加的绿色光圈完全没有对比度
                Draw.color(UnityPal.exp, Color.white, 0.6f + 0.4f * Mathf.absin(Time.time * 2f, 1f));
                Draw.rect(topRegion, x, y, rotdeg());
                Draw.color();
            }

            // 中心经验光圈: 加法混合的辉光环, 抬到方块层之上并放到顶盘外圈,
            // 否则会被顶盘白色菱形吞掉 (这就是之前"一直看不到"的原因)
            float pulse = Mathf.absin(Time.time * 2f, 1f);
            Draw.z(Layer.block + 1f);
            Draw.blend(Blending.additive);
            Draw.color(UnityPal.exp);
            Draw.alpha(0.35f + 0.45f * pulse);
            Fill.circle(x, y, 5.5f + 1.6f * pulse);
            Draw.alpha(0.65f + 0.35f * pulse);
            Lines.stroke(1.3f);
            Lines.circle(x, y, 8f + 1.6f * pulse);
            Draw.blend();
            Draw.color();
            Draw.alpha(1f);
            Draw.z(Layer.block);

            // 绘制抽取激光 (到正在被抽取的相邻方块)
            drawExtractLasers();
        }

        /** 抽取/输出双向传输特效: 光束 + 沿光束流动的经验光点。 */
        protected void drawExtractLasers(){
            if(Mathf.zero(Renderer.laserOpacity)) return;
            Draw.z(Layer.power + 1f);
            for(int i = 0; i < proximity.size; i++){
                Building b = proximity.get(i);
                if(b instanceof ExpHolder e && !(b instanceof ExpTurret.ExpTurretBuild) && e.getExp() > 0){
                    drawBeam(b, true);
                }
            }
            // 朝向前方输出时也画一条向外的光束
            Building front = front();
            if(exp > 0 && front instanceof ExpHolder && front.team == team
                && !(front instanceof ExpTurret.ExpTurretBuild) && !(front instanceof ExpUnloaderBuild)){
                drawBeam(front, false);
            }
            Draw.reset();
        }

        protected void drawBeam(Building target, boolean inward){
            Tmp.v2.set(target);
            Tmp.v1.set(Tmp.v2).sub(this).nor().scl(size * tilesize / 2f);
            Tmp.v2.sub(Tmp.v1);
            Tmp.v1.add(this);

            float alpha = Math.min(1f, Renderer.laserOpacity * (Mathf.absin(5f, 0.3f) + 0.15f));
            Draw.alpha(alpha);
            Drawf.laser(laser, laserEnd, Tmp.v1.x, Tmp.v1.y, Tmp.v2.x, Tmp.v2.y, 0.35f);

            // 沿光束流动的经验光点 (抽取时流向自身, 输出时流向前方)
            int dots = 3;
            for(int i = 0; i < dots; i++){
                float p = (Time.time * 0.02f + i / (float)dots) % 1f;
                if(!inward) p = 1f - p;
                float px = Mathf.lerp(Tmp.v1.x, Tmp.v2.x, p);
                float py = Mathf.lerp(Tmp.v1.y, Tmp.v2.y, p);
                Draw.color(UnityPal.exp);
                Draw.alpha(Renderer.laserOpacity);
                Fill.circle(px, py, 1.7f);
            }
            Draw.color();
            Draw.alpha(1f);
        }

        @Override
        public void drawLight(){
            super.drawLight();
            Drawf.light(x, y, lightRadius * expf(), UnityPal.exp, 0.5f);
        }

        @Override
        public boolean acceptOrb(){
            return false;  // 不接受经验球 (只发射)
        }

        /**
         * 统计输出方向前方一格范围内的经验颗粒数量
         *
         * <p>用于防止颗粒无限堆积: 前方没有可注入的经验方块时才吐颗粒,
         * 若前方已堆叠大量未被拾取的颗粒 (≥16) 则暂停输出, 等待拾取者清空。</p>
         */
        protected int frontExpOrbCount(){
            float ex = x + Geometry.d4x(rotation) * tilesize;
            float ey = y + Geometry.d4y(rotation) * tilesize;
            final int[] count = {0};
            // 半径 8px (约一格) 圆形范围内的经验球计数
            Groups.bullet.intersect(ex - 8f, ey - 8f, 16f, 16f, b -> {
                if(b.type() instanceof ExpOrbs.ExpOrb) count[0]++;
            });
            return count[0];
        }

        @Override
        public int handleTower(int amount, float angle){
            return 0;  // 不接受经验塔传输
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(reload);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            reload = read.f();
        }
    }
}
