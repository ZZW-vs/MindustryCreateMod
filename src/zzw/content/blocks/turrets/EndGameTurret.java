package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.game.Team;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Drawc;
import mindustry.gen.Entityc;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Posc;
import mindustry.gen.Unit;
import mindustry.entities.units.UnitController;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.Block;
import mindustry.world.meta.Stat;
import zzw.content.Z_Sounds;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import zzw.content.units.effects.ScarFx;
import zzw.content.units.effects.SlowLightning;
import zzw.content.units.effects.SpecialFx;
import zzw.content.units.util.UnityUtils;
import zzw.util.AntiCheatBuildings;

/**
 * EndGameTurret 移植自 PU132 (unity.world.blocks.defense.turrets.EndGameTurret)。
 *
 * <p>终局炮台 —— 三层旋转环 + 16 只眼睛追踪光束 + 全屏湮灭齐射。</p>
 *
 * <p>★ 本类是 PU132 原版的<b>逐行忠实移植</b>: draw / updateTile / updateEyes /
 * eyeShoot / killUnits / killTiles / shoot / collision / damage 的数值、判定顺序、
 * 特效参数与图层全部与原版一致, 能直接抄的一律直接抄。</p>
 *
 * <p>行为:</p>
 * <ul>
 *   <li><b>通电</b>: 眼睛亮起 (eyesAlpha 由电力满足度驱动);</li>
 *   <li><b>通电 + 有目标 + 有弹药</b>: 环灯亮起、轮盘旋转、随机释放慢速闪电,
 *       并且 16 只眼睛按逆时针顺序轮流发射秒杀光束 (激光射程 = {@code range});</li>
 *   <li><b>装填完成</b>: 以自身为中心释放 "终局齐射" —— {@link #blastRadius} 内
 *       所有敌方单位被瞬间湮灭, 所有敌方建筑被汽化 (爆炸范围与激光射程相互独立);</li>
 *   <li><b>不可摧毁</b>: {@code damage / kill / killed} 全部被阻断, 血量恒定不降;
 *       地块被外部抹掉或被拆除时由建筑防作弊登记写回。</li>
 * </ul>
 *
 * <p>★ v132 → v158 适配要点 (与"通电就亮"反复不生效的根因):</p>
 * <ul>
 *   <li>PU132 的 {@code efficiency()} 只表示<b>电力满足度</b>; 而 v158 中
 *       {@code Building.efficiency} 是 "所有非可选消费者效率的最小值"
 *       (见 {@code BuildingComp.updateConsumption()}), 本炮台有
 *       {@code consumeItem(terminum, 2)} 这个必选消费者, 一旦没有 terminum
 *       就会把 efficiency 直接压成 0 —— 于是旧代码里
 *       {@code efficiency > 0.0001f} 永远为 false, 眼睛永远不亮。
 *       本类改用 {@code power.status} (PowerModule, 纯粹的电力满足度 0~1,
 *       与物品无关) 作为 "通电" 信号, 见 {@link #powerStatus()} /
 *       {@link #trueEfficiency()} —— 这才是原版语义的等价物;</li>
 *   <li>{@code reload / reloadTime} (旧版: 当前 / 总装填) → v158 的
 *       {@code reloadCounter / reload};</li>
 *   <li>{@code Utils.offsetSin / offsetSinB} → 本类内联实现, 保持
 *       {@code absin(Time.time + offset*radDeg, ...)} 的相位算法不变;</li>
 *   <li>{@code Utils.trueEachBlock} → 项目内 {@link UnityUtils#trueEachBlock};</li>
 *   <li>{@code Utils.nearbyEnemySorted} → 项目内 {@link UnityUtils#nearbyEnemySorted};</li>
 *   <li>{@code AntiCheat.annihilateEntity} → 本类的 {@link #annihilateUnit}
 *       (反射克制 FlameOut 共鸣/消沉等 "神秘特殊机制" 的不死单位) +
 *       {@link #annihilateEntity} 统一入口;</li>
 *   <li>{@code SlowLightningType.create(...)} → 项目内自包含的
 *       {@link SlowLightning} (由本建筑在 {@code updateTile/draw} 中驱动)。</li>
 * </ul>
 */
public class EndGameTurret extends PowerTurret {

    /** End 系列主色 (scarColor)。 */
    public static final Color scarColor = Color.valueOf("f53036");

    /**
     * 爆炸齐射半径 (世界单位) —— 与激光射程解耦, 固定保持原版 820。
     *
     * <p>需求: "激光的攻击范围翻倍, 但爆炸的攻击范围保持不变"。激光 (眼睛光束 /
     * 炮台索敌) 使用 {@code range}, 爆炸 (装填完成时的全屏湮灭) 只使用本常量。</p>
     */
    public static final float blastRadius = 820f;

    /** 每次齐射最多播放的激光特效数量 (性能上限, 超出只结算伤害)。 */
    public static final int maxBurstLasers = 10;
    /** 每次齐射最多播放的汽化特效数量 (性能上限)。 */
    public static final int maxBurstVapourizes = 12;

    /** 需求: 炮台血量为负数 (方块定义里的 health 仍为 68000, 供 maxHealth/面板使用)。 */
    public static final float negativeHealth = -1f;

    // ================= 模组内部血量 =================
    // ★ 需求: 血量完全存在模组内部, 不写进游戏自身的 health 字段。
    //   关键点: 只要内部血量没掉完, 炮台就不会消失; 秒杀类入口 (kill/destroy/remove/
    //   直接改写字段) 触发一次"字段回溯", 而不是被一击抹除。普通伤害仍然可以磨掉内部血量。
    /** 内部血量 = 方块血量上限 × 该倍率 (普通伤害需要持续输出才磨得动)。 */
    public static final float internalHealthMultiplier = 100f;
    /** 内部血量表: 键为地块坐标 (packed), 值域 [0, 上限]; 世界加载时清空。 */
    public static final arc.struct.IntFloatMap internalHealth = new arc.struct.IntFloatMap();

    /** 字段回溯的最小间隔 (帧), 避免被连续秒杀调用时每帧做恢复工作。 */
    public static final float rollbackCooldown = 10f;

    static{
        // 换图 / 读档时清掉上一张图的内部血量记录
        arc.Events.on(mindustry.game.EventType.WorldLoadEvent.class, e -> internalHealth.clear());
    }

    /** 像电力节点一样建立电力连接的范围 (格, 按中心到中心计算; 14x14 方块故放宽)。 */
    public float linkRange = 26f;
    /** 电力连接数量上限 (与 PowerNode.maxNodes 默认值一致)。 */
    public int maxLinks = 10;


    /** 三层环的旋转速度系数 (原版 ringProgresses)。 */
    private static final float[] ringProgresses = {0.013f, 0.035f, 0.024f};
    /** 三层环的旋转方向 (原版 ringDirections)。 */
    private static final int[] ringDirections = {1, -1, 1};

    /** 眼睛数量: 内环 8 只 + 外环 8 只。 */
    public static final int eyeCount = 16;

    // ===== 反射缓存 (性能优化) =====
    // 湮灭流程原本每次击杀都会 Class.forName + 逐层 getDeclaredField, 终局齐射范围内
    // 可能有上百个单位, 反射开销会直接拖慢帧率。这里把 "单位类 → 字段" 与
    // "单位类 → 外部模组类" 的查找结果缓存起来, 后续击杀只做一次 Map 查询。
    private static final Map<Class<?>, Map<String, Field>> fieldCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Class<?>>> modClassCache = new ConcurrentHashMap<>();

    /** 眼睛目标刷新计时器索引 (PU132 eyeTime)。 */
    protected int eyeTime = timers++;
    /** 反子弹扫描计时器索引 (PU132 bulletTime)。 */
    protected int bulletTime = timers++;

    // ===== 贴图 =====
    public TextureRegion baseRegion, baseLightsRegion, bottomLightsRegion, eyeMainRegion;
    public TextureRegion ringABottomRegion, ringAEyesRegion, ringARegion, ringALightsRegion;
    public TextureRegion ringBBottomRegion, ringBEyesRegion, ringBRegion, ringBLightsRegion;
    public TextureRegion ringCRegion, ringCLightsRegion;

    public EndGameTurret(String name) {
        super(name);

        // ★ 与 PU132 EndGameTurret 构造 + UnityBlocks 方块定义逐项一致
        health = 68000;
        // ★ 需求: 允许像电力节点一样手动连接周围电器 (configurable + 自己实现 config(Integer))
        configurable = true;
        outputsPower = true;
        consumePower(320f);
        reload = 300f;
        // ★ 需求: 激光攻击范围持续加强:
        //   820 → 1640 (翻倍) → +150 格 → 再 +120 格 (= 960 世界单位) = 3800
        //   爆炸范围另见 blastRadius, 始终保持 820 不变
        range = 1640f + 150f * 8f + 120f * 8f;
        size = 14;
        shootCone = 360f;
        rotate = false;
        shake = 2.2f;
        absorbLasers = true;
        // ★ 允许索敌敌方建筑: v160 的 TurretBuild.findEnemy() 只有在
        //   Turret.targetBlocks && BulletType.targetBlocks 同时为 true 时
        //   才会把建筑纳入 bestTarget; 否则基类 target 永远只是单位,
        //   而眼睛发射 (updateEyesTargeting) 又以 target != null 为前置条件 ——
        //   结果就是"范围内只有敌方建筑时眼睛完全不开火"。
        targetBlocks = true;
        outlineIcon = false;
        noUpdateDisabled = false;
        hasItems = true;
        // ★ 每轮齐射消耗 2 个 terminum; 装填按原版 300 tick (5 秒) 一轮,
        //   320 的缓冲可支撑 160 轮齐射, 保证长时间不断火。
        itemCapacity = 320;
        loopSound = Z_Sounds.endgameActive;
        loopSoundVolume = 0.2f;
        shootSound = Z_Sounds.endgameShoot;

        // ★ 占位子弹 (PU132 原版: damage = (float)Double.MAX_VALUE)。
        //   本炮台不使用普通子弹, 伤害全部由眼睛光束 / 齐射结算,
        //   这里必须给 shootType 一个非 null 值 —— 否则 PowerTurret.peekAmmo()
        //   返回 null, 会在 ammoReloadMultiplier() 处抛 NPE。
        // ★ 32 倍装填不走 BulletType.reloadMultiplier —— 该通道会被
        //   TurretBuild.updateReload() 直接累加到 reloadCounter 上,
        //   等于把 300 tick 的"终局齐射"周期压缩到约 9 tick,
        //   表现为"爆炸一次 → 眼睛射击 → 又爆炸"的死循环。
        //   眼睛发射有自己的计时器 (eyeReloads), 与 reloadCounter 无关,
        //   因此这里保持 1 倍装填即可: 齐射回到原版 5 秒一次的节奏。
        shootType = new BulletType(){{
            damage = (float)Double.MAX_VALUE;
            speed = 0.01f;
            lifetime = 1f;
            collides = false;
            hittable = false;
            absorbable = false;
            // 见上方 targetBlocks 注释: 占位子弹也必须声明可索敌建筑,
            // 否则 findEnemy() 的 buildingPred 恒为 false, 基类 target 取不到建筑
            targetBlocks = true;
            despawnEffect = hitEffect = shootEffect = smokeEffect = Fx.none;
        }};
        ammoUseEffect = Fx.none;
    }

