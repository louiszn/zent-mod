package xyz.louiszn.zent.entity.endPhantom.goal;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import xyz.louiszn.zent.entity.endPhantom.PhantomMovementType;
import xyz.louiszn.zent.util.IPhantomEntity;

import java.util.List;

public class RetaliateWhenHurtGoal extends Goal {
    private final PhantomEntity phantom;
    private LivingEntity lastAttacker;

    public RetaliateWhenHurtGoal(PhantomEntity phantom) {
        this.phantom = phantom;
    }

    @Override
    public boolean canStart() {
        LivingEntity attacker = phantom.getAttacker();
        return attacker != null && attacker != lastAttacker;
    }

    @Override
    public void start() {
        if (phantom.getTarget() != null) {
            return;
        }

        lastAttacker = phantom.getAttacker();
        phantom.setTarget(lastAttacker);

        if (!(phantom.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        List<PhantomEntity> nearbyPhantoms = serverWorld.getEntitiesByClass(
                PhantomEntity.class,
                new Box(phantom.getBlockPos()).expand(32),
                other -> other != phantom && other.getTarget() == null
        );

        if (nearbyPhantoms.isEmpty()) {
            return;
        }

        int count = 1 + phantom.getRandom().nextInt(Math.min(3, nearbyPhantoms.size()));

        for (int i = 0; i < count; i++) {
            PhantomEntity ally = nearbyPhantoms.get(i);

            ally.setTarget(lastAttacker);

            if (ally instanceof IPhantomEntity ipAlly) {
                ipAlly.zent_mod$setMovementType(PhantomMovementType.SWOOP);
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        return phantom.getTarget() != null && phantom.getTarget().isAlive();
    }
}
