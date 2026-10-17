package zzw.map.objectives;

import arc.*;

/**
 * 全局剧情里程碑 (PU132 GlobalObjective 移植): 跨存档持久的位标记,
 * 常用于"完成某剧情后, 在其它地图/后续剧情里解锁新内容"。
 *
 * <p>用法: 剧情完成时 {@link #fire}, 后续判断 {@link #reached}。上限 64 个。</p>
 *
 * <p>★ 当前暂无定义里程碑 (PU132 原版仅 sectorAccretionComplete, 依赖其战役区块);
 * 后续制作战役剧情时在此追加枚举常量即可, 位存档 key 与旧版互通。</p>
 *
 * @author PU132 原作 (GlennFolker), 移植: zzw
 */
public enum GlobalObjective{
    ;

    private static final String settingsKey = "create.global-objective.status";
    private static long currentStatus = 0;

    static{
        update();
    }

    public static boolean reached(GlobalObjective objective){
        update();
        return (currentStatus & objective.value()) == objective.value();
    }

    public static boolean reached(GlobalObjective... objectives){
        for(GlobalObjective o : objectives){
            if(!reached(o)) return false;
        }
        return true;
    }

    public static void fire(GlobalObjective objective){
        if(reached(objective)) return;

        currentStatus |= objective.value();
        Core.settings.put(settingsKey, currentStatus);

        arc.Events.fire(objective);
    }

    private static void update(){
        currentStatus = Core.settings.getLong(settingsKey, 0);
    }

    public long value(){
        return 1L << ordinal();
    }
}
