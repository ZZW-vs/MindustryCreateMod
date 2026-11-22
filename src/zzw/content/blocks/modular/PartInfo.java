package zzw.content.blocks.modular;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.scene.ui.layout.Table;
import arc.struct.OrderedMap;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.type.ItemStack;
import zzw.content.util.GraphicUtils;

/**
 * 模块化方块零件定义 (PU132 younggamExperimental.PartInfo 移植)。
 *
 * <p>描述一个可放置零件的贴图区域 (tx/ty/tw/th 为零件图集坐标)、造价、
 * 连接规则与属性列表, 供蓝图编辑 UI 与方块运行时读取。</p>
 */
public class PartInfo{
    public final String name, desc;
    public final PartType category;
    public final int tx, ty, tw, th;
    public final boolean cannotPlace, isRoot;
    public final Point2 prePlace;
    public final ItemStack[] cost;
    public final byte[] connectOut, connectIn;
    public final OrderedMap<PartStatType, PartStat> stats = new OrderedMap<>(12);
    public TextureRegion sprite, sprite2, texRegion;
    /** 零件轮廓贴图 (part-*-outline), 用于把零件画成有体积的结构件而不是平贴图标 */
    public TextureRegion outline;
    /** 零件图标文件名 (不含 part- 前缀), 由方块 load() 映射成图集贴图 */
    public String icon;
    final Seq<ConnectData> connInList = new Seq<>(), connOutList = new Seq<>();
    int id;

    public PartInfo(String name, String desc, PartType category, int tx, int ty, int tw, int th, boolean cannotPlace, boolean isRoot, Point2 prePlace, ItemStack[] cost, byte[] connectOut, byte[] connectIn, PartStat... stats){
        this.name = name;
        this.desc = desc;
        this.category = category;
        this.tx = tx;
        this.ty = ty;
        this.tw = tw;
        this.th = th;
        this.cannotPlace = cannotPlace;
        this.isRoot = isRoot;
        this.prePlace = prePlace;
        this.cost = cost;
        this.connectOut = connectOut;
        this.connectIn = connectIn;
        for(var i : stats) this.stats.put(i.category, i);
    }

    public PartInfo(String name, String desc, PartType category, int tx, int ty, int tw, int th, ItemStack[] cost, byte[] connectOut, byte[] connectIn, PartStat... stats){
        this(name, desc, category, tx, ty, tw, th, false, false, null, cost, connectOut, connectIn, stats);
    }

    /** 预计算每个零件的进出连接点坐标 (Pu132 原逻辑只在首次调用时填充)。 */
    public static void preCalcConnection(PartInfo[] partsConfig){
        for(int i = 0, len = partsConfig.length; i < len; i++){
            PartInfo pInfo = partsConfig[i];
            if(pInfo.connInList.isEmpty()){
                for(int j = 0, iLen = pInfo.connectIn.length; j < iLen; j++){
                    if(pInfo.connectIn[j] != 0) pInfo.connInList.add(ConnectData.getConnectSidePos(j, pInfo.tw, pInfo.th).id(pInfo.connectIn[j]));
                }
            }
            if(pInfo.connOutList.isEmpty()){
                for(int j = 0, iLen = pInfo.connectOut.length; j < iLen; j++){
                    if(pInfo.connectOut[j] != 0) pInfo.connOutList.add(ConnectData.getConnectSidePos(j, pInfo.tw, pInfo.th).id(pInfo.connectOut[j]));
                }
            }
        }
    }

    /** 属性面板里展示的属性 (按展示优先级排列), 其余属性只在零件详情里显示。 */
    static final PartStatType[] shownStats = {
        PartStatType.hp, PartStatType.baseDmg, PartStatType.baseSpeed, PartStatType.magazine,
        PartStatType.shots, PartStatType.spread, PartStatType.reloadMultiplier, PartStatType.rangeinc,
        PartStatType.heat, PartStatType.radiate, PartStatType.support, PartStatType.mass, PartStatType.shaftSpd,
        PartStatType.damage, PartStatType.ammoType, PartStatType.mod
    };

