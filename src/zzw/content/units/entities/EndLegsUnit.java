package zzw.content.units.entities;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.UnitEntity;

/**
 * End 阵营飞行单位 (简化版防作弊, 完全用原版方法实现)
 *
 * ★ 仅用于 End 阵营飞行单位 (flying=true, 无腿):
 *   - enigma, voidVessel, chronos, opticaecus
 *   - 对于 End 阵营腿单位 (ravager/desolation), 请用 EndGroundUnit (extends LegsUnit)
 *
 * 替代 PU132 的 AntiCheatVariables 9参数系统 + EndComp @EntityComponent mixin
 * 直接在 EndLegsUnit 中内嵌简化版防作弊逻辑, 参数按 PU132 voidVessel/chronos 配置硬编码
 *
 * 防作弊机制 (完全照搬 PU132 EndComp 核心, 简化参数配置):
 * 1. 多槽位无敌帧 (invFrames[]): 每次受伤占用一个槽位, 轮询使用
 * 2. 单次伤害上限 (maxDamageTaken): 上限内的伤害全额结算
 * 3. 超量抗性 (resist): 仅超过单次上限的部分累积抗性并衰减,
 *    正常武器命中不会被抗性/曲线削减 (旧曲线在阈值处会直接把伤害归零)
 * 4. 怒气系统 (aggression): 受到超量伤害后加速武器 reload
 * 5. 死亡拒绝: trueHealth > 0 时拒绝 destroy/kill/remove
 *
 * ★ 参数按单位 health 比例计算 (无 antiCheatType 时的回退值):
 *   maxDamageTaken        = health / 4f
 *   resistScl             = 0.2f
 *   resistDuration        = 6f * 60f
 *   resistTime            = 3f * 60f
 *   invincibilityDuration = 15f
 *   invincibilityArray    = 4
 */
public class EndLegsUnit extends UnitEntity implements zzw.content.Factionc {

    /** 派系 (FactionMeta 按单位类型登记, End 单位统一为 end 派系)。 */
    @Override
    public zzw.content.Faction faction(){
        return zzw.content.FactionMeta.map(type);
    }

    // 防作弊运行时数据
    private float trueHealth, trueMaxHealth;
    private float aggression = 0f;
    private float aggressionTime = 0f;
    private float[] invFrames;
    private int invIndex = 0;
    private float invTimer = 0f;
    private float resist, resistMax, resistTime;
    /** 所属阵营 (PU132 trueTeam): 被作弊改队后, 台账充足时强制恢复原阵营 */
    private mindustry.game.Team trueTeam = null;
    /** 单位配置的防作弊参数集 (add() 时从 UnitType.antiCheatType 读取, 无配置回退默认值) */
    private zzw.content.units.anticheat.EndCheatVars ac;

    /**
     * ★ 大激光锁定剩余时间 (秒)。
     *
     * <p>发射大激光 (OppressionLaserBulletType) 期间, 由充能状态或激光子弹每帧刷新;
     * 大于 0 时本帧禁止移动和转向, 表现与压迫者大招一致 (虚空容器使用)。</p>
     */
    public float laserLock = 0f;
    /** 锁定开始时的朝向 (锁定期间固定不变, 使激光方向保持不变) */
    private float lockRotation = 0f;
    /** 是否正处于大激光锁定状态 (用于记录进入锁定瞬间的朝向) */
    private boolean lockActive = false;

    public static EndLegsUnit create() {
        return new EndLegsUnit();
    }

    /** 已使用的复活次数 (达到 reviveLimit 后走正常死亡)。 */
    private int revivesUsed;
    /** "受伤过快"统计: 窗口起点与窗口内累计原始伤害。 */
    private float fastDamageStart, fastDamage;

    /** 复活次数上限: 读取单位配置, 再受全局硬上限 5 限制。 */
    private int reviveLimit() {
        if (type instanceof zzw.content.type.UnityUnitType u) {
            return Math.min(u.reviveLimit, zzw.content.units.anticheat.EndCheatVars.MAX_REVIVES);
        }
        return 0;
    }

