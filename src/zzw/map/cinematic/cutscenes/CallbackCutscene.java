package zzw.map.cinematic.cutscenes;

import arc.util.Time;
import zzw.map.cinematic.Cutscene;

/**
 * 回调演出 (PU132 CallbackCutscene 移植): 在演出链里插入一段纯等待时间,
 * 期间在 startDelay 后执行一次 {@link #callback} (可用 callOnce 控制是否每帧重复执行)。
 *
 * <p>常用于演出链中"等特效播完再继续"或"在特定时刻 spawn 单位/触发事件"。</p>
 *
 * @author PU132 原作 (GlennFolker), 移植: zzw
 */
public class CallbackCutscene extends Cutscene{
    public float startDelay;
    public float endDelay;
    public float duration;

    public Runnable callback;
    private boolean called;
    private boolean callOnce;

    public CallbackCutscene(float startDelay, float endDelay, float duration, boolean callOnce, Runnable callback){
        this.startDelay = startDelay;
        this.endDelay = endDelay;
        this.duration = duration;
        this.callback = callback;
        this.callOnce = callOnce;
    }

    public CallbackCutscene(float duration, boolean callOnce, Runnable callback){
        this(0f, 0f, duration, callOnce, callback);
    }

    public CallbackCutscene(float duration, Runnable callback){
        this(0f, 0f, duration, true, callback);
    }

    public CallbackCutscene(Runnable callback){
        this(0f, 0f, 60f, true, callback);
    }

    @Override
    public boolean update(){
        float elapsed = Time.time - startTime();

        if(elapsed >= startDelay && elapsed <= startDelay + duration && !called){
            callback.run();
            called = callOnce;
        }

        return elapsed >= startDelay + duration + endDelay;
    }
}
