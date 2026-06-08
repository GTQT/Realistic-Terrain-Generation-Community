package rtg.api.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderServer;
import rtg.api.util.noise.SimplexNoise;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.DecoBase;

// This class stores expensive to calculate info like noises on a chunk basis to be used for regionally varying decorators.
public class ChunkInfo {

    public ChunkPos pos;
    public RTGWorld rtgWorld;

    private final static int TREESIMPLEX = 8;

    private Distribution treeDistribution;

    // ====== 区块16x16高度图缓存，惰性初始化 ======
    private int[] heightCache = null;
    private boolean heightCacheReady = false;

    public ChunkInfo(ChunkPos _pos, RTGWorld _rtgWorld) {
        pos = _pos;
        rtgWorld = _rtgWorld;
    }

    public ChunkInfo(ChunkPos _pos, RTGWorld _rtgWorld, float[] noise) {
        pos = _pos;
        rtgWorld = _rtgWorld;
        if (noise != null) {
            setHeightsFromNoise(noise);
        }
    }

    // ====== 直接从terrain noise构建高度缓存，避免256次world.getHeight()调用 ======
    public void setHeightsFromNoise(float[] noise) {
        heightCache = new int[256];
        for (int i = 0; i < 256; i++) {
            heightCache[i] = (int) noise[i];
        }
        heightCacheReady = true;
    }

    // ====== 获取区块内(x,z)的地表高度（带缓存，仅首次调用时构建） ======
    public int getHeight(int x, int z) {
        if (!heightCacheReady) {
            buildHeightCache();
        }
        return heightCache[(z & 15) * 16 + (x & 15)];
    }

    private void buildHeightCache() {
        heightCache = new int[256];
        net.minecraft.world.World world = rtgWorld.world();
        int baseX = pos.x * 16;
        int baseZ = pos.z * 16;
        net.minecraft.util.math.BlockPos.MutableBlockPos mpos = new net.minecraft.util.math.BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                mpos.setPos(baseX + x, 0, baseZ + z);
                heightCache[z * 16 + x] = world.getHeight(mpos).getY();
            }
        }
        heightCacheReady = true;
    }

    public float treedensity() {
        BlockPos offsetPos = DecoBase.getOffsetPos(pos);
        float noise = rtgWorld.simplexInstance(TREESIMPLEX)
                .noise2f(offsetPos.getX() / treeDistribution.getNoiseDivisor(), offsetPos.getZ() / treeDistribution.getNoiseDivisor())
                * treeDistribution.getNoiseFactor() + treeDistribution.getNoiseAddend();
        return noise;
    }

    private final int TREE_HEIGHT_INDEX = 8;
    private SimplexNoise treeHeightNoise() {return rtgWorld.simplexInstance(TREE_HEIGHT_INDEX);}
    private final float treeHeightNoiseDivisor = 1237;

    private Float storedTreeHeight = null;

    public float treeHeightNoiseValue() {
        if (storedTreeHeight == null) {
            BlockPos offsetPos = DecoBase.getOffsetPos(pos);
            storedTreeHeight = treeHeightNoise()
                    .noise2f(offsetPos.getX() / treeHeightNoiseDivisor, offsetPos.getZ() / treeHeightNoiseDivisor);
        }
        return storedTreeHeight;
    }

    public World world() {return rtgWorld.world();}

}