    @Override
    public void add() {
        if (added) return;
        super.add();
        // 读取单位配置的防作弊参数 (无配置时回退 PU132 voidVessel/chronos 默认值)
        if (type instanceof zzw.content.type.UnityUnitType u && u.antiCheatType != null) {
            ac = u.antiCheatType;
        }
        // 初始化防作弊数据 (无敌帧槽位数按参数配置, 无配置回退 4, 与 PU132 EndComp.add 一致)
        trueHealth = type.health;
        trueMaxHealth = type.health;
        zzw.content.units.anticheat.EndHealthLedger.put(this, trueHealth);
        trueTeam = team;
        invFrames = new float[ac != null ? ac.invincibilityArray : 4];
        revivesUsed = 0;
        fastDamageStart = 0f;
        fastDamage = 0f;
        // 登记进全局防作弊管理器 (PU132 Unity.antiCheat.addUnit)
        zzw.util.AntiCheat.addUnit(this);
    }

    @Override
    public void update() {
        // ★ 防作弊更新 (PU132 EndComp.update L141-178, 在 super.update() 之前)
        // ★ 阵营恢复 (PU132): 被作弊改队后, 只要台账仍充足就强制恢复原阵营
        if (trueTeam != null && team != trueTeam && trueHealth >= Math.max(trueMaxHealth / 100f, 150f)) {
            team = trueTeam;
        }
        // 血量防回退 (防作弊)
        // ★ 血量双轨 (修正): 台账独立扣减, 不回充 health (同 EndGroundUnit)
        if (maxHealth < trueMaxHealth || Float.isNaN(maxHealth)) maxHealth = trueMaxHealth;
        trueMaxHealth = maxHealth;
        if (trueHealth > 0f && revivesUsed < reviveLimit() && !zzw.util.ReviveSeal.isSealed(this)) dead = false;

        // 抗性衰减 (按单位配置参数 resistDuration, 无配置回退 6*60)
        float resistDuration = ac != null ? ac.resistDuration : 6f * 60f;
        if (resistTime <= 0f) {
            resist -= resistMax / resistDuration;
            resist = Math.max(resist, 0f);
        } else {
            resistTime -= Time.delta;
        }
        if (resist <= 0f) {
            resistMax = 0f;
        }

        // 无敌帧倒计时
        for (int i = 0; i < invFrames.length; i++) {
            invFrames[i] = Math.max(invFrames[i] - Time.delta, 0f);
        }
        if (invTimer > 0f) invTimer -= Time.delta;

        // 怒气系统: 加速武器 reload
        if (aggression > 0f) {
            for (WeaponMount mount : mounts) {
                mount.reload = Math.max(0f, mount.reload - (aggression * Time.delta));
            }
            if (aggressionTime > 0f) {
                aggressionTime -= Time.delta;
            } else {
                aggression = Mathf.lerpDelta(aggression, 0f, 0.1f);
            }
        }

        // ★ 大激光充能期间锁定 (与压迫者大招一致): 充能中时刷新锁定, 禁止移动/转向
        if (mounts != null) {
            for (WeaponMount mount : mounts) {
                if (mount.charging && mount.weapon != null
                    && mount.weapon.bullet instanceof zzw.content.units.bullets.OppressionLaserBulletType) {
                    laserLock = 5f;
                    break;
                }
            }
        }
        boolean laserLocked = laserLock > 0f;
        if (laserLocked) {
            // 进入锁定瞬间记录朝向, 锁定期间保持不变 (激光方向固定)
            if (!lockActive) {
                lockRotation = rotation;
                lockActive = true;
            }
            vel.setZero();
            laserLock = Math.max(0f, laserLock - Time.delta);
        }

        super.update();

        // ★ super.update() 后再次锁定 (控制器可能在本帧改了速度/朝向)
        if (laserLocked) {
            vel.setZero();
            rotation = lockRotation;
        }
        if (laserLock <= 0f && lockActive) {
            lockActive = false;
        }

        // ★ 只做"显示血量不得超过台账"的钳制, 绝不把台账回写成显示血量。
        //   旧实现的 else 分支每帧 trueHealth = health, 台账会被原始伤害拖着一起归零,
        //   kill()/destroy() 时台账已经 <= 0 → 复活时有时无。
        //   秒杀/作弊把 health 清零时, 只要内部台账还有血就回溯 (只在异常时触发)。
        if (trueHealth > 0f && revivesUsed < reviveLimit() && !zzw.util.ReviveSeal.isSealed(this)) {
            if (this.health > trueHealth) this.health = Math.min(maxHealth, trueHealth);
            if (this.health <= 0f) this.health = Math.min(maxHealth, trueHealth);
            dead = false;
        }
    }

