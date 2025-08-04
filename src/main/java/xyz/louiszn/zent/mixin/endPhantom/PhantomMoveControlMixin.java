package xyz.louiszn.zent.mixin.endPhantom;

import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PhantomEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.louiszn.zent.mixin.accessor.PhantomEntityAccessor;

@Mixin(targets = "net.minecraft.entity.mob.PhantomEntity$PhantomMoveControl")
public abstract class PhantomMoveControlMixin extends MoveControl {
    public PhantomMoveControlMixin(MobEntity entity) {
        super(entity);
    }

    /**
     * Check for target position before processing movement
     * Target position somehow doesn't exist sometimes
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void onTick(CallbackInfo ci) {
        if (!(this.entity instanceof PhantomEntity)) {
            ci.cancel();
            return;
        }

        PhantomEntity phantom = (PhantomEntity) entity;
        PhantomEntityAccessor accessor = (PhantomEntityAccessor) phantom;

        if (accessor.getTargetPosition() == null) {
            ci.cancel();
        }
    }
}
