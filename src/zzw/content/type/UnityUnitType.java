package zzw.content.type;

import arc.Core;
import arc.func.Func;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.*;
import arc.util.*;
import mindustry.ctype.*;
import mindustry.entities.Leg;
import mindustry.entities.abilities.*;
import mindustry.entities.units.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.blocks.environment.*;
import zzw.content.units.AbilityTextures;
import zzw.content.units.entities.DecorationUnitEntity;
import zzw.content.units.type.decal.UnitDecorationType;
import zzw.content.units.type.decal.UnitDecorationType.UnitDecoration;
import zzw.content.units.types.Engine;

import static arc.Core.*;
import static mindustry.Vars.content;

/**
 * UnityUnitType (PU132 unity.type.UnityUnitType 移植)
 *
 * 这是简化版，仅保留 buffer/omega/cache 等基本单位所需功能:
 * - weaponXs: 武器初始 x 坐标列表, 供 ShootArmorAbility 偏移镜像武器
 * - sortSegWeapons: 段身武器镜像复制排序
 * - bottomWeapons: 底层武器列表
 *
 * 移除的高级功能 (依赖链过深, buffer/omega/cache 不需要):
 * - Worm (虫子单位): Wormc/WormDefaultUnit/WormDecal/segmentWeapons 等
 * - Copter (直升机): Copterc/Rotor/drawRotors 等
 * - Tentacle (触手): Tentaclec/TentacleType 等
 * - Decoration (装饰): Decorationc/UnitDecorationType 等
 * - CLeg/TriJointLegs: CLegc/CLegGroupType/TriJointLegsc 等
 * - Monolith/World/End/Imber: 自定义实体接口
 * - AbilityTextures: Ability 贴图枚举
 */
@SuppressWarnings("unchecked")
public class UnityUnitType extends UnitType{
    public final Seq<Weapon> segWeapSeq = new Seq<>();
    public Seq<Weapon> bottomWeapons = new Seq<>();

    // For shoot armor ability
    public FloatSeq weaponXs = new FloatSeq();
    
    // For decorations system
    public Seq<UnitDecorationType> decorations = new Seq<>();

    // For ability system (PU132)
    /** 能力贴图数组 */
    public TextureRegion[] abilityRegions = new TextureRegion[AbilityTextures.values().length];
    
    /**
     * 自定义后腿贴图 (旧字段, 现已改名 {@link #backLegsRegion} 以腾出
     * {@link #customBackLegs} 承载 PU132 的 boolean 语义)。
     */
    public TextureRegion backLegsRegion;

    // ===== PU132 后腿独立贴图翻转渲染 (customBackLegs) =====
    /**
     * ★ PU132 unity.type.UnityUnitType.customBackLegs (boolean 语义):
     * true 时 drawLegs 用 leg-back/leg-base-back/foot-back 三张独立贴图
     * 渲染后腿并翻转 (前腿在前, 后腿在后), 实现"双层腿"遮挡效果
     * (excelsus 等 6 腿悬浮单位使用)。
     */
    public boolean customBackLegs = false;
    /** 后腿脚部贴图: atlas[name + "-foot-back"] */
    public TextureRegion footBackRegion;
    /** 后腿小腿贴图: atlas[name + "-leg-back"] */
    public TextureRegion legBackRegion;
    /** 后腿大腿贴图: atlas[name + "-leg-base-back"] */
    public TextureRegion legBaseBackRegion;

    /** 对象化引擎 (PU132): 非 null 时 drawEngine 走 Engine.draw 而非原版标量字段。 */
    public Engine engine;
    /** 拖尾工厂 (PU132 trailType): 替代原版 new Trail(trailLength), Monolith 用 TexturedTrail/MultiTrail。 */
    public Func<Unit, Trail> trailType = unit -> new Trail(trailLength);

    /** 调试用: 装饰绘制日志计数 (只打印前 3 次, 避免刷屏) */
    private int decorDrawLogs = 0;

    /**
     * 最大容纳灵魂数 (PU132 UnitType.maxSouls)。
     *
     * <p>已生效: 灵魂承载实体 (SoulMechUnit / SoulLegsUnit / SoulDecorationUnit)
     * 在 {@code setType} 时读取此值作为灵魂容量; 单位死亡时经
     * {@code Soul.spreadSouls()} 拆解为灵魂单位。0 = 该单位不承载灵魂。</p>
     */
    public int maxSouls = 0;

