# Vore Mod (mod id: gulp) - Forge 1.20.1

Forge 1.20.1 (47.x) mod: swallow mobs, carry them in a stomach, digest them for loot.

## Build
Easiest: push this folder to a GitHub repo. The workflow in .github/workflows/build.yml builds it
(Actions tab > newest run > Artifacts > gulp-mod).
Locally: install JDK 17 + Gradle 8.1.1, run `gradle build`. The jar lands in build/libs/gulp-0.1.0.jar.
For testing in a dev environment: `gradle runClient`.

## Install
Install Minecraft Forge for 1.20.1, then put gulp-0.1.0.jar in the mods folder. (No Fabric API needed.)

## Controls (rebindable)
- V = swallow the mob you're looking at (within 4 blocks). Looking UP makes the cooldown shorter.
- B = toggle Soft / Hard mode
- G = release the most recent mob. Sneak + G = release everything.
- N = open the stomach screen (see contents, release or digest each mob, spend perk points, settings)

## Mechanics
- Capacity is a volume number. A mob's size is its hitbox width x width x height (chicken ~0.1, villager ~0.7, cow ~1.1,
  horse ~3, iron golem ~5, modded mobs anywhere from 1 to hundreds). A mob fits if it is no bigger than your capacity and
  everything inside adds up to no more than your capacity. You can hold up to 100 creatures at once.
- Capacity = (1 + 0.75 x level + 0.08 x level^2) x (1 + 0.06 x Roomy Stomach rank). Every level gives more room than the
  last: 1.8 at level 1, 16.5 at 10, 48 at 20, 239 at 50, 859 at 99, and up to 2406 with Roomy Stomach 30/30.
- Levels go to 99. XP per level: 100, 280, 520 ... 3160 at level 10, then +40 per level (4080 at level 33, 6720 at
  level 98). Reaching level 33 takes about 630 zombies (with Gourmet), level 99 about 3000.
- Soft mode: mobs are held safely. A completely full stomach earns one level's worth of XP every 4 minutes, so the
  fuller you are the faster you level.
- Hard mode: mobs digest over time (13s for a cow, 22s for an iron golem, about 3 minutes for a size-500 monster).
  When the digestion bar is full they don't die instantly: for about 8 more seconds they take steady damage (their red
  health bar drains on the stomach screen), then they die. A digested mob is never added to the world (nobody sees it), and its loot and XP go straight into the
  digesting player's inventory (overflow lands at their feet). (DISSOLVE_SECONDS in StomachLogic.java.) Digesting gives 20 + 4 x the mob's max health in XP. Now and then (about 8% per mob per second)
  a mob struggles and hurts you: 2 HP plus 0.75 per 1.0 of mob volume, capped at 5 HP. Hard mode only; armor and Iron
  Stomach reduce it, and it never takes you below 2 hearts.
- Perk points: the game hands out exactly enough points by level 99 to buy every rank of every perk (113 points for the
  current 113 ranks), with none left over and none missing. Most levels give 1 point and about every eighth level
  gives 2 (see Stomach.cumulativePoints). If you add or change perks, the points adjust themselves. Older saves are
  topped up automatically to what their level should have earned.
  Spend them in N > Perks. There are two menus (switch with the Legacy / New look button, or Settings > Perk menu):
  the new look (every perk is a node around a hub with a progress ring, no connecting lines because nothing unlocks anything)
  and the legacy list. Click = 1 rank, Shift = 5, Ctrl = as many as you can afford:
  Roomy Stomach (30 ranks, +6% capacity each), Healing Stomach (15, soft mode: held mobs heal 1% of max HP per rank
  every 5s), Better Loot (10, +1 Looting level per 2 ranks on digested mobs, up to V), Quick Gulp (10, swallow
  cooldown -0.075s per rank anywhere and -0.15s per rank when looking up: 2s with no perk, 1.25s / 0.5s at 10 ranks),
  Iron Stomach (15, -5% struggle damage per rank), Gourmet (20, +5% XP from digesting per rank), Hearty Meal (10, heal
  0.5 HP per rank for every mob you digest), Iron Gut (3, more hunger per 32 items eaten, see Item digestion). Add more in Perk.java (append to the end).
