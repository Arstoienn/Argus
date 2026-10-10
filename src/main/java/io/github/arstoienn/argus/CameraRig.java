package io.github.arstoienn.argus;

import io.github.arstoienn.argus.mixin.CameraAccessor;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Places the second camera: at your eyes, over a player's shoulder, or behind a projectile.
 *
 * The over-the-shoulder and projectile cameras are damped towards where they should be, and the
 * damping runs before the wall clipping, so a camera gliding into place never passes through a block.
 */
final class CameraRig {
    // Time constants at 100% smoothing, in seconds. An arrow moves about 60 blocks a second and the
    // camera trails by speed times tau, so projectiles get much shorter ones.
    private static final double POSITION_TAU = 0.28;
    private static final double ROTATION_TAU = 0.18;
    private static final double PROJECTILE_POSITION_TAU = 0.06;
    private static final double PROJECTILE_ROTATION_TAU = 0.04;
    /** A longer gap than this since the last frame (a pause, a hidden window) snaps instead of gliding. */
    private static final double MAX_STEP_SECONDS = 0.5;
    /** A longer move than this in one step (a teleport, a dimension change, a far target) snaps, in blocks. */
    private static final double SNAP_DISTANCE = 32.0;
    private static final double CLIP_MARGIN = 0.1;

    private static boolean smoothed;
    private static long smoothedAt;
    private static double x;
    private static double y;
    private static double z;
    private static float yaw;
    private static float pitch;
    private static Vec3d lastProjectileDirection = new Vec3d(0.0, 0.0, 1.0);

    private CameraRig() {
    }

    /**
     * Puts the camera at the target's eyes, facing the given direction.
     *
     * The direction is the one the main view's camera used this frame, not the target's head: mods
     * with a free-look or decoupled camera turn the two apart, and the second window should look
     * where the main window looks. The position is set here rather than left to updateCamera, which
     * camera mods hook to add their own offsets and smoothing. There is nothing to damp or clip.
     */
    static void atEyes(Camera camera, Entity target, float tickProgress, float lookYaw, float lookPitch) {
        Vec3d eyes = target.getCameraPosVec(tickProgress);
        ((CameraAccessor) camera).argus$setPos(eyes.x, eyes.y, eyes.z);
        ((CameraAccessor) camera).argus$setRotation(lookYaw, lookPitch);
    }

    static void overShoulder(Camera camera, Entity target, float tickProgress) {
        Vec3d anchor = standingEyes(target, tickProgress);
        smooth(anchor, target.getYaw(tickProgress), target.getPitch(tickProgress), false);
        anchor = new Vec3d(x, y, z);

        // The shoulder offset goes sideways along the horizontal only and up along world up, so
        // looking up or down does not swing the camera around and the shoulder stays on one side.
        // It is clipped too: unclipped against a wall it would start the next ray inside a block.
        double side = ArgusConfig.get().shoulderSide;
        double up = ArgusConfig.get().shoulderUp;
        double yawRad = Math.toRadians(yaw);
        Vec3d offset = new Vec3d(-Math.cos(yawRad) * side, up, -Math.sin(yawRad) * side);
        double length = offset.length();
        if (length > 1.0E-4) {
            Vec3d direction = offset.multiply(1.0 / length);
            anchor = anchor.add(direction.multiply(clip(target, anchor, direction, length)));
        }

        placeBehind(camera, target, anchor);
    }

    static void behindProjectile(Camera camera, Entity projectile, float tickProgress) {
        Vec3d velocity = projectile.getVelocity();
        if (velocity.lengthSquared() > 1.0E-4) {
            lastProjectileDirection = velocity.normalize();
        }
        Vec3d direction = lastProjectileDirection;
        float flightYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float flightPitch = (float) Math.toDegrees(-Math.asin(direction.y));

        smooth(projectile.getCameraPosVec(tickProgress), flightYaw, flightPitch, true);
        placeBehind(camera, projectile, new Vec3d(x, y, z));
    }

