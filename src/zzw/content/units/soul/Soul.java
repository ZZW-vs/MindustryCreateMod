package zzw.content.units.soul;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.util.Align;
import arc.util.Tmp;
import mindustry.game.Team;
import mindustry.gen.BlockUnitc;
import mindustry.gen.Unit;
import mindustry.ui.Fonts;
import zzw.content.graphics.UnityPal;
import zzw.content.units.entities.MonolithSoulUnit;

/**
 * 灵魂持有者接口 (PU132 unity.entities.Soul 的纯 Java 移植)。
 *
 * <p>PU132 用 {@code interface Soul extends Teamc, Healthc, Sized} 统一描述
 * "能容纳灵魂的实体"——既可以是单位 ({@code MonolithComp})，也可以是建筑
 * ({@code SoulComp})。本模组无法使用注解织入，因此改为一个独立接口，由
 * 具体的单位实体 / 建筑 build 内部实现。</p>
 *
 * <p>为兼容"单位"和"建筑"两种宿主，接口只声明双方都具备的访问器：
 * {@link #team()} {@link #x()} {@link #y()} {@link #hitSize()} {@link #maxHealth()}。
 * (Unit 与 Building 都实现了这些方法。)</p>
 *
 * <p>核心机制：</p>
 * <ul>
 *   <li>{@link #join()} / {@link #unjoin()}：灵魂的进出，表现为 {@code souls} 计数增减；</li>
 *   <li>{@link #spreadSouls()}：实体死亡时把体内灵魂拆解为 {@link MonolithSoulUnit}
 *       (灵魂单位)，由 {@link #apply} 决定是否把其中一个交给玩家操控；</li>
 *   <li>{@link #soulf()}：灵魂充盈比例，供炮台/单位按比例缩放效率或伤害。</li>
 * </ul>
 *
 * @author GlennFolker (原作), 移植: zzw
 */
public interface Soul {
    /** 当前灵魂数量 */
    int souls();

    /** 最大灵魂数量 (0 = 该实体不承载灵魂) */
    int maxSouls();

    /** 所属队伍 */
    Team team();

    /** 世界坐标 X */
    float x();

    /** 世界坐标 Y */
    float y();

    /** 碰撞半径 (用于拆魂散布) */
    float hitSize();

    /** 最大血量 */
    float maxHealth();

    /** 是否还能再接纳灵魂 */
    default boolean canJoin() {
        return souls() < maxSouls();
    }

    /** 体内是否有灵魂 */
    default boolean hasSouls() {
        return souls() > 0;
    }

    /**
     * 计算还能接纳多少灵魂 (PU132 Soul.acceptSoul(int))。
     * 不实际写入，仅返回可接受数量。
     */
    default int acceptSoul(int amount) {
        return Math.min(maxSouls() - souls(), amount);
    }

    /** 尝试接纳另一实体的全部灵魂，返回实际接纳数量。 */
    default int acceptSoul(Object other) {
        Soul soul = toSoul(other);
        return soul != null ? acceptSoul(soul.souls()) : 0;
    }

    /** 尝试接纳另一灵魂持有者的全部灵魂，返回实际接纳数量。 */
    default int acceptSoul(Soul other) {
        return acceptSoul(other.souls());
    }

    /** 加入一个灵魂 (计数 +1)。 */
    void join();

    /** 移除一个灵魂 (计数 -1)。 */
    void unjoin();

    /** 灵魂充盈比例 (0~1)。maxSouls=0 时返回 0。 */
    default float soulf() {
        return maxSouls() <= 0 ? 0f : souls() / (float) maxSouls();
    }

    /**
     * 单个灵魂被该单位吸收时恢复的生命值 (PU132 注释中的 healAmount 方案)。
     *
     * <p>用户指定: 每个 monolith-soul 为单位恢复 10% 最大生命, 单次最低 1000。</p>
     */
    default float soulHealAmount() {
        return Math.max(maxHealth() * 0.1f, 1000f);
    }

    /**
     * 死亡时把体内灵魂拆解为灵魂单位 (PU132 Soul.spreadSouls)。
     *
     * <p>每个灵魂从本体中心随机方向偏移出生，再沿圆周均匀方向赋予初速度，
     * 随后 {@link #apply} 可能把其中一个交给玩家操控 (玩家可以指挥它去 join 别的容器)。
     * 该方法应在服务端调用 (单机即 {@code !net.active()} 或 {@code net.server()})。</p>
     */
    default void spreadSouls() {
        spreadSouls(souls());
    }