    @Override
    public void load() {
        super.load();
        baseRegion = Core.atlas.find(name + "-base");
        baseLightsRegion = Core.atlas.find(name + "-base-lights");
        bottomLightsRegion = Core.atlas.find(name + "-bottom-lights");
        eyeMainRegion = Core.atlas.find(name + "-eye");

        ringABottomRegion = Core.atlas.find(name + "-ring1-bottom");
        ringAEyesRegion = Core.atlas.find(name + "-ring1-eyes");
        ringARegion = Core.atlas.find(name + "-ring1");
        ringALightsRegion = Core.atlas.find(name + "-ring1-lights");

        ringBBottomRegion = Core.atlas.find(name + "-ring2-bottom");
        ringBEyesRegion = Core.atlas.find(name + "-ring2-eyes");
        ringBRegion = Core.atlas.find(name + "-ring2");
        ringBLightsRegion = Core.atlas.find(name + "-ring2-lights");

        ringCRegion = Core.atlas.find(name + "-ring3");
        ringCLightsRegion = Core.atlas.find(name + "-ring3-lights");
    }

    @Override
    public void setStats() {
        super.setStats();
        // 占位子弹没有意义, 不显示它的弹药信息
        stats.remove(Stat.ammo);
    }

    @Override
    public void init() {
        super.init();

        // ★ 需求: 像电力节点一样手动连接周围电器。
        //   逻辑与 PowerNode 的 config(Integer.class, ...) 完全一致:
        //   已连接 → 双向断开并重新分流电力图; 未连接 → 校验后双向写入并合并电力图。
        config(Integer.class, (entity, value) -> {
            mindustry.world.modules.PowerModule power = entity.power;
            Building other = Vars.world.build(value);
            boolean contains = power.links.contains(value);
            boolean valid = other != null && other != entity && other.power != null;

            if(contains){
                power.links.removeValue(value);
                if(valid) other.power.links.removeValue(entity.pos());

                PowerGraph newgraph = new PowerGraph();
                newgraph.reflow(entity);
                newgraph.update();

                if(valid && other.power.graph != newgraph){
                    PowerGraph og = new PowerGraph();
                    og.reflow(other);
                    og.update();
                }
            }else if(valid && linkValid(entity, other) && power.links.size < maxLinks){
                power.links.addUnique(other.pos());
                if(other.team == entity.team){
                    other.power.links.addUnique(entity.pos());
                }
                power.graph.addGraph(other.power.graph);
                power.graph.update();
            }
        });
    }

    /**
     * 电力连接合法性判定 (对应 {@code PowerNode.linkValid} 的核心规则):
     * 同队 / 有电力 / 可连接 / 中心距在 {@link #linkRange} 内。
     */
    public boolean linkValid(Building a, Building b){
        if(a == b || b == null || b.power == null) return false;
        if(!b.block.hasPower || !b.block.connectedPower || a.team != b.team) return false;
        return Mathf.dst(a.x, a.y, b.x, b.y) <= linkRange * Vars.tilesize;
    }

    public class EndGameTurretBuild extends PowerTurretBuild {

        // ===== 运行状态 (与 PU132 EndGameTurretBuilding 字段一一对应) =====

        /** 威胁等级 (1 + 范围内敌人强度), 放大眼睛光束伤害。 */
        protected float threatLevel = 1f;

        /** 内部血量是否已耗尽 (只有此时才允许真正移除)。 */
        protected boolean internalDead = false;

        /** 上次字段回溯的时间 (Time.time), 用于限频, 避免每帧回溯。 */
        protected float lastRollbackTime = -100f;

        /** 每秒自身回复的内部血量。 */
        public float internalHealPerSecond = 50000f;
        /** 停火后开始熄灭环灯的延时计时。 */
        protected float eyeResetTime = 0f;
        /** 眼睛亮度 (0~1)。 */
        protected float eyesAlpha = 0f;
        /** 环灯亮度 (0~1)。 */
        protected float lightsAlpha = 0f;
        /** 三层环的当前角度。 */
        protected float[] ringProgress = {0f, 0f, 0f};
        /** 内外环的发射计时: [0]=内环 15f, [1]=外环 5f。 */
        protected float[] eyeReloads = {0f, 0f};

        /** 内外环眼睛的轮转序号 (逆时针)。 */
        protected int eyeSequenceA = 0, eyeSequenceB = 0;

        /** 眼睛位置平滑偏移。 */
        protected Vec2 eyeOffset = new Vec2();
        protected Vec2 eyeOffsetB = new Vec2();
        protected Vec2 eyeTargetOffset = new Vec2();
        /** 16 只眼睛的世界坐标。 */
        protected Vec2[] eyesVecArray = new Vec2[eyeCount];
        /** 16 只眼睛当前锁定的目标。 */
        protected Posc[] targets = new Posc[eyeCount];

        // ===== 反子弹 / 齐射用的临时状态 (PU132 静态字段, 这里改为实例以免多炮台互相污染) =====
        protected float damageFull = 0f, damageB = 0f;
        protected int shouldLaser = 0;
        /** 复用的实体收集缓存 (PU132 entitySeq)。 */
        protected final Seq<Entityc> entitySeq = new Seq<>(512);
        /** 与 {@link #entitySeq} 平行的每发子弹威力缓存 (避免在反子弹扫描中重复计算破片伤害)。 */
        protected final arc.struct.FloatSeq bulletDamages = new arc.struct.FloatSeq();
        /** 本炮台当前存活的所有慢速闪电。 */
        protected final Seq<SlowLightning> lightnings = new Seq<>();
        /** 同时存在的慢速闪电数量上限 (需求: 比原版少一些)。 */
        protected int maxLightnings = 6;

        {
            // 构造阶段就填满眼睛坐标数组, 保证 draw/shoot 任何时刻都不会读到 null
            for(int i = 0; i < eyeCount; i++){
                eyesVecArray[i] = new Vec2();
            }
        }

        // ================= 效率 / 合法性 =================

        /**
         * ★ "通电" 信号 —— PU132 {@code efficiency()} 的 v158 等价物。
         *
         * <p>{@code power.status} 是 PowerModule 的电力满足度 (0~1),
         * 只与电力有关, 完全不受 {@code consumeItem(terminum, 2)}
         * 是否满足的影响 —— 这正是旧实现 (用 {@code efficiency}) 永远不亮的原因。</p>
         */
        float powerStatus(){
            return power == null ? 0f : power.status;
        }

        /** PU132 trueEfficiency(): 电力满足度, 钳制到 [0, 1]。 */
        float trueEfficiency(){
            return Mathf.clamp(powerStatus(), 0f, 1f);
        }

        /**
         * ★ 装填倍率 32 由占位子弹的 {@code reloadMultiplier} 提供
         * (见构造里的 {@code shootType}), 这里只保留基础速度倍率 (电力满足度),
         * 眼睛光束的发射节奏同样使用该值, 不额外乘 32。
         */
        @Override
        protected float baseReloadSpeed(){
            return Mathf.clamp(powerStatus(), 0f, 1f);
        }

        /** PU132 deltaB(): 按 baseReloadSpeed 缩放的 delta。 */
        float deltaB(){
            return delta() * baseReloadSpeed();
        }

        /**
         * PU132 consValid(): "可以工作" = 电力满足 且 有 terminum 弹药。
         *
         * <p>v158 中 {@code canConsume()} (potentialEfficiency > 0) 已经同时表达了
         * "所有必选消费者 (电力 + 物品) 都满足", 这里按原版语义显式判定。</p>
         */
        @Override
        public boolean canConsume(){
            return powerStatus() > 0.0001f && items != null && items.total() > 0;
        }

        @Override
        public boolean shouldActiveSound(){
            return trueEfficiency() >= 0.0001f;
        }

        // ================= 受击 / 死亡 =================

        /**
         * ★ 真正不可摧毁 —— 需求: "受击不掉血、不会被任何方式拆除或摧毁",
         * 且不能用天文数字血量 (会让血条/存档/伤害结算出现异常)。
         *
         * <p>做法是把所有掉血入口直接掐断:</p>
         * <ul>
         *   <li>{@code damage()} —— 引擎里所有伤害 (子弹/爆炸/逻辑伤害/碰撞) 的唯一入口,
         *       这里直接返回, 血量永远保持满值;</li>
         *   <li>{@code kill()} —— 外部模组常用的"强制击杀"入口, 直接忽略;</li>
         *   <li>{@code killed()} —— 引擎真正的销毁入口 ({@code Call.buildDestroyed}
         *       → {@code Tile.buildDestroyed} → {@code killed()}), 空实现即可堵住
         *       "直接调用销毁"这条旁路;</li>
         *   <li>地块被外部抹掉/被拆除 → 由防作弊登记把炮台写回 (见
         *       {@link zzw.util.AntiCheat} / {@link AntiCheatBuildings})。</li>
         * </ul>
         */
        @Override
        public void damage(float amount){
            // ★ 内部血量模型: 普通伤害按真实数值扣内部血量, 不写进游戏 health。
            //   单次伤害 ≥ 内部血量上限 → 视为秒杀类效果, 不扣血, 走字段回溯。
            if(amount <= 0f || internalDead) return;

            if(amount >= maxInternalHealth()){
                rollbackCheese();
                return;
            }

            float hp = internalHealthNow() - amount;
            setInternalHealth(hp);
            if(hp <= 0f) internalDie();
        }

        @Override
        public void kill(){
            // 强制击杀 → 字段回溯, 而不是被一击抹除
            rollbackCheese();
        }

        @Override
        public void killed(){
            // 引擎真正的销毁入口 → 同样回溯 (内部血量没掉完前绝不消失)
            rollbackCheese();
        }

        @Override
        public void remove(){
            // 只有内部血量掉完 (internalDie) 才允许真正移除, 其它任何路径都回溯
            if(internalDead){
                super.remove();
            }else{
                rollbackCheese();
            }
        }

        // ---- 其它伤害入口全部统一到内部血量模型 (外部模组常用这些旁路) ----

        @Override
        public void damage(float amount, boolean withEffect){
            damage(amount);
        }

        @Override
        public void damage(Team source, float amount){
            damage(amount);
        }

        @Override
        public void damagePierce(float amount){
            damage(amount);
        }

        @Override
        public void damagePierce(float amount, boolean withEffect){
            damage(amount);
        }

        @Override
        public void damageArmorMult(float amount, float armorMultiplier){
            damage(amount);
        }

        @Override
        public void damageArmorMult(float amount, float armorMultiplier, boolean withEffect){
            damage(amount);
        }

        @Override
        public void damageContinuous(float amount){
            damage(amount * delta());
        }

        @Override
        public void damageContinuousPierce(float amount){
            damage(amount * delta());
        }

        @Override
        public void damageContinuousArmorMult(float amount, float armorMultiplier){
            damage(amount * delta());
        }

        @Override
        public void health(float value){
            // 外部直接改写血量字段 → 回溯
            rollbackCheese();
        }

        @Override
        public void dead(boolean value){
            // 状态恒为"活着"
        }

        @Override
        public void heal(){
            // 自身回血由内部血量模型处理, 不接受外部治疗
        }

        @Override
        public void heal(float amount){
            // 同上
        }

        @Override
        public void clampHealth(){
            // 负血量不参与引擎的 clamp (否则会被压成 0)
        }

