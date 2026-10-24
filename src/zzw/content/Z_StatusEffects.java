package zzw.content;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.units.StatusEntry;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.StatusEffect;
import zzw.content.exp.UnityFx;
import zzw.content.units.effects.ParticleFx;

/**
 * PU132 自定义状态效果注册 (unity.content.UnityStatusEffects 按需移植)。
 *
 * <p>收录炮台武器弹所需的状态效果; 特效本体在 {@link ParticleFx}。</p>
 *
 * @author PU132 原作, 移植: zzw
 */
public class Z_StatusEffects{

    /**
     * 蓝色燃烧 (PU132 UnityStatusEffects.blueBurn)。
     *
     * <p>celsius/kelvin 等 advance 派系低温炮…… 不对 —— advance 的"蓝焰"其实是
     * 冷色调的高温等离子燃烧: 0.14/s 持续伤害 + advance 色光尘, 与 wet/freezing 互斥。</p>
     */
    public static StatusEffect blueBurn;

    /** 虚弱 (PU132 UnityStatusEffects.weaken): 伤害/生命 -25%, 移速 -50% */
    public static StatusEffect weaken;

    /** 瘫痪 (PU132 UnityStatusEffects.disabled): 移速/装填归零 + 缴械 */
    public static StatusEffect disabled;

    /** 扭曲 (PU132 UnityStatusEffects.distort): 力场内单位移速降至 35% */
    public static StatusEffect distort;

    /**
     * 时间停止 (chronos 能力): 移速 / 转向 / 装填 / 伤害全部归零。
     *
     * <p>★ 替代 PU132 的全局 TimeStop 系统: v160 没有暂停世界的接口,
     * 这里用三个归零倍率让敌方单位在时停期间"定住"—— 不移动、不转向、
     * 装填条不前进、打出的子弹伤害为 0。已发射的旧子弹不受影响 (原版
     * TimeStop 同样不暂停子弹实体)。</p>
     */
    public static StatusEffect timeStop;

    public static void load(){
        blueBurn = new StatusEffect("blue-burn"){{
            damage = 0.14f;
            effect = ParticleFx.blueBurnEffect;
            color = Color.valueOf("a3e3ff");

            init(() -> {
                opposite(StatusEffects.wet, StatusEffects.freezing);
            });
        }};

        // PU132 UnityStatusEffects.weaken: sedec/trigintaduo 治疗锥对敌人施加的削弱
        // (与原版 weaken 数值不同: 原版无 healthMultiplier)
        weaken = new StatusEffect("weaken"){{
            damageMultiplier = 0.75f;
            healthMultiplier = 0.75f;
            speedMultiplier = 0.5f;
        }};

        // PU132 UnityStatusEffects.disabled: trigintaduo 治疗核弹对敌人的瘫痪
        // (原版 unmoving 只锁移动, 这里的 disabled 额外归零装填并缴械)
        disabled = new StatusEffect("disabled"){{
            reloadMultiplier = 0f;
            speedMultiplier = 0f;
            disarm = true;
        }};

        // PU132 UnityStatusEffects.distort: fractal-laser-turret 力场对敌人的减速
        // ★ v160 适配: 原版 effect.at(..., 45f) 的 data 为方块旋转角, 这里保持一致
        distort = new StatusEffect("distort"){
            {
                speedMultiplier = 0.35f;
                color = Pal.lancerLaser;
                effect = UnityFx.distortFx;
            }

            @Override
            public void update(Unit unit, StatusEntry entry){
                // 与 PU132 一致: 不调用 super, 仅播放带旋转角数据的扭曲特效
                if(effect != Fx.none && Mathf.chanceDelta(effectChance)){
                    Tmp.v1.rnd(unit.type.hitSize / 2f);
                    effect.at(unit.x + Tmp.v1.x, unit.y + Tmp.v1.y, 0f, 45f);
                }
            }
        };

        // chronos 时间停止: 冻结敌方单位的移动/转向/装填/输出
        timeStop = new StatusEffect("time-stop"){{
            speedMultiplier = 0f;
            reloadMultiplier = 0f;
            damageMultiplier = 0f;
            color = Color.valueOf("e04a50");
            // 单位身上不额外喷粒子 (战场视觉交给 RuneFx.timeField 的大魔法阵)
            effect = Fx.none;
            show = false;
        }};
    }
}
