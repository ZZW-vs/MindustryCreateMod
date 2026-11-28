package zzw.content.units.anticheat;

import arc.math.Interp;

/**
 * End 阵营单位防作弊参数集 (移植自 PU132 type/AntiCheatVariables 完整版)
 *
 * <p>每个 End 单位在 UnitType 上配置一组 (PU132 写法:
 * {@code antiCheatType = new AntiCheatVariables(...)})。
 * 实体 (EndGroundUnit/EndInvisibleUnit) 在 add() 时读取并按参数运行防作弊逻辑。</p>
 *
 * <p>参数含义 (对照 PU132 原版注释):</p>
 * <ul>
 *   <li>damageThreshold: 伤害曲线启动阈值, 低于此值全额生效;</li>
 *   <li>maxDamageThreshold: 曲线衰减上限 (超过此值的部分按 curveType 压到最低);</li>
 *   <li>maxDamageTaken: 真实血量台账单次扣减的硬上限 (Boss 必须被打很多次才掉台账);</li>
 *   <li>resistStart / resistScl / resistDuration / resistTime: 抗性累积系统
 *       (被打出大伤害后临时减伤, 随时间衰减);</li>
 *   <li>invincibilityDuration: 每个无敌帧槽位的时长 (tick);</li>
 *   <li>invincibilityArray: 无敌帧槽位数量 (轮询使用)。</li>
 * </ul>
 */
public class EndCheatVars{
    /** 复活次数硬上限 (End 体系: 最多复活 10 次)。 */
    public static final int MAX_REVIVES = 10;
    /** "受伤过快"统计窗口 (tick): 1 秒。 */
    public static final float FAST_DAMAGE_WINDOW = 60f;
    /** 窗口内累计原始伤害达到最大生命的该比例, 即视为"受伤过快"。 */
    public static final float FAST_DAMAGE_RATIO = 0.15f;
    /** 受伤过快时, 超出安全速率的部分按该比例额外压入真实血量台账 (记忆)。 */
    public static final float FAST_DAMAGE_LEDGER_PENALTY = 0.5f;
    /** 复活时回充的血量比例 (相对台账/上限), 弱化"加血条"观感。 */
    public static final float REVIVE_HEAL_RATIO = 0.25f;
    /** 复活后的无敌时间 (tick): 避免刚复活就被同一波火力立刻打回死亡。 */
    public static final float REVIVE_INVULNERABLE_TIME = 120f;

    private final static Interp defaultIn = new Interp.Pow(2);

    /** 伤害曲线启动阈值 */
    public final float damageThreshold;
    /** 曲线衰减上限 */
    public final float maxDamageThreshold;
    /** 曲线类型 (PU132 默认 Pow2) */
    public final Interp curveType;
    /** 真实血量台账单次扣减硬上限 */
    public final float maxDamageTaken;
    /** 抗性累积启动阈值 */
    public final float resistStart;
    /** 抗性缩放系数 */
    public final float resistScl;
    /** 抗性衰减时长 (tick) */
    public final float resistDuration;
    /** 受击后抗性保持时长 (tick) */
    public final float resistTime;
    /** 无敌帧时长 (tick) */
    public final float invincibilityDuration;
    /** 无敌帧槽位数 */
    public final int invincibilityArray;

    public EndCheatVars(float dt, float mdthr, Interp ct, float mdtkn, float rStrt, float rScl, float rd, float rt, float inD, int inf){
        damageThreshold = dt;
        maxDamageThreshold = mdthr;
        curveType = ct;
        maxDamageTaken = mdtkn;
        resistStart = rStrt;
        resistScl = rScl;
        resistDuration = rd;
        resistTime = rt;
        invincibilityDuration = inD;
        invincibilityArray = inf;
    }

    public EndCheatVars(float dt, float mdthr, float mdtkn, float rStrt, float rScl, float rd, float rt, float inD, int inf){
        this(dt, mdthr, defaultIn, mdtkn, rStrt, rScl, rd, rt, inD, inf);
    }
}
