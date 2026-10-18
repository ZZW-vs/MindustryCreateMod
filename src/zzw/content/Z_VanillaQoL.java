package zzw.content;

import mindustry.content.Blocks;

/**
 * 原版体验调优 (2026-10-18 大规模数值加强)。
 *
 * <p>原则: <b>只调整原版单例的数值字段 / 调用公开配置方法, 不重写任何行为</b> ——
 * 无存档兼容问题、无副作用。仅在 loadContent 阶段 (原版内容已加载、方块尚未
 * init 统计面板时) 执行, 保证面板显示同步更新。</p>
 *
 * <p>★ 加强清单:
 * <ul>
 *   <li>存储: 容器 300→500, 仓库 1000→1800, 液罐 1800→2800, 液罐小 700→850;</li>
 *   <li>分配: 分配器/路由器速度 8→16, 十字交叉 26帧→18帧 (显示20/秒),
 *       卸载器 →16 物品/秒, 质量驱动器射程 55→75 格 + 射速加快,
 *       桥: 长度 +1 (普通) / +5 (相位), 速度加快, 分类器/闸门保持原版节奏;</li>
 *   <li>电力: 节点连接数/范围加大, 电池容量加大, 发电机组输出小幅加强 (+50%~70%);</li>
 *   <li>功能: 石墨压板机 +10%, 力墙/超速穹顶/修复投影仪 +25%, 小型修复/超速 +12%;</li>
 *   <li>核心血量: shard +250, foundation +500, nucleus +1000; 地雷伤害×4;</li>
 *   <li>修理: 修理点 →35 血/秒, 修理炮塔 →250 血/秒;</li>
 *   <li>泵: 机械 8/s, 旋转 52/s, 脉冲 130/s;</li>
 *   <li>核心单位 alpha/beta/gamma: 血量 +25%, 子弹伤害 11→13, 移速 +8%。</li>
 * </ul></p>
 *
 * <p>★ 已知限制: 原版路由器的单件缓冲是硬编码行为 (acceptItem 要求完全为空,
 * 容量字段无效), 故路由器/分配器以速度翻倍代替"容量2"。分类器/闸门的连续
 * 放置上限 2 同样硬编码于 instantTransfer 配对检查中, 无法精确改为 4,
 * 保持原版行为不变 (用户确认)。</p>
 *
 * <p>★ 注意: consumePowerBuffered / consumePower 在内容加载期重复调用会
 * <b>替换</b> (而非追加) 电源消费者 (Block.consume 的幂等设计), 因此电池/反应堆
 * 改动是安全的。Blocks 中字段声明为 Block 类型, 需 instanceof 强转;
 * 若原版未来版本类型/字段变化, 编译期直接暴露。</p>
 */
public class Z_VanillaQoL{

