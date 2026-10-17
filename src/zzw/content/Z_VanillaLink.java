package zzw.content;

import mindustry.entities.bullet.LiquidBulletType;
import mindustry.world.blocks.defense.turrets.LiquidTurret;

import static mindustry.content.Blocks.tsunami;
import static mindustry.content.Blocks.wave;

/**
 * 原版方块联动 (跨系统整合入口)
 *
 * <p>把模组的新资源接进原版的消耗/战斗体系, 让原版方块也能消费模组产物 ——
 * 全部通过原版内容单例的字段注入实现, 不重写任何方块行为。</p>
 *
 * <p>★ 当前联动清单:
 * <ul>
 *   <li><b>熔岩 → 波纹/海啸液体炮台</b>: 向原版 {@link LiquidTurret} 的弹药表
 *       注入 Z_Liquids.lava (LiquidBulletType)。坩埚/熔炼产出的熔岩除了模组内部
 *       消耗外, 也可以直接给原版液体炮台当弹药用 (与原版熔渣弹同级, 附带燃烧);
 *       {@code init()} 的 ConsumeLiquidFilter 会自动纳入新液体;</li>
 *   <li><b>纸浆/感染原 → 原版发电机燃料</b>: 物品定义了 flammability
 *       (见 {@link Z_Items}), 被原版 ConsumeItemFlammable 自动判定为可燃,
 *       燃烧发电机/蒸汽发电机可直接烧 —— 无需注入, 物品属性即接口。</li>
 * </ul></p>
 *
 * <p>★ 注入时机: 内容加载阶段 (loadContent), 早于原版 init(), 使消耗过滤器、
 * 统计面板自动纳入。</p>
 */
public class Z_VanillaLink{

    public static void load(){
        // ===== 熔岩 → 原版液体炮台 =====
        // 参照原版波纹的熔渣弹 (damage=4, drag=0.01) 定义熔岩弹:
        // 熔岩温度极高, 与熔渣同级伤害, 行为观感与原版液体弹完全一致
        ammo(wave, new LiquidBulletType(Z_Liquids.lava){{
            damage = 4f;
            drag = 0.01f;
        }});

        ammo(tsunami, new LiquidBulletType(Z_Liquids.lava){{
            damage = 6f;  // 海啸是波纹的上位, 略强
            drag = 0.01f;
        }});
    }

    /**
     * 向一个原版 {@link LiquidTurret} 注入模组液体弹药。
     * <p>Blocks 中字段声明为 Block 类型, 需 instanceof 强转 (同 Z_Overwriter 做法);
     * 类型不符时静默跳过。</p>
     *
     * @param block  目标原版液体炮台 (声明为 Block, 内部强转 LiquidTurret)
     * @param type   熔岩弹 (LiquidBulletType)
     */
    private static void ammo(mindustry.world.Block block, LiquidBulletType type){
        if(!(block instanceof LiquidTurret turret)) return;  // 原版改动导致类型变化时跳过
        if(turret.ammoTypes.get(Z_Liquids.lava) != null) return;  // 防重复注入
        turret.ammoTypes.put(Z_Liquids.lava, type);
    }
}
