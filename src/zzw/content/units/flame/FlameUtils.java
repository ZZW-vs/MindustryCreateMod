package zzw.content.units.flame;

import arc.Core;
import arc.func.Boolf;
import arc.func.Cons;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.QuadTree;
import arc.math.geom.QuadTree.QuadTreeObject;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.core.World;
import mindustry.entities.Sized;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Teamc;
import mindustry.world.Tile;

import java.util.Arrays;

/**
 * 消沉用的空间查询工具 (裁自 flame.Utils)。
 *
 * <p>★ 与 FO 原版的差异 (用户要求: 静态共享变量改局部/成员变量):
 * FO 的 {@code Utils.v/vv/rand/rand2/r/r2/hseq/ll} 是长期存活的静态对象，
 * 多个系统并发调用时会互相覆盖。本移植:</p>
 * <ul>
 *   <li>所有临时向量改用 Arc 官方 {@link Tmp} 槽位，只在单次调用内即用即弃;</li>
 *   <li>随机源改为调用点自建，不再保留带 seed 状态的全局 {@code rand};</li>
 *   <li>返回 Vec2 的方法返回 {@link Tmp#v1}，调用方必须立即使用 (与原版一致)。</li>
 * </ul>
 */
public class FlameUtils{
    /**
     * 消沉整体输出强度系数 (用户要求: 原版机制 + 强度按原版削弱一半)。
     *
     * <p>只缩放"持续削血"类伤害 (激光/轨道炮/核弹/导弹/长矛), 不缩放
     * 处决 (maxHealth + 常数) 伤害 —— 处决是机制而非数值。</p>
     */
    public static final float DES_DAMAGE_SCALE = 0.5f;

    /** 本模组内容名的贴图前缀 (Mindustry 打包 mod 贴图时会加上 mod 内部名)。 */
    public static final String MOD_PREFIX = "create-";

    /**
     * 按贴图短名取区域, 自动兼容 mod 前缀。
     *
     * <p>★ 修复消沉大招"武器贴图全是错的": 本模组贴图在 atlas 里是
     * {@code create-despondency-xxx}, 直接 {@code atlas.find("despondency-xxx")}
     * 会静默拿到 error 图 (粉黑格子)。这里先试带前缀的名字, 再回退裸名。</p>
     */
    public static TextureRegion atlasRegion(String name){
        if(Core.atlas.has(MOD_PREFIX + name)) return Core.atlas.find(MOD_PREFIX + name);
        return Core.atlas.find(name);
    }

    /** 线段命中回调 (entity, 命中点 x, 命中点 y)。 */
    public interface LineHitHandler<T>{
        void get(T t, float x, float y);
    }

    /** 四叉树内外判定回调 (bounds, 是否树节点)。 */
    public interface QuadTreeHandler{
        boolean get(Rect rect, boolean tree);
    }

    /**
     * 圆形范围扫描敌我单位与建筑。
     *
     * @param team 归属队伍 (扫描非本队目标)
     */
    public static void scanEnemies(Team team, float x, float y, float radius, boolean targetAir, boolean targetGround, Cons<Teamc> cons){
        Rect r = Tmp.r1.setCentered(x, y, radius * 2f);
        Groups.unit.intersect(r.x, r.y, r.width, r.height, u -> {
            if(u.team != team && Mathf.within(x, y, u.x, u.y, radius + u.hitSize / 2f) && u.checkTarget(targetAir, targetGround)){
                cons.get(u);
            }
        });

        if(targetGround){
            Seq<Building> found = new Seq<>();
            for(TeamData data : Vars.state.teams.present){
                if(data.team != team && data.buildingTree != null){
                    data.buildingTree.intersect(r, b -> {
                        if(Mathf.within(x, y, b.x, b.y, radius + b.hitSize() / 2f)){
                            found.add(b);
                        }
                    });
                }
            }
            for(Building b : found){
                cons.get(b);
            }
        }
    }

    /**
     * 宽度为 width 的射线扫描: 沿 (x1,y1)→(x2,y2) 收集所有命中的单位/建筑,
     * 按距起点距离升序回调; stop 返回 true 时截断射线并返回实际长度。
     *
     * @return 射线实际有效长度 (被遮挡时为 hit 点到起点距离)
     */
    public static float hitLaser(Team team, float width, float x1, float y1, float x2, float y2,
                                 Boolf<Healthc> within, Boolf<Healthc> stop, LineHitHandler<Healthc> cons){
        Seq<Hit> hits = new Seq<>();
        float length = Mathf.dst(x1, y1, x2, y2);

        for(TeamData data : Vars.state.teams.present){
            if(data.team == team) continue;

            if(data.unitTree != null){
                intersectLine(data.unitTree, width, x1, y1, x2, y2, (t, x, y) -> {
                    if(within != null && !within.get(t)) return;
                    hits.add(new Hit(t, x, y));
                });
            }
            if(data.buildingTree != null){
                intersectLine(data.buildingTree, width, x1, y1, x2, y2, (t, x, y) -> {
                    if(within != null && !within.get(t)) return;
                    hits.add(new Hit(t, x, y));
                });
            }
        }

        hits.sort(a -> a.entity.dst2(x1, y1));
        for(Hit hit : hits){
            Healthc t = hit.entity;
            cons.get(t, hit.x, hit.y);
            if(stop.get(t)){
                length = Mathf.dst(x1, y1, hit.x, hit.y) - (t instanceof Sized s ? s.hitSize() / 4f : 0f);
                break;
            }
        }
        return length;
    }