        // ================= 模组内部血量 =================

        /** 内部血量上限 (方块血量的 100 倍)。 */
        protected float maxInternalHealth(){
            return block.health * internalHealthMultiplier;
        }

        /** 当前内部血量 (第一次访问时初始化为满)。 */
        protected float internalHealthNow(){
            int p = pos();
            if(!internalHealth.containsKey(p)){
                internalHealth.put(p, maxInternalHealth());
            }
            return internalHealth.get(p);
        }

        protected void setInternalHealth(float value){
            internalHealth.put(pos(), Mathf.clamp(value, 0f, maxInternalHealth()));
        }

        /** 血量掉完 → 允许真正消失 (并注销防作弊登记, 否则会被写回)。 */
        protected void internalDie(){
            if(internalDead) return;
            internalDead = true;
            internalHealth.remove(pos(), internalHealth.get(pos(), 0f));
            AntiCheatBuildings.remove(this);
            health = 0f;
            if(tile != null && tile.build == this){
                tile.remove();
            }
        }

        /**
         * ★ 字段回溯: 秒杀类入口 (kill/killed/destroy/remove/超大单发伤害) 触发,
         * 把被外部改写的关键状态恢复回去, 而不是让炮台被一击抹除。
         *
         * <p>按需求不做每帧回溯: 用 {@link EndGameTurret#rollbackCooldown} 限频,
         * 连续多次秒杀调用只会在冷却结束后做一次恢复。</p>
         */
        protected void rollbackCheese(){
            float now = Time.time;
            if(now - lastRollbackTime < rollbackCooldown) return;
            lastRollbackTime = now;

            // 关键字段回滚
            internalDead = false;
            health = negativeHealth;
            enabled = true;
            efficiency = 1f;

            // 内部血量被外部清空/篡改 → 至少恢复到满值
            if(internalHealthNow() <= 0f) setInternalHealth(maxInternalHealth());

            // 电力连接被拆 → 按记忆重连 (复用已有的记忆机制)
            restorePowerLinks();

            // 地块被抹掉/覆盖由防作弊守卫写回 (见 AntiCheat)
        }

        // ================= 负血量 =================

        /**
         * ★ 需求: 血量改为负数。
         *
         * <p>建筑血量在 {@code create()} 里由方块的 {@code health} 填入, 这里直接覆写成负值;
         * 方块的 {@code block.health = 68000} 保持不变, 因此 {@code maxHealth()}、放置预览、
         * 数值面板仍然正常。</p>
         */
        @Override
        public Building create(Block block, Team team){
            super.create(block, team);
            health = negativeHealth;
            return this;
        }

        /**
         * ★ 保留负血量的同时不要让引擎认为它"已死"。
         *
         * <p>血量 <= 0 时 {@code dead()} 默认返回 true, 而引擎多处会据此跳过更新
         * (炮台会停止工作)、隐藏交互, 甚至走销毁流程 —— 那正是"自己消失 / 状态错乱"的来源。
         * 这里恒返回 false: 当前血量为负, 但状态上永远活着。</p>
         */
        @Override
        public boolean dead(){
            return false;
        }

        /**
         * 血条: 需求是"不再绘制血条"。
         *
         * <p>血量完全存在模组内部, 游戏 health 恒为负数; 直接把比例返回 1 (满),
         * 引擎与辅助单位都会认为它满血 —— 既不会画血条, 也不会被当成伤员。</p>
         */
        @Override
        public float healthf(){
            return 1f;
        }

        /** 需求: 不要让维修/治疗类辅助单位把它当作可修复目标。 */
        @Override
        public boolean damaged(){
            return false;
        }

        /** 把血量修正回负值 (防止读档恢复旧值 / 外部 heal 把血量加回正数)。 */
        protected void enforceNegativeHealth(){
            if(health >= 0f) health = negativeHealth;
        }

        /**
         * 碰撞: 吞掉来袭子弹 (不做伤害结算), 并对射程外偷袭者施加一次反击。
         */
        @Override
        public boolean collision(Bullet other){
            if(other.owner != null && !ownerWithin(other.owner) && other.owner instanceof Healthc){
                Healthc en = (Healthc)other.owner;
                en.damage(0.5f * en.maxHealth());
            }
            return true;
        }

        /** 攻击者是否位于本炮台激光射程之内 (用于 "射程外偷袭" 判定)。 */
        protected boolean ownerWithin(Entityc owner){
            Posc p = (Posc)owner;
            return Mathf.within(x, y, p.getX(), p.getY(), range);
        }

        // ================= 生命周期 =================

        @Override
        public void add() {
            for(int i = 0; i < eyeCount; i++){
                targets[i] = null;
            }
            super.add();
            // ★ 登记进建筑防作弊守卫: 地块被外部模组抹掉 / 被拆除时写回
            //   (见 AntiCheat)。登记按建筑 id 幂等, 重复 add() 不会重复登记。
            AntiCheatBuildings.add(this);

            // ★ 复活/重建: 若死亡前记下了电力节点, 下一帧逐个校验并重连
            pendingPowerRestore = rememberedPowerLinks.size > 0;
        }

        @Override
        public void onRemoved() {
            lightnings.clear();
            entitySeq.clear();
            // ★ 不可摧毁: 即使被外部强行移除也<b>不注销</b>登记, 由守卫写回地图。
            super.onRemoved();
        }

        // ================= 电力连接记忆 (死亡前记住节点, 复活后重连) =================

        /** 死亡前记住的电力节点连接点 (packed 地块坐标)。 */
        protected final IntSeq rememberedPowerLinks = new IntSeq();

        /** 重建/复活后的第一帧需要尝试恢复电力连接。 */
        protected boolean pendingPowerRestore = false;

        /**
         * 同步 "当前电力连接点" 到记忆表。
         *
         * <p>每帧只做几个 int 的拷贝, 开销可忽略; 好处是记忆始终等于最后一帧的
         * 真实连接 —— 玩家手动断开的线不会被记住, 死亡时留下的就是死亡前的状态。</p>
         */
        protected void snapshotPowerLinks(){
            rememberedPowerLinks.clear();
            if(power == null) return;

            for(int i = 0; i < power.links.size; i++){
                rememberedPowerLinks.add(power.links.get(i));
            }
        }

        /**
         * ★ 逐个校验并重连死亡前记住的电力节点。
         *
         * <p>记录的是<b>具体节点</b>而不是整张电网, 因此每一个都要现场校验:</p>
         * <ol>
         *   <li>该地块上是否仍然存在建筑 (读档、地图变动、方块被替换都可能改变它);</li>
         *   <li>是否仍然是<b>本队</b>的<b>电力节点</b> (被换成别的方块就不再重连);</li>
         *   <li>是否已经连上 (原版读档会自行恢复 links, 此时跳过, 不重复添加);</li>
         *   <li>沿用原版 {@link PowerNode#linkValid(Building, Building)} 判定
         *       (同队 / 有电力 / 双方 laserRange 覆盖), 并额外按原版
         *       {@code maxNodes} 规则限制节点剩余可连数量;</li>
         *   <li>建立方式与 {@code PowerNode.config(Integer)} 完全一致: 双向写入
         *       {@code power.links} 后合并电力图。</li>
         * </ol>
         *
         * <p>任何一项不满足就跳过该节点, 因此不会连出本该断开的线。</p>
         */
        protected void restorePowerLinks(){
            if(power == null || rememberedPowerLinks.size == 0) return;

            for(int i = 0; i < rememberedPowerLinks.size; i++){
                int nodePos = rememberedPowerLinks.get(i);
                Building other = Vars.world.build(nodePos);

                // 1) 目标不存在 / 已不是电力建筑 / 不是本队 → 不重连
                if(other == null || other == this || other.power == null) continue;
                if(other.team != team || !other.block.hasPower || !other.block.connectedPower) continue;

                // 2) 只重连电力节点 (原版只有节点会保存 "节点↔建筑" 的连接)
                if(!(other.block instanceof PowerNode)) continue;

                // 3) 已经连上 → 跳过
                if(power.links.contains(other.pos())) continue;

                PowerNode node = (PowerNode)other.block;

                // 4) 原版距离 / 同队 / 有电校验
                if(!node.linkValid(other, this)) continue;

                // 5) 原版节点连接数量上限 (maxNodes)
                if(other.power.links.size >= node.maxNodes && !other.power.links.contains(pos())) continue;

                // 6) 与原版 PowerNode.config(Integer) 相同的建立方式
                power.links.addUnique(other.pos());
                other.power.links.addUnique(pos());
                power.graph.addGraph(other.power.graph);
            }
        }

        // ================= 节点式电力连接 (手动连线) =================

        /** 与电力节点一致的配置数据 (供蓝图/复制粘贴还原连线)。 */
        @Override
        public Point2[] config(){
            Point2[] out = new Point2[power.links.size];
            for(int i = 0; i < out.length; i++){
                out[i] = Point2.unpack(power.links.get(i)).sub(tileX(), tileY());
            }
            return out;
        }

        @Override
        public boolean onConfigureBuildTapped(Building other){
            // 点击另一个电力建筑即建立/断开连接 (与 PowerNode 一致)
            if(other != this && EndGameTurret.this.linkValid(this, other)){
                configure(other.pos());
                return false;
            }
            return true;
        }

        @Override
        public void drawSelect(){
            super.drawSelect();
            drawPowerLinks();
        }

        @Override
        public void drawConfigure(){
            Drawf.circles(x, y, block.size * Vars.tilesize / 2f + 1f + Mathf.absin(Time.time, 4f, 1f));
            Drawf.circles(x, y, linkRange * Vars.tilesize);

            // 已连接的电力建筑画框 (与节点一致; 只遍历现有连线, 不做全范围扫描)
            for(int i = 0; i < power.links.size; i++){
                Building link = Vars.world.build(power.links.get(i));
                if(link != null && EndGameTurret.this.linkValid(this, link)){
                    Drawf.square(link.x, link.y, link.block.size * Vars.tilesize / 2f + 1f, Pal.place);
                }
            }
            Draw.reset();
        }

        /** 绘制当前电力连线 (选中时可见)。 */
        protected void drawPowerLinks(){
            if(power == null || power.links.size == 0) return;

            Draw.z(Layer.power + 1f);
            // ★ 与普通电力线区分: 本炮台的连线改用终局主色 (scarColor) 的
            //   外层光晕 + 内层亮芯, 不再沿用原版 Pal.power 的蓝色。
            for(int i = 0; i < power.links.size; i++){
                Building link = Vars.world.build(power.links.get(i));
                if(link == null) continue;

                Lines.stroke(3.6f);
                Draw.color(scarColor, 0.35f);
                Lines.line(x, y, link.x, link.y);

                Lines.stroke(1.3f);
                Draw.color(scarColor);
                Lines.line(x, y, link.x, link.y);
            }
            Draw.reset();
        }

        // ================= 主更新 =================

