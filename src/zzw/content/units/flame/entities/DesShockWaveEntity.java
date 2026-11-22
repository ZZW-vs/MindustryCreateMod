package zzw.content.units.flame.entities;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Circle;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.Teamc;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import zzw.content.units.flame.FlamePal;
import zzw.content.units.flame.FlameUtils;
import zzw.content.units.flame.unit.empathy.EmpathyDamage;
import zzw.content.units.flame.unit.weapons.EndDespondencyWeapon;

/**
 * 消沉清杂冲击波 (移植自 flame.entities.DesShockWaveEntity)。
 *
 * <p>以 2000 范围的扩张环，逐帧只结算"新进入环带"的脆弱单位与炮塔，
 * 对它们直接结算最大生命 + 1000 的处决伤害。</p>
 */
public class DesShockWaveEntity extends DrawEntity{
    static final float range = 2000f;

    float time = 0f;
    Team team = Team.derelict;
    Teamc targeting;

    public static void create(Team team, Teamc target, float x, float y){
        DesShockWaveEntity w = new DesShockWaveEntity();
        w.x = x;
        w.y = y;
        w.team = team;
        w.targeting = target;

        w.add();
    }

    @Override
    public void update(){
        float lrange = time > 0 ? getRange() : 0f;
        time += Time.delta / 120f;
        float crange = getRange();

        Circle c1 = new Circle();
        Seq<Building> found = new Seq<>();

        for(TeamData data : Vars.state.teams.present){
            if(data.team != team){
                FlameUtils.QuadTreeHandler handler = (rect, tree) -> {
                    if(tree){
                        c1.set(x, y, crange);
                        return (lrange <= 0f || !FlameUtils.circleContainsRect(x, y, lrange, rect)) && Intersector.overlaps(c1, rect);
                    }
                    float min = Math.min(rect.width, rect.height) / 2f;
                    float mx = rect.x + rect.width / 2f;
                    float my = rect.y + rect.height / 2f;

                    return Mathf.within(x, y, mx, my, crange + min) && !Mathf.within(x, y, mx, my, lrange - min);
                };

                if(data.unitTree != null){
                    FlameUtils.scanQuadTree(data.unitTree, handler, u -> {
                        if(EndDespondencyWeapon.isStray(u) && u != targeting){
                            EmpathyDamage.damageUnit(u, u.maxHealth + 1000f, true, null);
                        }
                    });
                }
                if(data.turretTree != null){
                    found.clear();
                    FlameUtils.scanQuadTree(data.turretTree, handler, bl -> {
                        if(bl != targeting){
                            found.add(bl);
                        }
                    });
                    for(Building bl : found){
                        EmpathyDamage.damageBuildingRaw(bl, bl.maxHealth + 1000f, true, null);
                    }
                    found.clear();
                }
            }
        }

        if(time >= 1f){
            remove();
        }
    }

    float getRange(){
        return 100f + Interp.pow2Out.apply(time) * range;
    }

    @Override
    public void draw(){
        float fout = 1f - time;
        Draw.z(Layer.darkness + 0.1f);
        Draw.color(FlamePal.redLight);
        Lines.stroke(30f * fout);
        Lines.circle(x, y, getRange());

        Rand r = new Rand(id);
        for(int i = 0; i < 20; i++){
            float rot = r.random(360f);
            Vec2 v = Tmp.v1.trns(rot, getRange()).add(x, y);
            Drawf.tri(v.x, v.y, 30f * fout, 100f + 500f * time, rot + 180f);
        }
        Draw.reset();
    }

    @Override
    public float clipSize(){
        return 99999999f;
    }
}
