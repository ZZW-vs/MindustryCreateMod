# Create Mod

**Create Mod** 是 **ProjectUnity** 模组的高版本移植修改和一些其他小东西的整合模组

**交流QQ群：276860651**

## 功能特性

### 新增物品
- **铁锭** - 基础金属原料，用于制作铁板
- **金锭** - 珍贵的金属原料，用于制作金板
- **铁板** - 通过铁锭压制而成的金属板材，用于建造各种设备
- **金板** - 通过金锭压制而成的珍贵金属板材，用于制造高级设备
- **铜板** - 通过原生铜压制而成的金属板材，用于制造各种设备
- **经验球** - 用于升级单位和建造高级设备
- **经验储罐** - 存储经验球的设备，可与其他经验设备连接

### 新增方块

#### 生产设备
- **铁板制造机** - 将铁锭加工成铁板的基础工业设备
  - 每次消耗2个铁锭，生产2个铁板
- **金板制造机** - 将金锭加工成金板的精密工业设备
  - 每次消耗2个金锭，生产2个金板
- **铜板制造机** - 将原生铜加工成铜板的基础工业设备
  - 每次消耗2个原生铜，生产2个铜板
- **经验输出器** - 从链接的经验储罐中提取经验球并发射
  - 主动从链接的经验储罐抽取经验，凑够一个经验球后发射
- **经验焚化炉** - 焚化物品与液体转化为经验球弹出的设备
  - 通电后焚化任意物品（可为不同种类）与所有液体，每焚化 5 个物品或 10 单位液体弹出一个经验球（最高 1.2 个/秒），经验球朝方块朝向 2 格外弹出（放置时 R 键调整方向），附近经验持有者（炮台/储罐/墙）飞过拾取；耗电比原版焚化炉多 0.2/tick，液体容量 20

#### 坩埚热力系统
- **坩埚容器** - 大型熔融物容器，扩展坩埚网络的容量
  - 点击可打开信息面板：坩埚温度条（含各物品熔点标记）、内容物堆叠图、所有可熔物品的熔点参考列表
- **铸模** - 将熔融物浇注并冷却铸回物品
  - 温度过高时面板显示"温度过高，无法铸造！"提示（已汉化）

#### 高级生产设备
- **大型铁板制造机** - 高效率的铁板生产设备，需要电力支持
  - 每次消耗3个铁锭，生产4个铁板，需要电力
- **大型金板制造机** - 高效率的金板生产设备，需要电力支持
  - 每次消耗3个金锭，生产4个金板，需要电力
- **大型铜板制造机** - 高效率的铜板生产设备，需要电力支持
  - 每次消耗3个原生铜，生产4个铜板，需要电力

#### 防御方块
- **铜块** - 由铜板和原生铜制作的防御块
  - 基础防御设施，可以合成大型铜块
- **铁块** - 由铁板和原生铜制作的防御块
  - 基础防御设施，可以合成大型铁块
- **大型铜块** - 由大量铜板和原生铜制作的大型防御块
  - 高耐久防御设施，由四个小铜块自动合成
- **大型铁块** - 由大量铁板和铁锭制作的大型防御块
  - 高耐久防御设施，由四个小铁块自动合成
- **PU 墙体系统**（对照 PU132 原版补齐功能）：
  - **玻璃墙（metaglass-wall / -large）** - `LightWall`：光可穿过墙体并按 `suppression=0.8` 衰减，接入光学系统
  - **单极子墙（electrophobic-wall / -large）** - `PowerWall`：受能量弹（激光/闪电等）攻击时按弹种倍率转化为电力输出，效率超 1 过载扣血，效率超阈值显示热图；友方治疗弹命中清零
  - **铜镍合金墙（cupronickel-wall / -large）** - `HeatWall`：接入坩埚热量网络，墙体温度越高越持续灼烧并伤害周围单位
  - **限伤墙（ustone/dense/steel/dirium-wall）** - `LimitWall` / `LevelLimitWall`：补回限伤命中（`withstandFx`）与闪烁免伤（`blinkFx`）特效；迪里姆墙恢复原版 `sparkle` 待机特效
  - **经验限伤墙（ExpLimitWall）** - 对齐原版 `hubValid`/`canHub` 逻辑，血条旁显示减伤百分比图标
  - **石头开采（PU 原版机制）** - 原版石头地板 / 岩坑地板（`Blocks.stone` / `Blocks.craters`）设置 `itemDrop = stone`，用钻头钻取可获得「石头」物品；与原版一致设为 `playerUnmineable`，禁止手动挖掘
  - **经验墙信息面板（PU 原版）** - 还原 `exp.tooltip` 详细经验说明：主动等级方块与 Exp. 球、古代塔/卸载器交互且经验可自由传输；被动等级方块不与 Exp. 球/塔交互，缓慢获得经验但无法自由利用

#### 炮台系统 (PU132 还原)
本模组的高级炮台全部对照 PU132 原版逐项还原，主要修正包括：
- **双炮管交替发射**：apparition / higgs-boson 的两根炮管改为原版交替开火（`ShootAlternate`）
- **激光粗细还原**：photon 激光变细、graviton 改为原版较细的半透明激光
- **液体强化数值**：ephemeron / muon / singularity / z-boson / higgs-boson / electron / electrobomb / plasma / current / shockwire / orb 统一为水 135%、冷冻液 185%；ghost / banshee 为水 120%、冷冻液 145%
- **子弹形态**：z-boson 改为原版长条型（取原版 2/3 长度）；banshee 子弹整体尺寸微增；celsius / kelvin 射速与拖尾还原；ephemeron 阴阳粒子碰撞特效还原
- **特殊机制**：gluon 结尾改为原版小漩涡；proton 转速微增；shockwire 激光加粗；plasma 射程 65 格；arc-caster / arc-storm 蓄力与六边形效果还原
- **supernova**：三项还原修正——① 蓄力光球按原版前移到炮口（`shootLength = size × tilesize / 2 − 8 = 20`，此前误设为 8，光球贴在炮台中心）；② 补回"开火结束后炮管泛蓝"的热感发光（原版 `cooldown = 0.006f` 约 2.8 秒才褪完，v160 该字段拆为 `cooldownTime`，默认 20f 只需 0.33 秒就褪光，肉眼看不到 → 换算为 `cooldownTime = 166.7f`）；③ 转向改为读取 block 上的 `firingMoveFract`（原先写死 0.2f，导致该配置项失效），光束存在期间按该系数减速，其余时间全速跟随目标（玩家操控时即始终跟随鼠标）
- **经验激光炮台（charge / frost / fractal / swarm）**：四项按 PU132 原版还原攻击方式与特效——
  - `charge-laser-turret`：改为**蓄力单发**（`shoot.firstShotDelay = 50`，蓄力播放 `laserCharge` + `laserChargeBegin`、开火播放 `laserChargeShoot`），弹药换成原版 `shardLaser`（150 长度 / 30 伤害 + 电击状态 + 激光碎片）
  - `frost-laser-turret`：弹药换成原版 `frostLaser`（170 / 130 + 冻结状态 + 冰片射击特效），命中生成**冻结圈**（`freezeEffect` + 冻结音效 + 范围冻结/瘫痪）；命中按原版 `expGain = 2` 给炮台加经验（该炮台无碎片弹，这是它唯一的升级途径）；炮台贴图沿用原版（此前误判为无贴图，已恢复）
  - `fractal-laser-turret`：改为**蓄力裂缝激光**（蓄力 `laserFractalCharge` / `laserFractalChargeBegin`，开火 `laserFractalShoot`），命中生成 `DistFieldBulletType` **扭曲力场**（减速场内敌方单位及其子弹）并播放闪电链 + 多个随机小力场；炮台贴图沿用原版（经核对 PU_V8 参考，`fractal-laser-turret.png` 存在且为完整炮台贴图，此前误判为无贴图，已撤销透明化处理并恢复）
  - `swarm-laser-turret`：由"简化版 ExpPowerTurret"改为新建的 `BurstChargePowerTurret`——**一次装填按 20 tick 间隔依次蓄力发射 4 发**，每发独立播放蓄力特效（忠实还原原版连发蓄力攻击方式），弹药为 `branchLaser`（140 / 20 + 3 发分裂碎片）
- **endgame**：按 PU132 原版**逐行忠实移植**（draw / updateTile / updateEyes / eyeShoot / killUnits / killTiles / shoot / collision / damage 的数值、判定顺序、特效参数与图层全部照抄）——三层旋转环 + 16 眼追踪光束（光束攻击半径 = 炮台射程 820）、攻击时轮盘旋转与底座纹理亮起、眼睛朝目标偏移、内外圈逆时针依次发射秒杀光束、齐射时以自身为中心湮灭爆炸
  - **慢闪电**：改为 PU132 原版的慢闪电形态——单段长度回到原版 50（远小于延伸距离，因此会自然**弯折 + 分叉**，不再是笔直光柱），延伸距离按需求取**炮台射程的一半**（820 / 2 = 410，原版为 810），并限制同时存在数量不超过 6 条；沿线段连续施加 520 × 效率 的伤害
  - **通电就亮修复**：v158 中 `efficiency` 会被必选物品消耗（terminum）拖成 0，导致通电也不亮；改用 `power.status`（纯电力满足度）作为"通电"信号，与物品解耦，通电即亮眼睛与底部线路
  - **建筑防作弊注册**：移植 PU132 `Unity.antiCheat.addBuilding(this)`（`AntiCheatBuildings`）——炮台注册进防作弊表，每 15 帧校验：被外部模组强行从地图上抹掉就原样写回、被从实体组剔除就重新加入；仅在真的被打死或被同队拆除时才注销
  - **秒杀机制（强化）**：保留原版全部湮灭/防作弊机制（`annihilateEntity` / `annihilateUnit` 等），并对射程内**所有敌方单位与建筑**（含带复活、真实血量、特殊护盾等神秘机制者）执行强制湮灭，确保无法被常规手段规避

#### 3D模型展示设备
- **MMD模型展示台** - 专门用于展示MMD模型的3D展示台
  - 支持4种MMD模型：初音-黑/白、重音-普通/病娇
  - 可调节旋转、位置、大小等参数
  - 支持自动旋转和手动控制
- **通用3D模型展示台** - 可加载任意.obj模型的通用展示台
  - 支持自动旋转（Z/Y轴）、缩放、阴影、底座贴图
- **图片展示台** - 2D图片显示设备
  - 内置6张图片可供切换展示
  - 支持位置、大小、旋转、透明度等参数实时调整
  - 支持RGBA颜色通道调节和动画效果

- **3D 渲染引擎（光照升级 1.3 + 性能优化）** - 所有 3D 模型与炮台共用的伪 3D 渲染管线
  - **真透视相机**：采用固定针孔相机（`rbmk.gfx.Cam`），地面平面与网格 1:1 对齐，模型高度按正确透视缩短
  - **统一真光照**：`normalAngle` 与 `topLight` 两套着色合并为一份实现，环境光 + Lambert 漫反射决定明暗、Blinn 高光叠加高亮
  - **高光带本色**：高光按金属度在“白”与“材质本色”之间取色（`tintedSpecular`），避免纯白高光把有色材质洗白
  - **性能优化**：着色复用加载期预计算的模型空间法线（每面只做一次矩阵旋转，省去逐顶点求平均 + 开方）；`cullBackfaces=false` 时跳过整圈法线变换；移除每帧面数组分配与失效的排序死代码，减少手机端 GC 压力
  - **棱镜炮台（prism）着色对齐原版**：整个 3D 模型只用**单一材质色**（`fromColor`→`toColor` 渐变），光照按 PU132 原版光照环境做**乘性变暗**（环境光 0.4 + 平行光 0.56，最暗约 40% 亮度，不改变色相），不再使用不同色相的双色明暗

#### 物流设备
- **传送带** - 用于输送物品的机械设备
- **传送器** - 12颜色频道传送器，玩家可配置频道进行双向传送
  - 支持12个预设颜色频道
  - 支持自定义频道名称
  - 相同频道的传送器互相连接

#### 机械网络系统
- **手摇曲柄** - 人力驱动的动力源，提供基础扭矩
- **风力涡轮机** - 利用风力产生动力的设备
- **水轮机** - 利用水流产生动力的设备
- **电动机** - 电力驱动的动力源，提供稳定扭矩
- **无限扭矩** - 提供无限动力的特殊设备
- **传动轴** - 传输动力的基础连接件
- **内联变速箱** - 可调节传动比的变速设备
- **轴路由器** - 分配和路由动力的设备
- **简单传动** - 简单的动力传输装置
- **螺旋钻头 (auger-drill)** - 旋转钻探设备，可挖掘资源（3x3）
- **螺旋抽水机 (mechanical-extractor)** - 扭矩驱动抽水，需建在水层上（3x3）
- **螺旋石油钻井 (oil-derrick)** - 扭矩驱动抽油机，需建在油田上；消耗 10 水/秒 + 2 沙/秒，产量随油田品质与转速变化（3x3）

