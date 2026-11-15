package zzw.content.blocks.modular;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.OrderedMap;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.ArtilleryBulletType;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Sounds;
import mindustry.gen.Tex;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.Stats;
import mindustry.world.modules.ItemModule;
import zzw.content.Z_Items;
import zzw.content.mechanics.torque.blocks.GraphBlockBase;
import zzw.content.mechanics.torque.graph.TorqueGraph;
import zzw.content.mechanics.torque.graphs.GraphTorque;
import zzw.content.mechanics.torque.graphs.Graphs;
import zzw.content.mechanics.torque.modules.GraphModules;
import zzw.util.UnityUtils;

import static mindustry.Vars.*;

/**
 * 模块化炮台 (PU132 younggamExperimental.blocks.ModularTurret 移植)。
 *
 * <p>玩家在蓝图编辑器里用零件拼出一门炮, 方块按蓝图自动从核心取料建造;
 * 建造完成后按零件组合决定口径、弹匣、射速与射程, 并从货物中接收弹药开火。</p>
 *
 * <p>本类只负责方块本体与运行时行为, 零件表由 {@link #addDefaultParts()} 统一注册,
 * 贴图直接使用 {@code part-*.png} 图标, 分类图标使用 {@code partscategory1..5}。</p>
 */
public class ModularTurret extends Turret implements GraphBlockBase{
    /** 造价配比编码: 高 16 位为已付数量, 低 16 位为需求数量 */
    static final int mask = 65535;
    /** 扭矩网络: 接入扭矩系统后由网络转速提升装填速度 */
    protected final Graphs graphs = new Graphs();
    /** 当前方块的全部零件 (顺序即蓝图配置中的 id - 1) */
    public final Seq<PartInfo> parts = new Seq<>();
    /** 零件分类, 顺序对应 partscategory1..5 */
    final PartType[] categories = new PartType[]{PartType.base, PartType.breach, PartType.ammo, PartType.payload, PartType.misc};
    /** 可旋转的炮塔头部 (含炮管) */
    TextureRegion headRegion;
    /** 从头部贴图裁出的炮管段与炮口块, 用于按炮闩数量延长炮管 */
    TextureRegion barrelStrip, muzzleBlock;
    /** 外挂设备 (装在车体下方、只露出边缘) */
    TextureRegion iconAmmo, iconArmor, iconRad, iconRadar;
    TextureRegion partsRegion, rootRegion, rootOutlineRegion;
    /** peekAmmo 兜底弹种: 未装完蓝图时避免炮塔更新流程取到 null */
    static final BulletType dummyBullet = new BasicBulletType(1f, 0f);
    float partCostAccum = 0.2f, autoBuildDelay = 10f;
    int gridW = 1, gridH = 1;

    public ModularTurret(String name){
        super(name);
        rotate = solid = configurable = acceptsItems = hasItems = true;
        targetAir = targetGround = true;
        inaccuracy = 4f;
        shootCone = 35f;
        rotateSpeed = 5f;
        heatColor = Pal.turretHeat;
        shootSound = Sounds.shootDuo;
        buildType = ModularTurretBuild::new;

        config(String.class, (ModularTurretBuild build, String value) -> build.changed = build.setBluePrintFromString(value));
        config(IntSeq.class, (ModularTurretBuild build, IntSeq value) -> build.changed = build.setBluePrint(UnityUtils.unpackInts(value)));
        configClear((ModularTurretBuild build) -> build.setBluePrint(null));

        // 扭矩网络在 init() 里注册: 端口数量依赖 size, 而 size 由子类构造块设置
    }

    @Override
    public Graphs graphs(){
        return graphs;
    }

    protected void setGridW(int s){
        gridW = Mathf.clamp(s, 1, 16);
    }

    protected void setGridH(int s){
        gridH = Mathf.clamp(s, 1, 16);
    }

    public PartType[] getPartsCategories(){
        return categories;
    }

    public PartInfo[] getPartsConfig(){
        return parts.toArray(PartInfo.class);
    }

    protected void addPart(String icon, String name, String desc, PartType category, int tw, int th, boolean cannotPlace, boolean isRoot, Point2 prePlace, ItemStack[] cost, byte[] connectOut, byte[] connectIn, PartStat... stats){
        PartInfo info = new PartInfo(name, desc, category, 0, 0, tw, th, cannotPlace, isRoot, prePlace, cost, connectOut, connectIn, stats);
        info.icon = icon;
        parts.add(info);
    }

