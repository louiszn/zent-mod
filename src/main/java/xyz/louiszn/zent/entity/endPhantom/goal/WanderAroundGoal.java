package xyz.louiszn.zent.entity.endPhantom.goal;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import xyz.louiszn.zent.entity.endPhantom.PhantomMovementType;
import xyz.louiszn.zent.mixin.accessor.PhantomEntityAccessor;
import xyz.louiszn.zent.util.IPhantomEntity;

import java.util.EnumSet;

public class WanderAroundGoal extends Goal {
    private final PhantomEntity phantom;
    private Vec3d targetPosition;
    private int cooldown;

    public Random random = Random.create();

    public WanderAroundGoal(PhantomEntity phantom) {
        this.phantom = phantom;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        return ((IPhantomEntity) phantom).zent_mod$getMovementType() == PhantomMovementType.WANDER;
    }

    @Override
    public void start() {
        pickNewTarget();
    }

    @Override
    public void tick() {
        if (--cooldown <= 0 || targetPosition == null || phantom.squaredDistanceTo(targetPosition) < 4.0) {
            pickNewTarget();
        }

        ((PhantomEntityAccessor) phantom).setTargetPosition(targetPosition);
    }

    private void pickNewTarget() {
        cooldown = 40 + random.nextInt(60);

        Vec3d origin = phantom.getPos();

        double radius = 10 + random.nextDouble() * 20;
        double angle = random.nextDouble() * Math.PI * 2;
        double dx = Math.cos(angle) * radius;
        double dz = Math.sin(angle) * radius;

        double targetX = origin.x + dx;
        double targetZ = origin.z + dz;

        BlockPos groundPos = phantom.getWorld().getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, new BlockPos((int) targetX, 0, (int) targetZ));
        double groundY = groundPos.getY();

        // Bay cao ít nhất 1 block, có thể cao tùy RNG
        double yOffset = 1.0 + random.nextDouble() * 20.0; // hoặc * 40.0 nếu muốn cao hơn
        double targetY = groundY + yOffset;

        this.targetPosition = new Vec3d(targetX, targetY, targetZ);
    }
}
