# Compatibility

Argus is built against a fixed set of Yarn mappings and touches renderer internals through mixins,
so it is tied to Minecraft 1.21.11 and will need updating for any other version.

## Sodium

Supported. [Sodium](https://modrinth.com/mod/sodium) replaces the terrain renderer but keeps
OpenGL, so the second window works the way it does without it. Sodium rebuilds its list of visible
sections for whichever camera it is handed, so the second view is culled for its own camera. What
it is not built for is two cameras in one frame, and three things are patched while the second
pass runs:

- The record it keeps of the previous camera is saved and put back, so that its translucency
  sorting only ever follows the main camera.
- Its per-frame timing, which sets the budget for chunk uploads, is not advanced a second time.
- Its visible list is marked stale on the way out, because there is only one and it has just been
  filled in for the second camera.

The cost is that Sodium recomputes that list for both cameras on every frame the second view
renders, including frames where the main camera has not moved.

Run against Sodium 0.8.14 for 1.21.11 in the development client. The patch names Sodium's internal
classes, so another version may need it adjusted.

## VulkanMod

Supported experimentally. [VulkanMod](https://modrinth.com/mod/vulkanmod) replaces the OpenGL
backend with Vulkan, so the main window has no OpenGL context for a second window to share and
none of Argus's own GL calls can run. With VulkanMod installed the second window gets a Vulkan
surface and swapchain of its own, and each frame the second view is blitted into it on VulkanMod's
device, in a command buffer submitted after the main frame's. Four things in VulkanMod assume there
is only one view and are handled around the second pass:

- It decides whether a render target is the main window by comparing it with
  `MinecraftClient.getFramebuffer`, which is exactly the call Argus redirects, so without a fix the
  second view is drawn into the main window. Inside VulkanMod's command encoder that call is made
  to return the real main framebuffer.
- Its terrain and cloud renderers bypass the game's render passes and bind the main window
  directly. For the duration of the second pass its `MainPass` is swapped for one that binds
  Argus's framebuffer instead.
- Its terrain renderer keeps one section grid, one indirect-draw buffer and one translucency sort.
  The second camera is stopped from moving the grid, resetting the buffer or re-sorting, for the
  same reason the vanilla culling state is kept separate.
- It records draws during the frame and executes them when the frame ends, so anything the second
  pass overwrites is seen by main-view draws that have not run yet. The second pass's own
  projection, fog and global buffers cover most of that, and the main frame's commands are also
  submitted and waited for before the second pass starts, which covers the per-frame state that
  cannot be given a second copy. The price is that the CPU waits for the GPU to finish the main
  view before each second pass.

**Following another player is disabled on VulkanMod.** On a multiplayer server it left entities in
the main window drawn in the wrong place for as long as the second window was on someone else, and
that has not been reproduced in a single-player world. Until the cause is found, the follow key and
the first/third-person key only show a message when VulkanMod is installed. Following your own
projectiles still works.

Run in the development client against VulkanMod 0.6.8 for 1.21.11 on Windows, and on macOS through
MoltenVK. Known gaps:

- The visible-section graph is rebuilt for each camera every frame, which costs more than the
  separate culling state does on OpenGL.
- With no world loaded, the window simply keeps its last image, whatever Keep last frame says.
- The patches name VulkanMod's internal classes, so another VulkanMod version may need them
  adjusted.

Without VulkanMod installed, none of this code is loaded.

## Zoom mods

Zoom mods zoom both views. While the second view is on you it takes the main view's field of view,
so zooming in the main window zooms the second window too. It is not worked out separately because
that does not give the same answer: [Zume](https://modrinth.com/mod/zume) zooms the field of view
only in first person and reads the perspective at that moment, which the second pass has changed,
so one view came out zoomed and the other not, whichever perspective the main window was in. On
VulkanMod that difference made the main window flicker.

## Ixeris

Incompatible. Ixeris moves the game off the real main thread while still naming that thread
"Render thread", and GLFW requires windows to be created on the main thread, so `glfwCreateWindow`
fails and the second window never opens. Argus detects Ixeris and says so in the log. Removing it
restores the second window.
