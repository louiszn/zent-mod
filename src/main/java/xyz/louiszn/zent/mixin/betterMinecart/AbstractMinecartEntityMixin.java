package xyz.louiszn.zent.mixin.betterMinecart;

import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.DefaultMinecartController;
import net.minecraft.entity.vehicle.MinecartController;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.enums.RailShape;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.louiszn.zent.mixin.invoker.AbstractMinecartEntityInvoker;
import xyz.louiszn.zent.util.IDefaultMinecartController;

@Mixin(AbstractMinecartEntity.class)
public abstract class AbstractMinecartEntityMixin extends Entity {
    public AbstractMinecartEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Shadow @Final
    private MinecartController controller;

    @Shadow
    private boolean onRail;

    /**
     * Initialize minecart entity in world and adjust to the rail using our logic
     */
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void create(World world, double x, double y, double z, EntityType<AbstractMinecartEntity> type, SpawnReason reason, ItemStack stack, @Nullable PlayerEntity player, CallbackInfoReturnable<AbstractMinecartEntity> cir) {
        AbstractMinecartEntity abstractMinecartEntity = type.create(world, reason);

        if (abstractMinecartEntity != null) {
            abstractMinecartEntity.initPosition(x, y, z);
            EntityType.copier(world, stack, player).accept(abstractMinecartEntity);
            MinecartController minecartController = abstractMinecartEntity.getController();

            if (minecartController instanceof DefaultMinecartController controller) {
                BlockPos blockPos = abstractMinecartEntity.getRailOrMinecartPos();
                BlockState blockState = world.getBlockState(blockPos);
                ((IDefaultMinecartController) controller).zent_mod$adjustToRail(blockPos, blockState, true);
            }
        }

        cir.setReturnValue(abstractMinecartEntity);
    }

    /**
     * Enhanced movement with collision
     */
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void move(MovementType type, Vec3d movement, CallbackInfo ci) {
        ci.cancel();

        Vec3d position = this.getPos().add(movement);
        super.move(type, movement);

        boolean handledCollision = this.controller.handleCollision();

        if (handledCollision) {
            super.move(type, position.subtract(this.getPos()));
        }

        if (type.equals(MovementType.PISTON)) {
            this.onRail = false;
        }
    }

    /**
     * Ignore collides at high speed
     */
    @Inject(method = "collidesWith", at = @At("HEAD"), cancellable = true)
    public void collidesWith(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (this.getVelocity().length() >= 1.0 || other instanceof ItemEntity) {
            cir.setReturnValue(false);
            return;
        }

        cir.setReturnValue(true);
    }
}
