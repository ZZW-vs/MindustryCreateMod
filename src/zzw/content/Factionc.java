package zzw.content;

import mindustry.gen.Building;
import mindustry.gen.Entityc;

/**
 * 派系实体接口 (移植自 PU132 {@code FactionComp} / 生成的 {@code Factionc})。
 *
 * <p>单位实现 {@link #faction()} 后就能被 {@link FactionMeta} 按派系查询,
 * 并可用 {@link #isSameFaction(Entityc)} 判定两个实体是否属于同一派系
 * (建筑取它的 {@code block} 归属)。</p>
 */
public interface Factionc{
    /** 本实体所属派系 (一般是 {@code FactionMeta.map(type)})。 */
    Faction faction();

    /** 两个实体是否属于同一派系 (PU132 {@code FactionComp.isSameFaction})。 */
    default boolean isSameFaction(Entityc other){
        if(other instanceof Building build){
            return faction() == FactionMeta.map(build.block);
        }
        return other instanceof Factionc fac && faction() == fac.faction();
    }
}
