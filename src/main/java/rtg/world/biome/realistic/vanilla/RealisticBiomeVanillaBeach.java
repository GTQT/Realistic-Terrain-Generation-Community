package rtg.world.biome.realistic.vanilla;


import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import rtg.api.world.RTGWorld;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGCocosNucifera;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceCoastDunes;
import rtg.api.world.terrain.TerrainBase;
import rtg.api.world.biome.RealisticBiomeBase;


public class RealisticBiomeVanillaBeach extends RealisticBiomeBase {

    public static Biome biome = Biomes.BEACH;
    public static Biome river = Biomes.RIVER;

    public RealisticBiomeVanillaBeach() {

        super(biome);
    }

    @Override
    public void initConfig() {
        this.getConfig().SURFACE_BLEED_IN.set(true);
        this.getConfig().SURFACE_BLEED_OUT.set(true);
        this.getConfig().addProperty(this.getConfig().ALLOW_PALM_TREES).set(true);
        this.getConfig().addProperty(this.getConfig().SURFACE_MIX_FILLER_BLOCK).set("");
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainVanillaBeach();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceCoastDunes(getConfig());
    }

    @Override
    public void initDecos() {

        // Scattered palm trees.

        TreeRTG nuciferaTree = new TreeRTGCocosNucifera();
        nuciferaTree.setMinTrunkSize(7);
        nuciferaTree.setMaxTrunkSize(9);
        nuciferaTree.setMinCrownSize(6);
        nuciferaTree.setMaxCrownSize(8);
        nuciferaTree.getValidGroundBlocks().clear();
        nuciferaTree.getValidGroundBlocks().add(Blocks.SAND.getDefaultState());
        this.addTree(nuciferaTree);

        DecoTree palmTrees = new DecoTree(nuciferaTree);
        palmTrees.setTreeType(DecoTree.TreeType.RTG_TREE);
        palmTrees.setTreeCondition(DecoTree.TreeCondition.NOISE_GREATER_AND_RANDOM_CHANCE);
        palmTrees.setTreeConditionNoise(-0.2f);
        palmTrees.setTreeConditionChance(12);
        palmTrees.setMaxY(68);
        this.addDeco(palmTrees, this.getConfig().ALLOW_PALM_TREES.get());
    }

    public static class TerrainVanillaBeach extends TerrainBase {

        public TerrainVanillaBeach() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG coast\RealisticBiomeCoastDunes.java:43-63（手写 rNoise，已提为 TerrainBase.terrainCoastDunes）
            return terrainCoastDunes(x, y, rtgWorld, river);
        }
    }

}
