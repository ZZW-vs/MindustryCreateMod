package zzw.content.blocks.distribution;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Tmp;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.world.blocks.Autotiler.SliceMode;
import mindustry.logic.LAccess;
import mindustry.type.Item;
import mindustry.world.Edges;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.ChainedBuilding;
import mindustry.world.blocks.distribution.Conveyor;

import static arc.Core.atlas;
import static mindustry.Vars.*;

/**
 * MechanicalConveyor 机械传送带 (重做版, 2026-10-18 用户需求)。
 *
 * <p><b>需求</b>: 原版有两种传送带 —— 普通传送带 (conveyor, 单件运输) 和
 * 塑料传送带 (plastanium-conveyor, 批量"打包"运输)。机械传送带要能:</p>
 * <p>1. 直接和原版两种传送带互相连接 (输入/输出都兼容);</p>
 * <p>2. 能承接塑料传送带末端的<b>批量倾倒</b> (打包流);</p>
 * <p>3. 也能像普通传送带一样单件运输;</p>
 * <p>4. 两种运输状态在同一条带上<b>同时出现</b>。</p>
 *
 * <p><b>实现原理</b>: 原版 Conveyor 的运输槽位数是父类里的私有常量 (3 格),
 * 无法从子类扩容, 所以这里完整重写 Build 运动逻辑 (对齐 v158.1 ConveyorBuild):
 * 槽位 {@link #beltCapacity} 个 (默认 8), 格内物品间距 {@link #itemSpace} =
 * 1/槽位数 相应缩小 —— 吞吐 = speed / itemSpace × 60, 远高于原版传送带,
 * 足以吃下塑料传送带末端的批量卸货。打包流和单件流共用同一条物品队列,
 * 自然在同一条带上混合出现。</p>
 *
 * <p><b>兼容性</b>:</p>
 * <p>- 与普通传送带/塑料传送带的连接走通用的 {@code acceptItem/handleItem}
 * 接口 (原版 ConveyorBuild.pass 与塑料末端倾倒都用这套), 天然互通;</p>
 * <p>- 同种机械传送带之间保留原版的 aligned 快速通道 (X 坐标接力,
 * 弯道 blending 视觉同原版);</p>
 * <p>- 存档格式与原版 ConveyorBuild 完全一致 (version 1), 旧存档无损兼容;</p>
 * <p>- 继承 Conveyor 方块级逻辑 (autotiler 贴图 blending / 拆除替换 /
 * junction 替换 / 放置连线桥接), 并保留 PU_V8 的 shadowRegion 影子渲染。</p>
 */
public class MechanicalConveyor extends Conveyor{
    /** 运输槽位数 (原版 3)。槽位越多单格容纳物品越多, 打包流吞吐越高 */
    public int beltCapacity = 8;
    /** 格内物品间距 = 1/槽位数 (原版 0.4); 间距越小同格能塞的物品越多 */
    public float itemSpace = 1f / 8f;
    /** 影子贴图 (PU_V8 视觉过渡) */
    public TextureRegion shadowRegion;

    public MechanicalConveyor(String name){
        super(name);
    }

    @Override
    public void init(){
        // 槽位/间距在 init 时统一生效 (构造期 beltCapacity 还没赋默认值)
        itemSpace = 1f / beltCapacity;
        itemCapacity = beltCapacity;
        super.init();
    }

    @Override
    public void load(){
        super.load();
        shadowRegion = atlas.find(name + "-shadow");
    }

    /**
     * 机械传送带建筑体 —— 完整重写的运输逻辑 (基于 v158.1 ConveyorBuild)。
     * <p>与原版的差异只有两点: 槽位/间距可配置; nextb 只指向同类建筑
     * (跨到原版传送带时走通用 acceptItem/handleItem 接口)。</p>
     */
    public class BeltBuild extends Building implements ChainedBuilding{
        // ===== 平行数组 (原版 ids/xs/ys, 长度 = beltCapacity) =====
        public Item[] ids = new Item[beltCapacity];
        public float[] xs = new float[beltCapacity], ys = new float[beltCapacity];
        /** 当前物品数 (始终 < beltCapacity) */
        public int len = 0;
        /** 前方建筑 (任意类型, pass 时走通用接口) */
        public @Nullable Building next;
        /** 前方同种机械传送带 (仅同类之间有 aligned 快速通道) */
        public @Nullable BeltBuild nextb;
        /** 前方同种传送带朝向是否与本格一致 (决定物品能否直接接力) */
        public boolean aligned;

