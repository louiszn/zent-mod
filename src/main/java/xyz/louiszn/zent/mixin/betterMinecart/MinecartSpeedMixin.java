package xyz.louiszn.zent.mixin.betterMinecart;

import com.mojang.datafixers.util.Pair;
import net.minecraft.block.*;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.DefaultMinecartController;
import net.minecraft.entity.vehicle.MinecartController;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.louiszn.zent.invoker.AbstractMinecartEntityInvoker;
import xyz.louiszn.zent.util.IDefaultMinecartController;
import xyz.louiszn.zent.util.MoveIteration;

import java.util.List;

/**
 * Mixin to enhance minecart speed and movement behavior by backporting experimental version
 * - Increases max speed to 2.5 blocks/tick
 * - Improves movement physics and rail handling
 * - Enhanced collision and entity interaction
 */
@Mixin(DefaultMinecartController.class)
public abstract class MinecartSpeedMixin extends MinecartController implements IDefaultMinecartController {

    @Shadow private Vec3d velocity;
    // Constants for improved readability
    @Unique
    private static final double ENHANCED_MAX_SPEED = 2.5;
    @Unique
    private static final double SPEED_RETENTION = 0.997;
    @Unique
    private static final double MINIMUM_VELOCITY_THRESHOLD = 1.0E-5;
    @Unique
    private static final double SLOPE_DECELERATION_BASE = 0.0078125;
    @Unique
    private static final double SLOPE_DECELERATION_MULTIPLIER = 0.02;
    @Unique
    private static final double WATER_DECELERATION_FACTOR = 0.2;

    public MinecartSpeedMixin(AbstractMinecartEntity minecart) {
        super(minecart);
    }

    /**
     * Main tick method that handles minecart movement and physics
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void tick(CallbackInfo ci) {
        ci.cancel();

        ServerWorld world = (ServerWorld) this.getWorld();
        BlockPos railPos = this.minecart.getRailOrMinecartPos();
        BlockState railState = this.getWorld().getBlockState(railPos);

        // Initialize minecart position on first update
        if (this.minecart.isFirstUpdate()) {
            this.minecart.setOnRail(AbstractRailBlock.isRail(railState));
            this.zent_mod$adjustToRail(railPos, railState, true);
        }

        // Apply physics
        this.minecart.applyGravity();
        ((AbstractMinecartEntityInvoker) minecart).invokeMoveOnRail(world);
    }

    /**
     * Enhanced movement on rails with improved physics
     */
    @Inject(method = "moveOnRail", at = @At("HEAD"), cancellable = true)
    public void moveOnRail(ServerWorld world, CallbackInfo ci) {
        ci.cancel();

        MoveIteration moveIteration = new MoveIteration();

        // Continue moving while minecart is alive and should move
        while (moveIteration.shouldContinue() && this.minecart.isAlive()) {
            Vec3d currentVelocity = this.getVelocity();
            BlockPos railPos = this.minecart.getRailOrMinecartPos();
            BlockState railState = this.getWorld().getBlockState(railPos);
            boolean isOnRail = AbstractRailBlock.isRail(railState);

            // Update rail status and adjust position if needed
            if (this.minecart.isOnRail() != isOnRail) {
                this.minecart.setOnRail(isOnRail);
                this.zent_mod$adjustToRail(railPos, railState, false);
            }

            if (isOnRail) {
                handleOnRailMovement(world, moveIteration, currentVelocity, railPos, railState);
            } else {
                handleOffRailMovement(world, moveIteration);
            }

            updateMinecartRotation();

            // Handle block collisions
            Vec3d positionChange = this.getPos().subtract(this.minecart.getLastRenderPos());
            if (positionChange.length() > MINIMUM_VELOCITY_THRESHOLD || moveIteration.initial) {
                this.minecart.tickBlockCollision();
                this.minecart.tickBlockCollision(); // Called twice for better collision detection
            }

            moveIteration.initial = false;
        }
    }