    /**
     * 注册默认零件表 (13 个零件, 覆盖 part-*.png 全部图标)。
     *
     * <p>除底座外全部零件四向互通 (端口 id 都为 1), 玩家可以自由拼接;
     * 根零件为火炮底座, 打开蓝图面板时会预置在网格 (1,1)。</p>
     */
    protected void addDefaultParts(){
        byte[] all1x1 = {1, 1, 1, 1};
        byte[] all3x1 = {1, 1, 1, 1, 1, 1, 1, 1};
        byte[] all2x1 = {1, 1, 1, 1, 1, 1};
        byte[] none = {0, 0, 0, 0};

        // ── 底座 ──
        // 根零件预置在蓝图网格正中心 (6x6 → (3,3), 8x8 → (4,4))
        Point2 center = new Point2(gridW / 2, gridH / 2);
        addPart("gbase", Core.bundle.get("part.unity.gbase.name"), Core.bundle.get("part.unity.gbase.info"), PartType.base,
            1, 1, true, true, center, ItemStack.with(Z_Items.nickel, 10, Items.titanium, 5), all1x1, none,
            new PartStat(PartStatType.hp, 40), new PartStat(PartStatType.support, "1x"), new PartStat(PartStatType.reload, 1f),
            new PartStat(PartStatType.mass, 3));
        addPart("rgbase", Core.bundle.get("part.unity.rgbase.name"), Core.bundle.get("part.unity.rgbase.info"), PartType.base,
            3, 1, false, false, null, ItemStack.with(Z_Items.nickel, 15, Items.titanium, 20, Items.graphite, 10), all3x1, all3x1,
            new PartStat(PartStatType.hp, 80), new PartStat(PartStatType.support, "3x"), new PartStat(PartStatType.reload, 1.6f),
            new PartStat(PartStatType.mass, 15), new PartStat(PartStatType.shaftSpd, 25));
        addPart("small-adapter", Core.bundle.get("part.unity.small-adapter.name"), Core.bundle.get("part.unity.small-adapter.info"), PartType.base,
            2, 1, false, false, null, ItemStack.with(Z_Items.nickel, 6, Items.titanium, 4), all2x1, all2x1,
            new PartStat(PartStatType.hp, 25), new PartStat(PartStatType.support, "2x"));

        // ── 炮闩 ──
        addPart("gbreach", Core.bundle.get("part.unity.gbreach.name"), Core.bundle.get("part.unity.gbreach.info"), PartType.breach,
            1, 1, false, false, null, ItemStack.with(Z_Items.nickel, 4), all1x1, all1x1,
            new PartStat(PartStatType.hp, 25), new PartStat(PartStatType.bulletType, "normal"), new PartStat(PartStatType.baseDmg, 14f),
            new PartStat(PartStatType.baseSpeed, 5f), new PartStat(PartStatType.ammoType, "normal"), new PartStat(PartStatType.payload, 1),
            new PartStat(PartStatType.magazine, 3), new PartStat(PartStatType.shots, 1), new PartStat(PartStatType.reloadMultiplier, 1f),
            new PartStat(PartStatType.spread, 8f), new PartStat(PartStatType.lifetime, 30f), new PartStat(PartStatType.heat, 120f));
        addPart("grbreach", Core.bundle.get("part.unity.grbreach.name"), Core.bundle.get("part.unity.grbreach.info"), PartType.breach,
            1, 1, false, false, null, ItemStack.with(Z_Items.nickel, 3, Items.graphite, 5), all1x1, all1x1,
            new PartStat(PartStatType.hp, 25), new PartStat(PartStatType.bulletType, "grenade"), new PartStat(PartStatType.baseDmg, 8f),
            new PartStat(PartStatType.baseSpeed, 4f), new PartStat(PartStatType.ammoType, "explosive"), new PartStat(PartStatType.payload, 1),
            new PartStat(PartStatType.magazine, 2), new PartStat(PartStatType.shots, 1), new PartStat(PartStatType.reloadMultiplier, 3.2f),
            new PartStat(PartStatType.spread, 14f), new PartStat(PartStatType.lifetime, 120f), new PartStat(PartStatType.mod, "Explosive"));
        addPart("cbreach", Core.bundle.get("part.unity.cbreach.name"), Core.bundle.get("part.unity.cbreach.info"), PartType.breach,
            1, 1, false, false, null, ItemStack.with(Items.silicon, 6, Items.titanium, 5), all1x1, all1x1,
            new PartStat(PartStatType.hp, 30), new PartStat(PartStatType.bulletType, "normal"), new PartStat(PartStatType.baseDmg, 20f),
            new PartStat(PartStatType.baseSpeed, 6f), new PartStat(PartStatType.ammoType, "normal"), new PartStat(PartStatType.payload, 1),
            new PartStat(PartStatType.magazine, 4), new PartStat(PartStatType.shots, 1), new PartStat(PartStatType.reloadMultiplier, 1.2f),
            new PartStat(PartStatType.spread, 5f), new PartStat(PartStatType.lifetime, 40f), new PartStat(PartStatType.heat, 180f));
        addPart("clbreach", Core.bundle.get("part.unity.clbreach.name"), Core.bundle.get("part.unity.clbreach.info"), PartType.breach,
            1, 2, false, false, null, ItemStack.with(Items.silicon, 8, Items.titanium, 8), all2x1, all2x1,
            new PartStat(PartStatType.hp, 35), new PartStat(PartStatType.bulletType, "normal"), new PartStat(PartStatType.baseDmg, 9f),
            new PartStat(PartStatType.baseSpeed, 5.5f), new PartStat(PartStatType.ammoType, "normal"), new PartStat(PartStatType.payload, 1),
            new PartStat(PartStatType.magazine, 6), new PartStat(PartStatType.shots, 2), new PartStat(PartStatType.reloadMultiplier, 0.8f),
            new PartStat(PartStatType.spread, 12f), new PartStat(PartStatType.lifetime, 32f), new PartStat(PartStatType.heat, 90f));

        // ── 弹药 / 载荷 / 辅助 ──
        addPart("apacker", Core.bundle.get("part.unity.apacker.name"), Core.bundle.get("part.unity.apacker.info"), PartType.ammo,
            1, 1, false, false, null, ItemStack.with(Z_Items.nickel, 5, Items.silicon, 4), all1x1, all1x1,
            new PartStat(PartStatType.hp, 20), new PartStat(PartStatType.magazine, 8));
        addPart("firemod", Core.bundle.get("part.unity.firemod.name"), Core.bundle.get("part.unity.firemod.info"), PartType.payload,
            1, 1, false, false, null, ItemStack.with(Items.silicon, 8, Items.graphite, 4), all1x1, all1x1,
            new PartStat(PartStatType.hp, 15), new PartStat(PartStatType.mod, "incendiary"));
        addPart("homingmod", Core.bundle.get("part.unity.homingmod.name"), Core.bundle.get("part.unity.homingmod.info"), PartType.payload,
            1, 1, false, false, null, ItemStack.with(Items.silicon, 10, Items.titanium, 5), all1x1, all1x1,
            new PartStat(PartStatType.hp, 15), new PartStat(PartStatType.mod, "homing"));
        addPart("aplate", Core.bundle.get("part.unity.aplate.name"), Core.bundle.get("part.unity.aplate.info"), PartType.misc,
            1, 1, false, false, null, ItemStack.with(Items.titanium, 8), all1x1, all1x1,
            new PartStat(PartStatType.hp, 60));
        addPart("rad", Core.bundle.get("part.unity.rad.name"), Core.bundle.get("part.unity.rad.info"), PartType.misc,
            1, 1, false, false, null, ItemStack.with(Items.copper, 10, Items.silicon, 6), all1x1, all1x1,
            new PartStat(PartStatType.hp, 20), new PartStat(PartStatType.radiate, 5f));
        addPart("rfinder", Core.bundle.get("part.unity.rfinder.name"), Core.bundle.get("part.unity.rfinder.info"), PartType.misc,
            1, 1, false, false, null, ItemStack.with(Items.silicon, 8), all1x1, all1x1,
            new PartStat(PartStatType.hp, 20), new PartStat(PartStatType.rangeinc, 12));
    }

