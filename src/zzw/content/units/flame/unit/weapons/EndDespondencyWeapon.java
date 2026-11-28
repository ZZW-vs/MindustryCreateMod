package zzw.content.units.flame.unit.weapons;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.InverseKinematics;
import mindustry.graphics.Layer;
import mindustry.type.Weapon;
import zzw.content.units.flame.FlameBatches;
import zzw.content.units.flame.FlameOutSFX;
import zzw.content.units.flame.FlamePal;
import zzw.content.units.flame.FlameUtils;
import zzw.content.units.flame.effects.Fragmentation.FragmentEntity;
import zzw.content.units.flame.effects.SpecialDeathEffects;
import zzw.content.units.flame.entities.DesShockWaveEntity;
import zzw.content.units.flame.entities.DesSpearEntity;
import zzw.content.units.flame.graphics.FragmentationBatch;
import zzw.content.units.flame.unit.DespondencyAI;
import zzw.content.units.flame.unit.empathy.EmpathyDamage;
import zzw.content.units.flame.unit.empathy.EmpathySpawner;

/**
 * 消沉主武器 (移植自 flame.unit.weapons.EndDespondencyWeapon)。
 *
 * <p>三阶段处决:</p>
 * <ol>
 *   <li>清杂: 释放 {@link DesShockWaveEntity} 冲击波，处决范围内脆弱目标;</li>
 *   <li>处决强敌: 对除主目标外的强力单位各投掷一支 {@link DesSpearEntity};</li>
 *   <li>最终处决: 连续投矛钉住主目标，6 秒后由 FragmentationBatch 拆成
 *       手控碎片 ({@link FragmentHands})，双手抓住碎片并将其掷向目标。</li>
 * </ol>
 */
public class EndDespondencyWeapon extends Weapon{
    Seq<DespondencyArm> arms = new Seq<>();
    TextureRegion[][] armRegions;

    public EndDespondencyWeapon(String name){
        super(name);
        mirror = false;
        x = y = 0f;
        mountType = DespondencyMount::new;
        noAttack = true;
        controllable = aiControllable = false;
        top = false;

        // ★ v160: UnitType.init() 会对 useAttackRange 的武器调用 weapon.range()，
        //   而 Weapon.range() 直接解引用 bullet。FO 原版依赖旧版空判，这里保留占位弹。
        bullet = new BulletType(0f, 0f);

        // ★ FO 用全局 Utils.rand，这里改为构造期局部随机源。
        Rand rand = new Rand(531125), rand2 = new Rand(53311);

        for(int i = 0; i < 5; i++){
            int fi = i;
            arms.add(new DespondencyArm("spear"){{
                x = 78f;
                y = -15f;
                rotation = -50f;

                tx = 20f - 5f * fi;
                ty = 12f - 12f * fi;
                trot = -125f + 21f * fi;
                armrot = 25f - 0.5f * fi;
                handrot = 15f - 0.2f * fi;
                weaponScl = 2f;
                weaponOffset = 8f;
                scl = 1.2f;
                tscl = fi * 0.1f;

                delay = 20f + 12f * fi;
                speed = 0.012f;

                length1 *= 1.7f;
                length2 *= 1.7f;
                insize *= 2.7f;
            }});
        }

        for(int i = 0; i < 15; i++){
            arms.add(new DespondencyArm("sword-" + rand2.random(4)){{
                x = 60f;
                y = -40f;
                rotation = -60f;

                tx = 38f + rand.range(10f);
                ty = -8f + rand.range(10f);

                float arange = rand.range(60f);
                trot = -45f + arange;
                armrot = rand.random(30f);
                handrot = rand.random(-12f, 8f);
                weaponScl = 1.9f;
                weaponOffset = 35f;
                scl = rand.random(0.8f, 1.2f);
                tscl = 0f;

                delay = 10f + (arange / 120f + 0.5f) * 140f + rand.random(15f);
                speed = 0.015f;

                length1 *= 1.7f;
                length2 *= 1.7f;
                insize *= 2.7f;
            }});
        }

        arms.addAll(
                new DespondencyArm("spear"){{
                    x = 60f;
                    y = -4f;

                    tx = 38f;
                    ty = 12f;
                    trot = -25f;
                    armrot = 25f;
                    handrot = 17f;
                    weaponScl = 2f;
                    weaponOffset = 8f;
                    scl = 1.2f;
                    tscl = 0f;

                    delay = 90f;
                }},

                new DespondencyArm("spear"){{
                    x = 60f;
                    y = -4f;

                    tx = 48f;
                    ty = -2f;
                    trot = -35f;
                    armrot = 12f;
                    handrot = 17f;
                    weaponScl = 2f;
                    weaponOffset = 8f;
                    scl = 1.2f;
                    tscl = 0.5f;

                    delay = 60f;
                }},

                new DespondencyArm("halberd"){{
                    x = 60f;
                    y = -4f;

                    tx = 35f;
                    ty = -30f;
                    trot = -65f;
                    armrot = 20f;
                    handrot = 15f;
                    weaponScl = 1.6f;
                    weaponOffset = 15f;
                    scl = 1.2f;
                    tscl = 0.6f;

                    delay = 30f;
                }},

                new DespondencyArm("claymore"){{
                    x = 60f;
                    y = -4f;

                    tx = 20f;
                    ty = -45f;
                    trot = -95f;
                    armrot = 12f;
                    handrot = 12f;
                    weaponScl = 1.75f;
                    weaponOffset = 42f;
                    scl = 1.2f;
                    tscl = 0.1f;
                }}
        );
    }