    /**
     * Handles movement when minecart is on rails
     */
    @Unique
    private void handleOnRailMovement(ServerWorld world, MoveIteration moveIteration,
                                      Vec3d currentVelocity, BlockPos railPos, BlockState railState) {
        this.minecart.onLanding();
        this.minecart.resetPosition();

        // Handle activator rail interaction
        if (railState.isOf(Blocks.ACTIVATOR_RAIL)) {
            boolean isPowered = railState.get(PoweredRailBlock.POWERED);
            this.minecart.onActivatorRail(railPos.getX(), railPos.getY(), railPos.getZ(), isPowered);
        }

        // Calculate new velocity and move along track
        RailShape railShape = railState.get(((AbstractRailBlock) railState.getBlock()).getShapeProperty());
        Vec3d newVelocity = this.calcNewHorizontalVelocity(world, currentVelocity.getHorizontal(),
                moveIteration, railPos, railState, railShape);

        // Update remaining movement calculation
        moveIteration.remainingMovement = moveIteration.initial
                ? newVelocity.horizontalLength()
                : moveIteration.remainingMovement + (newVelocity.horizontalLength() - currentVelocity.horizontalLength());

        this.setVelocity(newVelocity);

        moveIteration.remainingMovement = ((AbstractMinecartEntityInvoker) this.minecart)
                .invokeMoveAlongTrack(railPos, railShape, moveIteration.remainingMovement);
    }

    /**
     * Handles movement when minecart is off rails
     */
    @Unique
    private void handleOffRailMovement(ServerWorld world, MoveIteration moveIteration) {
        ((AbstractMinecartEntityInvoker) this.minecart).invokeMoveOffRail(world);
        moveIteration.remainingMovement = 0.0;
    }

    /**
     * Updates minecart rotation based on movement
     */
    @Unique
    private void updateMinecartRotation() {
        Vec3d positionChange = this.getPos().subtract(this.minecart.getLastRenderPos());
        double distance = positionChange.length();

        if (distance > MINIMUM_VELOCITY_THRESHOLD) {
            if (positionChange.horizontalLengthSquared() > MINIMUM_VELOCITY_THRESHOLD) {
                // Calculate yaw and pitch based on movement direction
                float yaw = 180.0f - (float)(Math.atan2(positionChange.z, positionChange.x) * 180.0 / Math.PI);
                yaw += this.minecart.isYawFlipped() ? 180.0f : 0.0f;

                float pitch = this.minecart.isOnGround() && !this.minecart.isOnRail() ? 0.0f
                        : 90.0f - (float)(Math.atan2(positionChange.horizontalLength(), positionChange.y) * 180.0 / Math.PI);
                pitch *= this.minecart.isYawFlipped() ? -1.0f : 1.0f;

                this.setAngles(yaw, pitch);
            } else if (!this.minecart.isOnRail()) {
                // Smooth pitch interpolation when not moving horizontally
                float targetPitch = this.minecart.isOnGround() ? 0.0f
                        : MathHelper.lerpAngleDegrees(0.2f, this.getPitch(), 0.0f);
                this.setPitch(targetPitch);
            }
        }
    }

    /**
     * Calculates new horizontal velocity with various modifiers applied
     */
    @Unique
    private Vec3d calcNewHorizontalVelocity(ServerWorld world, Vec3d horizontalVelocity, MoveIteration iteration, BlockPos pos,
                                            BlockState railState, RailShape railShape) {
        Vec3d velocity = horizontalVelocity;

        // Apply slope velocity changes
        if (!iteration.slopeVelocityApplied) {
            Vec3d slopeAdjusted = this.applySlopeVelocity(velocity, railShape);
            if (slopeAdjusted.horizontalLengthSquared() != velocity.horizontalLengthSquared()) {
                iteration.slopeVelocityApplied = true;
                velocity = slopeAdjusted;
            }
        }

        // Apply initial player input velocity
        if (iteration.initial) {
            Vec3d inputAdjusted = this.applyInitialVelocity(velocity);
            if (inputAdjusted.horizontalLengthSquared() != velocity.horizontalLengthSquared()) {
                iteration.decelerated = true;
                velocity = inputAdjusted;
            }
        }

        // Apply powered rail deceleration
        if (!iteration.decelerated) {
            Vec3d decelerated = this.decelerateFromPoweredRail(velocity, railState);
            if (decelerated.horizontalLengthSquared() != velocity.horizontalLengthSquared()) {
                iteration.decelerated = true;
                velocity = decelerated;
            }
        }

        // Apply general slowdown and speed limiting
        if (iteration.initial && velocity.lengthSquared() > 0.0) {
            velocity = ((AbstractMinecartEntityInvoker) this.minecart).invokeApplySlowdown(velocity);
            double maxSpeed = ((AbstractMinecartEntityInvoker) this.minecart).invokeGetMaxSpeed(world);
            double currentSpeed = Math.min(velocity.length(), maxSpeed);
            velocity = velocity.normalize().multiply(currentSpeed);
        }

        // Apply powered rail acceleration
        if (!iteration.accelerated) {
            Vec3d accelerated = this.accelerateFromPoweredRail(velocity, pos, railState);
            if (accelerated.horizontalLengthSquared() != velocity.horizontalLengthSquared()) {
                iteration.accelerated = true;
                velocity = accelerated;
            }
        }

        return velocity;
    }