- Aiming: mobs have a slightly bigger hitbox for swallowing, and aiming near a mob (about 12 degrees) still counts.
- The Ender Dragon and the Wither can never be swallowed.
- Item storage: look at a dropped item and press V to swallow the stack into your item storage; or open your inventory, point at
  an item and press V (rebindable: "Swallow Hovered Item (in Inventory)") to swallow that stack, holding Ctrl for just one. Only your
  own inventory and hotbar count, not armor, the offhand or the crafting grid. You can also press Storage on the
  stomach screen to open it as a chest-style screen (shift-click moves items in and out; a Back button beside the window returns to the stomach screen). It starts at 1 row (9 slots)
  and gains a row every 17 levels, up to 6 rows (54 slots) at level 85. It is separate from the mob volume, saved with
  the world, safe in both modes, and drops like an inventory if you die (unless keepInventory is on).
- Player boundaries (multiplayer): every player picks a role in Settings ("Role" button, cycles Pred / Prey / Both / Neither; saved
  on the server). Pred (the default) can swallow but can't be swallowed. Prey can be swallowed but can't swallow. Both can do both.
  Neither turns vore off completely: no swallowing, can't be swallowed, and the stomach menu and HUD are hidden (the N key opens
  Settings instead). Prey and Neither can't swallow anything, mobs and items included. Switching to a role that can't swallow lets
  out everything you were holding. Old saves: "Let players swallow me" ON became Both, OFF became Pred.
- Swallow requests: swallowing a player never happens straight away. The target gets [Accept] / [Decline] buttons in chat
  (/gulp accept and /gulp decline do the same) and has 30 seconds. Everything is checked again when they accept (roles, size,
  distance of 8 blocks). One request per target at a time, and after a decline the same player can't ask again for 60 seconds.
- Role badges: pointing at a player shows "[Pred] Name" and a hint under the crosshair. Trying to swallow a Pred shows "This player
  is a predator and cannot be swallowed" (a Neither player shows a similar "opted out" message).
- Swallowing players (multiplayer): the mode (soft or hard) is fixed when the player is swallowed, and the request they accept says
  which one it is (switching mode before they accept cancels it). Swallowed players are spectators looking through the swallower's
  eyes. In both modes they are let out when the swallower dies, logs out, changes dimension or the server stops, and their game mode
  is remembered (even through a crash) and restored. A HUD at the top shows them who holds them and what their keys do.
  - Soft mode: they are only held, never harmed. Release (G) lets them out any time (or automatically after playerMaxHoldSeconds).
    The Struggle key (R) is a wall rub: the swallower gets food, saturation, Regeneration and Speed, at most once per
    playerWallRubCooldownSeconds.
  - Hard mode (needs playerHardVoreEnabled): a digestion timer runs (playerDigestSeconds, default 3 minutes). Release does not
    work. The Struggle key (R, once a second at most) rolls playerStruggleChancePercent (default 12%, falling to half as digestion
    nears 100%) to break free. When the timer ends they are let out weakened, or die if playerDigestionKills is true (default
    false); the swallower gets XP and food. The request warns about the death setting.
  - Dynamic messages: the swallower's action bar shows "[Name] squirms in your gut" on a failed struggle, "[Name] rubs your
    stomach walls", "[Name] broke free!", "[Name] slept soundly in your gut".
  - Potions: drinking a potion also gives its effects to everyone inside. Instant damage and Wither are never passed on, and other
    harmful effects only reach players held in hard mode.
  - Co-op sleep: players inside don't count towards "everyone must sleep" (they are spectators), so they never hold up the night. When
    the swallower sleeps through the night, they sleep through it too: their rest timer resets (no phantoms) and they get a message.
