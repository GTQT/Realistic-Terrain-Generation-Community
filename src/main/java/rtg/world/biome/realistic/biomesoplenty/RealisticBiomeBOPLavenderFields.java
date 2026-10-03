package rtg.world.biome.realistic.biomesoplenty;


import net.minecraft.world.biome.Biome;
import rtg.api.util.Distribution;
import rtg.api.world.RTGWorld;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.DecoShrub;
import rtg.api.world.deco.DecoTree;
import rtg.api.world.deco.DecoTreeSet;
import rtg.api.world.gen.feature.tree.bop.TreeBOPJacaranda;
import rtg.api.world.gen.feature.tree.rtg.TreeRTG;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGResizable;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceMountainStone;
import rtg.api.world.terrain.TerrainBase;
import rtg.event.EventHandlerCommon;


public class RealisticBiomeBOPLavenderFields extends RealisticBiomeBase {

    public RealisticBiomeBOPLavenderFields(final Biome biome) { super(biome); }

    @Override
    public void initConfig() {
        this.getConfig().ALLOW_VILLAGES.set(true);
    }

    @Override
    public TerrainBase initTerrain() {

        return new TerrainBOPLavenderFields();
    }

    @Override
    public SurfaceBase initSurface() {
        return new SurfaceMountainStone(getConfig(), baseBiome().topBlock, baseBiome().fillerBlock, false, null, 1.2f);
    }

    @Override
    public void initDecos() {

        EventHandlerCommon.treeGenerationManager.suppressBOPBiome(this.baseBiome());

        TreeRTG jacarandaTree = new TreeBOPJacaranda();
        TreeRTGResizable variableJacaranda = new TreeRTGResizable(jacarandaTree);
        variableJacaranda.changeAverageHeightSqrt(-0.5f);
        variableJacaranda.changeHeightNoiseVariability(-1);
        DecoTree jacarandaDeco = new DecoTree(variableJacaranda);

        DecoTreeSet treeCombination = new DecoTreeSet();
        treeCombination.add(jacarandaDeco, 1);
        treeCombination.setDistribution(new Distribution(RTGWorld.getTreeFrequencyNoiseDivisor(), 1.5f, 1f));

        this.treeGenerator = treeCombination;

        DecoShrub decoShrub = new DecoShrub();
        decoShrub.setMaxY(110);
        decoShrub.setChance(10);
        decoShrub.setLoopMultiplier(3f);
        this.addDeco(decoShrub);
    }

    public static class TerrainBOPLavenderFields extends TerrainBase {

        public TerrainBOPLavenderFields() {

        }

        @Override
        public float generateNoise(RTGWorld rtgWorld, int x, int y, float border, float river) {
            // 照抄 RWG SupportBOP.java  lavenderFields -> TerrainMountainSpikes()
            return terrainMountainSpikes(x, y, rtgWorld, river);
        }
    }

}