    /**
     * End 阵营防作弊参数集 (PU132 UnitType.antiCheatType)。
     *
     * <p>由 EndGroundUnit / EndInvisibleUnit 等防作弊实体在 add() 时读取;
     * null = 无防作弊 (普通单位)。各 End 单位参数见 PU132 UnityUnitTypes。</p>
     */
    public zzw.content.units.anticheat.EndCheatVars antiCheatType;

    public UnityUnitType(String name){
        super(name);
        outlines = false;
        // v155.4 适配: PU132 通过注解处理器自动设置, 简化版需手动指定默认构造器
        constructor = mindustry.gen.UnitEntity::create;
    }

    /**
     * 单位出厂钩子 (PU132 unity.type.UnityUnitType.create 完整移植)。
     *
     * <p>PU132 原版: {@code Monolithc} 灵魂单位通过 {@code create(Team)} 出厂时
     * 自带 1 只灵魂 ({@code e.join()}) —— 否则单位出生时 0 灵魂,
     * 会被 MonolithComp/SoulLegsUnit 立即施加 disabled 瘫痪状态
     * (移速归零 + 缴械), 表现为"不能动也不能攻击"。</p>
     *
     * <p>★ 与 PU132 差异: PU132 用 {@code Monolithc} 实体接口判断,
     * 本模组改用 {@link zzw.content.units.soul.Soul} 接口 (SoulLegsUnit/
     * SoulMechUnit/SoulDecorationUnit 均实现它)。</p>
     *
     * <p>核心直接召唤的单位 (spawnedByCore = true) 无需处理:
     * {@code join()} 受 {@code canJoin()} 保护, 而核心单位 maxSouls = 0,
     * 灵魂不会被接纳; 后续 add() 钩子也会再清零一次。</p>
     */
    @Override
    public Unit create(Team team){
        Unit unit = super.create(team);
        // 步骤 1: 灵魂单位出厂自带 1 只灵魂 (PU132 原版注释掉了随机数量方案,
        //   只保留 e.join() 单灵魂注入; canJoin() 保证容量已满/为 0 时无效)
        if(unit instanceof zzw.content.units.soul.Soul s){
            s.join();
        }
        // 步骤 2: 返回创建的单位 (工厂/波次生成等调用方继续走各自流程)
        return unit;
    }

    /**
     * 加载阶段: 加载装饰贴图 (PU132 原版 load() 的 decorations.each 段)。
     *
     * <p>★ 调试: 打印装饰数量, 方便确认装饰系统已接入。</p>
     */
    @Override
    public void load(){
        super.load();
        
        // 加载能力贴图 (PU132)
        for(AbilityTextures type : AbilityTextures.values()){
            String regionName = name + "-" + type.name();
            abilityRegions[type.ordinal()] = Core.atlas.find(regionName, (arc.graphics.g2d.TextureRegion)null);
        }
        
        // 加载自定义后腿贴图 (旧 backlegs 单张贴图, 保留兼容)
        if(backLegsRegion == null){
            backLegsRegion = Core.atlas.find(name + "-backlegs", (arc.graphics.g2d.TextureRegion)null);
        }

        // ★ PU132: 加载后腿三件套贴图 (customBackLegs 渲染用),
        //   命名约定与原版一致: excelsus-leg-back / excelsus-leg-base-back / excelsus-foot-back
        legBackRegion = Core.atlas.find(name + "-leg-back");
        legBaseBackRegion = Core.atlas.find(name + "-leg-base-back");
        footBackRegion = Core.atlas.find(name + "-foot-back");
        
        if(decorations.size > 0){
            boolean decorEntity = constructor != null && constructor.get() instanceof DecorationUnitEntity;
            Log.info("[deco-debug] 单位 @ 开始加载装饰, 数量: @ (实体构造器: @)",
                name, decorations.size,
                decorEntity ? "DecorationUnitEntity OK" : "非装饰实体 (装饰不会工作!)");
        }
        decorations.each(UnitDecorationType::load);
    }

    /**
     * 绘制身体下方的装饰 (PU132 原版 drawOutline: top=false 的装饰, 如翅膀)。
     *
     * <p>★ 调试: 前 3 次绘制打印日志, 确认绘制链路接通。</p>
     */
    @Override
    public void drawOutline(Unit unit){
        if(unit instanceof DecorationUnitEntity d){
            for(UnitDecoration decor : d.decors){
                if(!decor.type.top) decor.type.draw(unit, decor);
            }
            if(decorDrawLogs < 3){
                decorDrawLogs++;
                Log.info("[deco-debug] @ drawOutline 绘制下方装饰, decors: @", name, d.decors.length);
            }
        }
        super.drawOutline(unit);
    }

