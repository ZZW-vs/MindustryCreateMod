package zzw.content;

import mindustry.entities.bullet.BulletType;
import mindustry.type.Item;
import mindustry.world.blocks.defense.turrets.ItemTurret;

import static mindustry.content.Blocks.arc;
import static mindustry.content.Blocks.cyclone;
import static mindustry.content.Blocks.duo;
import static mindustry.content.Blocks.foreshadow;
import static mindustry.content.Blocks.fuse;
import static mindustry.content.Blocks.hail;
import static mindustry.content.Blocks.lancer;
import static mindustry.content.Blocks.ripple;
import static mindustry.content.Blocks.salvo;
import static mindustry.content.Blocks.scatter;
import static mindustry.content.Blocks.scorch;
import static mindustry.content.Blocks.segment;
import static mindustry.content.Blocks.spectre;

/**
 * 原版炮台弹药扩展
 *
 * <p>向原版 {@link ItemTurret} 的弹药表注入模组物品, 使原版炮台可以装填本模组的
 * 材料作为弹药 —— 让模组物品在原版科技线上也有消费出口, 玩家不必换用模组炮台也能
 * 享受到模组材料的强度。</p>
 *
 * <p>★ 设计原则:
 * <ul>
 *   <li>子弹不是凭空造的, 而是<b>复制原版该炮台已有的最强弹药</b>, 再按系数强化
 *       (伤害与溅射伤害等比缩放, 其余字段 —— 弹速/命中特效/状态效果/穿透 —— 全部
 *       保持原版行为), 保证观感与手感与原版完全一致;</li>
 *   <li>注入时机在内容加载阶段 ({@code loadContent}), 早于原版 {@code init()} 的
 *       {@code limitRange()}, 因此新弹药的射程会被原版逻辑自动限制在炮台射程内;</li>
 *   <li>{@code init()} 的 {@code ConsumeItemFilter} 与统计面板都会自动把新弹药
 *       纳入, 无需额外处理;</li>
 *   <li>基础弹药缺失时 (原版未来改动) 自动回退为「当前伤害最高的原版弹药」,
 *       保证注入永不失败。</li>
 * </ul></p>
 *
 * <p>★ 消耗与强度: 每种弹药标注了单发消耗 (ammoMultiplier, 装填 1 个物品获得
 * 几发), 整体强度定位在原版最强弹药之上 ~15%~50%, 与其稀有度匹配:
 * 石头/钢 ≈ 原版石墨档 +15%~45%; 镍 ≈ 塑钢档 +35%; 感染原 ≈ 磷火档 +40%;
 * 灵岩 ≈ 浪涌合金档 +25%~50%; 迪里姆合金 ≈ 浪涌档 +25%~30%。</p>
 */
public class Z_VanillaAmmo{

    public static void load(){
        // ===== 基础档: 石头 / 钢 =====

        // 双管: 石墨弹 ×1.45 → 钢制弹 (早期即可用的强化选项)
        ammo(duo, Z_Items.steel, mindustry.content.Items.graphite, 1.45f, 2);

        // 冰雹: 石墨弹 ×1.15 → 石制炮弹 (便宜量大)
        ammo(hail, Z_Items.stone, mindustry.content.Items.graphite, 1.15f, 2);

        // 齐射: 石墨弹 ×1.35 → 钢制弹
        ammo(salvo, Z_Items.steel, mindustry.content.Items.graphite, 1.35f, 2);

        // 波纹(火炮): 石墨弹 ×1.15 → 大口径石弹
        ammo(ripple, Z_Items.stone, mindustry.content.Items.graphite, 1.15f, 3);

        // ===== 中档: 镍 / 感染原 =====

        // 散射(对空): 塑钢弹 ×1.4 → 镍制榴霰
        ammo(scatter, Z_Items.nickel, mindustry.content.Items.plastanium, 1.4f, 3);

        // 哨兵(对空点防): 塑钢弹 ×1.35 → 镍制弹
        ammo(segment, Z_Items.nickel, mindustry.content.Items.plastanium, 1.35f, 3);

        // 灼烧(火焰): 磷火弹 ×1.4 → 瘟疫燃烧弹 (感染原, 燃烧+状态与磷火一致)
        ammo(scorch, Z_Items.contagium, mindustry.content.Items.pyratite, 1.4f, 2);

        // ===== 高档: 灵岩 (能量系) =====

        // 电弧: 磷火电弧 ×1.5 → 灵岩电弧
        ammo(arc, Z_Items.monolite, mindustry.content.Items.pyratite, 1.5f, 2);

        // 齐射激光: 浪涌激光 ×1.3 → 灵岩激光
        ammo(lancer, Z_Items.monolite, mindustry.content.Items.surgeAlloy, 1.3f, 3);

        // 前兆(狙击): 浪涌弹 ×1.25 → 灵岩穿甲弹
        ammo(foreshadow, Z_Items.monolite, mindustry.content.Items.surgeAlloy, 1.25f, 5);

        // ===== 顶级: 迪里姆合金 =====

        // 融片(四管近距离): 石墨弹 ×1.3 → 迪里姆重弹
        ammo(fuse, Z_Items.dirium, mindustry.content.Items.graphite, 1.3f, 3);

        // 旋风: 浪涌弹 ×1.25 → 迪里姆弹
        ammo(cyclone, Z_Items.dirium, mindustry.content.Items.surgeAlloy, 1.25f, 3);

        // 幽灵(双管重炮): 石墨弹 ×1.3 → 迪里姆弹
        ammo(spectre, Z_Items.dirium, mindustry.content.Items.graphite, 1.3f, 3);
    }

    /**
     * 向一个原版 {@link ItemTurret} 注入模组弹药。
     * <p>★ Blocks 中的字段声明为 Block 类型, 此处统一强转 (同 Z_Overwriter 的做法);
     * 类型不符时静默跳过。</p>
     *
     * @param block        目标原版炮台 (声明为 Block, 内部强转 ItemTurret)
     * @param modItem      注入的模组物品
     * @param baseItem     作为模板的原版弹药物品 (取其子弹复制强化)
     * @param dmgMul       伤害缩放系数 (伤害与溅射伤害同时乘)
     * @param ammoPerItem  单个物品装填出的弹药数 (ammoMultiplier)
     */
    private static void ammo(mindustry.world.Block block, Item modItem, Item baseItem, float dmgMul, int ammoPerItem){
        if(!(block instanceof ItemTurret turret)) return;  // 原版改动导致类型变化时跳过

        // 目标炮台已注入过则跳过 (防重复调用叠加伤害)
        if(turret.ammoTypes.get(modItem) != null) return;

        BulletType base = turret.ammoTypes.get(baseItem);
        if(base == null){
            // 兜底: 原版改动导致 baseItem 弹药不存在时, 回退为当前伤害最高的弹药
            for(var entry : turret.ammoTypes.entries()){
                if(entry.value.damage > baseOf(base).damage) base = entry.value;
            }
        }
        if(base == null) return;  // 炮台没有任何弹药 (异常情况), 静默跳过

        // 复制原版子弹并等比强化伤害; 其余字段 (弹速/特效/状态/穿透) 保持原版行为
        BulletType copy = base.copy();
        copy.damage *= dmgMul;
        if(copy.splashDamage > 0f) copy.splashDamage *= dmgMul;
        copy.ammoMultiplier = ammoPerItem;

        turret.ammoTypes.put(modItem, copy);
    }

    /** 空子弹占位 (兜底遍历时的初始比较值) */
    private static BulletType baseOf(BulletType b){
        return b == null ? new BulletType(0f, 0f) : b;
    }
}