        @Override
        public void updateTile() {
            // ★ 复活/重建后的第一帧: 先按记忆恢复电力连接, 再刷新记忆
            if(pendingPowerRestore){
                restorePowerLinks();
                pendingPowerRestore = false;
            }
            snapshotPowerLinks();

            // ★ 负血量: 读档 / 外部 heal 都可能把血量改回非负, 这里修正
            enforceNegativeHealth();

            // ★ 需求: 每秒回复 50000 内部血量 (delta 归一化, 与帧率无关)
            if(!internalDead){
                setInternalHealth(internalHealthNow() + internalHealPerSecond * delta());
            }

            // ★ 持续施压: 被自身回溯拉回来的"哈希表血量"单位, 低频重新判定
            if(!suppressedUnits.isEmpty() && timer.get(suppressTime, 8f)){
                suppressTick();
            }

            updateEyes();

            float trueEff = trueEfficiency();
            if(trueEff > 0.0001f){
                float value = eyesAlpha > trueEff ? 1f : trueEff;
                eyesAlpha = Mathf.lerpDelta(eyesAlpha, trueEff, 0.06f * value);
            }else{
                eyesAlpha = Mathf.lerpDelta(eyesAlpha, 0f, 0.06f);
            }

            // 只有 "通电 + 有弹药" 时才跑基类逻辑 (索敌 / 装填 / 开火)
            if(canConsume()){
                updateAntiBullets();
                super.updateTile();
            }

            // 眼睛朝目标 (或玩家鼠标) 偏移
            if(isControlled()){
                eyeTargetOffset.trns(angleTo(unit.aimX(), unit.aimY()), dst(unit.aimX(), unit.aimY()) / (range / 3f));
            }else if(target != null && trueEff > 0.0001f){
                eyeTargetOffset.trns(angleTo(targetPos.x, targetPos.y), dst(targetPos.x, targetPos.y) / (range / 3f));
            }
            eyeTargetOffset.limit(2f);

            if(((target != null && !isControlled()) || (isControlled() && unit.isShooting())) && trueEff > 0.0001f){
                // 攻击态: 环灯亮 + 轮盘转 + 随机慢速闪电
                eyeResetTime = 0f;
                float value = lightsAlpha > trueEff ? 1f : trueEff;
                lightsAlpha = Mathf.lerpDelta(lightsAlpha, trueEff, 0.07f * value);

                for(int i = 0; i < 3; i++){
                    ringProgress[i] = Mathf.lerpDelta(ringProgress[i], 360f * (float)ringDirections[i], ringProgresses[i] * trueEff);
                }

                float chance = (((reloadCounter / reload) * 0.90f) + (1f - 0.90f)) * trueEff;
                float randomAngle = Mathf.random(360f);
                Tmp.v1.trns(randomAngle, 18.5f).add(x, y);

                if(Mathf.chanceDelta(0.75f * chance)){
                    createLightning(Tmp.v1.x, Tmp.v1.y, randomAngle, 5200f * trueEff);
                }
            }else{
                // 停火 60 帧后才开始熄灭环灯 / 让轮盘转回
                if(eyeResetTime >= 60f){
                    lightsAlpha = Mathf.lerpDelta(lightsAlpha, 0f, 0.07f);
                    for(int i = 0; i < 3; i++){
                        ringProgress[i] = Mathf.lerpDelta(ringProgress[i], 0f, ringProgresses[i] * trueEff);
                    }
                }else{
                    eyeResetTime += Time.delta;
                }
            }

            updateLightnings();
        }

        /**
         * 生成一条慢速闪电 (PU132 原版形态)。
         *
         * <p>★ 与 PU132 的差异 (按需求调整):</p>
         * <ul>
         *   <li><b>延伸距离</b>: PU132 原版 {@code range = 810f}, 闪电会一路延伸出去
         *       打远处单位; 本实现改为<b>激光射程的一半</b>, 只在炮台周边闪烁,
         *       不飞出去攻击远处目标;</li>
         *   <li><b>数量上限</b>: 同时存在的闪电不超过 {@link #maxLightnings} 条。</li>
         * </ul>
         *
         * <p>形态参数完全沿用 PU132 原版 —— 除下方注明的延伸距离外,
         * {@code nodeTime=3}、{@code nodeLength=50}、{@code lineWidth=2}、
         * {@code randSpacing=20}、{@code splitRandSpacing=60} 都走
         * {@link SlowLightning} 里与 {@code SlowLightningType} 对齐的默认值。
         * 闪电沿线段连续施加 {@code dmg} 伤害。</p>
         */
        protected void createLightning(float sx, float sy, float angle, float dmg){
            // ★ 数量上限: 达到上限就不再新增
            if(lightnings.size >= maxLightnings) return;

            SlowLightning l = new SlowLightning();
            l.colorFrom = Color.red;
            l.colorTo = Color.black;
            l.damage = dmg;
            // ★ 需求: 延伸距离 = 激光射程的一半
            l.range = range * 0.5f;
            // 其余形态参数全部沿用 SlowLightning 中 PU132 的默认值, 不要在这里覆盖
            // (曾经把 nodeTime 改成 5、并在 SlowLightning 里把纠偏量乘上 nodeTime,
            //  导致每段都被强行拧回目标方向 —— 就是"闪电笔直"的根因)
            l.create(team, sx, sy, angle, targetPos);
            lightnings.add(l);
        }

        /** 推进并回收本炮台的慢速闪电。 */
        protected void updateLightnings(){
            for(int i = lightnings.size - 1; i >= 0; i--){
                SlowLightning l = lightnings.get(i);
                l.update();
                if(l.removed){
                    lightnings.remove(i);
                }
            }
        }

        // ================= 眼睛 (方位 / 索敌 / 发射) =================

        /** PU132 updateEyes(): 眼睛偏移 + 目标刷新 + 内外环轮流发射。 */
        void updateEyes(){
            updateEyesOffset();
            eyeOffsetB.lerpDelta(eyeTargetOffset, 0.12f);

            eyeOffset.set(eyeOffsetB);
            eyeOffset.add(Mathf.range(reloadCounter / reload) / 2f, Mathf.range(reloadCounter / reload) / 2f);
            eyeOffset.limit(2f);

            if(((target != null && !isControlled()) || (isControlled() && unit.isShooting())) && canConsume() && trueEfficiency() >= 0.0001f){
                eyeReloads[0] += deltaB();
                eyeReloads[1] += deltaB();
            }

            if(canConsume() && trueEfficiency() > 0.0001f){
                updateEyesTargeting();
            }

            if(eyeReloads[0] >= 10f){
                eyeReloads[0] = 0f;
                if(!isControlled()){
                    if(targets[eyeSequenceA] != null) eyeShoot(eyeSequenceA);
                }else{
                    if(unit.isShooting()) playerShoot(eyeSequenceA);
                }
                eyeSequenceA = (eyeSequenceA + 1) % 8;
            }
            if(eyeReloads[1] >= 3f){
                eyeReloads[1] = 0f;
                if(!isControlled()){
                    if(targets[eyeSequenceB] != null) eyeShoot(eyeSequenceB + 8);
                }else{
                    if(unit.isShooting()) playerShoot(eyeSequenceB + 8);
                }
                eyeSequenceB = (eyeSequenceB + 1) % 8;
            }
        }

        /** PU132 updateEyesTargeting(): 失效剔除 + 威胁统计 + 每 15 帧轮转分配目标。 */
        void updateEyesTargeting(){
            for(int i = 0; i < eyeCount; i++){
                if(Units.invalidateTarget(targets[i], team, x, y)){
                    targets[i] = null;
                }
            }

            // 注意 PU132 的 eyeTime 计时与 "存在基类目标" 两个前置条件
            if(timer.get(eyeTime, 15f) && target != null && !isControlled()){
                // ★ 性能: 威胁统计原本每帧跑一次全量单位查询 (射程翻倍后查询盒更大);
                //   它只需要一个粗略的强度值, 与 15 帧一次的目标刷新共用节拍即可。
                updateThreats();

                Seq<Healthc> nTargets = UnityUtils.nearbyEnemySorted(team, x, y, range, 8f);
                if(!nTargets.isEmpty()){
                    for(int i = 0; i < targets.length; i++){
                        targets[i] = nTargets.get(i % nTargets.size);
                    }
                }
            }
        }

        /** PU132 updateThreats(): 统计范围内敌人强度, 并冻结高速单位。 */
        void updateThreats(){
            threatLevel = 1f;
            Units.nearbyEnemies(team, x - range, y - range, range * 2f, range * 2f, e -> {
                if(Mathf.within(x, y, e.x, e.y, range) && e.isAdded()){
                    threatLevel += Math.max(((e.maxHealth() + e.type.dpsEstimate) - 450f) / 1300f, 0f);
                    if(e.speed() >= 18f){
                        e.vel.setLength(0f);
                    }
                }
            });
        }

        /** PU132 updateEyesOffset(): 计算 16 只眼睛的世界坐标 (内环 36.75, 外环 25.75)。 */
        void updateEyesOffset(){
            for(int i = 0; i < eyeCount; i++){
                float angleC = ((360f / 8f) * (i % 8));
                if(i >= 8){
                    Tmp.v1.trns(angleC + 22.5f + ringProgress[1], 25.75f);
                }else{
                    Tmp.v1.trns(angleC + ringProgress[0], 36.75f);
                }
                eyesVecArray[i].set(Tmp.v1.x, Tmp.v1.y).add(x, y);
            }
        }

        // ================= 攻击 =================

        /**
         * PU132 eyeShoot(): 指定眼睛发射一束秒杀激光。
         *
         * <p>原版逻辑是先对目标造成 {@code 350 * threatLevel} 伤害, 若致死则
         * 汽化 + 湮灭, 然后<b>无论如何</b>都画一道 {@link ScarFx#endgameLaser}。
         * 本项目额外强化了秒杀: 目标即使没被这一击打死也会被直接湮灭
         * (需求: "秒杀这个游戏里几乎所有的单位")。</p>
         */
        void eyeShoot(int index){
            Posc t = targets[index];
            if(t == null) return;

            // ★ 单发威力加强: 350 → 4000 (再乘威胁等级)
            float dmg = 4000f * threatLevel;

            if(t instanceof Healthc) ((Healthc)t).damage(dmg);

            // ★ 强化秒杀: 只要还活着也一并湮灭 (原版仅在致死时湮灭)
            if(!Vars.net.client()){
                if(t instanceof Unit u && u.isValid() && !u.dead()){
                    SpecialFx.endgameVapourize.at(u.x, u.y, angleTo(u), new Object[]{this, u});
                    annihilateUnit(u);
                }else if(t instanceof Building b && b.isValid()){
                    ScarFx.vapourizeTile.at(b.x, b.y, b.block.size);
                    annihilateEntity(b);
                }
            }

            Object[] data = {eyesVecArray[index], t, 0.625f};
            ScarFx.endgameLaser.at(eyesVecArray[index].x, eyesVecArray[index].y, 0f, data);
            Z_Sounds.endgameSmallShoot.at(x, y);

            targets[index] = null;
        }

