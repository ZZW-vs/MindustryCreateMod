package zzw.content;

import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.TechTree;
import mindustry.content.TechTree.TechNode;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Objectives.Objective;
import mindustry.game.Objectives.Produce;
import mindustry.game.Objectives.Research;
import mindustry.content.UnitTypes;
import mindustry.type.ItemStack;
import zzw.content.Z_Items;
import zzw.content.Z_Liquids;
import zzw.content.blocks.Z_AdvTurrets;
import zzw.content.blocks.Z_Blocks;
import zzw.content.Z_Factory;
import zzw.content.exp.Z_Exp;
import zzw.content.blocks.Z_SoulTurrets;
import zzw.content.blocks.Z_Turrets;
import zzw.content.optics.Z_Optics;
import zzw.content.units.Z_MonolithUnits;
import zzw.content.units.Z_Units;
import zzw.content.Z_Other;
import zzw.content.blocks.distribution.Z_Distribution;
import zzw.content.mechanics.torque.Z_Torque;

import static mindustry.content.TechTree.nodeProduce;

/**
 * PU132 科技树移植版 (unity.content.UnityTechTree)。
 *
 * <p>把全部 PU 内容挂到原版科技树的对应父节点下 (区块挂原版方块节点、
 * 单位挂 fortress、生产物品挂 lead/graphite/thorium/surgeAlloy 的生产链)。</p>
 *
 * <p>★ v158 适配: PU132 的 {@code TechTree.get(parent)} 在 v158.1 中不存在,
 * 改用 {@link UnlockableContent#techNode} 公开字段直接取原版节点;
 * {@code node/objectives/requirements} 逻辑与 PU132 完全一致。</p>
 *
 * <p>★ PU132 原版即未包含 koruh 经验方块、scar/koruh 单位的科技树节点
 * (这些内容在 PU 中通过沙盒/区块解锁), 本移植保持一致。</p>
 *
 * @author PU132 原作, 移植: zzw
 */
public class Z_TechTree{
    private static TechNode context = null;

