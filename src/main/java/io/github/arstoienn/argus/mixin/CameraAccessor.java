package io.github.arstoienn.argus.mixin;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setPos")
    void argus$setPos(double x, double y, double z);

    @Invoker("setRotation")
    void argus$setRotation(float yaw, float pitch);

    /**
     * Written directly, without Camera.update. It decides whether your own player is drawn, and it
     * is the one thing the second view's first person would otherwise need updateCamera for.
     */
    @Accessor("thirdPerson")
    void argus$setThirdPerson(boolean thirdPerson);
}