        public int lastInserted, mid;
        public float minitem = 1;

        public int blendbits, blending;
        public int blendsclx = 1, blendscly = 1;

        public float clogHeat = 0f;
        /** PU_V8 影子渲染: back/left/right 三向是否有同种朝向传送带 */
        public boolean looking;

        // ===== 绘制 =====

        @Override
        public void draw(){
            int frame = enabled && clogHeat <= 0.5f ? (int)(((Time.time * speed * 8f * timeScale * efficiency)) % 4) : 0;

            // 非方形拼合: 给对着本格的相邻传送带补画切分贴图
            Draw.z(Layer.blockUnder);
            for(int i = 0; i < 4; i++){
                if((blending & (1 << i)) != 0){
                    int dir = rotation - i;
                    float rot = i == 0 ? rotation * 90 : (dir) * 90;

                    Draw.rect(sliced(regions[0][frame], i != 0 ? SliceMode.bottom : SliceMode.top),
                        x + Geometry.d4x(dir) * tilesize * 0.75f, y + Geometry.d4y(dir) * tilesize * 0.75f, rot);
                }
            }

            Draw.z(Layer.block - 0.2f);
            Draw.rect(regions[blendbits][frame], x, y, tilesize * blendsclx, tilesize * blendscly, rotation * 90);

            // PU_V8 影子渲染 (原 ShadowedConveyor): 无同类邻居的方向画影子贴图
            Draw.z(Layer.block - 0.15f);
            if(!looking) Draw.rect(shadowRegion, x, y, rotdeg());
            if(nextb == null) Draw.rect(shadowRegion, x, y, rotdeg() + 180f);

            // 物品
            Draw.z(Layer.block - 0.1f);
            float layer = Layer.block - 0.1f, wwidth = world.unitWidth(), wheight = world.unitHeight(), scaling = 0.01f;

            for(int i = 0; i < len; i++){
                Item item = ids[i];
                Tmp.v1.trns(rotation * 90, tilesize, 0);
                Tmp.v2.trns(rotation * 90, -tilesize / 2f, xs[i] * tilesize / 2f);

                float ix = (x + Tmp.v1.x * ys[i] + Tmp.v2.x),
                      iy = (y + Tmp.v1.y * ys[i] + Tmp.v2.y);

                // 保证绘制顺序确定性
                Draw.z(layer + (ix / wwidth + iy / wheight) * scaling);
                Draw.rect(item.fullIcon, ix, iy, itemSize, itemSize);
            }
        }

        @Override
        public void payloadDraw(){
            Draw.rect(block.fullIcon, x, y);
        }

        @Override
        public void drawCracks(){
            Draw.z(Layer.block - 0.15f);
            super.drawCracks();
        }

        // ===== 邻接 / 状态 =====

        @Override
        public void onProximityUpdate(){
            super.onProximityUpdate();

            int[] bits = buildBlending(tile, rotation, null, true);
            blendbits = bits[0];
            blendsclx = bits[1];
            blendscly = bits[2];
            blending = bits[4];

            next = front();
            // 只有同类机械传送带才走 aligned 快速通道; 原版传送带走通用接口
            nextb = next instanceof BeltBuild && next.team == team ? (BeltBuild)next : null;
            aligned = nextb != null && rotation == next.rotation;

            // PU_V8 影子渲染: back/left/right 三向是否有同种同朝向传送带
            looking = sameOriented(back()) || sameOriented(left()) || sameOriented(right());
        }

        /** 邻居是否为同种方块且朝向本格 (影子渲染判定) */
        private boolean sameOriented(Building other){
            return other != null && other.block == block
                && other.tile.relativeTo(tile) == other.rotation;
        }

        @Override
        public void overwrote(Seq<Building> builds){
            if(builds.first() instanceof BeltBuild build){
                ids = build.ids.clone();
                xs = build.xs.clone();
                ys = build.ys.clone();
                len = build.len;
                clogHeat = build.clogHeat;
                lastInserted = build.lastInserted;
                mid = build.mid;
                minitem = build.minitem;
                items.add(build.items);
            }
        }

