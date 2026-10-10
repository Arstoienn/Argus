package io.github.arstoienn.argus.mixin;

import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Writes the perspective field directly instead of calling setPerspective.
 *
 * The second pass switches perspective and back every frame. To the game that is just the field,
 * which is all getPerspective reads, but setPerspective is where camera mods hook a real
 * perspective change: Shoulder Surfing cancels it and runs its own state machine instead. Going
 * through it would trigger that twice a frame and never change the field at all.
 */
@Mixin(GameOptions.class)
public interface GameOptionsAccessor {
    @Accessor("perspective")
    void argus$setPerspective(Perspective perspective);
}