    /** Backs the camera away from the anchor along the damped view direction, stopping at walls. */
    private static void placeBehind(Camera camera, Entity target, Vec3d anchor) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        Vec3d back = new Vec3d(
            Math.sin(yawRad) * Math.cos(pitchRad),
            Math.sin(pitchRad),
            -Math.cos(yawRad) * Math.cos(pitchRad));
        double distance = clip(target, anchor, back, ArgusConfig.get().followDistance);
        Vec3d position = anchor.add(back.multiply(distance));
        ((CameraAccessor) camera).argus$setPos(position.x, position.y, position.z);
        ((CameraAccessor) camera).argus$setRotation(yaw, pitch);
    }

    /**
     * The target's eye position as if it were standing. A crouching, crawling or swimming player
     * would otherwise drag the camera down with them; a ceiling still stops it.
     */
    private static Vec3d standingEyes(Entity target, float tickProgress) {
        Vec3d eyes = target.getCameraPosVec(tickProgress);
        float standing = target.getEyeHeight(EntityPose.STANDING);
        float current = target.getStandingEyeHeight();
        if (standing <= current + 0.01F) {
            return eyes;
        }
        Vec3d raised = eyes.add(0.0, standing - current, 0.0);
        HitResult hit = raycast(target, eyes, raised);
        return hit.getType() == HitResult.Type.MISS ? raised : hit.getPos().add(0.0, -0.1, 0.0);
    }

    /**
     * How far the camera can go from {@code from} along {@code direction}, up to {@code wanted},
     * before a block gets in the way. Rays from the eight corners of a small box around the start
     * keep the near plane from cutting into a wall the camera is only just short of.
     */
    private static double clip(Entity target, Vec3d from, Vec3d direction, double wanted) {
        double allowed = wanted;
        for (int corner = 0; corner < 8; corner++) {
            Vec3d start = from.add(
                ((corner & 1) * 2 - 1) * CLIP_MARGIN,
                ((corner >> 1 & 1) * 2 - 1) * CLIP_MARGIN,
                ((corner >> 2 & 1) * 2 - 1) * CLIP_MARGIN);
            HitResult hit = raycast(target, start, start.add(direction.multiply(wanted)));
            if (hit.getType() != HitResult.Type.MISS) {
                allowed = Math.min(allowed, hit.getPos().distanceTo(from));
            }
        }
        return allowed;
    }

    private static HitResult raycast(Entity target, Vec3d from, Vec3d to) {
        return target.getEntityWorld().raycast(new RaycastContext(
            from, to, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, target));
    }

    /**
     * Moves the damped position and direction towards the wanted ones by 1 - e^(-dt/tau), which
     * traces the same path at any frame rate. That matters here because the second view skips
     * frames to stay inside its budget, and a fixed-ratio lerp would speed up and slow down with it.
     */
    private static void smooth(Vec3d wanted, float wantedYaw, float wantedPitch, boolean projectile) {
        long now = System.nanoTime();
        double amount = ArgusConfig.get().cameraSmoothing();
        double dt = (now - smoothedAt) / 1.0E9;
        smoothedAt = now;

        // Also snap across a teleport, a dimension change or a switch to a far-away target,
        // rather than sweeping across the world.
        if (amount <= 0.0 || !smoothed || dt <= 0.0 || dt > MAX_STEP_SECONDS
                || wanted.squaredDistanceTo(x, y, z) > SNAP_DISTANCE * SNAP_DISTANCE) {
            smoothed = true;
            x = wanted.x;
            y = wanted.y;
            z = wanted.z;
            yaw = MathHelper.wrapDegrees(wantedYaw);
            pitch = MathHelper.clamp(wantedPitch, -90.0F, 90.0F);
            return;
        }

        double move = 1.0 - Math.exp(-dt / (amount * (projectile ? PROJECTILE_POSITION_TAU : POSITION_TAU)));
        double turn = 1.0 - Math.exp(-dt / (amount * (projectile ? PROJECTILE_ROTATION_TAU : ROTATION_TAU)));
        x += (wanted.x - x) * move;
        y += (wanted.y - y) * move;
        z += (wanted.z - z) * move;
        yaw = MathHelper.wrapDegrees(yaw + (float) (MathHelper.wrapDegrees(wantedYaw - yaw) * turn));
        pitch = MathHelper.clamp(pitch + (float) ((wantedPitch - pitch) * turn), -90.0F, 90.0F);
    }
}
