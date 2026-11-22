package zzw.content.units.flame.unit;

import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.entities.Predict;
import mindustry.entities.Sized;
import mindustry.entities.units.AIController;
import mindustry.entities.units.WeaponMount;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Teamc;
import mindustry.gen.TimedKillc;
import mindustry.gen.Unit;
import mindustry.type.Weapon;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall.WallBuild;
import mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild;
import mindustry.world.blocks.defense.turrets.PowerTurret.PowerTurretBuild;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.defense.turrets.Turret.AmmoEntry;
import mindustry.world.blocks.defense.turrets.Turret.TurretBuild;
import mindustry.world.meta.BlockFlag;
import zzw.content.units.flame.FlameOutSFX;
import zzw.content.units.flame.HierarchyArray;
import zzw.content.units.flame.unit.empathy.EmpathyDamage;
import zzw.content.units.flame.unit.weapons.EndDespondencyWeapon.DespondencyMount;
import zzw.content.units.flame.unit.weapons.EndDespondencyWeapon;

import java.util.Iterator;

/**
 * 消沉 AI (移植自 flame.unit.DespondencyAI)。
 *
 * <p>核心行为:</p>
 * <ol>
 *   <li>每帧维护 8 个按 "DPS + 血量 - 距离" 排序的目标;</li>
 *   <li>普通武器按射程从目标表轮流分配；防空武器自行索敌;</li>
 *   <li>当目标强度达到处决阈值 (总评分 &gt;1000000，或大型目标 &gt;200000)
 *       且进入 80% 射程时，切换到主武器处决阶段;</li>
 *   <li>处决结束后进入 10 秒装填，失败则 2 秒后可再尝试。</li>
 * </ol>
 *
 * <p>★ v160 适配: FO 用 {@code flame.Utils} 的静态变量，本移植版已移除;
 * 这里只使用 {@link Tmp} 槽位，且不跨方法保存。</p>
 */
public class DespondencyAI extends AIController{
    Teamc moveTarget;
    HierarchyArray<Teamc> targets = new HierarchyArray<>(8);
    boolean death;
    float deathTime = 0f;
    float reloadTime = 0f;
    float distractionTime = 0f;
    boolean altTarget = false;

    @Override
    public void updateUnit(){
        updateTargeting();
        updateMovement();
    }

    @Override
    public void updateMovement(){
        Teamc move = ((moveTarget != null && !death && (unit.within(moveTarget, unit.type.maxRange + (moveTarget instanceof Sized s ? s.hitSize() / 2f : 0f)) || altTarget)) || target == null) ? moveTarget : target;
        float near = 720f;

        if(move != null && deathTime < 5f * 60f){
            vec.set(move.x() - unit.x, move.y() - unit.y).limit(unit.speed() * (death ? 0.5f : 1f));

            if(vec.isNaN() || vec.isInfinite() || vec.isZero()) return;

            unit.lookAt(move);

            if(!move.within(unit, near)){
                unit.movePref(vec);
            }else if(move.within(unit, near / 2f)){
                unit.move(-vec.x, -vec.y);
            }
        }else if(move != null){
            unit.lookAt(move);
        }
    }

    /** 主武器处决时返回当前目标，其余时间为 null (防空武器据此避开主目标)。 */
    public Teamc mainTarget(){
        return death ? target : null;
    }

