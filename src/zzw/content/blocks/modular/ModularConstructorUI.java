package zzw.content.blocks.modular;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.Button;
import arc.scene.ui.Button.ButtonStyle;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.GridMap;
import arc.struct.IntSeq;
import arc.struct.OrderedMap;
import arc.struct.OrderedSet;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import zzw.content.graphics.UnityPal;

import static mindustry.Vars.*;

/**
 * 模块化构造蓝图编辑 UI (PU132 younggamExperimental.ModularConstructorUI 移植)。
 *
 * <p>Chopper 等模块化方块的蓝图编辑面板: 左侧按类别选择零件, 中间在网格上摆放,
 * 右侧统计总造价, 结果通过 {@link #getPackedSave()} 打包成字符串保存到方块配置。</p>
 */
public class ModularConstructorUI extends Element{
    static final Color[] colorPorts = new Color[100];
    static ImageButton prevChecked;
    static PartType currentCat;
    Point2 hover;
    final Seq<PartPlaceObj> partList = new Seq<>(), rootList = new Seq<>();
    final GridMap<PartPlaceObj> grid = new GridMap<>();
    Runnable onTileAction;
    TextureRegion partsSprite;
    PartInfo partsSelect;
    KeyCode dragButton;
    float prefHeight = 100f, costAccum = 1f, costAccumRate = 0.2f;
    int gridW = 1, gridH = 1;
    boolean isClickedRN;

    static{
        // 为每个连接端口随机生成一个辨识色
        for(int i = 0; i < 100; i++) colorPorts[i] = Color.HSVtoRGB(360f * Mathf.random(), 100f * Mathf.random(0.3f, 1f), 100f * Mathf.random(0.9f, 1f), 1f);
    }

    /** 创建蓝图编辑元素 (未挂载到面板时使用, 供脚本/测试)。 */
    public static ModularConstructorUI getModularConstructorUI(float pHeight, TextureRegion partsSprite, PartInfo[] partsConfig, IntSeq preConfig, int maxW, int maxH, float cstacc){
        ModularConstructorUI pp = new ModularConstructorUI();
        pp.init();
        pp.prefHeight = pHeight;
        pp.partsSprite = partsSprite;
        pp.gridW = maxW;
        pp.gridH = maxH;
        pp.costAccum = cstacc;
        if(preConfig.isEmpty()){
            for(var pinfo : partsConfig){
                if(pinfo.prePlace != null) pp.placeTile(pinfo, pinfo.prePlace.x, pinfo.prePlace.y);
            }
        }else pp.loadSave(preConfig, partsConfig);
        return pp;
    }