    public static void load(){
        //region blocks

        attach(Blocks.surgeSmelter, () -> {
            node(Z_Factory.darkAlloyForge, () -> {
                nodeProduce(Z_Items.darkAlloy);
            });
            node(Z_Blocks.monolithAlloyForge);
            node(Z_Factory.sparkAlloyForge, Seq.with(new Research(Z_Items.sparkAlloy)));
        });

        // ★ 压板机系列 (铁/金/铜 + 大型) 挂原版石墨压缩机 (同类压制设备) 之后; 板材/黄铜/锌一并入树
        attach(Blocks.graphitePress, () -> {
            node(Z_Factory.Plate_Maker_Iron, () -> {
                node(Z_Factory.Large_Plate_Maker_Iron, () -> {
                    nodeProduce(Z_Items.Iron_Sheet);
                });
            });

            node(Z_Factory.Plate_Maker_Gold, () -> {
                node(Z_Factory.Large_Plate_Maker_Gold, () -> {
                    nodeProduce(Z_Items.Gold_Sheet);
                });
            });

            node(Z_Factory.Plate_Maker_Copper, () -> {
                node(Z_Factory.Large_Plate_Maker_Copper, () -> {
                    nodeProduce(Z_Items.Copper_Sheet);
                });
            });

            nodeProduce(Z_Items.Brass);
            nodeProduce(Z_Items.Zinc);
        });

        // ★ 工厂链: 硅冶炼厂 → 钢炉 → 致密合金炉 → 燃烧熔炼 → 迪里姆坩埚 → End 派系 (挂最深处, 用户指定)
        attach(Blocks.siliconSmelter, () -> {
            node(Z_Factory.steelSmelter, () -> {
                node(Z_Factory.denseSmelter, () -> {
                    node(Z_Factory.liquifier, () -> {
                        node(Z_Factory.solidifier);
                        node(Z_Factory.diriumCrucible, () -> {
                            node(Z_Factory.endForge, () -> {
                                nodeProduce(Z_Items.advanceAlloy, () -> {
                                    nodeProduce(Z_Items.lightAlloy, () -> {
                                        nodeProduce(Z_Items.luminum);
                                    });
                                });
                            });

                            node(Z_Factory.terminalCrucible, () -> {
                                node(Z_Factory.holdingCrucible);
                            });
                        });
                    });
                });
            });
        });

        attach(Blocks.powerNode, () -> {
            node(Z_Optics.lightLamp, () -> {
                node(Z_Optics.lightReflector, () -> {
                    node(Z_Optics.lightDivisor);
                });

                node(Z_Optics.oilLamp);
            });
        });

        attach(Blocks.arc, () -> {
            // ★ 光子科技全系列 (11 门炮) 按实力排序, 同档并列 (用户指定):
            //   photon(1280) → electron/proton(2540) → graviton/neutron(2780/2520)
            //   → wBoson/zBoson(4000) → gluon(5000) → higgsBoson(6000) → muon/ephemeron(9800)
            node(Z_Turrets.photon, () -> {
                node(Z_Turrets.electron, () -> {
                    node(Z_Turrets.proton);
                    node(Z_Turrets.graviton, () -> {
                        node(Z_Turrets.neutron);
                        node(Z_Turrets.wBoson, () -> {
                            node(Z_Turrets.zBoson, () -> {
                                node(Z_Turrets.gluon, () -> {
                                    node(Z_Turrets.higgsBoson, () -> {
                                        node(Z_Turrets.muon, () -> {
                                            node(Z_Turrets.ephemeron);
                                        });
                                    });
                                });
                            });
                        });
                    });
                });
            });

            node(Z_SoulTurrets.diviner, Seq.with(new Research(Z_Items.monolite)), () -> {
                node(Z_SoulTurrets.mage, () -> {
                    node(Z_AdvTurrets.heatRay, () -> {
                        node(Z_AdvTurrets.incandescence, () -> {
                            node(Z_AdvTurrets.prism, () -> {
                                node(Z_AdvTurrets.supernova);
                            });
                        });
                    });

                    node(Z_AdvTurrets.oracle, Seq.with(new Research(Z_Items.monolithAlloy)));
                });

                node(Z_AdvTurrets.recluse, () -> {
                    node(Z_SoulTurrets.blackout);
                });
            });

            node(Z_SoulTurrets.ricochet, Seq.with(new Research(Z_Items.monolite)), () -> {
                node(Z_SoulTurrets.shellshock, Seq.with(new Research(Z_Items.monolithAlloy)), () -> {
                    node(Z_SoulTurrets.purge);
                });

                node(Z_AdvTurrets.lifeStealer, () -> {
                    node(Z_AdvTurrets.absorberAura);
                });
            });
        });

        attach(Blocks.titaniumWall, () -> {
            node(Z_Blocks.metaglassWall, () -> {
                node(Z_Blocks.metaglassWallLarge);
            });

            node(Z_Blocks.electrophobicWall, Seq.with(new Research(Z_Items.monolite)), () -> {
                node(Z_Blocks.electrophobicWallLarge);
            });
        });

        attach(Blocks.siliconCrucible, () -> {
            node(Z_Factory.irradiator, Seq.with(new Research(Items.thorium), new Research(Items.titanium), new Research(Items.surgeAlloy)));
        });

        // ★ 生产/采集方块挂原版对应节点之后 (用户指定)
        attach(Blocks.waterExtractor, () -> {
            node(Z_Torque.mechanicalExtractor);
        });

        attach(Blocks.laserDrill, () -> {
            node(Z_Torque.augerDrill);
            nodeProduce(Z_Items.Iron);
            nodeProduce(Z_Items.Gold);
        });

        attach(Blocks.oilExtractor, () -> {
            node(Z_Torque.oilDerrick);
        });

        attach(Blocks.pneumaticDrill, () -> {
            node(Z_Distribution.distributionDrill);
            node(Z_Factory.coalExtractor);
        });

        attach(Blocks.cultivator, () -> {
            node(Z_Factory.sporeFarm, () -> {
                node(Z_Factory.sporePyrolyser, () -> {
                    nodeProduce(Z_Items.contagium);
                });
            });

            node(Z_Factory.Pumpkin_Drill, () -> {
                nodeProduce(Z_Items.Pumpkin_Seeds, () -> {
                    nodeProduce(Z_Items.Pulp);
                });
            });
        });

        // ★ orb 系列按实力挂原版 salvo 之后 (用户指定); shielder 例外单独挂 force-projector
        attach(Blocks.salvo, () -> {
            node(Z_Turrets.orb, () -> {
                node(Z_Turrets.shockwire, () -> {
                    node(Z_Turrets.current, () -> {
                        node(Z_Turrets.plasma, () -> {
                            node(Z_Turrets.electrobomb);
                        });
                    });
                });
            });
        });

        // ★ shielder (护盾炮) 单独挂原版力场投影仪之后 (用户指定)
        attach(Blocks.forceProjector, () -> {
            node(Z_Turrets.shielder);
        });

        // ★ 经验力场投影仪挂原版修复投影仪之后 (用户指定 2026-10-18)
        attach(Blocks.mendProjector, () -> {
            node(Z_Exp.shieldGenerator, () -> {
                node(Z_Exp.deflectGenerator);
            });
        });

        // ★ 经验物品炮台 create-inferno 挂原版 scorch (灼烧) 之后 (用户指定)
        attach(Blocks.scorch, () -> {
            node(Z_Exp.infernoTurret);
        });

        // ★ 斩骨刀 chopper 在原版 duo 后单独开一个分支 (用户指定)
        attach(Blocks.duo, () -> {
            node(Z_Torque.chopper);
        });

        // ★ 经验电力炮台链挂原版 lancer 之后 (用户指定 2026-10-18):
        //   laserTurret → chargeLaserTurret → fractalLaserTurret → [btLaserTurret ∥ swarmLaserTurret]
        attach(Blocks.lancer, () -> {
            node(Z_Exp.laserTurret, () -> {
                node(Z_Exp.chargeLaserTurret, () -> {
                    node(Z_Exp.fractalLaserTurret, () -> {
                        node(Z_Exp.btLaserTurret);
                        node(Z_Exp.swarmLaserTurret);
                    });
                });
            });
        });

        attach(Blocks.wave, () -> {
            node(Z_Exp.frostLaserTurret);
        });

        attach(Blocks.tsunami, () -> {
            node(Z_Exp.kelvinLaserTurret);
        });

        // ★ 护盾墙挂原版塑钢墙之后 (归入原版墙体分类)
        attach(Blocks.plastaniumWall, () -> {
            node(Z_Blocks.shieldedWall, () -> {
                node(Z_Blocks.shieldedWallLarge);
            });
        });

        attach(Blocks.overdriveProjector, () -> {
            node(Z_Blocks.superCharger, Seq.with(new Research(Z_Factory.irradiator)));
        });

        attach(Blocks.surgeTower, () -> {
            node(Z_Turrets.absorber, Seq.with(new Research(Z_Factory.sparkAlloyForge)));
        });

        // ★ dark 派系前三炮台挂原版 spectre (幽灵) 之后 (用户指定)
        attach(Blocks.spectre, () -> {
            node(Z_Turrets.apparition, () -> {
                node(Z_Turrets.ghost, () -> {
                    node(Z_Turrets.banshee);
                });
            });
        });

        // ★ 雷光系炮台升级线挂原版 fuse (融片) 之后 (用户指定)
        attach(Blocks.fuse, () -> {
            node(Z_Other.smite, () -> {
                node(Z_Other.rift, () -> {
                    node(Z_Other.rift_pro, () -> {
                        node(Z_Other.fracture);
                    });
                });
            });
        });

        //endregion
        //region units

        // ★ create-stele (巨石) 系列改挂原版 dagger (双刺) 之后 (用户指定, 原挂 fortress)
        attach(UnitTypes.dagger, () -> {
            // PU132: monolith 机甲/无人机树挂在原版单位下
            node(Z_MonolithUnits.stele, () -> {
                node(Z_MonolithUnits.pedestal, () -> {
                    node(Z_MonolithUnits.pilaster, () -> {
                        node(Z_MonolithUnits.pylon, () -> {
                            node(Z_MonolithUnits.monument, () -> {
                                node(Z_MonolithUnits.colossus, () -> {
                                    node(Z_MonolithUnits.bastion);
                                });
                            });
                        });
                    });
                });

                node(Z_MonolithUnits.adsect, () -> {
                    node(Z_MonolithUnits.comitate);
                });
            });
        });

        // ★ 直升机系列 (T1-T6) + EMP 系列 (T1-T5) 挂原版空军工厂之后 (用户指定)
        attach(Blocks.airFactory, () -> {
            node(Z_Units.caelifera, () -> {
                node(Z_Units.schistocerca, () -> {
                    node(Z_Units.anthophila, () -> {
                        node(Z_Units.vespula, () -> {
                            node(Z_Units.lepidoptera, () -> {
                                node(Z_Units.mantodea);
                            });
                        });
                    });
                });
            });

            node(Z_Units.discharge, () -> {
                node(Z_Units.pulse, () -> {
                    node(Z_Units.emission, () -> {
                        node(Z_Units.waveform, () -> {
                            node(Z_Units.ultraviolet);
                        });
                    });
                });
            });
        });

        // ★ 原版扩展 T6/T7 单位: 挂到对应原版 T5 单位之后 (用户指定)
        //   升级链与 recursive-reconstructor 的 upgrades/otherUpgrades 完全一致
        attach(UnitTypes.reign, () -> {
            node(Z_Units.citadel, () -> {
                node(Z_Units.empire);
            });
        });

        attach(UnitTypes.toxopid, () -> {
            node(Z_Units.araneidae, () -> {
                node(Z_Units.theraphosidae);
            });
        });

        attach(UnitTypes.corvus, () -> {
            node(Z_Units.cygnus, () -> {
                node(Z_Units.sagittarius);
            });
        });

        attach(UnitTypes.eclipse, () -> {
            node(Z_Units.mantle, () -> {
                node(Z_Units.aphelion);
            });
        });

        attach(UnitTypes.oct, () -> {
            node(Z_Units.sedec, () -> {
                node(Z_Units.trigintaduo);
            });
        });

        attach(UnitTypes.omura, () -> {
            node(Z_Units.fin, () -> {
                node(Z_Units.blue);
            });
        });

        // ★ 递归重构器 (T6/T7 合用) 挂原版 T5 重构器之后 (用户指定)
        attach(Blocks.tetrativeReconstructor, () -> {
            node(Z_Blocks.recursiveReconstructor);
        });

        //endregion
        //region items

        attach(Items.lead, () -> {
            nodeProduce(Z_Items.nickel, () -> {
                nodeProduce(Z_Items.cupronickel);
            });
        });

        attach(Items.graphite, () -> {
            nodeProduce(Z_Items.monolite);

            nodeProduce(Z_Items.stone, () -> {
                nodeProduce(Z_Items.Andesite_Alloy);

                nodeProduce(Z_Items.denseAlloy, () -> {
                    nodeProduce(Z_Items.steel, () -> {
                        nodeProduce(Z_Liquids.lava, () -> {
                            nodeProduce(Z_Items.dirium);
                        });
                    });
                });
            });
        });

        attach(Items.thorium, () -> {
            nodeProduce(Z_Items.archDebris, Seq.with(new Research(Z_Items.monolite)), () -> {
                nodeProduce(Z_Items.monolithAlloy);
            });
        });

        attach(Items.surgeAlloy, () -> {
            nodeProduce(Z_Items.imberium, () -> {
                nodeProduce(Z_Items.sparkAlloy);

                nodeProduce(Z_Items.irradiantSurge);
            });
        });

        // ★ 经验 / 扭矩 / 温度 三大系统独立科技树 (用户指定: 单独开一条;
        //   其中炮台/墙体归入原版对应分类, 不在此树内)

        // ── 经验系列: 焚化炉为系列树根, 之后 经验罐 → 经验箱 (用户指定 2026-10-18);
        //   沙盒类 (喷泉/虚空) 不入树; 铀反应堆挂原版钍反应堆之后
        root(Z_Exp.expIncinerator, () -> {
            node(Z_Exp.expTank, () -> {
                node(Z_Exp.expChest);

                node(Z_Exp.expRouter, () -> {
                    node(Z_Exp.expTower, () -> {
                        node(Z_Exp.expTowerDiagonal);
                        node(Z_Exp.bufferTower);
                    });
                });

                node(Z_Exp.expHub, () -> {
                    node(Z_Exp.expUnloader);
                    node(Z_Exp.expNode, () -> {
                        node(Z_Exp.expNodeLarge);
                    });
                });
            });
        });

        // ── 扭矩系列: 动力/传动/磁力 (chopper 例外, 挂原版 duo 后);
        //   沙盒类 (永动扭矩) 不入树; 钕定子不入树 (用户指定)
        root(Z_Torque.handCrank, () -> {
            // 动力
            node(Z_Torque.windTurbine);
            node(Z_Torque.waterTurbine);
            node(Z_Torque.electricMotor, () -> {
                node(Z_Torque.electricRotorSmall, () -> {
                    node(Z_Torque.electricRotor);
                });
            });
            node(Z_Torque.flywheel, () -> {
                node(Z_Torque.steamPiston);
            });

            // 传动
            node(Z_Torque.driveShaft, () -> {
                node(Z_Torque.inlineGearbox, () -> {
                    node(Z_Torque.simpleTransmission);
                });
                node(Z_Torque.shaftRouter);
                node(Z_Torque.driveBeltSmall, () -> {
                    node(Z_Torque.driveBeltLarge);
                });
            });

            // 磁力
            node(Z_Torque.nickelStator, () -> {
                node(Z_Torque.nickelStatorLarge, () -> {
                    node(Z_Torque.nickelElectromagnet);
                });
            });
        });

        // ── 温度系列: 热网 + 坩埚;
        //   沙盒类 (无限热源/冷源/坩埚源) 不入树; 塞贝克发电机改挂原版热发电机 (用户指定)
        root(Z_Torque.thermalHeater, () -> {
            node(Z_Torque.heatPipe, () -> {
                node(Z_Torque.smallRadiator);
            });

            node(Z_Torque.combustionHeater);
            node(Z_Torque.solarCollector, () -> {
                node(Z_Torque.solarReflector);
            });

            node(Z_Torque.crucible, () -> {
                node(Z_Torque.crucibleChannel, () -> {
                    node(Z_Torque.crucibleFluidLoader);
                    node(Z_Torque.cruciblePump);
                });
                node(Z_Torque.castingMold);
                node(Z_Torque.modularCrucible);
            });
        });

        // ★ 塞贝克发电机 (热差发电) 挂原版热发电机之后 (用户指定 2026-10-18)
        attach(Blocks.thermalGenerator, () -> {
            node(Z_Torque.seebeckGenerator);
        });

        // ★ 经验铀反应堆挂原版钍反应堆之后 (用户指定 2026-10-18)
        attach(Blocks.thoriumReactor, () -> {
            node(Z_Exp.uraniumReactor);
        });

        // ★ 兜底: 没挂科技节点的 PU 内容统一 alwaysUnlocked (普罗塞直接可造)
        unlockRemaining();
    }