    @Override
    public void updateWeapons(){
        DespondencyUnitType type = (DespondencyUnitType)unit.type;
        float rotation = unit.rotation - 90;
        WeaponMount main = unit.mounts[type.mainWeaponIdx];
        DespondencyMount dm = (DespondencyMount)main;

        unit.isShooting = false;
        if(reloadTime > 0) reloadTime -= Time.delta;

        if(!death || (target == null && dm.stage <= 2) || reloadTime > 0){
            main.shoot = false;
            main.target = null;

            deathTime = 0f;
            death = false;
            int idx = 0;
            if(targets.size > 0){
                noTargetTime = 0f;
                if(moveTarget != null && !altTarget){
                    boolean contains = false;
                    for(Teamc tar : targets){
                        if(!moveTarget.within(tar, 280f)){
                            contains = true;
                            break;
                        }
                    }
                    if(contains){
                        distractionTime += Time.delta;
                        if(distractionTime > 2f * 60){
                            moveTarget = targets.get(0);
                            altTarget = true;
                        }
                    }else if(distractionTime > 0){
                        distractionTime -= Time.delta;
                    }
                }

                for(WeaponMount mount : unit.mounts){
                    Weapon weapon = mount.weapon;
                    float wrange = weapon.range();

                    if(!weapon.controllable || weapon.autoTarget || mount == main || weapon.noAttack) continue;

                    float mountX = unit.x + Angles.trnsx(rotation, weapon.x, weapon.y), mountY = unit.y + Angles.trnsy(rotation, weapon.x, weapon.y);

                    Teamc tar;
                    int count = 0;
                    if(!altTarget || moveTarget == null){
                        tar = null;
                        while(count < targets.size){
                            Teamc t = targets.get(Mathf.mod(count + idx, targets.size));
                            if(unit.within(t, wrange + (t instanceof Sized si ? si.hitSize() / 2f : 0f))){
                                tar = t;
                                idx++;
                                break;
                            }
                            count++;
                        }
                        if(tar == null){
                            tar = targets.get(Mathf.mod(idx, targets.size));
                            idx++;
                        }
                    }else{
                        tar = moveTarget;
                    }

                    mount.target = tar;

                    boolean shoot = false;

                    if(tar != null){
                        shoot = tar.within(mountX, mountY, wrange + (tar instanceof Sized s ? s.hitSize() / 2f : 0f)) && shouldShoot();

                        Vec2 to = weapon.bullet != null && weapon.bullet.speed > 0.001f ? Predict.intercept(unit, mount.target, weapon.bullet.speed) : Tmp.v1.set(tar.x(), tar.y());
                        mount.aimX = to.x;
                        mount.aimY = to.y;
                    }

                    mount.shoot = mount.rotate = shoot;

                    unit.isShooting |= mount.shoot;

                    if(shoot){
                        unit.aimX = mount.aimX;
                        unit.aimY = mount.aimY;
                    }
                }
            }else{
                noTargetTime += Time.delta;
                distractionTime = 0f;

                for(WeaponMount mount : unit.mounts){
                    Weapon weapon = mount.weapon;

                    if(!weapon.controllable || weapon.autoTarget || mount == main || weapon.noAttack) continue;
                    mount.shoot = false;
                    mount.rotate = false;

                    if(noTargetTime >= rotateBackTimer){
                        float mountX = unit.x + Angles.trnsx(rotation, weapon.x, weapon.y), mountY = unit.y + Angles.trnsy(rotation, weapon.x, weapon.y);

                        mount.rotate = true;
                        Tmp.v1.trns(unit.rotation + mount.weapon.baseRotation, 5f);
                        mount.aimX = mountX + Tmp.v1.x;
                        mount.aimY = mountY + Tmp.v1.y;
                    }
                }
            }
        }else{
            for(WeaponMount mount : unit.mounts){
                Weapon weapon = mount.weapon;
                float mountX = unit.x + Angles.trnsx(rotation, weapon.x, weapon.y), mountY = unit.y + Angles.trnsy(rotation, weapon.x, weapon.y);

                mount.shoot = false;
                mount.rotate = true;

                Tmp.v1.trns(unit.rotation + mount.weapon.baseRotation, 5f);
                mount.aimX = mountX + Tmp.v1.x;
                mount.aimY = mountY + Tmp.v1.y;
            }

            main.shoot = true;
            if(target != null) main.target = target;

            deathTime += Time.delta;

            if(deathTime >= 30f){
                dm.activeTime += Time.delta;
            }
        }
    }