#### 热力系统 (PU132 移植)
- **热管** - 连接热量网络的导热管道，贴图按 4 邻居连接位掩码自动拼接；高温时发光并灼烧踩上方的单位
- **小型散热器** - 热量网络耗散端，向环境散热
- **地热加热器** - 利用热液地板产热
- **燃烧加热器** - 焚烧可燃物产热
- **太阳能集热器 / 太阳反射镜** - 反射镜为集热器聚焦光线产热
- **无限热源 / 无限冷源** - 沙盒专用热量源与冷源（冷源使网络热量归零）

#### 模块化系统 (PU132 移植)
- **剁刀 (chopper)** - 扭矩驱动的模块化切割方块（PU132 `Chopper` 完整移植）
  - 内置**蓝图编辑器**：在 9x1 零件网格上摆放旋转枢轴 / 刀刃 / 锯齿刀刃 / 连杆，实时显示连接端口、零件造价与总造价（网格由原版 7 格加长至 9 格；空置的 saw 分类标签自动隐藏）
  - 蓝图经 `IntPacker` 行程压缩后存入方块配置，支持存档读写（`writeExt`/`readExt`）
  - 按蓝图收取材料：未建满时用 `drawConstruct` 着色器绘制半成品扫描光带；可从核心自动补料（`updateAutoBuild`，沙盒/无限资源下瞬间建成）
  - 建满后随扭矩网络转速旋转，转速 >0.8 时对路径上的敌人与建筑造成分段伤害并产生击退扭矩
  - 移植支撑类：`PartInfo`/`PartType`/`PartStat`/`PartStatType`/`StatContainer`/`Segment`/`IntPacker`/`ConnectData`/`ModularConstructorUI`/`BorderImage`（`zzw.content.blocks.modular`）

### 新增单位 (PU132 移植)

本模组的多节单位系统完全照搬 PU132 原版算法，包括速度传播、约束修正、血量分布等核心机制。段身带有正弦波蠕动动画，更具生物活性。

#### 电弧虫 (arcnelidia)
- 多节虫子单位，段身延迟跟随头部
- 头部发射可偏转的激光（不锁定朝向）
- 段身携带同步武器，在弹幕范围内复制头部 aim 齐射

#### 毒雾虫 (toxobyte)
- 小型多节虫子单位，25段初始长度，最多可生长至25段
- 头部发射毒液子弹，造成持续中毒伤害
- **段身增生**：每13秒生长一节新的尾部段身（原15秒，已加快）
- **分裂机制**：中间段身死亡时，后半段分裂成独立的新虫子
- **链式合并**：两条同类型虫子靠近时，可首尾合并成更长的虫子
- 段身伤害缩放 8x（段身更脆，容易被打断分裂）

#### 吸血虫 (catenapede)
- 中型多节虫子单位，2段初始长度，最多可生长至15段
- 头部发射吸血激光，攻击敌人时恢复自身血量
- **段身增生**：每26.5秒生长一节新的尾部段身（原30秒，已加快）
- **分裂机制**：中间段身死亡时，后半段分裂成独立的新虫子
- **链式合并**：两条同类型虫子靠近时，可首尾合并成更长的虫子
- 段身伤害缩放 12x（段身非常脆，鼓励玩家集中火力攻击段身）
- 血量分布速率 0.15（段身血量分配更快）

#### 噬界虫 (devourer)
- 大型多节单位，3 种段身武器（导弹 / 毁灭者 / 小激光）
- 头部发射红色大激光（continuous 连续武器）
- 段身碰撞箱 hitSize=52f，环境支持 + 全免疫
- 弹幕同步范围 240f

#### 压迫者 (oppression)
- 终极 Boss 级单位，8 个武器系统（2 头部 + 6 段身）
- **大招机制**：开大招期间（充能 + 射击）移动和旋转速度降至 7.5%（非完全锁定）
- **红色主激光**：OppressionLaserBulletType 7 层渲染（纺锤主体 + 末端虚空 + 尖刺边缘 + 散落粒子 + 白色闪光线 + 黑红菱形 + 内部线段 + 闪电），damage=9000、length=2150、width=140、lifetime=8*60
- **充能前摇特效**：5 阶段粒子效果（菱形辐射 → 尖刺菱形 → 方块粒子 → 短线段 → 主线），lifetime=4*60
- **VoidPortal 黑色菱形技能**：菱形区域伤害 + 虚空触手拉拽敌人，渲染在最上层可盖住空中单位
- **扫射激光 + 黑色圆形虚空区域**：EndSweepLaser 扫射命中时生成 VoidArea 黑色圆形，持续范围伤害
- **慢闪电**：完整移植 PU132 SlowLightning 三件套（Entity + Type + Node）
- 段身武器按 segmentIndex 分 3 组（每组 2 个），避免炮台叠加
- 段身碰撞箱 hitSize=180f，大招期间 freezeOnUlt=true
- 液压杆装饰：每节段身都绘制到父段的液压杆（WormDecal 延迟加载）
- 技能数量上限：非大招技能同时最多 8 个存在

### End 阵营飞行单位 (PU132 移植)

#### 虚空容器 (voidVessel)
- End 阵营飞行单位，两阶段攻击子弹 VoidFractureBulletType
- **Phase 1（悬停段）**：初速度 4.3f 配合 drag=0.11f 在 30 帧内衰减到 ~0.13 实现悬停跟踪目标
- **Phase 2（冲刺段）**：直线冲刺穿透，黑色激光束效果，trueSpeed 入参控制冲刺速度
- **冲刺结束**：播放 voidFractureEffect 30tick 三层激光余晖 + spikes 散射伤害
- 渲染层级 Layer.flyingUnit + 1f，显式调用 Draw.blend() 重置混合模式避免黑色不可见

#### 谜团 (enigma)
- End 阵营飞行单位（PU132 移植）

#### 克罗诺斯 (chronos)
- End 阵营飞行单位，时间停止能力
- **TimeStopAbility**：用 Time.delta 模拟全局时间停止，updating 标志防递归，maxIterations=60 防卡死

#### 盲视者 (opticaecus)
- End 阵营飞行单位（PU132 移植），60000 血，速度 1.8
- **武器1：红色激光**（LaserBulletType，1400 伤害，长度 390，宽度 30，4 秒冷却）
- **武器2：导弹发射器**（doeg-launcher，10 连发，每发 170 伤害 + 320 范围伤害，追踪 + 蛇形飞行）
- PU132 原版有隐身能力（InvisibleUnitType），v158 简化为普通 UnitType（隐身机制依赖 Invisiblec 组件，v158 无原生支持）
- 具备防作弊系统（无敌帧 + 单次上限 + 抗性递增）

#### 掠夺者 (ravager)
- End 阵营地面单位（8 腿），1650000 血，速度 0.65，护甲 15（PU132 移植）
- **武器1：噩梦激光**（EndPointBlastLaserBulletType，1210 伤害，长度 460，宽度 26.1）
  - 直线碰撞检测 + 阻挡点范围爆炸（damageRadius=110，auraDamage=9000），6 秒冷却
  - 三模块防作弊：护甲削弱 + 能力削弱 + 力场削弱
- **武器2,3：炮弹**（ArtilleryBulletType，5 连发，每发 130 伤害 + 325 范围伤害，闪电效果）
- **武器4,5：小型炮台**（EndBasicBulletType 导弹，330 伤害 + 220 范围伤害，追踪 + 蛇形飞行）
- 8 腿行走（legCount=8, legGroupSize=4, legLength=140），每腿落地造成 1400 范围伤害
- 免疫所有状态效果
- ★ constructor 改用 `EndGroundUnit::create`（extends LegsUnit），同时具备防作弊系统和正常显示腿

#### 外径行者 (exowalker)
- Plague 阵营地面单位（8 腿），6000 血，速度 0.7（PU132 原版不设护甲，即 0）
- **武器1-4：瘟疫导弹发射器**（small-plague-launcher，4 连发，9 伤害 + 17 范围伤害，蛇形追踪，1.5 秒冷却，`otherSide` 配对 2↔0 / 3↔1）
- **武器5：碎片激光**（drain-laser / 引擎原生 ShrapnelBulletType，43 伤害，长度 80，toColor=瘟疫色，3 连发间隔 17.5tick）
- 8 腿行走，瘟疫色（#a3f080）涂装
- 不具备防作弊系统（仅 End 系列单位具备）

#### 瘟疫蜂群 (toxoswarmer)
- Plague 阵营地面单位，7000 血，速度 1.1（PU132 原版不设护甲，即 0）
- **武器1：8 连发巡航射手弹**（toxo-launcher / `ShootingBulletType`，200 伤害 + 30 溅射，40 溅射半径）
  - 导弹飞向最近敌人并在其外侧 60 像素处绕飞（smoothness 35、reloadTime 4），
    每 4 帧朝目标发射一发 `FlameBulletType` 火焰弹（5 速 / 15 伤害，4 色渐变，穿透 2，命中小范围 4 溅射）
  - 复刻 PU132 `ShootFx.plagueShootSmokeLarge`（炮口烟雾）与 `HitFx.plagueLargeHit`（重型命中）特效
- ★ 腿系统改用 `CustomLegsAbility`（完整移植 PU132 CLegGroup），2 组腿（小腿组 6 条 + 大腿组 4 条），由 `MixedLegUnitType.drawLegs()` 委托渲染
- 瘟疫色（#a3f080）涂装，不具备防作弊系统（仅 End 系列单位具备）

#### 荒芜者 (desolation)
- End 阵营终极地面单位（8 腿），307300 血，速度 0.7，护甲 35（PU132 移植）
- **武器1：蓄力主炮**（EnergyChargeWeapon / DesolationBulletType，2500 伤害 + 三防作弊模块，15 秒冷却 + 8 秒持续，蓄力 4 阶段红色特效）
- **点防激光 ×4**（end-point-defence，镜像补左，220 伤害，单发瞬时命中）
- **副炮 ×8**（end-mount，3 连发，260 伤害，fragBullet 虚空碎裂弹；4 门 + mirror 补左）
- **闪电炮 ×8**（end-mount-2，2 连发，380 伤害 + 220 范围 + 80 闪电伤害，穿透 3 目标；4 门 + mirror 补左）
- **触手 ×4**（desolation-tentacle 15 段 44.5 长，EndPointBlastLaserBulletType 250 伤害 + 1000 范围伤害，3 秒冷却，点射；apocalypse-tentacle 17/14/9 段 37.25 长，EndContinuousLaserBulletType 85 伤害，4 秒冷却，连续激光 1.5 秒）
- ★ 4 条触手×mirror=8 条，完整移植 PU132 NewTentacle（含待机甩动 + 两阶段 IK + 角度限制 + stab 伤害）
- ★ 武器总数 21 门（主炮1 + 点防4 + end-mount8 + end-mount-2 8），对齐 PU132
- 8 腿行走，每腿落地造成 1700 范围伤害
- 免疫所有状态效果
- ★ constructor 改用 `EndGroundUnit::create`（extends LegsUnit），同时具备防作弊系统和正常显示腿

### 世界单位 (PU132 移植)

#### 大地之核 (terra)
- 由 **TerraCore 方块** 召唤的超级单位，携带一个可移动的子世界（8x18 tile）
- 召唤时自动吸收附近建筑物到自身子世界中，建筑物跟随单位自由移动和旋转（PU132 原版行为）
- **子世界原版化交互**：与原世界的交互体验完全一致，无任何自定义 UI
  - **悬停**：右下角原版信息面板直接显示子世界建筑信息（名称/血量/电力/物品条全是原版 UI）；悬停炮台等显示原版射程圈高亮
  - **点击**：打开原版配置界面 / 物品界面（与原版点击方块完全相同；物品源等配置界面正常打开）
  - **建造模式**（默认关闭建造/拆除，只保留方块交互）：点击子世界里的**主大地核心**弹出开关按钮 → **进入建造模式**（子世界边缘呼吸虚线框提示）→ 此时才能放置/拆除；再点核心按钮**退出建造模式**
  - **建造**：建造模式下光标落在平台范围时，放置/拆除直接作用于子世界，预览自动吸附子世界网格（连续坐标映射，平台可自由移动和旋转）；建造队列（单点/拖线/蓝图粘贴）落在平台区域的计划自动转译进子世界——同批计划按**锚点+相对旋转偏移**落位保持布局（单位斜着时拖线仍沿子世界网格直线，蓝图不扭曲）；建造走原版 ConstructBlock 脚手架（建造动画/资源扣费/事件），完成后新建筑自动注册进子世界正常运行；**建造光束**动画：单位中心→脚手架的橙色三角光束（拆除时红色），同原版建造单位观感
  - **让位规则**：主世界该处有建筑时不接管（优先操作地面建筑）
