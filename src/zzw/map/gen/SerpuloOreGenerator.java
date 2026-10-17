package zzw.map.gen;

import arc.struct.FloatSeq;
import arc.struct.Seq;
import arc.util.noise.Simplex;
import mindustry.content.Blocks;
import mindustry.maps.planet.SerpuloPlanetGenerator;
import mindustry.world.Block;
import zzw.content.Z_Mine;

/**
 * 普罗塞星球矿物生成器 (原版 SerpuloPlanetGenerator 的扩展子类)。
 *
 * <p><b>目的</b>: 让模组矿物 (镍/灵岩/琥珀矿/流明矿/铀铭) 在普罗塞星球上
 * 自动随机生成 —— 原版 {@link SerpuloPlanetGenerator#generate()} 里的矿物
 * 序列是方法局部变量, 无法从外部注入, 所以用子类覆写的方式追加生成逻辑。</p>
 *
 * <p><b>生成策略</b> (对齐原版 [SerpuloPlanetGenerator L501-541] 的做法):</p>
 * <p>1. 原版生成照常执行 ({@code super.generate()}), 原版矿物不受影响;</p>
 * <p>2. 按扇区球面坐标做 3D 单纯形噪声门控, 决定本扇区出哪些模组矿 ——
 * 镍必出 (前期就能用到), 其余按稀缺度门槛递增, 有的扇区就是不出某些矿;</p>
 * <p>3. 每格用双重噪声 (斑块形状 + 密度) 决定是否成矿,
 * <b>只往空气上铺 ({@code ore == air}), 绝不覆盖原版矿脉</b>。</p>
 *
 * <p><b>安装</b>: 在 {@code Z_Mine.load()} 末尾把
 * {@code Planets.serpulo.generator} 替换为本类实例。
 * 注: 原版战役的剧情扇区 (有地图文件的 preset) 走各自的地图文件生成,
 * 不经过本生成器 —— 本类只影响程序化生成的扇区, 与原版矿石生成范围一致。</p>
 */
public class SerpuloOreGenerator extends SerpuloPlanetGenerator{
    /**
     * 模组矿物布矿优先级 (从稀有到常见)。
     * <p>成矿 pass 从表尾往前检查, 表尾的矿最先命中 ——
     * 所以镍 (最后一项) 覆盖面最广, 越靠前越稀有。
     * 用 supplier 懒取 Z_Mine 字段, 避免类加载顺序问题。</p>
     */
    private static final Block[] MOD_ORES = {
        Z_Mine.oreUmbrium,   // 铀铭: 最稀有 (门槛 0.35)
        Z_Mine.oreLuminum,   // 流明: 稀有 (门槛 0.47)
        Z_Mine.oreImberium,  // 琥珀: 较稀有 (门槛 0.59)
        Z_Mine.oreMonolite,  // 灵岩: 较常见 (门槛 0.71)
        Z_Mine.oreNickel     // 镍: 必出 (前期就能用)
    };

    @Override
    protected void generate(){
        // 先跑原版完整生成流程 (地形 / 河流 / 原版矿物 / 敌方基地)
        super.generate();

        // 编辑器/自定义地图没有扇区上下文, 不做模组布矿
        if(sector == null) return;

        float poles = Math.abs(sector.tile.v.y);

        // ===== 1. 扇区级门控: 按扇区球面位置决定本扇区出哪些模组矿 =====
        // 仿原版 coal/titanium/thorium 的做法 (L507-518), 用 +5~+8 偏移避免与原版门控相关
        Seq<Block> ores = new Seq<>();
        for(int i = 0; i < MOD_ORES.length; i++){
            Block entry = MOD_ORES[i];
            if(entry == null) continue;

            // 镍 (最后一项) 必出; 其余矿按稀缺度提高门槛, 门槛越高出现的扇区越少
            boolean forced = i == MOD_ORES.length - 1;
            float threshold = (0.35f + i * 0.12f) * 1.3f;
            if(forced || Simplex.noise3d(seed, 2, 0.5f, 1f,
                sector.tile.v.x + 5 + i, sector.tile.v.y, sector.tile.v.z) * 0.5f + poles > threshold){
                ores.add(entry);
            }
        }
        if(ores.isEmpty()) return;

        // ===== 2. 每格成矿: 只往空气上铺, 不覆盖原版矿脉 =====
        // 频率扰动与原版一致 (L524-527), 让矿斑形状在扇区间有随机差异
        FloatSeq frequencies = new FloatSeq();
        for(int i = 0; i < ores.size; i++){
            frequencies.add(rand.random(-0.1f, 0.01f) - i * 0.01f + poles * 0.04f);
        }

        pass((x, y) -> {
            // 已有原版矿的格子直接跳过
            if(ore != Blocks.air || !floor.asFloor().hasSurface()) return;

            int offsetX = x - 4, offsetY = y + 23;
            for(int i = ores.size - 1; i >= 0; i--){
                Block entry = ores.get(i);
                float freq = frequencies.get(i);
                // 双重噪声: 第一层决定斑块形状, 第二层决定斑块密度 (公式同原版 L536-537)
                if(Math.abs(0.5f - noise(offsetX, offsetY + i * 999, 2, 0.7, (40 + i * 2))) > 0.22f + i * 0.01f &&
                    Math.abs(0.5f - noise(offsetX, offsetY - i * 999, 1, 1, (30 + i * 4))) > 0.37f + freq){
                    ore = entry;
                    break;
                }
            }
        });
    }
}
