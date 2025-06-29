package rtg.api.world.biome;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraft.world.chunk.ChunkPrimer;
import rtg.RTGConfig;
import rtg.api.config.BiomeConfig;
import rtg.api.util.BlockUtil;
import rtg.api.util.ChunkInfo;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase.BeachType;
import rtg.api.world.biome.RealisticBiomeBase.RiverType;
import rtg.api.world.deco.DecoBase;
import rtg.api.world.deco.collection.DecoCollectionBase;
import rtg.api.world.deco.collection.DecoCollectionDesertRiver;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.terrain.TerrainBase;

import java.util.Collection;
import java.util.Random;


public interface IRealisticBiome {

    Biome baseBiome();

    ResourceLocation baseBiomeResLoc();

    int baseBiomeId();

    RiverType getRiverType();

    BeachType getBeachType();

    IRealisticBiome getRiverBiome();

    IRealisticBiome getBeachBiome();

    Biome preferredBeach();

    BiomeConfig getConfig();

    TerrainBase terrain();

    SurfaceBase surface();

    void rReplace(final ChunkPrimer primer, final BlockPos blockPos, final int x, final int y, final int depth, final RTGWorld rtgWorld, final float[] noise, final float river, final Biome[] base);

    void rReplace(ChunkPrimer primer, int i, int j, int x, int y, int depth, RTGWorld rtgWorld, float[] noise, float river, Biome[] base);

    float rNoise(RTGWorld rtgWorld, int x, int y, float border, float river);

    double waterLakeMult();

    double lavaLakeMult();

    float lakePressure(RTGWorld rtgWorld, int x, int y, float border, float lakeInterval, float largeBendSize, float mediumBendSize, float smallBendSize);

    void initDecos();
    
    public boolean allowVanillaTrees();

    Collection<DecoBase> getDecos();

    // TODO: [1.12] To be removed. All trees need to be a Deco and be added through #addDeco.
    @Deprecated
    Collection<TreeRTG> getTrees();

    /**
     * Adds a deco object to the list of biome decos.
     * The 'allowed' parameter allows us to pass biome config booleans dynamically when configuring the decos in the biome.
     */
    default void addDeco(DecoBase deco, boolean allowed) {

        if (allowed) {

            Collection<DecoBase> decos = this.getDecos();

            if (!deco.properlyDefined()) {
                throw new RuntimeException(deco.toString());
            }

            decos.add(deco);
        }
    }

    // Use this method to override a base biome's decorations.
    default void overrideDecorations() {}

    /**
     * Convenience method for addDeco() where 'allowed' is assumed to be true.
     */
    default void addDeco(DecoBase deco) {
        if (!deco.properlyDefined()) {
            throw new RuntimeException(deco.toString());
        }
        this.addDeco(deco, true);
    }

    // TODO: [1.12] The DecoCollection* classes should be removed and replaced by utility methods that return Collection<DecoBase>s that
    //              are added the same as in #addDeco
    @Deprecated
    default void addDecoCollection(DecoCollectionBase decoCollection) {

        // Don't add the desert river deco collection if the user has disabled it.
        if (decoCollection instanceof DecoCollectionDesertRiver) {
            if (!RTGConfig.lushRiverbanksInDesert()) {
                return;
            }
        }

        // Add this collection's decos to master deco list.
        if (decoCollection.decos.size() > 0) {
            for (int i = 0; i < decoCollection.decos.size(); i++) {
                this.addDeco(decoCollection.decos.get(i));
            }
        }

        // If there are any tree decos in this collection, then add the individual TreeRTG objects to master tree list.
        if (decoCollection.rtgTrees.size() > 0) {
            for (int i = 0; i < decoCollection.rtgTrees.size(); i++) {
                this.addTree(decoCollection.rtgTrees.get(i));
            }
        }
    }