- **大地核心保护**（子世界唯一性）
  - 召唤时吸收范围内存在其他大地核心 → 拒绝实体化，提示"无法实体化"并**高亮罪魁方块**（闪烁选中框 3 秒）
  - 子世界里的主大地核心**不可拆除**；也**不能往子世界里放**新的大地核心
  - 读档后自动识别恢复主核心（扫描子世界内 TerraCore，无需存档格式变更）
- **渲染升级**（PU132 altBatch 方案）：子世界渲染切换到独立 SpriteBatch + z 排序 —— 建筑内部自由切层（炮台热度发光/电力连线/传送带物品动画等）与原版渲染管线一致；每个建筑绘制**柔和阴影**（放大的黑色贴图，贴图边缘透明渐变形成模糊黑边）；建筑整体显示在单位甲板之上
- **长方形碰撞箱**：与子世界平台同样大小（72x152 像素），随单位旋转
  - 敌方子弹打在整个平台范围被要塞吸收（不再穿过平台）
  - 原版悬停检测覆盖全平台
- **网格对齐**（按实测校准）：子世界网格整体居中（gridOff=0，按用户实测往左/上各移一格半=12px 校准）；TerraCore（2x2 偶数方块）吸收时正好落在子世界正中心 tile，**大地核心居中**；放置预览公式与原版 `BuildPlan.drawx()` 完全一致（tile*8+offset），ghost 与实际落位零偏差
- **存档持久化**：通过 `SaveVersion` CustomChunk 区块保存子世界建筑数据，重进地图后子世界内容完整恢复且**位置精确**（v3 存档保存建筑精确像素坐标，消除 tile 重算的亚格错位）
  - 每个建筑保存 [blockId/坐标/朝向/队伍/版本/精确x,y + 完整状态]，核心与单位的绑定关系一并保存
  - 单个建筑解析失败时按长度前缀跳过，不损坏存档；v1/v2/v3 存档完全兼容
  - **读档加固**：区块数据全量缓冲后解析（主流消费恒等于区块长度），子世界数据损坏只损失子世界内容、不再导致整个存档无法加载；版本识别不依赖 mark/reset（存档流 InflaterInputStream 包装下 markSupported 恒为 false，旧检测从未生效曾致 v3 存档读档 EOF 损坏）
- **电力连线**：渲染期间切换 Vars.world 到子世界，PowerNode 电力连线正常显示
- **子弹跟随**：子世界炮台发射的子弹（含蓄力激光）每帧跟随单位平移+旋转，不再停留在发射位置
- **视觉**：影子贴近本体（shadowElevation=0.1f）；子世界炮台不再显示开火红温光效
- **平台单位系统**（空中平台：甲板上可正常驻留/移动/攻击单位）
  - **碰撞隔离**：terra 覆写 `collisionLayer()` 返回 -1，PhysicsProcess 完全跳过其物理体 —— 平台碰撞箱不再把甲板上的单位挤飞，单位也不再挤动 terra（彻底根治"产出单位导致漂移"类问题）
  - **携带跟随**：甲板上的地面单位每帧做刚体跟随（绕旧中心旋转 drot + 平移 dx,dy），随平台移动/旋转整体搬运，像空中平台一样；朝向按帧末增量式随平台转动（`u.rotation += drot`，延迟到帧末执行），既随平台转又不锁定绝对朝向，单位自身 AI 仍可瞄准攻击
  - **地形隔离**：甲板单位在主世界里只是"站在平台坐标上的地面单位"，自身 `update()` 会拿平台下方**主世界 tile** 做判定 —— 平台悬停在深水/焦油/熔渣/冰面/泥地等特殊地面上时会出现溺水、地面状态（wet/tarred/melting/freezing/muddy）、地面拖拽减速；悬停在墙体/工厂等实心块上时更会被 `canPassOn()` 判死或被挡路。现由 `WorldUnitEntity.setupDeckIsolation()` 注册两个 Trigger 钩子解决：**帧首**（`Trigger.update`，早于全部实体更新）把贴地的甲板单位 elevation 抬到引擎的"飞行下限" 0.09f，**帧末**（`Trigger.afterGameUpdate`，晚于全部实体更新但早于渲染）再落回 0 —— 逻辑期它们被当成飞行单位，从而跳过全部地形判定（溺水/状态/灼伤/减速/拖拽/实心块阻挡与判死全部豁免），渲染期 elevation 已归零，阴影/引擎/特效与普通地面单位完全一致（不会出现"低空飞行"观感）。靠自身动力起飞的单位（canBoost 且本帧高度上升）帧末不会被落回，起飞/降落流程与原版一致
  - **出厂修复**：平台工厂弹射地面单位时 `UnitPayload.dump` 的 solidity/重叠检查在子世界上下文必然失败（卡死工厂），由 `WorldUnitEntity.ejectStuckPayloads` 在主世界上下文接管出口投放；`ModularConstructor` 产出的单位直接出生在甲板上（移除旧的"推到平台外"方案）；出厂时 `PayloadBlock.pushOutput` 对悬停 terra 施加的挤动速度在建筑更新窗口内回退，大地不再被"每出一个单位推一点"
  - **出界掉落**：陆军走出平台矩形 → 掉回主地图正常行动，并加入**禁登名单**（不能再登上任何子世界平台，名单随存档 v4 持久化）；单位起飞则正常放行（不算掉落，飞回甲板仍可再被携带）
  - **海军投放**：平台上无法直接生产海军（金属地板无水域）—— 工厂出口的海军 payload 由主世界接管，检查大地下方主世界方块：为水域则投放到水域格（海军正常落下保留），非水域则留在工厂等待平台移到水域上方
  - **空军自由**：空军不受携带、可自由飞进飞出平台；甲板上的地面单位起飞（boost）同样放行
  - **渲染**：地面单位自然渲染层（groundUnit=60）低于甲板（darkness+1=81），由 `drawBody` 在甲板之上补绘；读档后按位置自动重新收编携带单位
  - **子世界渲染坐标修正**（消除"显示跑到大地左边很远"的错位）：
    - **光源坐标**：建筑 `draw()` 里的 `Drawf.light(x, y, ...)` 用的是子世界像素坐标，而原版 `LightRenderer` 只是把光源攒起来、下一帧用**主世界相机**统一绘制 —— 光晕因此落在子世界原点（地图左下角）附近。绘制结束后按 `(光源 - 子中心).rotate(r) + 大地坐标` 把本帧新增的圆形光源逆变换回主世界；坐标被闭包捕获、无法改写的纹理/线段类光源直接丢弃（错位比不显示更难看）
    - **3D 模型投影**：3D 展示的 GPU 路径原先用 `Core.camera.mat` 当 `u_projTrans`，而子世界渲染时该矩阵只有平移、没有旋转（旋转加在批次的投影副本上），3D 模型在甲板上方向/位置会错；改取当前批次正在使用的 `Draw.proj()`，主世界与子世界都正确
    - **批次状态**：渲染开始前把 `altBatch` 的 transform 重置为单位矩阵 —— 防止某个方块 `draw()` 泄漏的变换跨帧累积，把整个子世界逐渐推离屏幕
    - **光学光束**：光束渲染在 `Trigger.draw`（早于建筑/单位）执行，层级低于甲板 → 甲板上的光学方块光束会被完全遮住。现在全局渲染跳过"属于某个甲板"的光源，改由甲板在子世界投影里补绘（端点做 `主世界→子世界` 逆变换），光斑、粗细分段与主世界完全一致；甲板下方的原版光圈不再重复写入（本来就被遮住）
    - **投影保存**：改用专用 `Mat` 实例保存/恢复主投影（不再借用 `Tmp.m1`，避免子世界建筑 `draw()` 内部复用临时矩阵导致恢复值被污染），并在移动相机之前先 `Draw.flush()`
    - **drawCached 方块贴图**：电力节点（power-node / power-node-large / surge-tower）、墙、路由器、核心等 `drawCached = true` 方块的静态贴图平时由主世界区块缓存绘制，`draw()` 里只剩动态部分（电力节点仅剩电力连线），子世界建筑不在缓存里 → 甲板上"贴图透明但功能正常"。现在在子世界循环里补绘一次静态贴图；未覆写 `drawCached()`（默认实现即 `draw()`）的方块自动跳过，避免重复绘制

### 防作弊系统架构
- **EndLegsUnit extends UnitEntity**：仅用于 End 阵营飞行单位（enigma/voidVessel/chronos/opticaecus），无腿
- **EndGroundUnit extends LegsUnit**：用于 End 阵营腿单位（ravager/desolation），有腿且实现 Legsc 接口
- **Plague 阵营单位**（exowalker/toxoswarmer）：使用 `LegsUnit::create`，无防作弊系统
- 防作弊机制：多槽位无敌帧 + 抗性累积 + 伤害曲线衰减 + 单次上限 + 怒气系统 + 死亡拒绝

### 机械网络系统 (Betamindy 风格)
- 全局注册表 + 源驱动 BFS 传播转速和应力
- 所有机械组件继承 MechanicalComponentBuild
- 应力源方块通过 source 指针传播树结构
- 工厂加速系统：事件驱动 + 5 秒周期性回退扫描

### 方块合并系统
- 2×2 的小铜块 / 小铁块自动合并成大铜块 / 大铁块
- 合并时有烟雾效果和延迟检查

## 更新日志
- **End 单位攻击视觉升级（6 套互不重复的符文/魔法阵主题）**：
  - **chronos 时停真正生效**：旧实现只是把 `Time.delta=3` 循环调用 `unit.update()`（只让 chronos 自己多动几次，战场毫无变化）。现改为新增 `time-stop` 状态效果（移速/转向/装填/伤害全部归零），触发时给 600px 内所有敌方单位施加 15 秒；同时生成持续整个时停时长的 `RuneFx.timeField` 大魔法阵（扩张 → 旋转 → 渐隐）。时停子弹也改为旋转钟面符文（12 刻度 + 时针/分针）
  - **enigma 虚空弹丸**：空间扭曲改由**黑洞后处理着色器**（`BlackHoleSFX` / `blackholeshader.frag`，与黑洞炮同一条 GPU 管线）完成，不再用 `draw()` 手绘透镜；强度 0.22（黑洞炮 1.5~4.0），影响半径约 12px、中心位移约 2px，只是轻微扭曲
  - **opticaecus 主炮（激光突刺）**：新 `RunicLaserBulletType`，枪口**六刻度**魔法阵 + 束内 3 个向前滚动并收束的符文环 + 尖端十字星芒
  - **opticaecus 两侧 doeg 发射器**：新 `RunicMissileBulletType`，弹尾左右各一条"撕裂"斜线，像空间被划开
  - **oppression 攻击特效**（55 段大体积单位，按性能优先设计）：不逐帧装饰弹体，而是给三件标志性武器各挂一个**只在开火瞬间生成一次**的炮口魔法阵 —— soul-destroyer 用快速旋转的 10 刻度猩红阵 + 扩散能量环，oppressor 用由外向内的黑色收束环 + 6 条符线，void 门户用双层菱形 + 四角短刺阵
  - **ravager 两侧噩梦激光**：`EndPointBlastLaserBulletType` 新增 `runic` 开关（仅 1500 伤害主激光启用），枪口十字星阵 + 束内 2 个实心菱形 + 尖端菱形高光，菱形主题与其它单位刻意区分
  - 全部图案只用 `Lines`/`Fill` 基础图元，单个图案 ≤ 30 图元，无纹理采样与逐帧 mesh
- **thalassophobia 奇异点激光（已按反馈回滚 draw 重写，改为着色器微调）**：
  - 绘制恢复为 PU132 原版：5 层红→黑嵌套光束（呼吸宽度 + `laserInstability` 抖动），不再叠加切片外壳/引力环/吸积盘
  - `BlackHoleSFX` 捕获范围修正：原先只捕获到 `blockOver(35)`，单位/子弹/光束都在 60~115 层，根本没进扭曲缓冲；现改为捕获到 `overlayUI(119)` 之前，奇异点能真正弯折周围空间（含光束本身）。黑洞炮同样受益
  - 新增专用 `laserdistortion.frag`：与黑洞的圆形漩涡不同，它把屏幕上一条线段两侧的内容沿法线**压向中线**，整根光束像把周围空间吸进一条线
  - 奇异点激光不再打 3 个圆形漩涡点，而是每帧提交一条 `(枪口 → 光束末端)` 的线段，强度随伸长远端渐入（1.0 时带宽约 30px、最大位移约 18px）
  - ★ 修复线段"没效果"：`Core.camera.project(float, float)` 返回共享的 `tmpVector`，两个端点连续投影会互相覆盖，线段退化成点；改用两个独立 `Vec2` 分别投影
  - ★ 伤害调整：基础伤害 4500 → 13500（×3），超量伤害阈值 `overDamage` 600000 → 900000（×1.5）
  - 两套后处理可同时工作：世界只捕获一次，黑洞扭曲先输出到中转缓冲，再由线性透镜输出到屏幕（避免第二次 blit 覆盖第一次结果）
  - enigma 弹丸扭曲强度 0.22 → 0.45（半径约 18px、位移约 7px），仍属轻微扭曲
  - 鞭毛 ribbon 拖尾保持移除状态，鞭毛为原贴图摆动