    @Override
    public void load(){
        super.load();
        partsRegion = Core.atlas.find("create-partsicons");
        rootRegion = Core.atlas.find(name + "-root");
        rootOutlineRegion = Core.atlas.find(name + "-root-outline");
        headRegion = Core.atlas.find(name + "-head");
        if(headRegion.found()){
            // 从头部贴图直接裁出炮管中段与炮口块 (不新增美术资源)
            int w = headRegion.width, h = headRegion.height;
            barrelStrip = new TextureRegion(headRegion, (int)(w * 0.52f), (int)(h * 0.40f), (int)(w * 0.42f), (int)(h * 0.20f));
            muzzleBlock = new TextureRegion(headRegion, (int)(w * 0.80f), (int)(h * 0.34f), (int)(w * 0.18f), (int)(h * 0.32f));
        }
        iconAmmo = Core.atlas.find("create-part-apacker");
        iconArmor = Core.atlas.find("create-part-aplate");
        iconRad = Core.atlas.find("create-part-rad");
        iconRadar = Core.atlas.find("create-part-rfinder");
        for(PartInfo part : parts){
            TextureRegion region = Core.atlas.find("create-part-" + part.icon);
            part.sprite = region;
            part.texRegion = region;
        }
        for(int i = 0; i < categories.length; i++) categories[i].region = Core.atlas.find("create-partscategory" + (i + 1));
    }

    @Override
    public void init(){
        super.init();
        PartInfo[] config = getPartsConfig();
        for(int i = 0; i < config.length; i++) config[i].id = i;
        PartInfo.preCalcConnection(config);

        // 接入扭矩网络: 只有本地 -x 面 (炮管背面, side = 2) 能接扭矩。
        // accept[i] 是网络槽位号, 0 表示该端口不参与传动网络。
        // ★ accept 必须在注册时就填好 —— GraphModule.onCreate() 会直接读 graph.accept.length,
        //   留空会让炮塔在建造完成/读档/复制时抛 NPE。
        int[] ports = new int[size * 4];
        int backSide = 2; // side 2 = Geometry.d4(2) = (-1, 0)
        for(int i = backSide * size; i < (backSide + 1) * size; i++) ports[i] = 1;
        addGraph(new GraphTorque(0.1f, 10f).setAccept(ports));
    }

    @Override
    public void setStats(){
        super.setStats();
        // 扭矩网络信息 + 零件属性面板
        graphs.setStats(stats);
        setStatsExt(stats);
    }

