package xyz.louiszn.zent.entity.endPhantom.goal;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import xyz.louiszn.zent.entity.endPhantom.PhantomMovementType;
import xyz.louiszn.zent.mixin.accessor.PhantomEntityAccessor;
import xyz.louiszn.zent.util.IPhantomEntity;

public class StartAttackGoal extends Goal {
    private int cooldown;
    private final PhantomEntity phantom;

    public StartAttackGoal(PhantomEntity phantom) {
        this.phantom = phantom;
    }

    @Override
    public boolean canStart() {
        LivingEntity target = phantom.getTarget();
        return target != null && TargetPredicate.DEFAULT.test((ServerWorld) phantom.getWorld(), phantom, target);
    }

    @Override
    public void start() {
        this.cooldown = this.getTickCount(10);
        ((IPhantomEntity) phantom).zent_mod$setMovementType(PhantomMovementType.WANDER);
        this.startSwoop();
    }

    @Override
    public void stop() {
        ((IPhantomEntity) phantom).zent_mod$setMovementType(PhantomMovementType.WANDER);
    }

    @Override
    public void tick() {
        if (((IPhantomEntity) phantom).zent_mod$getMovementType() == PhantomMovementType.WANDER) {
            --this.cooldown;
            if (this.cooldown <= 0) {
                ((IPhantomEntity) phantom).zent_mod$setMovementType(PhantomMovementType.SWOOP);
                this.startSwoop();
                this.cooldown = this.getTickCount((8 + phantom.getRandom().nextInt(4)) * 20);
                phantom.playSound(SoundEvents.ENTITY_PHANTOM_SWOOP, 10.0f, 0.95f + phantom.getRandom().nextFloat() * 0.1f);
            }
        }
    }

    private void startSwoop() {
        LivingEntity target = phantom.getTarget();

        if (target == null) return;

        double heightOffset = 20 + phantom.getRandom().nextInt(20);

        Vec3d swoopStart = new Vec3d(
                target.getX(),
                target.getY() + heightOffset,
                target.getZ()
        );

        ((PhantomEntityAccessor) phantom).setTargetPosition(swoopStart);
    }
}
