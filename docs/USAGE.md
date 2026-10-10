# Using Argus

The second window opens once a world is loaded. Its position and size are remembered between
sessions.

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

Stored in `config/argus.json` and edited through the settings screen, which WalksyLib provides and
Mod Menu opens.

### View

| Option | Default | What it does |
|---|---|---|
| Complementary view | off | Displays a first-person perspective in the second window while the main window is in third person |
| Follow projectiles | on | Follows arrows, tridents and ender pearls launched by the player (see below) |
| Third-person follow | on | Views a followed player from behind; when disabled, the view is taken from the player's eyes |
| HUD | off | Displays the heads-up display, including the hotbar, health and chat, in the second window |
| Menus | off | Displays open menus, such as the inventory, in the second window, together with the mouse pointer; requires HUD |
| Hitboxes | on | Displays entity hitboxes in the second window whenever they are enabled with F3+B |
| Status text | on | Displays a label in the second window indicating what it is currently showing |
| Keep last frame | on | Retains the last rendered frame in the second window after leaving a world |

Follow projectiles applies to arrows (including tipped arrows and those fired from a crossbow),
spectral arrows, tridents and ender pearls launched by the player, until they land or stick in a
block. Snowballs, eggs, splash and lingering potions, bottles o' enchanting, wind charges, firework
rockets and fishing bobbers are not followed, nor are projectiles launched by anyone else.

With Menus off, the second window keeps the HUD but leaves out the menus you open and the pointer,
so a recording of it shows the game rather than your menus.

### Camera

| Option | Default | Range |
|---|---|---|
| Distance | 4.0 | 1.0–12.0 blocks behind |
| Shoulder offset | 0.5 | −2.0–2.0 blocks, negative is the left shoulder |
| Height offset | 0.3 | −1.0–1.5 blocks, measured along world up |
| Smoothing % | 50 | 0–100, `0` is off |

The camera jumps instead of gliding when it would have to move more than 32 blocks at once, such
as on a teleport or a dimension change.

### Performance

| Option | Default | Range |
|---|---|---|
| Frame budget % | 25 | 0–75, `0` is no limit |
| Render distance | 0 | 0–32 chunks, `0` matches the main window; it can only be shorter |
| Resolution % | 100 | 10–100, of the main window's resolution |
| Vsync | off | |

Resolution is pure fill rate and is the cheapest of the three to turn down. It applies to the
world only; the interface on the second view is always drawn at the main window's resolution.
Render distance is the largest saving on the GPU. The budget is what protects the main frame rate.

Vsync is off by default because the buffer swap happens on the render thread, so turning it on
makes that thread wait for the second window's vblank every frame and costs main frame rate.

## Troubleshooting

**The second window opens somewhere you cannot reach**, on a monitor that is no longer attached
for instance. Delete `config/argus-window.json` to put it back at its default position and size.
That file holds only the window's geometry, and is kept apart from `config/argus.json` so that the
settings file is not rewritten in the background whenever the window moves.

**The second window never opens.** The log says why, under `[argus]`. The usual cause is Ixeris;
see [Compatibility](COMPATIBILITY.md#ixeris).
