package zzw.map.cinematic;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.ui.layout.*;
import arc.util.*;

import static mindustry.Vars.*;

/**
 * 过场演出链 (PU132 unity.map.cinematic.Cutscene 移植)。
 *
 * <p>Cutscene 是一串依次执行、期间锁定玩家输入的演出动作。
 * 典型用法是 {@code Cutscene.root.then(new PanCutscene(...)).then(...)} 链式串联,
 * {@link #then(Cutscene)} 会返回传入的演出, 方便继续链式调用。</p>
 *
 * <p>与 PU132 保持一致的机制:
 * <ul>
 *   <li>root 演出在 {@link #init()} 时创建, 注册输入锁 ({@code control.input.addLock});</li>
 *   <li>演出期间 HUD 淡出、镜头缩放到 {@link #zoom} 指定的倍率、上下黑边收拢;</li>
 *   <li>每个子演出 {@link #update()} 返回 true 时结束, 由 root 擦除并推进到下一个;</li>
 *   <li>全部演完后再把镜头平滑拉回玩家位置、HUD 淡入。</li>
 * </ul>
 *
 * @author PU132 原作 (GlennFolker), 移植: zzw
 */
@SuppressWarnings("unchecked")
public class Cutscene{
    public static Cutscene root;
    public static float stripe = Scl.scl(56f);
    private float progress, zoomProgress;

    private final boolean isRoot;
    private boolean acting;

    protected Floatf<Cutscene> zoom = c -> Scl.scl(4f);
    protected Cutscene parent;
    protected Cutscene child;

    private Cons<Cutscene> start;
    private Cons<Cutscene> end;

    private Vec2 endPos;
    private float endTime = -1f;
    private float firstZoom = -1f, lastZoom = -1f;
    private float startTime = -1f;

    /** 客户端初始化: 创建 root 演出 + 上下黑边绘制元素 (每局游戏只调一次)。 */
    public static void init(){
        new Cutscene(true);
        Core.scene.add(new Element(){
            @Override
            public void act(float delta){
                setZIndex(ui.hudGroup.getZIndex() + 1);
                root.updateRoot();
            }

            @Override
            public void draw(){
                root.drawStripes();
            }
        });
    }

    private Cutscene(boolean isRoot){
        if(!isRoot){
            this.isRoot = false;
        }else if(root == null){
            root = this;
            this.isRoot = true;

            control.input.addLock(this::acting);
        }else{
            throw new IllegalArgumentException("Root cutscene already defined!");
        }
    }

    public Cutscene(){
        this(false);
    }

    /** 把 next 追加到演出链末尾 (沿 child 链走到尾), 返回 next 以便继续链式调用。 */
    public <T extends Cutscene> T then(T next){
        Cutscene target = this;
        while(target.child != null){
            target = target.child;
        }

        next.parent = target;
        Cutscene cur = target;
        while(cur != null && cur != root){
            if(cur == next) throw new IllegalArgumentException("Can't invoke then() to a parent cutscene!");
            cur = cur.parent;
        }

        target.child = next;
        return next;
    }

    public <T extends Cutscene> T start(Cons<T> start){
        this.start = (Cons<Cutscene>)start;
        return (T)this;
    }

    public <T extends Cutscene> T end(Cons<T> end){
        this.end = (Cons<Cutscene>)end;
        return (T)this;
    }

    public <T extends Cutscene> T zoom(Floatf<T> zoom){
        this.zoom = (Floatf<Cutscene>)zoom;
        return (T)this;
    }

    private void drawStripes(){
        Draw.color(Color.black);

        Vec2 pos = Core.scene.screenToStageCoordinates(Tmp.v1.set(0f, Core.graphics.getHeight()));
        float
            x = pos.x,
            y = pos.y,
            w = Core.graphics.getWidth(),
            h = Core.graphics.getHeight(),
            thick = stripe * progress;

        Fill.quad(
            x, y,
            x + w, y,
            x + w, y - thick,
            x, y - thick
        );
        Fill.quad(
            x, y - h,
            x + w, y - h,
            x + w, y - h + thick,
            x, y - h + thick
        );
    }

    private void updateRoot(){
        if(!isRoot) throw new IllegalStateException();

        if(child != null){
            if(control.input.locked() && !acting){
                // 其它模组也在做自己的演出 (输入被锁但本 root 未在演出中) → 让位
                return;
            }else{
                if(!acting) ui.hudGroup.actions(Actions.alpha(0f, 0.17f));
                if(firstZoom < 0f) firstZoom = renderer.getScale();
                if(lastZoom < 0f) lastZoom = renderer.getScale();
                acting = true;
            }

            endPos = null;
            endTime = -1f;

            progress = Mathf.approachDelta(progress, 1f, 1f / 60f);
            zoomProgress = Mathf.approachDelta(zoomProgress, 1f, 1f / 48f);

            float targetZoom = child.zoom();
            renderer.setScale(Mathf.lerp(lastZoom, targetZoom, zoomProgress));

            if(child.startTime < 0f) child.startTime = Time.time;

            if(child.start != null){
                child.start.get(child);
                child.start = null;
            }

            if(child.update()){
                if(child.end != null){
                    child.end.get(child);
                    child.end = null;
                }

                lastZoom = targetZoom;
                zoomProgress = 0f;
                child = child.child;
            }
        }else{
            // 全部演完: 黑边收起 + 镜头平滑拉回玩家
            progress = Mathf.approachDelta(progress, 0f, 1f / 60f);
            zoomProgress = Mathf.approachDelta(zoomProgress, 0f, 1f / 60f);

            if(firstZoom > 0f){
                renderer.setScale(Mathf.lerp(firstZoom, renderer.getScale(), zoomProgress));
                if(Mathf.equal(firstZoom, renderer.getScale())){
                    firstZoom = -1f;
                    lastZoom = -1f;
                    zoomProgress = 0f;
                }
            }

            if(acting){
                if(endPos == null){
                    endPos = new Vec2(Core.camera.position);
                    endTime = Time.time;
                }

                float endProgress = Time.time - endTime;

                Core.camera.position.set(endPos).lerp(player, Interp.smoother.apply(Mathf.clamp(endProgress / 52f)));
                if(endProgress >= 52f){
                    ui.hudGroup.actions(Actions.alpha(1f, 0.17f));

                    zoomProgress = 0f;
                    firstZoom = -1f;
                    lastZoom = -1f;
                    acting = false;
                }
            }
        }
    }

    /**
     * @return 本段演出是否演完。返回 true 后 root 会擦除它并推进到下一段。
     */
    public boolean update(){
        return true;
    }

    public float startTime(){
        return startTime;
    }

    public boolean acting(){
        return root.acting;
    }

    public float zoom(){
        return zoom.get(this);
    }

    /** 演出目标位置 (打包成 long 的两个 float, 见 arc zzw.util.Float2)。 */
    public interface Pos{
        long get();
    }
}