    /** 属性面板: 按分类双列列出零件的关键数值 (紧凑排布, 保证能收进一屏)。 */
    public void setStatsExt(Stats stats){
        stats.add(Stat.ammo, table -> table.table(t -> {
            PartInfo.addStatsTable(t, getPartsCategories(), parts);
            t.row();
        }).left());
    }

    public class ModularTurretBuild extends TurretBuild implements GraphBlockBase.GraphBuildBase{
        /** 扭矩网络模块 (接入传动系统后由网络提供装填加成) */
        protected GraphModules gms;
        /** 绘制时用于去重多格零件的临时集合 */
        final IntSet drawVisited = new IntSet();
        final OrderedMap<Item, Integer> blueprintRemainingCost = new OrderedMap<>(12);
        final IntSeq bluePrint = new IntSeq();
        final StatContainer currentStats = new StatContainer();
        final Seq<Item> ammoItems = new Seq<>();
        float turretRange, baseMaxHp,
            aniProg, aniTime,
            reloadMult, reloadBonus, bulletDamage, bulletSpeed,
            spreadSum, spreadStat;
        int totalItemCountCost, totalItemCountPaid,
            ammoCapacity, barrels, magazine, shotsTotal, spreadCount;
        boolean changed, validTurret,
            hasBasePart, hasBreachPart,
            grenadeBreach, normalBreach,
            normalAmmo, explosiveAmmo,
            incendiary, homing;
        BulletType turretBullet;

        float getPaidRatio(){
            if(totalItemCountCost == 0) return 0f;
            return ((float)totalItemCountPaid) / totalItemCountCost;
        }

        boolean setBluePrintFromString(String s){
            return setBluePrint(UnityUtils.unpackIntsFromString(s));
        }

        boolean setBluePrint(IntSeq s){
            if(!bluePrint.equals(s)){
                bluePrint.clear();
                if(s != null) bluePrint.addAll(s);
                return true;
            }
            return false;
        }

        @Override
        public void display(Table table){
            super.display(table);
            if(gms != null) gms.display(table);
            table.row();
            table.table().left().update(sub -> {
                sub.clearChildren();
                if(totalItemCountPaid >= totalItemCountCost) return;
                sub.left();
                if(blueprintRemainingCost.isEmpty()) sub.labelWrap(Core.bundle.get("bar.blueprint", "No blueprint")).color(Color.lightGray);
                else{
                    for(var i : blueprintRemainingCost){
                        sub.image(i.key.uiIcon).size(iconMed);
                        sub.add((i.value >>> 16) + "/" + (i.value & mask));
                        sub.row();
                    }
                }
            });
        }

        void updateAutoBuild(){
            if(totalItemCountPaid >= totalItemCountCost) return;
            if(state.rules.infiniteResources || team.rules().infiniteResources || team.rules().cheat){
                for(var i : blueprintRemainingCost){
                    int temp = i.value & mask;
                    i.value <<= 16;
                    i.value += temp;
                }
                totalItemCountPaid = totalItemCountCost;
                applyStats();
                return;
            }
            if(timer(timerDump, autoBuildDelay)){
                CoreBuild core = team.core();
                if(core == null) return;
                ItemModule cItems = core.items;
                for(var i : blueprintRemainingCost){
                    if((i.value >>> 16) < (i.value & mask) && cItems.get(i.key) > 0){
                        cItems.remove(i.key, 1);
                        totalItemCountPaid++;
                        blueprintRemainingCost.put(i.key, i.value + (1 << 16));
                        if(totalItemCountPaid == totalItemCountCost) applyStats();
                        return;
                    }
                }
            }
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            if(totalItemCountPaid < totalItemCountCost){
                int value = blueprintRemainingCost.get(item, 0);
                return (value >>> 16) < (value & mask);
            }
            return ammoItems.contains(item) && items.get(item) < ammoCapacity;
        }

        @Override
        public void handleItem(Building source, Item item){
            if(totalItemCountPaid < totalItemCountCost){
                totalItemCountPaid++;
                int value = blueprintRemainingCost.get(item, 0);
                blueprintRemainingCost.put(item, value + (1 << 16));
                if(totalItemCountPaid == totalItemCountCost) applyStats();
                return;
            }
            if(ammoItems.contains(item)) items.add(item, 1);
        }

        void resetStats(){
            if(baseMaxHp > 0f) maxHealth = baseMaxHp;
            validTurret = false;
            turretRange = range;
            ammoCapacity = 0;
            barrels = 1;
            reloadMult = 0f;
            reloadBonus = 0f;
            bulletDamage = 15f;
            bulletSpeed = 5f;
            spreadSum = 0f;
            spreadStat = 6f;
            spreadCount = 0;
            magazine = 0;
            shotsTotal = 0;
            hasBasePart = hasBreachPart = false;
            grenadeBreach = normalBreach = false;
            normalAmmo = explosiveAmmo = false;
            incendiary = homing = false;
            ammoItems.clear();
            turretBullet = null;
            currentStats.clear();
        }

