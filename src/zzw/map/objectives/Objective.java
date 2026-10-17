package zzw.map.objectives;

import arc.func.*;
import arc.struct.*;
import zzw.map.cinematic.StoryNode;

import static java.lang.String.*;

/**
 * 剧情目标基类 (PU132 unity.map.objectives.Objective 移植, 裁剪版)。
 *
 * <p>一个 StoryNode 持有若干 Objective, 全部 {@link #completed() 达成} 后节点完成,
 * 推进到子节点。生命周期:</p>
 * <ul>
 *   <li>{@link #init()} — 进入演出状态时调用一次 (可用来创建 HUD 面板);</li>
 *   <li>{@link #update()} — 每帧调用, 子类在此检测 {@code completed} 条件;</li>
 *   <li>{@link #execute()} — completed 后由节点调用, 执行 {@link #executor} 后 {@link #stop()};</li>
 *   <li>{@link #save(StringMap)} / {@link #load(StringMap)} — 存档持久化。</li>
 * </ul>
 *
 * <p>★ 与 PU132 的差异: 裁掉了 Rhino JS 脚本 (ext/FieldTranslator) 与剧情编辑器,
 * 目标改为纯 Java 代码或 ObjectiveTypes 注册的 JSON 字段构造。</p>
 *
 * @author PU132 原作 (GlennFolker), 移植: zzw
 */
@SuppressWarnings("unchecked")
public abstract class Objective{
    /** 本目标的名字 (存档时作为键, 同一节点内必须唯一)。 */
    public final transient String name;

    /** 目标达成后执行的回调 (典型用途: 弹对话 / 触发下一幕)。 */
    public final Cons<Objective> executor;
    public final transient StoryNode node;

    protected transient boolean
        executed = false,
        completed = false;

    public Cons<Objective> init = objective -> {};
    public Cons<Objective> update = objective -> {};
    public Cons<Objective> draw = objective -> {};

    public <T extends Objective> Objective(StoryNode node, String name, Cons<T> executor){
        this.name = name;
        this.node = node;
        this.executor = (Cons<Objective>)executor;
    }

    public void init(){
        init.get(this);
    }

    public void update(){
        update.get(this);
    }

    public void draw(){
        draw.get(this);
    }

    /** 是否还需要每帧 update (未执行完且未标记完成)。 */
    public boolean shouldUpdate(){
        return !executed && !completed;
    }

    /** 是否已达成、等待执行 executor。 */
    public boolean qualified(){
        return !executed && completed;
    }

    public boolean shouldDraw(){
        return shouldUpdate();
    }

    public <T extends Objective> T init(Cons<T> init){
        this.init = (Cons<Objective>)init;
        return (T)this;
    }

    public <T extends Objective> T update(Cons<T> update){
        this.update = (Cons<Objective>)update;
        return (T)this;
    }

    public <T extends Objective> T draw(Cons<T> draw){
        this.draw = (Cons<Objective>)draw;
        return (T)this;
    }

    public void save(StringMap map){
        map.put("executed", valueOf(executed));
        map.put("completed", valueOf(completed));
    }

    public void load(StringMap map){
        executed = map.getBool("executed");
        completed = map.getBool("completed");
    }

    public void reset(){
        completed = false;
        executed = false;
    }

    /** 执行 executor 并停止本目标 (completed + executed)。 */
    public void execute(){
        executor.get(this);
        stop();
    }

    public boolean completed(){
        return completed;
    }

    public boolean executed(){
        return executed;
    }

    public void stop(){
        completed = true;
        executed = true;
    }
}
