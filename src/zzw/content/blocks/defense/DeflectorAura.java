package zzw.content.blocks.defense;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.Effect;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.tilesize;

/**
 * 偏转光环 (PU132 Monolith deflector-aura)。
 *
 * <p>玩法定位与 shield-generator / deflect-generator 明确区分:
 * <ul>
 *   <li>不是护盾: 不吸收伤害、没有护盾血量、不会破碎, 也不参与经验升级;</li>
 *   <li>只干扰弹道: 周期性对范围内的敌方实体子弹做"切向拨偏 + 降速",
 *       子弹仍属于敌方, 只是打不到原目标;</li>
 *   <li>对激光/光束类 (reflectable = false) 完全无效, 因此不能当通用防御用。</li>
 * </ul></p>
 *
 * <p>性能取舍: 不做每帧全量单位扫描 —— 只用 {@link Groups#bullet} 的空间索引
 * ({@code intersect}) 按脉冲 (默认 5 tick 一次) 处理, 且每次脉冲有
 * {@link #maxPerPulse} 上限, 弹幕再密也不会出现单帧处理爆炸式增长。</p>
 */
public class DeflectorAura extends Block{
    /** 光环半径 (像素) */
    public float auraRadius = 168f;
    /** 每个脉冲对单颗子弹的偏转概率 */
    public float deflectChance = 0.35f;
    /** 每次偏转的角度 (度) */
    public float deflectAngle = 38f;
    /** 偏转后的速度保留比例 */
    public float speedMultiplier = 0.86f;
    /** 单个脉冲最多处理多少颗子弹 (性能上限) */
    public int maxPerPulse = 8;
    /** 脉冲间隔 (tick) */
    public float pulseTime = 5f;
    /** 光环颜色 */
    public Color auraColor = Pal.lancerLaser;

    public TextureRegion topRegion;

    /** 偏转时的火花 */
    public Effect deflectEffect = new Effect(18f, e -> {
        Draw.color(e.color);
        Draw.alpha(e.fout());
        Lines.stroke(2f * e.fout());
        Lines.spikes(e.x, e.y, 2f + 5f * e.fin(), 3f + 6f * e.fin(), 3);
        Draw.color();
    });

    public DeflectorAura(String name){
        super(name);
        solid = true;
        update = true;
        sync = true;
        hasPower = true;
        consumesPower = true;
        configurable = false;
        // 只耗电, 不吃物品/液体: 定位是"电子战装置"而不是护盾发生器
        consumePower(6f);
    }

    @Override
    public void load(){
        super.load();
        topRegion = Core.atlas.find(name + "-top");
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.range, auraRadius / tilesize, StatUnit.blocks);
        stats.add(Stat.baseDeflectChance, deflectChance, StatUnit.none);
        stats.add(Stat.reload, pulseTime / 60f, StatUnit.seconds);
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("power", (DeflectorAuraBuild b) -> new Bar(
            () -> Core.bundle.get("bar.power", "电力"),
            () -> Pal.power,
            () -> b.power == null ? 0f : Mathf.clamp(b.power.status)
        ));
    }

    public class DeflectorAuraBuild extends Building{
        protected float pulse;

        @Override
        public void updateTile(){
            float eff = power == null ? 0f : Mathf.clamp(power.status);
            if(eff <= 0.01f) return;

            pulse += delta();
            if(pulse < pulseTime) return;
            pulse = 0f;

            final float px = x, py = y;
            final float r = auraRadius, r2 = r * r;
            final int[] count = {0};

            Groups.bullet.intersect(px - r, py - r, r * 2f, r * 2f, b -> {
                if(count[0] >= maxPerPulse) return;
                if(b.team == team || b.type == null || !b.type.reflectable) return;

                float dx = b.x - px, dy = b.y - py;
                if(dx * dx + dy * dy > r2) return;
                if(!b.type.absorbable) return;
                if(!Mathf.chance(deflectChance)) return;

                deflect(b, px, py);
                count[0]++;
            });
        }

        /** 切向拨偏: 按子弹相对圆心的位置决定偏转方向, 让它"擦着光环滑开"。 */
        protected void deflect(Bullet b, float px, float py){
            float ang = b.rotation();
            // 叉积判断子弹在弹道哪一侧, 保证同一侧的子弹朝同一方向偏, 视觉上像被光滑曲面推开
            float cross = Mathf.cosDeg(ang) * (b.y - py) - Mathf.sinDeg(ang) * (b.x - px);
            float side = cross >= 0f ? 1f : -1f;

            b.rotation(ang + side * deflectAngle);
            b.vel.setAngle(b.rotation()).scl(speedMultiplier);
            deflectEffect.at(b.x, b.y, b.rotation(), auraColor);
        }

        @Override
        public void draw(){
            Draw.rect(region, x, y);
            if(topRegion.found()){
                // 顶环缓慢自转, 与静态本体形成对比
                Draw.rect(topRegion, x, y, Time.time * 0.8f);
            }
            drawAura();
        }

        protected void drawAura(){
            float eff = power == null ? 0f : Mathf.clamp(power.status);
            if(eff <= 0.01f) return;

            float r = auraRadius * (0.35f + 0.65f * eff);
            float z = Draw.z();

            Draw.z(Layer.shields);
            Draw.color(auraColor);
            Draw.alpha(0.10f + 0.05f * Mathf.absin(8f, 1f));
            Fill.circle(x, y, r);
            Draw.alpha(0.55f);
            Lines.stroke(1.4f);
            Lines.circle(x, y, r);

            // 三段旋转弧: 表现"力场在持续扫描"
            Lines.stroke(2.4f);
            for(int i = 0; i < 3; i++){
                Lines.arc(x, y, r, 0.16f, Time.time * 0.7f + i * 120f);
            }
            Draw.z(z);
            Draw.reset();
        }
    }
}