    /**
     * Applies velocity changes due to slope (ascending/descending rails)
     */
    @Unique
    private Vec3d applySlopeVelocity(Vec3d horizontalVelocity, RailShape railShape) {
        double deceleration = Math.max(SLOPE_DECELERATION_BASE,
                horizontalVelocity.horizontalLength() * SLOPE_DECELERATION_MULTIPLIER);

        // Reduce deceleration in water
        if (this.minecart.isTouchingWater()) {
            deceleration *= WATER_DECELERATION_FACTOR;
        }

        return switch (railShape) {
            case ASCENDING_EAST -> horizontalVelocity.add(-deceleration, 0.0, 0.0);
            case ASCENDING_WEST -> horizontalVelocity.add(deceleration, 0.0, 0.0);
            case ASCENDING_NORTH -> horizontalVelocity.add(0.0, 0.0, deceleration);
            case ASCENDING_SOUTH -> horizontalVelocity.add(0.0, 0.0, -deceleration);
            default -> horizontalVelocity;
        };
    }

    /**
     * Applies initial velocity from player input
     */
    @Unique
    private Vec3d applyInitialVelocity(Vec3d horizontalVelocity) {
        Entity passenger = this.minecart.getFirstPassenger();
        if (!(passenger instanceof ServerPlayerEntity serverPlayer)) {
            return horizontalVelocity;
        }

        Vec3d inputVelocity = serverPlayer.getInputVelocityForMinecart();
        if (inputVelocity.lengthSquared() > 0.0) {
            Vec3d normalizedInput = inputVelocity.normalize();
            double currentSpeed = horizontalVelocity.horizontalLengthSquared();

            // Apply small initial push if minecart is nearly stationary
            if (normalizedInput.lengthSquared() > 0.0 && currentSpeed < 0.01) {
                Vec3d horizontalInput = new Vec3d(normalizedInput.x, 0.0, normalizedInput.z);
                return horizontalVelocity.add(horizontalInput.normalize().multiply(0.001));
            }
        }

        return horizontalVelocity;
    }

    /**
     * Handles deceleration from unpowered powered rails
     */
    @Unique
    private Vec3d decelerateFromPoweredRail(Vec3d velocity, BlockState railState) {
        if (!railState.isOf(Blocks.POWERED_RAIL) || railState.get(PoweredRailBlock.POWERED)) {
            return velocity;
        }

        // Stop completely if moving very slowly
        if (velocity.length() < 0.03) {
            return Vec3d.ZERO;
        }

        // Apply 50% speed reduction
        return velocity.multiply(0.5);
    }

    /**
     * Handles acceleration from powered rails with improved logic for tight spaces
     */
    @Unique
    private Vec3d accelerateFromPoweredRail(Vec3d velocity, BlockPos railPos, BlockState railState) {
        if (!railState.isOf(Blocks.POWERED_RAIL) || !railState.get(PoweredRailBlock.POWERED)) {
            return velocity;
        }
        if (velocity.length() > 0.01) {
            return velocity.normalize().multiply(velocity.length() + 0.06);
        }
        Vec3d vec3d = this.minecart.getLaunchDirection(railPos);
        if (vec3d.lengthSquared() <= 0.0) {
            return velocity;
        }
        return vec3d.multiply(velocity.length() + 0.2);
    }