    @Override
    public void load(){
        super.load();
        armRegions = new TextureRegion[2][4];

        for(int i = 0; i < 2; i++){
            String outline = i == 0 ? "-outline" : "";
            armRegions[i][0] = findRegion("despondency-h-arm" + outline);
            armRegions[i][1] = findRegion("despondency-f-arm" + outline);
            armRegions[i][2] = findRegion("despondency-hand" + outline);
            armRegions[i][3] = findRegion("despondency-hand2" + outline);
        }

        for(DespondencyArm arm : arms){
            arm.weapon = findRegion("despondency-" + arm.name);
        }
    }

    /**
     * 查找贴图并在缺失时给出明确日志。
     *
     * <p>★ 修复"大招武器贴图错误": 本模组贴图在 atlas 中是
     * {@code create-despondency-xxx}, 之前用裸名查找会静默拿到 error 图。
     * 现统一走 {@link FlameUtils#atlasRegion(String)} 兼容 mod 前缀。</p>
     */
    private static TextureRegion findRegion(String name){
        TextureRegion region = FlameUtils.atlasRegion(name);
        if(region == null || !region.found()){
            arc.util.Log.warn("[despondency] 缺少贴图 @, 已回退到 despondency-halberd", name);
            return FlameUtils.atlasRegion("despondency-halberd");
        }
        return region;
    }