    /**
     * 开一条<b>普罗塞树内</b>的独立系列 (用户 2026-10-18 指定: 不开独立栏目/树根,
     * 而是挂在塞普罗科技树起点 core-shard 之下, 作为树里的一个单独系列显示)。
     *
     * <p>实现: 通过 {@link #attach} 把 context 切到普罗塞树根 (core-shard 节点),
     * 再把系列首方块挂为其直接子节点 —— 与原版 message/groundFactory 等并列,
     * 不进入 {@link TechTree#roots}, 研究界面的树选择器里不会多出栏目。</p>
     *
     * @param content  系列首方块 (作为该系列的树根)
     * @param children 系列的整棵分支
     */
    private static void root(UnlockableContent content, Runnable children){
        attach(Blocks.coreShard, () -> node(content, content.researchRequirements(), children));
    }

    /** 把 children 挂到 parent 的普罗塞科技树节点下 (Erekir 树的同名节点不算数)。 */
    private static void attach(UnlockableContent parent, Runnable children){
        context = serpuloNode(parent);
        children.run();
    }

    /**
     * 解析内容在<b>普罗塞 (Serpulo) 树</b>里的科技节点。
     *
     * <p>关键: 原版 Erekir 树也包含 graphite/thorium/surgeAlloy 等物品的 produce 节点,
     * 而 {@code content.techNode} 只保存<b>最后创建</b>的那个 (Erekir 树加载在后)。
     * 若直接用 techNode, 挂在这些物品下的模组物品会全跑到 Erekir 树里
     * (用户实测)。这里沿每条节点的父链找到树根, 根为原版 core-shard 的才是普罗塞树。</p>
     */
    private static TechNode serpuloNode(UnlockableContent content){
        for(TechNode n : content.techNodes){
            TechNode cur = n;
            while(cur.parent != null) cur = cur.parent;
            if(cur.content == Blocks.coreShard) return n;
        }
        return content.techNode;  // 兜底: 内容不在任何树 (techNode 也为 null 时 context=null)
    }

