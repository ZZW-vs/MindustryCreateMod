package zzw.content.exp.vanilla;

import arc.math.Mathf;
import arc.util.Log;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.io.SaveVersion;
import mindustry.ui.Bar;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import zzw.content.exp.ExpHolder;
import zzw.content.exp.LevelHolder;
import zzw.content.exp.UnityPal;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;

import static mindustry.content.Blocks.*;

/**
 * 原版炮台接入经验系统
 *
 * <p>把原版 {@link ItemTurret} 的建筑实现类替换为 {@link VanillaExpTurretBuild}
 * (通过 {@code buildType} 注入), 使原版炮台成为经验持有者:
 * <ul>
 *   <li>实现 {@link ExpHolder} —— 经验球拾取 (ExpOrbs 按 instanceof 扫描) 与
 *       经验塔发送 (ExpTower 按 instanceof 扫描) 自动覆盖原版炮台, 无需改经验网络;</li>
 *   <li>实现 {@link LevelHolder} —— 等级数学与 PU ExpTurret 完全一致
 *       (maxLevel=10, expScale=1, requiredExp = l*l*5);</li>
 *   <li>等级收益: <b>伤害/8%</b> (含溅射伤害等比), 满级 +80% —— 通过覆写
 *       {@code peekAmmo()/useAmmo()} 注入按等级缓存的弹药副本实现, 原版
 *       弹速/特效/状态效果行为零改动;</li>
 *   <li>信息面板追加 等级/经验 条 (与模组经验炮台同款 UI)。</li>
 * </ul></p>
 *
 * <p>★ 存档兼容: 原版建筑存档流<b>无长度前缀</b>, 直接在 read/write 里加字段会让
 * 旧存档错位损坏 —— 因此经验值走独立的 {@link SaveVersion} CustomChunk
 * (区块名 "zzw-vanilla-exp"), 按 [tilePos → exp] 保存, 旧存档无该区块自动跳过;
 * 读档时在区块解析中直接回填到已加载的建筑 (CustomChunk 在建筑数据之后读取)。</p>
 *
 * <p>★ 覆盖范围: 与 {@link zzw.content.Z_VanillaAmmo} 相同的 13 个原版炮台
 * (双管/冰雹/齐射/波纹/散射/哨兵/灼烧/电弧/齐射激光/前兆/融片/旋风/幽灵)。</p>
 */
public class VanillaExp{

    /** 满级等级 (PU ExpTurret 默认 maxLevel=10) */
    public static final int maxLevel = 10;
    /** 经验曲线缩放 (PU ExpTurret 默认 expScale=1) */
    public static final int expScale = 1;

    /** 升到 l 级所需总经验 (公式与 PU ExpTurret.requiredExp 完全一致) */
    public static int requiredExp(int l){
        return l * l * 5 * expScale;
    }

    /** 由经验值反推等级 (公式与 PU ExpTurret.expLevel 完全一致) */
    public static int expLevelOf(int e){
        return Math.min(maxLevel, (int)(Mathf.sqrt(e / (5f * expScale))));
    }

    public static void load(){
        swap(duo);
        swap(hail);
        swap(salvo);
        swap(ripple);
        swap(scatter);
        swap(segment);
        swap(scorch);
        swap(arc);
        swap(lancer);
        swap(foreshadow);
        swap(fuse);
        swap(cyclone);
        swap(spectre);

        // 注册经验存档区块 (独立于建筑流, 旧存档自动兼容)
        SaveVersion.addCustomChunk("zzw-vanilla-exp", new ExpSaveChunk());
    }

    /** 替换一个原版炮台的建筑实现类 (Blocks 字段声明为 Block, 统一强转) */
    private static void swap(mindustry.world.Block block){
        if(!(block instanceof ItemTurret turret)) return;
        // ItemTurretBuild 是内部类, 子类需传入外部实例 (qualified super 调用)
        turret.buildType = () -> new VanillaExpTurretBuild((ItemTurret)block);
    }

    /**
     * 原版炮台的经验化建筑实现。
     * <p>继承 ItemTurretBuild 保持全部原版行为, 仅叠加经验/等级逻辑。</p>
     */
    public static class VanillaExpTurretBuild extends ItemTurret.ItemTurretBuild implements ExpHolder, LevelHolder{
        /** 累计经验 */
        public int exp;

        // 等级化弹药缓存 (避免 peekAmmo 每帧 copy)
        protected BulletType cacheBase;
        protected int cacheLevel = -1;
        protected BulletType cacheLeveled;

        public VanillaExpTurretBuild(ItemTurret outer){
            outer.super();
        }

        // ===== 等级 =====

        @Override
        public int level(){
            return expLevelOf(exp);
        }

        @Override
        public int maxLevel(){
            return maxLevel;
        }

        @Override
        public float levelf(){
            return level() / (float)maxLevel;
        }

        /** 当前级经验进度 (0~1, 满级恒 1; 公式同 PU ExpTurretBuild.expf) */
        public float expf(){
            int lv = level();
            if(lv >= maxLevel) return 1f;
            float lb = lv <= 0 ? 0f : requiredExp(lv);
            float lc = requiredExp(lv + 1);
            return ((float)exp - lb) / (lc - lb);
        }

