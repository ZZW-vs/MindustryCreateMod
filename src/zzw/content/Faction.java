package zzw.content;

import arc.Core;
import arc.graphics.Color;
import mindustry.graphics.Pal;
import zzw.content.graphics.UnityPal;

/**
 * 派系 (移植自 PU132 {@code unity.mod.Faction}, 逐项一致)。
 *
 * <p>PU132 的 "派系" 不是玩法系统, 而是一套<b>内容分类元数据</b>:</p>
 * <ul>
 *   <li>每个派系有名字、主题色、本地化显示名;</li>
 *   <li>方块/单位/星球通过 {@link FactionMeta} 记录归属;</li>
 *   <li>物品栏 / 科技树的排列顺序按派系分组 (本项目已在 {@code TestMod.loadContent}
 *       的加载顺序里还原);</li>
 *   <li>物品说明末尾自动追加一行 "派系: ◆名字" (颜色取本枚举的主题色);</li>
 *   <li>单位实体通过 {@link Factionc#faction()} 拿到自己的派系, 并提供
 *       {@link Factionc#isSameFaction} 判定。</li>
 * </ul>
 *
 * <p>每个枚举项最后一个参数是 "该派系的内容名列表", 直接照抄 PU132 生成的
 * {@code FactionMeta.init()} (内容名与 PU 原版一一对应, 本模组按名字匹配)。
 * 本模组改名的内容统一在 {@link FactionMeta} 的别名表里补登, 不写进这里。</p>
 */
public enum Faction{
    scar("scar", Pal.remove,
        "terra-core", "terra", "hovos", "ryzer", "zena", "sundown", "rex", "excelsus",
        "whirlwind", "jetstream", "vortex"),

    dark("dark", Color.valueOf("fc6203"),
        "ore-umbrium", "apparition", "ghost", "banshee", "fallout", "catastrophe", "calamity",
        "extinction", "dark-wall", "dark-wall-large", "dark-alloy-forge"),

    advance("advance", Color.sky,
        "advance-constructor-module", "advance-constructor", "celsius", "kelvin", "caster",
        "storm", "eclipse", "xeno-corruptor", "cube", "wavefront", "deviation", "anomaly"),

    imber("imber", Pal.surge,
        "ore-imberium", "electro-tile", "orb", "shockwire", "current", "plasma", "electrobomb",
        "shielder", "orb-turret", "power-plant", "absorber", "piper", "spark-alloy-forge",
        "electrode", "inert", "arcnelidia", "ray-test", "test-link", "test"),

    plague("plague", Color.valueOf("a3f080"),
        "exowalker", "toxoswarmer", "toxobyte", "catenapede"),

    koruh("koruh", Color.valueOf("96f7c3"),
        "dense-smelter", "solidifier", "steel-smelter", "liquifier", "titanium-extractor",
        "lava-smelter", "dirium-crucible", "coal-extractor", "stone-wall", "dense-wall",
        "steel-wall", "steel-wall-large", "dirium-wall", "dirium-wall-large", "shield-projector",
        "dirium-projector", "time-mine", "shield-wall", "shield-wall-large", "steel-conveyor",
        "teleporter", "teleunit", "dirium-conveyor", "buffer-pad", "omega-pad", "cache-pad",
        "convert-pad", "uranium-reactor", "exp-fountain", "exp-void", "exp-tank", "exp-chest",
        "exp-router", "exp-tower", "exp-tower-diagonal", "buffer-tower", "exp-hub", "exp-node",
        "exp-node-large", "laser", "laser-charge", "laser-branch", "laser-fractal",
        "laser-breakthrough", "laser-frost", "laser-kelvin", "inferno", "buff-turret",
        "upgrade-turret", "buffer", "omega", "cache", "dijkstra", "phantasm", "kami"),

    light("light", Color.valueOf("fffde8"),
        "ore-luminum", "photon", "electron", "graviton", "proton", "neutron", "gluon", "w-boson",
        "z-boson", "higgs-boson", "singularity", "muon", "ephemeron", "light-lamp", "oil-lamp",
        "light-lamp-infi", "light-reflector", "light-divisor", "metaglass-wall",
        "metaglass-wall-large", "light-forge"),

    monolith("monolith", UnityPal.monolith,
        "ore-monolite", "sharpslate", "sharpslate-wall", "infused-sharpslate",
        "infused-sharpslate-wall", "arch-sharpslate", "arch-energy", "lore-monolith",
        "debris-extractor", "soul-infuser", "monolith-alloy-forge", "electrophobic-wall",
        "electrophobic-wall-large", "life-stealer", "absorber-aura", "heat-ray", "incandescence",
        "ricochet", "shellshock", "purge", "blackout", "diviner", "mage", "recluse", "oracle",
        "prism", "supernova", "megalith", "accretion", "salvaged-lab", "char-shadowcape",
        // Monolith 单位 (PU132 MonolithUnitTypes)
        "monolith-soul", "stele", "pedestal", "pilaster", "pylon", "monument", "colossus",
        "bastion", "adsect", "comitate", "stray", "tendence", "liminality", "calenture",
        "hallucination", "escapism", "fantasy"),

    youngcha("youngcha", Color.valueOf("a69f95"),
        "ore-nickel", "concrete-blank", "concrete-fill", "concrete-number", "concrete-stripe",
        "concrete", "stone-full-tiles", "stone-full", "stone-half", "stone-tiles", "small-turret",
        "med-turret", "chopper", "auger-drill", "mechanical-extractor", "spore-farm",
        "mechanical-conveyor", "heat-pipe", "drive-shaft", "inline-gearbox", "shaft-router",
        "simple-transmission", "crucible", "holding-crucible", "crucible-pump", "casting-mold",
        "spore-pyrolyser", "small-radiator", "thermal-heater", "combustion-heater",
        "solar-collector", "solar-reflector", "nickel-stator", "nickel-stator-large",
        "nickel-electromagnet", "electric-rotor-small", "electric-rotor", "hand-crank",
        "wind-turbine", "water-turbine", "electric-motor", "cupronickel-wall",
        "cupronickel-wall-large", "small-thruster", "infi-heater", "infi-cooler", "infi-torque",
        "neodymium-stator"),

    end("end", Color.gray,
        "terminal-crucible", "end-forge", "end-game", "tenmeikiri", "enigma", "void-vessel",
        "chronos", "opticaecus", "devourer", "oppression", "apocalypse", "ravager", "desolation",
        "thalassophobia");

    /** 全部派系 (PU132 {@code Faction.all})。 */
    public static final Faction[] all = values();

    /** 内部名 (同时是 bundle 键 {@code faction.<name>})。 */
    public final String name;
    /** 本地化显示名 (由 {@link #init()} 从 bundle 填充, 自带颜色标记)。 */
    public String localizedName;
    /** 主题色 (用于说明里的派系标记 / 派系 UI)。 */
    public final Color color;

    /** PU132 生成表里的内容名 (不含本模组改名后的别名)。 */
    public final String[] contents;

    Faction(String name, Color color, String... contents){
        this.name = name;
        this.color = color.cpy();
        this.contents = contents;
    }

    /** 从 bundle 读取本地化名 (headless 下跳过)。 */
    public static void init(){
        if(mindustry.Vars.headless) return;
        for(Faction faction : all){
            faction.localizedName = Core.bundle.format("faction." + faction.name, faction.color);
        }
    }

    @Override
    public String toString(){
        return localizedName == null ? name : localizedName;
    }
}