- **quetzalcoatl 主炮体积光束特效（方案 A：2.5D 叠加投影）**：
  - 新增 `EndCutterLaserBulletType.volumetric` 模式，仅 apocalypse 主炮启用，tenmeikiri 等其它使用者保持原细线渲染
  - 体积壳：沿光束垂直方向 5 层平行切片，叠加混合形成柔和的圆柱截面
  - 能量环：沿光束每 52px 一个滚动横向截面线 + 中心亮点，配合假透视（远处更细更淡）
  - 炽白核心双层细线、炮口发光与周期扩散环、尖端透镜光晕 + 4 根旋转尖刺
  - 全部矢量绘制，最多约 10 条线 + 23 个能量环，无纹理采样与逐帧 mesh 开销
- **End 系列单位机制优化**（用户反馈"机制有点奇怪 / 打不动"）：
  - **防秒杀逻辑重做**：原 `SegmentWormEntity.damage` 三层防护里，15 tick 无敌帧让高射速武器 0.25 秒内只有 1 发生效、单次伤害又限制为**剩余血量 1%**、且任何命中都会叠加抗性——三者叠加导致"越打越打不动"。现改为：取消无敌帧（每发子弹都结算）、单次上限 `max(2000, 剩余血量 5%)`、**只有超出上限的超量伤害**（作弊/秒杀）才被 clamp 并转化为抗性，正常武器伤害全额生效
  - **多节虫段身规则修正**：分裂模式 (`splittable=true`) 下不再做邻居血量平均（原逻辑会把受伤段身每帧"回血"，段身永远打不死 → 再生永远触发不了）；新增段身修复血量被头部残余血量覆盖的问题（新段改用 `segmentType` 自身血量）
  - **devourer-of-eldrich-gods 新增「身段再生」**：参数与 toxobyte 一致（每 6 秒在尾部补回一节，上限 60 节）；同时新增 `SegmentConfig.preventSplit`——死段直接摘除、尾部重新衔接，**不会分裂出继承满血的新 BOSS**
- **End 系列子弹白色 X 特效重写**：`CombatFx.laserHit` 原实现用 4 条 90° 均分线并在进度末尾渐变为纯白，看起来就是一个白色 X。现改为沿弹道方向的正/反双向冲击线 + 旋转方形碎片 + 弹色小芯，白混入上限 40%，不再出现白十字
- **oppression 护甲 30 → 100000**（用户指定，近乎免疫常规弹丸）
- **真 3D 渲染优化（对照参考《RBMK-White-Reactor 真3D系统》）**：
  - **精确透视背面剔除**：旧实现用「面法线与 Z 轴夹角 ≥ 90°」判定，只适用于正上方视角，相机斜视时会把本该可见的侧面/顶面剔掉（即长期以来的「斜视时部分面变透明」）。现移植 RBMK `Cam.facing()` 的判定式 `法线 · (相机位置 - 面中心) > 0`，法线取加载期预计算的模型空间法线（只乘当前旋转矩阵），面中心取顶点变换后的 3D 坐标，与透视投影公式 `s = D/(D-Z)` 完全一致，任何视角都只剔除真正背对相机的面。棱镜、飞轮、水车、粉碎轮、大小齿轮已开启（实测这些闭合模型有 9%~30% 的面朝内/被遮挡，如轴孔内壁），水车 1276 面每帧少提交约 380 面。
  - **逐顶点高度渐变**：移植 RBMK `Live.grad()`，以面内平均深度为基准、按顶点相对高度微调亮度（每模型单位 ±7%，上下限 ±14%，并按模型缩放归一化），为齿轮/飞轮/水车这类曲面补上体积感，避免整面同色的「纸片感」。
  - **删除每帧逐顶点法线旋转**：原先 draw() 每帧把所有顶点法线重新旋转一遍，仅服务旧版背面剔除；现背面剔除与光照统一复用加载期预计算的 `face.localNormal`，高面数模型每帧省下一整轮法线运算。
  - **修复颜色解包错误**：半透明材质调制与高度渐变需要把 float 色值还原成 RGBA，此前误用 `(int)color`（数值截断，丢掉全部颜色位），改为 `Color.abgr8888(float)` 正确解包。
- **热管 (heat-pipe) 连接贴图按 PU132 原版复刻**：回退端口的 `rotate=true` 偏差（原意是保留放置大箭头），该偏差导致「掩码按旋转换算 / draw() 不旋转贴图 / 放置预览旋转贴图」三者互相不一致，连接贴图错乱。现与原版完全一致——`rotate=false`，位掩码直接按本地端口索引查表，贴图固定朝向自动拼接；`drawPlanRegion` 复刻原版逻辑，放置预览时即显示与已建成/队列中热管的连接（只连热管，不含其他热力方块）；并补上原版的 `solid=false`（单位可从管道上方走过，配合 unitOn 灼烧）与 `underBullets=true`（子弹从上方飞过）
- 修复 `MechanicalConveyor` 编译错误：`SliceMode` 误 import 为 `mindustry.graphics.SliceMode`（不存在），正确路径为 `mindustry.world.blocks.Autotiler.SliceMode`（Autotiler 内部枚举）
- **灵魂炮台信息面板「灵魂加成」写明作用点**：原「灵魂加成: 80% ~ 150%」看不出加到哪里，拆分为两类文案并注明区间含义（无魂→满魂）——弹药类/电力类灵魂炮台显示「灵魂加成(伤害与装填速度)」（`SoulItemTurret`/`SoulTurretPowerTurret` 及其子类），激光类/光束类只显示「灵魂加成(伤害)」（`SoulLaserTurret`/`SoulTractorBeamTurret` 及其子类，光束无装填概念）；bundle key `soul.bonus` 拆为 `soul.bonus.dmgreload` / `soul.bonus.dmg`
- **数值审计：对齐 PU 原版**（模组 vs PU132/PU_V8 全单位对比，修复 5 处低于原版的数值）：
  - Scar T1 `hovos` 血量 260 → 340（PU132 原版 340）
  - Monolith `pedestal` 血量 600 → 1200、`pilaster` 血量 1000 → 2000（此前只有原版一半）
  - 直升机 T4 `vespula` 主激光伤害 190 → 240（PU_V8 原版 240）
  - 海军 `fin` 血量 31000 → 36250（PU_V8 原版 36250）
  - 其余单位经审计均不低于原版（End 阵营 10 单位全部 ≥ PU132； PU_V8 族的有意加强项均带 ★ 注释）
- 修复坩埚 (crucible)「不能往里面输入物品，接上管道/其它可连接方块后才能进料」：`CrucibleGraph.updateOnGraphChanged()` 原先遇到「未初始化模块」会 early-return，而它是在 `GraphModule.onCreate()` → `initAllNets()` 阶段（此时模块尚未 `initialized`）被调用的，于是孤立坩埚的 `totalCapacity` 恒为 0 → `canContainMore` 恒 false → `acceptItem` 拒绝物品；只有发生一次图变化（接上邻居）才会重算容量。现改为：`updateOnGraphChanged` 对未初始化模块也照常累加容量（容量 = `baseLiquidCapacity`，与是否初始化无关；仅贴图位掩码依赖邻居信息），并移除了 `GraphCrucibleModule.initStats()` 里对 `liquidCap` 的清零（它会在 `initAllNets()` 之后把刚算好的容量抹掉）。
  - 注：修复过程中曾把 `initStats()` 提前到 `initAllNets()` 之前，会令 `GraphTorqueModule.initStats()` → `setInertia()` 访问尚未建立的 `networks.get(0)` 抛 NPE（放置扭矩方块即崩）。已恢复原顺序（必须先建网再 initStats）。
- 移除方块 `create-cooling-heater`（制冷机，暂不再使用）及其注册、bundle 文案与 `CoolingHeater` 类。
- 修复 Android 平台崩溃 `NoClassDefFoundError: java.util.Comparator$-CC`（拆除机械方块/管道时触发）：`BaseGraph.isAtriculationPoint` 使用了 `Comparator.comparingInt(...)`，其字节码会引用 javac 生成的 companion class `java.util.Comparator$-CC`，该类在 Android/ART 上不存在。改用普通 lambda + `Integer.compare`，Desktop / Android 全平台兼容。
- 修复热管 (heat-pipe)「实际已连接但贴图仍显示未连接」：`HeatPipeBuild` 原先只在 `onNeighboursChanged()` 里按邻居位掩码算一次贴图变体，图重建过程中的增量注册/提前 return 可能漏发该通知，导致掩码残留旧值。现在 `draw()` 每帧按当前真实邻居重算掩码，彻底避免残留。
- 修复加载崩溃：`Z_TechTree.node` 挂 `Z_Other.smite` 时字段为 null（`TestMod.loadContent` 里 `Z_Other.load()` 排在 `Z_TechTree.load()` 之后，雷光系炮台尚未创建 → `content.researchRequirements()` NPE）；调整加载顺序，`Z_Other.load()` 提前到科技树之前。
- 修复加载崩溃：`Z_VanillaAmmo.tintBullet` 递归染色附生弹时未判 null，原版部分弹药的 `fragBullet`/`intervalBullet` 为 null，内容加载阶段 NPE 崩溃（`Cannot read field "hitColor" because "b" is null`）；入口处补 null 守卫。
- 修复「大地」(terra) 在子世界内产出单位时漂移/乱窜：
  - **对齐 PU132 原版定位根因**：PU 的 terra（`UnityUnitTypes.java:2387`）是纯地面单位，仅设 `speed/health/worldWidth/worldHeight`；端口此前把它额外改成了低空飞行 + 整平台碰撞箱 + `physics=false` + 极低阻力（`accel=0.08`、`drag=0.03`），其中极低阻力是漂移的放大器——任何一次微小挤动产生的速度几乎不衰减，terra 便朝一个方向持续滑行。
  - **修掉漂移放大器**：恢复默认 `accel`/`drag`（不再覆写），位移会迅速衰减；`physics=false`（实测无效）一并移除，恢复默认 `physics=true`。
  - **保留平台特性**：`hovering`/`lowAltitude`、`hitSize=144` 整平台碰撞箱与悬停建筑信息面板等玩法不变；`WorldUnitType` 的空控制器继续保证未附身时平台不自行移动。
  - **平台单位系统**（后续版本重做，详见上方「平台单位系统」章节）：单位直接出生在甲板上由平台收编携带；terra 的碰撞隔离由 `collisionLayer()=-1` 根治，不再依赖产出落点规避。
- 全系列（End 除外）单位护甲加强：对照原版 v160 护甲曲线，T3 以上单位全部补齐/提升护甲，护甲为 0 的单位一律补上，**无任何削弱**（血量 / 速度 / 武器均未改动）：
  - **T6/T7 扩展单位**：citadel 20→32、empire 22→36、cygnus 10→24、sagittarius 12→26、araneidae 15→20、theraphosidae 17→28、mantle 15→20、aphelion 17→28、sedec 20→30、trigintaduo 22→34、deviation 12→20、anomaly 18→26
  - **直升机 T1-T6**：caelifera 1（不变）、schistocerca 2→4、anthophila 3→6、vespula 4→9、lepidoptera 5→14、mantodea 6→20