    /** 命中记录 (内部使用)。 */
    private static class Hit{
        final Healthc entity;
        final float x, y;

        Hit(Healthc entity, float x, float y){
            this.entity = entity;
            this.x = x;
            this.y = y;
        }
    }

    /** 与线段相交的四叉树节点遍历 (圆-矩形近似)。 */
    public static <T extends QuadTreeObject> void intersectLine(QuadTree<T> tree, float width, float x1, float y1, float x2, float y2, LineHitHandler<T> cons){
        Rect r = Tmp.r1.set(tree.bounds).grow(width);
        if(!Intersector.intersectSegmentRectangle(x1, y1, x2, y2, r)) return;

        Rect r2 = Tmp.r2;
        for(T t : tree.objects){
            t.hitbox(r2);
            r2.grow(width);
            float cx = r2.x + r2.width / 2f, cy = r2.y + r2.height / 2f;
            float cr = Math.max(r2.width, r2.height);

            Vec2 v = intersectCircle(x1, y1, x2, y2, cx, cy, cr / 2f);
            if(v != null){
                float scl = (cr - width) / cr;
                v.sub(cx, cy).scl(scl).add(cx, cy);
                cons.get(t, v.x, v.y);
            }
        }

        if(!tree.leaf){
            intersectLine(tree.botLeft, width, x1, y1, x2, y2, cons);
            intersectLine(tree.botRight, width, x1, y1, x2, y2, cons);
            intersectLine(tree.topLeft, width, x1, y1, x2, y2, cons);
            intersectLine(tree.topRight, width, x1, y1, x2, y2, cons);
        }
    }

    /** 递归扫描四叉树，命中回调; within 决定节点/对象是否进入。 */
    public static <T extends QuadTreeObject> void scanQuadTree(QuadTree<T> tree, QuadTreeHandler within, Cons<T> cons){
        if(within.get(tree.bounds, true)){
            Rect r2 = Tmp.r2;
            for(T t : tree.objects){
                t.hitbox(r2);
                if(within.get(r2, false)){
                    cons.get(t);
                }
            }
            if(!tree.leaf){
                scanQuadTree(tree.botLeft, within, cons);
                scanQuadTree(tree.botRight, within, cons);
                scanQuadTree(tree.topLeft, within, cons);
                scanQuadTree(tree.topRight, within, cons);
            }
        }
    }

    /** 圆是否完全包含矩形 (四角都在圆内)。 */
    public static boolean circleContainsRect(float x, float y, float radius, Rect rect){
        int count = 0;
        for(int i = 0; i < 4; i++){
            int mod = i % 2;
            int i2 = i / 2;
            float rx1 = rect.x + rect.width * mod;
            float ry1 = rect.y + rect.height * i2;
            if(Mathf.within(x, y, rx1, ry1, radius)) count++;
        }
        return count == 4;
    }

    /** 线段与圆的最近交点 (取自 BadWrong_ 的算法)。 */
    public static Vec2 intersectCircle(float x1, float y1, float x2, float y2, float cx, float cy, float cr){
        Vec2 out = Tmp.v4;
        if(!Intersector.nearestSegmentPoint(x1, y1, x2, y2, cx, cy, out).within(cx, cy, cr)) return null;

        float ox = x1 - cx, oy = y1 - cy;
        float vx = x2 - x1, vy = y2 - y1;
        float a = vx * vx + vy * vy;
        float b = 2 * (vx * ox + vy * oy);
        float c = ox * ox + oy * oy - cr * cr;
        float det = b * b - 4 * a * c;

        if(a <= Mathf.FLOAT_ROUNDING_ERROR || det < 0){
            return null;
        }else if(det == 0f){
            float t = -b / (2 * a);
            return out.set(x1 + t * vx, y1 + t * vy);
        }else{
            det = Mathf.sqrt(det);
            float t1 = (-b - det) / (2 * a);
            return out.set(x1 + t1 * vx, y1 + t1 * vy);
        }
    }