    private static void node(UnlockableContent content, ItemStack[] requirements, Seq<Objective> objectives, Runnable children){
        TechNode node = new TechNode(context, content, requirements);
        if(objectives != null) node.objectives = objectives;

        TechNode prev = context;
        context = node;
        children.run();
        context = prev;
    }

    private static void node(UnlockableContent content, ItemStack[] requirements, Runnable children){
        node(content, requirements, null, children);
    }

    private static void node(UnlockableContent content, Seq<Objective> objectives, Runnable children){
        node(content, content.researchRequirements(), objectives, children);
    }

    private static void node(UnlockableContent content, Runnable children){
        node(content, content.researchRequirements(), children);
    }

    private static void node(UnlockableContent content, Seq<Objective> objectives){
        node(content, content.researchRequirements(), objectives, () -> {});
    }

    /**
     * ★ 兜底解锁 (研究制下的有限兜底, 2026-10 起):
     * 科技树已启用研究制 — 挂了科技节点的内容 ({@code techNode != null}) 不再强制
     * alwaysUnlocked, 由玩家在普罗塞战役里按树研究解锁。
     *
     * <p>仍走兜底直接解锁的内容 (techNode == null, 未挂树): 扭矩/坩埚系统方块、
     * 经验系方块、除巨石外的单位派系等。这些内容在 PU132 原版里同样不在树上
     * (PU 通过沙盒/区块解锁), 保持一致并保证普罗塞战役直接可用。</p>
     *
     * <p>★ 全环境可见: 强制覆盖 envEnabled, 避免 PU 移植方块因环境位不含
     * Env.terrestrial 而在 Serpulo(陆地块) 中被 hidden 过滤掉、只在
     * Erekir/太空(EasicEnvironment/space) 显示 (用户实测: 埃里克尔可见,
     * 赛普罗缺很多方块). Serpulo/Erekir/太空任意行星均显示。</p>
     */
    private static void unlockRemaining(){
        int[] count = {0};
        mindustry.Vars.content.each(c -> {
            if(!(c instanceof mindustry.ctype.UnlockableContent u)) return;
            if(!u.name.startsWith("create-")) return;
            // 段身等 hidden 内容保持隐藏
            if(u instanceof mindustry.type.UnitType ut && ut.hidden) return;
            if(u instanceof mindustry.world.Block blk && blk.envEnabled != mindustry.world.meta.Env.any){
                blk.envEnabled = mindustry.world.meta.Env.any;
            }
            // 研究制: 已挂树的内容交给科技树研究解锁, 不强制 alwaysUnlocked
            if(u.techNode != null) return;
            if(!u.alwaysUnlocked){
                u.alwaysUnlocked = true;
                count[0]++;
            }
        });
        arc.util.Log.info("[techtree] 兜底解锁 @ 个未挂树的本模组内容 (其余按科技树研究解锁)", count[0]);
    }

    private static void node(UnlockableContent content){
        node(content, () -> {});
    }

    private static void nodeProduce(UnlockableContent content, Seq<Objective> objectives, Runnable children){
        objectives.add(new Produce(content));
        node(content, content.researchRequirements(), objectives, children);
    }

    private static void nodeProduce(UnlockableContent content, Runnable children){
        nodeProduce(content, new Seq<>(), children);
    }

    private static void nodeProduce(UnlockableContent content){
        nodeProduce(content, () -> {});
    }

}
