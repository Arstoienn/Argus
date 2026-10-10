package io.github.arstoienn.argus.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HeldItemRenderer.class)
public interface HeldItemRendererAccessor {
    @Invoker("renderFirstPersonItem")
    void argus$renderFirstPersonItem(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
                                     float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                     OrderedRenderCommandQueue queue, int light);

    @Accessor("mainHand")
    ItemStack argus$mainHand();

    @Accessor("mainHand")
    void argus$setMainHand(ItemStack item);

    @Accessor("offHand")
    ItemStack argus$offHand();

    @Accessor("offHand")
    void argus$setOffHand(ItemStack item);
}
