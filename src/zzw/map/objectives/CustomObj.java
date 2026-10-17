package zzw.map.objectives;

import arc.func.*;
import zzw.map.cinematic.StoryNode;

/**
 * 自定义目标 (PU132 CustomObj 移植): 达成条件完全由 {@link #completer} 决定。
 *
 * <p>PU132 用 JS completer; 本移植版用 Java lambda, 只能通过
 * {@link ObjectiveTypes#register} 注册的代码工厂创建 (无法从 JSON 声明)。</p>
 *
 * @author PU132 原作 (GlennFolker), 移植: zzw
 */
public class CustomObj extends Objective{
    public final Boolf<CustomObj> completer;

    public CustomObj(StoryNode node, String name, Boolf<CustomObj> completer, Cons<CustomObj> executor){
        super(node, name, executor);
        this.completer = completer;
    }

    @Override
    public void update(){
        super.update();
        completed = completer.get(this);
    }
}
