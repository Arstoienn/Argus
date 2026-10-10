package io.github.arstoienn.argus;

import io.github.arstoienn.argus.mixin.PersistentProjectileAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;

/**
 * What the second view is pointed at when it is not on you: another player, or one of your own
 * projectiles while it is in the air. A projectile takes priority over a player.
 *
 * The follow key cycles off, then the nearest player, then locked onto that player, then on to
 * each next player in turn.
 */
public final class Follow {
    private enum Mode { OFF, NEAREST, LOCKED }

    /** How long "closest player" stays in the status text after switching to nearest. */
    private static final long NEAREST_LABEL_NS = 2_500_000_000L;
    // Nearest mode only moves to a new player once they are clearly closer (0.8 of the distance,
    // compared squared) and not more often than this, so two players at similar range do not
    // make the camera flick back and forth.
    private static final double NEAREST_SWITCH_RATIO = 0.64;
    private static final long NEAREST_SWITCH_NS = 1_500_000_000L;

    private static Mode mode = Mode.OFF;
    private static UUID locked;
    private static UUID nearest;
    private static long nearestModeSince;
    private static long nearestSwitchedAt;

    private static long projectileScannedAt = Long.MIN_VALUE;
    private static Entity projectile;

    private Follow() {
    }

    static void pressFollow(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        switch (mode) {
            case OFF -> {
                mode = Mode.NEAREST;
                nearestModeSince = System.nanoTime();
                nearest = null;
                PlayerEntity target = nearestPlayer(mc);
                ArgusClient.actionBar(target != null
                    ? "Following nearest player — " + target.getName().getString()
                    : "Following nearest player");
            }
            case NEAREST -> {
                PlayerEntity target = nearestPlayer(mc);
                if (target != null) {
                    mode = Mode.LOCKED;
                    locked = target.getUuid();
                    ArgusClient.actionBar("Locked on " + target.getName().getString());
                } else {
                    mode = Mode.OFF;
                    ArgusClient.actionBar("Follow off");
                }
            }
            case LOCKED -> lockNext(mc);
        }
    }

    static void stop() {
        boolean wasFollowing = mode != Mode.OFF;
        mode = Mode.OFF;
        locked = null;
        nearest = null;
        if (wasFollowing) {
            ArgusClient.actionBar("Follow off");
        }
    }

    static void togglePerspective() {
        ArgusConfig config = ArgusConfig.get();
        config.followFirstPerson = !config.followFirstPerson;
        config.config().save();
        ArgusClient.actionBar("Watching in " + (config.followFirstPerson ? "1st" : "3rd") + " person");
    }

    /** The player being followed, or null when following is off or the player is out of reach. */
    static PlayerEntity player(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            return null;
        }
        return switch (mode) {
            case OFF -> null;
            case NEAREST -> nearestPlayer(mc);
            case LOCKED -> find(mc, locked);
        };
    }

    static boolean lockedTargetLost(MinecraftClient mc) {
        return mode == Mode.LOCKED && find(mc, locked) == null;
    }

    static boolean showNearestLabel() {
        return mode == Mode.NEAREST && System.nanoTime() - nearestModeSince < NEAREST_LABEL_NS;
    }

    /**
     * Your nearest arrow, trident or ender pearl still in flight, or null. Finding one walks every
     * entity in the world, so that happens once a game tick, and not at all with the option off.
     */
    static Entity projectile(MinecraftClient mc) {
        if (!ArgusConfig.get().followProjectiles || mc.world == null || mc.player == null) {
            projectile = null;
            projectileScannedAt = Long.MIN_VALUE;
            return null;
        }
        long time = mc.world.getTime();
        if (time != projectileScannedAt) {
            projectileScannedAt = time;
            projectile = scanProjectiles(mc);
        } else if (projectile != null && !inFlight(projectile)) {
            projectile = null;
        }
        return projectile;
    }

    private static Entity scanProjectiles(MinecraftClient mc) {
        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.world.getEntities()) {
            boolean tracked = entity instanceof PersistentProjectileEntity || entity instanceof EnderPearlEntity;
            if (tracked && inFlight(entity) && ((ProjectileEntity) entity).getOwner() == mc.player) {
                double distance = entity.getEntityPos().squaredDistanceTo(mc.player.getEntityPos());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = entity;
                }
            }
        }
        return closest;
    }

    private static boolean inFlight(Entity entity) {
        return !entity.isRemoved()
            && !(entity instanceof PersistentProjectileEntity arrow && ((PersistentProjectileAccessor) arrow).argus$isInGround());
    }

    private static PlayerEntity nearestPlayer(MinecraftClient mc) {
        PlayerEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                continue;
            }
            double distance = distanceTo(mc, player);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = player;
            }
        }
        if (closest == null) {
            nearest = null;
            return null;
        }

        PlayerEntity current = find(mc, nearest);
        long now = System.nanoTime();
        if (current == null
                || closest != current
                    && distanceTo(mc, closest) < distanceTo(mc, current) * NEAREST_SWITCH_RATIO
                    && now - nearestSwitchedAt > NEAREST_SWITCH_NS) {
            nearest = closest.getUuid();
            nearestSwitchedAt = now;
            return closest;
        }
        return current;
    }

    private static void lockNext(MinecraftClient mc) {
        List<PlayerEntity> others = new ArrayList<>(mc.world.getPlayers());
        others.remove(mc.player);
        if (others.isEmpty()) {
            stop();
            return;
        }
        int current = -1;
        for (int i = 0; i < others.size(); i++) {
            if (others.get(i).getUuid().equals(locked)) {
                current = i;
                break;
            }
        }
        PlayerEntity next = others.get((current + 1) % others.size());
        locked = next.getUuid();
        ArgusClient.actionBar("Following " + next.getName().getString());
    }

    private static PlayerEntity find(MinecraftClient mc, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player != mc.player && player.getUuid().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    private static double distanceTo(MinecraftClient mc, PlayerEntity player) {
        return player.getEntityPos().squaredDistanceTo(mc.player.getEntityPos());
    }
}