- Advanced items and equipment:
  - Enchant Stripper (perk, 3 ranks, unlocks at stomach level 50): the storage screen has a panel under Back with two boxes (they show
    slot 1 and slot 2, the first two storage slots) and two bars. Strip moves the enchantments off the gear in slot 1 into an enchanted
    book in your storage. Transfer (soft mode only) moves them from slot 1 (gear or enchanted book) onto the item in slot 2, skipping
    enchantments the item can't take or that conflict. Every enchantment loses (3 - rank) levels; an enchantment that would drop below
    level 1 is never destroyed (strip refuses if nothing would survive). Code: EnchantTools.
  - Mending Gut (perk, 5 ranks, unlocks at stomach level 60): every 5 seconds, damaged gear and tools in the item storage repair
    2 durability per rank. Runs while the storage screen is closed, in both modes (note hard mode also breaks gear down over time).
  - Shared storage: a swallowed player presses the stomach key (N) to see the swallower's item storage, read-only, refreshing every 2
    seconds. Server switch: sharedStorageViewEnabled.
  - Hazard warnings: swallowing sharp or hazardous items from the inventory (V key) first shows a warning that must be confirmed, then
    deals hazardSelfDamage (default 2 HP, never below 2 hearts, none in creative). Swords, axes, tridents, shears and arrows always
    count; edit the tag data/gulp/tags/items/stomach_hazardous.json for the rest (cactus, lava, TNT ...). The server refuses an
    unconfirmed hazardous swallow.
  - Nested weighting: shulker boxes and bundles weigh their contents (each item like a stack in a slot, times nestedWeightMultiplier,
    default 1.0, on top of the box itself), so the item belly grows with what is inside. Code: Stomach.weightOf.
- Stomach contents are saved with the world. Dying releases everything.

## Item digestion
- Gear deconstruction (hard mode only): tools, weapons, armor and other damageable items in item storage are broken down, one every 3 seconds,
  into the ingredients of their crafting recipe (iron chestplate -> 8 iron ingots). Worn gear gives back less (about half when nearly broken).
  Gear with no crafting recipe (netherite, some modded items) is kept. Enchanted gear is kept unless deconstructEnchanted is true.
  Switch: itemDigestionEnabled.
- Trash (hard mode only): items in the tag gulp:stomach_trash dissolve (cobblestone, dirt, gravel, seeds ...). Edit
  data/gulp/tags/items/stomach_trash.json or add to it from a datapack. Switch: trashDissolveEnabled. Nothing else is ever destroyed.
- Eat buttons (item storage screen, top right): "Eat 1" and "Eat 64" always say "Eat", whatever the item is. They act on the item in the
  FIRST slot, in either mode. Gear that can be broken down (see above) is broken down into its parts instead of eaten, in either mode
  (it does not count toward Iron Gut). Every other item (food, blocks, junk, gear with no recipe) is digested by the
  Iron Gut perk below. Things that hold items (shulker boxes,
  bundles) and enchanted gear (unless deconstructEnchanted) are refused. The server checks that the storage screen is really open (action 10 in StomachLogic).
- Iron Gut / Universal Digestion (perk, 3 ranks, rank 0 is free and always active): every 32 items eaten with the buttons (any kind) restores
  hunger. Rank 0 = 0.5 hunger bars (1 point) per 32 items, rank 1 = 1.0, rank 2 = 1.5, rank 3 = 2.0 (4 points). The running count is saved in the
  stomach (Stomach.digestProgress, NBT "DigestProgress"), so it survives closing the screen, logging out and dying. "Eat 64" can pay out twice.
  Payouts restore hunger only (no saturation) and can't overfill the bar. The storage screen shows "Digestion Progress: X / 32 Items" under the
  window with a hover tooltip, and the perk wheel shows Current / Next payout. Server switch: universalDigestionEnabled. Code: StomachItems.digestFirstSlot.
- Nothing automatic happens while any container screen is open. Code: StomachItems.java.

