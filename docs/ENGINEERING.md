# Engineering

## Building

```bash
./gradlew build
```

The jar is written to `build/libs/`.

To run a development client:

```bash
./gradlew runClient
```

The development client runs on vanilla OpenGL. To run it with Sodium or with VulkanMod, which
cannot be loaded together:

```bash
./gradlew runClient -Pdev_renderer=sodium
```

```bash
./gradlew runClient -Pdev_renderer=vulkanmod
```

macOS has no system Vulkan loader. For the VulkanMod run, the build extracts MoltenVK from LWJGL's
natives and points LWJGL at it, so nothing needs to be installed.

`runClient` passes `--quickPlaySingleplayer 'New World'` so it loads straight into a world. The
second window only opens once a world is loaded, so there is nothing to see on the title screen.

## How it works

After the game finishes a frame, Argus runs the game's own world renderer a second time with the
camera replaced and the render target redirected to a framebuffer it owns, then copies that
framebuffer into a second GLFW window created with the main window's OpenGL context shared.

### The second pass

At the end of `GameRenderer.render`, Argus snapshots the camera the main frame already computed,
places its own, and calls the world renderer again. `MinecraftClient.getFramebuffer` is redirected
for the duration of the pass, so the game draws into a framebuffer Argus owns without any of the
renderer knowing it is running twice. View bob, hurt tilt and the field of view apply while the
second view is on you, in first or third person, since all three come from your own player; they
are suppressed while it follows another player or a projectile. The field of view is the one the
main view was drawn with that frame, taken as it stands rather than worked out a second time, so
sprinting, a speed effect or a zoom mod changes both views by the same amount. The exception is a
spyglass: the game only zooms for one in first person, and the second view is usually in the
perspective the main one is not. The camera, perspective, camera entity and crosshair target are
restored afterwards, so the pass leaves no state behind.

The game keeps one projection buffer, one fog buffer and one global-settings buffer, each written
once a frame. The second pass has its own of each, swapped in while it runs, so it never writes
over the main view's. On OpenGL that makes no difference, because a draw happens the moment it is
issued; it matters on a backend that records draws and runs them later (see
[VulkanMod](COMPATIBILITY.md#vulkanmod)).

### Independent culling

`WorldRenderer` keeps its visible-chunk lists as instance state, so a second camera walking them
would destroy the main view's. Argus swaps in a second `ChunkRenderingDataPreparer`, its own visible
and nearby chunk lists and its own record of where the camera last was for the duration of the
pass, and swaps them back afterwards.

Not everything is separate. The chunk storage itself is shared and stays centred on the main
camera, so the second view can only show sections the main view has loaded. Translucent geometry is
not re-sorted for the second camera; it keeps the main camera's order. Most invalidations are
mirrored into the second occlusion graph, and it is also rebuilt in full every 500 ms to catch the
ones that are not, such as a chunk unloading.

### Render distance

The shorter render distance does not come from the culling state. It is a clamp on the far plane
and on the fog distance in the second pass; the frustum built from that projection then drops the
far sections from the second view's own visible list, which is safe only because that list is
separate. The occlusion graph still walks the main view's full distance, so the saving is on the
GPU, and the second view can never reach further than the main one.

### The throttle

Argus keeps exponential moving averages of the main frame time and of what the second pass costs,
and renders the second view once every *n* frames, where *n* is the pass cost divided by the
budget's share of frame time, rounded up and clamped to 1–20. Between renders the last frame is
presented again, so the window keeps showing something rather than stalling.

### Presentation

The second window is created with `glfwCreateWindow` sharing the main window's context. Presenting
copies the frame into a texture Argus owns, places a fence in the main context, waits on it in the
second, and draws the texture through a minimal shader. Because the texture is Argus's rather than
the game's, it survives the world unloading, which is what lets the window hold its last frame
instead of going grey when you leave a world.

### The interface

The HUD and any open screen are replayed onto the second view, not drawn a second time, so nothing
in the interface ticks twice. `GuiRenderer.render` first turns the frame's interface into meshes
and draws and then submits them to the main render target; a mixin wraps that submit and runs it
once more with the render target swapped, so the second view gets exactly the draws the main window
got. That happens on every frame, outside the throttled second pass: the world is kept in one
framebuffer, and each frame it is copied into another that the interface is drawn over, so the
interface stays current while the world behind it is presented again. The copy is always the size
of the main window's framebuffer, whatever Resolution % is, because the interface's scissor
rectangles are computed in main-window pixels.

The game draws no cursor of its own (the pointer over the main window belongs to the operating
system), so Argus draws one, as a small bitmap, along with the status text. Its position is the one
the interface was drawn with that frame, so it sits on whatever the interface shows as hovered.

The HUD and an open screen are built into one picture before anything is drawn, so a replay cannot
leave the screen out. With Menus off, the HUD goes to the second view on its own instead: as soon
as it is built and before the screen is added, it is drawn there, then built again for the main
window, and the full replay is skipped. The HUD is therefore built twice on frames where a screen
is open, and only then.

The replay differs from the main window's interface in two ways. The second view is rendered at the
main window's aspect ratio and stretched to fit, so the interface, and the cursor with it, is
distorted if the two windows have different shapes. And the drawn cursor is always an arrow,
whatever shape the system pointer has taken.

## Project layout

Under `src/main/java/io/github/arstoienn/argus/`:

| Path | Contents |
|---|---|
| `ArgusClient.java` | Key bindings, the tick hook and the action-bar messages |
| `ArgusConfig.java` | Settings and their descriptions, persisted to `config/argus.json` |
| `ArgusApi.java` | WalksyLib entry point, which puts the settings screen in Mod Menu |
| `SecondPass.java` | The second pass: picks the camera, draws the world, restores the game's state, presents |
| `SecondView.java` | The world and interface framebuffers, and the render-target redirect |
| `SecondWindow.java` | The second GLFW window: creation, presentation, fullscreen, remembered geometry |
| `VulkanWindow.java` | The second window's surface, swapchain and presentation when VulkanMod is installed |
| `PassUniforms.java` | The second pass's own projection, fog and global-settings buffers |
| `CameraRig.java` | Camera placement, wall clipping and damping |
| `Follow.java` | Follow targets: the nearest or a locked player, and your projectiles in flight |
| `FrameBudget.java` | The frame-time throttle |
| `WindowState.java` | Second window geometry, persisted to `config/argus-window.json` |
| `Blit.java` | Texture-to-texture copies |
| `BitmapText.java` | Status text and the drawn mouse cursor |
| `mixin/GameRendererMixin.java` | Runs the second pass; view bob, hurt tilt, field of view, far plane, fog distance and hands in it |
| `mixin/WorldRendererMixin.java` | Separate culling state for the second pass |
| `mixin/GuiRendererMixin.java` | Submits each frame's interface draws a second time, onto the second view |
| `mixin/MinecraftClientMixin.java` | Redirects the render target |
| `mixin/EntityHitboxDebugRendererMixin.java` | Hitboxes in the second view |
| `mixin/RenderSystemMixin.java` | Presents the second window after VulkanMod has submitted the main frame |
| `mixin/sodium/` | Patch to Sodium's terrain setup, applied only when it is installed |
| `mixin/vulkan/` | Patches to VulkanMod's command encoder and terrain renderer, applied only when it is installed |
| `mixin/ArgusMixinPlugin.java` | Skips `mixin/sodium/` and `mixin/vulkan/` when those mods are absent |
| `mixin/*Accessor.java` | Accessors for otherwise private state |