    @Override
    public void damage(float amount) {
        // ★ 防作弊伤害处理 (PU132 EndComp.damage 结构 + 段身单位的"超量伤害才衰减"规则)
        if (Float.isNaN(amount) || Float.isInfinite(amount) || amount <= 0f) return;

        // ★ 受伤过快判定 (记忆/台账介入): 1 秒窗口内累计原始伤害超过最大生命 15% 的部分,
        //   按 50% 额外压入真实血量台账, 让爆发/持续高 DPS 更快耗尽"隐藏血量", 避免无脑硬复活。
        if (Time.time - fastDamageStart > zzw.content.units.anticheat.EndCheatVars.FAST_DAMAGE_WINDOW) {
            fastDamageStart = Time.time;
            fastDamage = 0f;
        }
        fastDamage += amount;
        float fastThreshold = trueMaxHealth * zzw.content.units.anticheat.EndCheatVars.FAST_DAMAGE_RATIO;
        if (fastDamage > fastThreshold) {
            float fastExtra = Math.min(amount, fastDamage - fastThreshold)
                    * zzw.content.units.anticheat.EndCheatVars.FAST_DAMAGE_LEDGER_PENALTY;
            if (fastExtra > 0f) {
                trueHealth = Math.max(trueHealth - fastExtra, 0f);
                zzw.content.units.anticheat.EndHealthLedger.put(this, trueHealth);
            }
        }

        // 无敌帧中, 拒绝伤害
        if (invFrames[invIndex] > 0f) return;

        float resistScl = ac != null ? ac.resistScl : 0.2f;
        float invincibilityDuration = ac != null ? ac.invincibilityDuration : 15f;
        float resistTimeMax = ac != null ? ac.resistTime : 3f * 60f;
        // 单次伤害上限: 上限之内的正常弹丸全额结算 (旧实现先被 h/15~h/10 封顶,
        // 再被 150~900 点护甲吃掉大半, 实际单次只有几十点 → 小单位"打不动")
        float maxDamageTaken = ac != null ? ac.maxDamageTaken : trueMaxHealth / 4f;

        float nextAmount = Math.min(amount, maxDamageTaken);

        // ★ 只有超过单次上限的"超量伤害"(作弊/秒杀级)才进入抗性衰减 + 怒气;
        //   正常武器命中不再被抗性削减 (与 SegmentWormEntity 的修复口径一致)
        if (amount > maxDamageTaken) {
            float excess = amount - maxDamageTaken;
            float immunityAdd = (float)Math.pow(excess / maxDamageTaken, 2f) * 2f;
            resist = Math.min(resist + Math.min(immunityAdd, 10f), 100f);
            if (Float.isInfinite(resist)) resist = Float.MAX_VALUE;
            resistMax = Math.max(resistMax, resist);
            resistTime = resistTimeMax;
            aggression += Math.min(excess / (trueMaxHealth / 5f), 1.5f);
            aggression = Math.min(aggression, 4f);
            aggressionTime = 5f * 60f;
            nextAmount = maxDamageTaken / Math.max(resist * resistScl + 1f, 1f);
        }

        amount = nextAmount;

        // 占用无敌帧槽位
        invFrames[invIndex] = invincibilityDuration;
        if (invTimer <= 0f) {
            invIndex++;
            invIndex %= invFrames.length;
            invTimer = 3f;
        }

        // 台账扣减 (按防作弊上限/曲线/抗性后的金额)
        float tmpAmount = Math.max(amount - armor, Vars.minArmorDamage * amount) / healthMultiplier;

        if (tmpAmount > 0) {
            float shieldDamage = Math.min(Math.max(shield, 0), tmpAmount);
            tmpAmount -= shieldDamage;

            if (tmpAmount > 0) {
                trueHealth -= tmpAmount;
                zzw.content.units.anticheat.EndHealthLedger.put(this, trueHealth);
            }
        }

        // ★ 原版显示血量并行扣减 (PU132 关键机制): 显示血量比台账先归零
        float rawAmount = Math.max(amount - armor, Vars.minArmorDamage * amount) / healthMultiplier;
        if (rawAmount > 0) {
            float shieldDamage = Math.min(Math.max(shield, 0), rawAmount);
            rawAmount -= shieldDamage;
            if (rawAmount > 0) {
                health -= rawAmount;
            }
        }
        this.hitTime = 1f;
        if (health <= 0f && !dead) {
            kill();
        }
    }