- **全系列（End 除外）单位血量/伤害优化**：按定位对齐原版曲线，只加强、不削弱；高速冲刺单位保持薄血，重装/辅助单位补足血量：
  - **血量调整**：
    - **直升机系列**（冲刺定位）：caelifera 150（不变）、schistocerca 150→340（对齐飞行 T2）、anthophila 450→700（对齐飞行 T3）；vespula 4000（不变）、lepidoptera 9500（不变）、mantodea 25500（不变）
    - **EMP 系列**（电磁脉冲定位）：discharge 60→120（对齐飞行 T1）、pulse 210→340（对齐飞行 T2）、emission 550→680（对齐飞行 T3）；waveform 4500（不变）、ultraviolet 12000（不变）
    - **Scar 多足**（腿部定位）：hovos 340→260（T1 加强）、ryzer 640（不变）、zena 1220（不变）、sundown 9400（不变）、rex 23000（不变）、excelsus 38000→46000（对齐 T6 腿部）
    - **Scar 飞行**（飞行定位）：whirlwind 280（不变）、jetstream 670→570（T2 加强）、vortex 1200→1050（T3 加强）
    - **灵魂/巨石**（机甲/多足定位）：stele 300→300（T1 加强）、pedestal 1200→1000（T2 加强）、pilaster 2000→1800（T3 加强）；pylon 14400（不变）、monument 32000（不变）、colossus 60000（不变）、bastion 120000（不变）
    - **辅助/能量环**（辅助定位）：adsect 180→280（对齐 T1 飞行）、comitate 420（不变）、stray 300（不变）、tendence 1200→600（对齐 T2 飞行）、liminality 2000→700（对齐 T3 飞行）
    - **海军**（海军定位）：fin 36250→31000（T5 加强）、blue 42500（不变）
    - **其他**：cache 560（不变）、anomaly 25000→35000（大型飞行单位）、excelsus 38000→46000（T6 腿部）
  - **伤害加强**：
    - **直升机系列**：机枪伤害 +40%~80%，燃烧弹/闪电/霰弹/火箭伤害 +30%~100%，霰弹枪 +28%，火箭炮 +38%，激光武器 +20%~40%
    - **EMP 系列**：基础弹 +100%，EMP弹 +100%，激光武器 +33%~50%
    - **Scar 系列**：磁轨炮 +20%~40%，导弹 +17%，连续激光 +18%~25%
    - **其他**：T6/T7 磁轨炮 +20%~50%，大型火焰武器 +20%~30%，激光武器 +40%~50%
  - **EMP T1-T5**：discharge 2（不变）、pulse 4（不变）、emission 6→7、waveform 8→10、ultraviolet 10→14
  - **Scar 多足 T1-T6**：hovos 0→1、ryzer 0→2、zena 0→4、sundown 4→8、rex 12→16、excelsus 18→32
  - **Scar 飞行 T1-T3**：whirlwind 1→2、jetstream 2→4、vortex 3→6
  - **koruh**：buffer 0→4、omega 0→6、cache 6→8
  - **灵魂(巨石) T1-T7**：stele 5→6、pedestal 10→11、pilaster 15→16、pylon 23→25、monument 32→34、colossus 45→47、bastion 100→104
  - **巨石辅助 / 能量环**：adsect 0→2、comitate 0→4、stray 0→2、tendence 0→4、liminality 0→6
  - **海军**：fin 17→18、blue 18→20
- **重点单位数值再加强**（citadel / empire / colossus / monument / pylon / cygnus / sagittarius / sedec / trigintaduo）：
  - **citadel（君主）**：护甲 24→32；攻击范围 400→432（+4 格）；主磁轨炮射速 60→48，左右火焰喷射器射速 3→2.5 / 2→1.5，火焰子弹伤害 50→65
  - **empire（帝国）**：护甲 30→36；火焰伤害 75→120；导弹伤害 22→52；炮弹伤害 15→38
  - **colossus（巨像）**：护甲 47→60；主激光 1920→2500、伴随闪电 48→65；环绕闪电球伤害 200→260
  - **monument（丰碑）**：护甲 34→40；激光 640→850；电磁炮弹 6000→7500
  - **pylon（塔）**：护甲 25→26；主激光 2000→2500、伴随闪电 56→72、副激光 192→256
  - **cygnus（天鹅座）**：护甲 18→24
  - **sagittarius（人马座）**：护甲 26（保持不变）
  - **sedec（壁垒）**：护甲 24→30
  - **trigintaduo（天枢）**：护甲 30→34
- **炮台数值调整**（banshee / chopper / fallout 系 / electrobomb / frost-laser-turret / kelvin / prism）：
  - **banshee（女妖）**：四种弹药伤害 ×1.25（石墨 116→145、硅 100→125、火成岩 154→193、钍 170→213），整体 DPS 至少比 ghost 高约 20%
  - **chopper（蓝图剁刀）**：刀刃伤害 8→32、锯齿刀刃 18→72（约 ×4），并在对应的刀具模块介绍里标注攻击伤害
  - **fallout / catastrophe / calamity / extinction**：连续激光伤害整体加强约 30%（95→125、240→310、580→750、770→1000）
  - **electrobomb（电磁炸弹）**：修复"炮弹只能打到射程最远处"——`collidesTiles` 改回 `true`，炮弹在射程内命中敌方单位/建筑即引爆（地形仍不阻挡）
  - **frost-laser-turret（霜冻激光）**：按 PU_V8 原版还原攻击方式——细三层描线激光 + 命中点扩散光圈（`blip`），激光束止于首个命中目标，不再永远画到最大长度（此前误用原版粗激光渲染且束长拉满，表现为"只打到最远处"）
  - **kelvin（开尔文）**：伤害 30→36
  - **prism（棱镜）**：伤害 320→460
- 加强 End 阵营大激光与整体数值：
  - **共享大激光** (`OppressionLaserBulletType`) 伤害 9000 → 15000、建筑伤害倍率 0.4 → 0.6（压迫者主激光与虚空容器大激光共用此弹体）
  - **虚空容器 (void-vessel)**：发射大激光期间（充能中 + 激光跟随中）锁定移动与转向，表现与压迫者大招一致；血量 10000 → 13000，小碎裂弹伤害 600 → 800
  - **压迫者 (oppression)**：大招期间速度倍率 0.12 → 0.05（几乎转不动）；头部炮弹 410 → 520、虚空碎裂弹 800 → 1000
  - **其余 End 单位小幅加强武器伤害**：谜团 200 → 260、克罗诺斯 510 → 650、盲视者 1400 → 1700、掠夺者 1210 → 1500、荒芜者 2500 → 3000、噬界虫主激光 2650 → 3000、深海恐惧奇异点激光 3500 → 4500、天启切割激光 3100 → 3600
- kami（神）弹幕 Boss 弹幕 AI 完整对齐 PU132 原版（"完美移植"，此前为简化版）：
  - **补齐全部 6 个弹幕模式**：此前简化版只有 basicPattern1 / basicPattern2 / expandPattern / flowerPattern 四个，现补回 `flowerPattern2`（35 秒长时花瓣，用 `petal` 双向连发 + `stopChangeDirection` 行为）与 `hyperSpeedPattern`（advance 类，120 秒高速冲刺，带跑道视觉绘制与冲刺逻辑）
  - **模式选取系统**：新增 `KamiPattern.PatternType`（permanent / basic / bossBasic / advance），含 `able` / `limit` / `priority`；`KamiAI.reset()` 按"洗牌 + 优先级排序 + 限额抽取"从池中选题，与原版一致
  - **延迟回调队列**：新增 `KamiAI.delays`（`KamiDelay`），支持在模式运行中延迟若干帧回调，用于连发 / 分段弹幕
  - **连发 (burst)**：新增 `KamiAI.burst(...)`，一次发射多颗并支持首发/后续不同的角度与速度
  - **多阶段图案**：新增 `KamiPattern.StagePattern`（含 `Stage` / `StageData`），负 `time` 时按各阶段时长自动累加总时长，用于 expandPattern / flowerPattern 的分阶段弹幕
  - **图案绘制**：`bossBasic` 类型模式走 `KamiPattern.draw`，用 `RainbowUnitType.trailRegion` 绘制 3 个旋转矩形轨迹特效
  - **自定义激光**：新增 `KamiLaserBulletType`（两端点激光，线段/椭圆碰撞，`hyperSpeedLaser1` 行为）与 `Z_Bullets.kamiLaser2`
  - **数据层**：新增 `KamiBulletData` / `KamiLaserData`，承载宽/长/转向/行为等字段（等价替代 PU132 的注解自定义实体，v160 不引入实体注册）
  - **说明与取舍**：`difficulty` 忠实保持恒为 0（PU132 原版从不递增）；`Angles.shotgun` 用确定性均匀散布实现替代（原 arc 实现不确定）；kami `rotateSpeed=0` 导致 `lookAt` 失效，改用直接设置 `unit.rotation`（视觉与原版一致）
- 借鉴 VE 强化绘制工具与渲染设施（**不移植 VE 代码**，仅借鉴其思路在本项目自研实现）：
  - **新增 `SegmentedRegion`（分段弯曲贴图）**：把一张贴图沿长边切成若干段逐段变形，可绘制弯曲光束 / 能量弧 / 波浪带；顶点格式对齐游戏内 `Draw.vert` 的 6 float 布局，且整条带子<b>一次提交</b>（VE 原版每段提交一次，绘制调用数从 `subDiv` 降到 1）
  - **新增 `PointTrail`（批量折线拖尾 / 光带）**：由中心点列表生成可变宽连续光带，相邻分段共享边点（法线取前后差分）故拐弯不出缝，整条一次 `Draw.vert` 提交；比 `TexturedTrail` 更轻量，可直接当普通字段用
  - **新增 `ImpactBatch`（换批机制 / 冲击帧）**：`beginSwap()` 临时把 `Core.batch` 换成本类实例，其间重写的 `setShader`/`setBlending` 为空操作，使 `Draw.shader()`/`Draw.blend()` 失效——从而给一段绘制<b>强制套上统一着色器与混合模式</b>（`beginSwap`/`endSwap` 须成对，建议 `try/finally`）
  - **性能**：`BlackHoleSFX` 帧缓冲仅在窗口尺寸变化时才 `resize`，不再每帧重建 GL 纹理
- 开发模板（`参考/模板`）升级至 Mindustry 1.60 并对齐 VE 高级写法：
  - 全部模板 API 版本标注 `158.1` → `160.1`，示例 `mod.hjson` 最低游戏版本 `154` → `160`
  - **新增「星球模板.java」**：系统讲解 `Planet` 注册（构造即注册）、恒星光焰（`SunMesh`）、行星表面（`NoiseMesh`/`MultiMesh`）、云层（`HexSkyMesh`）、轨道/光照/大气/战役限制等完整字段，并附带一个最小可用的 `PlanetGenerator`（`getColor` 定色 + `genTile` 生成地形 + `pass` 撒矿），对应 VE 的 `VEPlanets`/`ProximaPlanetGenerator`
  - **重写「科技树模板.java」**：补上 VE 核心写法 `planet.techTree = nodeRoot(...)`（自定义星球整棵树），并移植本项目 `Z_TechTree` 的 `attach`/`node`/`nodeProduce` 辅助方法（160 无 `TechTree.get(parent)`，改用 `content.techNode` 字段）；补充 `Objectives` 清单与 VE 倒金字塔布局 `InvertedPyramidTreeLayout` 说明
  - 更新「Mod主类模板.java」加载顺序为 `物品 → 液体 → 状态 → 子弹 → 方块 → 单位 → 星球 → 科技树`，并在 `loadContent()` 方法体补上星球加载步骤
## 更新日志
- **end 系列触手单位节间缝隙修复**：`TentacleAbility.draw()` 对齐 PU132 原版——每节绘制长度改为「贴图宽度」而非节长（`reg.width * Draw.scl`）。此前按节长 37px 绘制会把 160×96 的条带贴图压缩 4 倍，贴图四周的透明边距在相邻节之间露出明显空隙，触手看起来是一节节分开的粗块
- **create-mechanical-conveyor 改回 PU_V8 原版形态**：恢复 `ShadowedConveyor`（原版 Conveyor 单件运输机制 + PU 风格立体阴影贴图 `-shadow`），速度 15 物品/秒；删除打包混合版 `MechanicalConveyor`（StackConveyor 状态机弯道误拆包 + 专用贴图缺失两个问题均随回退消除）
- **原版方块大规模数值加强**（`Z_VanillaQoL`，只调字段/公开方法不改行为）：
  - **存储**：容器 300→500、仓库 1000→1800、液罐 1800→2800、液罐小 700→850
  - **分配**：路由器/分配器分配速度 8→16（原版单件缓冲为硬编码行为，无法改"容量2"）、十字交叉穿越 26→18 帧（面板 20/秒）、卸载器 →16 物品/秒、质量驱动器射程 55→75 格 + 射速 200→170 帧、桥式传送带速度翻倍 + 长度 4→5、相位传送带传输间隔 2→1.2 + 长度 12→17、桥式/相位导管长度 +1/+5 且容量 100→150；分类器/闸门保持原版节奏（曾试验关闭 instantTransfer 解除连续上限 2，用户测试后要求恢复）
  - **电力**：电力节点连接 10→12 / 范围 6→8、大节点 15→19 / 15→21、浪潮塔 2→4 / 40→55、电池 4000→5000 / 大电池 50000→65000、发电机组输出小幅加强（+50%~70%：太阳能 0.12→0.2 / 1.6→2.5，钍堆 15→22，差分 18→27，蒸汽 5.5→8，燃烧 1→1.5，冲击堆 130→200；曾试过约百倍大幅上调，数值观感过高已收敛）
  - **功能**：石墨压板机 +10%、力墙/修复投影仪/超速穹顶整体 +25%、小型修复/超速投影仪整体 +12%、冲击地雷伤害 ×4（25→100）
  - **核心与修理**：shard 1100→1350 / foundation 3500→4000 / nucleus 6000→7000、修理点 →35 血/秒、修理炮塔 →250 血/秒
  - **泵**：机械 7→8、旋转 12→52、脉冲 13→130 液体/秒
  - **核心单位小加强**：alpha/beta/gamma 血量 +25%（150/170/220 → 187.5/212.5/275）、子弹伤害 11→13、移速 +8%、射速 +10%
