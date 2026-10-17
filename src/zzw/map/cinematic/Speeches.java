package zzw.map.cinematic;

import arc.audio.*;
import arc.flabel.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.actions.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.Label.LabelStyle;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

import static mindustry.Vars.*;

/**
 * 剧情对话 (PU132 Speeches 移植): 打字机式对话条, 显示在 HUD 左下角,
 * 支持标题/头像/逐字音效/变量替换/暂停跟随。
 *
 * <p>用法: {@code Speeches.root.show(s -> s.setTitle("...").setContent("...")).show(...)}
 * 链式追加多段对话, 依次播放。</p>
 *
 * <p>★ 与 PU132 的差异: 样式改用原版 Styles (PU 用自研 UnityStyles 打字机样式);
 * HUD 挂载点查找改为防御式 (v160 HUD 结构与 v132 不同, 找不到 statustable 时
 * 直接挂在 hudGroup 左下角)。</p>
 *
 * @author PU132 原作 (GlennFolker), 移植: zzw
 */
public class Speeches{
    public static Speeches root;
    private static boolean shown;
    private static boolean initialized;
    private static FLabel label;

    public Prov<CharSequence> title = () -> "";
    public CharSequence content = "";
    public Sound sound = Sounds.click;
    public FListener listener;
    public Prov<Drawable> image = () -> Tex.clear;
    public Prov<Color> color = () -> Pal.accent;
    public float endDelay = 2f;

    protected boolean ended = false, finished = false;
    protected Speeches child;

    /** 客户端初始化 (每局游戏一次): 在 HUD 的状态栏里塞入对话条。 */
    public static void init(){
        if(initialized) return;
        initialized = true;

        // 防御式找挂载点: v160 的 HUD 层级名可能与 v132 不同, 任一找不到就退回 hudGroup 直挂
        Table host = null;
        Table marker = ui.hudGroup.<Table>find("overlaymarker");
        if(marker != null){
            Stack wavesEditor = marker.<Stack>find("waves/editor");
            Table waves = wavesEditor == null ? null : wavesEditor.<Table>find("waves");
            Table status = waves == null ? null : waves.<Table>find("statustable");
            if(status != null) host = status.row().table(Tex.buttonRight, t -> buildSpeechTable(t)).width(320f).minHeight(200f).fillY().align(Align.topLeft).get();
        }
        if(host == null){
            // WidgetGroup 没有 table(), 用 fill 包一层
            ui.hudGroup.fill(wrap -> wrap.table(Tex.buttonRight, t -> buildSpeechTable(t))
                .width(320f).minHeight(200f).bottom().left().fillY());
        }
    }

