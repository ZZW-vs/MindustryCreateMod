package zzw.content.blocks.turrets;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
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
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.power.PowerNode;
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
        consumePower(320f);
        reload = 430f;
        // ★ 需求: 激光攻击范围翻倍 (原 820 → 1640); 爆炸范围另见 blastRadius, 保持 820
        range = 1640f;
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
        // ★ 32 倍装填下每轮齐射仍消耗 2 个 terminum: 原版 10 的缓冲只够约 1 秒,
        //   这里按 32 倍同比放大 (10 × 32) 让一箱弹药的总射击时长与原版一致。
        itemCapacity = 320;
        loopSound = Z_Sounds.endgameActive;
        loopSoundVolume = 0.2f;
        shootSound = Z_Sounds.endgameShoot;

        // ★ 占位子弹 (PU132 原版: damage = (float)Double.MAX_VALUE)。
        //   本炮台不使用普通子弹, 伤害全部由眼睛光束 / 齐射结算,
        //   这里必须给 shootType 一个非 null 值 —— 否则 PowerTurret.peekAmmo()
        //   返回 null, 会在 ammoReloadMultiplier() 处抛 NPE。
        shootType = new BulletType(){{
            damage = (float)Double.MAX_VALUE;
            speed = 0.01f;
            lifetime = 1f;
            collides = false;
            hittable = false;
            absorbable = false;
            // ★ 需求: 装填倍率 = 32。走原版 BulletType.reloadMultiplier 通道
            //   (TurretBuild.ammoReloadMultiplier() 读取), 不需要额外覆写装填逻辑。
            reloadMultiplier = 32f;
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

    public class EndGameTurretBuild extends PowerTurretBuild {

        // ===== 运行状态 (与 PU132 EndGameTurretBuilding 字段一一对应) =====

        /** 威胁等级 (1 + 范围内敌人强度), 放大眼睛光束伤害。 */
        protected float threatLevel = 1f;
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
            // 不可摧毁: 任何来源的伤害都不结算
        }

        @Override
        public void kill(){
            // 不可摧毁: 忽略一切强制击杀
        }

        @Override
        public void killed(){
            // 不可摧毁: 忽略引擎的销毁入口 (damage/kill 之外的最后一条路径)
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

        // ================= 主更新 =================

        @Override
        public void updateTile() {
            // ★ 复活/重建后的第一帧: 先按记忆恢复电力连接, 再刷新记忆
            if(pendingPowerRestore){
                restorePowerLinks();
                pendingPowerRestore = false;
            }
            snapshotPowerLinks();

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
                    createLightning(Tmp.v1.x, Tmp.v1.y, randomAngle, 520f * trueEff);
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

            if(eyeReloads[0] >= 15f){
                eyeReloads[0] = 0f;
                if(!isControlled()){
                    if(targets[eyeSequenceA] != null) eyeShoot(eyeSequenceA);
                }else{
                    if(unit.isShooting()) playerShoot(eyeSequenceA);
                }
                eyeSequenceA = (eyeSequenceA + 1) % 8;
            }
            if(eyeReloads[1] >= 5f){
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

            float dmg = 350f * threatLevel;

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

            // ★ 第一层: 廉价击杀
            if(killDirect(u)) return;

            // ★ 第二层: 复活封印后重试
            zzw.util.ReviveSeal.seal(u);
            if(killDirect(u)) return;

            // ★ 第三层: 反射拆解 (只有打不死的单位才会走到这里)
            annihilateStubbornUnit(u);
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

                // ★ 兜底 —— 标记 dead 并直接从实体组摘除。
                //   即使单位重写了 remove()/destroy() 并拒绝死亡, 这里也能保证它消失。
                try{
                    u.dead = true;
                }catch(Throwable ignored){
                }
                hardRemove(u);
            }catch(Throwable ignored){
            }finally{
                restoreExternalRevive(reviveRegistry, reviveWasOn);
                restoreFlameRespawn(u, respawnWasOn);
            }
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