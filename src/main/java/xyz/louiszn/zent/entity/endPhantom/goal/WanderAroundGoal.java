package xyz.louiszn.zent.entity.endPhantom.goal;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import xyz.louiszn.zent.entity.endPhantom.PhantomMovementType;
import xyz.louiszn.zent.mixin.accessor.PhantomEntityAccessor;
import xyz.louiszn.zent.util.IPhantomEntity;

import java.util.EnumSet;

public class WanderAroundGoal extends Goal {
    private static final int MAX_ATTEMPTS = 10;

    private final PhantomEntity phantom;
    private final Random random = Random.create();

    private Vec3d targetPosition;
    private int cooldown;

    private boolean wasDiving = false;

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
        double baseRadius = 10 + random.nextDouble() * 20;
        double angle = random.nextDouble() * Math.PI * 2;

        double dx = Math.cos(angle) * baseRadius;
        double dz = Math.sin(angle) * baseRadius;

        double targetX = origin.x + dx;
        double targetZ = origin.z + dz;

        double groundY = findGroundY(targetX, targetZ);

        if (groundY < 0) {
            // Failed to find valid position
            return;
        }

        double targetY;

        if (wasDiving) {
            // Swoop up with curved motion
            Vec3d curved = getCurvedOffset(origin, targetX, targetZ, 30 + random.nextDouble() * 10);
            targetX += curved.x;
            targetZ += curved.z;

            targetY = groundY + 40 + random.nextDouble() * 30;
            wasDiving = false;

        } else if (random.nextFloat() < 0.3) {
            // Dive down low
            targetY = groundY + 1 + random.nextDouble() * 2;
            wasDiving = true;

        } else {
            // Wander mid-air
            double yOffset = 20 + random.nextDouble() * 40;
            targetY = groundY + yOffset;
        }

        this.targetPosition = new Vec3d(targetX, targetY, targetZ);
    }

    private double findGroundY(double x, double z) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            BlockPos pos = BlockPos.ofFloored(x, 64, z);
            BlockPos ground = phantom.getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING, pos);
            double y = ground.getY();

            if (y >= 40 && y <= 200) {
                return y;
            }

            double radius = 10 + random.nextDouble() * 20;
            double angle = random.nextDouble() * Math.PI * 2;

            x = phantom.getX() + Math.cos(angle) * radius;
            z = phantom.getZ() + Math.sin(angle) * radius;
        }

        return -1;
    }

    private Vec3d getCurvedOffset(Vec3d origin, double targetX, double targetZ, double distance) {
        Vec3d direction = new Vec3d(targetX - origin.x, 0, targetZ - origin.z).normalize();

        double curveAngle = (random.nextDouble() - 0.5) * (Math.PI / 2); // -45° to +45°
        double cos = Math.cos(curveAngle);
        double sin = Math.sin(curveAngle);

        double curvedX = direction.x * cos - direction.z * sin;
        double curvedZ = direction.x * sin + direction.z * cos;

        return new Vec3d(curvedX * distance, 0, curvedZ * distance);
    }
}