        /** 把一块零件的属性累加进当前蓝图统计。 */
        void accumStats(PartInfo part, int x, int y){
            for(var stat : part.stats){
                PartStatType type = stat.key;
                PartStat value = stat.value;
                switch(type){
                    case hp -> currentStats.hpinc += value.asInt();
                    case rangeinc -> currentStats.rangeInc += value.asInt();
                    case magazine -> magazine += value.asInt();
                    case shots -> shotsTotal += value.asInt();
                    case baseDmg -> bulletDamage = Math.max(bulletDamage, value.asFloat());
                    case baseSpeed -> bulletSpeed = Math.max(bulletSpeed, value.asFloat());
                    case reloadMultiplier -> {
                        float v = value.asFloat();
                        if(v > 0f) reloadMult = reloadMult <= 0f ? v : Math.min(reloadMult, v);
                    }
                    case spread -> {
                        spreadSum += value.asFloat();
                        spreadCount++;
                    }
                    case bulletType -> {
                        if("grenade".equalsIgnoreCase(value.asString())) grenadeBreach = true;
                        else normalBreach = true;
                    }
                    case ammoType -> {
                        if("explosive".equalsIgnoreCase(value.asString())) explosiveAmmo = true;
                        else normalAmmo = true;
                    }
                    case mod -> {
                        String mod = value.asString();
                        if("incendiary".equalsIgnoreCase(mod)) incendiary = true;
                        else if("homing".equalsIgnoreCase(mod)) homing = true;
                    }
                    default -> {}
                }
            }
            if(part.category == PartType.base) hasBasePart = true;
            if(part.category == PartType.breach) hasBreachPart = true;
        }

        /** 蓝图变化后重算全部运行时数值。 */
        void applyStats(){
            if(baseMaxHp <= 0f) baseMaxHp = block.health;
            float newMax = baseMaxHp + currentStats.hpinc;
            float diff = newMax - maxHealth;
            maxHealth = newMax;
            if(diff > 0f) health = Math.min(maxHealth, health + diff);
            else if(diff < 0f) health = Math.min(health, maxHealth);

            turretRange = range + currentStats.rangeInc;
            barrels = Math.max(1, shotsTotal);
            ammoCapacity = 15 + magazine * 4;
            spreadStat = spreadCount > 0 ? spreadSum / spreadCount : 6f;
            // 装填倍率取最快值换算成提速比例 (1.0 → 0, 0.8 → +25%)
            reloadBonus = reloadMult > 0f ? Mathf.clamp(1f / reloadMult - 1f, -0.5f, 1.5f) : 0f;

            ammoItems.clear();
            if(normalAmmo || !explosiveAmmo && hasBreachPart){
                ammoItems.add(Items.copper, Items.lead, Items.silicon, Items.metaglass);
            }
            if(explosiveAmmo){
                ammoItems.add(Items.graphite, Items.titanium, Items.thorium);
            }
            if(ammoItems.isEmpty() && hasBreachPart) ammoItems.add(Items.copper);

            turretBullet = buildBullet();
            validTurret = hasBasePart && hasBreachPart && totalItemCountCost > 0 && totalItemCountPaid >= totalItemCountCost;
        }

        BulletType buildBullet(){
            float speed = Math.max(2f, bulletSpeed);
            float damage = Math.max(1f, bulletDamage);
            float life = (turretRange / 8f + 2f) * 60f / speed;
            BulletType type;
            if(grenadeBreach && !normalBreach){
                ArtilleryBulletType art = new ArtilleryBulletType(speed, damage);
                art.width = 8f;
                art.height = 10f;
                art.hitSize = 6f;
                art.hitEffect = Fx.blastExplosion;
                art.splashDamage = damage * 0.6f;
                art.splashDamageRadius = 20f;
                art.collidesTiles = true;
                art.collidesAir = true;
                type = art;
            }else{
                BasicBulletType basic = new BasicBulletType(speed, damage);
                basic.width = 4f;
                basic.height = 8f;
                basic.hitSize = 4f;
                basic.shootEffect = Fx.shootSmall;
                basic.smokeEffect = Fx.shootSmallSmoke;
                basic.hitEffect = Fx.hitBulletSmall;
                basic.despawnEffect = Fx.none;
                type = basic;
            }
            type.lifetime = life;
            type.collidesTiles = true;
            type.collidesAir = true;
            if(incendiary) type.status = StatusEffects.burning;
            if(homing){
                type.homingPower = 0.05f;
                type.homingRange = turretRange * 0.75f;
            }

            // ★ 必须手动 load(): 这颗子弹是运行时 (改蓝图/自动建造完成) 才 new 出来的,
            //   不会经过内容加载阶段的 ContentLoader.load(), 贴图 region 会一直是 null;
            //   一旦开火, BasicBulletType.draw 里 backRegion.found() 就会 NPE (实体组绘制崩溃)。
            //   专用服务器 (headless) 没有图集, 跳过。
            if(!headless) type.load();

            return type;
        }

