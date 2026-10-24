package zzw.content.units.abilities;

import arc.audio.Sound;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Groups;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import zzw.content.Z_StatusEffects;
import zzw.content.units.effects.RuneFx;

/**
 * 时间停止能力 (chronos)。
 *
 * <p>★ 与旧实现的区别: 旧版只是把 {@code Time.delta} 调成 3 再循环调用
 * {@code unit.update()} —— 那只是让 chronos "自己"瞬间多行动几次, 战场上
 * 完全没有时间被停住, 所以看起来毫无效果。现改为给范围内所有敌方单位施加
 * {@link Z_StatusEffects#timeStop} 状态:</p>
 * <ul>
 *   <li>{@code speedMultiplier = 0} —— 移速与转向速度归零;</li>
 *   <li>{@code reloadMultiplier = 0} —— 装填条不再前进;</li>
 *   <li>{@code damageMultiplier = 0} —— 时停期间打出的子弹无伤害;</li>
 * </ul>
 *
 * <p>视觉上在触发点生成 {@link RuneFx#timeField} 大魔法阵, 持续整个时停时长
 * (15 秒), 首段扩张、末段渐隐。</p>
 *
 * <p>★ 已发射的旧子弹不会被冻结 —— v160 没有暂停实体更新的接口, PU132 的全局
 * TimeStop 同样只影响单位行动。</p>
 */
public class TimeStopAbility extends Ability {
    /** 持续时间 (ticks) */
    public float duration;
    /** 充能时间 (ticks) */
    public float rechargeTime;
    /** 触发/生效范围 (像素) */
    public float range = 300f;
    /** 时停覆盖半径: 比触发范围大得多, 形成"领域"感 */
    public float fieldRadius = 600f;

    /** ★ PU132 原版: timeStopSound = UnitySounds.stopTime */
    public Sound timeStopSound;

    /** 当前充能计时器 */
    protected float timer = 0f;

    public TimeStopAbility(float duration, float rechargeTime) {
        this.duration = duration;
        this.rechargeTime = rechargeTime;
    }

    @Override
    public String localized(){
        return "时间停止";
    }

    @Override
    public void update(Unit unit) {
        timer += Time.delta;

        if (timer >= rechargeTime) {
            // 检查附近是否有敌方目标
            Teamc target = Units.closestTarget(unit.team, unit.x, unit.y, range,
                u -> !u.dead, t -> true);
            if (target != null) {
                trigger(unit);
            }
        }
    }

    /** 触发时间停止: 领域特效 + 音效 + 冻结范围内所有敌方单位。 */
    protected void trigger(Unit unit) {
        timer = 0f;

        if (timeStopSound != null) {
            timeStopSound.at(unit.x, unit.y, Mathf.random(0.9f, 1.1f));
        }

        RuneFx.timeField.at(unit.x, unit.y);

        float radius = fieldRadius;
        Groups.unit.each(u -> {
            if (u == unit || !u.isValid() || u.team == unit.team) return;
            if (u.dst(unit) <= radius + u.hitSize / 2f) {
                u.apply(Z_StatusEffects.timeStop, duration);
            }
        });
    }
}