    @Override
    public void update(Unit unit, WeaponMount mount){
        DespondencyMount m = (DespondencyMount)mount;

        boolean shoot = (mount.shoot || m.active) && unit.controller() instanceof DespondencyAI && (mount.target instanceof Unit uu && uu.isValid() || m.targetDestroyed);
        if(shoot){
            m.time += Time.delta;
            for(DespondencyArm arm : arms){
                if(m.time >= arm.delay){
                    m.times[arm.id] = Mathf.lerpDelta(m.times[arm.id], 1f, arm.speed);
                }
            }
            m.magicIn = Mathf.clamp(m.magicIn + Time.delta / 180f);
            m.magicOut = Mathf.clamp(m.magicOut - Time.delta / 30f);

            if((mount.target != null && Angles.within(unit.rotation, unit.angleTo(mount.target), 15f) && m.activeTime > 120f) || m.stage > 0 || m.activeTime > 5f * 60f){
                m.stageTime -= Time.delta;
                if(!m.active) m.active = true;
                if(m.stageTime <= 0f){
                    switch(m.stage){
                        case 0 -> {
                            destroyStrays(unit, m);
                            m.stageTime = 1.5f * 60f;
                        }
                        case 1 -> {
                            destroyStrong(unit, m);
                            m.stageTime = 2f * 60f;
                        }
                        case 2 -> {
                            destroyMain(unit, m);
                            m.stageTime = 25f * 60f;
                            m.finalActive = true;
                        }
                        case 3 -> {
                            m.stage = 0;
                            m.stageTime = 0f;
                            m.active = false;

                            endFinal(m);

                            if(unit.controller() instanceof DespondencyAI ai) ai.endDeath();
                            mount.target = null;
                            return;
                        }
                    }
                    m.stage++;
                }
            }
            if(m.finalActive) updateFinal(unit, m);
        }else{
            if(m.stage > 2){
                endFinal(m);
                if(unit.controller() instanceof DespondencyAI ai) ai.endDeathFailed();
            }
            m.time = 0f;
            for(int i = 0; i < m.times.length; i++){
                m.times[i] = Mathf.lerpDelta(m.times[i], 0f, 0.07f);
            }
            m.activeTime = Math.max(Math.min(m.activeTime - (m.activeTime / 76f) * Time.delta, m.activeTime - Time.delta), 0f);
            m.stage = 0;
            m.stageTime = 0f;
            m.active = false;

            if(m.magicIn > 0){
                m.magicOut = Mathf.clamp(m.magicOut + Time.delta / 60f);
                if(m.magicOut >= 1f){
                    m.magicIn = 0f;
                    m.magicOut = 0f;
                }
            }
        }
        if(!m.spears.isEmpty()){
            m.spears.removeAll(s -> {
                if(s.spear != m.lastSpear && m.lastSpear != null && m.lastSpear.collided && m.lastSpear.isAdded()){
                    s.spear.cx = m.lastSpear.cx;
                    s.spear.cy = m.lastSpear.cy;
                    s.spear.cr = m.lastSpear.cr;
                }
                if(m.finalActive && s.spear.collided){
                    m.spearCollided = true;
                }
                s.fade = Mathf.approachDelta(s.fade, (s.spear.fading() || !s.spear.isAdded() || !m.finalActive) ? -0.001f : 1f, 1f / 12f);
                return s.fade < 0f;
            });
        }
        if(!m.fragHands.isEmpty()){
            for(FragmentHands h : m.fragHands){
                h.update(unit, m);
            }
            m.fragHands.remove(h -> h.fade >= 1f);
        }
    }

    void destroyStrays(Unit unit, DespondencyMount mount){
        DesShockWaveEntity.create(unit.team, mount.target, unit.x, unit.y);
    }

    void destroyStrong(Unit unit, DespondencyMount mount){
        for(Unit u : mindustry.gen.Groups.unit){
            if(u.team != unit.team && !isStray(u) && u != mount.target){
                float ang = unit.angleTo(u);
                Vec2 v = Tmp.v1.trns(ang, 240f).add(unit.x, unit.y);
                DesSpearEntity.create(u, v.x, v.y, ang + Mathf.range(45f), true);
            }
        }
    }

    void destroyMain(Unit unit, DespondencyMount mount){
    }