        /** 玩家手动控制时, 朝鼠标位置 15 范围内秒杀。 */
        void playerShoot(int index){
            final float rnge = 15f;
            float ux = unit.aimX();
            float uy = unit.aimY();

            if(!Mathf.within(x, y, ux, uy, range * 1.5f)) return;

            // ★ 建筑: 用空间索引查询 (半径很小, 但索引仍比逐 tile 扫描便宜);
            //   先收集再摧毁, 避免在索引遍历中直接删除建筑
            entitySeq.clear();
            Vars.indexer.allBuildings(ux, uy, rnge, building -> {
                if(building.team == team || building == this || building.dead) return;
                if(!Mathf.within(ux, uy, building.x, building.y, rnge)) return;
                entitySeq.add(building);
            });
            for(int i = 0; i < entitySeq.size; i++){
                Building b = (Building)entitySeq.get(i);
                announceLaser(new Vec2(ux, uy), b, 0.525f);
                annihilateBuilding(b);
            }
            entitySeq.clear();

            // ★ 先收集再击杀: 在单位空间索引遍历过程中直接删除单位会破坏遍历
            entitySeq.clear();
            Units.nearbyEnemies(team, ux - rnge, uy - rnge, rnge * 2f, rnge * 2f, e -> {
                if(!e.dead && Mathf.within(ux, uy, e.x, e.y, rnge + e.hitSize)){
                    entitySeq.add(e);
                }
            });
            for(int i = 0; i < entitySeq.size; i++){
                Unit u = (Unit)entitySeq.get(i);
                if(u == null || u.dead) continue;
                SpecialFx.endgameVapourize.at(u.x, u.y, angleTo(u), new Object[]{this, u});
                annihilateUnit(u);
            }
            entitySeq.clear();

            Tmp.v1.set(eyesVecArray[index]).add(ux, uy).scl(0.5f);

            Object[] dataB = {eyesVecArray[index], new Vec2(ux, uy), 0.625f};
            ScarFx.endgameLaser.at(Tmp.v1.x, Tmp.v1.y, 0f, dataB);
            Z_Sounds.endgameSmallShoot.at(x, y);
        }

        /** 画一道从本炮台指向目标的终局激光 (统一入口, 便于限流)。 */
        protected void announceLaser(Position from, Posc to, float width){
            Object[] data = {from, to, width};
            ScarFx.endgameLaser.at(x, y, 0f, data);
        }

        /**
         * PU132 updateAntiBullets(): 定时扫描范围内敌方子弹, 威胁过大者直接被湮灭。
         *
         * <p>判定条件 (任一满足): 单发威力 > 1600 / 溅射半径 > 120 /
         * 全场累计威力 > 13000 / 攻击者本身在射程之外。</p>
         */
        void updateAntiBullets(){
            float trueEff = trueEfficiency();
            // ★ 性能: 场上没有子弹时直接跳过空间查询
            if(trueEff > 0.0001f && !Groups.bullet.isEmpty() && timer.get(bulletTime, 4f / Math.max(trueEff, 0.001f))){
                damageFull = 0f;

                // ★ 性能优化: 原版做了两次空间查询 (第一次求和, 第二次判定并移除)。
                //   这里合并为一次查询: 收集子弹的同时记录每发威力, 第二轮只遍历已收集的列表,
                //   既省掉一次 Groups.bullet.intersect, 也避免对同一发子弹重复计算破片伤害。
                entitySeq.clear();
                bulletDamages.clear();
                Groups.bullet.intersect(x - range, y - range, range * 2f, range * 2f, b -> {
                    if(Mathf.within(x, y, b.x, b.y, range) && b.team != team){
                        float d = bulletDamage(b.type);
                        damageFull += d;
                        entitySeq.add(b);
                        bulletDamages.add(d);
                    }
                });

                boolean killed = false;
                int fx = 0;
                for(int i = 0; i < entitySeq.size; i++){
                    Bullet b = (Bullet)entitySeq.get(i);
                    damageB = bulletDamages.get(i);
                    boolean ownerOut = b.owner != null && !ownerWithin(b.owner);

                    if(damageB > 1600f || b.type.splashDamageRadius > 120f || damageFull + damageB > 13000f || ownerOut){
                        killed = true;
                        // ★ 特效上限: 一次扫描最多画 maxBurstLasers 道
                        if(fx++ < maxBurstLasers){
                            Object[] data = {new Vec2(x + (eyeOffset.x * 2f), y + (eyeOffset.y * 2f)), new Vec2(b.x, b.y), 0.625f};
                            ScarFx.endgameLaser.at(x, y, 0f, data);
                        }
                        try{
                            b.remove();
                        }catch(Throwable ignored){
                        }
                    }
                }

                if(killed) Z_Sounds.endgameSmallShoot.at(x, y);
                entitySeq.clear();
                bulletDamages.clear();
            }
        }

        /** 计算一发子弹 (含所有破片) 的总威力 (PU132 Utils.getBulletDamage 语义)。 */
        protected float bulletDamage(BulletType type){
            float sum = zzw.util.UnityUtils.getBulletDamage(type);
            BulletType current = type;
            int totalFrags = 1;

            for(int i = 0; i < 16; i++){
                if(current.fragBullet == null) break;

                BulletType frag = current.fragBullet;
                totalFrags *= current.fragBullets;
                sum += zzw.util.UnityUtils.getBulletDamage(frag) * totalFrags;

                current = frag;
            }
            return sum;
        }

        /**
         * PU132 killUnits(): 齐射时湮灭爆炸范围内的所有敌方单位。
         *
         * <p>★ 性能与范围:</p>
         * <ul>
         *   <li>范围固定用 {@link EndGameTurret#blastRadius} (820, 与激光射程解耦, 保持不变);</li>
         *   <li>先收集再击杀 —— 在单位空间索引遍历中直接删除单位会破坏遍历;</li>
         *   <li>汽化 / 激光特效各有数量上限, 目标很多时只给前若干个播放
         *       (整体范围观感由 {@link ScarFx#endGameShoot} 的冲击波承担),
         *       伤害结算对所有目标照常执行;</li>
         *   <li>击杀走 {@link #annihilateUnit} 的三级升级链, 常规单位在最低开销的
         *       第一层就会消失。</li>
         * </ul>
         */
        void killUnits(){
            entitySeq.clear();

            Units.nearbyEnemies(team, x - blastRadius, y - blastRadius, blastRadius * 2f, blastRadius * 2f, e -> {
                if(!e.dead && Mathf.within(x, y, e.x, e.y, blastRadius)){
                    entitySeq.add(e);
                }
            });

            int fx = 0;
            for(int i = 0; i < entitySeq.size; i++){
                Unit u = (Unit)entitySeq.get(i);
                if(u == null || u.dead) continue;

                if(fx < maxBurstVapourizes){
                    SpecialFx.endgameVapourize.at(u.x, u.y, angleTo(u), new Object[]{this, u});
                }
                if(fx < maxBurstLasers){
                    announceLaser(new Vec2(x + eyeOffset.x, y + eyeOffset.y), u, 1f);
                }
                fx++;

                annihilateUnit(u);
            }
            entitySeq.clear();
        }

        /**
         * PU132 killTiles(): 齐射时摧毁爆炸范围内所有敌方建筑。
         *
         * <p>★ 性能: 原实现 {@code trueEachBlock} 逐个 tile 扫描 (半径 820 时约 1 万次
         * {@code world.build}), 目标一多就是齐射卡顿的主要来源。改为方块空间索引
         * ({@code indexer.allBuildings}) 一次查询 —— 每个建筑只回调一次, 天然去重。</p>
         *
         * <p>大的方块 (size>=3) 播放汽化; 每 5 个 (或 size>=5) 画一道终局激光
         * (两者均有数量上限); 最后以 "最远建筑距离 ×2" 为半径放一次批量建筑汽化。</p>
         */
        void killTiles(){
            entitySeq.clear();
            damageB = 0f;
            shouldLaser = 0;

            Vars.indexer.allBuildings(x, y, blastRadius + 5f, building -> {
                if(building == this || building.dead || building.team == team || !building.isValid()) return;
                if(!Mathf.within(x, y, building.x, building.y, blastRadius + 5f)) return;

                if(shouldLaser < maxBurstVapourizes && building.block.size >= 3){
                    ScarFx.vapourizeTile.at(building.x, building.y, building.block.size, building);
                }
                if(shouldLaser < maxBurstLasers && ((shouldLaser % 5) == 0 || building.block.size >= 5)){
                    announceLaser(new Vec2(x + (eyeOffset.x * 2f), y + (eyeOffset.y * 2f)), building, 1f);
                }
                entitySeq.add(building);
                damageB = Math.max(Mathf.dst2(building.x, building.y, x, y), damageB);
                shouldLaser++;
            });

            damageB = Mathf.sqrt(damageB) * 2f;

            int count = entitySeq.size;
            if(count > 0){
                Building[] arr = new Building[count];
                for(int i = 0; i < count; i++){
                    arr[i] = (Building)entitySeq.get(i);
                }
                for(int i = 0; i < count; i++){
                    annihilateBuilding(arr[i]);
                }
                SpecialFx.endgameVapourize.at(x, y, damageB, new Object[]{this, arr, hitSize() / 4f});
            }
            entitySeq.clear();
        }

        /** PU132 shoot(): 装填完成 → 全屏湮灭齐射。 */
        @Override
        protected void shoot(BulletType type){
            consume();
            killTiles();
            killUnits();

            ScarFx.endGameShoot.at(x, y);
            Z_Sounds.endgameShoot.at(x, y, 1f, 1.5f);
        }

        // ================= 湮灭 (秒杀机制核心) =================

        /**
         * 统一湮灭入口: 单位走 {@link #annihilateUnit}, 建筑走 {@link #annihilateBuilding}。
         *
         * <p>★ 性能: 该入口不再只给眼睛光束用, 而是每个齐射目标都会走一次, 因此
         * 必须保持"绝大多数目标在第一层就结束"的开销特性。</p>
         */
        void annihilateEntity(Entityc e){
            if(e instanceof Unit){
                annihilateUnit((Unit)e);
            }else if(e instanceof Building){
                annihilateBuilding((Building)e);
            }
        }

        /**
         * 摧毁建筑: 标准 {@code kill()} → 仍存活则抹掉地块。
         *
         * <p>部分模组会重写建筑的 {@code kill()} 让建筑"打不死"; 地块抹除是最后兜底。</p>
         */
        void annihilateBuilding(Building b){
            if(b == null || b == this) return;

            try{
                b.kill();
            }catch(Throwable ignored){
            }

            if(b.isValid() && b.tile != null && b.tile.build == b){
                try{
                    b.tile.remove();
                }catch(Throwable ignored){
                }
            }
        }

        /**
         * ★ 湮灭单位 —— 三级升级链, 保证任何单位都能被真正消灭, 同时把开销压到最低。
         *
         * <p>需求: "特别强但开销大的手段放到最低优先级: 只有前面的手段全部失效时才启用,
         * 正常运行时不启用"。这里的层级顺序就是该优先级:</p>
         * <ol>
         *   <li><b>第一层 (极低成本)</b>: 血量归零 + 引擎 {@code kill → destroy → remove}。
         *       原版单位、绝大多数模组单位都在这一层就消失;</li>
         *   <li><b>第二层 (低成本)</b>: 打上复活封印 {@link zzw.util.ReviveSeal} 后再走一次
         *       击杀链 —— 覆盖"会检查血量台账复活"的本项目 End 单位</li>
         *   <li><b>第三层 (高成本, 反射)</b>: 只在单位仍然存活/仍在实体组里时启用 ——
         *       关闭外部复活注册表、清空防秒杀字段、打穿独立血量池、移植 FlameOut 的
         *       两种"逆天攻击", 最后从实体组硬摘除。</li>
         * </ol>
         *
         * <p>另外两条设计约束保持不变: 不写 NaN 坐标 (会扩散到特效/索敌/统计),
         * 不手工拆解实体 (kill/destroy/remove 自带幂等守卫, 队伍计数不会被重复扣减)。</p>
         *
         * @param u 要湮灭的单位
         */
        void annihilateUnit(Unit u){
            if(u == null) return;

            // ★ FlameOut 共鸣单位必须走它自己的移除协议 (见 purgeFlameEmpathy):
            //   它重写了 remove()/destroy() 并拒绝死亡, 用普通击杀链只会被"假死"——
            //   AI 仍被 FO 每帧直接 update()、拖尾/技能照常, 但本体已不在引擎组里而消失。
            if(purgeFlameEmpathy(u)) return;

            // ★ 第一层: 廉价击杀
            if(killDirect(u)) return;

            // ★ 第二层: 复活封印后重试
            zzw.util.ReviveSeal.seal(u);
            if(killDirect(u)) return;

            // ★ 第三层: 反射拆解 (只有打不死的单位才会走到这里)
            annihilateStubbornUnit(u);
        }

