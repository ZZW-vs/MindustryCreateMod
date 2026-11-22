package zzw.content.units.flame.unit.weapons;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.entities.Sized;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Healthc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.Weapon;
import zzw.content.units.flame.FlamePal;
import zzw.content.units.flame.FlameUtils;
import zzw.content.units.flame.graphics.GraphicUtils;
import zzw.content.units.flame.unit.DespondencyAI;

import java.util.Arrays;

/**
 * 消沉防空武器 (移植自 flame.unit.weapons.EndAntiAirWeapon)。
 *
 * <p>锁定范围内高速/空中目标，在装填完成后对全部锁定目标同时结算一次
 * "最大生命/15 + 9000" 的伤害，并绘制红色+白色的锁定连线。</p>
 *
 * <p>★ v160 适配: 原版用 {@code t.angleTo(x,y)}，v160 已无该方法，
 * 改用 {@link Angles#angle(float, float, float, float)}。</p>
 */
public class EndAntiAirWeapon extends Weapon{
    protected float targetingRange = 700f;

    static Effect hitAirEffect = new Effect(10f, 800f * 3f, e -> {
        Draw.color(FlamePal.red);
        GraphicUtils.diamond(e.x, e.y, 5f * e.fout(), e.color.r * 2f, e.rotation);
    });

    public EndAntiAirWeapon(String name){
        super(name);

        autoTarget = true;
        controllable = false;

        mountType = EndAntiAirMount::new;
    }

    @Override
    public void update(Unit unit, WeaponMount mount){
        boolean can = unit.canShoot();

        float mountX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
                mountY = unit.y + Angles.trnsy(unit.rotation - 90, x, y);

        EndAntiAirMount aa = (EndAntiAirMount)mount;
        Teamc main = unit.controller() instanceof DespondencyAI ai ? ai.mainTarget() : null;

        if((mount.retarget -= Time.delta) <= 0f){
            mount.retarget = aa.targetCount <= 0 ? targetInterval : targetSwitchInterval;

            FlameUtils.scanEnemies(unit.team, mountX, mountY, targetingRange, true, false, u -> {
                if((u instanceof Unit un && (un.type.speed > (un.type.circleTarget ? 1.1f : 2.4f))) && u != main && !aa.contains(u)){
                    aa.addTarget(u);
                }
            });
        }

        for(int i = 0; i < aa.targetCount; i++){
            Teamc t = aa.targets[i];

            if(t == null || !t.isAdded() || (t instanceof Healthc h && !h.isValid()) || !Mathf.within(mountX, mountY, t.x(), t.y(), targetingRange + (t instanceof Sized s ? s.hitSize() / 2f : 0f))){
                aa.removeIdx(i);
                i--;
                continue;
            }

            aa.targetWarmups[i] = Mathf.approachDelta(aa.targetWarmups[i], 1f, 1f / 30f);
        }

        if(can){
            if(aa.targetCount > 0){
                mount.reload = Math.min(mount.reload + Time.delta * unit.reloadMultiplier, reload);
            }else{
                mount.reload = 0f;
            }
        }

        if(mount.reload >= reload){
            for(int i = 0; i < aa.targetCount; i++){
                Teamc t = aa.targets[i];

                float mx = (mountX + t.x()) / 2f;
                float my = (mountY + t.y()) / 2f;
                float dst = Mathf.dst(mountX, mountY, t.x(), t.y());

                Tmp.c1.set(0f, 0f, 0f, 0f);
                Tmp.c1.r = dst;
                hitAirEffect.at(mx, my, Angles.angle(t.x(), t.y(), mountX, mountY), Tmp.c1);

                if(t instanceof Healthc h){
                    h.damage(h.maxHealth() / 15f + 9000f);
                }
            }
            mount.reload = 0f;
        }
    }

    @Override
    public void draw(Unit unit, WeaponMount mount){
        super.draw(unit, mount);
        float z = Draw.z();

        float mountX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
                mountY = unit.y + Angles.trnsy(unit.rotation - 90, x, y);

        EndAntiAirMount aa = (EndAntiAirMount)mount;

        for(int c = 0; c < 2; c++){
            Color col = c == 0 ? FlamePal.red : Color.white;
            float str = c == 0 ? 1.25f : 0.5f;
            float layer = c == 0 ? Layer.bullet : Layer.bullet + 0.01f;
            Draw.z(layer);

            Draw.color(col);
            for(int i = 0; i < aa.targetCount; i++){
                Teamc t = aa.targets[i];
                float warm = aa.targetWarmups[i];

                float width = warm * Interp.pow2Out.apply(mount.reload / reload) * (3f + Mathf.absin(Mathf.pow(mount.reload / reload, 4f) * reload, 1.5f, 2.5f) + Mathf.absin(3f, 0.2f));

                Lines.stroke(width * str);
                Lines.line(mountX, mountY, t.x(), t.y(), false);
                Fill.circle(mountX, mountY, width * str);
                Fill.circle(t.x(), t.y(), width * str);
            }
        }
        Draw.z(z);
    }

    static class EndAntiAirMount extends WeaponMount{
        Teamc[] targets = new Teamc[16];
        float[] targetWarmups = new float[16];
        int targetCount = 0;

        EndAntiAirMount(Weapon w){
            super(w);
        }

        boolean contains(Teamc target){
            for(int i = 0; i < targetCount; i++){
                if(targets[i] == target) return true;
            }
            return false;
        }

        void addTarget(Teamc target){
            if(targetCount >= targets.length) resize();
            targets[targetCount] = target;
            targetWarmups[targetCount] = 0f;

            targetCount++;
        }

        void removeTarget(Teamc target){
            for(int i = 0; i < targetCount; i++){
                if(targets[i] == target){
                    removeIdx(i);
                    break;
                }
            }
        }

        void removeIdx(int i){
            Teamc last = targets[targetCount - 1];
            float lastf = targetWarmups[targetCount - 1];
            targets[i] = last;
            targetWarmups[i] = lastf;
            targets[targetCount - 1] = null;
            targetWarmups[targetCount - 1] = 0f;

            targetCount--;
        }

        void clear(){
            for(int i = 0; i < targetCount; i++){
                targets[i] = null;
            }

            targetCount = 0;
        }

        void resize(){
            int nsize = (int)(targets.length * 1.75f);

            targets = Arrays.copyOf(targets, nsize);
            targetWarmups = Arrays.copyOf(targetWarmups, nsize);
        }
    }
}
