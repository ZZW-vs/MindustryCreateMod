package zzw.content;

import arc.graphics.Color;
import mindustry.entities.bullet.BulletType;
import mindustry.type.Item;
import mindustry.world.blocks.defense.turrets.ItemTurret;

import java.util.function.Consumer;

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
        // ★ 特效重写: 直接复制浪涌弹的话, 命中/射击特效与巨浪合金完全同款 (染色
        //   只影响弹色, 不影响特效), 这里改为灵岩专属的虚空系观感
        ammo(foreshadow, Z_Items.monolite, mindustry.content.Items.surgeAlloy, 1.25f, 5, b -> {
            b.hitEffect = zzw.content.units.effects.HitEffect.voidHit ; // 命中: 黑芯+弹色描边 (PU132 虚空系, 灵岩 = imber 时空系暗色石)
            b.shootEffect = zzw.content.units.effects.CombatFx.muzzleFlashSmoke ; // 射击: 模组炮口闪
        });

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
        ammo(block, modItem, baseItem, dmgMul, ammoPerItem, null);
    }

    /**
     * 向一个原版 {@link ItemTurret} 注入模组弹药 (带特效自定义钩子)。
     * <p>★ Blocks 中的字段声明为 Block 类型, 此处统一强转 (同 Z_Overwriter 的做法);
     * 类型不符时静默跳过。</p>
     *
     * @param block        目标原版炮台 (声明为 Block, 内部强转 ItemTurret)
     * @param modItem      注入的模组物品
     * @param baseItem     作为模板的原版弹药物品 (取其子弹复制强化)
     * @param dmgMul       伤害缩放系数 (伤害与溅射伤害同时乘)
     * @param ammoPerItem  单个物品装填出的弹药数 (ammoMultiplier)
     * @param custom       注入前的额外定制 (重写特效等), null 表示不定制;
     *                     在颜色染色<b>之前</b>调用, 染色仍会作用于自定义后的特效弹色
     */
    private static void ammo(mindustry.world.Block block, Item modItem, Item baseItem, float dmgMul, int ammoPerItem, Consumer<BulletType> custom){
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

        // ★ 附生弹 (子母弹) 处理: BulletType.copy() 是浅拷贝, fragBullet/intervalBullet
        //   仍与原版弹药共享同一实例。这里深拷贝一份并等比强化伤害, 让注入弹药
        //   拥有独立的附生弹 (也避免未来调整互相串改)。
        copy.fragBullet = strengthenChild(copy.fragBullet, dmgMul);
        copy.intervalBullet = strengthenChild(copy.intervalBullet, dmgMul);

        // ★ 特效自定义: 在染色前调用, 允许个别弹药重写攻击特效
        //   (染色只改弹色字段, 重写的特效若依赖 hitColor 仍会被染色)
        if(custom != null) custom.accept(copy);

        // ★ 颜色区分: 原版炮台不同弹药本就有不同颜色 (如旋风的玻璃/塑钢/浪涌),
        //   注入弹药若与模板弹药同色, 视觉上无法区分装的是哪种。
        //   把主弹与附生弹的整套颜色向模组物品色偏移 40%, 观感上"同款加强版"。
        tintBullet(copy, modItem.color);

        turret.ammoTypes.put(modItem, copy);
    }

    /**
     * 深拷贝一颗附生子弹并等比强化伤害 (null 直接返回 null)。
     * <p>附生弹自己如果还有下一级 fragBullet, 不再递归 —— 原版附生弹最多一级。</p>
     */
    private static BulletType strengthenChild(BulletType child, float dmgMul){
        if(child == null) return null;
        BulletType copy = child.copy();
        copy.damage *= dmgMul;
        if(copy.splashDamage > 0f) copy.splashDamage *= dmgMul;
        return copy;
    }

    /**
     * 把一颗子弹的整套颜色字段向 {@code to} 偏移 40% (null 字段自动跳过)。
     * <p>覆盖: 基础弹的前后色/命中色/拖尾色/闪电色, 以及激光类的 colors 渐变数组;
     * 所有 Color 均先 cpy() 再调 lerp, <b>绝不修改原版弹药共享的 Color 实例</b>;
     * lerp 会连 alpha 一起插值, 因此插值后恢复原 alpha (保持渐变数组半透明段)。</p>
     */
    private static void tintBullet(BulletType b, Color to){
        // ★ null 安全: 原版部分弹药的 fragBullet/intervalBullet 为 null,
        //   下方递归染色附生弹时会把 null 传进来, 直接 NPE 崩掉整个内容加载
        if(b == null) return;
        b.hitColor = tint(b.hitColor, to);
        b.trailColor = tint(b.trailColor, to);
        b.lightningColor = tint(b.lightningColor, to);

        if(b instanceof mindustry.entities.bullet.BasicBulletType basic){
            basic.backColor = tint(basic.backColor, to);
            basic.frontColor = tint(basic.frontColor, to);
        }
        if(b instanceof mindustry.entities.bullet.LaserBulletType laser){
            laser.colors = tintColors(laser.colors, to);
        }
        if(b instanceof mindustry.entities.bullet.ContinuousLaserBulletType cl){
            cl.colors = tintColors(cl.colors, to);
        }
        if(b instanceof mindustry.entities.bullet.ContinuousFlameBulletType cf){
            cf.colors = tintColors(cf.colors, to);
        }
        // 附生弹一并染色, 保持主弹与子母弹观感统一
        tintBullet(b.fragBullet, to);
        tintBullet(b.intervalBullet, to);
    }

    /** 颜色向 {@code to} 偏移 40%, 保留原 alpha; null 安全。 */
    private static Color tint(Color c, Color to){
        if(c == null) return null;
        float a = c.a;
        Color result = c.cpy().lerp(to, 0.4f);
        result.a = a;
        return result;
    }

    /** 颜色数组逐个偏移 (拷贝为新数组, 不动原版共享数组); null 安全。 */
    private static Color[] tintColors(Color[] colors, Color to){
        if(colors == null) return null;
        Color[] result = new Color[colors.length];
        for(int i = 0; i < colors.length; i++) result[i] = tint(colors[i], to);
        return result;
    }

    /** 空子弹占位 (兜底遍历时的初始比较值) */
    private static BulletType baseOf(BulletType b){
        return b == null ? new BulletType(0f, 0f) : b;
    }
}
