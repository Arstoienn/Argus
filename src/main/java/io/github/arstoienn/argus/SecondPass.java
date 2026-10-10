package io.github.arstoienn.argus;

import io.github.arstoienn.argus.mixin.CameraAccessor;
import io.github.arstoienn.argus.mixin.GameOptionsAccessor;
import io.github.arstoienn.argus.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlobalSettings;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.TextureFilteringMode;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the world a second time, from the second camera, once the main frame is finished, and
 * presents the result in the second window.
 *
 * The game's own world renderer does the drawing. For the length of the pass the camera is moved
 * and {@code MinecraftClient.getFramebuffer} is redirected to a framebuffer Argus owns, so nothing
 * in the renderer needs to know it is running twice. Everything the pass changes is put back
 * afterwards, and the per-frame uniform buffers it would overwrite are swapped for its own
 * ({@link PassUniforms}).
 */
public final class SecondPass {
    /** True while the world is being drawn for the second view. */
    public static boolean active;
    /**
     * The second view is on you this pass, rather than on another player or a projectile. View bob,
     * hurt tilt and the FOV multiplier all come from your own player, so they apply only then.
     */
    public static boolean selfView;
    /**
     * The second view is your own first person this pass. Vanilla's rules then apply as they are:
     * your hands are drawn and your body is not.
     */
    public static boolean selfFirstPerson;

    /** The second view is on you and takes the main view's field of view as it stands; see {@link #sharedFov}. */
    private static boolean sharesMainFov;
    // The fields of view the main view was drawn with this frame, for the world and for the hands.
    private static float mainWorldFov = Float.NaN;
    private static float mainHandFov = Float.NaN;

    private SecondPass() {
    }

    /** Called at the end of every {@code GameRenderer.render}. */
    public static void afterFrame(GameRenderer renderer, RenderTickCounter ticks, boolean tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (tick) {
            if (mc.isFinishedLoading() && mc.world != null && mc.player != null && mc.getCameraEntity() != null) {
                renderAndPresent(mc, renderer, ticks);
            } else if (SecondWindow.exists() && !SecondWindow.isHidden()) {
                SecondWindow.setStatus(SecondWindow.hasHeldFrame() ? "no world\nlast frame" : "");
                SecondWindow.presentHeld();
            }
        }
        // An interface drawn this frame and not presented (the window was just hidden, the world
        // went away) must not turn up on the next one.
        SecondView.takeInterface();
        SecondView.takeHudDrawnAlone();
        // The next frame works its own out before its second pass; an old one must not stand in.
        mainWorldFov = Float.NaN;
        mainHandFov = Float.NaN;
    }

    /** Called with every field of view the game works out for the main view. */
    public static void mainFovWas(boolean world, float fov) {
        if (world) {
            mainWorldFov = fov;
        } else {
            mainHandFov = fov;
        }
    }

    /**
     * The main view's field of view, for the second pass to use in place of working one out, or NaN
     * if it should work out its own.
     *
     * On you the two views are meant to share it. Working it out a second time does not reliably
     * give the same answer: a zoom mod decides by the perspective, which the second pass has
     * changed, and eases its zoom over time. Zume zooms the field of view only in first person, so
     * one view would be zoomed and the other not, whichever perspective the main window was in. And
     * on VulkanMod, two views drawn with different fields of view have made the main window flicker.
     */
    public static float sharedFov(boolean world) {
        if (!active || !sharesMainFov) {
            return Float.NaN;
        }
        return world ? mainWorldFov : mainHandFov;
    }