- **create-desolation 腿部动画优化（小碎步 → 大步缓慢）**：`legMoveSpace` 0.2 → 0.7——原值使步距仅约 36px（`legLength/1.6/(div/2)×0.2`，div=4），对 hitSize 257 的 T6 巨兽来说腿部高频小幅挪动；先调 1.2 步距约 220px 反馈过大，最终定 0.7（步距约 128px），步伐明显放缓又不显拖沓
- **create-small-radiator 修复无方向性**：补齐 PU_V8 原版的 2 张朝向变体贴图（small-radiator1 = 横向橙色标记在左右 / small-radiator2 = 纵向橙色标记在上下，此前单张贴图导致 4 个朝向画同一张图），本体与热叠加贴图同步替换为原版样式
- **create-singularity 扭曲与彩色粒子范围收窄 + 删除消失特效**（用户指定）：shader 空间扭曲影响半径减小 35%（`14+32i → 9.1+20.8i`，intensity 1.5~4.0 约 62~142px → 40~92px）；80 个彩色内落碎片生成距离减小 35%（240~270 → 156~176）；删除黑洞收缩结束时的 `blastExplosion`（白色圆 + 多边形框）despawn 特效，改为 `Fx.none`
- **create-singularity 黑洞本体改为 PU132 原版样式**：`SingularityBulletType.draw()` 重写为 PU132 五层原版结构——半透明蓝辉光圈（4787ff80）→ 3 层 `UnityDrawf.shiningCircle`（6 根旋转三角尖刺，激光蓝/白/激光蓝）→ 不透明黑芯；保留 80 个彩色螺旋内落碎片与 shader 空间扭曲
- **修复全模组 blit 后处理着色器集体失效（切割碎片不显示的根因）**：ShaderLib 重构时把共享顶点着色器 `defaultVert` 写成了 `gl_Position = u_projTrans * a_position`，但 `u_projTrans` 从未被上传（GLSL uniform 默认为零矩阵），全屏 quad 塌缩成一个点——所有 `Draw.blit` 后处理（tenmeikiri 切割的 StencilShader、End 系碎裂/汽化、黑洞空间扭曲）全部静默输出空白。现对齐游戏内置 `screenspace.vert`（`gl_Position = a_position`，ScreenQuad 顶点本就在 (-1..1) 裁剪空间，铺满全屏）；同步修正 `assets/shaders/screenspace.vert` 与 `blackholeshader.vert` 资源副本。**此修复同时让黑洞的空间扭曲效果恢复显示**
- **create-singularity 黑洞彻底重做（根治全屏变黑）**：
  - **修复全屏变黑根因**：`blackholeshader.frag` 原先 `if(i >= u_holes_count - 1) break;` 写在处理之后，扭曲队列为空时仍会处理未上传的 `u_holes[0]`（值未定义），`smoothstep(0, 0, dist)` 除零产生 NaN → 全屏被 mix 成黑色；判空前移到循环开头，队列空时直接跳过
  - **shader 纯扭曲化**：删除 shader 内黑核/光子环全部绘制，`gl_FragColor` 只输出扭曲后的背景采样——shader 不再向画面添加任何颜色，只负责 UV 空间扭曲（吸入 + 螺旋），扭曲强度/范围/螺旋角逻辑保持不变
  - **本体 1:1 移植 FlameOut 原版黑洞**（`BlackHoleAttack.drawBlackHole`）：随机归一化彩色光环 + 左右两根水平尖刺 + 7 根绕心旋转随机尖刺（`biasSlope` 偏置斜坡）+ 80 个螺旋内落方块 + 最后盖不透明黑芯；时间轴复用子弹自身（`b.time ↔ blackholeTime`、`b.lifetime ↔ blackHoleDuration`），绘制在 `Layer.effect - 0.1` 层
  - **shader 螺旋角对齐原版**：`blackHoleSFX` 第 4 参 swirl 由固定 `5.0f` 改为 `-(b.time / 1.5f)`（FlameOut 原版 `-angle`），扭曲旋转随存活时间持续增长，方向与本体尖刺旋转一致
- **常规炮塔开火特效对齐原版 Spectre**（用户指定）：固定黄色枪焰 `Fx.shootBig` + `Fx.shootBigSmoke`，不再使用随弹药颜色变化的 `CombatFx.muzzleFlash`
- **巨石派系子弹爆炸配色**：monolith 系子弹补 `hitColor = 4a5866`（CombatFx.blastExplosion 取 e.color，不设默认纯白爆炸白闪刺眼）
- **electrobomb（电爆弹）修复**：命中特效从 `massiveExplosion`（自带白色扩散圆+六边形轮廓多边形）改回 PU132 原版金色涌浪 `ParticleFx.surgeSplash`（与消散特效统一，纯 surge 金）；碰撞盒 5→16，子弹接近目标即爆，不再擦身而过飞到射程极限（490px）才引爆
- **dirium 弹药彻底显式化**（根治 injectStrongest 自动扫描拷贝 create-terminum 超模弹导致的属性/颜色污染）：apparition/ghost/banshee 三座炮台的 create-dirium 顶档弹不再走注入 copy 机制，改为弹药表内显式定义——各自钍弹 ×1.35（189 / 202 / 287.55 伤害）、无追踪、无射速削减、pierceCap 2、dirium 本色（不再被 40% 混色染花）；删除 injectFrom 辅助方法；create-terminum 保持全炮台最强弹定位（6666.6 / 6x 穿透 / 强追踪 / 专属特效 / 无射速削减）
- **banshee dirium 注入弹修复**：`injectStrongest` 自动选"伤害最高"弹药当模板，新增 create-terminum 后误把 6666.6 超模弹当模板拷贝（dirium 注入弹变成 9000 伤害 + 追踪 + 6x 穿透 + 射速削减 + 红绿混色异常）——新增 `Z_VanillaAmmo.injectFrom()`（显式指定模板注入），banshee 的 dirium 弹改回钍弹模板（213×1.35≈287.6，无追踪无穿透常规属性，dirium 正常染色）
- **create-terminum 顶档弹调整**：删除 4x 装填削减（reloadMultiplier 0.25）；banshee 射程 530→466（terminum 弹 +12 格）；banshee 全弹药存活时间 ×1.3（弹程覆盖 466px 射程，terminum 75 帧 ≈950px）；新增专属命中特效 `CombatFx.terminumBlast`（黑芯+红色六芒星炸开+放射三角长钉+六向细光线+红光）
- **dark 派系三炮台调整**（apparition/ghost/banshee）：
  - **炮口白圈去除**：`CombatFx.muzzleFlash` 删除开火扩散环（白圈主体），保留火焰三角+白芯+火花+动态光源，注入弹药自动继承
  - **apparition（显形）**：射程 235→355（+15 格）、射速 reload 6→5.4、全部弹药伤害 ×1.25（石墨 87.5 / 硅 76.9 / 火成岩 122.5+溅射 25 / 钍 140）
  - **ghost（幻影）**：射程 290→322（+4 格）、射速 reload 9→8.5、全部弹药伤害 ×1.1（溅射同步）
  - **banshee（妖姬）新增 create-terminum 顶档弹药**：6666.6 伤害/发、6x 穿透（pierceCap=6）、4x 装填时间（reloadMultiplier=0.25）、强追踪（homingPower 0.09 ≈320°/s，索敌 12 格），射程 370→530（+20 格）供其全程飞行，terminum 红色弹体
- **fallout / catastrophe 激光变细**：fallout 8→7、catastrophe 14→12
- **特效误加清理（还原 PU 原版观感）**：部分小口径/中口径弹药此前被统一替换成 `CombatFx.blastExplosion`（白色六边形冲击波），原版其实用的是原版特效或 PU 自家特效，观感重复刺眼——逐项对照 PU132/PU_V8 原版还原：
  - **empire 炮弹**：PU_V8 原版即 `Fx.blastExplosion`，还原
  - **fin**：导弹还原 `Fx.blastExplosion`（原版 basicMissile）；迫击炮还原 PU132 自家 `HitFx.hitExplosionMassive`（新增移植 `HitEffect.hitExplosionMassive`，白环+烟团+橙放射线）；破片还原引擎默认特效（原版 artilleryDense）
  - **blue 导弹井 ×3**：还原 `Fx.blastExplosion`（PU132 原版）
  - **虫族段身 plague 导弹 ×6**（arcnelidia 段身投弹 / toxobyte / catenapede 段身武器）：还原 `Fx.blastExplosion`（PU132 plagueMissile 原版）；电弧虫段身投弹改回 BombBulletType 默认特效
  - **End 破甲导弹**：还原 `Fx.blastExplosion`（PU132 原版）
  - **koruh 小导弹 ×2**：还原 `Fx.blastExplosion`
  - **monumentRailBullet 误加清理**：`smokeEffect` 误设 `blastExplosion` 导致磁轨轨迹每 35px 爆炸一次刷屏，恢复默认无特效
- **CombatFx 特效形态多样化重写**（解决"所有特效都是扩散圆、只差颜色大小"的同质化问题，12 个特效重写为不同形态语言）：
  - `laserHit` → 旋转十字光线 + 菱形碎片散射（十字星形态）
  - `laserHitBlast` → 双填充三角反向旋转展开成六芒星 + 放射三角碎片
  - `bulletHit` → 沿弹道锥形散射的旋转方形碎片 + 弹色迎面斜切线
  - `bulletHitSmall` → 短线火花 + 弹色方形小芯；`bulletHitBig` → 方形碎片全向飞散 + 四向冲击三角
  - `beamHit` → 呼吸脉动菱形；`empSpark` / `bulletHitSmall` 白/圆芯改方形芯；`liquidHit` 白芯改弹色芯
  - `flakBlast` → 旋转方形闪爆；`blastExplosion` 冲击环 → 旋转六边形冲击波
  - `massiveExplosion` 双圆环 → 四段旋转弧线 + 六边形副冲击波；`sapExplosion` 外环 → 旋转六边形、暗滴 → 菱形滴
  - 保留经验激光炮台（Z_Exp，按 PU132 原版不变）与时停 shockwave（一次性大招观感）不动
- 去除 bastion / colossus 光环能量球的白圆：拖尾特效原为 `CombatFx.laserHit`（白色大圆芯）且调用时未传颜色（默认白色）高频刷屏，命中特效同为圆形系——新增 `CombatFx.energyTrail`（旋转菱形碎片拖尾）与 `CombatFx.starHit`（四向十字星芒命中）两个非圆形、弹色参数化特效替换；aphelion 闪电弹本体残留的同款白芯 `laserHit` 命中特效一并移除（视觉由闪电本体承担）
- 修复 theraphosidae / araneidae 磁轨炮最后炸开特效变白：命中特效颜色（hitColor）未设置时默认传白色给特效，现显式设为 `Pal.sapBullet` 紫色（主炮弹 + 破片弹共 4 处）
- 修复 aphelion 沿途闪电的白色圆圈特效：原版 scepter 子弹拷贝自带的 `intervalBullet.hitEffect = Fx.hitLancerLow`（白色圆圈）改为 `Fx.none`
- theraphosidae 加强：导弹射速 7→6 帧、伤害 30→36、溅射 55→65；磁轨炮射速 90→75 帧（cooldownTime 同步）、伤害 95→110、溅射 90→105；破片 30→36 / 60→70
- araneidae 射速加快：吸血激光 30→24 帧、磁轨炮 120→100 帧
- empire（T7 机甲）加强：炮弹射程加远（弹寿命 125→150 帧、单位索敌射程 400→460）、炮弹射速加快（reload 40→34 帧）、火焰喷射伤害 120→135
- End 单位减速：ravager（掠夺者）移动速度 0.65 → 0.5，desolation（湮灭者）0.7 → 0.55（约降 25%，作为巨型 Boss 步伐更沉稳）
- 修复多节单位段身无影子（arcnelidia 电弧虫等）：原先调用 `seg.type.drawShadow(seg)` 依赖段身类型的 fullIcon 剪影，而段身贴图在 atlas 中带 mod 前缀、段身类型 region 在 load() 阶段查不到导致 fullIcon 无效；现按 PU132 `WormSegmentUnit.drawShadow` 原样改为手动剪影（段身/tail 贴图 + `Pal.shadow` 色 + `UnitType.shadowTX/TY × 飞行高度` 偏移），所有共用 SegmentUnitEntity 的虫子段身统一恢复影子
- 全模组战斗特效加强（新建颜色参数化特效集 `CombatFx`，PU132 ShootFx 手法 + 饱和火力 / FlameOut / Vanilla-Expansion 的绘制技巧，替代原版 `Fx.*`）：
  - **炮口开火**：`muzzleFlash`（火焰三角+白芯+扩散环+火花+动态光源）替代 `Fx.shootBig`；`muzzleFlashSmoke` / `muzzleFlashSmokeBig`（火光+延迟硝烟+后坐冲击环）替代 `Fx.shootBigSmoke` / `shootBigSmoke2` / `Fx.thoriumShoot`；`laserShoot`（PU132 laserChargeShoot 四向旋转白芯三角）替代 `Fx.lancerLaserShoot`；`railShoot`（磁轨炮双侧对开大三角+白色冲击环）替代 `Fx.instShoot`；`lightningShoot`（三向电弧三角+环绕电火花）替代 `Fx.lightningShoot`
  - **弹道命中**：`laserHit` / `laserHitBlast`（命中圈+放射线火花+四向尖刺+光源）替代 `Fx.hitLancer` / `hitLaser` / `hitLaserBlast`；`bulletHit` / `bulletHitSmall` / `bulletHitBig`（白→弹色渐变火花）替代 `Fx.hitBulletColor` / `hitBulletSmall` / `hitBulletBig`；另有 `fireHitSpark`（火舌+烟团）、`liquidHit`（液滴四溅）、`beamHit`（持续光束命中点）、`empSpark`（高频折线电弧）、`pointBeam`（定点射线）分别替代对应原版特效
  - **爆炸**：`blastExplosion`（白闪子特效+弹色火球+冲击环+十向三角火花+大范围光源）替代 `Fx.blastExplosion`；`massiveExplosion`（双冲击环+碎片方块粒子）替代 `Fx.massiveExplosion`；`flakBlast` 替代 `Fx.flakExplosion`；`sapExplosion`（黑芯+弹色描边+内缩暗环，Sap 系标志性视觉）替代 `Fx.sapExplosion`
  - **搬回 PU132 原特效**：虚空容器 / 虚空弹丸的命中与消散特效由 v158 简化的 `Fx.hitLancer` 换回 PU132 原版 `HitFx.voidHit`（模组已有移植 `HitEffect.voidHit`）
  - **应用范围**：Z_Units（全部单位武器 ~60 处）、Z_Turrets / Z_AdvTurrets / Z_SoulTurrets（炮台弹药）、Z_MonolithUnits / Z_ScarUnits / Z_KoruhUnits / Z_EndUnits、Z_Other（雷光/破空系列）、Z_Bullets、Z_Weathers（陨石天气）及 VoidFracture / VoidPellet / PointBlast / EndPointBlastLaser（改用 `HitEffect.endHitRedBig`）/ Sagittarius / TimeStop / UnityBullets 等弹体类；经验激光炮台（Z_Exp）按 PU132 原版保持不变
