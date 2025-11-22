package rtg.api.world.deco;

import rtg.api.world.gen.feature.tree.rtg.TreeMaterials;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGBetulaNigra;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGBetulaPendula;
import rtg.api.world.gen.feature.tree.rtg.TreeRTGCupressusSempervirens;

public class DecoVariableBirch extends DecoVariableTree {

    public DecoVariableBirch() {
        tallTree = new TreeRTGBetulaPendula();
        mediumTree = new TreeRTGBetulaNigra();
        smallTree = new TreeRTGCupressusSempervirens();
        this.materials = TreeMaterials.Picker.birch;
        this.averageHeightSqrt += 0f;
    }

}