    /**
     * 绘制身体上方的装饰 (PU132 原版 drawBody: top=true 的装饰)。
     */
    @Override
    public void drawBody(Unit unit){
        super.drawBody(unit);
        if(unit instanceof DecorationUnitEntity d){
            for(UnitDecoration decor : d.decors){
                if(decor.type.top) decor.type.draw(unit, decor);
            }
        }
    }

    /**
     * 腿部绘制 (PU132 unity.type.UnityUnitType.drawLegs 完整移植)。
     *
     * <p>customBackLegs = false: 走原版渲染。</p>
     *
     * <p>customBackLegs = true: 后腿改用独立贴图三件套
     * (foot-back/leg-back/leg-base-back) 渲染并放在前腿底层,
     * 形成"前后双层腿"遮挡效果 —— 前腿用常规贴图且后绘制 (盖住后腿),
     * 后腿用 back 系列贴图且先绘制。贴图通过 load() 的
     * {@code name + "-foot-back" / "-leg-back" / "-leg-base-back"} 命名约定加载
     * (与 PU132 原版完全一致, excelsus 贴图已就位)。</p>
     *
     * <p>★ v160.1 差异: 原版 visualElevation 字段已移除,
     * 移动中腿的"抬升投影" (shadowTX/shadowTY 偏移阴影) 省略,
     * 仅保留 Drawf.shadow 落地阴影, 视觉差异极小。</p>
     */
    @Override
    public <T extends Unit & Legsc> void drawLegs(T unit){
        // 未启用后腿翻转: 直接走原版渲染
        if(!customBackLegs){
            super.drawLegs(unit);
            return;
        }

        applyColor(unit);

        Leg[] legs = unit.legs();
        // 脚部阴影尺寸 (PU132: footRegion 宽度 × 1.5)
        float ssize = footRegion.width * Draw.scl * 1.5f;
        float rotation = unit.baseRotation();

        // 先画所有腿的落地阴影
        for(Leg leg : legs){
            Drawf.shadow(leg.base.x, leg.base.y, ssize);
        }

        // 腿按 j 从后往前绘制: j 大 (前腿) 后画 → 盖住后腿, 形成前后遮挡
        for(int j = legs.length - 1; j >= 0; j--){
            // 交错还原真实腿索引 (与原版 drawLegs 一致)
            int i = (j % 2 == 0 ? j / 2 : legs.length - 1 - j / 2);
            Leg leg = legs[i];
            // ★ v160.1 差异: legAngle 只需腿索引 (旋转已含 baseRotation)
            float angle = unit.legAngle(i);
            // 腿索引过半 → 该腿需要左右翻转
            boolean flip = i >= legs.length / 2f;
            // 最后绘制的两条腿 (j = length-1, length-2) 是前腿, 其余为后腿
            boolean back = j < legs.length - 2;
            int flips = Mathf.sign(flip);

            // 后腿用 back 三件套, 前腿用常规贴图
            TextureRegion fr = back ? footRegion : footBackRegion;
            TextureRegion lr = back ? legRegion : legBackRegion;
            TextureRegion lbr = back ? legBaseRegion : legBaseBackRegion;

            // 腿根位置: 从单位中心沿腿角度偏移 legBaseOffset
            Vec2 position = new Vec2().trns(angle, legBaseOffset).add(unit.x, unit.y);
            // 大腿连接处向关节方向的延伸偏移 (PU132 legExtension 负值 → 贴图反向)
            Vec2 ext = new Vec2().set(leg.base).sub(leg.joint).inv().setLength(legExtension);

            // 脚部贴图 (朝向腿根 → 脚底方向)
            Draw.rect(fr, leg.base.x, leg.base.y, position.angleTo(leg.base));

            // 小腿: 腿根 → 膝盖关节
            Lines.stroke(lr.height * Draw.scl * flips);
            Lines.line(lr, position.x, position.y, leg.joint.x, leg.joint.y, false);

            // 大腿: 膝盖关节 → 脚底 (经延伸偏移修正)
            Lines.stroke(lbr.height * Draw.scl * flips);
            Lines.line(lbr, leg.joint.x + ext.x, leg.joint.y + ext.y, leg.base.x, leg.base.y, false);

            // 膝盖关节贴图
            if(jointRegion.found()){
                Draw.rect(jointRegion, leg.joint.x, leg.joint.y);
            }
            // 腿根贴图 (随身体旋转)
            if(baseJointRegion.found()){
                Draw.rect(baseJointRegion, position.x, position.y, rotation);
            }
        }

        // 身体底座
        if(baseRegion.found()){
            Draw.rect(baseRegion, unit.x, unit.y, rotation - 90);
        }

        Draw.reset();
    }