    public static void load(){
        // Blocks 的字段均为静态, 直接 Blocks.xxx 引用

        // ===== 存储容量 =====
        Blocks.container.itemCapacity = 500;
        Blocks.vault.itemCapacity = 1800;
        Blocks.liquidTank.liquidCapacity = 2800f;
        Blocks.liquidContainer.liquidCapacity = 850f;

        // ===== 分配系统 =====

        // 路由器/分配器: 分配速度 8→16 (容量为硬编码, 见类注释)
        if(Blocks.router instanceof mindustry.world.blocks.distribution.Router r) r.speed = 16f;
        if(Blocks.distributor instanceof mindustry.world.blocks.distribution.Router r) r.speed = 16f;

        // 十字交叉: 穿越 26帧→18帧, 面板显示 20 物品/秒
        if(Blocks.junction instanceof mindustry.world.blocks.distribution.Junction j){
            j.speed = 18f;
            j.displayedSpeed = 20f;
        }

        // 分类器/反向分类器/溢流门/反溢流门: 保持原版 instantTransfer (连续放置上限 2)
        // (曾试验关闭 instantTransfer 解除上限, 用户测试后要求恢复原版节奏)

        // 卸载器: 16 物品/秒 (speed = 每件占用帧数)
        if(Blocks.unloader instanceof mindustry.world.blocks.storage.Unloader u) u.speed = 60f / 16f;

        // 质量驱动器: 射程 55→75 格 (440→600), 射速 200→170 帧
        if(Blocks.massDriver instanceof mindustry.world.blocks.distribution.MassDriver m){
            m.range = 600f;
            m.reload = 170f;
        }

        // 桥式传送带: 速度 74→148 (翻倍), 长度 4→5
        if(Blocks.itemBridge instanceof mindustry.world.blocks.distribution.BufferedItemBridge bb){
            bb.speed = 148f;
            bb.range = 5;
        }

        // 相位传送带: 传输间隔 2→1.2 (吞吐 +67%), 长度 12→17
        if(Blocks.phaseConveyor instanceof mindustry.world.blocks.distribution.ItemBridge pc){
            pc.transportTime = 1.2f;
            pc.range = 17;
        }

        // 桥式导管: 长度 4→5, 容量 100→150 (容量直接决定过桥液体流速)
        if(Blocks.bridgeConduit instanceof mindustry.world.blocks.distribution.ItemBridge bc) bc.range = 5;
        Blocks.bridgeConduit.liquidCapacity = 150f;

        // 相位导管: 长度 12→17, 容量 100→150
        if(Blocks.phaseConduit instanceof mindustry.world.blocks.distribution.ItemBridge pc) pc.range = 17;
        Blocks.phaseConduit.liquidCapacity = 150f;

        // ===== 电力系统 =====

        // 电力节点: 连接 10→12, 范围 6→8
        if(Blocks.powerNode instanceof mindustry.world.blocks.power.PowerNode pn){
            pn.maxNodes = 12;
            pn.laserRange = 8;
        }

        // 大型电力节点: 连接 15→19, 范围 15→21
        if(Blocks.powerNodeLarge instanceof mindustry.world.blocks.power.PowerNode pn){
            pn.maxNodes = 19;
            pn.laserRange = 21;
        }

        // 浪潮塔: 连接 2→4, 范围 40→55
        if(Blocks.surgeTower instanceof mindustry.world.blocks.power.PowerNode pn){
            pn.maxNodes = 4;
            pn.laserRange = 55;
        }

        // 电池: 4000→5000 / 50000→65000 (consumePowerBuffered 会替换旧电源消费者)
        Blocks.battery.consumePowerBuffered(5000f);
        Blocks.batteryLarge.consumePowerBuffered(65000f);

        // 发电机组输出: 小幅加强 (+50%~70%, 曾试过大幅上调, 观感过高已收敛)
        if(Blocks.solarPanel instanceof mindustry.world.blocks.power.SolarGenerator s) s.powerProduction = 0.2f;   // 0.12→0.2
        if(Blocks.largeSolarPanel instanceof mindustry.world.blocks.power.SolarGenerator s) s.powerProduction = 2.5f; // 1.6→2.5
        if(Blocks.thoriumReactor instanceof mindustry.world.blocks.power.NuclearReactor n) n.powerProduction = 22f;   // 15→22
        if(Blocks.differentialGenerator instanceof mindustry.world.blocks.power.ConsumeGenerator g) g.powerProduction = 27f; // 18→27
        if(Blocks.steamGenerator instanceof mindustry.world.blocks.power.ConsumeGenerator g) g.powerProduction = 8f;  // 5.5→8
        if(Blocks.combustionGenerator instanceof mindustry.world.blocks.power.ConsumeGenerator g) g.powerProduction = 1.5f;  // 1→1.5
        if(Blocks.impactReactor instanceof mindustry.world.blocks.power.ImpactReactor i) i.powerProduction = 200f;   // 130→200

        // ===== 功能方块 =====

        // 石墨压板机: 生产效率 +10% (加工时间 90→81.8)
        if(Blocks.graphitePress instanceof mindustry.world.blocks.production.GenericCrafter g){
            g.craftTime = 90f / 1.1f;
        }

        // 力墙投影仪 +25%: 半径 101.7→127.1, 护盾血量 750→937.5
        if(Blocks.forceProjector instanceof mindustry.world.blocks.defense.ForceProjector f){
            f.radius = 127.1f;
            f.shieldHealth = 937.5f;
        }

        // 修复投影仪 +25%: 轮次 250→200, 范围 85→106.25, 单次治疗 11→13.75
        if(Blocks.mendProjector instanceof mindustry.world.blocks.defense.MendProjector m){
            m.reload = 200f;
            m.range = 106.25f;
            m.healPercent = 13.75f;
        }

        // 超速穹顶 +25%: 加速 2.5→3.125, 范围 200→250, 物品周期 300→240
        if(Blocks.overdriveDome instanceof mindustry.world.blocks.defense.OverdriveProjector o){
            o.speedBoost = 3.125f;
            o.range = 250f;
            o.useTime = 240f;
        }

        // 小型修复器 +12%: 轮次 200→178, 范围 40→44.8, 单次治疗 4→4.48
        if(Blocks.mender instanceof mindustry.world.blocks.defense.MendProjector m){
            m.reload = 178f;
            m.range = 44.8f;
            m.healPercent = 4.48f;
        }

        // 小型超速投影仪 +12%: 加速 1.5→1.68, 范围 80→89.6
        if(Blocks.overdriveProjector instanceof mindustry.world.blocks.defense.OverdriveProjector o){
            o.speedBoost = 1.68f;
            o.range = 89.6f;
        }

        // 冲击地雷: 伤害 25→100 (×4)
        if(Blocks.shockMine instanceof mindustry.world.blocks.defense.ShockMine s){
            s.damage = 100f;
        }

        // ===== 核心血量 =====
        Blocks.coreShard.health = 1100 + 250;      // 1350
        Blocks.coreFoundation.health = 3500 + 500; // 4000
        Blocks.coreNucleus.health = 6000 + 1000;   // 7000

        // ===== 修理 =====
        // repairSpeed 单位 = 血/帧, 面板显示 = ×60
        if(Blocks.repairPoint instanceof mindustry.world.blocks.units.RepairTurret r) r.repairSpeed = 35f / 60f;
        if(Blocks.repairTurret instanceof mindustry.world.blocks.units.RepairTurret r) r.repairSpeed = 250f / 60f;

        // ===== 泵 =====
        // pumpAmount 单位 = 液体/帧, 面板显示 = ×60
        if(Blocks.mechanicalPump instanceof mindustry.world.blocks.production.Pump p) p.pumpAmount = 8f / 60f;   // 8 液体/秒
        if(Blocks.rotaryPump instanceof mindustry.world.blocks.production.Pump p) p.pumpAmount = 52f / 60f;      // 52 液体/秒
        if(Blocks.impulsePump instanceof mindustry.world.blocks.production.Pump p) p.pumpAmount = 130f / 60f;    // 130 液体/秒

        // ===== 基础核心单位 alpha / beta / gamma (小加强) =====
        buffCoreUnit(mindustry.content.UnitTypes.alpha, 187.5f, 3.25f, 17f * 0.9f);
        buffCoreUnit(mindustry.content.UnitTypes.beta, 212.5f, 3.55f, 20f * 0.9f);
        buffCoreUnit(mindustry.content.UnitTypes.gamma, 275f, 3.8f, 15f * 0.9f);

        // 保留: 基础传送带三档微速提升
        if(Blocks.conveyor instanceof mindustry.world.blocks.distribution.Conveyor c){
            c.speed = 0.035f;
            c.displayedSpeed = 4.9f;
        }
        if(Blocks.titaniumConveyor instanceof mindustry.world.blocks.distribution.Conveyor c){
            c.speed = 0.09f;
            c.displayedSpeed = 12.4f;
        }
        if(Blocks.armoredConveyor instanceof mindustry.world.blocks.distribution.ArmoredConveyor c){
            c.speed = 0.09f;
            c.displayedSpeed = 12.4f;
        }

        // ★ 调试: 记录应用后的实际值 (排查"改动没生效"问题)
        debugDump("loadContent");
    }