    /**
     * 把蓝图编辑 UI 挂载到指定 Table 上 (方块 buildConfiguration 调用)。
     *
     * <p>左侧为零件分类 + 零件列表滚动面板, 中间为编辑网格, 右侧为总造价统计。</p>
     */
    public static ModularConstructorUI applyModularConstructorUI(Table table, TextureRegion partsSprite, int spriteW, int spriteH, PartInfo[] partsConfig, int maxW, int maxH, IntSeq preConfig, PartType[] categories, float cstaccum){
        PartInfo.preCalcConnection(partsConfig);
        PartInfo.assignPartSprites(partsConfig, partsSprite, spriteW, spriteH);
        ModularConstructorUI modElement = getModularConstructorUI(400f, partsSprite, partsConfig, preConfig, maxW, maxH, cstaccum);
        currentCat = null;
        Cons<Table> partSelectCons = scrollTable -> {
            float costInc = modElement.costAccum;
            scrollTable.clearChildren();
            scrollTable.top().left();
            // 自动选中当前分类的第一个可放置零件, 避免"点了网格却没反应"
            if(modElement.partsSelect == null || modElement.partsSelect.cannotPlace || modElement.partsSelect.category != currentCat){
                for(var pinfo : partsConfig){
                    if(!pinfo.cannotPlace && pinfo.category == currentCat){
                        modElement.partsSelect = pinfo;
                        break;
                    }
                }
            }
            for(var pinfo : partsConfig){
                if(pinfo.cannotPlace || pinfo.category != currentCat) continue;
                scrollTable.row();
                addConsButton(scrollTable, butt -> {
                    butt.top().left().margin(10f).defaults().left().top();
                    butt.add(pinfo.name).width(170f).left().padBottom(2f).row();
                    butt.table(topTable -> {
                        topTable.add(new BorderImage(pinfo.texRegion, 2f)).size(36f).padRight(4f);
                        topTable.button(Tex.whiteui, Styles.clearNonei, 50f, () -> {
                            displayPartInfo(pinfo);
                        }).size(50f).get().getStyle().imageUp = Icon.infoSmall;
                    }).left().padBottom(4f).row();
                    butt.add("[accent]" + Core.bundle.get("bar.blueprint.cost", "Cost")).left().padBottom(2f).row();
                    butt.table(botTable -> addCostGrid(botTable, pinfo.cost, costInc, 2)).left();
                }, Styles.defaultb, () -> modElement.partsSelect = pinfo).minWidth(150f).padBottom(8f);
            }
        };
        Table parts = new Table();
        Runnable rebuildParts = () -> partSelectCons.get(parts);
        ScrollPane pane = new ScrollPane(parts, Styles.defaultPane);
        prevChecked = null;
        Table catTable = new Table();
        catTable.margin(12f).top().left();
        ImageButton firstCat = null;
        for(var i : categories){
            // 跳过没有任何可放置零件的空分类 (PU132 原版 saw 分类即为空, 点击只会得到空白列表)
            boolean usable = false;
            for(var p : partsConfig){
                if(p.category == i && !p.cannotPlace){
                    usable = true;
                    break;
                }
            }
            if(!usable) continue;
            ImageButton catButt = new ImageButton(i.region, Styles.clearTogglei);
            catButt.clicked(() -> {
                currentCat = i;
                rebuildParts.run();
                catButt.setChecked(true);
                if(prevChecked != null) prevChecked.setChecked(false);
                prevChecked = catButt;
            });
            catTable.add(catButt).tooltip(PartInfo.text("part.category." + i.name(), i.name()));
            if(firstCat == null){
                firstCat = catButt;
                currentCat = i;
                catButt.setChecked(true);
                prevChecked = catButt;
            }
        }
        rebuildParts.run();
        Table leftSide = new Table();
        leftSide.add(catTable).align(Align.left).row();
        leftSide.add(pane).minWidth(200f).maxHeight(400f).align(Align.top).get().setScrollingDisabled(true, false);

        Cons<Table> costCons = cstTable -> {
            cstTable.clearChildren();
            cstTable.add("[accent]" + Core.bundle.get("bar.blueprint.totalcost", "Total Cost")).padBottom(3f).left().row();
            cstTable.table(botTable -> {
                OrderedMap<Item, Integer> csTot = modElement.getTotalCost();
                int i = 0;
                for(var cost : csTot){
                    if(i % 2 == 0) botTable.row();
                    botTable.table(cell -> {
                        cell.left();
                        cell.image(cost.key.uiIcon).size(iconSmall).padRight(3f);
                        cell.add("[gray]" + cost.value).minWidth(26f).left();
                    }).width(70f).left();
                    i++;
                }
            }).left();
        };
        Table totals = new Table();
        Runnable rebuildTotals = () -> costCons.get(totals);
        table.add(leftSide).minWidth(150f).align(Align.top);
        table.add(modElement).size(750f, 400f);
        table.add(totals).minWidth(100f).maxHeight(400f).align(Align.top);
        modElement.onTileAction = () -> {
            rebuildParts.run();
            rebuildTotals.run();
        };
        rebuildTotals.run();
        return modElement;
    }

    /** 配方网格: 图标 + 数量按固定列宽对齐, 超过 columns 个自动换行。 */
    static void addCostGrid(Table table, ItemStack[] costs, float costInc, int columns){
        int i = 0;
        for(var cst : costs){
            if(i % columns == 0) table.row();
            table.table(cell -> {
                cell.left();
                cell.image(cst.item.uiIcon).size(iconSmall).padRight(3f);
                cell.add("[gray]" + Mathf.floor(cst.amount * costInc)).minWidth(26f).left();
            }).width(70f).left();
            i++;
        }
    }

    static Cell<Button> addConsButton(Table table, Cons<Button> consFunc, ButtonStyle style, Runnable runnable){
        Button button = new Button(style);
        button.clearChildren();
        button.clicked(runnable);
        consFunc.get(button);
        return table.add(button);
    }