    private static void renderAndPresent(MinecraftClient mc, GameRenderer renderer, RenderTickCounter ticks) {
        if (!SecondWindow.ensure()) {
            return;
        }
        SecondWindow.keepOpen();
        FrameBudget.frameStarted();
        if (!SecondWindow.isVisible()) {
            return;
        }

        Framebuffer main = mc.getFramebuffer();
        Framebuffer world = SecondView.world(main.textureWidth, main.textureHeight);
        Entity projectile = Follow.projectile(mc);
        PlayerEntity followed = projectile == null ? Follow.player(mc) : null;
        boolean paused = mc.isPaused();
        SecondWindow.setStatus(paused ? "PAUSED" : status(mc, projectile, followed));

        // A paused world stands still, so the last picture is still right. Skipping the pass does
        // not hide an inventory opened while paused: the interface is drawn every frame regardless.
        boolean due = FrameBudget.renderThisFrame();
        if (SecondView.worldEmpty() || due && !paused) {
            SecondView.worldDrawn();
            render(mc, renderer, ticks, world, projectile, followed);
        }

        // The interface framebuffer holds the latest world picture with this frame's interface over it.
        Framebuffer withInterface = SecondView.takeInterface();
        if (withInterface != null) {
            SecondWindow.present(withInterface, SecondView.cursor());
        } else {
            SecondWindow.present(world, null);
        }
    }

    private static void render(MinecraftClient mc, GameRenderer renderer, RenderTickCounter ticks,
                               Framebuffer target, Entity projectile, PlayerEntity followed) {
        long start = System.nanoTime();
        GameRendererAccessor rendererAccess = (GameRendererAccessor) renderer;
        GameOptionsAccessor options = (GameOptionsAccessor) mc.options;
        Camera camera = renderer.getCamera();
        float tickProgress = ticks.getTickProgress(true);

        // The main frame's camera, put back directly afterwards when the pass only moved and
        // turned it, rather than through another updateCamera call, which camera mods hook.
        Vec3d cameraPos = camera.getCameraPos();
        float cameraYaw = camera.getYaw();
        float cameraPitch = camera.getPitch();
        boolean cameraThirdPerson = camera.isThirdPerson();
        boolean cameraSetDirectly = false;

        Perspective perspective = mc.options.getPerspective();
        Entity cameraEntity = mc.getCameraEntity();
        HitResult crosshairTarget = mc.crosshairTarget;
        Entity targetedEntity = mc.targetedEntity;
        float fovMultiplier = rendererAccess.argus$fovMultiplier();
        float lastFovMultiplier = rendererAccess.argus$lastFovMultiplier();
        // With the main window in third person the second one shows your own first person.
        boolean complementary = ArgusConfig.get().mirrorPerspective && !perspective.isFirstPerson();

        try {
            active = true;
            SecondView.redirect = target;
            if (ArgusClient.VULKANMOD) {
                VulkanWindow.beginSecondPass();
            }
            GlobalSettings globalSettings = PassUniforms.swapIn(rendererAccess);
            selfView = projectile == null && followed == null;
            applyFov(mc, rendererAccess, perspective, complementary);

            // updateCamera still runs for everything else a camera carries (the entity it is on,
            // third person, what it is submerged in); the rig then overwrites position and rotation.
            if (projectile != null) {
                options.argus$setPerspective(Perspective.THIRD_PERSON_BACK);
                renderer.updateCamera(ticks);
                CameraRig.behindProjectile(camera, projectile, tickProgress);
            } else if (followed != null && !ArgusConfig.get().followThirdPerson) {
                mc.setCameraEntity(followed);
                options.argus$setPerspective(Perspective.FIRST_PERSON);
                renderer.updateCamera(ticks);
            } else if (followed != null) {
                options.argus$setPerspective(Perspective.THIRD_PERSON_BACK);
                renderer.updateCamera(ticks);
                CameraRig.overShoulder(camera, followed, tickProgress);
            } else if (complementary) {
                // Set entirely by hand, so camera mods hooked on updateCamera are not run again.
                cameraSetDirectly = true;
                selfFirstPerson = true;
                options.argus$setPerspective(Perspective.FIRST_PERSON);
                ((CameraAccessor) camera).argus$setThirdPerson(false);
                CameraRig.atEyes(camera, mc.player, tickProgress, cameraYaw, cameraPitch);
            } else {
                options.argus$setPerspective(Perspective.THIRD_PERSON_BACK);
                renderer.updateCamera(ticks);
                CameraRig.overShoulder(camera, mc.player, tickProgress);
            }

            globalSettings.set(
                mc.getWindow().getFramebufferWidth(),
                mc.getWindow().getFramebufferHeight(),
                mc.options.getGlintStrength().getValue(),
                mc.world.getTime(),
                ticks,
                mc.options.getMenuBackgroundBlurrinessValue(),
                camera,
                mc.options.getTextureFiltering().getValue() == TextureFilteringMode.RGSS);
            renderer.renderWorld(ticks);
            renderer.getEntityRenderCommandQueue().onNextFrame();
            rendererAccess.argus$entityRenderDispatcher().endLayeredCustoms();
        } finally {
            active = false;
            selfView = false;
            selfFirstPerson = false;
            sharesMainFov = false;
            PassUniforms.swapOut(rendererAccess);
            if (ArgusClient.VULKANMOD) {
                VulkanWindow.endSecondPass();
            }
            SecondView.redirect = null;
            rendererAccess.argus$setFovMultiplier(fovMultiplier);
            rendererAccess.argus$setLastFovMultiplier(lastFovMultiplier);
            mc.setCameraEntity(cameraEntity);
            options.argus$setPerspective(perspective);
            mc.crosshairTarget = crosshairTarget;
            mc.targetedEntity = targetedEntity;
            if (cameraSetDirectly) {
                CameraAccessor access = (CameraAccessor) camera;
                access.argus$setThirdPerson(cameraThirdPerson);
                access.argus$setPos(cameraPos.x, cameraPos.y, cameraPos.z);
                access.argus$setRotation(cameraYaw, cameraPitch);
            } else {
                renderer.updateCamera(ticks);
            }
            FrameBudget.passFinished(System.nanoTime() - start);
        }
    }

