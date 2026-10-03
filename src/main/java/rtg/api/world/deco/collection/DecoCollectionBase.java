package rtg.api.world.deco.collection;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.ChunkPos;
import rtg.api.config.BiomeConfig;
import rtg.api.util.ChunkInfo;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.IRealisticBiome;
import rtg.api.world.deco.AbstractDeco;
import rtg.api.world.deco.DecoBase;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;

import java.util.ArrayList;
import java.util.Random;


/**
 * @author WhichOnesPink
 */
// TODO: [1.12] The DecoCollection* classes should be removed and replaced by utility methods that return a Collection<DecoBase> that
//              are added the same as in IRealisticBiome#addDeco
// Zeno: Restored. Nobody is ever going to do this.

public class DecoCollectionBase extends AbstractDeco {

    public ArrayList<DecoBase> decos;
    public ArrayList<TreeRTG> rtgTrees;
    protected BiomeConfig config;

    public DecoCollectionBase(BiomeConfig config) {

        this.config = config;
        this.decos = new ArrayList<DecoBase>();
        this.rtgTrees = new ArrayList<TreeRTG>();
    }

    // 底层 API 变动（移植上游新树系统 / T3）：上游 DecoCollectionBase 有这个访问器，
    // 新的 DecoCollection*Trees 工厂会用它读 ALLOW_LOGS 等配置。逐行照抄上游。
    protected BiomeConfig getConfig() {
        return config;
    }

    public DecoCollectionBase addDeco(DecoBase deco) {

        if (!deco.properlyDefined()) {
            throw new RuntimeException();
        }
        this.decos.add(deco);
        return this;
    }

    public DecoCollectionBase addDeco(DecoBase deco, boolean allowed) {

        if (allowed) {
            if (!deco.properlyDefined()) {
                throw new RuntimeException();
            }
            this.decos.add(deco);
        }
        return this;
    }

    /**
     * Adds a tree to the list of RTG trees associated with this collection.
     * The 'allowed' parameter allows us to pass biome config booleans dynamically when configuring the trees in the collection.
     *
     * @param tree
     * @param allowed
     */
    public void addTree(TreeRTG tree, boolean allowed) {

        if (allowed) {

            this.rtgTrees.add(tree);
        }
    }

    /**
     * Convenience method for addTree() where 'allowed' is assumed to be true.
     *
     * @param tree
     */
    public void addTree(TreeRTG tree) {

        this.addTree(tree, true);
    }

    public ArrayList<IBlockState> treeLogs() {

        ArrayList<IBlockState> logBlocks = new ArrayList<IBlockState>();

        for (int i = 0; i < rtgTrees.size(); i++) {
            logBlocks.add(rtgTrees.get(i).getLogBlock());
        }

        return logBlocks;
    }

    public ArrayList<IBlockState> treeLeaves() {

        ArrayList<IBlockState> leafBlocks = new ArrayList<IBlockState>();

        for (int i = 0; i < rtgTrees.size(); i++) {
            leafBlocks.add(rtgTrees.get(i).getLeavesBlock());
        }

        return leafBlocks;
    }

    // 底层 API 变动（移植上游新树系统 / T5）：上游 DecoCollectionBase extends AbstractDeco
    // 并实现 generate（逐个跑集合里的 deco）。逐行照抄上游 DecoCollectionBase.java:55-59。
    @Override
    public void generate(final IRealisticBiome biome, final RTGWorld rtgWorld, final Random rand,
                         final ChunkPos chunkPos, final float river, final boolean hasVillage, ChunkInfo chunkInfo) {
        for (AbstractDeco deco : decos) {
            deco.generate(biome, rtgWorld, rand, chunkPos, river, hasVillage, chunkInfo);
        }
    }
}
