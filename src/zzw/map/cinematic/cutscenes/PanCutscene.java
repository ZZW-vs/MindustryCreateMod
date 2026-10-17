package zzw.map.cinematic.cutscenes;

import arc.*;
import arc.func.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import zzw.map.cinematic.Cutscene;

/**
 * 镜头平移演出 (PU132 PanCutscene 移植)。
 *
 * <p>先把镜头平滑插值到目标位置 {@link #pos}, 停留 {@link #endDelay} 后结束。
 * 可通过 {@link #moved} / {@link #arrived} 在"即将开始移动"和"即将到达"时收到回调
 * (常用于配合 Speeches 对话的显示/收起时机)。</p>
 *
 * @author PU132 原作 (GlennFolker), 移植: zzw
 */
@SuppressWarnings("unchecked")
public class PanCutscene extends Cutscene{
    public final Pos pos;
    public final Interp interp;
    public final float delay, panDuration, endDelay;

    private Vec2 initPos;
    private float initTime = -1f;

    private Cons<PanCutscene> moved;
    private Cons<PanCutscene> arrived;
    private float movedThreshold = 5f, arrivedThreshold = 8f;

    public PanCutscene(Pos pos, float delay, float panDuration, float endDelay, Interp interp){
        this.pos = pos;
        this.interp = interp;
        this.delay = delay;
        this.panDuration = panDuration;
        this.endDelay = endDelay;
    }

    public PanCutscene(Pos pos){
        this(pos, 15f, 90f, 60f, Interp.smoother);
    }

    public PanCutscene(Position pos){
        this(() -> zzw.util.Float2.construct(pos.getX(), pos.getY()));
    }

    public PanCutscene(Pos pos, float panDuration){
        this(pos, 15f, panDuration, 60f, Interp.smooth2);
    }

    public PanCutscene(Position pos, float panDuration){
        this(() -> zzw.util.Float2.construct(pos.getX(), pos.getY()), 15f, panDuration, 60f, Interp.smooth2);
    }

    public PanCutscene(Pos pos, float panDuration, Interp interp){
        this(pos, 15f, panDuration, 60f, interp);
    }

    public PanCutscene(Position pos, float panDuration, Interp interp){
        this(() -> zzw.util.Float2.construct(pos.getX(), pos.getY()), 15f, panDuration, 60f, interp);
    }

    public PanCutscene(Position pos, float delay, float panDuration, float endDelay, Interp interp){
        this(() -> zzw.util.Float2.construct(pos.getX(), pos.getY()), delay, panDuration, endDelay, interp);
    }

    public <T extends PanCutscene> T moved(Cons<T> moved, float threshold){
        this.moved = (Cons<PanCutscene>)moved;
        movedThreshold = threshold;

        return (T)this;
    }

    public <T extends PanCutscene> T arrived(Cons<T> arrived, float threshold){
        this.arrived = (Cons<PanCutscene>)arrived;
        arrivedThreshold = threshold;

        return (T)this;
    }

    @Override
    public boolean update(){
        // 距演出开始经过的时间
        float elapsed = Time.time - startTime();

        // 即将开始移动 (留出阈值提前量), 触发 moved 回调
        if(moved != null && elapsed >= delay - movedThreshold){
            moved.get(this);
            moved = null;
        }

        // 延迟结束, 开始移动 (initPos == null 表示还没动过)
        if(elapsed >= delay && initPos == null){
            initPos = new Vec2(Core.camera.position);
            // elapsed 不保证从 0 开始, 用 initTime 防止插值时镜头抖动
            initTime = Time.time;
        }

        // 从开始移动起经过的时间
        float progress = Time.time - initTime;
        if(initPos != null){
            // 镜头位置从起点插值到目标点
            long pos = this.pos.get();
            Core.camera.position.set(initPos).lerp(
                zzw.util.Float2.x(pos), zzw.util.Float2.y(pos),
                interp.apply(Mathf.clamp(progress / panDuration))
            );

            // 即将到达, 触发 arrived 回调
            if(arrived != null && progress >= panDuration - arrivedThreshold){
                arrived.get(this);
                arrived = null;
            }
        }

        // 平移时长 + 结尾停留都过了才算演完
        return initTime > -1f && progress > panDuration + endDelay;
    }
}
