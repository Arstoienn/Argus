package io.github.arstoienn.argus.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.arstoienn.argus.SecondPass;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.ChunkRenderingDataPreparer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder.BuiltChunk;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the second pass a culling state of its own.
 *
 * The renderer keeps its occlusion graph, its visible-chunk lists and where the camera last was as
 * instance state, so a second camera walking them would wreck the main view's. For the length of
 * the pass all of that is swapped for a second copy, and swapped back afterwards.
 *
 * The chunk storage itself stays shared and centred on the main camera. Invalidations are mirrored
 * into the second graph as they happen, and it is rebuilt in full every so often to catch the ones
 * that are not, such as a chunk unloading.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Unique
    private static final long REBUILD_INTERVAL_MS = 500L;

    @Shadow
    @Final
    private MinecraftClient client;
    @Shadow
    private BuiltChunkStorage chunks;
    @Shadow
    private int viewDistance;
    @Shadow
    @Final
    @Mutable
    private ChunkRenderingDataPreparer chunkRenderingDataPreparer;
    @Shadow
    @Final
    @Mutable
    private ObjectArrayList<BuiltChunk> builtChunks;
    @Shadow
    @Final
    @Mutable
    private ObjectArrayList<BuiltChunk> nearbyChunks;
    @Shadow
    private int cameraChunkX;
    @Shadow
    private int cameraChunkY;
    @Shadow
    private int cameraChunkZ;
    @Shadow
    private double lastCameraX;
    @Shadow
    private double lastCameraY;
    @Shadow
    private double lastCameraZ;
    @Shadow
    private double lastCameraPitch;
    @Shadow
    private double lastCameraYaw;

    // The second view's copy of each field above; swapped with the live ones around the pass.
    @Unique
    private ChunkRenderingDataPreparer argus$preparer;
    @Unique
    private ObjectArrayList<BuiltChunk> argus$builtChunks;
    @Unique
    private ObjectArrayList<BuiltChunk> argus$nearbyChunks;
    @Unique
    private int argus$cameraChunkX;
    @Unique
    private int argus$cameraChunkY;
    @Unique
    private int argus$cameraChunkZ;
    @Unique
    private double argus$lastCameraX;
    @Unique
    private double argus$lastCameraY;
    @Unique
    private double argus$lastCameraZ;
    @Unique
    private double argus$lastCameraPitch;
    @Unique
    private double argus$lastCameraYaw;

    /** The storage the second graph is attached to; null after leaving a world, until the next pass. */
    @Unique
    private BuiltChunkStorage argus$storage;
    @Unique
    private long argus$rebuiltAt;
    @Unique
    private boolean argus$swapped;

    @Inject(method = "render", at = @At("HEAD"))
    private void argus$enterSecondPass(CallbackInfo ci) {
        // A pass that threw never reached RETURN; put the main state back first.
        if (this.argus$swapped) {
            this.argus$swap();
        }
        // While the view distance is changing the storage is about to be rebuilt; leave it alone.
        if (!SecondPass.active || this.chunks == null || this.client.options.getClampedViewDistance() != this.viewDistance) {
            return;
        }

        if (this.argus$preparer == null) {
            this.argus$preparer = new ChunkRenderingDataPreparer();
            this.argus$builtChunks = new ObjectArrayList<>();
            this.argus$nearbyChunks = new ObjectArrayList<>();
        }
        if (this.argus$storage != this.chunks) {
            this.argus$preparer.setStorage(this.chunks);
            this.argus$storage = this.chunks;
            this.argus$builtChunks.clear();
            this.argus$nearbyChunks.clear();
            // Nothing matches this, so the first pass on new storage updates everything.
            this.argus$lastCameraX = this.argus$lastCameraY = this.argus$lastCameraZ = Double.MIN_VALUE;
            this.argus$lastCameraPitch = this.argus$lastCameraYaw = Double.MIN_VALUE;
        }
        long now = System.currentTimeMillis();
        if (now - this.argus$rebuiltAt > REBUILD_INTERVAL_MS) {
            this.argus$preparer.scheduleTerrainUpdate();
            this.argus$rebuiltAt = now;
        }

        this.argus$swap();
        Vec3d position = this.client.gameRenderer.getCamera().getCameraPos();
        this.cameraChunkX = ChunkSectionPos.getSectionCoord(position.getX());
        this.cameraChunkY = ChunkSectionPos.getSectionCoord(position.getY());
        this.cameraChunkZ = ChunkSectionPos.getSectionCoord(position.getZ());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void argus$exitSecondPass(CallbackInfo ci) {
        if (this.argus$swapped) {
            this.argus$swap();
        }
    }

    @Unique
    private void argus$swap() {
        this.argus$swapped = !this.argus$swapped;

        ChunkRenderingDataPreparer preparer = this.chunkRenderingDataPreparer;
        this.chunkRenderingDataPreparer = this.argus$preparer;
        this.argus$preparer = preparer;
        ObjectArrayList<BuiltChunk> built = this.builtChunks;
        this.builtChunks = this.argus$builtChunks;
        this.argus$builtChunks = built;
        ObjectArrayList<BuiltChunk> nearby = this.nearbyChunks;
        this.nearbyChunks = this.argus$nearbyChunks;
        this.argus$nearbyChunks = nearby;

        int chunkX = this.cameraChunkX;
        this.cameraChunkX = this.argus$cameraChunkX;
        this.argus$cameraChunkX = chunkX;
        int chunkY = this.cameraChunkY;
        this.cameraChunkY = this.argus$cameraChunkY;
        this.argus$cameraChunkY = chunkY;
        int chunkZ = this.cameraChunkZ;
        this.cameraChunkZ = this.argus$cameraChunkZ;
        this.argus$cameraChunkZ = chunkZ;

        double x = this.lastCameraX;
        this.lastCameraX = this.argus$lastCameraX;
        this.argus$lastCameraX = x;
        double y = this.lastCameraY;
        this.lastCameraY = this.argus$lastCameraY;
        this.argus$lastCameraY = y;
        double z = this.lastCameraZ;
        this.lastCameraZ = this.argus$lastCameraZ;
        this.argus$lastCameraZ = z;
        double pitch = this.lastCameraPitch;
        this.lastCameraPitch = this.argus$lastCameraPitch;
        this.argus$lastCameraPitch = pitch;
        double yaw = this.lastCameraYaw;
        this.lastCameraYaw = this.argus$lastCameraYaw;
        this.argus$lastCameraYaw = yaw;
    }

    // Keep the second graph's invalidations in step with the main one's. Only while it is attached:
    // the next world's chunks start arriving before the second pass reattaches it, and mirroring
    // into a detached graph throws inside packet handling, which disconnects you.

    @Inject(method = "scheduleTerrainUpdate", at = @At("RETURN"))
    private void argus$mirrorTerrainUpdate(CallbackInfo ci) {
        if (this.argus$storage != null) {
            this.argus$preparer.scheduleTerrainUpdate();
        }
    }

    @Inject(method = "addBuiltChunk", at = @At("RETURN"))
    private void argus$mirrorBuiltChunk(BuiltChunk chunk, CallbackInfo ci) {
        if (this.argus$storage != null) {
            this.argus$preparer.schedulePropagationFrom(chunk);
        }
    }

    @Inject(method = "scheduleNeighborUpdates", at = @At("RETURN"))
    private void argus$mirrorNeighborUpdates(ChunkPos pos, CallbackInfo ci) {
        if (this.argus$storage != null) {
            this.argus$preparer.addNeighbors(pos);
        }
    }

    @Inject(method = "setWorld", at = @At("RETURN"))
    private void argus$detachOnLeave(ClientWorld world, CallbackInfo ci) {
        if (world == null && this.argus$preparer != null) {
            this.argus$preparer.setStorage(null);
            this.argus$storage = null;
            this.argus$builtChunks.clear();
            this.argus$nearbyChunks.clear();
        }
    }

    /** Translucent geometry keeps the main camera's sort order; re-sorting for the second would break it. */
    @Inject(method = "translucencySort", at = @At("HEAD"), cancellable = true)
    private void argus$noResortInSecondPass(CallbackInfo ci) {
        if (SecondPass.active) {
            ci.cancel();
        }
    }

    /** Glowing outlines use a single framebuffer shared with the main view. */
    @Inject(method = "canDrawEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void argus$noOutlinesInSecondPass(CallbackInfoReturnable<Boolean> cir) {
        if (SecondPass.active) {
            cir.setReturnValue(false);
        }
    }

    /**
     * The game never draws your own player while the camera is on it. The second view wants to see
     * you, except in your own first person, where vanilla's rule is right.
     */
    @ModifyExpressionValue(method = "fillEntityRenderStates", at = @At("MIXINEXTRAS:EXPRESSION"))
    @Definition(id = "LocalPlayer", type = ClientPlayerEntity.class)
    @Expression("? instanceof LocalPlayer")
    private boolean argus$drawYourselfInSecondPass(boolean isLocalPlayer) {
        return SecondPass.active && !SecondPass.selfFirstPerson ? false : isLocalPlayer;
    }
}