        /** 等级伤害倍率: 每级 +8%, 满级 1.8 倍 */
        public float damageMul(){
            return 1f + 0.08f * level();
        }

        /**
         * 取当前等级的弹药副本 (按 原版弹药引用 + 等级 双键缓存, 换弹药/升级才重建)。
         * <p>伤害与溅射伤害等比放大, 其余字段 (弹速/特效/状态/穿透) 保持原版行为。</p>
         */
        protected BulletType leveled(BulletType base){
            if(base == null) return null;
            int lv = level();
            if(cacheBase != base || cacheLevel != lv || cacheLeveled == null){
                BulletType copy = base.copy();
                float mul = damageMul();
                copy.damage *= mul;
                if(copy.splashDamage > 0f) copy.splashDamage *= mul;
                cacheBase = base;
                cacheLevel = lv;
                cacheLeveled = copy;
            }
            return cacheLeveled;
        }

        @Override
        public BulletType peekAmmo(){
            return leveled(super.peekAmmo());
        }

        @Override
        public BulletType useAmmo(){
            return leveled(super.useAmmo());
        }

        // ===== 经验收支 (ExpHolder) =====

        /** 增加经验并处理升级 (与 PU ExpTurretBuild.incExp 一致, 原版炮台不接 hub) */
        public int incExp(int amount){
            int cap = requiredExp(maxLevel);
            int e = Math.min(amount, cap - exp);
            if(e == 0) return 0;
            exp += e;
            return e;
        }

        @Override
        public int getExp(){
            return exp;
        }

        @Override
        public int handleExp(int amount){
            return incExp(amount);
        }

        @Override
        public int unloadExp(int amount){
            int e = Math.min(amount, exp);
            exp -= e;
            return e;
        }

        @Override
        public boolean acceptOrb(){
            return exp < requiredExp(maxLevel);
        }

        @Override
        public boolean handleOrb(int orbExp){
            // orbScale 0.8f, 同 PU ExpTurret 默认值
            int a = (int)(0.8f * orbExp);
            if(a < 1) return false;
            return incExp(a) > 0;
        }

        // ===== 信息面板 =====

        @Override
        public void displayBars(arc.scene.ui.layout.Table table){
            super.displayBars(table);
            table.row();
            table.add(new Bar(
                () -> level() >= maxLevel ? "MAX" : "Lv " + level(),
                () -> UnityPal.exp,
                this::expf
            )).growX();
            table.row();
        }
    }

    /**
     * 经验存档区块: [版本=1][条目数][tilePos, exp]...
     * <p>读档时 CustomChunk 在建筑数据之后解析, 直接回填到已加载建筑;
     * 全量缓冲解析, 区块损坏只损失经验, 不炸存档 (同 WorldUnitEntity.SaveChunk 模式)。</p>
     */
    static class ExpSaveChunk implements SaveVersion.CustomChunk{
        static final byte VERSION = 1;

        @Override
        public boolean shouldWrite(){
            // 任何一门经验化原版炮台有经验就写 (便宜的全表扫描)
            for(Building b : Groups.build){
                if(b instanceof VanillaExpTurretBuild v && v.exp > 0) return true;
            }
            return false;
        }

        /** 单人存档专用, 不写入联机同步流 */
        @Override
        public boolean writeNet(){
            return false;
        }

        @Override
        public void write(DataOutput stream) throws IOException{
            Writes write = new Writes(stream);
            int count = 0;
            for(Building b : Groups.build){
                if(b instanceof VanillaExpTurretBuild v && v.exp > 0) count++;
            }
            write.b(VERSION);
            write.i(count);
            for(Building b : Groups.build){
                if(b instanceof VanillaExpTurretBuild v && v.exp > 0){
                    write.i(b.tile.pos());
                    write.i(v.exp);
                }
            }
        }

        /** 读档入口 (带区块长度, v158+ 调用路径): 全量缓冲后解析, 见类注释 */
        public void read(DataInput stream, int length) throws IOException{
            byte[] data = new byte[length];
            stream.readFully(data);
            try(ByteArrayInputStream bin = new ByteArrayInputStream(data)){
                parse(new Reads(new DataInputStream(bin)));
            }catch(Exception e){
                Log.err("zzw-vanilla-exp chunk parse failed, exp data skipped", e);
            }
        }

        /** 读档入口 (无长度版本, v155.4 抽象方法 / v158 default 转发兜底) */
        @Override
        public void read(DataInput stream) throws IOException{
            parse(new Reads(stream));
        }

        private void parse(Reads read){
            int ver = read.b() & 0xFF;
            int count = read.i();
            for(int i = 0; i < count; i++){
                int pos = read.i();
                int exp = read.i();
                Tile t = Vars.world.tile(pos);
                if(t != null && t.build instanceof VanillaExpTurretBuild v){
                    v.exp = exp;
                }
            }
        }
    }
}
