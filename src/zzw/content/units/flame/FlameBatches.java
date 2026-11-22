package zzw.content.units.flame;

import zzw.content.units.flame.graphics.*;

/** FO 全局 Batch 单例的项目内落点 (替代 FlameOut.fragBatch 等静态字段)。 */
public class FlameBatches{
    public static final FragmentationBatch fragBatch = new FragmentationBatch();
    public static final CutBatch cutBatch = new CutBatch();
    public static final VaporizeBatch vaporBatch = new VaporizeBatch();
    public static final DevastationBatch devasBatch = new DevastationBatch();
}