    /**
     * 按指定数量拆出灵魂单位。
     *
     * <p>魂系单位死亡时改用本重载: 数量由阶级决定 (T1/T2=1, T3/T4=2, T5=3,
     * T6=4, T7=5), 与体内灵魂计数无关。</p>
     */
    default void spreadSouls(int count) {
        int n = count;
        if (n <= 0) return;

        boolean transferred = false;
        float start = Mathf.random(360f);

        for (int i = 0; i < n; i++) {
            MonolithSoulUnit soul = MonolithSoulUnit.create(team());

            // 出生位置：本体范围内随机偏移
            Tmp.v1.trns(Mathf.random(360f), Mathf.random(hitSize()));
            soul.set(x() + Tmp.v1.x, y() + Tmp.v1.y);

            // 初速度：沿圆周均匀分布方向
            Tmp.v1.trns(start + 360f / n * i, Mathf.random(6f, 12f));
            soul.rotation = Tmp.v1.angle();
            soul.vel.set(Tmp.v1.x, Tmp.v1.y);

            transferred = apply(soul, i, transferred);
            soul.add();
        }
    }

    /**
     * 拆魂时的回调：决定是否把某个灵魂单位的操控权交给玩家
     * (PU132 Soul.apply)。返回值为"是否已转移过操控权"。
     */
    boolean apply(MonolithSoulUnit soul, int index, boolean transferred);

    /**
     * 单位头顶的魂数量条 (与灵魂炮台/工厂面板的 {@code bar.souls} 同一套表现:
     * 灵魂题材色进度条 + 数值标签)。
     *
     * <p>由 Soul 实体在 {@code draw()} 末尾调用, 只对 {@code maxSouls > 0} 的单位绘制。</p>
     */
    static void drawSoulBar(Unit unit) {
        Soul soul = toSoul(unit);
        if (soul == null || soul.maxSouls() <= 0) return;

        float width = Math.max(unit.hitSize, 20f), height = 3.5f;
        float y = unit.y + unit.hitSize * 0.5f + 6f;
        float z = Draw.z();

        Draw.z(mindustry.graphics.Layer.effect + 1f);
        Draw.color(Color.black, 0.65f);
        Fill.rect(unit.x, y, width, height);

        Draw.color(UnityPal.monolith);
        float fill = width * Mathf.clamp(soul.soulf());
        Fill.rect(unit.x - width * 0.5f + fill * 0.5f, y, fill, height);
        Draw.reset();

        Fonts.outline.draw(soul.souls() + "/" + soul.maxSouls(), unit.x, y + height + 3f, Align.center);
        Draw.z(z);
    }

    /** 判断任意对象 (含控制器/BlockUnitc) 是否为灵魂持有者。 */
    static boolean isSoul(Object e) {
        return toSoul(e) != null;
    }

    /**
     * 把控制器 / 建筑占位单位解析为灵魂持有者 (PU132 Soul.toSoul)。
     *
     * <p>除了本身实现 {@link Soul} 的单位 / 建筑, 本模组还额外把实现了
     * {@code ISoulTurret} 的灵魂炮台建筑用 {@link TurretSoul} 适配为统一 {@link Soul},
     * 于是巨石单位拆出的灵魂单位可以飞到炮台上。</p>
     */
    static Soul toSoul(Object e) {
        if (e instanceof Unit cont) e = cont;
        if (e instanceof BlockUnitc unit) e = unit.tile();
        if (e instanceof Soul soul) return soul;
        // 灵魂炮台建筑: 适配为统一 Soul
        if (e instanceof mindustry.gen.Building build
            && e instanceof zzw.content.blocks.soul.ISoulTurret turret) {
            return new TurretSoul(build, turret);
        }
        return null;
    }

    /**
     * 灵魂炮台建筑的 {@link Soul} 适配器。
     *
     * <p>把炮台的 {@code souls()/maxSouls()/joinSoul()/unjoinSoul()} 语义映射到
     * 统一的 {@link Soul#join()}/{@link Soul#unjoin()} 上, 使灵魂单位能将其视为容器。</p>
     */
    class TurretSoul implements Soul {
        private final mindustry.gen.Building build;
        private final zzw.content.blocks.soul.ISoulTurret turret;

        public TurretSoul(mindustry.gen.Building build, zzw.content.blocks.soul.ISoulTurret turret) {
            this.build = build;
            this.turret = turret;
        }

        @Override public int souls() { return turret.souls(); }
        @Override public int maxSouls() { return turret.maxSouls(); }
        @Override public Team team() { return build.team(); }
        @Override public float x() { return build.x(); }
        @Override public float y() { return build.y(); }
        @Override public float hitSize() { return build.hitSize(); }
        @Override public float maxHealth() { return build.maxHealth(); }
        @Override public void join() { turret.joinSoul(); }
        @Override public void unjoin() { turret.unjoinSoul(); }
        @Override public boolean apply(MonolithSoulUnit soul, int index, boolean transferred) { return transferred; }
    }
}