# Argus

A Fabric client mod that renders a second camera into a window of its own, alongside Minecraft's.

After the game finishes a frame, Argus runs the game's own world renderer a second time with the
camera replaced and the render target redirected to a framebuffer it owns, then blits that
framebuffer into a second GLFW window created with the main window's OpenGL context shared. The
second pass keeps its own chunk-culling state, so the second camera never disturbs what the main
view has decided is visible, and it can be given a shorter render distance and a lower resolution
than the main view. It is throttled against a share of frame time, so the cost of the second view
is bounded rather than paid in full every frame.

The camera can sit over your shoulder, lock onto another player, or ride a projectile you fired.
When the main window is put into third person the second window flips to first person instead, so
the two views never show the same thing twice.

## Features

- Second window with its own camera, position and size, remembered between sessions
- Over-the-shoulder camera with configurable distance, shoulder offset and height offset, pulled
  in by walls so it never ends up inside a block
- Follow another player, either from behind or from their own eyes, with their hands and swing
  animation drawn
- Automatic tracking of your own arrows, tridents and ender pearls while they are in the air,
  handed back to the previous target when they land
- Complementary perspective: the second window shows first person while the main window is in
  third person, restoring the near field and the hand animations that third person costs you
- Frame-time budget, a shorter render distance and a lower resolution for the second view
- The interface replayed over the second view — hotbar, health, chat, open screens and a drawn
  cursor — so the second window is a complete picture of the session for recording
- Entity hitboxes in the second view when F3+B is on, including your own
- Frame-rate-independent camera damping with a snap distance for teleports and dimension changes
- Settings screen through WalksyLib, reachable from Mod Menu

## Requirements

