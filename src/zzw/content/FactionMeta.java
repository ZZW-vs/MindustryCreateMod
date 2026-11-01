package zzw.content;

import arc.Core;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.ctype.MappableContent;
import mindustry.ctype.UnlockableContent;
import mindustry.mod.Mod;

/**
 * 派系元数据 (移植自 PU132 由 {@code FactionProcessor} 生成的 {@code unity.gen.FactionMeta})。
 *
 * <p>PU132 用注解处理器 {@code @FactionDef} 扫描内容字段生成 "内容 → 派系" 的静态表;
 * 本模组没有注解处理器, 改为<b>运行时按内容名匹配</b> —— 本模组内容名与 PU 一一对应,
 * 因此直接照抄 {@link Faction#contents} 的名单即可, 改名的内容在 {@link #registerAliases()} 补登。</p>
 *
 * <p>功能:</p>
 * <ul>
 *   <li>{@link #map(Object)}: 取某个内容 (方块/单位/星球/区块) 所属的派系;</li>
 *   <li>{@link #put(Object, Faction)}: 手动登记, 并在 {@link UnlockableContent} 的说明末尾
 *       追加一行 "派系: ◆名字" (颜色取派系主题色) —— 与 PU132 完全一致;</li>
 *   <li>{@link #getByFaction(Faction, Class)} / {@link #getByCtype(ContentType)}: 按派系筛选内容。</li>
 * </ul>
 *
 * <p>只处理本模组自己的内容 ({@code content.minfo.mod == owner}), 不会误标原版或其它模组。</p>
 */
public final class FactionMeta{
    /** 内容对象 → 派系 (PU132 {@code FactionMeta.map})。 */
    private static final ObjectMap<Object, Faction> map = new ObjectMap<>();
    /** 内容名 (已去掉 "模组名-" 前缀) → 派系, 用于初始化时的按名匹配。 */
    private static final ObjectMap<String, Faction> nameMap = new ObjectMap<>();
    /** 防止重复初始化 (重复执行会把说明里的派系行追加两遍)。 */
    private static boolean inited;

    private FactionMeta(){
    }

    /** 取内容所属派系 (未登记返回 null)。 */
    public static Faction map(Object content){
        return content == null ? null : map.get(content);
    }

    /** 按内容名 (不含模组前缀) 查询派系。 */
    public static Faction byName(String name){
        return name == null ? null : nameMap.get(name);
    }

    /**
     * 登记内容 → 派系, 并在说明末尾追加派系标记 (PU132 {@code FactionMeta.put})。
     *
     * @param content 内容对象 (一般是 {@link UnlockableContent})
     * @param faction 所属派系
     */
    public static void put(Object content, Faction faction){
        if(content == null || faction == null) return;

        map.put(content, faction);

        if(content instanceof UnlockableContent unlockable && faction.localizedName != null){
            String label = Core.bundle.getOrNull("faction.name");
            if(label == null || label.isEmpty()) label = "Faction";
            String tag = "[gray]" + label + ":[] " + faction.localizedName;

            // 说明可能为 null (bundle 里没写 description), 不能直接 +=
            if(unlockable.description == null || unlockable.description.isEmpty()){
                unlockable.description = tag;
            }else if(!unlockable.description.contains(tag)){
                unlockable.description += "\n" + tag;
            }
        }
    }

    /** 按派系 + 类型筛选 (PU132 {@code FactionMeta.getByFaction})。 */
    public static <T> Seq<T> getByFaction(Faction faction, Class<T> type){
        Seq<T> contents = new Seq<>();
        for(Object o : map.keys()){
            if(map.get(o) == faction && type.isAssignableFrom(o.getClass())){
                contents.add(type.cast(o));
            }
        }
        return contents;
    }

    /** 按内容类型筛选所有已登记内容 (PU132 {@code FactionMeta.getByCtype})。 */
    @SuppressWarnings("unchecked")
    public static <T extends Content> Seq<T> getByCtype(ContentType ctype){
        Seq<T> contents = new Seq<>();
        for(Object o : map.keys()){
            if(o instanceof Content c && c.getContentType() == ctype){
                contents.add((T)c);
            }
        }
        return contents;
    }

    /**
     * 初始化派系元数据 (PU132 {@code FactionMeta.init})。
     *
     * <p>在所有内容加载完之后调用: 遍历本模组的方块/单位/星球/区块, 按内容名匹配派系,
     * 并把派系标记追加到说明里。未匹配的内容 (本模组自创的板材/南瓜/展示等) 不标记。</p>
     *
     * @param owner 本模组实例 (用于确认内容归属)
     */
    public static void init(Mod owner){
        if(inited) return;
        inited = true;

        for(Faction faction : Faction.all){
            for(String name : faction.contents){
                nameMap.put(name, faction);
            }
        }
        registerAliases();

        for(Content c : Vars.content.blocks()) assign(c, owner);
        for(Content c : Vars.content.units()) assign(c, owner);
        for(Content c : Vars.content.planets()) assign(c, owner);
        for(Content c : Vars.content.sectors()) assign(c, owner);
    }

    /** 单个内容按名匹配派系。 */
    private static void assign(Content content, Mod owner){
        if(!(content instanceof UnlockableContent)) return;
        if(content.minfo == null || content.minfo.mod == null || content.minfo.mod.main != owner) return;

        Faction faction = nameMap.get(localName((UnlockableContent)content));
        if(faction != null) put(content, faction);
    }

    /** 去掉内容名的 "模组名-" 前缀 (MappableContent 构造时由 ContentLoader 自动加)。 */
    private static String localName(MappableContent content){
        String prefix = content.minfo.mod.name + "-";
        return content.name.startsWith(prefix) ? content.name.substring(prefix.length()) : content.name;
    }

    /**
     * ★ 别名表: 本模组与 PU132 生成表用不同命名的内容 (OreBlock 去掉了 ore- 前缀、炮台改了名字等)。
     */
    private static void registerAliases(){
        // 矿石方块 (PU: ore-umbrium / ore-luminum / ore-imberium / ore-nickel / ore-monolite)
        alias("umbrium", Faction.dark);
        alias("luminum", Faction.light);
        alias("imberium", Faction.imber);
        alias("nickel", Faction.youngcha);
        alias("monolite", Faction.monolith);

        // 方块改名
        alias("endgame", Faction.end);                   // PU: end-game
        alias("the-cube", Faction.advance);              // PU: cube
        alias("arc-caster", Faction.advance);            // PU: caster
        alias("arc-storm", Faction.advance);             // PU: storm
        alias("blue-eclipse", Faction.advance);          // PU: eclipse
        alias("archaic-sharpslate", Faction.monolith);   // PU: arch-sharpslate
        alias("archaic-energy", Faction.monolith);       // PU: arch-energy

        // 单位改名
        alias("devourer-of-eldrich-gods", Faction.end);  // PU: devourer
    }

    private static void alias(String name, Faction faction){
        nameMap.put(name, faction);
    }
}
