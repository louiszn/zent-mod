package xyz.louiszn.zent.entity.endPhantom.goal;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.World;
import xyz.louiszn.zent.entity.endPhantom.PhantomMovementType;
import xyz.louiszn.zent.mixin.accessor.PhantomEntityAccessor;
import xyz.louiszn.zent.util.IPhantomEntity;

import java.util.EnumSet;
import java.util.List;

public class SwoopMovementGoal extends Goal {
    private final PhantomEntity phantom;
    private boolean catsNearby;
    private int nextCatCheckAge;

    public SwoopMovementGoal(PhantomEntity phantom) {
        this.phantom = phantom;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return ((IPhantomEntity) phantom).zent_mod$getMovementType() == PhantomMovementType.SWOOP;
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity target = phantom.getTarget();
        if (target == null || !target.isAlive()) return false;

        if (target instanceof PlayerEntity player) {
            if (player.isCreative() || player.isSpectator()) return false;
        }

        if (phantom.age > nextCatCheckAge) {
            nextCatCheckAge = phantom.age + 20;
            Box checkBox = phantom.getBoundingBox().expand(16.0);
            List<CatEntity> cats = phantom.getWorld().getEntitiesByClass(CatEntity.class, checkBox, Entity::isAlive);
            cats.forEach(CatEntity::hiss);
            catsNearby = !cats.isEmpty();
        }

        return !catsNearby && canStart();
    }

    @Override
    public void tick() {
        LivingEntity target = phantom.getTarget();
        if (target == null) return;

        Vec3d targetPos = new Vec3d(target.getX(), target.getBodyY(0.5), target.getZ());

        ((PhantomEntityAccessor) phantom).setTargetPosition(targetPos);

        if (phantom.getBoundingBox().expand(0.2f).intersects(target.getBoundingBox())) {
            phantom.tryAttack(getServerWorld(this.phantom), target);

            ((IPhantomEntity) phantom).zent_mod$setMovementType(PhantomMovementType.WANDER);

            if (!phantom.isSilent()) {
                World world = phantom.getWorld();
                world.syncWorldEvent(WorldEvents.PHANTOM_BITES, phantom.getBlockPos(), 0);
            }
        }
    }

    @Override
    public void stop() {
//        phantom.setTarget(null);
    }
}