        // ================= FlameOut 共鸣单位 (走它自己的移除协议) =================

        /**
         * ★ 修复 FO 共鸣单位"假死"。
         *
         * <p>成因: {@code EmpathyUnit} 重写了 {@code remove()}/{@code destroy()}, 只有它自己的
         * {@code trueHealth} 归零才会真正移除; 若我们直接反射把它从 {@code Groups.unit/draw/all}
         * 摘掉并把 {@code added} 置 false, FO 的 `EmpathyDamage` 仍持有它的引用:</p>
         * <ul>
         *   <li>{@code EmpathyDamage.update()} 每 15 帧发现它"不在 Groups.all", 会递增
         *       {@code removeCount}, 并 {@code add()}/{@code duplicate()} 把它重新拉回来;</li>
         *   <li>{@code removeCount >= 5} 后 FO 每帧直接调用它的 {@code update()} ——
         *       AI 继续瞄准/攻击/放技能, 拖尾与技能特效照常绘制;</li>
         *   <li>但引擎侧该实体已 not added, 本体贴图不再按正常单位绘制 —— 于是出现
         *       "单位还活着、拖尾还在, 但本体和贴图消失"的半死状态。</li>
         * </ul>
         *
         * <p>正确做法与 FO 自己的 {@code forceKillAllEmpathy()} 一致: 先从
         * {@code EmpathyDamage.units / empathyMap} 摘掉持有者 (断掉 update/draw 与
         * duplicate 重生), 再调用它公开的 {@code forceRemove()} (停 AI 音效 + 正规
         * {@code super.remove()})。这样单位是"真正被移除", 不会残留半死状态。</p>
         *
         * @return true 表示已按 FO 协议处理完成 (调用方无需再做反射拆解)
         */
        protected boolean purgeFlameEmpathy(Unit u){
            // 用单位自己的 ClassLoader 找 FO 的类 (跨模组 Class.forName 不可靠), 结果按类缓存
            Class<?> empathyCls = findModClassCached(u, "flame.unit.empathy.EmpathyUnit");
            if(empathyCls == null || !empathyCls.isInstance(u)) return false;

            try{
                Class<?> damageCls = findModClassCached(u, "flame.unit.empathy.EmpathyDamage");
                if(damageCls == null) return false;

                // 1) 摘掉 FO 的持有者引用 (units / empathyMap), 含被 duplicate 出来的新实例
                Seq<Unit> duplicates = new Seq<>();
                Object holderSeq = findField(damageCls, "units").get(null);
                Object empathyMap = findField(damageCls, "empathyMap").get(null);

                if(holderSeq instanceof Seq){
                    Seq<?> seq = (Seq<?>)holderSeq;
                    for(int i = seq.size - 1; i >= 0; i--){
                        Object holder = seq.get(i);
                        if(holder == null) continue;
                        Unit hu = (Unit)findField(holder.getClass(), "unit").get(holder);
                        if(hu == null) continue;

                        // 同一对象 / 同 id / 同一位置上的 duplicate (FO 重生时会原地复制新实例)
                        boolean same = hu == u || hu.id == u.id
                            || (hu.type == u.type && Mathf.within(hu.x, hu.y, u.x, u.y, hu.hitSize + u.hitSize + 200f));
                        if(!same) continue;

                        if(hu != u && !duplicates.contains(hu)) duplicates.add(hu);
                        ((Seq)seq).remove(i);
                        if(empathyMap instanceof arc.struct.IntMap){
                            ((arc.struct.IntMap)empathyMap).remove(hu.id);
                        }
                    }
                }
                if(empathyMap instanceof arc.struct.IntMap){
                    ((arc.struct.IntMap)empathyMap).remove(u.id);
                }

                // 2) 走 FO 自己的 forceRemove() —— 正规移除, 不触发 duplicate 重生
                java.lang.reflect.Method forceRemove = empathyCls.getMethod("forceRemove");
                forceRemove.invoke(u);

                // 3) duplicate 出来的实例 FO 已经不再管理, 也一并正规移除
                for(Unit other : duplicates){
                    if(other != null && other != u && other.isAdded()){
                        try{
                            forceRemove.invoke(other);
                        }catch(Throwable ignored){
                        }
                    }
                }

                return !u.isAdded();
            }catch(Throwable ignored){
                return false;
            }
        }

        /**
         * 廉价击杀: 血量归零 → {@code kill()} → {@code destroy()} → {@code remove()},
         * 每一步都用引擎自带的幂等守卫。
         *
         * @return true 表示单位已经不在实体组里 (击杀完成)
         */
        protected boolean killDirect(Unit u){
            try{
                if(u.health > 0f) u.health = 0f;
            }catch(Throwable ignored){
            }

            try{
                if(!u.dead) u.kill();
            }catch(Throwable ignored){
            }

            if(u.isAdded()){
                try{
                    u.destroy();
                }catch(Throwable ignored){
                }
            }

            if(u.isAdded()){
                try{
                    u.remove();
                }catch(Throwable ignored){
                }
            }

            return !u.isAdded();
        }

        /**
         * 第三层: 反射拆解"打不死的单位" (FlameOut 共鸣/消沉、其它锁血模组)。
         *
         * <p>开销大, 只在 {@link #annihilateUnit} 的前两层全部失效时才会执行。</p>
         */
        void annihilateStubbornUnit(Unit u){
            if(!u.isAdded() && u.dead) return;

            // 外部 "复活注册表" (FlameOut EmpathyDamage) 的启用开关,
            // 必须在整段击杀流程里保持关闭, 否则 remove() 时会 duplicate() 重生。
            Class<?> reviveRegistry = findModClassCached(u, "flame.unit.empathy.EmpathyDamage");
            Boolean reviveWasOn = disableExternalRevive(reviveRegistry);
            // ★ FlameOut 共鸣的 5 分钟后自动重生总开关 (FlameSettings.empathyRespawn)
            Boolean respawnWasOn = disableFlameRespawn(u);

            try{
                // ★ 清除各类 "防秒杀/死亡拒绝" 字段 (含 antiCheat/ac 嵌套台账)
                clearProtectionFields(u);

                // ★ 摘除外部复活注册表并关闭共鸣的复活生成器
                if(reviveRegistry != null){
                    purgeReviveRegistry(reviveRegistry, u);
                    neutralizeReviveSpawner(reviveRegistry);
                }

                // ★ 打穿模组的 "独立血量池"
                breakExternalTrueHealth(u);

                // ★ 移植 FlameOut 的两种 "逆天攻击" 作为额外秒杀手段
                //   消沉 (Despondency) 的长矛贯穿: 直接注入 maxHealth + 1000 的伤害,
                //   绕开一切防作弊上限 —— 原版武器就是这么强杀的。
                applyDespondencySpear(u);
                //   共鸣 (Empathy) 的黑洞坍缩: 5 段 maxHealth/5 连续写入。
                applyEmpathyCollapse(u);

                // ★ 血量归零 → 正常击杀链 (kill/destroy/remove 都已被封印放行)
                killDirect(u);

                // ★ 第四层 (最末兜底): 穿透"把血量存在模组内部容器里"的实现 ——
                //   反射遍历单位对象图, 找到血量字段/血量容器并清零, 然后再走一次击杀链。
                if(purgeExternalHealthPools(u)){
                    killDirect(u);
                }

                // ★ 兜底 —— 标记 dead 并直接从实体组摘除。
                //   即使单位重写了 remove()/destroy() 并拒绝死亡, 这里也能保证它消失。
                try{
                    u.dead = true;
                }catch(Throwable ignored){
                }
                hardRemove(u);

                // ★ 仍可能被它自己的字段回溯重新拉回来 → 登记 "持续施压",
                //   周期性重新判定 (数量与时长都有上限, 不参与常规战斗开销)。
                registerSuppression(u);
            }catch(Throwable ignored){
            }finally{
                restoreExternalRevive(reviveRegistry, reviveWasOn);
                restoreFlameRespawn(u, respawnWasOn);
            }
        }

        // ================= 模组侧血量容器穿透 =================

        /** 类 → 全部字段 (含父类与静态字段, 已 setAccessible) 缓存。 */
        private static final Map<Class<?>, Seq<Field>> allFieldCache = new ConcurrentHashMap<>();

        /** 血量相关字段名 (排除 max / time / recent / suppress 等非血量字段)。 */
        private static final java.util.regex.Pattern healthName =
            java.util.regex.Pattern.compile("^(health|hp|blood|life|vital|durab|hull|curhp|curhealth|realhealth|truehealth|nowhealth)[a-z0-9_]*$");

        protected static Seq<Field> allFields(Class<?> cls){
            return allFieldCache.computeIfAbsent(cls, c -> {
                Seq<Field> out = new Seq<>();
                for(Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()){
                    for(Field f : k.getDeclaredFields()){
                        try{
                            f.setAccessible(true);
                        }catch(Throwable ignored){
                            continue;
                        }
                        out.add(f);
                    }
                }
                return out;
            });
        }

        /**
         * ★ 穿透"模组内部哈希表血量": 扫描单位对象图, 把血量字段与血量容器清零。
         *
         * <p>只处理两类明确的血量来源, 避免误伤其它状态:</p>
         * <ol>
         *   <li>名字是血量语义的数值字段 (health/hp/blood/life/vital/durability…),
         *       不处理 max/time/recent 这类字段;</li>
         *   <li>以该单位 (或它的 id) 为键的容器 —— {@code Map / ObjectMap /
         *       ObjectFloatMap / IntMap / IntFloatMap}, 把对应数值清零或移除条目。</li>
         * </ol>
         *
         * <p>性能: 只在整条击杀链全部失效时才调用; 字段列表按类缓存,
         * 深度限制为 1 层、对象数量上限 64。</p>
         *
         * @return true 表示确实改写到了东西 (调用方应再试一次击杀)
         */
        protected boolean purgeExternalHealthPools(Unit u){
            if(u == null) return false;
            try{
                java.util.IdentityHashMap<Object, Boolean> visited = new java.util.IdentityHashMap<>();
                return purgeHealthIn(u, u, 0, visited);
            }catch(Throwable ignored){
                return false;
            }
        }