        @Override
        public void buildConfiguration(Table table){
            table.button(Tex.whiteui, Styles.clearNonei, 50f, () -> {
                BaseDialog dialog = new BaseDialog(Core.bundle.get("bar.blueprint.title", "Edit Blueprint"));
                dialog.setFillParent(false);
                ModularConstructorUI mtd = ModularConstructorUI.applyModularConstructorUI(dialog.cont, partsRegion, 8, 6,
                    getPartsConfig(), gridW, gridH, bluePrint, getPartsCategories(), partCostAccum
                );
                dialog.buttons.button("@ok", () -> {
                    configure(mtd.getPackedSave());
                    dialog.hide();
                }).size(130f, 60f);
                dialog.update(() -> {
                    if(!(tile.build instanceof ModularTurretBuild)) dialog.hide();
                });
                dialog.show();
            }).size(50f).get().getStyle().imageUp = Icon.pencil;
        }

        @Override
        public void configured(mindustry.gen.Unit builder, Object value){
            changed = false;
            super.configured(builder, value);
            if(!changed) return;
            resetStats();
            PartInfo[] config = getPartsConfig();
            float cstMult = 1f;
            int len = bluePrint.size;
            for(int p = 0; p < len; p++){
                int temp = bluePrint.get(p);
                if(temp != 0 && temp <= config.length){
                    PartInfo part = config[temp - 1];
                    cstMult += partCostAccum * part.tw * part.th;
                }
            }
            cstMult -= partCostAccum;
            totalItemCountCost = totalItemCountPaid = 0;
            blueprintRemainingCost.clear();
            for(int p = 0; p < len; p++){
                int temp = bluePrint.get(p);
                if(temp != 0 && temp <= config.length){
                    PartInfo part = config[temp - 1];
                    for(ItemStack cost : part.cost){
                        int cur = blueprintRemainingCost.get(cost.item, 0);
                        int increment = Mathf.floor(cost.amount * cstMult);
                        blueprintRemainingCost.put(cost.item, cur + increment);
                        totalItemCountCost += increment;
                    }
                    accumStats(part, p / gridH, p % gridH);
                }
            }
            applyStats();
        }

        @Override
        public String config(){
            if(bluePrint.isEmpty()) return "";
            return IntPacker.packArray(bluePrint).toStringPack();
        }

        @Override
        public void write(Writes write){
            super.write(write);
            if(gms != null) gms.write(write);
            // ★ 读写必须严格对称: 空蓝图也要写出 0 长度段, 否则 read 会多读两个字节导致后续数据错位
            IntSeq packed = bluePrint.isEmpty() ? new IntSeq() : IntPacker.packArray(bluePrint).packed;
            write.s(packed.size);
            for(int i = 0; i < packed.size; i++) write.i(packed.get(i));
            write.s(blueprintRemainingCost.size);
            for(var i : blueprintRemainingCost){
                write.s(i.key.id);
                write.s(i.value >>> 16);
            }
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(gms != null) gms.read(read, revision);
            short packedSize = read.s();
            IntSeq pack = new IntSeq();
            for(int i = 0; i < packedSize; i++) pack.add(read.i());
            configureAny(pack);
            short costSize = read.s();
            if(costSize > 0){
                for(int i = 0; i < costSize; i++){
                    Item item = content.item(read.s());
                    short paid = read.s();
                    int cur = blueprintRemainingCost.get(item, -1);
                    if(cur != -1){
                        blueprintRemainingCost.put(item, (paid << 16) + cur);
                        totalItemCountPaid += paid;
                    }
                }
            }
            if(totalItemCountPaid == totalItemCountCost) applyStats();
        }

        @Override
        public boolean hasAmmo(){
            if(!validTurret) return false;
            for(Item item : ammoItems){
                if(items.get(item) > 0) return true;
            }
            return false;
        }

        @Override
        public BulletType useAmmo(){
            if(turretBullet == null) return null;
            for(Item item : ammoItems){
                if(items.get(item) > 0){
                    items.remove(item, 1);
                    return turretBullet;
                }
            }
            // 没有实弹库存时不能凭空发射
            return null;
        }

        @Override
        public BulletType peekAmmo(){
            // 供 updateReload / ammoReloadMultiplier 调用, 任何时刻都不能为 null
            return turretBullet != null ? turretBullet : dummyBullet;
        }

        @Override
        public float getAmmoFraction(){
            if(!validTurret || ammoCapacity <= 0) return 0f;
            int total = 0;
            for(Item item : ammoItems) total += items.get(item);
            return Mathf.clamp(total / (float)ammoCapacity);
        }

        @Override
        protected void updateShooting(){
            if(!validTurret || !hasAmmo()) return;
            super.updateShooting();
        }

        @Override
        protected void shoot(BulletType ammo){
            // 自行覆写 shoot 时必须手动消耗弹药, 否则弹药无限
            BulletType type = useAmmo();
            if(type == null) return;
            int count = Math.max(1, barrels);
            for(int i = 0; i < count; i++){
                float angleOffset = (i - (count - 1) * 0.5f) * spreadStat + Mathf.range(inaccuracy);
                bullet(type, 0f, 0f, angleOffset, null);
            }
            shootSound.at(x, y, Mathf.random(0.9f, 1.1f));
        }