    private static void buildSpeechTable(Table t){
        root = new Speeches();
        label = new FLabel(""){
            boolean gamePaused, lastPaused;

            {
                setWrap(true);
                setAlignment(Align.topLeft);
                setStyle(new LabelStyle(Styles.defaultLabel));
                setTypingListener(new FListener(){
                    @Override
                    public void event(String event){
                        Speeches current = root.child;
                        if(current != null && current.listener != null) current.listener.event(event);
                    }

                    @Override
                    public String replaceVariable(String variable){
                        Speeches current = root.child;
                        if(current != null && current.listener != null){
                            return current.listener.replaceVariable(variable);
                        }else{
                            return FListener.super.replaceVariable(variable);
                        }
                    }

                    @Override
                    public void onChar(char ch){
                        Speeches current = root.child;
                        if(current != null){
                            if(!Character.isWhitespace(ch)) current.sound.play();
                            if(current.listener != null) current.listener.onChar(ch);
                        }
                    }

                    @Override
                    public void end(){
                        Speeches current = root.child;
                        if(current != null){
                            if(!current.ended){
                                current.ended = true;
                                actions(
                                    Actions.delay(current.endDelay),
                                    Actions.run(() -> current.finished = true)
                                );
                            }

                            if(current.listener != null) current.listener.end();
                        }
                    }
                });
            }

            @Override
            public void act(float delta){
                // 游戏暂停时打字机也暂停, 恢复时按之前的状态还原
                if(state.isPaused()){
                    if(!gamePaused){
                        gamePaused = true;
                        lastPaused = isPaused();
                        pause();
                    }
                }else if(gamePaused){
                    gamePaused = false;
                    if(!lastPaused) resume();
                }

                super.act(delta);
            }

            @Override
            public void resume(){
                if(gamePaused){
                    lastPaused = false;
                }else{
                    super.resume();
                }
            }
        };

        t.margin(5f);
        t.setClip(true);
        t.table(Styles.black3, header -> {
            header.image().update(i -> {
                if(root.child != null){
                    Drawable icon = root.child.image.get();
                    i.setDrawable(icon == Tex.clear ? Styles.black5 : icon);
                }else{
                    i.setDrawable(Styles.black5);
                }
            }).pad(5f).size(iconXLarge);

            header.table(title -> {
                title.table(up -> {
                    up.label(() -> root.child != null ? root.child.title.get() : "")
                        .style(new LabelStyle(Styles.defaultLabel)).grow().padRight(5f)
                        .get().setAlignment(Align.left);

                    up.button(Icon.play, Styles.emptyi, label::skipToTheEnd)
                        .fill().align(Align.topRight)
                        .get().setDisabled(label::hasEnded);
                }).pad(5f).grow();

                title.row().image(Tex.whiteui).update(i -> {
                    if(root.child != null){
                        i.setColor(root.child.color.get());
                    }
                }).growX().height(3f).pad(5f);
            }).pad(5f).grow();
        }).pad(5f).growX().fillY();

        t.row().table(Styles.black3, cont -> cont.add(label).pad(5f).grow()).pad(5f).grow();

        // 对话条的出现/收起/换段逻辑 (PU132 原样)
        t.update(() -> {
            t.setOrigin(Align.topLeft);
            if(!(state.isPlaying() || state.isPaused())){
                root.child = null;

                label.restart("");
                label.pause();

                shown = false;
            }else{
                Speeches current = root.child;
                if(current != null && !current.finished && !shown){
                    shown = true;
                    t.actions(
                        Actions.run(() -> {
                            label.restart(current.content);
                            label.pause();
                        }),
                        Actions.scaleTo(1f, 1f, 0.2f, Interp.pow3Out),
                        Actions.run(label::resume)
                    );
                }

                if(current != null && current.finished){
                    if(current.child != null){
                        Speeches child = current.child;
                        current.child = null;

                        root.child = child;

                        label.restart(child.content);
                        label.resume();
                    }else if(shown){
                        shown = false;
                        t.actions(Actions.scaleTo(1f, 0f, 0.2f, Interp.pow3In));
                    }
                }

                if(root.child == null){
                    t.actions(Actions.scaleTo(1f, 0f));
                }
            }
        });
    }

    /** 追加一段对话 (cons 里配置标题/内容等), 返回新段以便继续链式追加。 */
    public Speeches show(Cons<Speeches> cons){
        Speeches child = new Speeches();
        cons.get(child);

        return show(child);
    }

    public Speeches show(Speeches child){
        return last().child = child;
    }

    public Speeches last(){
        Speeches current = this;
        while(current.child != null){
            current = current.child;
        }

        return current;
    }

    public Speeches setTitle(CharSequence title){
        return setTitle(() -> title);
    }

    public Speeches setTitle(Prov<CharSequence> title){
        this.title = title;
        return this;
    }

    public Speeches setContent(CharSequence content){
        this.content = content;
        return this;
    }

    public Speeches setSound(Sound sound){
        this.sound = sound;
        return this;
    }

    public Speeches setListener(FListener listener){
        this.listener = listener;
        return this;
    }

    public Speeches setImage(TextureRegion region){
        var image = new TextureRegionDrawable(region);
        return setImage(() -> image);
    }

    public Speeches setImage(Drawable image){
        return setImage(() -> image);
    }

    public Speeches setImage(Prov<Drawable> image){
        this.image = image;
        return this;
    }

    public Speeches setColor(Color color){
        return setColor(() -> color);
    }

    public Speeches setColor(Prov<Color> color){
        this.color = color;
        return this;
    }

    public Speeches setDelay(float endDelay){
        this.endDelay = endDelay;
        return this;
    }
}