    static void displayPartInfo(PartInfo part){
        BaseDialog dialog = new BaseDialog(part.name);
        dialog.setFillParent(false);
        Table cont = dialog.cont;
        cont.margin(12f).top().left();
        cont.add(part.name).left().padBottom(3f).row();
        cont.add("[lightgray]" + part.desc).width(480f).wrap().left().padBottom(8f).row();
        cont.add("[accent]" + Core.bundle.get("bar.blueprint.partstats", "属性")).left().padBottom(3f).row();
        // 名称定宽 + 数值左对齐, 保证多行属性整齐
        cont.table(t -> {
            for(var stat : part.stats){
                t.row();
                t.add("[lightgray]" + PartInfo.text(stat.key.name, stat.key.name())).width(120f).left();
                t.add("[white]" + PartInfo.valueText(stat.value.value)).left();
            }
        }).left();
        dialog.buttons.button("@ok", dialog::hide).size(130f, 60f);
        dialog.show();
    }

    @Override
    public void draw(){
        float amx = x + width * 0.5f;
        float amy = y + height * 0.5f;
        int gw = gridW * 32;
        int gh = gridH * 32;
        float gamx = amx - gw * 0.5f;
        float gamy = amy - gh * 0.5f;
        Draw.color(UnityPal.bgCol);
        Fill.rect(amx, amy, width, height);
        Draw.color(UnityPal.blueprintCol);
        Fill.rect(amx, amy, gw, gh);
        Draw.color();
        for(var p : partList){
            if(!p.valid){
                Draw.color(p.flash % 10 < 5 ? Color.pink : Color.white);
                p.flash++;
            }else Draw.color();
            Draw.rect(p.part.texRegion, p.x * 32f + gamx + p.part.tw * 16f, p.y * 32f + gamy + p.part.th * 16f, p.part.tw * 32f, p.part.th * 32f);
            drawOpenConnectionPorts(p.part, p.x, p.y, gamx, gamy);
        }
        Draw.color(Color.black);
        Fill.rect(x + 20f, y + 20f, 40f, 40f);
        Draw.color();
        if(partsSelect != null){
            Draw.rect(partsSelect.texRegion, x + 20f, y + 20f, 32f, 32f);
            if(hover != null){
                Draw.color(canPlace(partsSelect, hover.x, hover.y) ? Color.white : Color.red, 0.3f);
                Draw.rect(partsSelect.texRegion, hover.x * 32f + gamx + partsSelect.tw * 16f, hover.y * 32f + gamy + partsSelect.th * 16f, partsSelect.tw * 32f, partsSelect.th * 32f);
                drawOpenConnectionPorts(partsSelect, hover.x, hover.y, gamx, gamy);
            }
        }
    }

    void drawOpenConnectionPorts(PartInfo ps, int x, int y, float offx, float offy){
        for(var conout : ps.connInList){
            int opcx = x + conout.x + conout.dir.x;
            int opcy = y + conout.y + conout.dir.y;
            if(getPartAt(opcx, opcy) == null){
                float brcx = (opcx - conout.dir.x * 0.5f + 0.5f) * 32f + offx;
                float brcy = (opcy - conout.dir.y * 0.5f + 0.5f) * 32f + offy;
                Draw.color(Color.black);
                Fill.square(brcx, brcy, 6f, 45f);
                Draw.color(colorPorts[conout.id - 1]);
                Fill.square(brcx, brcy, 2f, 45f);
                Draw.color();
            }
        }
        for(var conout : ps.connOutList){
            int opcx = x + conout.x + conout.dir.x;
            int opcy = y + conout.y + conout.dir.y;
            if(getPartAt(opcx, opcy) == null){
                float brcx = (opcx - conout.dir.x * 0.5f + 0.5f) * 32f + offx;
                float brcy = (opcy - conout.dir.y * 0.5f + 0.5f) * 32f + offy;
                Draw.color(Color.black);
                Fill.square(brcx, brcy, 6f, 45f);
                Draw.color(colorPorts[conout.id - 1]);
                Lines.stroke(2f);
                Lines.poly(brcx, brcy, 4, 3f, 0f);
                Draw.color();
            }
        }
    }

