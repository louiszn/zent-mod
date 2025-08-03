package xyz.louiszn.zent;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZentMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ZentMod");

    @Override
    public void onInitialize() {
        BiomeModifications.addSpawn(
                BiomeSelectors.foundInTheEnd(),
                SpawnGroup.CREATURE,
                EntityType.PHANTOM,
                10,
                1,
                2
        );

        LOGGER.info("Loaded zent mod!");
    }
}