    /**
     * Adjusts minecart position to align with rail
     * Implementation of DefaultMinecartControllerInterface method
     */
    @Unique
    public void zent_mod$adjustToRail(BlockPos pos, BlockState blockState, boolean ignoreWeight) {
        if (!AbstractRailBlock.isRail(blockState)) {
            return;
        }

        RailShape railShape = blockState.get(((AbstractRailBlock) blockState.getBlock()).getShapeProperty());
        Pair<Vec3i, Vec3i> railEndPoints = AbstractMinecartEntity.getAdjacentRailPositionsByShape(railShape);

        Vec3d firstEnd = new Vec3d(railEndPoints.getFirst()).multiply(0.5);
        Vec3d secondEnd = new Vec3d(railEndPoints.getSecond()).multiply(0.5);
        Vec3d firstHorizontal = firstEnd.getHorizontal();
        Vec3d secondHorizontal = secondEnd.getHorizontal();

        // Determine which direction to face based on velocity
        if (this.getVelocity().length() > MINIMUM_VELOCITY_THRESHOLD &&
                this.getVelocity().dotProduct(firstHorizontal) < this.getVelocity().dotProduct(secondHorizontal) ||
                this.ascends(secondHorizontal, railShape)) {
            Vec3d temp = firstHorizontal;
            firstHorizontal = secondHorizontal;
            secondHorizontal = temp;
        }

        // Calculate yaw angle
        float yaw = 180.0f - (float)(Math.atan2(firstHorizontal.z, firstHorizontal.x) * 180.0 / Math.PI);
        yaw += this.minecart.isYawFlipped() ? 180.0f : 0.0f;

        Vec3d currentPos = this.getPos();
        Vec3d newPosition;

        // Handle curved vs straight rails
        boolean isCurved = firstEnd.getX() != secondEnd.getX() && firstEnd.getZ() != secondEnd.getZ();

        if (isCurved) {
            // Curved rail positioning
            Vec3d railDirection = secondEnd.subtract(firstEnd);
            Vec3d positionOffset = currentPos.subtract(pos.toBottomCenterPos()).subtract(firstEnd);
            Vec3d projectedOffset = railDirection.multiply(railDirection.dotProduct(positionOffset) / railDirection.dotProduct(railDirection));
            newPosition = pos.toBottomCenterPos().add(firstEnd).add(projectedOffset);

            yaw = 180.0f - (float)(Math.atan2(projectedOffset.z, projectedOffset.x) * 180.0 / Math.PI);
            yaw += this.minecart.isYawFlipped() ? 180.0f : 0.0f;
        } else {
            // Straight rail positioning
            boolean adjustX = firstEnd.subtract(secondEnd).x != 0.0;
            boolean adjustZ = firstEnd.subtract(secondEnd).z != 0.0;
            newPosition = new Vec3d(
                    adjustZ ? pos.toCenterPos().x : currentPos.x,
                    pos.getY(),
                    adjustX ? pos.toCenterPos().z : currentPos.z
            );
        }

        // Apply position change
        Vec3d positionDelta = newPosition.subtract(currentPos);
        this.setPos(currentPos.add(positionDelta));

        // Handle vertical rails
        float pitch = 0.0f;
        boolean isVertical = firstEnd.getY() != secondEnd.getY();

        if (isVertical) {
            Vec3d targetPos = pos.toBottomCenterPos().add(secondHorizontal);
            double distance = targetPos.distanceTo(this.getPos());
            this.setPos(this.getPos().add(0.0, distance + 0.1, 0.0));
            pitch = this.minecart.isYawFlipped() ? 45.0f : -45.0f;
        } else {
            this.setPos(this.getPos().add(0.0, 0.1, 0.0));
        }

        this.setAngles(yaw, pitch);
    }

