package rtg.api.world.deco;

import rtg.api.world.gen.feature.tree.rtg.TreeMaterials;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGQuercusFalcata;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGQuercusNigra;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGQuercusRobur;

/**
 * Variable Forest Trees
 * This class make trees of variable type and height based on a noise parameter from ChunkInfo
 * It gets multiplier
 */

public class DecoVariableOak extends DecoVariableTree {


    private final TreeMaterials.Picker materialsPicker = new TreeMaterials.Picker();

    public DecoVariableOak() {
        tallTree = new TreeRTGQuercusNigra();
        mediumTree = new TreeRTGQuercusFalcata();
        smallTree = new TreeRTGQuercusRobur();
        this.materials = TreeMaterials.Picker.oak;
        this.averageHeightSqrt += 0.2f;
        this.heightNoiseVariability += 0.2f;
        this.mediumTreeMinimumHeight += 1;
        this.tallTreeMinimumHeight += 3;
        this.tallTreeMinimumVariability += 1;
    }

}