    @Override
    public void init(){
        super.init();

        weapons.each(w -> weaponXs.add(w.x));

        //worm segment weapons sorting (保留用于段身单位)
        sortSegWeapons(segWeapSeq);

        Seq<Weapon> addBottoms = new Seq<>();
        for(Weapon w : weapons){
            if(bottomWeapons.contains(w) && w.otherSide != -1){
                addBottoms.add(weapons.get(w.otherSide));
            }
        }

        bottomWeapons.addAll(addBottoms.distinct());
    }

    public void sortSegWeapons(Seq<Weapon> weaponSeq){
        Seq<Weapon> mapped = new Seq<>();
        for(int i = 0, len = weaponSeq.size; i < len; i++){
            Weapon w = weaponSeq.get(i);
            if(w.recoilTime < 0f){
                w.recoilTime = w.reload;
            }
            mapped.add(w);

            if(w.mirror){
                Weapon copy = w.copy();
                copy.x *= -1;
                copy.shootX *= -1;
                copy.flipSprite = !copy.flipSprite;
                mapped.add(copy);

                w.reload *= 2;
                copy.reload *= 2;
                w.recoilTime *= 2;
                copy.recoilTime *= 2;
                w.otherSide = mapped.size - 1;
                copy.otherSide = mapped.size - 2;
            }
        }

        weaponSeq.set(mapped);
    }

    @Override
    public void drawWeapons(Unit unit){
        float z = Draw.z();

        applyColor(unit);
        for(WeaponMount mount : unit.mounts){
            Weapon weapon = mount.weapon;
            if(bottomWeapons.contains(weapon)) Draw.z(z - 0.0001f);

            weapon.draw(unit, mount);
            Draw.z(z);
        }

        Draw.reset();
    }

    /**
     * 引擎绘制 (PU132 UnityUnitType.drawEngine 移植, v158 签名为 drawEngines)。
     *
     * <p>engine 字段非 null 时走对象化引擎 (支持多引擎/自定义颜色),
     * 否则回退到原版 engines/标量字段渲染。</p>
     */
    @Override
    public void drawEngines(Unit unit){
        if(!unit.isFlying()) return;
        if(engine != null){
            engine.draw(unit);
        }else{
            super.drawEngines(unit);
        }
    }

    /**
     * 拖尾绘制 (PU132 行为补全: trailType 工厂接入)。
     *
     * <p>原版 v158 的 drawTrail 只会创建普通 Trail 并用队伍色绘制,
     * PU132 的 trailType 工厂 (MultiTrail/TexturedTrail, 幻影/灵魂蓝色拖尾) 永远不会被调用。
     * 这里在拖尾为空时优先用 trailType 工厂创建, 再走原版绘制路径
     * (MultiTrail.draw 内部每个 TrailHold 优先用自己的颜色, 覆盖队伍色)。</p>
     *
     * <p>★ 关键: TexturedTrail 的丝带本体色取自 {@code Draw.getColor()} (渲染管线状态色),
     * 传入的 color 参数只作用于 mix 混色层 —— 而 soul 丝带的 mixAlpha=0, 混色层关闭,
     * 若不提前钉住管线色, 丝带会继承渲染序列中上一个绘制调用的残留色 (表现为黄色)。
     * 因此绘制前先把管线色钉住为引擎色 (Monolith 蓝), 与 PU132 的 CTrail 渲染行为对齐。</p>
     */
    @Override
    public void drawTrail(Unit unit){
        if(trailLength > 0 && unit.trail == null){
            unit.trail = trailType.get(unit);
        }
        // 钉住管线色: engineColor (单位 apply 写回的 Monolith 蓝), 为空时退回队伍色
        Draw.color(engineColor != null ? engineColor : unit.team.color);
        super.drawTrail(unit);
        Draw.reset();
    }

    /**
     * 数值面板 (覆盖): 可承载灵魂的单位额外显示灵魂容量与"无魂瘫痪"提示
     * (PU132 MonolithComp 阵营机制 —— 巨石单位必须供魂)。
     */
    @Override
    public void setStats(){
        super.setStats();

        if(maxSouls > 0){
            stats.add(mindustry.world.meta.Stat.abilities, t -> t.table(bt -> {
                bt.left().defaults().padRight(3).left();
                bt.add(Core.bundle.format("soul.capacity", maxSouls));
                bt.row();
                bt.add(Core.bundle.get("soul.disabled"));
            }));
        }
    }
}