        @Override
        public boolean shouldAmbientSound(){
            return clogHeat <= 0.5f;
        }

        // ===== 单位运输 =====

        @Override
        public void unitOn(Unit unit){
            if(!pushUnits || clogHeat > 0.5f || !enabled) return;

            noSleep();

            float mspeed = speed * tilesize * 55f;
            float centerSpeed = 0.1f;
            float centerDstScl = 3f;
            float tx = Geometry.d4x(rotation), ty = Geometry.d4y(rotation);

            float centerx = 0f, centery = 0f;

            if(Math.abs(tx) > Math.abs(ty)){
                centery = Mathf.clamp((y - unit.y()) / centerDstScl, -centerSpeed, centerSpeed);
                if(Math.abs(y - unit.y()) < 1f) centery = 0f;
            }else{
                centerx = Mathf.clamp((x - unit.x()) / centerDstScl, -centerSpeed, centerSpeed);
                if(Math.abs(x - unit.x()) < 1f) centerx = 0f;
            }

            if(len * itemSpace < 0.9f){
                unit.impulse((tx * mspeed + centerx) * delta(), (ty * mspeed + centery) * delta());
            }
        }

        // ===== 物品移动 =====

        @Override
        public void updateTile(){
            minitem = 1f;
            mid = 0;

            // 没东西且无加速时休眠
            if(len == 0 && Mathf.equal(timeScale, 1f)){
                clogHeat = 0f;
                sleep();
                return;
            }

            // aligned 时前方物品未走远则本格队首先顶着 (防重叠接力)
            float nextMax = aligned ? 1f - Math.max(itemSpace - nextb.minitem, 0) : 1f;
            float moved = speed * edelta();

            for(int i = len - 1; i >= 0; i--){
                float nextpos = (i == len - 1 ? 100f : ys[i + 1]) - itemSpace;
                float maxmove = Mathf.clamp(nextpos - ys[i], 0, moved);

                ys[i] += maxmove;

                if(ys[i] > nextMax) ys[i] = nextMax;
                if(ys[i] > 0.5 && i > 0) mid = i - 1;
                xs[i] = Mathf.approach(xs[i], 0, moved * 2);

                if(ys[i] >= 1f && pass(ids[i])){
                    // 同类接力时保留横向偏移 (视觉平滑)
                    if(aligned){
                        nextb.xs[nextb.lastInserted] = xs[i];
                    }
                    // 移除队首及之后的物品 (pass 已转发)
                    items.remove(ids[i], len - i);
                    len = Math.min(i, len);
                }else if(ys[i] < minitem){
                    minitem = ys[i];
                }
            }

            if(minitem < itemSpace + (blendbits == 1 ? 0.3f : 0f)){
                clogHeat = Mathf.approachDelta(clogHeat, 1f, 1f / 60f);
            }else{
                clogHeat = 0f;
            }

            noSleep();
        }

        /** 把队首物品交给前方建筑 (通用 acceptItem/handleItem 接口, 兼容原版两种传送带) */
        public boolean pass(Item item){
            if(item != null && next != null && next.team == team && next.acceptItem(this, item)){
                next.handleItem(this, item);
                return true;
            }
            return false;
        }

        // ===== 物品输入/输出 =====

        @Override
        public int removeStack(Item item, int amount){
            noSleep();
            int removed = 0;

            for(int j = 0; j < amount; j++){
                for(int i = 0; i < len; i++){
                    if(ids[i] == item){
                        remove(i);
                        removed++;
                        break;
                    }
                }
            }

            items.remove(item, removed);
            return removed;
        }

        @Override
        public void getStackOffset(Item item, Vec2 trns){
            trns.trns(rotdeg() + 180f, tilesize / 2f);
        }

        /** 批量输入上限 (打包流入口): 由队首空间决定能再塞几个 */
        @Override
        public int acceptStack(Item item, int amount, Teamc source){
            return Math.min((int)(minitem / itemSpace), amount);
        }

