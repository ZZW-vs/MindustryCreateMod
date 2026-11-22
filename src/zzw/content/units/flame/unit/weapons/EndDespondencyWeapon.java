package zzw.content.units.flame.unit.weapons;

import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;
import mindustry.type.Weapon;

/**
 * 消沉主武器占位 (P0 阶段)。
 *
 * <p>三阶段处决逻辑 (长矛/冲击波/碎片手) 将在 P2 阶段完整实现。
 * 当前只提供 AI 需要的 {@link DespondencyMount#stage} / {@code activeTime}
 * 字段与空更新，保证 P0 可以编译运行。</p>
 */
public class EndDespondencyWeapon extends Weapon{

    public EndDespondencyWeapon(String name){
        super(name);

        // 主武器不参与普通自动瞄准, 由 DespondencyAI 直接接管
        controllable = false;
        autoTarget = false;

        // 占位弹体: UnitType.init() 会调用 weapon.range() 计算 maxRange,
        // bullet 为 null 会直接空指针; 真正的处决逻辑在 P2 用自定义绘制实现。
        bullet = new mindustry.entities.bullet.BulletType(0f, 0f);

        mountType = DespondencyMount::new;
    }

    @Override
    public void update(Unit unit, WeaponMount mount){
        // P2 填充
    }

    @Override
    public void draw(Unit unit, WeaponMount mount){
        // P2 填充
    }

    public static class DespondencyMount extends WeaponMount{
        /** 处决演出已进行的时间 (AI 在启动 30 帧后累加)。 */
        public float activeTime;
        /** 当前处决阶段。 */
        public int stage;
        /** 处决是否完成 (P2 使用)。 */
        public boolean finished;

        DespondencyMount(Weapon w){
            super(w);
        }
    }
}