    /**
     * On you, the field of view is the one the main view was drawn with this frame, whatever went
     * into it (sprinting, flying, speed, drawing a bow, a zoom mod), taken as it stands rather than
     * worked out again; see {@link #sharedFov}. On anything else the FOV multiplier is 1.
     *
     * The spyglass is the exception. The game only zooms for it in first person, and the second
     * view is often in the other perspective, so it gets the value its own perspective calls for.
     */
    private static void applyFov(MinecraftClient mc, GameRendererAccessor renderer, Perspective perspective, boolean complementary) {
        if (!selfView) {
            renderer.argus$setFovMultiplier(1.0F);
            renderer.argus$setLastFovMultiplier(1.0F);
        } else if (mc.player.isUsingSpyglass() && complementary != perspective.isFirstPerson()) {
            float fov = mc.player.getFovMultiplier(complementary, mc.options.getFovEffectScale().getValue().floatValue());
            renderer.argus$setFovMultiplier(fov);
            renderer.argus$setLastFovMultiplier(fov);
        } else {
            sharesMainFov = true;
        }
    }

    private static String status(MinecraftClient mc, Entity projectile, PlayerEntity followed) {
        if (projectile != null) {
            return "projectile\nF5";
        }
        if (followed != null) {
            String name = followed.getName().getString();
            String person = ArgusConfig.get().followThirdPerson ? "3rd person" : "1st person";
            return (Follow.showNearestLabel() ? "closest player\n" + name : name) + "\n" + person;
        }
        boolean complementary = ArgusConfig.get().mirrorPerspective && !mc.options.getPerspective().isFirstPerson();
        if (Follow.lockedTargetLost(mc)) {
            return complementary ? "target lost\nyou (1st person)" : "target lost\nF5 (you)";
        }
        return complementary ? "you\n1st person" : "F5 (you)\n3rd person";
    }
}
