package zzw.util;

import arc.struct.IntMap;
import arc.util.Time;
import mindustry.gen.Unit;

/**
 * 复活封印表 —— 被终局炮台湮灭的单位在被彻底移除前不允许任何代码复活。
 *
 * <p>本项目与 FlameOut 等模组的 Boss 单位都有 "死亡拒绝" 保护:
 * 血量归零时先检查自己的独立血量台账 (trueHealth / lastHealth), 台账还有余量
 * 就回充血量、播放红色蓄力特效并复活。即使终局炮台清零了这些字段, 单位在
 * {@code update()} 里仍可能把 {@code dead} 重置为 false, 或在 {@code remove()}
 * 里再次拒绝移除。</p>
 *
 * <p>因此湮灭流程会先调用 {@link #seal(Unit)} 打上封印标记, 各单位实体在
 * 死亡判定 (denyDeath / remove / dead 回填) 时查询 {@link #isSealed(Unit)},
 * 命中封印就直接跳过复活逻辑, 保证一击彻底消灭。</p>
 *
 * <p>封印按单位 id + 时间戳记录, 10 秒后自动失效, 避免 id 被回收后误封新单位。</p>
 */
public final class ReviveSeal {

    /** 封印持续时长 (tick) */
    public static final float DURATION = 600f;

    private static final IntMap<Float> sealed = new IntMap<>();

    private ReviveSeal(){
    }

    /** 给单位打上 "禁止复活" 封印。 */
    public static void seal(Unit u){
        if(u != null) sealed.put(u.id, Time.time);
    }

    /** 单位是否处于封印期内。 */
    public static boolean isSealed(Unit u){
        if(u == null || sealed.size == 0) return false;
        float t = sealed.get(u.id, Float.NEGATIVE_INFINITY);
        if(t == Float.NEGATIVE_INFINITY) return false;
        if(Time.time - t > DURATION){
            sealed.remove(u.id);
            return false;
        }
        return true;
    }

    /** 解除封印 (调试/测试用)。 */
    public static void clear(Unit u){
        if(u != null) sealed.remove(u.id);
    }
}
