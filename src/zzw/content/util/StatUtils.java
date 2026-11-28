package zzw.content.util;

import arc.graphics.Color;
import arc.util.Strings;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatCat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValue;
import mindustry.world.meta.Stats;

import static mindustry.Vars.iconXLarge;

/**
 * 方块统计工具 (PU 工厂移植辅助)
 *
 * <p>v158 原版输出物品速率用 autoFixed(v, 3) 最多显示 3 位小数
 * (如致密熔炉 1.299/秒), 用户要求四舍五入到最多 2 位小数。
 * 原版格式化在 {@code StatValues.displayItem} 内部写死, 无法按方块定制,
 * 这里提供替换用的自定义 StatValue (显示格式与原版一致, 仅小数位数不同)。</p>
 */
public class StatUtils{

    /** 灵魂承载机制的独立面板条目 (避免和原生能力列表挤在同一行) */
    public static final Stat soulStat = new Stat("soulCapacity", StatCat.function);
    /** 灵魂炮台附加状态效果的独立面板条目 */
    public static final Stat soulStatusStat = new Stat("soulStatus", StatCat.function);

    /**
     * 替换工厂的输出物品统计为 2 位小数版本.
     * <p>在 {@code setStats()} 中调用 {@code super.setStats()} 之后调用:
     * 先移除原版自动添加的 3 位小数输出统计, 再以 2 位小数重新添加。</p>
     */
    public static void roundOutputStats(GenericCrafter block){
        block.stats.remove(Stat.output);

        if(block.outputItems != null && block.outputItems.length > 0){
            GenericCrafter c = block;
            block.stats.add(Stat.output, table -> {
                for(ItemStack s : c.outputItems){
                    table.image(s.item.uiIcon).size(iconXLarge).padRight(4).right();
                    table.add(s.item.localizedName + "\n[lightgray]" + Strings.autoFixed(s.amount / (c.craftTime / 60f), 2) + StatUnit.perSecond.localized()).padLeft(2).padRight(5).style(Styles.outlineLabel);
                }
            });
        }
    }

    /**
     * 追加一个统一样式的信息说明框 (深灰面板 + 强调色标题 + 自动换行正文)。
     *
     * <p>用于替换散落的纯文本 {@code Stat.abilities} 行, 让不同单位/方块的面板
     * 在字号、间距和宽度上保持一致。{@code lines} 中为 null 的条目会被跳过。</p>
     *
     * @param title 标题 (可为 null, 为空则不显示标题行)
     * @param lines 正文行, 自动换行到统一宽度
     */
    public static void addInfoBox(Stats stats, Stat stat, String title, String... lines){
        stats.add(stat, (StatValue)(table) -> {
            table.table(Styles.grayPanel, box -> {
                box.left().defaults().left();
                if(title != null && !title.isEmpty()){
                    box.add(title).color(Pal.accent);
                    if(lines.length > 0) box.row();
                }
                for(int i = 0; i < lines.length; i++){
                    String line = lines[i];
                    if(line == null || line.isEmpty()) continue;
                    box.add(line).color(Color.lightGray).width(320f).wrap();
                    if(i < lines.length - 1) box.row();
                }
            }).pad(8f).growX();
        });
    }
}