        @Override
        public void handleStack(Item item, int amount, Teamc source){
            amount = Math.min(amount, beltCapacity - len);

            for(int i = amount - 1; i >= 0; i--){
                add(0);
                xs[0] = 0;
                ys[0] = i * itemSpace;
                ids[0] = item;
                items.add(item, 1);
            }

            noSleep();
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            if(len >= beltCapacity) return false;
            Tile facing = Edges.getFacingEdge(source.tile, tile);
            if(facing == null) return false;
            int direction = Math.abs(facing.relativeTo(tile.x, tile.y) - rotation);
            return (((direction == 0) && minitem >= itemSpace) || ((direction % 2 == 1) && minitem > 0.7f)) && !(source.block.rotate && next == source);
        }

        @Override
        public void handleItem(Building source, Item item){
            if(len >= beltCapacity) return;

            int r = rotation;
            Tile facing = Edges.getFacingEdge(source.tile, tile);
            int ang = ((facing.relativeTo(tile.x, tile.y) - r));
            float x = (ang == -1 || ang == 3) ? 1 : (ang == 1 || ang == -3) ? -1 : 0;

            noSleep();
            items.add(item, 1);

            if(Math.abs(facing.relativeTo(tile.x, tile.y) - r) == 0){ // 从背面插入: idx = 0
                add(0);
                xs[0] = x;
                ys[0] = 0;
                ids[0] = item;
                lastInserted = 0;
            }else{ // 从侧面插入: idx = mid
                add(mid);
                xs[mid] = x;
                ys[mid] = 0.5f;
                ids[mid] = item;
                lastInserted = mid;
            }
        }

        // ===== 存档 (格式与原版 ConveyorBuild v1 一致, 旧存档无损读取) =====

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(len);

            for(int i = 0; i < len; i++){
                write.s(ids[i].id);
                write.b((byte)(xs[i] * 127));
                write.b((byte)(ys[i] * 255 - 128));
            }
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            int amount = read.i();
            len = Math.min(amount, beltCapacity);

            for(int i = 0; i < amount; i++){
                short id;
                float x, y;

                if(revision == 0){
                    int val = read.i();
                    id = (short)(((byte)(val >> 24)) & 0xff);
                    x = (float)((byte)(val >> 16)) / 127f;
                    y = ((float)((byte)(val >> 8)) + 128f) / 255f;
                }else{
                    id = read.s();
                    x = (float)read.b() / 127f;
                    y = ((float)read.b() + 128f) / 255f;
                }

                if(i < beltCapacity){
                    ids[i] = content.item(id);
                    xs[i] = x;
                    ys[i] = y;
                }
            }

            // 读档后立即推进一步, 恢复 minitem/mid 等状态
            updateTile();
        }

        // ===== 逻辑传感 =====

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.progress){
                if(len == 0) return 0;
                return ys[len - 1];
            }
            return super.sense(sensor);
        }

        @Override
        public Object senseObject(LAccess sensor){
            if(sensor == LAccess.firstItem && len > 0) return ids[len - 1];
            return super.senseObject(sensor);
        }

        @Override
        public void setProp(UnlockableContent content, double value){
            if(content instanceof Item item && items != null){
                int amount = Math.min((int)value, beltCapacity);
                if(items.get(item) != amount){
                    if(items.get(item) < amount){
                        handleStack(item, amount - items.get(item), null);
                    }else if(amount >= 0){
                        removeStack(item, items.get(item) - amount);
                    }
                }
            }else super.setProp(content, value);
        }

        // ===== 内部工具 =====

        /** 在下标 o 处插入一个空槽 (把 [o, len) 后移一位) */
        public final void add(int o){
            for(int i = Math.max(o + 1, len); i > o; i--){
                ids[i] = ids[i - 1];
                xs[i] = xs[i - 1];
                ys[i] = ys[i - 1];
            }

            len++;
        }

        /** 移除下标 o 的物品 (把 [o+1, len) 前移一位) */
        public final void remove(int o){
            for(int i = o; i < len - 1; i++){
                ids[i] = ids[i + 1];
                xs[i] = xs[i + 1];
                ys[i] = ys[i + 1];
            }

            len--;
        }

        /** ChainedBuilding: 链式查询返回同类前建筑 */
        @Nullable
        @Override
        public Building next(){
            return nextb;
        }
    }
}