    public void endDeath(){
        deathTime = 0f;
        reloadTime = 10f * 60f;
        death = false;
    }

    public void endDeathFailed(){
        deathTime = 0f;
        reloadTime = 2f * 60f;
        death = false;
    }

    @Override
    public void updateTargeting(){
        if(target != null && (target instanceof Healthc h && !h.isValid())){
            target = null;
            timer.reset(0, 0f);
        }
        if(moveTarget != null && !((Healthc)moveTarget).isValid()){
            moveTarget = null;
            distractionTime = 0f;
            altTarget = false;
            timer.reset(0, 0f);
        }

        Iterator<Teamc> iter = targets.iterator();
        while(iter.hasNext()){
            Teamc t = iter.next();
            if(!((Healthc)t).isValid()){
                iter.remove();
            }
        }

        if(target instanceof Unit u && !death && !EmpathyDamage.containsExclude(u.id) && unit.within(u, unit.range() * 0.8f + u.hitSize / 2.5f) && reloadTime <= 0f){
            float scr = (FlameOutSFX.inst.getUnitDps(u.type) + u.maxHealth * u.healthMultiplier);
            if(EmpathyDamage.isNaNInfinite(scr) || scr > 1000000f || (u.hitSize > 100f && scr > 200000f)){
                death = true;
            }
        }

        if(retarget() && !death){
            double score = 0, score2 = 0;
            Teamc t = null;
            Teamc mt = null;
            targets.clear();
            for(TeamData data : Vars.state.teams.present){
                if(data.team != unit.team && data.team != Team.derelict){
                    for(Unit u : data.units){
                        if(u instanceof TimedKillc) continue;
                        double s = (((double)FlameOutSFX.inst.getUnitDps(u.type)) + (double)(u.maxHealth * u.healthMultiplier)) - (u.dst(unit) / 1000f);
                        if(t == null || s > score){
                            t = u;
                            score = s;
                        }
                        if((mt == null || s > score2) && (u.type.speed < 2.2f) && (s > 200000f || (u.hitSize > 100f))){
                            mt = u;
                            score2 = s;
                        }

                        float fs = (float)s;
                        if(EmpathyDamage.isNaNInfinite(fs)){
                            fs = Float.MAX_VALUE;
                        }

                        targets.add(u, fs);
                    }
                    for(Building build : data.buildings){
                        if(build.tile.build != build) continue;
                        float s = build.maxHealth;
                        Block block = build.block;

                        if(build instanceof TurretBuild tb){
                            float max = 0f;
                            float reloadSpeed = ((Turret)tb.block).reload;
                            if(!(build instanceof PowerTurretBuild)){
                                for(AmmoEntry ammo : tb.ammo){
                                    max = (Math.max(max, FlameOutSFX.inst.getBulletDps(ammo.type())) / reloadSpeed) * ammo.amount;
                                }
                            }else{
                                max = Math.max(max, FlameOutSFX.inst.getBulletDps(((mindustry.world.blocks.defense.turrets.PowerTurret)tb.block).shootType));
                            }
                            max *= build.efficiency;

                            s += max;
                        }else if(block.flags.contains(BlockFlag.generator) || block.flags.contains(BlockFlag.factory)){
                            s *= 2f;
                        }
                        s -= unit.dst(build) / 1000f;

                        if(build instanceof WallBuild){
                            s /= 120;
                        }
                        if(mt == null || s > score2){
                            mt = build;
                            score2 = s;
                        }

                        targets.add(build, s);
                    }
                }
            }

            target = t;
            if(!altTarget) moveTarget = mt;
        }
        updateWeapons();
    }

    @Override
    public boolean retarget(){
        return timer.get(timerTarget, (target == null || targets.size <= 0) ? 40 : 90);
    }

    @Override
    public boolean isLogicControllable(){
        return false;
    }
}