    void updateFinal(Unit unit, DespondencyMount m){
        if(!(m.target instanceof Unit u)) return;

        m.finalTime += Time.delta;
        m.spearTime -= Time.delta;
        if(m.spearTime <= 0f && !m.spearFinal){
            int sc = m.spears.size / 2;
            int sside = m.spears.size % 2 == 0 ? 1 : -1;

            float offset = ((unit.hitSize / 2f) / (1f + Mathf.pow(sc, 2f))) * sside;

            Vec2 v = Tmp.v1.set(0f, offset).rotate(unit.rotation), v2 = Tmp.v2.rnd(Mathf.random(u.hitSize / 2.1f)), v3 = Tmp.v3.trns(unit.rotation, (unit.hitSize / 2f) * 1.6f);
            float val = 1f / (1f + m.spears.size / 2f);

            DesSpearEntity spear = DesSpearEntity.create(u, v.x + unit.x + v3.x, v.y + unit.y + v3.y, Angles.angle(unit.x + v.x + v3.x, unit.y + v.y + v3.y, u.x + v2.x, u.y + v2.y), false);
            spear.forceScl = val;
            spear.damageScl = val / 4f;
            spear.tx = v2.x;
            spear.ty = v2.y;
            spear.size *= 1f + sc / 8f;
            spear.crySound = m.spears.isEmpty();

            if(m.lastSpear != null){
                spear.last = m.lastSpear;
            }

            v.rotate(-unit.rotation);

            SpearHolder sh = new SpearHolder(spear, v.x, v.y, (v.dst(u) - u.hitSize / 2f) * 0.6f);
            sh.flipped = v.y < 0;
            m.spears.add(sh);
            m.lastSpear = spear;

            m.spearTime = Mathf.random(7f, 15f);
        }

        if(m.spears.size >= 6 && !m.spearFinal){
            m.spearFinal = true;
        }
        if(m.spearFinal && m.spearCollided && m.lastSpear != null){
            m.spearCollidedTime = Mathf.clamp(m.spearCollidedTime + Time.delta / 120f);

            DesSpearEntity spr = m.lastSpear;
            Vec2 v = Tmp.v1.trns(unit.rotation, 480f).add(unit.x, unit.y);
            float dx = -(spr.cx - v.x) * 0.05f * m.spearCollidedTime, dy = -(spr.cy - v.y) * 0.05f * m.spearCollidedTime;
            spr.cx += dx;
            spr.cy += dy;
        }

        if(m.finalTime >= 6f * 60f && m.stageTime > 4f * 60f + 90f){
            SpecialDeathEffects eff = SpecialDeathEffects.get(u.type);
            Runnable run = eff.solid ? () -> {
                u.elevation = 0f;

                FragmentationBatch batch = FlameBatches.fragBatch;
                batch.baseElevation = 0f;
                batch.trailEffect = Fx.none;
                batch.explosionEffect = eff.explosionEffect != Fx.none ? eff.explosionEffect : null;
                batch.altFunc = (hx, hy, tex) -> {};
                batch.fragColor = Color.white;
                batch.useAlt = false;
                batch.resScale = 0.5f;
                batch.islandScl = 0.075f;
                batch.genGore = eff.hasInternal;
                batch.goreColor = eff.internalColor;
                batch.sound = eff.deathSound;
                batch.onDeathFunc = eff.fragDeath;
                batch.fragFunc = e -> {
                    e.z = 0f;
                    e.lifetime = 4f * 60f + Mathf.random(25f);
                    e.impact = true;
                    e.vx = 0f;
                    e.vy = 0f;
                    e.vz = 0f;
                    e.vr = 0f;

                    m.fragHands.add(new FragmentHands(unit, Mathf.range(4f), Mathf.range(24f), u, e));
                };

                batch.switchBatch(u::draw);
            } : () -> eff.cutAlt(u);
            EmpathyDamage.damageUnit(u, u.maxHealth + 1000f, true, run);
            m.stageTime = eff.solid ? 4f * 60f + 90f : 120f;
            m.targetDestroyed = true;
        }
    }

    void endFinal(DespondencyMount m){
        m.spears.clear();
        m.finalTime = 0f;
        m.spearTime = 0f;
        m.lastSpear = null;
        m.spearFinal = false;

        m.spearCollided = false;
        m.spearCollidedTime = 0f;

        m.finalActive = false;
        m.targetDestroyed = false;
        m.finalTime = 0f;
    }

    public static boolean isStray(Unit unit){
        float v = (FlameOutSFX.inst.getUnitDps(unit.type) + unit.maxHealth * unit.healthMultiplier);
        return v < 140000 && !EmpathyDamage.isNaNInfinite(v);
    }

    @Override
    public void draw(Unit unit, WeaponMount mount){
        DespondencyMount m = (DespondencyMount)mount;
        float z = Draw.z();

        Draw.z(Layer.bullet);

        if(m.magicIn > 0) drawMagic(unit, m);

        Draw.z(z);
    }

    @Override
    public void drawOutline(Unit unit, WeaponMount mount){
        drawMain(unit, mount);
    }