## Visible belly
The belly of every player model swells forward as their stomach fills (using the player's own skin).
Other players see it too. Settings > Belly style switches between Classic (whole torso) and Stomach only.
Settings > Action messages turns the short action-bar messages (Gulp! Swallowed Cow, Too big for your stomach, mode
changes, struggle warnings ...) on or off. Level-up messages are a separate setting. Messages telling a swallowed player
they were swallowed and how to get out are always shown.
Tweak MAX_EXTRA_DEPTH / MAX_EXTRA_WIDTH in BellyLayer.java for how far it sticks out, and
BELLY_TOP / BELLY_HEIGHT in BellyModel.java for the stomach-only shape.
Custom player model mods can read the fullness (0..1) from ClientState.bellyAmount(playerUuid).

## Sounds
The sounds are in src/main/resources/assets/gulp/sounds/ (.ogg) and wired up in sounds.json:
- Swallow: the 12 gulp*_*.ogg files (random each time) - Spit out: dropping1-3 (random) - Digestion (mob digests on its own): digestion1-3 (random)
- Digest button (stomach screen): digestion_button - Screen music (loops quietly, as background ambience, on the stomach,
  perks and settings screens): screen_loop. Its volume is VOLUME in ScreenLoopSound.java (0.0 - 1.0).
Settings > Sounds has a "Sounds: Custom / Minecraft" switch (Minecraft = the vanilla stand-in sounds), an ON/OFF
button and a 0% - 200% volume slider for each sound (100% is the middle; drag to hear a preview). Every player's
choice is their own (saved in config/gulp-client.properties).
Minecraft can't play a file louder than it was recorded, so each effect also has a "_loud" twin (+6 dB, exactly
double the amplitude) that the slider switches to above 100%. If you replace a sound, make its _loud twin too
(gain it by 6 dB through a soft limiter) and keep the same file name plus "_loud".
The Minecraft stand-in sounds can't go above 100%. The screen music is quieter by design: its 100% is BASE_VOLUME in
ScreenLoopSound.java.
To replace a sound, convert it to .ogg (mono for in-world sounds) and keep the same file name.

## Art hooks
- HUD is drawn with plain rectangles in GulpHud.java; perk icons are in PerksScreen.iconFor().