        protected boolean purgeHealthIn(Object holder, Unit u, int depth, java.util.IdentityHashMap<Object, Boolean> visited){
            if(holder == null || depth > 1 || visited.size() > 64 || visited.put(holder, Boolean.TRUE) != null) return false;

            boolean changed = false;
            for(Field f : allFields(holder.getClass())){
                try{
                    Class<?> type = f.getType();
                    String n = f.getName().toLowerCase(java.util.Locale.ROOT);
                    boolean healthish = healthName.matcher(n).matches() && !n.contains("max");

                    if(type == boolean.class){
                        if(healthish && f.getBoolean(holder)){
                            f.setBoolean(holder, false);
                            changed = true;
                        }
                        continue;
                    }

                    if(type == float.class || type == double.class || type == int.class || type == long.class){
                        if(healthish){
                            if(type == float.class) f.setFloat(holder, 0f);
                            else if(type == double.class) f.setDouble(holder, 0d);
                            else if(type == int.class) f.setInt(holder, 0);
                            else f.setLong(holder, 0L);
                            changed = true;
                        }
                        continue;
                    }

                    if(Number.class.isAssignableFrom(type)){
                        if(healthish && f.get(holder) != null){
                            Object zero = type == Double.class ? (Object)0d
                                : type == Long.class ? (Object)0L
                                : type == Integer.class ? (Object)0
                                : (Object)0f;
                            f.set(holder, zero);
                            changed = true;
                        }
                        continue;
                    }

                    Object value = f.get(holder);
                    if(value == null) continue;

                    // 容器: 只清与目标单位相关的条目; 字段名本身就是血量语义时清空全部数值
                    if(zeroUnitEntries(value, u, healthish)){
                        changed = true;
                        continue;
                    }

                    // 再深入一层: 只看血量/状态/数据/池/管理类字段, 限制开销
                    if(depth < 1 && !(value instanceof Unit) && !(value instanceof mindustry.type.UnitType)
                        && !(value instanceof Block) && !(value instanceof BulletType)
                        && (healthish || n.contains("data") || n.contains("state") || n.contains("attr")
                            || n.contains("status") || n.contains("pool") || n.contains("manager")
                            || n.contains("storage") || n.contains("holder") || n.contains("body"))){
                        changed |= purgeHealthIn(value, u, depth + 1, visited);
                    }
                }catch(Throwable ignored){
                }
            }
            return changed;
        }

        /** 把容器里与目标单位关联的数值清零 (支持常见容器类型)。 */
        protected boolean zeroUnitEntries(Object container, Unit u, boolean zeroAll){
            boolean changed = false;
            try{
                if(container instanceof java.util.Map){
                    java.util.Map.Entry<?, ?>[] entries = ((java.util.Map<?, ?>)container).entrySet().toArray(new java.util.Map.Entry[0]);
                    for(java.util.Map.Entry<?, ?> e : entries){
                        if((zeroAll || keyMatches(e.getKey(), u)) && e.getValue() instanceof Number){
                            ((java.util.Map)container).put(e.getKey(), 0f);
                            changed = true;
                        }
                    }
                }else if(container instanceof arc.struct.ObjectMap){
                    arc.struct.ObjectMap map = (arc.struct.ObjectMap)container;
                    for(Object o : map){
                        arc.struct.ObjectMap.Entry entry = (arc.struct.ObjectMap.Entry)o;
                        if((zeroAll || keyMatches(entry.key, u)) && entry.value instanceof Number){
                            entry.value = 0f;
                            changed = true;
                        }
                    }
                }else if(container instanceof arc.struct.ObjectFloatMap){
                    arc.struct.ObjectFloatMap map = (arc.struct.ObjectFloatMap)container;
                    if(zeroAll){
                        for(Object k : map.keys()){
                            map.put(k, 0f);
                            changed = true;
                        }
                    }else if(map.containsKey(u)){
                        map.put(u, 0f);
                        changed = true;
                    }
                }else if(container instanceof arc.struct.IntMap){
                    arc.struct.IntMap map = (arc.struct.IntMap)container;
                    if(map.containsKey(u.id)){
                        if(zeroAll) map.put(u.id, 0f);
                        else map.remove(u.id);
                        changed = true;
                    }
                }else if(container instanceof arc.struct.IntFloatMap){
                    arc.struct.IntFloatMap map = (arc.struct.IntFloatMap)container;
                    if(map.containsKey(u.id)){
                        map.put(u.id, 0f);
                        changed = true;
                    }
                }else if(container instanceof arc.struct.LongMap){
                    arc.struct.LongMap map = (arc.struct.LongMap)container;
                    if(map.containsKey(u.id)){
                        if(zeroAll) map.put(u.id, 0f);
                        else map.remove(u.id);
                        changed = true;
                    }
                }
            }catch(Throwable ignored){
            }
            return changed;
        }

        protected boolean keyMatches(Object key, Unit u){
            if(key == null) return false;
            if(key == u) return true;
            if(key instanceof Number){
                return ((Number)key).longValue() == u.id;
            }
            return String.valueOf(u.id).equals(String.valueOf(key));
        }

        // ================= 持续施压 =================

        /** 持续施压: 上次被回溯拉回来的单位, 按固定节拍重新判定。 */
        protected int suppressTime = timers++;

        /** 待持续施压的单位 → 剩余帧数 (有数量上限)。 */
        protected final arc.struct.ObjectFloatMap<Unit> suppressedUnits = new arc.struct.ObjectFloatMap<>();

        protected final Seq<Unit> suppressScratch = new Seq<>();

        protected static final int maxSuppressed = 64;
        protected static final float suppressFrames = 600f;

        protected void registerSuppression(Unit u){
            if(u == null) return;
            // 已在施压列表中 → 不重置计时, 保证一定会到期回收
            if(suppressedUnits.containsKey(u)) return;
            if(suppressedUnits.size >= maxSuppressed) return;
            suppressedUnits.put(u, suppressFrames);
        }

        /** 每 8 帧一次, 对登记单位先廉价再升级地重新施压; 到期或真正消失后移除。 */
        protected void suppressTick(){
            if(suppressedUnits.isEmpty()) return;

            suppressScratch.clear();
            for(Unit u : suppressedUnits.keys()){
                if(u != null) suppressScratch.add(u);
            }

            int limit = Math.min(suppressScratch.size, 16);
            for(int i = 0; i < limit; i++){
                Unit u = suppressScratch.get(i);
                float left = suppressedUnits.get(u, 0f) - 8f;

                if(u == null || u.dead() || left <= 0f){
                    suppressedUnits.remove(u, 0f);
                    continue;
                }
                if(!u.isAdded()){
                    suppressedUnits.put(u, left);
                    continue;
                }

                suppressedUnits.put(u, left);

                if(killDirect(u)) continue;
                zzw.util.ReviveSeal.seal(u);
                if(killDirect(u)) continue;
                annihilateStubbornUnit(u);
            }
            suppressScratch.clear();
        }

        /** ★ 消沉式长矛贯穿: 直接写入 {@code maxHealth + 1000} 的伤害 (移植 EndDespondencyWeapon)。 */
        protected void applyDespondencySpear(Unit u){
            try{
                u.health -= u.maxHealth() + 1000f;
            }catch(Throwable ignored){
            }
        }

        /** ★ 共鸣式黑洞坍缩: 分 5 段写入 {@code maxHealth/5} 的伤害 (移植 BlackHoleAttack)。 */
        protected void applyEmpathyCollapse(Unit u){
            float chunk = u.maxHealth() / 5f;
            for(int i = 0; i < 5; i++){
                try{
                    u.health -= chunk;
                }catch(Throwable ignored){
                }
            }
        }

        /** 关掉 FlameOut 的 "共鸣复活" 总开关 (EmpathyDamage.activeAdd)。 */
        protected Boolean disableExternalRevive(Class<?> registry){
            if(registry == null) return null;
            try{
                java.lang.reflect.Field f = findField(registry, "activeAdd");
                if(f == null) return null;
                boolean old = f.getBoolean(null);
                f.setBoolean(null, false);
                return old;
            }catch(Throwable ignored){
                return null;
            }
        }

        /** 还原 {@link #disableExternalRevive} 关掉的开关。 */
        protected void restoreExternalRevive(Class<?> registry, Boolean old){
            if(registry == null || old == null) return;
            try{
                java.lang.reflect.Field f = findField(registry, "activeAdd");
                if(f != null) f.setBoolean(null, old);
            }catch(Throwable ignored){
            }
        }

        /**
         * ★ 关闭 FlameOut 共鸣的 "5 分钟后自动重生" 总开关 (FlameSettings.empathyRespawn)。
         *
         * <p>共鸣即使被彻底移除, EmpathyDamage.update() 里仍会在
         * {@code spawner.shouldSpawn && FlameSettings.empathyRespawn} 时把 Boss 重新刷回来。
         * 只关 activeAdd 不够, 必须同时关这个全局开关。</p>
         */
        protected Boolean disableFlameRespawn(Unit u){
            Class<?> settings = findModClassCached(u, "flame.FlameSettings");
            if(settings == null) return null;
            try{
                java.lang.reflect.Field f = findField(settings, "empathyRespawn");
                if(f == null || f.getType() != boolean.class) return null;
                boolean old = f.getBoolean(null);
                f.setBoolean(null, false);
                return old;
            }catch(Throwable ignored){
                return null;
            }
        }

        /** 还原 {@link #disableFlameRespawn} 关掉的开关。 */
        protected void restoreFlameRespawn(Unit u, Boolean old){
            if(old == null) return;
            Class<?> settings = findModClassCached(u, "flame.FlameSettings");
            if(settings == null) return;
            try{
                java.lang.reflect.Field f = findField(settings, "empathyRespawn");
                if(f != null) f.setBoolean(null, old);
            }catch(Throwable ignored){
            }
        }

        /**
         * ★ 清空共鸣的复活生成器 (EmpathyDamage.spawner)。
         *
         * <p>把 spawner 的 active/shouldSpawn/spawned/disabled 全部压成 "不再生成",
         * 即使全局开关被其它代码重新打开也不会把 Boss 刷回来。</p>
         */
        protected void neutralizeReviveSpawner(Class<?> registry){
            try{
                java.lang.reflect.Field f = findField(registry, "spawner");
                if(f == null) return;
                Object sp = f.get(null);
                if(sp == null) return;
                setObjField(sp, "active", 0f);
                setObjField(sp, "shouldSpawn", 0f);
                setObjField(sp, "disabled", 1f);
                setObjField(sp, "spawned", 1f);
                setObjField(sp, "health", -1f);
            }catch(Throwable ignored){
            }
        }

        /** 反射清除各类单位用来 "防秒杀" 的私有字段。 */
        protected void clearProtectionFields(Unit u){
            Class<?> clazz = u.getClass();
            setUnitField(u, clazz, "lastHealth", 0f);
            setUnitField(u, clazz, "trueHealth", 0f);
            setUnitField(u, clazz, "immunity", 0f);
            setUnitField(u, clazz, "rogueDamageResist", 0f);
            setUnitField(u, clazz, "parryTime", 0f);
            setUnitField(u, clazz, "damageTaken", 0f);
            setUnitField(u, clazz, "invFrames", 0f);
            // trueMaxHealth 压到 1 而不是 0: 保持 health/maxHealth 比例可计算, 避免除零 NaN
            setUnitField(u, clazz, "trueMaxHealth", 1f);
            // ★ 本项目 End 单位的血量台账在嵌套对象里 (antiCheat / ac), 不在单位本身
            clearLedgerObjects(u);
        }