        @Override
        protected float baseReloadSpeed(){
            return validTurret ? super.baseReloadSpeed() * (1f + reloadBonus) * (1f + torqueBoost() * 0.75f) : 0f;
        }

        /**
         * 扭矩联动系数 (0~1): 由所在扭矩网络的转速决定。
         *
         * <p>网络里转速越高, 装填越快, 最高提升 75%。</p>
         */
        protected float torqueBoost(){
            if(gms == null || gms.torque() == null) return 0f;
            TorqueGraph<?> net = gms.torque().getNetwork();
            if(net == null) return 0f;
            // lastVelocity / 6 即网络转速 (r/s), 取 2 r/s 达到满增益
            return Mathf.clamp(Math.abs(net.lastVelocity) / 6f / 2f, 0f, 1f);
        }

        @Override
        public float range(){
            return turretRange > 0f ? turretRange : super.range();
        }

        @Override
        public float trackingRange(){
            return range() + 20f;
        }

        @Override
        public void updateTile(){
            updateAutoBuild();
            float prog = getPaidRatio();
            aniProg = Mathf.lerpDelta(aniProg, prog, 0.02f);
            aniTime += Time.delta;
            super.updateTile();
            if(gms != null){
                gms.updateTile();
                gms.prevTileRotation(((Building)this).rotation);
            }
        }

        @Override
        public void created(){
            super.created();
            gms = new GraphModules(this);
            graphs.injectGraphConnector(gms);
            gms.created();
        }

        @Override
        public GraphModules gms(){
            return gms;
        }

        @Override
        public void onProximityUpdate(){
            super.onProximityUpdate();
            if(gms != null) gms.onProximityUpdate();
        }

        @Override
        public void onRemoved(){
            if(gms != null) gms.updateGraphRemovals();
            super.onRemoved();
        }

