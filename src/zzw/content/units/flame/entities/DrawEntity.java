package zzw.content.units.flame.entities;

import arc.math.geom.Position;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.gen.Building;
import mindustry.gen.Drawc;
import mindustry.gen.Groups;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;

/**
 * 可绘制的轻量实体 (移植自 flame.entities.DrawEntity)。
 *
 * <p>同时加入 {@link Groups#all} (update) 与 {@link Groups#draw} (draw)，
 * 供碎片/冲击波等纯视觉实体使用。</p>
 */
public class DrawEntity extends BasicEntity implements Drawc{
    public float x, y;

    @Override
    public void draw(){
    }

    @Override
    public float clipSize(){
        return Float.MAX_VALUE;
    }

    @Override
    public Floor floorOn(){
        Tile tile = tileOn();
        return tile == null || tile.block() != Blocks.air ? (Floor)Blocks.air : tile.floor();
    }

    @Override
    public Block blockOn(){
        Tile tile = tileOn();
        return tile == null ? Blocks.air : tile.block();
    }

    @Override
    public Building buildOn(){
        return Vars.world.buildWorld(x, y);
    }

    @Override
    public Tile tileOn(){
        return Vars.world.tileWorld(x, y);
    }

    @Override
    public boolean onSolid(){
        Tile tile = tileOn();
        return tile == null || tile.solid();
    }

    @Override
    public float getX(){
        return x;
    }

    @Override
    public float getY(){
        return y;
    }

    @Override
    public int tileX(){
        return World.toTile(x);
    }

    @Override
    public int tileY(){
        return World.toTile(y);
    }

    @Override
    public void set(Position p){
        set(p.getX(), p.getY());
    }

    @Override
    public void set(float x, float y){
        this.x = x;
        this.y = y;
    }

    @Override
    public void trns(Position p){
        trns(p.getX(), p.getY());
    }

    @Override
    public void trns(float x, float y){
        this.x += x;
        this.y += y;
    }

    @Override
    public void x(float v){
        x = v;
    }

    @Override
    public float x(){
        return x;
    }

    @Override
    public void y(float v){
        y = v;
    }

    @Override
    public float y(){
        return y;
    }

    @Override
    protected void addGroup(){
        super.addGroup();
        Groups.draw.add(this);
    }

    @Override
    protected void removeGroup(){
        super.removeGroup();
        Groups.draw.remove(this);
    }

    @Override
    public void afterReadAll(){
    }

    @Override
    public void beforeWrite(){
    }
}
