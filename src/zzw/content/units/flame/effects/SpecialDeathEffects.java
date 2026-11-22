package zzw.content.units.flame.effects;

import arc.audio.Sound;
import arc.graphics.Color;
import arc.math.Angles;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.ctype.MappableContent;
import mindustry.entities.Effect;
import mindustry.entities.Sized;
import mindustry.gen.Building;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import zzw.content.units.flame.FlamePal;
import zzw.content.units.flame.FlameSounds;

/**
 * 单位/建筑死亡演出配置 (移植自 flame.effects.SpecialDeathEffects)。
 *
 * <p>★ P1 阶段说明: FO 的死亡演出最终由 FragmentationBatch / VaporizeBatch 等
 * 四套 Batch 框架生成实体碎片。这些框架在 P3 阶段完整接入；当前先用
 * {@link FlameFX#heavyDebris}、{@link FlameFX#destroySparks} 与
 * {@link FlameFX#fragmentExplosion} 组合出等价的爆炸观感，保证子弹功能完整。</p>
 */
public class SpecialDeathEffects{
    public static ObjectMap<String, SpecialDeathEffects> nameMap = new ObjectMap<>();
    public static SpecialDeathEffects def, blood;

    public Effect debrisEffect = FlameFX.heavyDebris, sparkEffect = FlameFX.destroySparks, explosionEffect = Fx.none;
    public boolean solid = true;
    public Sound deathSound = Sounds.wind, primaryDeathSound = Sounds.none;

    public static void load(){
        def = new SpecialDeathEffects();
        blood = new SpecialDeathEffects(){{
            explosionEffect = FlameFX.fragmentExplosion;
            deathSound = FlameSounds.expOrganic;
        }};

        nameMap.put("flameout-despondency", new SpecialDeathEffects(){{
            explosionEffect = FlameFX.fragmentExplosion;
            deathSound = FlameSounds.expExotic;
        }});
    }

    /** 按内容名查找死亡配置；未注册的内容回落到默认配置 (巨型单位回落到血肉爆炸)。 */
    public static SpecialDeathEffects get(MappableContent content){
        SpecialDeathEffects ef = nameMap.get(content.name);
        if(ef == null){
            ef = def != null ? def : (def = new SpecialDeathEffects());
            if(content instanceof UnitType u && (u.health >= 4000000f || !Float.isFinite(u.health))){
                if(blood == null) blood = ef;
                return blood;
            }
        }
        return ef;
    }

    public static SpecialDeathEffects get(String name){
        SpecialDeathEffects g = nameMap.get(name);
        return g != null ? g : (def != null ? def : (def = new SpecialDeathEffects()));
    }

    /** 非实体化死亡 (无法碎片化时的回退: 直接爆炸)。 */
    public void cutAlt(Unit u){
        effect().at(u.x, u.y, u.hitSize / 2f);
        deathSound.at(u.x, u.y, 1f, 1f);
    }

    public void hit(Sized ent, float x, float y, float rotation, float scl){
        effect().at(x, y, (ent.hitSize() / 2f) * scl);
    }

    public void deathUnit(Unit u, float x, float y, float rotation){
        debrisEffect.at(u.x, u.y, rotation, u.type.outlineColor, u.hitSize / 2f);
        effect().at(u.x, u.y, u.hitSize / 2f);
        (primaryDeathSound != Sounds.none ? primaryDeathSound : deathSound).at(u.x, u.y, 1f, 1.5f);

        Tmp.v1.trns(rotation - 180f, u.hitSize / 2f).add(u.x, u.y);
        sparkEffect.at(Tmp.v1.x, Tmp.v1.y, rotation, u.hitSize / 2f);

        // P1 观感回退: 用真实贴图碎片粒子表现碎片化, P3 换回 FragmentationBatch。
        FlameFX.simpleFragmentation.at(u.x, u.y, rotation, u.type.outlineColor, u.type.region);
    }

    public void deathBuilding(Building b, float x, float y, float rotation){
        debrisEffect.at(b.x, b.y, b.angleTo(x, y) + 180f, b.block.outlineColor, b.hitSize() / 2f);

        Tmp.v1.trns(rotation - 180f, b.hitSize() / 2f).add(b.x, b.y);
        sparkEffect.at(Tmp.v1.x, Tmp.v1.y, rotation, b.hitSize() / 2f);

        (primaryDeathSound != Sounds.none ? primaryDeathSound : deathSound).at(b.x, b.y, 1f, 1.5f);
        effect().at(b.x, b.y, b.hitSize() / 2f);
    }

    private Effect effect(){
        return explosionEffect != Fx.none ? explosionEffect : FlameFX.fragmentExplosion;
    }
}