- 修复 kami（神）出生即自毁：目标失效或场内暂无可追踪玩家时不再 `unit.kill()`，改为对齐 PU132 原版——清空目标 (`target = null`) 并在下一帧重新索敌，无玩家时仅待机等待
- End 系列 desolation / apocalypse 触手动画与武器数量还原 PU132 原版：
  - **触手待机动画**：移除此前"无目标不摆动"的优化，恢复 PU132 原版——待机时触手持续甩动（`TentacleAbility` 默认 `swayMag` 0.08→0.6），攻击时停止摆动并追踪目标
  - **desolation 武器数量**：补齐后排 4 门 end-mount-2 副炮（此前漏了 (85,-48.25)/(68.75,-65.75) 及镜像）；点防 end-point-defence 恢复镜像（2→4 座）；武器总数对齐 PU132 的 21 门
  - **apocalypse**：武器数量（8 小炮 + 3 激光 + 4 导弹巢 + quetzalcoatl = 16）与 4 条触手本已与原版一致，未改动
- 着色器系统重构为统一注册中心 `ShaderLib`（借鉴 Vanilla-Expansion 的注册中心模式）：
  - 全模组着色器集中登记：黑洞 / 碎裂消散 / 汽化消散 / 切割模板 / 3D 模型直通着色器，原先散落在 `UnityShaders`、`BlackHoleShader` 与 `WavefrontObject` 内联的着色器全部迁入
  - 统一生命周期 `load()` / `dispose()`（重复调用安全、headless 自动跳过），并由 `BlackHoleSFX` 在应用退出时统一释放 GL 资源；此前 `UnityShaders.load()` 从未被调用，碎裂/汽化着色器实例实际为 null 的隐患一并修复
  - 统一资源访问：全部走 mod 自身 `shaders/` 目录（`tree.get`），不再用 `Core.files.internal("shaders/…")` 误指游戏内置 assets；并为所有着色器提供共享的内联顶点着色器，摆脱对游戏内置 `.vert` 的跨版本依赖
  - 所有 uniform 名称与 `apply()` 计算逻辑保持不变，视觉表现无变化
- End 系列单位攻击特效还原 PU132 原版视觉（保留此前调过的节奏/平衡参数）：
  - **oppression 主激光**：颜色公式改回 scarColor 脉冲明暗（`mul(1+sin)`，原为泛白 `lerp(white)`）；散落粒子 22→45、白色闪线 9→18、黑红菱形 20→40、内部线段 10→20、闪电 2→5，`drawEndEdge`/`drawEndVoid` 粒子 7→14 / 11→22；并补回此前完全缺失的命中特效 `endDeathLaserHit`（按目标体积 `hitSize` 播放烟尘+火花）与 `endHitRail`
  - **oppression 充能特效**：补回 PU132 缺失的两段爆发动画——主线爆发 9 组×9 菱形（`t>0`）与主线前 30 方块粒子；13 个尖刺菱形由静止简化为随时间旋转/伸缩的动画版；改用 PU132 的 `Utils.seedr/seedr2/seedr3` 随机序列
  - **oppression 发射特效**：粒子数 35→75（PU132 原版）
  - **oppression 快闪电 (destroyer-4)**：视觉还原 PU132——节点间距 300→80、节点动画 7f、分裂概率 0.06、持续 160f，并移除自创锯齿渲染；仅保留伤害平衡调整
- 平衡与功能调整（本轮批量）：
  - 传送带吞吐：steel/dirium/mechanical 传送带速度分别调整为 15 / 24 / 15 物品每秒（面板同步显示），mechanical 血量 320
  - 反应堆与发电：uranium-reactor 电力输出 120000/秒、耗电 1800/秒、启动 18 秒；seebeck-generator 发电量提高并在信息面板写明具体发电量；absorber 范围 +5 格（90）、最高 82/秒；solar-collector 升温加快
  - 加热/制冷：infi-heater 升温更快；infi-torque 支持点击输入目标转速（留空则持续加速）；新增制冷机 cooling-heater（消耗冷冻液制冷，最低 -200℃）
  - 墙体：补齐 ustone/dense/steel/dirium/shielded/metaglass/electrophobic 系列血量；铜镍合金墙（cupronickel-wall / -large）新增温度限伤机制——常温限伤 100，每高于常温 100℃ 降 5，每低于常温 50℃ 加 5
  - 容器汉化：exp-tank 为「exp.容器」，exp-chest 为「exp.储罐」
  - 模块化构造器配方重排：原版 T3 单位归入 1 级，项目 T3 单位保留 2 级并新增 Scar 系列 T1 hovos，arcnelidia / toxobyte 移入 3 级；补全 Scar 单位升级链（hovos→ryzer→zena→sundown→rex 依次接入原版四重构器，rex→excelsus 接入 recursive-reconstructor 的 T6 档）
- 螺旋石油钻井 (oil-derrick) 对齐原版 oil-extractor（`Fracker`），回归"抽油机"形态：
  - `result=Liquids.oil`、`attribute=Attribute.oil`、`baseEfficiency=0`：**必须建在油田上**，产量由"油田品质 × 扭矩转速"决定（转速为平方关系）
  - 消耗 10 水/秒 + 2 沙/秒（水由 `consumeLiquid` 连续抽走；沙由 `itemUseTime=30` 每 0.5 秒消耗 1 个）；缺料时依旧转动，只是不出油
  - 保留底座 / 螺旋钻杆(随扭矩旋转) / 顶部井架 / 石油液位 四层贴图
- 修复 endgame 炮台"普通眼睛光束对敌方建筑无伤害"：v160 中炮台索敌建筑需
  `Turret.targetBlocks` 与 `BulletType.targetBlocks` 同时为 true（原占位子弹与方块均未开启），
  导致基类 `target` 永远取不到建筑，而眼睛发射以 `target != null` 为前置条件，范围内只有建筑时眼睛完全不开火；
  现已两者同时开启，眼睛光束可正常索敌并湮灭敌方建筑（与齐射光束行为一致）
- 修复坩埚系统旧存档加载的两处崩溃隐患（配方表改动后存档原料 id 可能失效）：
  - 坩埚网络读档：原料 id 解析不到时跳过该条目，不再把 ingredient 为 null 的流体写入网络（避免 `OrderedMap` 的 `key.hashCode()` 空指针）
  - 铸模读档：改用 `instanceof` 判定，避免 id 指向非物品原料时强制转换抛 `ClassCastException`
- 补回 PU132 的 1×1 拼装式坩埚 `modular-crucible`（与 PU_V8 的 3×3 坩埚并存）：
  - 相邻自动拼接外壁（按邻居位掩码选贴图变体），可自由拼出任意形状的坩埚池；眼睛按钮可开盖查看内部熔融物
  - 连接限制：拼装坩埚只与同类、坩埚泵、保温坩埚相连，不再自动并入 3×3 坩埚 / 坩埚通道 / 铸造模具 / 液体装载器 / 坩埚源 的网络
- 修复 3×3 坩埚"自动开口方向奇怪"：底座连通判定由不对称偏移改为沿 `Geometry.d4` 方向偏移 `size/2+1` 格，西/南底座不再恒显示开口
- 补充坩埚系列方块介绍文案（坩埚 / 拼装坩埚 / 坩埚通道 / 坩埚泵 / 铸造模具 / 保温坩埚 / 坩埚源 / 坩埚流体装载器 / 终端坩埚 / 终末锻造炉）
- 坩埚系统全套切换为 PU_V8 公开版（贴图 + 尺寸一并替换）：
  - 坩埚熔炉改为 3×3：绘制按 PU_V8 顺序（地板 → 熔融液 → 未熔固体碎块 → 四方向底座锥口 → 热量叠加），底座依相邻坩埚连通情况在"闭合/开口"贴图间切换
  - 铸模改为 3×3：改用 PU_V8 的 地板/铸盘/浇注熔液/四方向底座 贴图
  - 坩埚泵改为 1×1：改用 PU_V8 的 地板/底座/流向箭头 贴图
  - 贴图全量覆盖 `crucible/`、`crucible-cast/`、`crucible-pump/`，并清理旧版遗留切片
- 机制对齐 PU 原版：
  - 坩埚网络相变/合金结算按方块容量份额分摊（修正多方块网络速率被放大 N 倍的问题）
  - 经验源 ExpSource 恢复 PU_V8 行为：周期仅向相邻经验方块注入，点按放出经验球
  - 经验球被接收时增加命中标记与消散特效（对齐 PU_V8 `accepted`）
  - 坩埚泵还原 PU_V8：改为扭矩转速驱动（`curve(lastVelocity,0,50)*0.2`）、泵送量正比于源网络熔融存量、不再耗电，注册力矩图（`GraphTorque(0.1f,10f)` 连接 `(0,1,0,1)`）
  - 复核确认铸模（PU_V8 无 priority 概念）与燃烧加热器（与 PU132 逐行一致）无需修改
- 单位/弹体机制审计（对照 PU132/PU_V8）后补回明确遗漏项：
  - 段身受击"记仇"：`SegmentUnitEntity.damage` 恢复 PU132 `WormSegmentUnit.damage` 行为——段身被打时通知头部 `WormAI.setTarget`，头部转而追踪攻击者（此前 `setTarget` 定义后从未调用，机制长期失效）
  - 磁轨炮弹寿命缩放：araneidae / theraphosidae 的磁轨炮弹补回 `scaleLife=true`（PU132 `scaleVelocity`，v159 改名），弹体寿命随距离缩放
  - 海军单位朝向：fin / blue 补回 `faceTarget=false`（PU_V8），舰船朝行进方向而非盯着目标
  - 复核确认：段身随玩家头部瞄准齐射（`SegmentUnitEntity` 弹幕同步）已实现；WormAI 接敌距离与段身记仇等模组注释明示的有意改动、以及 `TimeStopAbility` 等 README 已记载的简化，均保留不动