## Minecraft Comes Alive (MCA Reborn)
Optional: if MCA is installed, villagers react to what you do (nothing changes if it isn't). MCA is reached by name, not as
a build dependency, so the mod builds and runs the same either way. If MCA ever changes in a way that breaks this, the
reactions switch themselves off with one line in the log.
- Hearts and mood: swallowing a villager changes how it feels about you (MCA hearts and mood, with MCA's own particles).
  Hard mode: about -40 hearts (the villager ends up "depressed"); soft mode: about -10. Nearby villagers who see it react too
  (up to 12 within 16 blocks), and non-guard witnesses panic and run from you for a few seconds.
- Villagers who enjoy it: by MCA personality. Peppy, Confident, Athletic and Odd villagers enjoy being carried safely:
  +15 hearts and a happy mood on a soft swallow, and +5 up to +25 more when you let them out (the longer the ride, the
  more). Sensitive, Shy, Gloomy and Grumpy villagers hate it most (-25 soft, -50 hard). Everyone else dislikes it a little.
  Nobody likes hard mode, but fans mind it far less (-10).
- MCA has no "scared" or "angry" mood (its moods are depressed, sad, unhappy, passive, fine, happy, overjoyed), so fear shows as
  the lowest mood plus the panic behaviour.
- The belly and MCA's outfit: when MCA gives a player its villager look (its "villager" player model), the belly is drawn
  with MCA's own skin texture (with the villager's skin tone) and clothing texture, in MCA's order, so the outfit grows with
  the belly instead of the normal Minecraft skin poking through it. Players who aren't using MCA's villager look get the
  normal belly. (McaSkins.java; MCA's clothing sits further out than its skin, so the clothing pass uses slightly bigger meshes.)
- Digesting a villager also triggers MCA's own death rules (witnesses lose hearts, family grieves, a headstone may appear).
- Predator status: swallowing a villager adds to it (hard 15, soft 3; digesting adds about 25; fans count for less) and it
  fades by 1 every 20 seconds. It is shown on the stomach screen. At 30 or more, villagers who are afraid of you refuse to
  talk, trade or marry you ("X is too afraid of you to talk"), except villagers who enjoy it. Any villager whose hearts for
  you are -30 or lower also refuses while the status lasts. When it fades, everyone talks to you again.
- Server settings (gulp-server.toml): mcaIntegrationEnabled, mcaReactionMultiplier (0 = no reactions), mcaLockoutEnabled,
  mcaPredatorLockThreshold, mcaHeartsLockThreshold, mcaPredatorDecaySeconds, mcaVillagersPanic.

## Custom Player Models (CPM) belly sliders
Optional: if Custom Player Models is installed, your model's belly can follow how full you are. (Nothing changes without CPM.)
It runs on your own client, so it works even if the server doesn't have CPM; CPM then shares it with other players the way it
shares any animation.
- For the model's author: in the CPM editor, add animations to the model as value layers (so they can sit anywhere from
  "empty" to "full") and give them these names:
    belly        how full you are overall (creatures, plus your item storage counting half as much)
    belly_mobs   how full of creatures you are (their volume compared to your capacity)
    belly_items  how full your item storage is
  Use any of them, or all three. Each is set between 0 and the maximum the model allows for that animation (the mod reads the
  maximum from the model, so any range works). An animation the model doesn't have is skipped. Values ease toward their
  target, so the belly grows and shrinks smoothly.
- Names can be changed in config/gulp-client.properties (cpmBellyName, cpmMobsName, cpmItemsName). Settings > CPM belly
  sliders switches it off; Settings > Belly style > "Off (my model has one)" turns off the built-in bulge. If your own model
  has a "belly" animation, the built-in bulge steps aside for you automatically.
- Capital letters don't matter: CPM itself treats "Belly" and "belly" as different names, so the mod tries each name in a few
  spellings (belly, Belly, BELLY, Belly_Mobs, "Belly Mobs" ...) and also a few common alternatives (stomach, stomach_scale,
  belly_scale, tummy; stomach_mobs; stomach_items). CPM can't list a model's animations, so a slider with a completely
  different name still needs its name put in the config. The mod writes "found a CPM belly slider named ..." to the log when it finds one.
- How it's built: CPM finds the @CPMPlugin class (CpmPlugin) by itself. The few CPM signatures used are compiled against
  stand-ins in src/cpmApi (never put in the jar), so the mod builds without CPM and the real CPM classes are used at runtime.

## Multiplayer
- Everyone (server and every player) needs the mod installed. It is Forge 1.20.1 and works on dedicated servers.
- Each player has their own stomach, saved with the world. Other players see your belly and hear your sounds.
- Settings (belly style, sounds, volumes, level-up messages, action messages, perk menu) are per player and saved on
  that player's computer.
- Pets are protected: you can't swallow another player's tamed animal or horse.
- Dead and spectating players can't use the stomach; if you die, everything inside is let out.
- Server admins can edit serverconfig/gulp-server.toml (in the world folder) to: turn hard mode / digesting off
  (hardModeEnabled), scale struggle damage (struggleDamageMultiplier, 0 = never hurts), and scale XP (xpMultiplier), turn item storage off (itemStorageEnabled), turn player swallowing off
  (playerSwallowingEnabled) and set the longest a player can be held (playerMaxHoldSeconds).

## Mod info
Name: Vore Mod. Description: This Mod adds Vore to your world with progression, perks and capacity to carry mobs
wherever you want. The icon (src/main/resources/logo.png, also pack.png) is a pixel-art drumstick, the hunger icon.
Replace logo.png with your own 256x256 art any time.
