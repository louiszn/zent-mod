package xyz.louiszn.zent.mixin.endPhantom;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.louiszn.zent.entity.endPhantom.PhantomMovementType;
import xyz.louiszn.zent.entity.endPhantom.goal.RetaliateWhenHurtGoal;
import xyz.louiszn.zent.entity.endPhantom.goal.StartAttackGoal;
import xyz.louiszn.zent.entity.endPhantom.goal.SwoopMovementGoal;
import xyz.louiszn.zent.entity.endPhantom.goal.WanderAroundGoal;
import xyz.louiszn.zent.util.IPhantomEntity;

@Mixin(PhantomEntity.class)
public abstract class PhantomEntityMixin extends FlyingEntity implements IPhantomEntity {
    protected PhantomEntityMixin(EntityType<? extends FlyingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Unique
    PhantomMovementType movementType = PhantomMovementType.WANDER;
    
    @Unique
    public PhantomMovementType zent_mod$getMovementType() {
        return this.movementType;
    }
    
    @Unique
    public void zent_mod$setMovementType(PhantomMovementType movementType) {
        this.movementType = movementType;
    }

    @Inject(method = "initGoals", at = @At("TAIL"))
    private void replaceGoals(CallbackInfo ci) {
        goalSelector.clear(goal -> true);
        targetSelector.clear(goal -> true);

        PhantomEntity self = (PhantomEntity) (Object) this;

        goalSelector.add(1, new StartAttackGoal(self));
        goalSelector.add(2, new SwoopMovementGoal(self));
        goalSelector.add(3, new WanderAroundGoal(self));

        targetSelector.add(1, new RetaliateWhenHurtGoal(self));
    }
}