    /**
     * 核心单位小加强: 血量 / 移速直接赋值, 武器伤害与射速对首武器生效。
     *
     * @param type        单位类型 (alpha/beta/gamma)
     * @param health      新血量
     * @param speed       新移速
     * @param reload      新射击轮次 (帧)
     */
    private static void buffCoreUnit(mindustry.type.UnitType type, float health, float speed, float reload){
        type.health = health;
        type.speed = speed;
        if(type.weapons.size > 0){
            var w = type.weapons.first();
            w.reload = reload;
            if(w.bullet != null) w.bullet.damage = 13f; // 11→13
        }
    }

    /**
     * 调试: 输出原版方块当前实际值, 同时写入 Mindustry 数据目录的 create-debug.txt。
     *
     * <p>用途: 在 loadContent (应用改动时) 和 init (全部内容初始化完成后) 各调用一次,
     * 若两次值都正确但游戏里仍是原版数值, 说明有更晚的机制在改写; 若 init 时值被改回,
     * 说明有其他模组在覆盖。文件路径: %APPDATA%\Mindustry\create-debug.txt</p>
     *
     * @param phase 调用阶段标记 (如 "loadContent" / "init")
     */
    public static void debugDump(String phase){
        try{
            String line = "[Create] 原版QoL @" + phase + ": 容器=" + Blocks.container.itemCapacity
                + ", 仓库=" + Blocks.vault.itemCapacity
                + ", 液罐=" + (int)Blocks.liquidTank.liquidCapacity
                + ", 铜带速度=" + (Blocks.conveyor instanceof mindustry.world.blocks.distribution.Conveyor c ? c.speed : -1f)
                + ", 卸载器帧=" + (Blocks.unloader instanceof mindustry.world.blocks.storage.Unloader u ? u.speed : -1f)
                + ", 钍堆输出=" + (Blocks.thoriumReactor instanceof mindustry.world.blocks.power.NuclearReactor n ? n.powerProduction : -1f)
                + ", 冲击堆输出=" + (Blocks.impactReactor instanceof mindustry.world.blocks.power.ImpactReactor i ? i.powerProduction : -1f)
                + ", 分类器instant=" + Blocks.sorter.instantTransfer;
            arc.util.Log.info(line);
            arc.Core.settings.getDataDirectory().child("create-debug.txt").writeString(line + "\n", true);
        }catch(Throwable t){
            arc.util.Log.err("[Create] 原版QoL调试输出失败", t);
        }
    }
}