    /**
     * 死亡拒绝+复活 (PU132 EndComp.destroy/remove 完整移植):
     * 台账 (trueHealth) 未耗尽时, 播放红色蓄力特效并复活。
     */
    private boolean denyDeath() {
        // ★ 终局炮台封印: 被湮灭时不走任何复活逻辑
        if (zzw.util.ReviveSeal.isSealed(this)) return false;
        // ★ 以哈希台账为准: 即使实体字段被秒杀/作弊清零, 内部还有血就回溯
        trueHealth = zzw.content.units.anticheat.EndHealthLedger.get(this, trueHealth);
        if (trueHealth > 0f) {
            // ★ 复活次数上限: 用尽后不再复活 (最多 MAX_REVIVES 次)
            if (revivesUsed >= reviveLimit()) return false;
            revivesUsed++;
            aggression = 4f;
            aggressionTime = 10f * 60f;
            // 复活: 只回充台账的 40% (弱化"加血条"观感)
            health = Math.max(health, Math.min(trueHealth, trueMaxHealth)
                    * zzw.content.units.anticheat.EndCheatVars.REVIVE_HEAL_RATIO);
            dead = false;
            if (trueTeam != null) team = trueTeam;
            hitTime = 1f;
            zzw.content.units.effects.SpecialFx.endDeny.at(x, y, rotation, this);
            return true;
        }
        return false;
    }

    @Override
    public void destroy() {
        if (denyDeath()) return;
        super.destroy();
    }

    @Override
    public void kill() {
        if (denyDeath()) return;
        super.kill();
    }

    @Override
    public void remove() {
        // ★ 以哈希台账为准, 不再要求当前 health > 0 (被秒杀清零时也要能回溯)
        float ledger = zzw.content.units.anticheat.EndHealthLedger.get(this, trueHealth);
        trueHealth = ledger;
        if (!zzw.util.ReviveSeal.isSealed(this) && ledger > 0f && revivesUsed < reviveLimit()) {
            revivesUsed++;
            health = Math.max(health, Math.min(ledger, trueMaxHealth)
                    * zzw.content.units.anticheat.EndCheatVars.REVIVE_HEAL_RATIO);
            dead = false;
            if (trueTeam != null) team = trueTeam;
            return;
        }
        // 真实死亡: 从全局防作弊登记表注销 (PU132 EndComp.remove)
        zzw.content.units.anticheat.EndHealthLedger.remove(this);
        zzw.util.AntiCheat.removeUnit(this);
        super.remove();
    }

    /** 穿甲伤害转普通伤害 (PU132 EndComp.damagePierce): 防止穿甲弹绕过防作弊判定。 */
    @Override
    public void damagePierce(float amount, boolean withEffect) {
        float pre = hitTime;
        damage(amount);
        if (!withEffect) {
            hitTime = pre;
        }
    }

    /**
     * 击退免疫 + 反噬 (PU132 EndComp.impulse):
     * End 单位不承受击退, 仅在击退力度超过 mass*8 时累积抗性并播放红色蓄力特效。
     */
    @Override
    public void impulse(float ix, float iy) {
        float mass = mass();
        float len = arc.util.Tmp.v1.set(ix, iy).len();
        float resistTimeMax = ac != null ? ac.resistTime : 3f * 60f;
        if (len > mass * 8f) {
            float l = (len - (mass * 8f)) / (mass / 2f);
            resist += l;
            resistMax = Math.max(resistMax, resist);
            resistTime = Math.max(resistTime, resistTimeMax / 2f);
            zzw.content.units.effects.SpecialFx.endDeny.at(x, y, rotation, arc.util.Tmp.c1.a(Mathf.clamp(l / (mass * 15f))), this);
        }
        // PU132 未回接原版击退 → 击退免疫 (不调用 super.impulse)
    }
}