    void drawMagic(Unit unit, DespondencyMount m){
        Draw.color(FlamePal.redLight);
        float fout = 1f - m.magicOut;
        float fin = m.magicIn;

        float rad1 = 150f + fin * 150f;
        Lines.stroke(3f * fout);
        EmpathySpawner.progressiveCircle(unit.x, unit.y, rad1, 90f, fin);
        EmpathySpawner.progressiveCircle(unit.x, unit.y, rad1 / 3f, 180f, fin);
        EmpathySpawner.progressiveStar(unit.x, unit.y, rad1 / 3f, Time.time * 0.15f, 7, 2, fin);
        Lines.stroke(fout);
        EmpathySpawner.progressiveCircle(unit.x, unit.y, rad1 - 5f, -90f, fin);
        EmpathySpawner.progressiveCircle(unit.x, unit.y, rad1 / 2f, -90f, fin);
        EmpathySpawner.progressiveStar(unit.x, unit.y, rad1 / 2f, Time.time * 0.15f, 9, 2, fin);
        EmpathySpawner.progressiveStar(unit.x, unit.y, rad1 / 2f, Time.time * -0.15f, 4, 1, fin);
        Lines.stroke(3f * fout);
        EmpathySpawner.progressiveStar(unit.x, unit.y, rad1, Time.time * 0.1f, 4, 1, fin);
        EmpathySpawner.progressiveStar(unit.x, unit.y, rad1, -Time.time * 0.1f * 1.1f, 4, 1, fin);

        EmpathySpawner.progressiveStar(unit.x, unit.y, rad1, -90f, 3, 1, fin);
        for(int i = 0; i < 4; i++){
            float ang = i * 90f + Time.time * 0.2f;
            Vec2 v = Tmp.v1.trns(ang, rad1).add(unit.x, unit.y);
            Lines.stroke(3f * fout);
            EmpathySpawner.progressiveCircle(v.x, v.y, 90f, ang, fin);
            EmpathySpawner.progressiveStar(v.x, v.y, 90f, -90f, 5, 2, fin);
            Lines.stroke(fout);
            EmpathySpawner.progressiveCircle(v.x, v.y, 82f, ang, fin);
            EmpathySpawner.progressiveStar(v.x, v.y, 82f, 0f, 4, 1, fin);
            Lines.stroke(fout * 0.75f);
            EmpathySpawner.progressiveStar(v.x, v.y, 90f, 90f, 3, 1, fin);

            v.trns(ang, rad1 + 110f).add(unit.x, unit.y);
            Drawf.tri(v.x, v.y, 30f * fin * fout, 120f, ang);
            Drawf.tri(v.x, v.y, 30f * fin * fout, 20f, ang + 180f);
        }

        Draw.reset();
    }

    void drawMain(Unit unit, WeaponMount mount){
        DespondencyMount m = (DespondencyMount)mount;

        // ★ v160: drawOutline 阶段会先 applyOutlineColor, 部分情况下会把 Draw.color
        //   留成深色, 导致手臂/武器整片变黑 ("贴图错误" 观感)。这里强制恢复白色。
        Draw.color(Color.white);
        unit.type.applyColor(unit);
        Draw.color(Color.white);

        if(!m.spears.isEmpty()){
            float z = Draw.z();
            for(SpearHolder spear : m.spears){
                float fade = spear.fade * (1f - Mathf.clamp((m.finalTime - 4f * 60f) / 12f));
                float dz = Math.min(spear.spear.getZ(), z);

                Draw.z(dz);
                drawFinalSpears(unit, spear, fade, armRegions[0]);
                spear.spear.drawRaw();
                drawFinalSpears(unit, spear, fade, armRegions[1]);
            }
            Draw.z(z);
        }
        if(!m.fragHands.isEmpty()){
            for(FragmentHands h : m.fragHands){
                h.draw(unit);
            }
        }

        for(DespondencyArm arm : arms){
            if(m.times[arm.id] <= 0.001f) continue;
            for(int i = 0; i < 2; i++){
                arm.draw(unit, m.times[arm.id], i == 1);
            }
        }
        Draw.reset();
    }

