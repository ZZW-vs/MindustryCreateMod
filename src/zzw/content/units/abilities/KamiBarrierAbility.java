package zzw.content.units.abilities;

import mindustry.entities.abilities.Ability;
import mindustry.gen.Unit;
import zzw.content.units.KamiAI;

/**
 * kami 屏障与弹幕绘制能力
 * 把 {@link KamiAI} 的屏障圆环与弹幕模式特效接入单位渲染流程。
 * 独立成具名类, 以便面板正确读取 ability.kamibarrier 的本地化文案。
 */
public class KamiBarrierAbility extends Ability {
    @Override
    public void draw(Unit unit) {
        if (unit.controller() instanceof KamiAI ai) {
            ai.draw();
        }
    }
}