    /** 线段与矩形的最近交点 (返回 null 表示不相交)。 */
    public static Vec2 intersectRect(float x1, float y1, float x2, float y2, Rect rect){
        boolean intersected = false;
        float nearX = 0f, nearY = 0f;
        float lastDst = 0f;

        for(int i = 0; i < 4; i++){
            int mod = i % 2;
            float rx1 = i < 2 ? (rect.x + rect.width * mod) : rect.x;
            float rx2 = i < 2 ? (rect.x + rect.width * mod) : rect.x + rect.width;
            float ry1 = i < 2 ? rect.y : (rect.y + rect.height * mod);
            float ry2 = i < 2 ? rect.y + rect.height : (rect.y + rect.height * mod);

            Vec2 out = Tmp.v4;
            if(Intersector.intersectSegments(x1, y1, x2, y2, rx1, ry1, rx2, ry2, out)){
                float dst = Mathf.dst2(x1, y1, out.x, out.y);
                if(!intersected || dst < lastDst){
                    nearX = out.x;
                    nearY = out.y;
                    lastDst = dst;
                }
                intersected = true;
            }
        }

        if(rect.contains(x1, y1)){
            nearX = x1;
            nearY = y1;
            intersected = true;
        }

        return intersected ? Tmp.v2.set(nearX, nearY) : null;
    }

    /** 目标占据射线的"覆盖比例" (0~1, 用于放射状伤害衰减)。 */
    public static float inRayCastCircle(float x, float y, float[] in, Sized target){
        float amount = 0f;
        float hsize = target.hitSize() / 2f;
        int collision = 0;
        int isize = in.length;

        float dst = Mathf.dst(x, y, target.getX(), target.getY());
        float ang = Angles.angle(x, y, target.getX(), target.getY());
        float angSize = Mathf.angle(dst, hsize);

        int idx1 = (int)(((ang - angSize) / 360f) * isize + 0.5f);
        int idx2 = (int)(((ang + angSize) / 360f) * isize + 0.5f);

        for(int i = idx1; i <= idx2; i++){
            int mi = Mathf.mod(i, isize);
            float range = in[mi];
            if((dst - hsize) < range){
                amount += Mathf.clamp((range - (dst - hsize)) / hsize);
            }
            collision++;
        }
        return collision > 0 ? (amount / collision) : 0f;
    }

    /**
     * 从 (x,y) 向四周放射 res 条射线，被 stop 命中的方向记录遮挡距离到 out。
     * 可通过 ambient/edge/hit 回调做地形与建筑的附加处理 (核弹用)。
     */
    public static void rayCastCircle(float x, float y, float radius, Boolf<Tile> stop, Cons<Tile> ambient, Cons<Tile> edge, Cons<Building> hit, float[] out){
        Arrays.fill(out, radius);

        int res = out.length;
        IntSet collided = new IntSet(), collided2 = new IntSet();
        Seq<Building> buildings = new Seq<>();

        for(int i = 0; i < res; i++){
            final int fi = i;
            float ang = (i / (float)res) * 360f;
            Vec2 v2 = Tmp.v1.trns(ang, radius).add(x, y);
            float vx = v2.x, vy = v2.y;
            int tx1 = (int)(x / Vars.tilesize), ty1 = (int)(y / Vars.tilesize);
            int tx2 = (int)(vx / Vars.tilesize), ty2 = (int)(vy / Vars.tilesize);

            World.raycastEach(tx1, ty1, tx2, ty2, (rx, ry) -> {
                Tile tile = Vars.world.tile(rx, ry);
                boolean collide = false;

                if(tile != null && !tile.block().isAir() && stop.get(tile)){
                    Rect r2 = Tmp.r2;
                    tile.getBounds(r2);
                    r2.grow(0.1f);
                    Vec2 inter = intersectRect(x, y, vx, vy, r2);
                    if(inter != null){
                        if(tile.build != null && collided.add(tile.build.id)){
                            buildings.add(tile.build);
                        }
                        out[fi] = Mathf.dst(x, y, inter.x, inter.y);
                        collide = true;
                    }
                }

                if(tile != null && collided2.add(tile.pos())){
                    ambient.get(tile);
                    if(collide) edge.get(tile);
                }
                return collide;
            });
        }
        for(Building b : buildings){
            hit.get(b);
        }
    }

    /** 先升后降的三角曲线 (fin 在 [0,1], bias 为峰值位置)。 */
    public static float biasSlope(float fin, float bias){
        return fin < bias ? (fin / bias) : 1f - (fin - bias) / (1f - bias);
    }

    /** 带符号的角度差 (-180, 180]。 */
    public static float angleDistSigned(float a, float b){
        a += 360f;
        a %= 360f;
        b += 360f;
        b %= 360f;
        float d = Math.abs(a - b) % 360f;
        int sign = (a - b >= 0f && a - b <= 180f) || (a - b <= -180f && a - b >= -360f) ? 1 : -1;
        return (d > 180f ? 360f - d : d) * sign;
    }
}