    /**
     * 安全取文案: 缺 key 时回退到给定文本。
     *
     * <p>Mindustry 对缺失的本地化 key 会渲染成 {@code ???key???},
     * 零件分类/属性名这类动态拼接的 key 一旦漏配就会在界面上露出来, 所以统一走这里兜底。</p>
     */
    public static String text(String key, String fallback){
        String s = Core.bundle.getOrNull(key);
        return s == null || s.isEmpty() ? fallback : s;
    }

    /**
     * 生成紧凑的零件属性表 (按分类分组、双列排布), 用于方块信息面板。
     *
     * <p>排布约定: 每个分类一行标题 (图标 + 名称), 分类内每个零件一行,
     * 图标 / 名称 / 数值三列定宽对齐, 两列并排以控制面板高度。</p>
     */
    public static void addStatsTable(Table table, PartType[] categories, Iterable<PartInfo> parts){
        table.left().defaults().left();
        boolean firstCat = true;
        for(PartType cat : categories){
            Seq<PartInfo> list = new Seq<>();
            for(PartInfo p : parts) if(p.category == cat) list.add(p);
            if(list.isEmpty()) continue;

            // 分组标题
            table.row();
            table.table(head -> {
                head.left();
                if(cat.region != null && cat.region.found()) head.image(cat.region).size(20f).padRight(5f);
                head.add("[accent]" + text("part.category." + cat.name(), cat.name())).left();
            }).left().padTop(firstCat ? 0f : 6f).padBottom(2f);

            // 分类内零件: 两列, 每列 图标 | 名称(定宽) | 数值
            for(int i = 0; i < list.size; i += 2){
                final int start = i;
                table.row();
                table.table(row -> {
                    row.left();
                    for(int k = start; k < Math.min(start + 2, list.size); k++){
                        PartInfo p = list.get(k);
                        row.table(cell -> {
                            cell.left();
                            if(p.texRegion != null && p.texRegion.found()) cell.image(p.texRegion).size(18f).padRight(3f);
                            cell.add(p.name).width(74f).left().padRight(4f);
                            cell.add("[lightgray]" + summary(p)).left();
                        }).left().padRight(14f);
                    }
                }).left().padBottom(1f);
            }
            firstCat = false;
        }
    }

    /** 单个零件的关键属性摘要, 例如 "生命40 支撑1 质量3"。 */
    static String summary(PartInfo p){
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for(PartStatType type : shownStats){
            PartStat s = p.stats.get(type);
            if(s == null) continue;
            // 每个零件最多 4 项, 保证一行放得下
            if(shown == 4) break;
            if(sb.length() > 0) sb.append(' ');
            sb.append(text(type.shortName, type.name()));
            sb.append(valueText(s.value));
            shown++;
        }
        return sb.toString();
    }

    /** 属性值转文本: 整数不带小数点, 字符串值 (弹种/改造) 走本地化, 不暴露内部枚举名。 */
    public static String valueText(Object v){
        if(v instanceof Float f){
            if(Mathf.within(f, Math.round(f), 0.05f)) return Integer.toString(Math.round(f));
            return Strings.fixed(f, f < 1f ? 2 : 1);
        }
        if(v instanceof Integer i) return Integer.toString(i);
        if(v instanceof Boolean b) return b ? "是" : "否";
        String s = String.valueOf(v);
        return text("stat.unity.value." + s, s);
    }

    /** 从零件图集中切出每个零件对应的贴图区域。 */
    public static void assignPartSprites(PartInfo[] partsConfig, TextureRegion partsSprite, int spriteW, int spriteH){
        for(int i = 0, len = partsConfig.length; i < len; i++){
            PartInfo pinfo = partsConfig[i];
            pinfo.id = i;
            // 已经由方块自身指定贴图 (如模块化炮台直接使用 part-*.png) 时不再从图集切片
            if(pinfo.texRegion == null){
                pinfo.texRegion = GraphicUtils.getRegionRect(partsSprite, pinfo.tx, pinfo.ty, pinfo.tw, pinfo.th, spriteW, spriteH);
            }
        }
    }
}