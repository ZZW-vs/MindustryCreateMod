package zzw.content.units.abilities;

import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.entities.abilities.RepairFieldAbility;
import mindustry.gen.Unit;

/**
 * 修复场扫描 + 对敌伤害
 * 治疗部分完全沿用 RepairFieldAbility (曲线/百分比/冷却均不变),
 * 额外在每次触发时对范围内敌方单位造成与治疗量换算等量的伤害, 对建筑再乘 buildingDamageMultiplier (默认 180%).
 */
public class RepairFieldDamageAbility extends RepairFieldAbility {
    /** 建筑伤害倍率 */
    public float buildingDamageMultiplier = 1.8f;

    private float damageTimer = 0f;

    public RepairFieldDamageAbility(float amount, float reload, float range) {
        super(amount, reload, range);
    }

    @Override
    public void update(Unit unit) {
        // 先执行原版治疗扫描, 保持治疗行为不变
        super.update(unit);

        damageTimer += Time.delta;
        if (damageTimer >= reload) {
            damageTimer = 0f;

            Units.nearbyEnemies(unit.team, unit.x, unit.y, range, other ->
                other.damage(amount + healPercent / 100f * other.maxHealth()));

            Vars.indexer.eachBlock(null, unit.x, unit.y, range,
                build -> build.team != unit.team && build.health > 0f && build.isValid(),
                build -> build.damage((amount + healPercent / 100f * build.maxHealth()) * buildingDamageMultiplier));
        }
    }
}