| | |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.3 or newer |
| Fabric API | any version for 1.21.11 |
| [WalksyLib](https://modrinth.com/mod/walksylib) | 0.9.992+1.21.11 (provides the settings screen) |
| Java | 21 or newer |

Client-side only. The mod does nothing on a server and does not need to be installed on one.

## Building

```bash
./gradlew build
```

The jar is written to `build/libs/`. Drop it, Fabric API and WalksyLib into `.minecraft/mods/`.

To run a development client:

```bash
./gradlew runClient
```

The development client runs on vanilla OpenGL. To run it with Sodium:

```bash
./gradlew runClient -Pdev_renderer=sodium
```

`runClient` passes `--quickPlaySingleplayer 'New World'` so it loads straight into a world. The
second window only opens once a world is loaded, so there is nothing to see on the title screen.

## Controls

The keys are grouped under an "Argus" heading in the vanilla Controls screen.

| Key | Action |
|---|---|
| `\` | Follow: cycles off → nearest player → locked on that player → next player → … |
| `Backspace` | Stop following |
| `]` | Watch a followed player in first or third person |
| unbound | Hide or show the second window |
| unbound | Open the settings screen |
| `F11` | Fullscreen the second window (pressed while it has focus) |

Hiding the window and opening the settings are left unbound deliberately: both are used once in a
while and do not earn a default key. The three that are bound sit in the cluster at the top right
of the keyboard, which few mods claim.

Following a projectile takes priority over following a player, so every shot pulls the window away
from whatever it was on and gives it back when the projectile lands.

## Settings

Stored in `config/argus.json`, edited through the settings screen. Every option carries a full
description there; the tables below give the defaults and ranges.

### Camera

| Option | Default | Range |
|---|---|---|
| Complementary view | on | |
| Camera smoothing | on | |
| Smoothing amount | 50% | 0–100% |
| Snap distance | 32 | 1–256 blocks |
| Follow distance | 4.0 | 1.0–12.0 blocks |
| Shoulder offset | 0.5 | −2.0–2.0 blocks, negative is over the left shoulder |
| Height offset | 0.3 | −1.0–1.5 blocks, measured along world up |
| Follow your projectiles | on | |
| First-person follow | off | |

### Performance

| Option | Default | Range |
|---|---|---|
| Frame-time budget % | 25 | 0–75, `0` disables the throttle |
| Render distance (chunks) | 0 | 0–32, `0` means the same as the main view; it can only shorten, so a value above the main view's does nothing |
| Resolution % | 100 | 10–100, of the main window's resolution |

Resolution is pure fill rate and is the cheapest of the three to turn down. It applies to the
world only; the interface on the second view is always drawn at the main window's resolution.
Render distance is the largest saving on the GPU. The budget is what protects the main frame rate.

### Behaviour

| Option | Default |
|---|---|
| Interface on the second view | on |
| Show status text | on |
| Show hitboxes | on |
| Hold last frame when you leave | on |
| Second window vsync | off |

Vsync on the second window is off by default because the buffer swap happens on the render thread,
so turning it on makes that thread wait for the second window's vblank every frame and costs main
frame rate.

## How it works

**The second pass.** At the end of `GameRenderer.render`, Argus snapshots the camera the main
frame already computed, places its own, and calls the world renderer again.
`MinecraftClient.getFramebuffer` is redirected for the duration of the pass, so the game draws
into a framebuffer Argus owns without any of the renderer knowing it is running twice. View bob,
hurt tilt and the FOV multiplier apply while the second view is on you, in first or third person,
since all three come from your own player; they are suppressed while it follows another player or
a projectile. The multiplier used is the main view's own, taken as it stands that frame, except
for a spyglass: the game only zooms for one in first person, and the second view is usually in
the perspective the main one is not. The camera, perspective, camera entity and crosshair target
are restored afterwards, so the pass leaves no state behind.

**Independent culling.** `WorldRenderer` keeps its visible-chunk lists as instance state, so a
second camera walking them would destroy the main view's. Argus swaps in a second
`ChunkRenderingDataPreparer`, its own visible and nearby chunk lists and its own record of where
the camera last was for the duration of the pass, and swaps them back afterwards.

Not everything is separate. The chunk storage itself is shared and stays centred on the main
camera, so the second view can only show sections the main view has loaded. Translucent geometry
is not re-sorted for the second camera; it keeps the main camera's order. Most invalidations are
mirrored into the second occlusion graph, and it is also rebuilt in full every 500 ms to catch the
ones that are not, such as a chunk unloading.

**Render distance.** The shorter render distance does not come from the culling state. It is a
clamp on the far plane and on the fog distance in the second pass; the frustum built from that
projection then drops the far sections from the second view's own visible list, which is safe
only because that list is separate. The occlusion graph still walks the main view's full
distance, so the saving is on the GPU, and the second view can never reach further than the main
one.

**The throttle.** Argus keeps exponential moving averages of the main frame time and of what the
second pass costs, and renders the second view once every *n* frames, where *n* is the pass cost
divided by the budget's share of frame time, rounded up and clamped to 1–20. Between renders the
last frame is presented again, so the window keeps showing something rather than stalling.

**Presentation.** The second window is created with `glfwCreateWindow` sharing the main window's
context. Presenting copies the frame into a texture Argus owns, places a fence in the main
context, waits on it in the second, and draws the texture through a minimal shader. Because the
texture is Argus's rather than the game's, it survives the world unloading — which is what lets
the window hold its last frame instead of going grey when you leave a world.

**The interface.** The HUD and any open screen are replayed onto the second view, not drawn a
second time, so nothing in the interface ticks twice. `GuiRenderer.render` first turns the
frame's interface into meshes and draws and then submits them to the main render target; a mixin
wraps that submit and runs it once more with the render target swapped, so the second view gets
exactly the draws the main window got. That happens on every frame, outside the throttled second
pass: the world is kept in one framebuffer, and each frame it is copied into another that the
interface is drawn over, so the interface stays current while the world behind it is presented
again. The copy is always the size of the main window's framebuffer, whatever Resolution % is,
because the interface's scissor rectangles are computed in main-window pixels.

The game draws no cursor of its own — the pointer over the main window belongs to the operating
system — so Argus draws one, as a small bitmap, along with the status text. Its position is the
one the interface was drawn with that frame, so it sits on whatever the interface shows as
hovered.

The replay differs from the main window's interface in two ways. The second view is rendered at
the main window's aspect ratio and stretched to fit, so the interface, and the cursor with it, is
distorted if the two windows have different shapes. And the drawn cursor is always an arrow,
whatever shape the system pointer has taken.

## Compatibility

The mod is built against a fixed set of Yarn mappings and touches renderer internals through
mixins, so it is tied to Minecraft 1.21.11 and will need updating for any other version.

**Sodium is supported.** [Sodium](https://modrinth.com/mod/sodium) replaces the terrain renderer
but keeps OpenGL, so the second window works the way it does without it. Sodium rebuilds its list
of visible sections for whichever camera it is handed, so the second view is culled for its own
camera. What it is not built for is two cameras in one frame, and three things are patched while
the second pass runs: the record it keeps of the previous camera is saved and put back, so that
its translucency sorting only ever follows the main camera; its per-frame timing, which sets the
budget for chunk uploads, is not advanced a second time; and its visible list is marked stale on
the way out, because there is only one and it has just been filled in for the second camera. The
cost is that Sodium recomputes that list for both cameras on every frame the second view renders,
including frames where the main camera has not moved. Run against Sodium 0.8.14 for 1.21.11 in the
development client; the patch names Sodium's internal classes, so another version may need it
adjusted.

**VulkanMod is not supported in this version.** It replaces OpenGL, so there is no context for the
second window to share. With VulkanMod installed Argus logs that and leaves the second window off.

**Ixeris is incompatible.** It moves the game off the real main thread while still naming that
thread "Render thread", and GLFW requires windows to be created on the main thread, so
`glfwCreateWindow` fails and the second window never opens. Argus detects Ixeris and says so in the
log. Removing it restores the second window.

If the second window opens somewhere you cannot reach it — on a monitor that is no longer attached,
for instance — delete `config/argus-window.json` to put it back at its default position and size.
That file holds runtime geometry only and is kept separate from `config/argus.json` so that the
settings file is not rewritten every few seconds in the background.

## Project layout

| Path | Contents |
|---|---|
| `ArgusClient.java` | Key bindings, the tick hook and the action-bar messages |
| `ArgusConfig.java` | Settings and their descriptions, persisted to `config/argus.json` |
| `ArgusApi.java` | WalksyLib entry point, which puts the settings screen in Mod Menu |
| `SecondPass.java` | The second pass: picks the camera, draws the world, restores the game's state, presents |
| `SecondView.java` | The world and interface framebuffers, and the render-target redirect |
| `SecondWindow.java` | The second GLFW window: creation, presentation, fullscreen, remembered geometry |
| `CameraRig.java` | Camera placement, wall clipping and damping |
| `Follow.java` | Follow targets: the nearest or a locked player, and your projectiles in flight |
| `FrameBudget.java` | The frame-time throttle |
| `WindowState.java` | Second window geometry, persisted to `config/argus-window.json` |
| `Blit.java` | GL texture-to-texture copies |
| `BitmapText.java` | Status text and the drawn mouse cursor |
| `mixin/GameRendererMixin.java` | Runs the second pass; view bob, hurt tilt, far plane, fog distance and hands in it |
| `mixin/WorldRendererMixin.java` | Separate culling state for the second pass |
| `mixin/GuiRendererMixin.java` | Submits each frame's interface draws a second time, onto the second view |
| `mixin/MinecraftClientMixin.java` | Redirects the render target |
| `mixin/EntityHitboxDebugRendererMixin.java` | Hitboxes in the second view |
| `mixin/sodium/` | Patch to Sodium's terrain setup, applied only when it is installed |
| `mixin/ArgusMixinPlugin.java` | Skips `mixin/sodium/` when Sodium is absent |
| `mixin/*Accessor.java` | Accessors for otherwise private state |

## License

Released under the MIT License; see [LICENSE](LICENSE).
