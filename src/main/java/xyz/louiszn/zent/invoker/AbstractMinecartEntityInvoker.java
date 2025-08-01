package xyz.louiszn.zent.invoker;

import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractMinecartEntity.class)
public interface AbstractMinecartEntityInvoker {
    @Invoker("moveOffRail")
    void invokeMoveOffRail(ServerWorld world);

    @Invoker("moveAlongTrack")
    double invokeMoveAlongTrack(BlockPos pos, RailShape shape, double remainingMovement);

    @Invoker("applySlowdown")
    Vec3d invokeApplySlowdown(Vec3d velocity);

    @Invoker("getMaxSpeed")
    double invokeGetMaxSpeed(ServerWorld world);

    @Invoker("moveOnRail")
    void invokeMoveOnRail(ServerWorld world);
}