    /**
     * Sets minecart angles with proper wrapping and flipping logic
     */
    @Unique
    private void setAngles(float yaw, float pitch) {
        double yawDifference = Math.abs(yaw - this.getYaw());

        // Handle 180-degree turns
        if (yawDifference >= 175.0 && yawDifference <= 185.0) {
            this.minecart.setYawFlipped(!this.minecart.isYawFlipped());
            yaw -= 180.0f;
            pitch *= -1.0f;
        }

        // Clamp pitch to reasonable values
        pitch = Math.clamp(pitch, -45.0f, 45.0f);

        this.setPitch(pitch % 360.0f);
        this.setYaw(yaw % 360.0f);
    }
    /**
     * Enhanced track movement with improved physics
     */
    @Inject(method = "moveAlongTrack", at = @At("HEAD"), cancellable = true)
    public void moveAlongTrack(BlockPos blockPos, RailShape railShape, double remainingMovement, CallbackInfoReturnable<Double> cir) {
        if (remainingMovement < MINIMUM_VELOCITY_THRESHOLD) {
            cir.setReturnValue(0.0);
            return;
        }

        Vec3d currentPos = this.getPos();
        Pair<Vec3i, Vec3i> railEndPoints = AbstractMinecartEntity.getAdjacentRailPositionsByShape(railShape);
        Vec3i firstEnd = railEndPoints.getFirst();
        Vec3i secondEnd = railEndPoints.getSecond();
        Vec3d horizontalVelocity = this.getVelocity().getHorizontal();

        // Stop if no horizontal velocity
        if (horizontalVelocity.length() < MINIMUM_VELOCITY_THRESHOLD) {
            this.setVelocity(Vec3d.ZERO);
            cir.setReturnValue(0.0);
            return;
        }

        // Determine movement direction
        boolean isVertical = firstEnd.getY() != secondEnd.getY();
        Vec3d targetEndHorizontal = new Vec3d(secondEnd).multiply(0.5).getHorizontal();
        Vec3d sourceEndHorizontal = new Vec3d(firstEnd).multiply(0.5).getHorizontal();

        if (horizontalVelocity.dotProduct(sourceEndHorizontal) < horizontalVelocity.dotProduct(targetEndHorizontal)) {
            sourceEndHorizontal = targetEndHorizontal;
        }

        // Calculate target position
        Vec3d targetPosition = blockPos.toBottomCenterPos()
                .add(sourceEndHorizontal)
                .add(0.0, 0.1, 0.0)
                .add(sourceEndHorizontal.normalize().multiply(MINIMUM_VELOCITY_THRESHOLD));

        if (isVertical && !this.ascends(horizontalVelocity, railShape)) {
            targetPosition = targetPosition.add(0.0, 1.0, 0.0);
        }

        // Calculate movement vector
        Vec3d movementDirection = targetPosition.subtract(this.getPos()).normalize();
        Vec3d adjustedVelocity = movementDirection.multiply(horizontalVelocity.length() / movementDirection.horizontalLength());
        double movementMultiplier = isVertical ? MathHelper.SQUARE_ROOT_OF_TWO : 1.0f;
        Vec3d newPosition = currentPos.add(adjustedVelocity.normalize().multiply(remainingMovement * movementMultiplier));

        // Check if we've reached the target
        if (currentPos.squaredDistanceTo(targetPosition) <= currentPos.squaredDistanceTo(newPosition)) {
            remainingMovement = targetPosition.subtract(newPosition).horizontalLength();
            newPosition = targetPosition;
        } else {
            remainingMovement = 0.0;
        }

        // Move the minecart
        this.minecart.move(MovementType.SELF, newPosition.subtract(currentPos));

        // Handle vertical rail transitions
        if (isVertical) {
            BlockState newRailState = this.getWorld().getBlockState(BlockPos.ofFloored(newPosition));
            if (AbstractRailBlock.isRail(newRailState)) {
                RailShape newRailShape = newRailState.get(((AbstractRailBlock) newRailState.getBlock()).getShapeProperty());
                if (this.restOnVShapedTrack(railShape, newRailShape)) {
                    cir.setReturnValue(0.0);
                    return;
                }
            }

            // Adjust vertical position for slopes
            double horizontalDistance = targetPosition.getHorizontal().distanceTo(this.getPos().getHorizontal());
            double targetY = targetPosition.y + (this.ascends(adjustedVelocity, railShape) ? horizontalDistance : -horizontalDistance);

            if (this.getPos().y < targetY) {
                this.setPos(this.getPos().x, targetY, this.getPos().z);
            }
        }

        // Handle stuck minecart
        if (this.getPos().distanceTo(currentPos) < MINIMUM_VELOCITY_THRESHOLD &&
                newPosition.distanceTo(currentPos) > MINIMUM_VELOCITY_THRESHOLD) {
            this.setVelocity(Vec3d.ZERO);
            cir.setReturnValue(0.0);
            return;
        }

        this.setVelocity(adjustedVelocity);
        cir.setReturnValue(remainingMovement);
    }

    /**
     * Checks if minecart should rest on V-shaped track transition
     */
    @Unique
    private boolean restOnVShapedTrack(RailShape currentRailShape, RailShape newRailShape) {
        boolean isMovingSlow = this.getVelocity().lengthSquared() < 0.005;
        boolean newRailIsAscending = newRailShape.isAscending();
        boolean currentlyAscending = this.ascends(this.getVelocity(), currentRailShape);
        boolean wouldAscendOnNewRail = this.ascends(this.getVelocity(), newRailShape);

        if (isMovingSlow && newRailIsAscending && currentlyAscending && !wouldAscendOnNewRail) {
            this.setVelocity(Vec3d.ZERO);
            return true;
        }

        return false;
    }

    /**
     * Determines if velocity is ascending for given rail shape
     */
    @Unique
    private boolean ascends(Vec3d velocity, RailShape railShape) {
        return switch (railShape) {
            case ASCENDING_EAST -> velocity.x < 0.0;
            case ASCENDING_WEST -> velocity.x > 0.0;
            case ASCENDING_NORTH -> velocity.z > 0.0;
            case ASCENDING_SOUTH -> velocity.z < 0.0;
            default -> false;
        };
    }