        @Override
        public void draw(){
            // ★ 底座: 只用固定贴图, 不取任何朝向变体 —— 无论瞄准角、放置朝向如何都绝不旋转
            Draw.rect(region, x, y);
            drawTeamTop();

            // 回转环 (固定, 不随瞄准转动), 让底座与旋转体有层次
            Draw.color(Color.black, 0.35f);
            Lines.stroke(1.4f);
            Lines.circle(x, y, size * tilesize * 0.23f);
            Draw.color(Pal.accent, 0.25f);
            Lines.stroke(1f);
            Lines.circle(x, y, size * tilesize * 0.23f + 1.4f);
            Draw.color();

            Draw.z(Layer.turret);

            // 旋转层: 整体绕方块中心按瞄准角旋转, 并带后坐力位移
            float rot = rotation - 90f;
            float recoil = -curRecoil * 2.5f;
            float ox = Mathf.cosDeg(rotation) * recoil;
            float oy = Mathf.sinDeg(rotation) * recoil;
            float cx = x + ox, cy = y + oy;
            boolean built = validTurret && headRegion.found();
            Draw.alpha(built ? 1f : 0.25f + 0.5f * aniProg);

            // ===== 按蓝图合成外观: 统计实际装上的零件 =====
            int baseCount = 0, breaches = 0, heavies = 0, ammoBoxes = 0, armors = 0, rads = 0, radars = 0;
            boolean rotary = false, twin = false, fireMod = false, homingMod = false;
            drawVisited.clear();
            for(int i = 0; i < bluePrint.size; i++){
                if(drawVisited.contains(i)) continue;
                int value = bluePrint.get(i);
                if(value <= 0 || value > parts.size) continue;
                PartInfo p = parts.get(value - 1);
                // 多格零件的其它格一并标记, 避免重复统计
                int gx = i / gridH, gy = i % gridH;
                for(int dx = 0; dx < p.tw; dx++){
                    for(int dy = 0; dy < p.th; dy++){
                        int idx = (gx + dx) * gridH + (gy + dy);
                        if(idx >= 0 && idx < bluePrint.size) drawVisited.add(idx);
                    }
                }

                switch(p.icon == null ? "" : p.icon){
                    case "rgbase" -> { baseCount++; rotary = true; }
                    case "small-adapter" -> baseCount++;
                    case "gbase" -> baseCount++;
                    case "gbreach" -> breaches++;
                    case "cbreach" -> { breaches++; heavies++; }
                    case "grbreach" -> { breaches++; heavies++; }
                    case "clbreach" -> { breaches++; twin = true; }
                    case "apacker" -> ammoBoxes++;
                    case "aplate" -> armors++;
                    case "rad" -> rads++;
                    case "rfinder" -> radars++;
                    case "firemod" -> fireMod = true;
                    case "homingmod" -> homingMod = true;
                    default -> { }
                }
            }

            float hw = headRegion.found() ? headRegion.width : size * tilesize;
            float hh = headRegion.found() ? headRegion.height : size * tilesize;
            // 底座零件越多, 车体越壮
            float bodyScale = 1f + 0.06f * Math.max(0, baseCount - 1);

            // ① 外挂设备: 画在车体下面, 只从边缘露出来, 像是装在炮塔内部
            drawGadget(iconAmmo, ammoBoxes > 0, -0.30f * hw, 0.17f * hh, rot, cx, cy);
            drawGadget(iconArmor, armors > 0, -0.30f * hw, -0.17f * hh, rot, cx, cy);
            drawGadget(iconRad, rads > 0, 0f, 0.33f * hh, rot, cx, cy);
            drawGadget(iconRadar, radars > 0, 0f, -0.33f * hh, rot, cx, cy);

            // ② 车体 + 基本炮管
            if(headRegion.found()){
                Draw.rect(headRegion, cx, cy, hw * bodyScale, hh * bodyScale, rot);

                if(built && heat > 0.02f){
                    // 连续射击后的过热泛光
                    Draw.color(heatColor, Mathf.clamp(heat * 0.55f));
                    Draw.rect(headRegion, cx, cy, hw * bodyScale, hh * bodyScale, rot);
                    Draw.color();
                }
            }

            // ③ 炮管延伸段 + 炮口: 炮闩越多越长, 重型/双管各自变形
            float barrelEnd = 0.42f * hw;
            if(breaches > 0 && barrelStrip != null && barrelStrip.found()){
                float thick = 1f + 0.22f * heavies;
                int barrels = twin ? 2 : 1;
                float extra = Math.min(2.4f, 0.55f + 0.4f * breaches) * size * tilesize * 0.42f;
                float start = 0.30f * hw;
                barrelEnd = start + extra;
                for(int b = 0; b < barrels; b++){
                    // 双管时两根炮管并排, 间距按车体尺寸缩放
                    float perp = (b - (barrels - 1) * 0.5f) * hw * 0.20f * thick;
                    Tmp.v1.set(start + extra * 0.5f, perp).rotate(rot).add(cx, cy);
                    Draw.rect(barrelStrip, Tmp.v1.x, Tmp.v1.y, extra, barrelStrip.height * thick, rot);
                    if(muzzleBlock != null && muzzleBlock.found()){
                        Tmp.v1.set(start + extra, perp).rotate(rot).add(cx, cy);
                        Draw.rect(muzzleBlock, Tmp.v1.x, Tmp.v1.y, muzzleBlock.width * 1.05f, muzzleBlock.height * thick * 1.15f, rot);
                    }
                }
            }

            // ④ 旋转底座: 座圈加宽, 让"旋转炮塔"的结构更明确
            if(rotary && rootRegion.found()){
                Draw.rect(rootRegion, cx, cy, rootRegion.width * 0.85f, rootRegion.height * 0.85f, rot);
            }

            // ⑤ 改造件: 在炮口附近发光, 一眼看出装了哪种改造
            if(fireMod || homingMod){
                float gx = barrelEnd + 2f;
                Tmp.v1.set(gx, homingMod ? -3f : 0f).rotate(rot).add(cx, cy);
                Draw.color(fireMod ? Pal.lightOrange : Pal.accent);
                Draw.alpha(0.55f + 0.45f * Mathf.absin(Time.time * 3f, 1f));
                Fill.circle(Tmp.v1.x, Tmp.v1.y, 1.8f);
                Draw.color();
                Draw.alpha(1f);
            }

            // ⑥ 未建完时露出转轴 (建完后被车体遮住)
            if(!built && rootRegion.found()){
                if(rootOutlineRegion.found()){
                    Draw.color(0f, 0f, 0f, 0.5f);
                    Draw.rect(rootOutlineRegion, cx, cy, rootRegion.width, rootRegion.height, rot);
                    Draw.color();
                }
                Draw.rect(rootRegion, cx, cy, rot);
            }

            // ⑦ 炮口火光
            if(built && curRecoil > 0.08f){
                float mx, my;
                if(barrelEnd > 0.42f * hw){
                    Tmp.v1.set(barrelEnd, 0f).rotate(rot).add(cx, cy);
                    mx = Tmp.v1.x;
                    my = Tmp.v1.y;
                }else{
                    float d = size * tilesize * 0.42f;
                    mx = x + Mathf.cosDeg(rotation) * d + ox;
                    my = y + Mathf.sinDeg(rotation) * d + oy;
                }
                float f = Mathf.clamp(curRecoil * 2f);
                Draw.color(heatColor);
                Draw.alpha(f * 0.8f);
                Fill.circle(mx, my, 2f + 4.5f * f);
                Draw.color(Color.white);
                Draw.alpha(f * 0.9f);
                Fill.circle(mx, my, 1.2f + 2f * f);
                Draw.color();
                Draw.alpha(1f);
                Drawf.light(mx, my, 26f * f, heatColor, 0.6f);
            }
            Draw.alpha(1f);
        }

        /** 在车体下方绘制外挂零件, 只露出边缘 (看起来像装在炮塔内部)。 */
        protected void drawGadget(TextureRegion icon, boolean present, float lx, float ly, float rot, float cx, float cy){
            if(!present || icon == null || !icon.found()) return;
            Tmp.v1.set(lx, ly).rotate(rot).add(cx, cy);
            Draw.rect(icon, Tmp.v1.x, Tmp.v1.y, icon.width * 0.55f, icon.height * 0.55f, rot);
        }
    }
}
