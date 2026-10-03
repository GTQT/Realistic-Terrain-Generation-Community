package rtg.api.util;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.WeakHashMap;

// 上游来源：Zeno410/Realistic-Terrain-Generation-Plus 提交 74cf4fd「Lighting bug reduction」
// （src/main/java/rtg/api/util/ChunkTracker.java，逐行搬入，仅改缩进为 4 空格）。
//
// 用途：按世界记录「最近处理过的区块」，用于判断某个一次性动作是否已经对某区块做过。
// 每个世界最多记 chunksPerWorld 个区块，超出后按插入顺序淘汰最早的（LinkedHashSet 的迭代顺序）。
// 世界用 WeakHashMap 作键，世界卸载后条目可被回收，不会长期堆积。
//
// ⚠ 上游当前把唯一的调用点注释掉了（EventHandlerCommon 里的 lightChecked），
// 即这个类在上游是「备而不用」的状态。此处按上游原样搬入，调用点同样保持注释状态。
public class ChunkTracker {

    private final WeakHashMap<World, LinkedHashSet<ChunkPos>> remembered = new WeakHashMap<>();
    private final int toRemember;

    public ChunkTracker(int chunksPerWorld) {
        toRemember = chunksPerWorld;
    }

    public synchronized boolean addIfNeeded(World world, ChunkPos chunkPos) {
        if (!remembered.containsKey(world)) {
            remembered.put(world, new LinkedHashSet<ChunkPos>());
        }
        LinkedHashSet<ChunkPos> rememberedChunks = remembered.get(world);
        if (rememberedChunks.contains(chunkPos)) {
            return false;
        }
        rememberedChunks.add(chunkPos);
        if (rememberedChunks.size() > toRemember) {
            Iterator<ChunkPos> list = rememberedChunks.iterator();
            if (list.hasNext()) {
                rememberedChunks.remove(list.next());
            }
        }
        return true;
    }
}
