package zzw.content.optics;

import arc.scene.ui.layout.Table;
import arc.util.Tmp;
import zzw.content.util.SVec2;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Icon;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.ui.Styles;

/**
 * 光源方块 (PU132 unity.world.blocks.light.LightSource 移植)
 *
 * <p>灯: 产生一束可旋转 (22.5° 步进) 的光, 光强 = 效率 * lightProduction。
 * created() 创建 Light 实体并 queueAdd; onRemoved 时 queueRemove;
 * updateTile 持续同步位置/朝向/强度。</p>
 */
public class LightSource extends LightHoldBlock {
    /** 产生的基础光强 */
    public float lightProduction = 1f;

    public LightSource(String name) {
        super(name);
        solid = true;
        configurable = true;
        outlineIcon = true;

        config(Boolean.class, (LightSourceBuild tile, Boolean value) ->
            tile.lightRot = Light.fixRot(tile.lightRot + (value ? Light.rotationInc : -Light.rotationInc)));
    }

    @Override
    public void init() {
        // ★ 原版光照兼容: emitLight=true 让 BlockRenderer 把本建筑加入 lightview,
        //   每帧调用 LightSourceBuild.drawLight() (原版 LightBlock 同款做法)
        emitLight = true;
        // 光照裁剪范围: 强度上限 2.0 × 传播距离系数, 保证大范围灯光不被视口裁掉
        lightClipSize = Math.max(lightClipSize, 2.5f * Light.yield);
        super.init();
    }

    @Override
    public float getRotation(Building build) {
        return build instanceof LightSourceBuild b ? b.lightRot : 0f;
    }

    public class LightSourceBuild extends LightHoldBuild {
        public Light light;
        public float lightRot = 90f;

        @Override
        public Object config() {
            return lightRot;
        }

        /**
         * ★ 原版光照兼容: 灯除自绘光束外, 还向原版 LightRenderer 发动态光。
         * <p>黑暗地图 (state.rules.lighting 开启) 下能像原版照明方块一样照亮周围;
         * 半径 = 当前光强 × 光传播距离系数 (与光束实际长度一致), 上限 2.0 防无限灯
         * (lightProduction=600000) 生成超大光圈; 关闭光照规则时原版内部自动忽略。</p>
         */
        @Override
        public void drawLight() {
            if (light == null || !light.valid) return;
            float str = light.strength;
            if (str <= 0.01f) return;
            Drawf.light(x, y, Math.min(str, 2f) * Light.yield,
                Tmp.c1.set(light.color), 0.75f);
        }

        @Override
        public void created() {
            super.created();

            light = Light.create();
            light.queuePosition = SVec2.construct(x, y);
            light.queueRotation = lightRot;
            light.queueSource = this;

            light.queueAdd();
        }

        @Override
        public void onRemoved() {
            light.queueRemove();
            super.onRemoved();
        }

        @Override
        public void updateTile() {
            super.updateTile();

            light.queuePosition = SVec2.construct(x, y);
            light.queueRotation = lightRot;
            light.queueSource = this;
            light.queueStrength = efficiency * lightProduction;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.button(Icon.left, Styles.cleari, () -> configure(true)).size(40f);
            table.button(Icon.right, Styles.cleari, () -> configure(false)).size(40f);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(lightRot);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            lightRot = read.f();
        }

        @Override
        public void afterRead() {
            super.afterRead();
            // 读档后光源实体未创建 (created 不触发), 补建
            if (light == null) {
                light = Light.create();
                light.queuePosition = SVec2.construct(x, y);
                light.queueRotation = lightRot;
                light.queueSource = this;
                light.queueAdd();
            }
        }
    }
}