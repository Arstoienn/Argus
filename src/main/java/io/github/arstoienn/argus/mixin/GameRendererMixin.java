package io.github.arstoienn.argus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.arstoienn.argus.ArgusConfig;
import io.github.arstoienn.argus.PassUniforms;
import io.github.arstoienn.argus.SecondPass;
import io.github.arstoienn.argus.SecondView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow
    @Final
    private OrderedRenderCommandQueueImpl orderedRenderCommandQueue;
    @Shadow
    @Final
    private RenderDispatcher entityRenderDispatcher;
    @Shadow
    @Final
    private BufferBuilderStorage buffers;
    @Shadow
    @Final
    public HeldItemRenderer firstPersonRenderer;
    @Shadow
    @Final
    private GuiRenderer guiRenderer;
    @Shadow
    @Final
    private FogRenderer fogRenderer;

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("RETURN"))
    private void argus$renderSecondView(RenderTickCounter ticks, boolean tick, CallbackInfo ci) {
        SecondPass.afterFrame((GameRenderer) (Object) this, ticks, tick);
    }

    /**
     * With screens left off the second view, the HUD goes there on its own: as soon as it is built,
     * before the open screen is added to the same picture, it is drawn onto the second view, then
     * built again for the main window.
     */
    @WrapOperation(
        method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"))
    private void argus$hudWithoutScreen(InGameHud hud, DrawContext context, RenderTickCounter ticks, Operation<Void> original) {
        original.call(hud, context, ticks);
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean screenComing = mc.currentScreen != null && mc.getOverlay() == null;
        if (screenComing && !ArgusConfig.get().secondViewScreens && SecondView.wantsInterface()
                && SecondView.drawHudAlone(this.guiRenderer, this.fogRenderer.getFogBuffer(FogRenderer.FogType.NONE))) {
            original.call(hud, context, ticks);
        }
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void argus$closeSecondPassUniforms(CallbackInfo ci) {
        PassUniforms.close();
    }

    @Inject(method = "bobView(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"), cancellable = true)
    private void argus$bobOnlyOnYou(CallbackInfo ci) {
        if (SecondPass.active && !SecondPass.selfView) {
            ci.cancel();
        }
    }

    @Inject(method = "tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"), cancellable = true)
    private void argus$hurtTiltOnlyOnYou(CallbackInfo ci) {
        if (SecondPass.active && !SecondPass.selfView) {
            ci.cancel();
        }
    }

    // The shorter render distance is a far plane and fog clamp; the frustum then culls the far
    // sections from the second view's own visible list.

    @Inject(method = "getFarPlaneDistance", at = @At("RETURN"), cancellable = true)
    private void argus$clampFarPlane(CallbackInfoReturnable<Float> cir) {
        int chunks = ArgusConfig.get().secondViewRenderDistance;
        if (SecondPass.active && chunks > 0) {
            cir.setReturnValue(Math.min(cir.getReturnValueF(), chunks * 16.0F + 64.0F));
        }
    }

    @ModifyArg(
        method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;"),
        index = 1)
    private int argus$clampFog(int renderDistance) {
        int chunks = ArgusConfig.get().secondViewRenderDistance;
        return SecondPass.active && chunks > 0 ? Math.min(renderDistance, chunks) : renderDistance;
    }

    /**
     * In the second pass, hands are drawn only where they belong to the view: yours in your own first
     * person (left to vanilla), and a followed player's when watching from their eyes.
     */
    @Inject(method = "renderHand(FZLorg/joml/Matrix4f;)V", at = @At("HEAD"), cancellable = true)
    private void argus$secondPassHands(float tickProgress, boolean sleeping, Matrix4f positionMatrix, CallbackInfo ci) {
        if (!SecondPass.active || SecondPass.selfFirstPerson) {
            return;
        }
        ci.cancel();
        // renderHand starts by drawing the entities queued so far; cancelling it must not skip that.
        this.entityRenderDispatcher.render();
        this.buffers.getEntityVertexConsumers().draw();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (!sleeping
                && mc.options.getPerspective().isFirstPerson()
                && mc.getCameraEntity() instanceof AbstractClientPlayerEntity target
                && target != mc.player
                && !target.isInvisible()) {
            this.argus$renderFollowedHands(mc, target, tickProgress, positionMatrix);
        }
    }

    @Unique
    private void argus$renderFollowedHands(MinecraftClient mc, AbstractClientPlayerEntity target, float tickProgress, Matrix4f positionMatrix) {
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(positionMatrix.invert(new Matrix4f()));
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().mul(positionMatrix);

        // The held-item renderer keeps its own idea of what is in each hand; point it at the
        // target's items while drawing theirs.
        HeldItemRendererAccessor held = (HeldItemRendererAccessor) this.firstPersonRenderer;
        ItemStack ownMain = held.argus$mainHand();
        ItemStack ownOff = held.argus$offHand();
        ItemStack mainItem = target.getMainHandStack();
        ItemStack offItem = target.getOffHandStack();
        try {
            held.argus$setMainHand(mainItem);
            held.argus$setOffHand(offItem);
            int light = mc.getEntityRenderDispatcher().getLight(target, tickProgress);
            float swing = target.getHandSwingProgress(tickProgress);
            Hand swingingHand = target.preferredHand != null ? target.preferredHand : Hand.MAIN_HAND;
            float pitch = target.getLerpedPitch(tickProgress);
            // A bow or crossbow being drawn takes the view, so only the hand holding it is drawn.
            boolean drawingBow = target.isUsingItem()
                && (target.getActiveItem().isOf(Items.BOW) || target.getActiveItem().isOf(Items.CROSSBOW));
            Hand bowHand = drawingBow ? target.getActiveHand() : null;

            if (!mainItem.isEmpty() && bowHand != Hand.OFF_HAND) {
                held.argus$renderFirstPersonItem(target, tickProgress, pitch, Hand.MAIN_HAND,
                    swingingHand == Hand.MAIN_HAND ? swing : 0.0F, mainItem, 0.0F, matrices, this.orderedRenderCommandQueue, light);
            }
            if (!offItem.isEmpty() && bowHand != Hand.MAIN_HAND) {
                held.argus$renderFirstPersonItem(target, tickProgress, pitch, Hand.OFF_HAND,
                    swingingHand == Hand.OFF_HAND ? swing : 0.0F, offItem, 0.0F, matrices, this.orderedRenderCommandQueue, light);
            }
            this.entityRenderDispatcher.render();
            this.buffers.getEntityVertexConsumers().draw();
        } finally {
            held.argus$setMainHand(ownMain);
            held.argus$setOffHand(ownOff);
            modelView.popMatrix();
        }
    }
}