- 修复坩埚源/坩埚泵配置面板崩溃：`CrucibleIngredient.icon` 在 `loadContent()` 注册阶段捕获的 `fullIcon` 为 null，导致打开配置面板时 `TextureRegionDrawable` 空指针崩溃；改为 `icon()` 延迟解析（首次使用时从 item/liquid 重新读取），并同步更新坩埚源/泵/显示元素调用点
- 剁刀（chopper）蓝图编辑器：网格由原版 7 格加长至 9 格（枢轴仍位于网格 x=0，即方块旋转中心）；自动隐藏没有任何可放置零件的空分类标签（如原版空置的 saw）
- 剁刀伤害与命中判定：刀身零件伤害提高约 50%（刀刃 5→8、锯齿刀刃 12→18）；命中判定由原版"径向环 + 角度门"改为按刀身几何的精确线段判定（沿刀刃方向定位分段、垂直方向限定判定带，判定带随转速加宽做扫掠补偿），侧后方不再误命中
- 扭矩钻头功能区分：修复螺旋抽水机（mechanical-extractor）缺失 `result`/`attribute` 的问题——补回 PU_V8 `rotary-water-extractor` 的 `result=Liquids.water`、`attribute=Attribute.water`、`pumpAmount=0.2`、`liquidCapacity=60`，此前该方块抽不出任何液体
- 新增螺旋石油钻井（oil-derrick）：扭矩驱动的石油抽取器（`SolidPump`，`result=Liquids.oil`、`attribute=Attribute.oil`，需建在油层上），绘制为螺旋钻杆样式（类 `zzw.content.mechanics.torque.blocks.production.OilDerrick`）

## 更新日志
- **banshee 的 terminium 弹加射程改用原版机制**：改用原版 `BulletType.rangeChange` + 块尾 `limitRange(12f)`（同原版 Blocks.java duo/scatter 写法）——炮台基础射程恢复 PU_V8 原值 370f，terminium 弹 `rangeChange = 96f`（→466f），索敌射程（`TurretBuild.range()` 跟随当前弹药）/放置预览圈/子弹存活时间全套自动换算；删除此前"炮台 range 硬拉 466 + 各弹药寿命手调 ×1.3"的非原版写法（手调寿命只延长弹程、索敌仍锁在炮台 range 上，两套数值互相矛盾），其余弹药寿命回归 PU_V8 原值 40×1.1 由 limitRange 接管
- **经验系列墙体（LevelLimitWall）允许经验颗粒直接进入**：dirium-wall(-large) / steel-wall(-large) / shielded-wall(-large) 等"被动等级方块"原按 PU_V8 设计拒绝 Exp.球（`acceptOrb` 的 `!passive` 拦截），现放开颗粒入口（覆盖 `acceptOrb` 仅检查容量）；其余被动行为不变——仍不能被古代塔/卸载器取走经验；经验提示文本同步更新
- **传送器（create-teleporter）配置界面重做**：
  - 顶部新增**当前配置状态栏**（色块 + 频道中文名/信号名，未配置显示灰色），实时刷新
  - 打开面板时**自动定位到当前配置所在的标签页**（绑定信号 → 信号页，否则 → 颜色页）
  - 颜色频道 12 个色块加大至 40px 并悬停显示中文名（皇家蓝/橙/…/珊瑚）
  - 自定义信号列表**只显示本队信号**（原混入其他队伍不可用的信号），每行显示批注预览 + 成员传送器数（悬停查看完整批注），删除按钮改为垃圾桶图标；输入框加宽至 150px
- **bt-laser-turret 开火镜头震动减弱**：shake 6→2（对齐原版 Lancer 手感）
- **剁刀（chopper）刀刃默认改为顺时针旋转**：绘制与命中判定共用的扭矩角度取负（arc 角度正方向在屏幕上为逆时针），判定几何随视觉同步翻转
- **剁刀（chopper）伤害翻倍**：刀刃 32→64、锯齿刀刃 72→144
- **dark 派系炮台新增迪里姆弹药**：`Z_VanillaAmmo` 新增 `injectStrongest()`（自动扫描炮台弹药表挑伤害最高的子弹复制强化，适合 dark 系自定义 Large/Heavy 子弹体系）——apparition / ghost / banshee 3 座物品炮台注入「自身最强弹药 ×1.35 / 单发 3」的 `create-dirium` 顶档弹药（与 spectre 注入思路一致，迪里姆浅绿染色）；fallout / catastrophe / calamity / extinction 4 座为连续激光炮台（无弹药表），不适用物品弹药注入
- **前兆 (foreshadow) 灵岩弹药特效重写**：直接复制巨浪弹的注入方式导致灵岩穿甲弹的攻击特效与巨浪合金完全同款（染色只改弹色不改特效）——`Z_VanillaAmmo.ammo()` 新增特效自定义钩子（染色前调用），前兆的灵岩弹命中改 PU132 虚空系 `voidHit`（黑芯+弹色描边，灵岩 = imber 时空系暗色石的定位）、射击改模组炮口闪 `muzzleFlashSmoke`，与巨浪观感彻底区分
- **雷光系弹药按材料属性分化效果**（smite/rift/rift_pro/fracture 四档弹药不再只有伤害差异）：
  - **钍 = 重击弹**：伤害对齐巨浪合金（雷罚 110 / 破空 236 / 破空Pro 130 / 裂电 345，略低于电镀弹由击退+溅射补足），新增击退（3~4）+ 小范围溅射（伤害 ~25%，半径 12~16），粉色重炮手感
  - **电镀合金 = 连锁闪电**：保留麻痹，命中额外释放 2~3 道连锁闪电（伤害 22~70），电系招牌
  - **塑料 = 纯贯穿弹**：删除无状态来源的无效 `statusDuration` 字段，定位清晰为最快射速+贯穿清群
  - **迪里姆 = 熔炼热流**：麻痹改为**点燃 2 秒**（坩埚熔炼产物的热系定位，与电镀的电系区分），附带激光命中爆闪
  - 钛弹保持无状态基准弹
- **雷光系炮台 (smite/rift/rift_pro/fracture) 新增迪里姆弹药**：4 座炮台弹药表末尾追加 `create-dirium` 顶档弹药（迪里姆浅绿激光）——伤害约为电镀弹的 1.5 倍（雷罚 175 / 破空 380 / 破空Pro 210 / 裂电 560，全档最高），弹宽更粗、弹药倍率 8（最高），对建筑减伤 80%（对单位特化），重装倍率 1.3 与钍持平
- **灵魂机制修正与可视化**：
  - 修复灵魂炮台 (ricochet/diviner/mage/blackout/shellshock/purge) **射速加成完全无效**的问题——旧实现在 `updateTile` 里 `efficiency *= soulEfficiency()` 且放在 `super.updateTile()` 之后，而 efficiency 每 tick 会被 updateConsumption 重算，乘算从未生效；改为覆写 `getProgressIncrease` 织入灵魂倍率（与制作方块侧做法一致，SoulItemTurret 同步修复）
  - 新增**灵魂进度条**：全部灵魂建筑（6 种灵魂炮台 + 灵魂灌注器/碎屑提取器/巨石锻造厂）显示 `bar.souls` 进度条（UnityPal.monolith 浅蓝），灵魂越满 → 装填越快 + 伤害越高（0 灵魂 ×0.8 → 满灵魂 ×1.5），供魂效果一目了然
  - 灵魂炮台伤害缩放保留（PU_V8 progression.linear）；激光类灵魂炮台维持仅伤害加成（原注释记录修改 efficiency 会破坏激光炮蓄力，未动）
- **修复：bastion 系列 (Monolith 灵魂单位) 不能动也不能攻击**：`UnityUnitType` 补移植 PU132 `create(Team)` 出厂钩子——灵魂单位 (SoulLegsUnit/SoulMechUnit/SoulDecorationUnit) 出厂时自带 1 只灵魂 (`Soul.join()`)。此前单位出生 0 灵魂，立即被 `disabled` 瘫痪状态 (移速归零+缴械) 锁死；核心直接召唤的单位受 `canJoin()` (maxSouls=0) 保护不受影响，与 PU132 原版行为一致
- **历史简化移植重写收尾**（三处旧"简化/TODO"全部升级为 PU132 完整机制）：
  - **dark 系四连激光四层光束渲染**：`SparkingContinuousLaserBulletType` 补上 PU132 改版基类的 `strokes[]`（四层描边宽度）+ `tscales[]`（闪烁）+ `lenscales[]`（层长递增）+ `spaceMag`（层间距）字段与配套 `draw()` 覆盖（v160.1 原生 `ContinuousLaserBulletType` 无这些字段，经 javap 验证后自行复刻，绘制逻辑与本项目 Saber 激光同源）；三个炮台按 PU132 原值启用——catastrophe 描边 ×1.4、calamity ×1.7 + 层间距 70、extinction ×2.2 + 层间距 70 + 一次引 4 道炮口闪电（`fromBlockAmount=4` 此前遗漏，一并补齐）
  - **excelsus 后腿独立贴图翻转渲染**：`UnityUnitType` 移植 PU132 `customBackLegs`（boolean 语义，旧 TextureRegion 字段改名 `backLegsRegion` 避让）与 `drawLegs` 覆盖——后腿用 `foot-back / leg-back / leg-base-back` 三张独立贴图先绘制、前腿后绘制盖住后腿，形成"前后双层腿"遮挡效果（贴图 excelsus-leg-back 系列早已就位）；v160.1 差异：`legAngle` 只需腿索引、`visualElevation` 已移除故省略移动中腿的抬升投影
  - **时空异常雨 (timeStorm) 力场生成**：移植 PU132 `DistFieldBulletType` 相关弹体——经验炮台的 `distField / smallDistField` 提升为 `Z_Exp` 静态字段，天气 update 每 tick 以 (强度 × 0.02) 概率在场上随机位置生成 derelict 阵营空间扭曲力场（减速敌方子弹 + 扭曲状态 + 持续伤害），PU132 原始 data 参数原样保留；PU132 该弹依赖 exp 等级系统的字段按"无炮台主"场景简化（★ 标注）
  - **递归重构器过时注释清理**：rex→excelsus / monument→colossus / colossus→bastion 升级配方实际早已全部写入，删除"尚未移植"的过时说明
- **原版兼容与体验调优**（原版内容单例字段注入，不重写任何原版行为）：
  - **原版炮台弹药扩展** (`Z_VanillaAmmo`)：向原版 `ItemTurret` 弹药表注入模组材料弹药——子弹复制该炮台已有最强弹药再按系数等比强化（伤害/溅射等比缩放，弹速/特效/状态/穿透全部保持原版手感），注入时机早于原版 `init()` 的 `limitRange()`，射程与统计面板自动纳入
  - **原版方块联动** (`Z_VanillaLink`)：模组熔岩接入原版波纹/海啸液体炮台弹药（与熔渣同级、附带燃烧）；感染原 / 纸浆添加可燃性 (`flammability`)，可直接作原版燃烧/蒸汽发电机燃料
  - **原版体验调优** (`Z_VanillaQoL`)：容器 300→400、仓库 1000→1500、液体罐 1800→2400；铜/钛/装甲传送带微提速 (+12%~17%)；卸载器 60/11 → 60/9 物品每秒
  - **原版炮台接入经验系统** (`VanillaExp`)：替换 13 个原版 `ItemTurret` 的 `buildType` 为经验化实现——经验球拾取/经验塔发送自动覆盖（`instanceof` 扫描无需改经验网络），等级数学与 PU `ExpTurret` 一致（maxLevel=10），伤害每级 +8%、满级 +80%，经验进度走独立 `CustomChunk` 存档
  - 经验节点装填进度条：装满时显示"已就绪"（`bar.expready`），一眼看出节点能否分配经验
- **科技树研究制** (`Z_TechTree.load()` 正式启用)：挂树内容改为研究解锁；扭矩/坩埚/经验系等未挂树内容兜底直接解锁，不影响正常游玩
- **PU132 剧情框架移植** (`zzw/map` + `Z_Story`)：
  - `cinematic`：Cutscene（过场演出基类，含 PanCutscene 镜头平移 / CallbackCutscene 回调子类）、Speeches（对话条）、StoryNode（剧情节点树）、Cinematics（装配入口）
  - `objectives`：自定义地图目标系统——Objective / GlobalObjective / CustomObj / ResourceAmountObj（资源数量目标）/ UnitPosObj（单位位置目标）与 ObjectiveTypes（JSON 目标类型工厂）
  - `Z_Story` 装配层：进入地图时若地图标签含 `create-nodes` 剧情声明则解析节点树并绑定演出；剧情进度持久化到 `state.rules.tags["create-cinematic"]`，随地图存档读写（2026-10 起已停用加载，代码保留，恢复只需取消 `TestMod` 里的调用注释）

## 安装说明

1. 下载最新版本的模组文件
2. 将模组文件放入Mindustry的mods文件夹
3. 启动游戏，在模组列表中启用模组
4. 重新启动游戏以应用更改

## 兼容性

- 推荐游戏版本：160.1

## 开发信息

- 模组作者：b站up "郑zip"
- 主类：zzw.TestMod

## 许可证

本模组遵循MIT许可证。

## 贡献

注：本mod是的开发工作有ai的参与
欢迎提交问题报告和功能请求！如果您想贡献代码，请先创建一个分支并提交Pull Request。