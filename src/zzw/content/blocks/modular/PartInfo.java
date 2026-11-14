package zzw.content.blocks.modular;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
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
        PartStatType.heat, PartStatType.support, PartStatType.mass, PartStatType.shaftSpd,
        PartStatType.damage, PartStatType.ammoType, PartStatType.mod
    };

    /**
     * 生成紧凑的零件属性表 (按分类分组、双列排布), 用于方块信息面板。
     *
     * <p>原版逐条平铺会让面板超出一屏, 这里每个零件压成一行、每行两个零件,
     * 且只显示参与实际运算的关键属性。</p>
     */
    public static void addStatsTable(Table table, PartType[] categories, Iterable<PartInfo> parts){
        table.left().defaults().left();
        for(PartType cat : categories){
            boolean has = false;
            for(PartInfo p : parts) if(p.category == cat) has = true;
            if(!has) continue;

            table.row();
            table.table(head -> {
                head.left();
                if(cat.region != null && cat.region.found()) head.image(cat.region).size(20f).padRight(4f);
                head.add("[accent]" + Core.bundle.get("part.category." + cat.name())).left();
            }).left().padBottom(1f).row();

            int n = 0;
            Table row = null;
            for(PartInfo p : parts){
                if(p.category != cat) continue;
                if(n % 2 == 0){
                    table.row();
                    row = new Table();
                    table.add(row).left().padBottom(1f);
                }
                row.table(cell -> {
                    cell.left();
                    if(p.texRegion != null && p.texRegion.found()) cell.image(p.texRegion).size(18f).padRight(3f);
                    cell.add(p.name + "  [lightgray]" + summary(p)).left().padRight(16f);
                }).left();
                n++;
            }
        }
    }

    /** 单个零件的关键属性摘要, 例如 "生命+40 支撑1 质量3"。 */
    static String summary(PartInfo p){
        StringBuilder sb = new StringBuilder();
        for(PartStatType type : shownStats){
            PartStat s = p.stats.get(type);
            if(s == null) continue;
            if(sb.length() > 0) sb.append(' ');
            sb.append(Core.bundle.get("stat.unity.s." + type.name()));
            Object v = s.value;
            if(v instanceof Float f) sb.append(Strings.fixed(f, 1));
            else if(v instanceof Boolean b) sb.append(b ? "√" : "×");
            else sb.append(v);
        }
        return sb.toString();
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