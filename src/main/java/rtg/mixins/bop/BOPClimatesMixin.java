package rtg.mixins.bop;

import biomesoplenty.api.enums.BOPClimates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BOPClimates.class)
public class BOPClimatesMixin {

    /**
     * @reason Fix ArrayIndexOutOfBoundsException when BOP climate ordinal exceeds enum bounds.
     *         BOP's GenLayerClimate may produce values beyond the 15-element enum array (e.g., ordinal 24),
     *         crashing with ArrayIndexOutOfBoundsException. Returns ICE_CAP as fallback.
     * @author RTG Community
     */
    @Overwrite(remap = false)
    public static BOPClimates lookup(int ordinal) {
        BOPClimates[] vals = BOPClimates.values();
        if (ordinal >= 0 && ordinal < vals.length) {
            return vals[ordinal];
        }
        return BOPClimates.ICE_CAP;
    }
}
