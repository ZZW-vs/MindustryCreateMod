package zzw.content;

/**
 * 原版体验调优 (小型 QoL 微调)
 *
 * <p>原则: <b>只调整原版单例的数值字段, 不重写任何行为</b> —— 无存档兼容问题、
 * 无副作用, 全部是可感知但克制的加强 (幅度 +15%~50%), 让原版基础设施在模组
 * 后期科技面前不至于完全鸡肋。</p>
 *
 * <p>★ 调优清单 (对照 Mindustry v158/v160 Blocks.java 原值):
 * <ul>
 *   <li>存储: 容器 300→400, 仓库 1000→1500, 液体罐 1800→2400;</li>
 *   <li>传送带微速提升 (仅基础/钛/装甲三档, 略微 +12%~17%,
 *       塑钢堆叠传送带机制不同不动): 铜 0.03→0.035 (4.2→4.9 格/秒),
 *       钛 0.08→0.09 (11→12.4 格/秒), 装甲 0.08→0.09 (11→12.4 格/秒);</li>
 *   <li>卸载器: 60/11 → 60/9 物品/秒 (+22%)。</li>
 * </ul></p>
 *
 * <p>★ 注意: 钻头速度保持原版 (机械钻头本就应是最慢的, 用户明确要求不改);
 * Blocks 中字段声明为 Block 类型, 需 instanceof 强转 (同 Z_Overwriter /
 * Z_VanillaAmmo 的做法); 若原版未来版本类型/字段变化, 编译期直接暴露,
 * 不会悄悄失效。</p>
 */
public class Z_VanillaQoL{

    public static void load(){
        // ===== 存储容量 =====
        mindustry.content.Blocks.container.itemCapacity = 400;
        mindustry.content.Blocks.vault.itemCapacity = 1500;
        mindustry.content.Blocks.liquidTank.liquidCapacity = 2400f;

        // ===== 传送带微速提升 (displayedSpeed 为面板显示值, 需同步调整) =====
        if(mindustry.content.Blocks.conveyor instanceof mindustry.world.blocks.distribution.Conveyor c){
            c.speed = 0.035f;
            c.displayedSpeed = 4.9f;
        }
        if(mindustry.content.Blocks.titaniumConveyor instanceof mindustry.world.blocks.distribution.Conveyor c){
            c.speed = 0.09f;
            c.displayedSpeed = 12.4f;
        }
        if(mindustry.content.Blocks.armoredConveyor instanceof mindustry.world.blocks.distribution.ArmoredConveyor c){
            c.speed = 0.09f;
            c.displayedSpeed = 12.4f;
        }

        // ===== 卸载器 =====
        if(mindustry.content.Blocks.unloader instanceof mindustry.world.blocks.storage.Unloader u) u.speed = 60f / 9f;
    }
}