        /**
         * ★ 清除嵌套血量台账 (antiCheat / ac)。
         *
         * <p>EndInvisibleUnit 的血量台账是 {@code antiCheat.lastHealth} (AntiCheatBase),
         * ThalassophobiaUnit 是 {@code ac + trueHealth}; 原来只清单位自身字段时,
         * 这些台账仍有余额 → kill() 里的 denyDeath() 直接把血量回满 → "秒杀后复活"。</p>
         */
        protected void clearLedgerObjects(Unit u){
            Class<?> clazz = u.getClass();
            for(String name : new String[]{"antiCheat", "ac"}){
                try{
                    java.lang.reflect.Field f = findField(clazz, name);
                    if(f == null || f.getType().isPrimitive()) continue;
                    Object o = f.get(u);
                    if(o == null) continue;
                    setObjField(o, "lastHealth", 0f);
                    setObjField(o, "trueHealth", 0f);
                    setObjField(o, "immunity", 1f);
                    // invTime 拉高 → 跳过 AntiCheatBase 的 30 tick 无敌帧门
                    setObjField(o, "invTime", 9999f);
                    setObjField(o, "resist", 0f);
                }catch(Throwable ignored){
                }
            }
        }

        /** 把对象上指定名字的 float/boolean 字段设为给定值 (找不到时静默跳过)。 */
        protected void setObjField(Object o, String name, float value){
            try{
                java.lang.reflect.Field f = findField(o.getClass(), name);
                if(f == null) return;
                if(f.getType() == float.class) f.setFloat(o, value);
                else if(f.getType() == boolean.class) f.setBoolean(o, value != 0f);
            }catch(Throwable ignored){
            }
        }

        /** 把单位身上指定名字的 float 字段设为给定值 (找不到/不可写时静默跳过)。 */
        protected void setUnitField(Unit u, Class<?> clazz, String name, float value){
            try{
                java.lang.reflect.Field f = findField(clazz, name);
                if(f != null && f.getType() == float.class) f.setFloat(u, value);
            }catch(Throwable ignored){
            }
        }

        /** 把单位身上指定名字的 boolean 字段设为给定值 (找不到/不可写时静默跳过)。 */
        protected void setUnitBoolField(Unit u, Class<?> clazz, String name, boolean value){
            try{
                java.lang.reflect.Field f = findField(clazz, name);
                if(f != null && f.getType() == boolean.class) f.setBoolean(u, value);
            }catch(Throwable ignored){
            }
        }

        /** 读取单位身上指定名字的字段值 (找不到时返回 null)。 */
        protected Object getUnitField(Unit u, Class<?> clazz, String name){
            try{
                java.lang.reflect.Field f = findField(clazz, name);
                return f == null ? null : f.get(u);
            }catch(Throwable ignored){
                return null;
            }
        }

        /** 通过目标单位自己的类加载器查找其它模组的类 (找不到返回 null)。 */
        protected Class<?> findModClass(Unit u, String name){
            try{
                return Class.forName(name, false, u.getClass().getClassLoader());
            }catch(Throwable ignored){
            }
            try{
                return Class.forName(name);
            }catch(Throwable ignored){
            }
            return null;
        }

        /**
         * 打穿模组的 "独立血量池" (FlameOut 共鸣/消沉等)。
         *
         * <p>数组字段只在确认目标确实是这类单位 (存在 {@code trueController}
         * 特征字段) 时才处理, 避免误伤其它模组中同名的无关字段。</p>
         */
        protected void breakExternalTrueHealth(Unit u){
            Class<?> clazz = u.getClass();
            setUnitField(u, clazz, "trueHealth", 0f);

            if(findField(clazz, "trueController") == null) return;

            Object dObj = getUnitField(u, clazz, "d");
            if(dObj instanceof float[]){
                float[] d = (float[])dObj;
                if(d.length > 0) d[0] = 0f;
                if(d.length > 1) d[1] = 1f;
            }

            Object d2Obj = getUnitField(u, clazz, "d2");
            if(d2Obj instanceof boolean[]){
                boolean[] d2 = (boolean[])d2Obj;
                if(d2.length > 0) d2[0] = true;
            }

            setUnitBoolField(u, clazz, "decoy", true);
        }

        /** 把目标单位从 FlameOut 的复活注册表里摘除。 */
        protected void purgeReviveRegistry(Class<?> registry, Unit u){
            purgeSeqField(registry, "units", u);
            purgeSeqField(registry, "excludeSeq", u);
            purgeSeqField(registry, "queueExcludeRemoval", u);
            purgeSeqField(registry, "excludeReAdd", u);

            removeIntKeyField(registry, "empathyMap", u.id);
            removeIntKeyField(registry, "exclude", u.id);
            removeIntKeyField(registry, "excludeTime", u.id);
        }

        /** 从静态 {@link Seq} 字段中移除所有指向目标单位的元素。 */
        protected void purgeSeqField(Class<?> registry, String name, Unit u){
            try{
                java.lang.reflect.Field f = findField(registry, name);
                if(f == null) return;
                Object value = f.get(null);
                if(!(value instanceof Seq)) return;
                Seq<Object> raw = (Seq<Object>)value;
                for(int i = raw.size - 1; i >= 0; i--){
                    Object e = raw.get(i);
                    if(e == u || holderUnit(e) == u){
                        raw.remove(i);
                    }
                }
            }catch(Throwable ignored){
            }
        }

        /** 从静态 int 键容器 (IntMap / IntSet / IntIntMap) 中移除目标单位的 id。 */
        protected void removeIntKeyField(Class<?> registry, String name, int id){
            try{
                java.lang.reflect.Field f = findField(registry, name);
                if(f == null) return;
                Object value = f.get(null);
                if(value == null) return;
                value.getClass().getMethod("remove", int.class).invoke(value, id);
            }catch(Throwable ignored){
            }
        }

        /** 取出注册表元素所引用的单位 (元素本身就是单位, 或元素带有 unit 字段)。 */
        protected Object holderUnit(Object holder){
            if(holder == null) return null;
            if(holder instanceof Unit) return holder;
            try{
                java.lang.reflect.Field f = findField(holder.getClass(), "unit");
                return f == null ? null : f.get(holder);
            }catch(Throwable ignored){
                return null;
            }
        }

        /** 最终兜底: 直接从实体组里摘除单位。 */
        protected void hardRemove(Unit u){
            boolean wasAdded = false;
            try{
                wasAdded = u.isAdded();
            }catch(Throwable ignored){
            }

            try{
                setUnitBoolField(u, u.getClass(), "added", false);
                // 只有单位仍挂在世界里才需要扣队伍计数/通知控制器,
                // 已正常移除的单位再扣一次会让队伍人数变成负数。
                if(wasAdded){
                    try{
                        u.team.data().updateCount(u.type, -1);
                    }catch(Throwable ignored){
                    }
                    try{
                        UnitController c = u.controller();
                        if(c != null) c.removed(u);
                    }catch(Throwable ignored){
                    }
                }
            }catch(Throwable ignored){
            }

            try{
                Groups.unit.remove(u);
            }catch(Throwable ignored){
            }
            try{
                Groups.all.remove(u);
            }catch(Throwable ignored){
            }
            try{
                Groups.draw.remove((Drawc)u);
            }catch(Throwable ignored){
            }
        }

        /** 递归查找字段 (包括父类)。★ 结果按类缓存, 避免每次湮灭都做一遍类层级扫描。 */
        java.lang.reflect.Field findField(Class<?> clazz, String name){
            Map<String, Field> cache = fieldCache.computeIfAbsent(clazz, c -> new HashMap<>());
            if(cache.containsKey(name)) return cache.get(name);

            Field found = null;
            Class<?> cur = clazz;
            while(cur != null){
                try{
                    Field f = cur.getDeclaredField(name);
                    f.setAccessible(true);
                    found = f;
                    break;
                }catch(NoSuchFieldException e){
                    cur = cur.getSuperclass();
                }catch(Throwable ignored){
                    break;
                }
            }
            cache.put(name, found);
            return found;
        }

        /** ★ 按单位类缓存的跨模组类查找 (Class.forName 开销大, 每个单位类只查一次)。 */
        Class<?> findModClassCached(Unit u, String name){
            Class<?> clazz = u.getClass();
            Map<String, Class<?>> cache = modClassCache.computeIfAbsent(clazz, c -> new HashMap<>());
            if(cache.containsKey(name)) return cache.get(name);
            Class<?> found = findModClass(u, name);
            cache.put(name, found);
            return found;
        }

        // ================= 渲染 =================

        @Override
        public void draw() {
            float oz = Draw.z();

            // 底座
            Draw.rect(baseRegion, x, y);

            // 环的底层
            Draw.z(oz + 0.01f);
            Draw.rect(ringABottomRegion, x, y, ringProgress[0]);
            Draw.rect(ringBBottomRegion, x, y, ringProgress[1]);

            // 环体 (自旋)
            Draw.z(oz + 0.02f);
            Drawf.spinSprite(ringARegion, x, y, ringProgress[0]);
            Drawf.spinSprite(ringBRegion, x, y, ringProgress[1]);
            Drawf.spinSprite(ringCRegion, x, y, ringProgress[2]);

            Draw.blend(Blending.additive);

            // 底座灯光 (颜色 G/B 通道错相位闪烁)
            Draw.z(oz + 0.005f);
            Draw.color(1f, offsetSin(0f, 5f), offsetSin(90f, 5f), eyesAlpha);
            Draw.rect(bottomLightsRegion, x, y);
            Draw.color(1f, offsetSin(0f, 5f), offsetSin(90f, 5f), lightsAlpha * offsetSin(0f, 12f));
            Draw.rect(baseLightsRegion, x, y);

            // 眼睛与环灯光
            TextureRegion[] regions = {ringAEyesRegion, ringBEyesRegion, eyeMainRegion};
            TextureRegion[] regionsB = {ringALightsRegion, ringBLightsRegion, ringCLightsRegion};
            float[] trnsScl = {1f, 0.9f, 2f};

            for(int i = 0; i < 3; i++){
                int h = i + 1;

                Draw.z(oz + 0.015f);
                Draw.color(1f, offsetSin(10f * h, 5f), offsetSin(90f + (10f * h), 5f), eyesAlpha);
                Draw.rect(regions[i], x + (eyeOffset.x * trnsScl[i]), y + (eyeOffset.y * trnsScl[i]), ringProgress[i]);

                Draw.z(oz + 0.025f);
                Draw.color(1f, offsetSin(10f * h, 5f), offsetSin(90f + (10f * h), 5f), lightsAlpha * offsetSin(5f * h, 12f));
                Draw.rect(regionsB[i], x, y, ringProgress[i]);
            }

            Draw.blend();
            Draw.z(oz);

            // 慢速闪电 (节点式, 覆盖在炮台上方)
            Draw.blend(Blending.additive);
            for(int i = 0; i < lightnings.size; i++){
                lightnings.get(i).draw();
            }
            Draw.blend();

            Draw.reset();
        }
    }

    /**
     * PU132 Utils.offsetSin: 让 RGB 通道错相位闪烁 (带 0.5 基线)。
     *
     * <p>与 {@code offsetSinB} 一样先乘 {@link Mathf#radDeg} 再叠加 Time.time。</p>
     */
    private static float offsetSin(float offset, float scl){
        return Mathf.absin(Time.time + (offset * Mathf.radDeg), scl, 0.5f) + 0.5f;
    }
}