    /**
     * Adds a tree to the list of RTG trees associated with this biome.
     * The 'allowed' parameter allows us to pass biome config booleans dynamically when configuring the trees in the biome.
     */
    // TODO: [1.12] To be removed. All trees need to be a Deco and be added through #addDeco.
    @Deprecated
    default void addTree(TreeRTG tree, boolean allowed) {

        if (allowed) {

            // Set the sapling data for this tree before we add it to the list.
            tree.setSaplingBlock(BlockUtil.getSaplingFromLeaves(tree.getLeavesBlock(), Blocks.SAPLING.getDefaultState()));

            /*
             * Make sure all leaves delay their decay to prevent insta-despawning of leaves (e.g. Swamp Willow)
             * The try/catch is a safeguard against trees that use leaves which aren't an instance of BlockLeaves.
             */
            try {
                IBlockState leaves = tree.getLeavesBlock().withProperty(BlockLeaves.CHECK_DECAY, false);
                tree.setLeavesBlock(leaves);
            }
            catch (Exception ignore) {
                // Do nothing.
            }

            this.getTrees().add(tree);
        }
    }

    /**
     * Convenience method for addTree() where 'allowed' is assumed to be true.
     */
    // TODO: [1.12] To be removed. All trees need to be a Deco and be added through #addDeco.
    @Deprecated
    default void addTree(TreeRTG tree) {
        this.addTree(tree, true);
    }

    default void rDecorate(final RTGWorld rtgWorld, final Random rand, final ChunkPos chunkPos, final float river, final boolean hasVillage) {
        ChunkInfo info = new ChunkInfo(chunkPos, rtgWorld);

        //标准RTG装饰
        this.getDecos().stream()
                .filter(deco -> deco.preGenerate(river))
                .forEach(deco -> deco.generate(this, rtgWorld, rand, chunkPos, river, hasVillage, info));

        //RWG特色河流装饰（当河流强度足够高时）
        BlockPos blockPos = new BlockPos(chunkPos.x * 16, 0, chunkPos.z * 16);
        if (river > 0.85f) {
            World world = rtgWorld.world();
            int seaLevel = rtgWorld.getGeneratorSettings().seaLevel;

            // 河床粘土生成（RWG风格）
            for (int i = 0; i < 5 + rand.nextInt(8); i++) { // 5-12次尝试
                BlockPos clayPos = blockPos.add(
                        rand.nextInt(16),
                        seaLevel - 3 - rand.nextInt(4), // 河床底部
                        rand.nextInt(16)
                );

                // 在河床位置生成粘土
                if (world.getBlockState(clayPos).getBlock() == Blocks.SAND) {
                    world.setBlockState(clayPos, Blocks.CLAY.getDefaultState());

                    // 50%几率在上方生成沙砾层
                    if (rand.nextBoolean()) {
                        BlockPos gravelPos = clayPos.up();
                        if (world.getBlockState(gravelPos).getMaterial() == Material.WATER) {
                            world.setBlockState(gravelPos, Blocks.GRAVEL.getDefaultState());
                        }
                    }
                }
            }

            //河岸沙砾生成
            for (int i = 0; i < 7 + rand.nextInt(5); i++) { // 7-12次尝试
                BlockPos gravelPos = blockPos.add(
                        rand.nextInt(16),
                        seaLevel + rand.nextInt(3), // 河岸位置
                        rand.nextInt(16)
                );

                //只在泥土/草地上生成沙砾
                Block currentBlock = world.getBlockState(gravelPos).getBlock();
                if (currentBlock == Blocks.GRASS || currentBlock == Blocks.DIRT) {
                    world.setBlockState(gravelPos, Blocks.GRAVEL.getDefaultState());
                }
            }
        }

        if (overridesHardcoded()) {
            this.baseBiome().decorator.decorate(rtgWorld.world(), rand, baseBiome(), blockPos);
        } else {
            if (this.allowVanillaTrees()) {
                this.baseBiome().decorate(rtgWorld.world(), rand, blockPos);
            } else {
                BiomeDecorator decorator = this.baseBiome().decorator;
                decorator.extraTreeChance = 0f;
                decorator.treesPerChunk = 0;
                this.baseBiome().decorate(rtgWorld.world(), rand, blockPos);
            }
        }
    }
    /**
     * Some biomes have hard-coded decorations.
     * If true, RTG will call the biome decorator's decorate() method instead of the biome's decorate() method.
     */
    default boolean overridesHardcoded() { return false; }

    TerrainBase initTerrain();

    SurfaceBase initSurface();

    void initConfig();
}