    void drawFinalSpears(Unit unit, SpearHolder holder, float fade, TextureRegion[] set){
        Vec2 v = Tmp.v1.trns(unit.rotation, holder.x, holder.y).add(unit.x, unit.y);
        Vec2 end = Tmp.v2.set(holder.spear.x, holder.spear.y).add(Tmp.v4.trns(holder.spear.rotation, -50f * holder.spear.size));
        float ang = v.angleTo(end);
        float dst = v.dst(end);
        float dst2 = v.dst(holder.spear.x, holder.spear.y);
        int s = holder.flipped ? 1 : -1;

        Vec2 mid = Tmp.v3.set(v.x, v.y).lerp(end.x, end.y, 0.5f).add(Tmp.v4.trns(ang + 90f * -s, 25f * Mathf.clamp(1f - (dst2 / holder.dst))));

        if(dst < 8f) return;

        float scl = holder.spear.size * 0.5f;

        TextureRegion tr = Tmp.tr1, hand = set[2], fore = set[1], hind = set[0];
        tr.set(hand);
        float mu = Mathf.lerp(hand.v, hand.v2, 0.5f);
        tr.setV(Mathf.lerp(mu, hand.v, fade));
        tr.setV2(Mathf.lerp(mu, hand.v2, fade));

        Draw.rect(tr, end.x, end.y, tr.width * Draw.scl * scl, tr.height * Draw.scl * s * scl, holder.spear.rotation);

        tr.set(fore);
        mu = Mathf.lerp(fore.v, fore.v2, 0.5f);
        tr.setV(Mathf.lerp(mu, fore.v, fade));
        tr.setV2(Mathf.lerp(mu, fore.v2, fade));

        float dx = (end.x - mid.x) * 0.08f, dy = (end.y - mid.y) * 0.08f;
        Lines.stroke(tr.height * Draw.scl * scl * s);
        Lines.line(tr, mid.x - dx, mid.y - dy, end.x + dx * 0.2f, end.y + dy * 0.2f, false);

        tr.set(hind);
        mu = Mathf.lerp(hind.v, hind.v2, 0.5f);
        tr.setV(Mathf.lerp(mu, hind.v, fade));
        tr.setV2(Mathf.lerp(mu, hind.v2, fade));

        dx = (mid.x - v.x) * 0.15f;
        dy = (mid.y - v.y) * 0.15f;
        Lines.stroke(tr.height * Draw.scl * scl * s);
        Lines.line(tr, v.x - dx, v.y - dy, mid.x + dx, mid.y + dy, false);
    }

    @Override
    public void init(){
        super.init();

        for(int i = 0; i < arms.size; i++){
            DespondencyArm arm = arms.get(i);
            arm.id = i;
        }
    }

    public static class DespondencyMount extends WeaponMount{
        float time;
        float[] times;

        public float activeTime;
        public int stage;
        public float stageTime;
        boolean active;

        boolean finalActive;
        float finalTime;
        Seq<SpearHolder> spears = new Seq<>();
        DesSpearEntity lastSpear;
        float spearTime;
        boolean spearFinal = false;

        boolean spearCollided = false;
        float spearCollidedTime;

        Seq<FragmentHands> fragHands = new Seq<>();
        boolean targetDestroyed = false;

        float magicIn, magicOut;

        DespondencyMount(Weapon w){
            super(w);

            times = new float[((EndDespondencyWeapon)w).arms.size];
        }
    }

    class FragmentHands{
        /** ★ FO 原版是全局静态临时向量；这里改成每个手部实例自己的槽位，避免多台消沉互相覆盖。 */
        final Vec2 v1 = new Vec2(), v2 = new Vec2(), v3 = new Vec2(), v4 = new Vec2(), v5 = new Vec2(), v6 = new Vec2();

        float time, timeB;
        float fade;
        float length;
        boolean flipped;

        //source position
        float sx, sy;
        //position of targeted unit
        float tx, ty;
        //hand position, joint position
        float hx, hy, jx, jy;

        float cx, cy, cr;
        FragmentEntity frag;
        boolean holding, move2;

        float nx, ny;
        float delay;
        float scl;

        FragmentHands(Unit owner, float x, float y, Unit target, FragmentEntity f){
            frag = f;

            tx = target.x;
            ty = target.y;

            Vec2 v = v1.trns(owner.rotation, x, y).add(owner.x, owner.y);
            hx = jx = v.x;
            hy = jy = v.y;
            sx = x;
            sy = y;

            length = f.dst(v.x, v.y) * 1.1f;
            flipped = y < 0;
            delay = Mathf.random(60f);
            scl = Math.min(Mathf.pow((f.boundSize / 24f), 0.5f), 2f);
        }

