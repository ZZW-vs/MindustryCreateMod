package zzw.content.units.flame;

import arc.audio.Sound;
import mindustry.Vars;

/**
 * 消沉专用音效 (移植自 flame.FlameSounds 的 des-* / exp* 部分)。
 *
 * <p>音效文件已经位于 {@code assets/sounds/} 根目录，直接通过
 * {@link Vars#tree} 加载，不依赖 FlameOut 的 FlameSounds 类。</p>
 */
public class FlameSounds{
    public static Sound
            desSpearHit, desSpearCry, desLaser, desLaserShoot, desRailgun, desRailHit,
            desNukeShoot, desNukeHit, desNukeHitFar,
            expOrganic, expExotic, expElectric;

    public static void load(){
        desSpearHit = Vars.tree.loadSound("des-spear-hit");
        desSpearCry = Vars.tree.loadSound("des-spear-cry");

        desLaser = Vars.tree.loadSound("des-laser");
        desLaserShoot = Vars.tree.loadSound("des-laser-shoot");
        desRailgun = Vars.tree.loadSound("des-railgun");
        desRailHit = Vars.tree.loadSound("des-railgun-hit");
        desNukeShoot = Vars.tree.loadSound("des-nuke-shoot");
        desNukeHit = Vars.tree.loadSound("des-nuke-hit");
        desNukeHitFar = Vars.tree.loadSound("des-nuke-hit-far");

        expOrganic = Vars.tree.loadSound("explosion-organic");
        expExotic = Vars.tree.loadSound("explosion-exotic");
        expElectric = Vars.tree.loadSound("explosion-electric");
    }
}
