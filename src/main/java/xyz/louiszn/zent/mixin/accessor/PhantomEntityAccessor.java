package xyz.louiszn.zent.mixin.accessor;

import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xyz.louiszn.zent.entity.endPhantom.PhantomMovementType;

@Mixin(PhantomEntity.class)
public interface PhantomEntityAccessor {
    @Accessor("targetPosition")
    void setTargetPosition(Vec3d position);

    @Accessor("targetPosition")
    Vec3d getTargetPosition();

    @Accessor("circlingCenter")
    BlockPos getCirclingCenter();
}