        void update(Unit unit, DespondencyMount mount){
            if((delay -= Time.delta) > 0) return;
            time = Mathf.clamp(time + Time.delta / 25f);

            Vec2 src = v6.trns(unit.rotation, sx, sy).add(unit);
            if(!holding){
                Vec2 t = v1.trns(unit.rotation, sx, sy).add(unit).lerp(frag, time);
                hx = t.x;
                hy = t.y;
                float ang = Angles.angle(jx, jy, hx, hy);
                if(time >= 1f){
                    v5.set(frag.x - hx, frag.y - hy).rotate(-ang);
                    cr = frag.rotation - ang;
                    cx = v5.x;
                    cy = v5.y;
                    holding = true;

                    if(frag.main.explosionSound != Sounds.none) frag.main.explosionSound.at(frag.x, frag.y, Mathf.random(0.9f, 1.1f) * Math.max(1f / (1f + (frag.area - 8f) / 70f), 0.5f), Mathf.clamp(frag.area / 8.1f));
                    frag.main.explosionEffect.at(frag.x, frag.y, frag.area / 2.2f, frag.main.effectColor);
                    Effect.shake(frag.area / 3.2f, frag.area / 4.2f, frag.x, frag.y);
                    if(frag.main.onDeath != null) frag.main.onDeath.get(frag);

                    float dx = (frag.x - tx) * 2f;
                    float dy = (frag.y - ty) * 2f;
                    v4.set(dx, dy).limit(length * 0.6f).add(t).sub(unit).limit(length * 0.9f);

                    v4.rotate(-unit.rotation);
                    nx = v4.x;
                    ny = v4.y;

                    int side = (flipped ? -1 : 1) * ny > 0 ? 1 : -1;
                    ny *= side;
                }
            }else{
                timeB += Time.delta;
                if(timeB > 40f && !move2){
                    float angDst = Angles.angleDist(unit.rotation, Angles.angle(src.x, src.y, hx, hy)) * 1.25f;
                    v3.set(hx, hy).sub(src).rotate(-unit.rotation).setLength(length * 0.95f).rotate((flipped ? -1 : 1) * (7f + angDst));
                    nx = v3.x;
                    ny = v3.y;
                    move2 = true;
                }
                float speed = move2 ? Mathf.clamp((timeB - 40f) / 40f) * 0.02f : 0.05f;

                Vec2 v = v4.set(nx, ny).rotate(unit.rotation).add(unit);
                hx = Mathf.lerpDelta(hx, v.x, speed);
                hy = Mathf.lerpDelta(hy, v.y, speed);
            }
            float time2 = Mathf.clamp(time * 1.75f);
            InverseKinematics.solve((length / 2f) * time2, (length / 2f) * time2, v2.set(hx, hy).sub(src), !flipped, v3);
            Vec2 j = v3.add(src);
            jx = Mathf.lerpDelta(jx, j.x, 0.5f);
            jy = Mathf.lerpDelta(jy, j.y, 0.5f);

            if(holding){
                float ang = Angles.angle(jx, jy, hx, hy);
                Vec2 v = v5.set(cx, cy).rotate(ang).add(hx, hy);
                frag.x = v.x;
                frag.y = v.y;
                frag.rotation = cr + ang;
            }

            if((!frag.isAdded() || frag.time >= frag.lifetime) || !mount.finalActive){
                fade += Time.delta / 12f;
            }
        }

        void draw(Unit unit){
            if(delay > 0 || fade >= 1f) return;
            TextureRegion[][] regs = armRegions;
            Vec2 src = v1.trns(unit.rotation, sx, sy).add(unit);
            float ang = Mathf.slerp(Angles.angle(jx, jy, hx, hy), Angles.angle(src.x, src.y, hx, hy), 0.05f);
            float fin = Mathf.clamp(fade + Mathf.clamp(1f - time * 2f));
            int s = flipped ? 1 : -1;

            for(int i = 0; i < 2; i++){
                TextureRegion[] set = regs[i];

                TextureRegion tr = Tmp.tr1, hand = set[3], fore = set[1], hind = set[0];
                tr.set(hand);
                float mu = Mathf.lerp(hand.v, hand.v2, 0.5f);
                tr.setV(Mathf.lerp(hand.v, mu, fin));
                tr.setV2(Mathf.lerp(hand.v2, mu, fin));

                Draw.rect(tr, hx, hy, tr.width * Draw.scl * scl, tr.height * s * Draw.scl * scl, ang);

                tr.set(fore);
                mu = Mathf.lerp(fore.v, fore.v2, 0.5f);
                tr.setV(Mathf.lerp(fore.v, mu, fin));
                tr.setV2(Mathf.lerp(fore.v2, mu, fin));

                float dx = (hx - jx) * 0.08f, dy = (hy - jy) * 0.08f;
                Lines.stroke(tr.height * Draw.scl * s * scl);
                Lines.line(tr, jx - dx, jy - dy, hx + dx * 0.2f, hy + dy * 0.2f, false);

                tr.set(hind);
                mu = Mathf.lerp(hind.v, hind.v2, 0.5f);
                tr.setV(Mathf.lerp(hind.v, mu, fin));
                tr.setV2(Mathf.lerp(hind.v2, mu, fin));

                dx = (jx - src.x) * 0.1f;
                dy = (jy - src.y) * 0.1f;
                Lines.stroke(tr.height * Draw.scl * s * scl);
                Lines.line(tr, src.x - dx, src.y - dy, jx + dx, jy + dy, false);
            }
        }
    }

