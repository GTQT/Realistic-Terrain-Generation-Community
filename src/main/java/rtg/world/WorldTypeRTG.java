package rtg.world;
import net.minecraft.client.Minecraft;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.WorldInfo;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import rtg.RTG;
import rtg.api.RTGAPI;
import rtg.api.util.Logger;
import rtg.api.world.RTGWorld;
import rtg.compat.ModCompat;
import rtg.world.biome.BiomeProviderBOP;
import rtg.world.biome.BiomeProviderRTG;
import rtg.world.gen.ChunkGeneratorRTG;

import static net.minecraftforge.fml.common.Loader.isModLoaded;


public final class WorldTypeRTG extends WorldType {

    private static WorldTypeRTG INSTANCE;

    private WorldTypeRTG() {
        super(RTG.MODID);
    }

    public static WorldTypeRTG getInstance() {
        if (INSTANCE == null) {
            init();
        }
        return INSTANCE;
    }

    public static void init() {
        INSTANCE = new WorldTypeRTG();
    }

    @Override
    public BiomeProvider getBiomeProvider(World world)
    {
        if (!world.isRemote) {
            final DimensionType type = world.provider.getDimensionType();
            if (RTGAPI.isAllowedDimensionType(type)) {
                Logger.debug("Allowed DimensionType detected (ID:{}, Type:{}, Suffix:{}).. returning BiomeProviderBOP", type.getId(), type, type.getSuffix());
                if (ModCompat.Mods.biomesoplenty.isLoaded())return new BiomeProviderBOP(world);
                else return new BiomeProviderRTG(RTGWorld.getInstance(world));
            } else {
                Logger.debug("DimensionType not in whitelist (ID:{}, Type:{}, Suffix:{}).. returning BiomeProvider", type.getId(), type, type.getSuffix());
            }
        }
        return new BiomeProvider(world.getWorldInfo());
    }

    @Override
    public IChunkGenerator getChunkGenerator(World world, String generatorOptions)
    {
        if (!world.isRemote) {
            final DimensionType type = world.provider.getDimensionType();
            if (RTGAPI.isAllowedDimensionType(type)) {
                Logger.debug("Allowed DimensionType detected (ID:{}, Type:{}, Suffix:{}).. returning ChunkGeneratorRTG", type.getId(), type, type.getSuffix());
                return new ChunkGeneratorRTG(RTGWorld.getInstance(world));
            } else {
                Logger.debug("DimensionType not in whitelist (ID:{}, Type:{}, Suffix:{}).. returning ChunkGeneratorOverworld", type.getId(), type, type.getSuffix());
            }
        }
        final WorldInfo wi = world.getWorldInfo();
        return new ChunkGeneratorOverworld(world, wi.getSeed(), wi.isMapFeaturesEnabled(), wi.getGeneratorOptions());
    }

    @Override
    public float getCloudHeight() {
        return 384F;
    }

    @Override
    public boolean isCustomizable() {
        return true;
    }

    @Override // Client-only
    public String getTranslationKey() {
        return "gui.createWorld.worldtypename";
    }

    @Override // Client-only; we make a proxied call here (no going back to SideOnly) so the dedicated server doesn't flip out with ClassNotFoundException
    @SideOnly(Side.CLIENT)
    public void onCustomizeButton(net.minecraft.client.Minecraft mc, net.minecraft.client.gui.GuiCreateWorld guiCreateWorld) {
        Minecraft.getMinecraft().displayGuiScreen(new rtg.client.GuiCustomizeWorldScreenRTG(guiCreateWorld, guiCreateWorld.chunkProviderSettingsJson));
    }
}
