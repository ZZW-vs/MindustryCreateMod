package zzw.map.objectives;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import zzw.map.cinematic.StoryNode;

import static mindustry.Vars.*;

/**
 * 资源收集目标 (PU132 ResourceAmountObj 移植): 玩家核心库存齐 {@link #items}
 * 里的所有物品即达成, 左侧会显示带渐变进度的资源面板。
 *
 * @author PU132 原作 (GlennFolker), 移植: zzw
 */
public class ResourceAmountObj extends Objective{
    /** HUD 面板的内层容器 (资源列表) */
    protected Table container;
    /** HUD 面板的外层容器 (ui.hudGroup.fill 创建的表), 移除时整块删除避免残留空表 */
    protected Table hudRoot;

    public ItemStack[] items = new ItemStack[0];
    public Team team;
    public Color from = Color.white;
    public Color to = Color.lime;

    public ResourceAmountObj(StoryNode node, String name, Cons<ResourceAmountObj> executor){
        super(node, name, executor);
        team = state.rules.defaultTeam;
    }

    @Override
    public void init(){
        super.init();

        // 幂等: 已创建过面板就不再重复创建 (剧情重新绑定时由 reset 先移除)
        if(headless || completed || hudRoot != null) return;
        ui.hudGroup.fill(table -> {
            hudRoot = table;
            table.name = name;

            table.actions(
                Actions.scaleTo(0f, 1f),
                Actions.visible(true),
                Actions.scaleTo(1f, 1f, 0.07f, Interp.pow3Out)
            );

            table.center().left();

            Cell<Table> cell = table.table(Tex.pane, t -> {
                container = t;

                ScrollPane pane = t.pane(Styles.defaultPane, cont -> {
                    cont.defaults().pad(4f);

                    for(int i = 0; i < items.length; i++){
                        if(i > 0) cont.row();

                        ItemStack item = items[i];
                        cont.table(hold -> {
                            hold.defaults().pad(4f);

                            hold.image(() -> item.item.uiIcon).size(iconMed);
                            hold.add().growX();
                            hold.label(() -> {
                                float amount = Math.min(count(item.item), item.amount);
                                return
                                    "[#" + Tmp.c1.set(from).lerp(to, amount / (float)item.amount).toString() + "]" + (int)amount +
                                    " []/ [accent]" + item.amount + "[]";
                            });
                        })
                            .height(40f)
                            .growX()
                            .left();
                    }
                })
                    .update(p -> {
                        if(p.hasScroll()){
                            Element result = Core.scene.hit(Core.input.mouseX(), Core.input.mouseY(), true);
                            if(result == null || !result.isDescendantOf(p)){
                                Core.scene.setScrollFocus(null);
                            }
                        }
                    })
                    .grow()
                    .pad(4f, 0f, 4f, 4f)
                    .get();

                pane.setScrollingDisabled(true, false);
                pane.setOverscroll(false, false);
            })
                .minSize(300f, 48f)
                .maxSize(300f, 156f);

            cell.visible(() -> ui.hudfrag.shown && node.valid() && container == cell.get());
        });
    }

    @Override
    public void update(){
        super.update();

        completed = true;
        for(ItemStack item : items){
            completed = count(item.item) >= item.amount;
            if(!completed) break;
        }
    }

    protected int count(Item item){
        CoreBuild core = state.teams.cores(team).firstOpt();
        if(core == null) return 0;

        return core.items.get(item);
    }

    @Override
    public void stop(){
        super.stop();

        // 完成时内层滑出, 随后连同外层容器一起移除
        if(container != null && hudRoot != null){
            Table pane = container;
            pane.actions(
                Actions.moveBy(-pane.getWidth(), 0f, 2f, Interp.pow3In),
                Actions.visible(false),
                Actions.run(this::removeHud)
            );
        }else{
            removeHud();
        }
    }

    @Override
    public void reset(){
        super.reset();

        // 剧情重新绑定 (切图/读档重进) 时立即移除旧面板, 防止 HUD 叠加
        removeHud();
    }

    /** 移除本目标在 HUD 上的整块面板 (外层 fill 表一并删除)。 */
    protected void removeHud(){
        container = null;

        if(hudRoot != null){
            hudRoot.remove();
            hudRoot = null;
        }
    }
}