    @Override
    public float getPrefHeight(){
        return prefHeight;
    }

    boolean inBounds(PartInfo partType, int x, int y){
        return partType != null && inBoundsRect(x, y, partType.tw, partType.th);
    }

    boolean inBoundsRect(int x, int y, int w, int h){
        return !(x < 0 || x + w > gridW || y < 0 || y + h > gridH);
    }

    /**
     * 是否可以在指定格放置零件。
     *
     * <p>只校验边界与占用, 不强制要求与已有零件连通 —— 否则网格边角格 (与中心根零件
     * 斜对角、无法正交连通) 将永远放不下零件。连接端口仍会绘制出来作为拼装提示。</p>
     */
    boolean canPlace(PartInfo partType, int x, int y){
        return canPlaceConn(partType, x, y, false);
    }

    boolean canPlaceConn(PartInfo partType, int x, int y, boolean chkConnection){
        if(!inBounds(partType, x, y)) return false;
        for(int px = 0, lenX = partType.tw; px < lenX; px++){
            for(int py = 0, lenY = partType.th; py < lenY; py++){
                if(getPartAt(x + px, y + py) != null) return false;
            }
        }
        if(chkConnection){
            boolean hasConnection = partType.connInList.isEmpty();
            for(var i : partType.connInList){
                PartPlaceObj fromPart = getPartAt(x + i.x + i.dir.x, y + i.y + i.dir.y);
                if(fromPart != null) hasConnection |= partCanConnectOut(fromPart, i.x + x, i.y + y, i.id);
            }
            if(!hasConnection){
                for(var i : partType.connOutList){
                    PartPlaceObj fromPart = getPartAt(x + i.x + i.dir.x, y + i.y + i.dir.y);
                    if(fromPart != null) hasConnection |= partCanConnectIn(fromPart, i.x + x, i.y + y, i.id);
                }
            }
            return hasConnection;
        }
        return true;
    }

    void rebuildFromRoots(){
        // 允许自由摆放: 所有已放置零件都视为有效并保存
        for(int i = 0, len = partList.size; i < len; i++) partList.get(i).valid = true;
    }

    boolean removeTile(PartPlaceObj part){
        if(part == null) return false;
        PartInfo prt = part.part;
        if(prt.isRoot) return false;
        for(var i : part.parents){
            i.children.remove(part);
        }
        int lenX = prt.tw;
        int lenY = prt.th;
        for(int px = 0; px < lenX; px++){
            for(int py = 0; py < lenY; py++) grid.remove(part.x + px, part.y + py);
        }
        partList.remove(part);
        rebuildFromRoots();
        costAccum -= costAccumRate * lenX * lenY;
        return true;
    }

    boolean placeTile(PartInfo partType, int x, int y){
        if(!canPlace(partType, x, y)) return false;
        placeTileDirect(partType, x, y);
        return true;
    }

    boolean placeTileDirect(PartInfo partType, int x, int y){
        PartPlaceObj partPlaceObj = new PartPlaceObj(x, y, partType);
        for(var i : partType.connInList){
            PartPlaceObj fromPart = getPartAt(x + i.x + i.dir.x, y + i.y + i.dir.y);
            if(fromPart != null && partCanConnectOut(fromPart, i.x + x, i.y + y, i.id)){
                partPlaceObj.parents.add(fromPart);
                fromPart.children.add(partPlaceObj);
            }
        }
        for(var i : partType.connOutList){
            PartPlaceObj fromPart = getPartAt(x + i.x + i.dir.x, y + i.y + i.dir.y);
            if(fromPart != null && partCanConnectOut(fromPart, i.x + x, i.y + y, i.id)){
                partPlaceObj.children.add(fromPart);
                fromPart.parents.add(partPlaceObj);
            }
        }
        int xLen = partType.tw;
        int yLen = partType.th;
        for(int px = 0; px < xLen; px++){
            for(int py = 0; py < yLen; py++) grid.put(x + px, y + py, partPlaceObj);
        }
        if(partType.isRoot) rootList.add(partPlaceObj);
        partList.add(partPlaceObj);
        rebuildFromRoots();
        costAccum += costAccumRate * xLen * yLen;
        return true;
    }