    /**
     * Sets enhanced maximum speed
     */
    @Inject(method = "getMaxSpeed", at = @At("HEAD"), cancellable = true)
    public void getMaxSpeed(ServerWorld world, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(ENHANCED_MAX_SPEED);
    }

    /**
     * Configures speed retention
     */
    @Inject(method = "getSpeedRetention", at = @At("HEAD"), cancellable = true)
    public void getSpeedRetention(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(SPEED_RETENTION);
    }

    /**
     * Enhanced collision handling for entities
     * Entities touching the minecart at high speed will receive knockback and damage
     */
    @Inject(method = "handleCollision", at = @At("HEAD"), cancellable = true)
    public void handleCollision(CallbackInfoReturnable<Boolean> cir) {
        Box boundingBox = this.minecart.getBoundingBox().expand(0.2, 0.0, 0.2);

        List<Entity> list = this.minecart.getWorld().getOtherEntities(this.minecart, boundingBox);

        Vec3d cartVelocity = this.minecart.getVelocity();

        double speed = cartVelocity.length();

        if (speed >= 1.0) {
            float damage = (float)Math.min(10.0, speed * 8.0);

            for (Entity entity : list) {
                if (entity instanceof ItemEntity || entity == minecart.getFirstPassenger()) {
                    continue;
                }

                entity.damage((ServerWorld) getWorld(), this.minecart.getDamageSources().flyIntoWall(), damage);

                Vec3d knockbackDir = cartVelocity.normalize().multiply(1);

                entity.addVelocity(knockbackDir.x, 0.3, knockbackDir.z);
                entity.velocityModified = true;
            }
        } else if (this.minecart.horizontalCollision || this.minecart.verticalCollision) {
            boolean pickedUpEntities = this.pickUpEntities(boundingBox);
            boolean pushedAwayFromEntities = this.pushAwayFromEntities(boundingBox);

            cir.setReturnValue(pickedUpEntities && !pushedAwayFromEntities);
            return;
        }

        cir.setReturnValue(false);
    }


    /**
     * Attempts to pick up nearby entities as passengers
     */
    @Unique
    public boolean pickUpEntities(Box boundingBox) {
        if (!this.minecart.isRideable() || this.minecart.hasPassengers()) {
            return false;
        }

        List<Entity> nearbyEntities = this.getWorld().getOtherEntities(
                this.minecart,
                boundingBox,
                EntityPredicates.canBePushedBy(this.minecart)
        );

        if (nearbyEntities.isEmpty()) {
            return false;
        }

        for (Entity entity : nearbyEntities) {
            // Skip certain entity types and entities that are already riding something
            if (entity instanceof PlayerEntity ||
                    entity instanceof IronGolemEntity ||
                    entity instanceof AbstractMinecartEntity ||
                    this.minecart.hasPassengers() ||
                    entity.hasVehicle()) {
                continue;
            }

            if (entity.startRiding(this.minecart)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Pushes away entities that collide with the minecart
     */
    @Unique
    public boolean pushAwayFromEntities(Box boundingBox) {
        boolean pushedAnyEntity = false;

        if (this.minecart.isRideable()) {
            List<Entity> collidingEntities = this.getWorld().getOtherEntities(
                    this.minecart,
                    boundingBox,
                    EntityPredicates.canBePushedBy(this.minecart)
            );

            if (!collidingEntities.isEmpty()) {
                for (Entity entity : collidingEntities) {
                    // Push away specific entity types
                    if (entity instanceof PlayerEntity ||
                            entity instanceof IronGolemEntity ||
                            entity instanceof AbstractMinecartEntity ||
                            !this.minecart.hasPassengers() ||
                            !entity.hasVehicle()) {
                        entity.pushAwayFrom(this.minecart);
                        pushedAnyEntity = true;
                    }
                }
            }
        } else {
            List<Entity> allCollidingEntities = this.getWorld().getOtherEntities(this.minecart, boundingBox);

            for (Entity entity : allCollidingEntities) {
                if (!this.minecart.hasPassenger(entity) &&
                        entity.isPushable() &&
                        entity instanceof AbstractMinecartEntity) {
                    entity.pushAwayFrom(this.minecart);
                    pushedAnyEntity = true;
                }
            }
        }

        return pushedAnyEntity;
    }
}