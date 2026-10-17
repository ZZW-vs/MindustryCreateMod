package zzw.map.objectives;

import arc.func.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;
import zzw.map.cinematic.StoryNode;

import static java.lang.String.*;
import static mindustry.Vars.*;

/**
 * 单位点兵目标 (PU132 UnitPosObj 移植): {@link #count} 个满足 {@link #valid} 的
 * 单位进入 {@link #pos} 周围 {@link #radius} 范围即达成。
 *
 * <p>{@link #continuous} = true 时单位走出范围会被释放并从计数里减掉
 * (适合"护送到某地并保持驻留"类任务)。</p>
 *
 * @author PU132 原作 (GlennFolker), 移植: zzw
 */
@SuppressWarnings("unchecked")
public class UnitPosObj extends Objective{
    public int count = 1;
    public boolean continuous;

    public Cons2<UnitPosObj, Unit> spotted = (objective, unit) -> {};
    public Cons2<UnitPosObj, Unit> released = (objective, unit) -> {};
    public Boolf2<UnitPosObj, Unit> valid = (objective, unit) -> true;

    public Team team;
    public Vec2 pos = new Vec2();
    public float radius = 4f * tilesize;

    public final transient IntSet within = new IntSet();
    private static final IntSet tmp = new IntSet();

    public UnitPosObj(StoryNode node, String name, Cons<UnitPosObj> executor){
        super(node, name, executor);
        team = state.rules.defaultTeam;
    }

    @Override
    public void update(){
        super.update();

        float sqrRad = radius * radius;

        tmp.clear();
        Groups.unit.each(this::valid, u -> {
            if(within.contains(u.id)){
                if(continuous) tmp.add(u.id);
                return;
            }

            u.hitbox(Tmp.r1);
            if(
                Intersector.overlaps(Tmp.cr1.set(pos, radius), Tmp.r1) ||
                Intersector.intersectSegmentCircle(
                    Tmp.v1.set(u.lastX, u.lastY),
                    Tmp.v2.set(u.x, u.y),
                    pos, sqrRad
                )
            ){
                spotted.get(this, u);
                within.add(u.id);
            }
        });

        if(continuous){
            for(var it = tmp.iterator(); it.hasNext;){
                int id = it.next();
                Unit u = Groups.unit.getByID(id);
                if(u == null){
                    within.remove(id);
                    continue;
                }

                u.hitbox(Tmp.r1);
                if(!valid(u) || !Intersector.overlaps(Tmp.cr1.set(pos, radius), Tmp.r1)){
                    within.remove(id);
                    released.get(this, u);
                }
            }
        }

        completed = within.size >= count;
    }

    @Override
    public void reset(){
        super.reset();
        within.clear();
    }

    @Override
    public void save(StringMap map){
        super.save(map);
        map.put("pos", valueOf(zzw.util.Float2.construct(pos.x, pos.y)));
        map.put("radius", valueOf(radius));
        map.put("team", valueOf(team.id));
        map.put("count", valueOf(count));
        map.put("continuous", valueOf(continuous));

        Seq<Unit> units = new Seq<>(within.size);
        for(var it = within.iterator(); it.hasNext;){
            var unit = Groups.unit.getByID(it.next());
            if(unit != null && valid(unit)) units.add(unit);
        }

        map.put("within", mindustry.io.JsonIO.json.toJson(units.map(u -> valueOf(zzw.util.Float2.construct(u.x, u.y))), Seq.class, String.class));
    }

    @Override
    public void load(StringMap map){
        super.load(map);
        if(map.containsKey("pos")){
            long saved = map.getLong("pos");
            pos.set(zzw.util.Float2.x(saved), zzw.util.Float2.y(saved));
        }

        radius = map.getFloat("radius", radius);
        team = Team.get(map.getInt("team", team.id));
        count = map.getInt("count", count);
        continuous = !map.containsKey("continuous") ? continuous : map.getBool("continuous");

        within.clear();
        Seq<String> units = mindustry.io.JsonIO.json.fromJson(Seq.class, String.class, map.get("within", "[]"));
        for(String str : units){
            long pos = Long.parseLong(str);
            var unit = Groups.unit.find(u ->
                u != null &&
                Mathf.equal(u.x, zzw.util.Float2.x(pos)) &&
                Mathf.equal(u.y, zzw.util.Float2.y(pos))
            );

            if(unit != null) within.add(unit.id);
        }
    }

    public boolean valid(Unit unit){
        return unit.team == team && unit.isValid() && valid.get(this, unit);
    }
}