    void onIsClicked(InputEvent event, float x, float y, int point, KeyCode butt){
        isClickedRN = true;
        Point2 gPos = uiToGridPos(x, y);
        boolean success;
        if(butt == KeyCode.mouseRight) success = removeTile(getPartAt(gPos.x, gPos.y));
        else success = placeTile(partsSelect, gPos.x, gPos.y);
        dragButton = butt;
        if(onTileAction != null && success) onTileAction.run();
    }

    void onIsDragged(InputEvent event, float x, float y, int point){
        if(isClickedRN){
            Point2 gPos = uiToGridPos(x, y);
            boolean success;
            if(dragButton == KeyCode.mouseRight) success = removeTile(getPartAt(gPos.x, gPos.y));
            else success = placeTile(partsSelect, gPos.x, gPos.y);
            if(onTileAction != null && success) onTileAction.run();
        }
    }

    boolean onIsHovering(InputEvent event, float x, float y){
        if(x < 0f || x > width || y < 0f || y > height){
            hover = null;
            return false;
        }
        hover = uiToGridPos(x, y);
        return true;
    }

    void init(){
        addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                onIsClicked(event, x, y, pointer, button);
                return true;
            }

            @Override
            public boolean mouseMoved(InputEvent event, float x, float y){
                return onIsHovering(event, x, y);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                isClickedRN = false;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer){
                onIsDragged(event, x, y, pointer);
            }
        });
    }

    OrderedMap<Item, Integer> getTotalCost(){
        OrderedMap<Item, Integer> cst = new OrderedMap<>(partList.size);
        for(var i : partList){
            for(var p : i.part.cost){
                int cur = cst.get(p.item, 0);
                cst.put(p.item, cur + Mathf.floor(p.amount * (costAccum - costAccumRate)));
            }
        }
        return cst;
    }

    PartPlaceObj getPartAt(int x, int y){
        if(!inBoundsRect(x, y, 1, 1)) return null;
        return grid.get(x, y);
    }

    boolean partCanConnectOut(PartPlaceObj part, int x, int y, byte portId){
        for(var i : part.part.connOutList){
            if(i.id == portId && x == part.x + i.x + i.dir.x && y == part.y + i.y + i.dir.y) return true;
        }
        return false;
    }

    boolean partCanConnectIn(PartPlaceObj part, int x, int y, byte portId){
        for(var i : part.part.connInList){
            if(i.id == portId && x == part.x + i.x + i.dir.x && y == part.y + i.y + i.dir.y) return true;
        }
        return false;
    }

    Point2 uiToGridPos(float x, float y){
        int gw = gridW * 32;
        int gh = gridH * 32;
        float gamx = (width - gw) * 0.5f;
        float gamy = (height - gh) * 0.5f;
        return new Point2(Mathf.floor((x - gamx) / 32f), Mathf.floor((y - gamy) / 32f));
    }

    /** 把当前蓝图打包为字符串 (存进方块配置)。 */
    public String getPackedSave(){
        IntPacker packer = new IntPacker();
        for(int px = 0; px < gridW; px++){
            for(int py = 0; py < gridH; py++){
                PartPlaceObj p = getPartAt(px, py);
                if(p != null && p.x == px && p.y == py && p.valid) packer.add(p.part.id + 1);
                else packer.add(0);
            }
        }
        packer.end();
        return packer.toStringPack();
    }

    void loadSave(IntSeq array, PartInfo[] partList){
        for(int i = 0, len = array.size; i < len; i++){
            int temp = array.get(i);
            if(temp == 0) continue;
            int px = i / gridH;
            int py = i % gridH;
            // 蓝图允许自由摆放, 读取时只校验边界与占用, 不再要求与根件连通
            PartInfo part = partList[temp - 1];
            if(canPlaceConn(part, px, py, false)) placeTileDirect(part, px, py);
        }
    }

    static class PartPlaceObj{
        final PartInfo part;
        final int x, y;
        int flash;
        boolean valid;
        final OrderedSet<PartPlaceObj> parents = new OrderedSet<>(12), children = new OrderedSet<>(12);

        PartPlaceObj(int x, int y, PartInfo part){
            this.x = x;
            this.y = y;
            this.part = part;
        }
    }
}