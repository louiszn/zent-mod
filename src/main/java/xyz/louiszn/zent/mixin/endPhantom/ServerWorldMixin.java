package xyz.louiszn.zent.mixin.endPhantom;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.spawner.PhantomSpawner;
import net.minecraft.world.spawner.SpecialSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static List<SpecialSpawner> removeDefaultSpawner(List<SpecialSpawner> spawners, @Local(argsOnly = true) RegistryKey<World> worldKey) {
        if (worldKey == World.OVERWORLD) {
            return spawners.stream()
                    .filter((spawner) -> !(spawner instanceof PhantomSpawner))
                    .toList();
        }

        return spawners;
    }
}