    static class SpearHolder{
        DesSpearEntity spear;
        float x, y;
        float fade;
        float dst;
        boolean flipped;

        SpearHolder(DesSpearEntity spear, float x, float y, float dst){
            this.spear = spear;
            this.x = x;
            this.y = y;
            this.dst = dst;
        }
    }

    class DespondencyArm{
        float x, y, rotation = -30f;
        float tx, ty, trot;
        float length1 = 64f - 5f, length2 = 68f - 5f;
        float insize = 5f;
        float scl = 1f;
        float tscl = 0f;

        float armrot, handrot = 4f;
        float delay;
        float speed = 0.02f;

        String name;
        TextureRegion weapon;
        float weaponOffset = 10f;
        float weaponScl = 1f;

        int id;

        DespondencyArm(String name){
            this.name = name;
        }

        void draw(Unit unit, float progress, boolean flip){
            int s = flip ? -1 : 1;
            Vec2 v = Tmp.v1.trns(unit.rotation - 90f, (x + tx * progress) * s, y + ty * progress).add(unit.x, unit.y);
            float rx = v.x, ry = v.y;
            float rprog = progress * 0.75f + 0.25f;
            float fscl = scl + tscl * progress;
            float len1 = length1 * fscl * rprog, len2 = length2 * fscl * rprog;

            float rot = unit.rotation + (rotation + trot * progress) * s;
            float rot2 = rot + (180f - (180f - armrot) * progress) * s;
            float rot3 = rot2 + handrot * progress * s;

            Tmp.v1.trns(rot, len1).add(rx, ry);
            float j1x = Tmp.v1.x, j1y = Tmp.v1.y;
            Tmp.v1.trns(rot2, len2).add(j1x, j1y);
            float j2x = Tmp.v1.x, j2y = Tmp.v1.y;
            Tmp.v1.trns(rot3, 12f * fscl).add(j2x, j2y);
            float hx = Tmp.v1.x, hy = Tmp.v1.y;

            Tmp.v1.set(j1x, j1y).sub(rx, ry).nor();
            float n1x = Tmp.v1.x, n1y = Tmp.v1.y;
            Tmp.v1.set(j2x, j2y).sub(j1x, j1y).nor();
            float n2x = Tmp.v1.x, n2y = Tmp.v1.y;

            for(int i = 0; i < 2; i++){
                TextureRegion root = armRegions[i][0], end = armRegions[i][1], hand = armRegions[i][2];

                Lines.stroke(root.height * Draw.scl * s * fscl);
                Draw.rect(hand, hx, hy, hand.width * Draw.scl * fscl, hand.height * Draw.scl * fscl * s, rot3);
                Lines.line(end, j1x - n2x * fscl * 5f, j1y - n2y * fscl * 5f, j2x + n2x * fscl * 5f, j2y + n2y * fscl * 5f, false);
                Lines.line(root, rx - n1x * fscl * 5f, ry - n1y * fscl * 5f, j1x + n1x * fscl * insize, j1y + n1y * fscl * insize, false);

                if(i == 0){
                    float cprog = Mathf.curve(progress, 0f, 0.85f);

                    TextureRegion reg = Tmp.tr1;
                    reg.set(weapon);

                    float mid = Mathf.lerp(reg.u, reg.u2, Mathf.clamp(0.5f - (weaponOffset / (weapon.width * Draw.scl))));
                    reg.setU(Mathf.lerp(mid, weapon.u, cprog));
                    reg.setU2(Mathf.lerp(mid, weapon.u2, cprog));

                    float width = reg.width * fscl * Draw.scl * weaponScl;
                    float height = reg.height * fscl * Draw.scl * weaponScl * s;

                    Vec2 v2 = Tmp.v1.trns(rot3, weaponOffset * (width / (weapon.width * Draw.scl)));

                    Draw.rect(reg, hx + v2.x, hy + v2.y, width, height, rot3);
                }
            }
        }
    }
}
