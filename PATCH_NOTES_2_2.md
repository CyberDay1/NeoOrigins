# NeoOrigins 2.2 — Patch Notes

---

## v2.2.16

> A compatibility hotfix: Dragon Survival dragons keep their growth across server rejoins, and `active_recall` sends you home to your bed or respawn anchor from any dimension.
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 26.2 (Java 25) · Minecraft 1.21.1 (Java 21)

### Bug Fixes

- **`active_recall` returns you to your bed or respawn anchor from any dimension.** The power only resolved a respawn point when you were already in the same dimension as it, so casting recall from the Nether or the End dropped you at the overworld world-spawn instead of your bed. It now uses the same respawn resolution the game runs on death: you arrive safely at your bed or charged respawn anchor wherever it sits, cross-dimension included, and because this is a repeatable recall rather than a death, the anchor keeps its charge. With no valid respawn set it still falls back to world spawn.

### Compatibility

- **Dragon Survival growth no longer resets when you rejoin a server.** With NeoOrigins installed, a dragon's accumulated growth snapped back to newborn every time the player reconnected to a dedicated server. An origin that grants the dragon form re-applied that form on every login, and each re-application re-seeded the starting growth stage. The form is now seeded only on a genuine first transformation: if you are already that dragon species when you log in, NeoOrigins leaves your Dragon Survival state untouched, so growth carries across rejoins.

---

## v2.2.15

> A hotfix: powers no longer vanish when rejoining a multiplayer server, `action_on_hit` runs the entity and bi-entity actions it always documented, and FTB Quests can be installed alongside NeoOrigins.
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 26.2 (Java 25) · Minecraft 1.21.1 (Java 21)

### Bug Fixes

- **Powers no longer disappear after rejoining a multiplayer server.** On a dedicated server a player could occasionally reconnect with their origin intact but most of their powers gone until something forced a refresh. The per-player power cache was keyed on the account alone, so a returning session matched the stale entry left by the previous one: if that entry had been built during the login window before the player's data finished loading, it was empty or partial. Each cache entry is now tied to the live player session, so a reconnect always rebuilds powers from freshly loaded data.
- **`action_on_hit` runs its `entity_action` and `bientity_action`.** The power documented an action against the entity you hit and a bi-entity action over the attacker/target pair, but neither was applied — so packs relying on an on-hit effect against the victim, like poison or knockback, saw nothing happen. Both now fire against the victim as documented.

### Compatibility

- **FTB Quests can be installed alongside NeoOrigins.** The optional FTB Quests dependency was pinned to an impossible version range, so having FTB Quests present failed the game at load. The range is corrected, and the `loot_pool_grant` power's quest-tag integration is reachable again.

---

## v2.2.14

> A hotfix for tamed-pet lifecycle and origin evolutions: pets now survive their tamer's death and persist across logout like vanilla pets, evolution tiers stop re-granting effects the base origin already has, and two item-condition gaps that broke Origins: Dietary Delights are closed.
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 26.2 (Java 25) · Minecraft 1.21.1 (Java 21)

### Bug Fixes

- **Tamed pets survive their tamer's death and recall to them on respawn.** The owner-death cleanup discarded every tracked minion, tamed pets included. Vanilla-pet semantics now apply: summoned minions still die with their summoner, but tamed pets are exempt and teleport to the tamer on respawn — cross-dimension if needed — with their loyalty AI rebound to the respawned player instead of idling against the dead one.
- **Tamed pets persist across logout and server restarts.** Tame state lived only in session memory, so a pet was orphaned the moment its tamer logged out and reverted to hostile vanilla AI when its chunk reloaded. The tame is now stored on the mob itself, vanilla-style: pets rehydrate on load, idle politely while their owner is offline, and rebind automatically on login. Summons remain session-scoped by design.
- **Evolution tiers no longer re-grant effects the origin already has.** Five built-in origins granted a redundant effect on evolution: fire resistance on blazeling, cinderborn, fire mage, and strider over their base fire immunity, and breeze T2 re-granting its own toggleable slow falling — silently defeating the toggle. The redundant grants are dropped, and blazeling T2 gains a new +4 HP step in their place so the tier isn't empty.

### Compatibility (Apoli / Origins)

- **Item conditions accept `all_of`/`any_of` and vanilla array-form `ingredient`.** Both gaps hit Origins: Dietary Delights' vegetarian power: the unrecognized `all_of` collapsed the condition to match-everything (blocking all item use), and the array-form ingredient never matched (breaking the diet exemption). The power now compiles and runs as the addon ships it, and both forms are documented with regenerated schemas.

---

## v2.2.13

> A legacy-datapack compatibility release: pre-2.2.8 sub-power ids keep their resource caps again, 1.20-era packs with plural folder names load without edits, and a batch of missing Apoli powers, actions, and conditions round out the compat layer.
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 26.2 (Java 25) · Minecraft 1.21.1 (Java 21)

### Bug Fixes

- **Legacy slash-form sub-power ids keep their resource caps.** The 2.2.11 shim resolved reads and writes for pre-2.2.8 `parent/sub` ids but not their resource min/max bounds, so `change_resource` could push a resource past its cap and permanently disable the ability (reported as a dead double-jump). Bounds now resolve through the same alias, and affected packs run correctly as downloaded.

### Compatibility (Apoli / Origins)

- **Pre-1.21 plural folder names load without edits.** Legacy datapacks using `functions/`, `predicates/`, `loot_tables/`, and friends are now served under the modern singular paths automatically — in both originpacks and world datapacks — so downloaded packs run as-is.
- **More legacy power, action, and condition types translate.** Functional `origins:cooldown` powers, `action_on_block_use` and `bonemeal` powers, positional `offset` actions, `area_of_effect` block fan-out, `offset` block conditions, `hands` filters, and inverted `effect_immunity` exception lists.
- **Item conditions got a fidelity pass.** Legacy NBT checks (potions, custom effects) now match against modern components, `amount`/`name`/`food` item checks work, `block_collision` performs a real collision test, and there is a new `origins:inventory` entity condition.
- **All of the above is documented,** with regenerated schemas for the in-game and web editors.

---

## v2.2.12

> A compatibility release: a new `origins:origin` condition and four more built-in powers so third-party addon packs load, more forgiving `/power` and `/resource` commands, and a Dragon Survival fix that stops us from blocking its appearance editor.
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 26.2 (Java 25) · Minecraft 1.21.1 (Java 21)

### Compatibility (Apoli / Origins)

- **`origins:origin` condition implemented.** A pack can now gate a power on whether the player currently holds a given origin — optionally scoped to a single layer — instead of the condition being ignored. This mirrors Apoli's own `origins:origin` entity condition, so packs that key powers off "does this player have origin X" translate correctly.
- **Four more built-in Origins powers now translate.** Addon packs that reference `origins:strong_arms`, `origins:natural_armor`, or `origins:more_exhaustion` by ID — without shipping the power themselves — now resolve to NeoOrigins equivalents (a mining-tool boost, an always-on resistance passive, and a doubled-exhaustion penalty). More of an addon's powers translate, so origins that were auto-hidden for translating too few of theirs — like the `wou` pack's rock human — now show up in the picker.
- **`/power` accepts an optional source argument.** `/power grant`, `revoke`, and `remove` now take a trailing power-source ID the way Apoli's own command does, so an addon's functions that pass one no longer fail to parse. The shim reference-counts nothing yet — the source is accepted and ignored — but the command runs instead of silently killing the rest of the function.
- **`/resource` commands accept multiple targets.** `/resource set`, `change`, and `operation` now take an entity selector that resolves to more than one player and apply to each of them, matching the multi-target behavior addon function packs expect.

### Bug Fixes

- **`origins:weak_arms` slows mining again.** The compat mapping was passing its factor under the wrong codec key, so it silently fell back to the 2× default and made weak-armed origins mine *faster* — the opposite of the intent. It now correctly halves mining speed.
- **The Flower Man addon pack loads clean.** Its abilities relied on 1.20-era `/power` and `/resource` syntax and on particle definitions that the newer NBT parser rejected; with the command shims above and the pack's own particle lists corrected, its functions register and run without load errors.
- **Dragon Survival's appearance editor opens again.** The compat suppressor was cancelling DS's `DragonEditorScreen` — the skin/appearance editor a player opens deliberately — so it could never be opened, even via `/dragon editor`. It now leaves the editor, skins, abilities, and inventory alone and only intercepts DS's *species*-choice screens. That interception is off by default too: enable `compat.suppress_dragon_species_screens` in the client config if you want the origin picker to be the sole way to choose a dragon species.

---

## v2.2.9

> A hotfix for 2.2.8: condition-gated invisibility now ends the moment its condition lapses, true invisibility hides held items along with armor, and the cooldown-icon HUD honors the "hide idle icons" setting again.
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 26.2 (Java 25) · Minecraft 1.21.1 (Java 21)

### Bug Fixes

- **Condition-gated invisibility ends when its condition does.** An `invisibility` power that turns on only while a condition holds no longer keeps you invisible for up to 15 seconds after that condition stops — it now fades within a moment of the condition lapsing, like every other gated passive.
- **True invisibility hides held items.** With `render_armor` off, the item in your hand is now hidden along with your armor and body, so a fully invisible player no longer trails a floating weapon or tool.
- **The cooldown HUD honors "hide idle icons" again.** With **always show ability icons** turned off, idle ability icons now actually disappear: the ability HUD's default mode shows recharging cooldowns and toggles only, and an idle icon stays hidden unless its power opts in with `always_show_icon` or you re-enable the always-show setting.
  - *Upgrading from an earlier version? An existing `config/neoorigins/client.toml` keeps its saved value — set `hud_ability_display = COOLDOWNS_AND_TOGGLES` (or delete the file to regenerate it) to pick up the new default.*

---

## v2.2.8

> A systems and compatibility release: a native **aura** power, entity filtering for area effects, four more native powers (invisibility, item gating, flight-speed scaling, sound muffling), a batch of Apoli/Origins compat fixes, author-controlled origin-picker order, evolution-tier fixes, and phasing/respawn/health polish.
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 26.2 (Java 25) · Minecraft 1.21.1 (Java 21)

### New Powers & Systems

- **`effect_over_time` aura power.** A native sustained area-of-effect power that runs an action on a fixed `interval` for as long as it is active. Set `activation` to `passive` for an always-on aura or to an active toggle for an upkeep-style aura, and mark it `toggleable` so players can switch it on and off. Pair it with `area_of_effect` to wrap nearby entities in regeneration, particles, or any action you like — the basis for healing auras, damage fields, and persistent buffs.
- **Entity filtering on `area_of_effect`.** Area effects now accept an `entity_condition` that filters *every* entity in the radius — mobs and players alike — so an aura can target only players, only hostiles, only a tagged entity type, and so on. This makes auras like a heal-allies-but-ignore-mobs field possible without touching the action itself.
- **`invisibility` power.** A native invisibility power with an optional `render_armor` flag to hide worn armor along with the body, instead of routing through the status-effect compat layer.
- **`restrict_items` power.** A modular item-gate that can block equipping, using, or totem-popping items, driven by either a deny-list or an allow-list — for origins that can't wield certain gear.
- **`modify_flight_speed` power.** Scales a flying player's speed by a multiplier, a native replacement for the Pehkui-based approach.
- **`muffle_sound` power.** A native power that dampens sound for the holder.
- **`distance_from_coordinates` condition.** Tests the player's distance from a reference point — world origin or world spawn, plus an optional per-axis offset — under a cube (Chebyshev), star (Manhattan), or sphere (Euclidean) metric, with optional per-axis exclusion and an off-dimension fallback. An Apoli-compatible condition for proximity-gated powers.

### Compatibility (Apoli / Origins)

- **`origins:phasing` is now server-authoritative,** with its `in_block` gate honoring block tags and inverted conditions correctly.
- **`origins:nbt` partial-NBT matching implemented** — the condition now parses SNBT and matches against an entity's `Tags`, instead of being ignored.
- **Fixed Apoli `multiple` sub-power desync** where synthetic sub-power IDs containing a slash drifted out of sync with the client; slashes are now normalized to underscores on both sides.
- **AvP (Aliens vs Predator) soft-dependency compat** — a new `xeno_passive` host gate, plus registration fixes for `xeno_passive` and `creative_flight` so both load reliably.
- **`/scale` shim for Pehkui-dependent packs.** When Pehkui is not installed, a stand-in `/scale` command is registered so Origins/Apoli packs that call it (for example the seer astral origin's init functions) still parse and run the rest of their commands instead of silently failing to load.

### Origin Picker & Commands

- **Author-set picker order is honored.** The origin picker can now sort by the `order` field an author assigns, so packs control exactly where each origin appears in the list.
- **`/origin` is a real command alias.** It is now registered as a proper command literal rather than a loose alias, so it tab-completes and parses like the full command.

### Bug Fixes

- **Boosted max health is restored to full on respawn.** Origins that raise max HP no longer leave the player short after dying.
- **Newly granted hearts fill immediately.** When a +HP origin is granted, the new hearts are filled rather than left empty.
- **In-game power editor shows name and description fields again.** Both fields were missing from the editor and are now back.
- **Phasing no longer ghost-blocks or grants spam-jump flight.** Wraith-phase / astral projection now drives vertical movement with velocity nudges (jump up, sneak down, neither settles slowly) instead of arming creative flight, and keeps the server and client agreeing on position — so block placement raytraces correctly and players can no longer rise freely by spamming jump.
- **Evolution tiers no longer double-stack lower-tier buffs.** Advancing a tier now removes the previous overlay (night vision, slow fall) before granting the upgraded one, so effects like night vision are no longer applied twice at tier 2+.
- **Evolution tier force-set applies powers properly.** Setting an evolution tier from a command now grants/revokes tier powers and reconciles max health the same way the accept-prompt path does, instead of only changing the tier field until the next relog.
- **Evolution panel shows tier-power names and descriptions.** The origin picker's evolution section now resolves each tier power's name and description, collapsing a `multiple` power's synthetic sub-powers into a single authored row instead of showing blank entries.

### Assets

- **Community resource-bar GUI textures** contributed by huang and spiderkolo are now bundled.

---

## v2.2.7

> A big content update built around **six brand-new martial-arts origins** — each with a full kit and a three-tier evolution path that unlocks a new signature art as you advance. Underpinning them is a wave of new systems: a `variable` power type, true creative-style flight, the ability to bind powers to vanilla keys, a modular projectile-rain / telegraph / thrown-sword VFX toolkit, an on-hit event hook, and global vision toggles — plus the usual polish and fixes.
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 26.2 (Java 25) · Minecraft 1.21.1 (Java 21)

### New Origins

Six new origins join the roster, each a self-contained eastern martial-arts fantasy with its own base kit *and* a three-tier evolution path (see below):

- **Asura** *(high impact)* — A frail-bodied berserker who grows faster and stronger the closer death looms: fragile when whole, monstrous when bleeding.
- **Windwalker** *(medium)* — A master of *qinggong*: steps off the air, scales sheer walls, dashes on the wind and never fears a fall, but carries no extra strength into a fight.
- **Qi Cultivator** *(medium)* — Channels Qi into ranged palm-strikes and hardened flesh, meditating to refill reserves — but a cultivator run dry is just a monk.
- **Golden Body** *(medium)* — A hard-qigong Iron Body master: an armored, immovable wall that punishes anyone who strikes it, at the cost of nimbleness.
- **Iron Monk** *(medium)* — A defender monk whose guard, blocks, and strikes all draw on one shared pool of stamina; an exhausted Iron Monk is wide open.
- **Sword Immortal** *(high impact)* — Flies on their blade, hurls sword-qi at range and blinks through battle; every signature art is bound to the sword in hand.

### Origin Evolutions

- **Origins can now evolve.** An origin can define an evolution path that grants new powers as the player advances through its tiers, so an origin grows into a distinct late-game shape instead of staying fixed. All six new origins ship with a three-stage path:
  - **Asura** — *Undying Rage* (regenerate and shrug off fire while near death) → *Blood Tithe* (drain life from every blow you land) → *Wrath Eruption* (an on-demand shockwave that savages and flings everything around you).
  - **Windwalker** — *Sky Dancer* (glide freely on the wind) → *Riding the Wind* (true flight) → *Eye of the Storm* (a cyclone far greater than the Typhoon).
  - **Qi Cultivator** — *Dantian Expansion* (Qi circulates even while you move and fight) → *Core Pressure* (strike harder while your core runs deep) → *Flying Sword of Qi* (loose a blade of pure energy).
  - **Golden Body** — *Diamond Body* (harder still to wound) → *Reflected Force* (rebound twice the force of every blow) → *Bell Toll* (a resonating shockwave that slows and weakens nearby foes).
  - **Iron Monk** — *Counter Stance* (blocked blows rebound on the attacker) → *Sea of Stamina* (stamina trickles back even mid-guard) → *Lohan Palm* (a wide stamina-fueled shockwave).
  - **Sword Immortal** — *Sword Heart Unity* (your edge bites deeper with a sword in hand) → *Heavenly Sword Formation* (rain a ring of blades around you) → *Heaven-Severing Slash* (one gathered, cleaving sword-qi crescent).

### New Powers & Systems

- **`variable` power type.** A named, hidden counter that persists across sessions, with no HUD bar, regen, or per-tick cost — it only changes when actions touch it. Use it as a hidden state-tracker for charges, stacks, or progress that resources aren't suited to.
- **`creative_flight` power.** Grants true creative-style hover flight while active and cleanly restores normal movement when toggled off (without stripping flight from players already in creative/spectator) — built for "levitating cultivator" fantasies, distinct from elytra and natural glide.
- **Bind powers to vanilla keys.** Active and reactive powers can now key off vanilla inputs — jump, sneak, sprint, use, attack, and the four movement keys (forward / back / left / right) — instead of consuming a named hotkey slot, so lightweight powers like double-jump fire on the keys players already use. Held `key.use` / `key.attack` powers now also fire correctly while the key is held down, where before they only triggered on an actual interaction or attack swing. A worked `double_jump` example ships in the example pack.
- **`hit_dealt` event + `hit_dealt_amount` condition.** Powers can react to the damage *you deal* — mirroring the existing hit-taken hook — so `action_on_event` can fire on a landed hit (used for life-drain) and conditions can test how hard a blow landed.
- **`active_dash` damage scaling.** Dash powers gain `damage`, `damage_radius`, and `weapon_damage_scale` fields, so a dash can cut through the path it travels and scale with the weapon in hand.

### New VFX & Actions

- **Modular projectile-rain (`spawn_projectile_rain` / `spawn_sword_rain`).** Rain projectiles or baked-mesh blades down over an area, with configurable radius, count, duration, damage, knockup, a per-landing `impact_action`, and custom projectile entities or spectral-sword models. The rain can be anchored to the caster, their look direction, or an impact point.
- **`spawn_telegraph` action.** A particle-only ground danger reticle (a contracting circle) with a configurable radius and wind-up, optionally firing an action when it expires — a clean wind-up cue for telegraphed attacks.
- **Thrown-sword and componentized tornado VFX.** New baked-mesh model support drives a thrown-sword storm and a rebuilt, component-based tornado (with a proper spectral-sword model and updated tornado mesh/texture).

### Configuration

- **Global vision toggles.** Two new independent `content.toml` switches for packs that want darkness to stay threatening: `disable_night_vision` strips the `minecraft:night_vision` effect from any power that would apply it (other effects on that power still apply), and `disable_enhanced_vision` withholds the `enhanced_vision` low-light brightness boost (cat eyes, salamander, oculus drone, etc.) so it never reaches the client.

### Bug Fixes

- **Fixed a dedicated-server crash on load when walk-on-water / walk-on-lava was present.** The `walk_on_fluid` mixins named a client-only player type directly, so on a dedicated server Mixin tried to load that client class while weaving and aborted with "invalid dist DEDICATED_SERVER" — taking the whole server down at startup whenever a pack used walk-on-water or walk-on-lava. The local-player check is now routed through a client-only helper, so the client type never touches the server's load path.
- **The Windwalker Typhoon (and any moving VFX) now travels smoothly.** The tornado's position only synced to clients every several ticks, so a drifting funnel visibly jumped block to block. Moving storm entities now sync every tick, so the client smoothly interpolates the funnel between updates instead of snapping it, and it follows the ground beneath it as it drifts.
- **Hidden resource bars stay hidden.** A resource power marked `hidden` at the top level (such as the Windwalker's air-step counter) still leaked a "Resource 1/2" bar onto the HUD, because the loader only auto-hid simple on/off resources. The top-level `hidden` flag is now honored, so the bar no longer shows.

### Polish

- **Every active ability now shows a cooldown icon.** The last few actives without an explicit `cooldown_icon` (Windwalker's Gale Dash, the Sword Immortal's Flickering Slash, and the eastern martial-arts actives) now display a proper item icon on the cooldown HUD instead of the fallback drain bar.

---

## v2.2.6

> A hotfix for 2.2.5: lava/water walking now works on flowing fluid and stands you
> cleanly on the surface, summoned piglin/hoglin minions properly chase and follow
> their owner, and `/power grant|revoke` works on grouped (`multiple`) powers.
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 26.2 (Java 25) · Minecraft 1.21.1 (Java 21)

### Bug Fixes

- **Walk-on-lava / walk-on-water works on flowing fluid and stands you on the surface.** `walk_on_fluid` only solidified *source* blocks, so poured or flowing lava dropped you straight through, and even on a source block you sank knee-deep and bobbed. Any fluid level is now a solid surface and you stand on a full block with your feet clear — jumping works and you no longer sink, while submerging your eyes still lets you dive.
- **Summoned and tamed piglin/hoglin minions chase and follow their owner.** Brain-driven minions ran only on the goal system that 2.2.4 pacified, so they neither chased the owner's target nor leashed back until the owner walked right up to them. They now drive movement through the brain — chasing a live combat target, otherwise following the owner (the same 8-block follow / 24-block teleport distances as goal-based pets) — and no longer turn on a fellow minion the owner clips by accident.
- **`/power grant` and `/power revoke` work on `multiple` powers.** A `multiple` power isn't itself a registered holder, so granting or revoking the parent id silently did nothing. The command now expands a `multiple` to its leaf sub-powers (recursively, for nested multiples) and grants/revokes each.

### Compat Improvements

- **`neoorigins:self_action_on_hit` is recognized** alongside the existing `origins:` / `apace:` `self_action_on_hit` aliases, so a power authored under the native namespace fires when the holder deals damage instead of being dropped at load.

---

## v2.2.5

> Adds a Build A Spell integration, two new powers, a big client-config expansion for the HUD ability display, an array-form authoring fix, and a fresh **Minecraft 26.2** build alongside the existing 26.1.x and 1.21.1 ones.
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 26.2 (Java 25) · Minecraft 1.21.1 (Java 21)

### New Powers & Systems

- **Build A Spell integration — `neoorigins:cast_spell`.** A new power that casts an inline Build A Spell spell when activated. Spells are defined effect-first as components on the power, so they don't require a pre-registered BaS spell item, and the cast is **mod-gated via `required_mods`** — packs that ship it stay loadable when Build A Spell isn't installed. Cost is charged on the NeoOrigins power (resource/hunger/cooldown), so the spell fires regardless of BaS's own mana system. (Build A Spell recently collapsed its mod id / package to the single token `buildaspell`; the integration targets that renamed coordinate.)
- **`attract_mobs` power.** Pulls nearby mobs toward the holder, backed by a new `AvoidEntityGoalMixin` so mobs that would normally flee can be drawn in instead.
- **`prevent_item_damage` power.** Stops items from losing durability under the power's conditions, via a new `ItemStackHurtAndBreakMixin`.
- **`tame_target` action verb.** Tames the entity on the other side of an interaction (the context target), complementing the existing `tame_mob` power.

### Pack Author Features

- **Action/condition fields accept an object *or* an array.** A field like `entity_action` / `condition` can now be written as a single object or as a bare array (an implicit "all-of"/AND). Previously a bare array silently no-opped, so packs that authored these as lists lost the behavior with no error.
- **`summon_minion` gains attribute & enchantment fields** for the summoned minions, alongside the existing equipment/mount options.
- **`disable_hotkey` precedence clarified** so a hotkey-disabling setting wins consistently over a power's own bind.

### Client Configuration

- **Expanded HUD ability-display options.** New client-config controls over the on-HUD ability cluster — including cooldown-countdown opacity and the default origin sort order — with the full surface documented in the new `CLIENT_CONFIG.md` reference.

### Bug Fixes

- **Oceanic conduit/apex powers are usable on land again.** Powers built on vanilla's `conduit_power` were water-gated, so the affected ocean origins' abilities were effectively dead out of water. They've been rebuilt on land-usable effects, so the ability works regardless of whether the player is submerged.

### Minecraft 26.2 Support

- **A native Minecraft 26.2 build joins the release.** NeoOrigins now ships a third jar built for MC 26.2 (NeoForge, Java 25), in addition to the 26.1.x and 1.21.1 jars. Pick the jar matching your Minecraft version.
- **Note on 26.2 mod integrations.** A few soft-dependency integrations (JEI, Jade, FTB) aren't available on the 26.2 build yet, pending those mods publishing 26.2 versions — the core mod and the Build A Spell integration are fully present. They'll be restored on 26.2 as upstream catches up; the 26.1.x and 1.21.1 builds are unaffected.

### Localization

- **All 12 locale files updated** for the new powers, fields, and client-config strings.

### Documentation

- **`cast_spell`, `attract_mobs`, `prevent_item_damage`, the `tame_target` verb, the array-or-object field form, the new `summon_minion` fields, and the client-config options documented** across `ACTIONS.md`, `API.md`, `CONDITIONS.md`, `POWER_TYPES.md`, and the new `CLIENT_CONFIG.md`, with the power/action schemas and the web-editor schema mirror regenerated.

---

## v2.2.4

> A bug-fix and compat follow-up to 2.2.3, driven by Discord reports: a fix for the 26.1 mod-loading warning, an area-of-effect option for `tame_mob`, a batch of power fixes, and better Apoli compatibility.
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 1.21.1 (Java 21)

### Pack Author Features

- **Native `neoorigins:multiple` container.** You can now bundle sub-powers under a first-class in-namespace `neoorigins:multiple` instead of reaching for the foreign `origins:multiple`. It flattens through the exact same path: each sub-power object becomes a standalone `<ns>:<parent>/<subkey>` power, nested multiples and `*:*` self-refs included. `origins:` / `apace:` / `apoli:multiple` still work identically.
- **`summon_minion` can mount its minions.** A new optional `mount` field seats each summoned minion on its own freshly-spawned mount (e.g. piglin cavalry on hoglins): the mount is forced, tracked under the same cap, drops nothing, and despawns with its rider. Equipment is applied to the rider only.
- **`tame_mob` gains an area-of-effect mode.** Alongside the default raycast, `targeting: area` tames every eligible mob in range at once, greedily nearest-first until the resource or the minion slots run out. An optional `entity_whitelist` (entity ids and `#tags`) narrows what it will tame, on top of the existing blacklist and boss-tier exclusions. Cost is now charged **per mob** from whatever resource the power is given (`resource_cost` / `resource_cost_amount`), falling back to hunger when resource bars are disabled.

### Bug Fixes

- **The 26.1 "@OnlyIn" loading warning is gone (26.1).** On NeoForge 26.1.2 the client popped a "Warning while loading mods" gate before the main menu, because five client renderers carried a leftover `@OnlyIn` annotation that 26.1 no longer strips. The annotation is removed; the client boots straight to the title screen. (1.21.1 never showed the warning, but it's cleaned up there too for parity.)
- **First-pick invulnerability covers the whole pick, plus a brief grace after.** New players are protected from damage for the entire initial origin selection — every layer, so a multi-layer (origin + class) pick no longer leaves you vulnerable while you're still choosing your class — and stay invulnerable for about 5 seconds after committing, so you don't eat a hit the instant you spawn in (e.g. when a `spawn_location` origin teleports you somewhere hostile). Escaping the picker mid-pick still drops the protection, and Orb-of-Origin re-picks don't qualify.
- **`modify_food_nutrition` fills the hunger bar fully.** The power recomputed the bar from a post-eat delta, which over-corrected once vanilla clamped the gain at 20, so food "wouldn't fill the bar all the way." It now snapshots food + saturation before the bite and recomputes the bar absolutely from that baseline.
- **`tame_mob` drops the new owner as a target.** A mob tamed mid-swing kept attacking its new owner until it lost sight of them. Taming now clears the mob's current target and last-hurt-by record if they point at the owner.
- **`summon_minion` pacifies brain mobs.** Piglins and hoglins run on the Brain/memory system, not goals, so they bypassed the goal-based target interceptor and re-armed against the summoner. They're now pacified at spawn and held friendly each tick (anger and attack-target memories toward the owner are cleared, zombification immunity set).
- **`keep_inventory` covers Curios and Accessories (1.21.1).** `neoorigins:keep_inventory` only walked vanilla inventory slots, so trinkets in Curios / Accessories slots dropped on death. It now matches accessory slots too (`*` / `all`, the `curio` / `accessory` / `trinket` umbrellas, or a specific slot id) and re-equips kept trinkets into their original slot on respawn.
- **Long subclass names no longer overflow the picker scroll.** Two-word origin / subclass names (e.g. a datapack's "Fire Wizard") ran off the right edge of the parchment scroll button. They now scale down to fit the scroll's inner area; names that already fit render unchanged.

### Compat Improvements

- **`key.jump` double-jumps fire correctly.** A `key.jump` `active_self` proxy mis-detected the gesture — it fired during a normal jump's ascent and was inactive at the moment you re-pressed jump while falling, so double-jump spells never triggered. It now reads the real airborne jump-press edge, so the action runs once per double-jump press.
- **`ADD_MULTIPLIED_TOTAL` attribute ops are no longer demoted to a flat add.** The uppercase NeoForge-enum form of the operation fell through to `add_value`, silently turning a multiplier into a flat addition (corrupting momentum / gravity scaling). Operations are now case-normalized, and the known-unrepresentable `set_*` / `min_*` / `max_*` ops fall back cleanly without the misleading "unknown operation" warning.
- **`modify_fall_damage` / fall-damage `conditioned_attribute` load again.** With no `modify_fall_damage` handler in the compat layer, a `conditioned_attribute` targeting fall damage returned null and the loader silently dropped the **whole** power. A full handler is added (reusing the existing native fall-damage hook), and `conditioned_attribute` forwards a `fall_damage` leaf to it instead of dropping the power.

### Documentation

- **`neoorigins:multiple`, `summon_minion` `mount`, the `tame_mob` `targeting` / `entity_whitelist` / `resource_cost` fields, the compat `modify_fall_damage` verb, and the `keep_inventory` `slots` field documented** in `POWER_TYPES.md`, with the power schema + web-editor mirror regenerated.

---

## v2.2.3

> A focused follow-up to 2.2.2: a few quality-of-life features and a batch of bug fixes, several of them from tester reports against the compat layer and the event system.
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 1.21.1 (Java 21)

### New Powers & Systems

- **Browse every origin on the info screen.** The O-info screen now pages through all available origins in a layer with the new `</>` buttons or the left/right arrow keys, defaulting to your own pick. A "Your Origin" marker and an N/M position indicator show where you are; live evolution progress stays gated to your own origin, while other origins show only their static evolution path.
- **`fail_action` on blocked abilities.** `active_ability` (and the compat `active_self` / toggle / launch paths) accept an optional `fail_action` that fires when a key press is blocked by the power's condition gate, replacing the old silent no-op. Cooldown and hunger/resource aborts stay silent and never consume the cooldown; continuous binds edge-detect so the feedback fires once per press.
- **New `activate_power` action.** Triggers another power's activation path by id — skill slots and named hotkeys — with recursion detection.

### Pack Author Features

- **Flat reach for shrunk origins (`reach_bonus`).** `size_scaling` gains a `reach_bonus` field decoupled from body scale, and `scale` / `modify_reach` / `reach_bonus` are now exposed per-origin in `power_overrides.toml`. The built-in small origins (Inchling, Tiny) now ship with `modify_reach: false` and a positive `reach_bonus`, so they stay playable out of the box instead of having their reach shrink with their hitbox.
- **Toggle HUD-icon fields exposed on `persistent_effect` / `condition_passive`.** Both toggleable types already supported a HUD icon, but their FieldSpecs omitted `toggle_icon` / `always_show_icon`, so the web form and schema gave authors no way to configure it. They're now in the schema.

### Bug Fixes

- **`prevent_item_use` now catches instant-use items.** `LivingEntityUseItemEvent.Start` only fires for use-duration items, so fireworks, ender pearls, snowballs and other instant items slipped past `prevent_item_use` and the `item_use` event. New right-click handlers (air + block-aimed, gated on zero use-duration) close the gap; the block path denies only the item's `useOn`, so normal block interaction (chests, doors) still works.
- **`item_action` and `slot` are honored in compat.** `equipped_item_action` only read the undocumented `action` key, so authors following the docs (which say `item_action`) hit a silent no-op — it now prefers `item_action` and keeps `action` as a legacy alias. `modify_inventory` documented a `slot` field it never read, so a slot-scoped consume destroyed matching items inventory-wide; `slot` is now honored (equipment names or a raw inventory index).
- **`summon_minion` quantity works.** The `quantity` field was advertised but only ever lived on `spawn_entity`; `summon_minion` ignored it and spawned a single mob. It now spawns up to the requested count (capped by `max_count` headroom) with position jitter, plays the summon sound, and charges hunger once per activation.
- **`mod_anvil_cost` actually applies.** NeoForge's `AnvilUpdateEvent` discards a cost-only change unless a listener also sets an output, so the handler was a guaranteed no-op. It now runs from an `AnvilMenu` mixin, so the adjusted level cost is both displayed and charged. (Known limit: vanilla's "Too Expensive!" cap is still measured against the undiscounted cost, so a discount can't rescue an operation vanilla refuses outright.)
- **BREED events fire at the right position.** `BabyEntitySpawnEvent` fires before vanilla moves the offspring to its parent, so the child still sat at (0,0,0) — `twin_breeding` dropped every twin at the world origin, and BREED distance / `can_see` conditions and positional `target_action`s evaluated against (0,0,0). The child is now pre-positioned at the parent before the BREED dispatch, and the twin anchors on the parent independently.
- **Slime-HP schema keys match the codec.** The FieldSpecs read `split_max_h_p` / `levels_per_h_p` / `max_bonus_h_p` while the codecs parse `split_max_hp` / `levels_per_hp` / `max_bonus_hp`, so authors using the documented keys got silent defaults. The keys now line up.
- **Font license no longer logs an invalid-path error.** The bundled `OFL.txt` sat under `assets/.../font/` with an uppercase name, which isn't a valid resource path, so the client logged an "Invalid path in pack" error on every load. Renamed to lowercase — the license still ships with the font, the error is gone.

### Documentation

- **Origin `spawn_location` is now documented in `PACK_FORMAT.md`** — the LocationCondition shape, when it fires (first pick + bedless respawn), the `modify_player_spawn` alternative for every-death control, and the `[spawn_location] teleports_enabled` kill switch.
- **`tier_powers` added to the PACK_FORMAT origin field table** (it was already covered in `EVOLUTION.md` and the cookbook).
- **`reach_bonus` mentioned in the API table** and the `size_scaling` tip.
- **`active_teleport` / `active_swap` docs corrected** — both codecs read `range`, not `max_distance`, and `active_swap`'s default is 20 (the docs said 16).

---

## v2.2.2

> A big quality pass: a dragon morph system, animated resource bars, eleven languages, inline validation in all three editors, and a long list of compat and bug fixes driven by tester reports. Thanks to everyone filing issues — most of what's below started as one.
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 1.21.1 (Java 21)

### New Powers & Systems

- **Dragon morph system.** New `entity_model` and `become_dragon` powers: morph the player into another entity's model, including a full dragon form with its own movement.
- **Animated resource-bar FX.** Resource bars can now play datapack-driven animated presets (fire, pulse, shimmer) instead of a flat fill.
- **`power_activated` event.** Powers can now react to another power's successful activation: chain follow-up effects, costs, or cooldown displays off the trigger power.
- **`required_mods` gate.** Origins and powers can declare soft mod dependencies: if the listed mods aren't installed, the entry is skipped cleanly instead of erroring.
- **`action_on_event` gains `cooldown_ticks`.** After the action fires, the power goes inert for the configured ticks: per-player, survives relog, and never shared between two powers of the same type.
- **`tame_mob` entity blacklist.** Per-power `entity_blacklist` (entity ids and `#tags`), a global `tame_scare_entity_blacklist` config, and a built-in boss-tier exclusion (Warden, Ender Dragon, Wither) — the same exclusion list now also gates `scare_entities` and `mobs_ignore_player`.
- **Warden dark-vision is now toggleable and config-disableable.** The Warden's persistent night-vision, tremor-sense, and echolocation powers can be turned on/off in-game (`toggleable`), and each can be force-disabled in the config for servers that don't want them.
- **Draconic sees through lava.** Lava vision without the fog and fire overlay, and Molten Stride retuned to water-swim pace now that the lava-speed fix actually applies.
- **The Slime origin actually bounces now.** The lang text always promised it; the power finally delivers (see Bug Fixes).
- **Ability icons on the HUD.** Active powers can now declare a `cooldown_icon` (an item id, or a datapack texture ending in `.png`): the HUD slot swaps its drain bar for the 16×16 icon under a clock-style radial sweep, with a translucent seconds readout, on by default: opacity and visibility are client-configurable, and packs can opt a power out via `cooldown_countdown`. All 61 built-in active and toggleable abilities ship with fitting vanilla-item icons out of the box. Toggleable powers join the cluster too — full-bright while on, dimmed while off — and by default every ability with a hotkey or toggle is visible (a client option can restrict it to cooldowns and toggles only). Icon slots are labeled with the actual bound hotkey — and the origin picker and O-info screens tag each ability with its key too — abilities can opt into staying visible off cooldown (`always_show_icon`, or force it client-side), hovering an icon shows the power's name and description whenever a cursor is available, and the whole cluster is draggable in the HUD editor. Iconless powers keep the classic bars.
- **The ability cluster is yours to arrange.** In the HUD editor you can rotate the cluster between horizontal and vertical, split it into individually placeable icons (and merge it back — placements are remembered), and resize any element from 50% to 200%. Everything is saved locally and sticks across sessions.

### Pack Author Features

- **`power_condition` / `power_condition_mode` are now documented, and plain `condition` works as an alias.** Every power supports a whole-power runtime gate. On power types that don't claim `condition` for their own config, a top-level `condition` is accepted as an alias for `power_condition` (active while the condition holds). Previously an unrecognized top-level `condition` was silently ignored — now it either works or warns, never disappears.
- **Inline validation in all three editors.** The in-game origin creator, the in-game mob-origin creator, and the web editor all validate as you type: required fields, malformed numbers and ranges, bad ids, and raw-JSON errors are flagged on the field with a reason, and saving/exporting is blocked with an explanation while real problems remain.
- **Web editor: export is gated on valid ids.** Blank or duplicate namespace/path/power ids now block export with a clear list instead of producing a broken datapack.
- **Web editor: power-type search.** A filter box above the type picker — type a substring, press Enter to select the only match.
- **New powers default to `neoorigins:attribute_modifier`** in the web editor instead of the first alphabetical type.
- **Custom resource-bar art is now documented.** The animated-bar artist spec gains a `sprite_location` section covering custom `resource_bar.png` sheets: `bar_index` / `icon_index` and the exact sprite strides, so packs can ship their own bar art instead of recoloring the built-in sheet.

### Configuration

- **The config is now a folder.** The two monolithic config files are split up under `config/neoorigins/`: `gameplay.toml`, `admin.toml`, `power_overrides.toml` (all the per-power tuning sections, now in a file of their own), `client.toml`, plus per-world `serverconfig/neoorigins/content.toml` for the origin and class toggles. Existing configs migrate automatically on first boot: hand-tuned values carry over and the old files are renamed `*.toml.migrated`.
- **Origin spawn teleports now have a master switch.** Some origins relocate the player when first picked (ocean origins to an ocean biome, for example). Setting `teleports_enabled = false` under the new `[spawn_location]` section in `gameplay.toml` shuts all of these off at once: built-in, datapack and imported origins alike spawn at the world's normal spawn point.

### Localization

- **NeoOrigins is now accepting community translations.** A first Russian (`ru_ru`) translation has landed, contributed by [@Nienya972](https://github.com/Nienya972) — thank you! To contribute a language, open a pull request (or an issue with the file attached) adding a `<locale>.json` to `src/main/resources/assets/neoorigins/lang/`. Missing keys fall back to English, so partial translations are welcome too.
- **Ten machine-translated languages.** Simplified Chinese (`zh_cn`), Spanish (`es_es`), German (`de_de`), Brazilian Portuguese (`pt_br`), French (`fr_fr`), Italian (`it_it`), Polish (`pl_pl`), Ukrainian (`uk_ua`), Japanese (`ja_jp`), and Korean (`ko_kr`), all at full key coverage. These are unreviewed machine translations meant as a head start — native-speaker corrections are especially appreciated.

### Compat Improvements

- **`origins:all_of` / `origins:any_of` are now supported** as aliases of `and` / `or` (the Apoli 2.9+ renames), for conditions, actions, and bare block combinators. Previously these failed closed and disabled every power using them.
- **Unknown top-level keys survive translation.** The origin normalizer now passes through everything it doesn't explicitly handle (so `tier_powers`, `required_mods`, and `spawn_location` survive), and power translation preserves `required_mods`, `badges`, `loading_priority`, and `power_condition`/`power_condition_mode`. An Apoli-style top-level `condition` hoists to `power_condition` automatically.
- **`apoli:`-prefixed types canonicalize everywhere** — power types, the `multiple` expander, and the loader, not just action/condition parsing.
- **`action_over_time` `rising_action` / `falling_action` now fire** on the condition's false→true / true→false edges. These fields validated fine but silently never ran.
- **Resource wildcards.** `*`-glob resource ids work in resource conditions and in `change_resource` / `set_resource` (with the Apoli operation vocabulary), matching Apoli's behavior for packs that fan out over generated resources.
- **New compat conditions:** `creative_mode` and `cover` (alias `covered_by_block`).
- **Multi-modifier Apoli attribute powers split correctly** instead of only applying their first modifier.
- **Array-form action and condition fields now compile.** Apoli lets a single `entity_action` / `condition` field be written as either one object or an array (an implicit "all-of" / AND). The Route B power compiler assumed an object everywhere, so packs that authored these as arrays hit a cast error and failed to load. Array and single-object forms are now both accepted across the compiled power types.
- **`command` accepted as an alias of `execute_command`** in action dispatch.

### Bug Fixes

- **Powers no longer go dead after a server restart (#99).** A login-ordering race wiped hotkey assignments for active powers; toggles and abilities now survive restart + relog.
- **`modify_lava_speed` works again (#102).** The movement mixin targeted a method that no longer carried lava travel; retargeted so lava swim speed applies.
- **Slime bounces (#102).** `bounce_on_land` measured server-side velocity, which is always ~0 for client-controlled players, so the bounce never triggered. It now measures actual per-tick fall distance — and the built-in Slime origin gained the bounce power it always claimed to have.
- **Blacksmith quality survives smithing upgrades (#103).** Durability and bonus attributes are recomputed from the item prototype on upgrade instead of being locked or lost.
- **`cancel_event` actually cancels.** On `hit_taken`, `land`, `item_use`, and the interact events (`villager_interact`, `entity_use`, `block_use`, `breed`, `tame`, `bonemeal`), the cancellable event is now carried through to the action — previously cancellation was a silent no-op on these.
- **Wraith phasing respects its block blacklist again.** The client-side check only knew about bedrock (so obsidian never blocked phasing), and a server override switched collision back off right after the blacklist turned it on — both fixed, and the full `blocked_blocks` list now syncs to the client. Also closed a hole where spam-jumping while phased on the surface could latch vanilla flight on.
- **The gravity well's black hole actually renders now.** The VFX model sampled a fully transparent corner texel of its texture for every one of its 5,781 cubes, so the whole effect drew invisibly — the pull, damage, and sound were real, the sphere just wasn't there. Sampling now lands on the painted area.
- **`enhanced_vision` works in production builds.** The lightmap hook silently no-op'd outside dev because it matched on a compiled-away variable name; retargeted to stable method calls.
- **In-game power editing no longer drops hand-authored JSON.** Editing a power in the form view used to rebuild its JSON from modeled fields only, deleting things like `name`, `description`, `hidden`, and any extra keys — edits now patch only the fields they own.
- **Web editor: fixed a crash selecting `neoorigins:resource`,** powers rendering as condition blocks after a Blocks⇄Form round-trip, condition defaults leaking into exported power JSON, `should_render` flipping itself off in a round-trip, and the Identity "Hidden" checkbox exporting a key that the compat detector misread as an Origins-format pack (it folds into `unchoosable` now).

### Performance

- **Resource sync split into value-only ticks** with full metadata only at chokepoints — much less network traffic for resource-heavy packs.
- **Power JSON snapshots are no longer deep-copied eagerly on reload.**
- **Parchment panel texture cut from 28.1 MiB to 7.7 MiB** (lossless compress + downscale).

---

## v2.2.1

### Compat Improvements

- **`origins:recipe` craft-gating now works on Minecraft 26.1.** 26.1 made the recipe map immutable and dropped the `replaceRecipes` hook 1.21.1 relies on, so inline and referenced `origins:recipe` powers were globally craftable. A new RecipeManager mixin restores the replace path: every gated recipe is wrapped with a `has_power` check keyed to the owning power, so only holders can craft it — exact parity with 1.21.1.
- **`origins:simple` / `origins:tooltip` now show their name and description.** These display-only powers were being skipped during compat translation, so they rendered blank in the power list. They now translate to `neoorigins:simple` and carry their text through.
- **`origins:aqua_affinity` maps to the speed-based underwater mining power.** It now cancels the submerged mining-speed penalty through the vanilla `minecraft:submerged_mining_speed` attribute — positional and client-synced by vanilla, so Aqua Affinity behaves like the real enchantment instead of no-op'ing.
- **`self_action_on_hit` / `action_on_hit` fire when the holder deals damage.** The victim is passed directly as the target, so wrapped bientity actions resolve (actor = holder, target = victim) instead of silently doing nothing.
- **The `hidden` flag survives compat translation.** Powers flagged hidden in the original Origins JSON stay hidden in the power list after translation.
- **(26.1) `food_item_in_tag` strips a leading `#`,** so tag-based diets (fish, etc.) match.

### Bug Fixes

- **The View Origin scrollbar is now grab-and-drag.** Click-drag the thumb, or click the track to page to that spot — previously the bar was display-only.
- **Hidden powers stay out of the power list but remain command-targetable.** The power grant/revoke autocomplete now lists all powers (including hidden ones), so you can still administer them.
- **First-pick invulnerability clears once an origin is committed,** instead of lingering after the pick.
- **`neoorigins:and` in an AoE applies wrapped damage/effects to mobs,** not just players.
- **`neoorigins:action_over_time` alias registered;** corrected a couple of phantom entries in the power-type docs.
- **Save-validator loader-attribute prefix tolerance,** so packs authored against either loader's attribute prefixing validate cleanly.

### New / Changed Powers

- **`orb_of_origins.scale_cost`** — flat XP cost option for the orb.

---

## v2.2.0

> This release is a large compatibility + UI pass — a major Origins / Apoli / Apugli compatibility overhaul, first-class integrations with EMI / Jade / The One Probe / FTB Quests / FTB Teams / FTB Ultimine / Accessories, a parchment-scroll UI, a browser-based pack editor, config hardening, and a long list of new powers, actions, and fixes. If you hit a rough edge, please report it on the [GitHub issue tracker](https://github.com/CyberDay1/NeoOrigins/issues).
>
> **Supports:** Minecraft 26.1.x (Java 25) · Minecraft 1.21.1 (Java 21)

The headline of this release is a big jump in **Origins / Apoli / Apugli pack compatibility** — real spell packs (deanos, CrystalWeaver) that previously hit "Unknown power type" or silently no-op'd now load and behave correctly — alongside a parchment-scroll skin for the origin picker and a browser-based pack editor to complement the existing in-game creator.

### Compat Improvements

- **`apoli:` / `apugli:` namespaces are now recognized.** Power types, actions, and conditions declared under the `apoli:` / `apugli:` namespaces are canonicalized to their `origins:` equivalents before dispatch (e.g. `apoli:resource`, `apoli:multiple`, `apoli:and`, `apoli:raycast`, `apoli:change_resource`, `apoli:sneaking`). Previously these fell through to "Unknown power type" even when an identical handler already existed, so modern packs that author in the Apoli namespace lost large chunks of behavior on load.
- **Apoli resource bars render with the real sprite sheet.** Compat resource bars now draw using Apoli's actual `resource_bar.png` art — bar fill selected by `bar_index`, icon by `icon_index` — including a per-power `sprite_location` override so packs that ship a restyled bar sheet render with *their* texture. Native NeoOrigins resources keep the existing color-tinted fill, so nothing regresses. (Apoli's `resource_bar.png` is bundled under its MIT license — see Documentation.)
- **`apoli:modify_velocity` is now supported.** Per-axis modifier on the player's movement each tick, **condition-gated and fail-closed**: if a gating condition can't be parsed the power refuses to compile rather than risk freezing the player (e.g. a resource-gated "stop" effect that would otherwise apply unconditionally).
- **`origins:modify_attribute` now translates** to `neoorigins:attribute_modifier`. (This is the top-level-attribute schema, distinct from `origins:attribute`, which nests the attribute inside each modifier.) Resource-driven dynamic values apply their static base value.
- **Raycast spells gain `before_action` + `command_at_hit`.** `before_action` runs once up front (e.g. consume the offhand reagent) and `command_at_hit` runs a command at the exact impact point. Together with the existing `command_along_ray`, this covers deanos-style projectile and teleport spells.
- **Right-click (`key.use`) active powers now fire.** Apoli `active_self` powers bound to `key.use` — cast by right-clicking with a plain or empty hand, the way most spell packs work — previously never triggered, because the server only saw item-use *animations* (food/bow/shield) and not a plain tap. The server now tracks the right-click tick so these powers cast.
- **Nested bientity actions resolve instead of no-op'ing.** Entity-actions tucked inside a bientity `and` (`add_velocity`, `mount`, `if_else`, `spawn_particles`, …) now run on the actor with the hit entity as context. Genuinely-unknown verbs still warn (and don't double-warn).
- **Plural modifier arrays accepted.** Conditioned `modify_damage_taken` / `modify_damage_dealt` and `modify_xp_gain` now accept Apoli's plural `modifiers` / `amount` forms in addition to the legacy singular fields.
- **Apoli attribute-operation mapping is safer.** The operation mapper now **drops** clamp-style operations that have no vanilla `AttributeModifier` equivalent (`set_base`, `min_total`, `max_total`, …) instead of silently mismapping them onto an unrelated vanilla operation. `modify_attribute` and conditioned-attribute powers skip any non-representable op rather than apply a wrong one.
- **`add_velocity` honors the Apoli `space` transform** (`world` / `local` / `velocity`), so directional knockback/launch verbs push along the intended frame instead of always in world space.
- **Inline Apoli recipes now decode on NeoForge.** `origins:recipe` powers that ship pre-1.20.5 recipe JSON (e.g. Toxophilite's makeshift arrows) are normalized to the 1.21.1 codec shape. Ingredient objects are left in NeoForge's `either(list, object)` form (rewriting them to bare strings made the codec reject every inline recipe); only the `result`'s Apoli `item` key is rewritten to the 1.21.1 `id` key.
- **Iron's Spells damage types match by full id.** Namespaced damage-type ids (e.g. `irons_spellbooks:fire_magic`) now compare on the full registry key in `modify_damage` / `action_on_hit`, so spell-school damage conditions resolve instead of dropping the namespace.
- **The `/resource` command matches Apoli.** Brought to parity with apace100/apoli's `ResourceCommand` — `has` / `get` / `set` / `change` / `operation` subcommands, single-target, "power not granted" errors, vanilla scoreboard feedback messages, correct return values, and resource-name suggestions scoped to the target. A load-time warning is also logged when a `resource` reference (in the condition or `change_resource` / `set_resource`) uses the `*:` self-reference wildcard, which is only resolved for `power_active` and `origins:multiple` — never for resources (see Documentation).

### Mod Integrations

A batch of new out-of-the-box integrations with popular mods. Each is a **soft dependency** — gated behind a mod-list check or reflection, so none of these mods are required and the integration silently no-ops when its mod is absent. The full catalogue lives in the new [`COMPATIBILITY.md`](docs/COMPATIBILITY.md).

- **EMI** *(1.21.1)* — adds an information panel for the Orb of Origin item.
- **Jade / The One Probe** — looking at a player shows their origin in the block/entity tooltip (Jade on both builds; The One Probe on 1.21.1).
- **FTB Quests** — a first-class **"NeoOrigins: Grant Loot Pool"** reward type (set a loot-table id + roll count directly on the quest) *(1.21.1)*, plus a quest-tag route: quests tagged `neoorigins_loot_pool_grant:<table_id>` grant a loot pool on completion (both builds). The tag route now hooks FTBQ's real completion event and grants to every online team member.
- **FTB Teams** — players on an **allied** team (not just the same team) are now trusted for mount consent, so an ally can ride your mountable origin without sending a request.
- **FTB Ultimine** — new zero-config marker power **`neoorigins:ultimine`**. FTB Ultimine's restriction API is deny-only, so the power works by denying vein-mining to non-holders; it stays fully **dormant until at least one `ultimine` power is present in loaded data**, so simply installing both mods never disables vein-mining for everyone.
- **Accessories** *(1.21.1)* — the `equipped_item` condition gains an `accessory` slot value plus an optional `slot_type` to narrow to a named curio/accessory slot; it aggregates equipped stacks from both Curios (reflection) and Accessories (typed).
- **Vampires Need Umbrellas** — holding an umbrella now shields the holder from **both** weather-damage gates: `exposed_to_sun` (sun-burn origins) *and* `in_rain` (rain/water-damage origins like Wet Fur and True Hydrophobia). The umbrella is detected in either hand or any Curios/Accessories slot.

### New / Changed Powers, Actions & Conditions

- **`neoorigins:simple`** — new display-only marker power. Does nothing mechanically; it exists to show a `name` + `description` line in the origin info panel (lore, or to describe an effect implemented by another power). Direct equivalent of `origins:simple`; `origins:tooltip` / `apace:tooltip` translate to it as well.
- **`drop_inventory` action** — drops the holder's full inventory as item entities (`throw_randomly` / `retain_ownership` honored).
- **`riding_action` action** — runs an entity-action on the entity the holder is *riding* (the mirror of `passenger_action`).
- **`spawn_particles` action** — server-broadcasts particles from the player's position (count / speed / offset / spread; simple particle types).
- **`hardness` condition** — compares the destroy-hardness of the targeted block, enabling "only affect blocks with hardness ≤ N" style spells.
- **`summon` quantity** — the summon entity-action now takes a `quantity` field.
- **`starting_equipment` multi-item** — accepts a list of item stacks (`stacks`) instead of just one; the legacy singular `item` form still works. Each entry carries full per-item data — `components` (`item[...]`-style data components: custom name, enchantments, custom model data, lore) and a structured `enchantments` list — and a new **`legacy_tag`** field bridges pre-1.21 flat SNBT onto the component system.
- **`tame_mob` owner-aware goals** — tamed-mob aggro/defend goals are split into owner-aware targeting, so a tamed mob defends its owner and won't target them.
- **`spawn_projectile` gains `no_gravity`.** When `true`, the projectile flies dead-straight along its launch vector instead of arcing (drag still applies). Works for *any* projectile entity, not just the magic orb.
- **`selector_action` action** — resolves a vanilla entity selector relative to the holder and runs a bientity-action per selected entity, publishing each as a source-entity context. A nested `fire_projectile` can then fire *from* each selected entity using its position and rotation — the basis of fan-out volleys like Toxophilite's `hyper_multishot`.
- **`fire_projectile` honors Apoli `count` + `tag`.** Non-orb projectiles spawn `count` copies (default 1), and an Apoli `tag` SNBT compound (e.g. `{pickup:1b}`) is applied to each spawned projectile. For the magic orb, `count` keeps its trail-particle meaning.
- **`break_speed_modifier` now respects `block_tag`.** The power advertised a `block_tag` filter but ignored it, scaling mining speed on every block. It is reimplemented through `PlayerEvent.BreakSpeed` (fired client + server so mining prediction matches) and now actually filters by the target block's tag/id — a leading `#` forces tag-only.

### Dual-Actor Spells & Data-Driven Projectiles

A new family of "caster + target" spell building blocks. Previously a projectile's `on_hit_action` always ran on the *shooter*, and `target_action` was a transparent pass-through — so you couldn't write "swap me with the mob I hit" or "shear whatever this orb lands on." These verbs let a spell act on **the entity (or block) on the other side of the interaction**, and the magic-orb projectile is now styled entirely from JSON.

- **`target_action` actually retargets now.** It resolves the entity on the other side of the interaction (hit / hit-by / killed / interacted-with / projectile-struck) and runs the inner entity-action against it, instead of passing through to the holder. A **player** target gets the full entity-action surface; a **non-player mob** gets the entity-general subset (`apply_effect`, `clear_effect`, `damage`, `heal`, `set_on_fire`, `extinguish`, `add_velocity`, `play_sound`, `set_fall_distance`, `dismount`, `swing_hand`). It also works directly as a `neoorigins:`-namespaced action (e.g. inside a projectile `on_hit_action`). `actor_action` is unchanged.
- **Movement verbs.** `swap_positions` atomically swaps the actor and the context target's full transform (position + yaw + pitch) — both transforms are snapshotted before either moves, so they never collapse to one point. `teleport_to_target` moves the actor to the target; `teleport_target_to_self` moves the target to the actor.
- **Entity-target verbs.** `shear` (sheep / mooshroom / snow golem / bogged / any modded `IShearable`), `dye` (`color` — dyeable mobs like sheep), `force_drop` (`slot`, default `mainhand` — makes the target drop a named equipment slot), and `steal_item` (`slot` — transfers the target's item to the actor's inventory). Each works as a projectile `on_hit_action` or wrapped in `target_action`.
- **Block-target verbs.** Spells can now act on the *block* a projectile or raycast impacts: `strip` (logs/wood → stripped, axis-preserving), `till` (grass/dirt → farmland, vanilla hoe rule), `path` (→ dirt path, vanilla shovel rule), `grow` (one bonemeal-style growth tick), and the generic `transform_block` (`to` required, optional `from` guard). `block_target_action` is the block-side analogue of `target_action` for running these explicitly.
- **Area-of-effect fan-out.** `area_of_effect` on impact now applies its inner verb to **every** matching entity in the blast — including the new dual-actor verbs — so "swap / shear / disarm everything in the radius" is a one-liner. Kill credit is now attributed to the casting player for AoE damage.
- **Data-driven projectile visuals.** `spawn_projectile` (magic orb) gains author-controlled visuals: `orb_color` and `glow_color` (RGB `[r,g,b]` 0–255 **or** hex `"#RRGGBB"`), `size`, `glow_size`, `glow_alpha`, `shape` (`cross` / `cube` / `ring` / `sphere` — all four ship), and `trail_particle` (any vanilla particle id, with `count` / `spread` / `trail_speed` tuning). `effect_type` stays as a shorthand that sets sensible defaults; any explicit field overrides it. The original seed request — *a green orb emitting purple particles that swaps the two entities when it hits* — is now fully expressible in JSON.

### Global Power Sets

- **`apoli:global` global power sets are now supported.** A datapack file at `data/<ns>/global_powers/<id>.json` grants a bundle of `powers` to entities **without any origin assignment** — NeoOrigins' port of Apoli's Global Power Set, using the same JSON shape. An optional `entity_types` list may **mix** literal entity ids (`"minecraft:creeper"`) and tag refs (`"#minecraft:skeletons"`) in one array; when absent or empty the set applies to **all** entities. An optional `order` (default `0`, lower applies first) controls apply ordering, and powers referenced by multiple sets are granted once. **Players** receive matching powers on login and on datapack sync (`/reload`), granted through the same dynamic-grant mechanism as `grant_power` so they persist across respawn and reload; removing a set revokes its grant on the next login/`/reload` unless an origin still supplies the power. **Mobs** receive matching, mob-applicable powers at spawn (`FinalizeSpawnEvent`) — already-spawned mobs pick up changes on respawn/reload rather than live-updating.

### UI & Theming

- **Parchment scroll buttons.** The origin picker's list rows, the top-right sort button, and the Random / Back / Confirm row now render as parchment scrolls — rolled up at rest, unrolled with a warm glow on hover/selection — drawn with a horizontal 3-slice so the rolled ends never distort at any width. A compact "short" scroll variant is used for the list rows and the sort button so they don't look oversized. Replaces the old flat-fill + outline buttons.
- **Live Essence Evolution progress** now shows on the Origin Info screen — your current kill-count toward the next evolution tier.
- **Themed fonts across the picker.** The bundled Newsreader font (shipped under the OFL) is now applied consistently across screen titles, body text, power names/descriptions, headers, and button labels, instead of only the layer title.
- **In-game creator polish.** The existing in-game `/neoorigins editor` and mob editor pick up the parchment widget/font theming, and raw-JSON fields gained shape-hint placeholders. The editor also gained a per-session tooltip toggle and now tolerates blank item/enchantment rows in `starting_equipment` (skipping the row instead of failing the whole power).
- **Classic-picker accessibility fallback.** A `classic_picker_style` client option swaps the parchment skin for a flat, high-contrast picker for players who find the textured UI hard to read.
- **Named keybind / hotkey support** for pack-defined keybinds.

### Web Editor (new) — https://cyberday1.github.io/NeoOrigins/

A browser-based datapack editor, complementing the existing in-game `/neoorigins editor`. New this release:

- **Origin editor** — Identity / Powers / JSON Preview tabs, a schema-driven form engine with live AJV validation, datapack `.zip` **export and import**, class creation (layer + upgrades), localStorage autosave, and an MC-version / `pack_format` toggle.
- **Block (Scratch-style) editing** — an optional Blockly view for building powers visually, with a data-safety guard when switching views.
- **Mob Origin editor** — a full second editor track (Identity / Spawn Rules / Drops / JSON Preview) with its own serializer, `.zip` export/import, and a published `mob_origin.schema.json`.
- **Accessibility — light/dark theme + colorblind palettes** — a theme toggle plus five selectable palettes (default, protanopia, deuteranopia, tritanopia, monochrome) that repaint both the block category colors and the UI accent tokens, with aria labels and screen-reader descriptions throughout.
- **Vanilla item typeahead** — the origin icon field (and other item fields) offer a searchable vanilla-item suggestion list.
- **Load vanilla template** — start from a prebuilt vanilla origin/power as a starting point, picked from a searchable template list, rather than building every draft from scratch.
- **Nested action/condition editing** — recursive ref rows let you build nested action/condition trees, and raw-JSON fields round-trip imported objects correctly (no more `[object Object]`).

### Bug Fixes

- **The origin picker auto-skipped third-party compat layers.** Packs that nest their origins at `data/<ns>/origins/origins/<name>.json` (the Origins-mod nested-id convention) were registered under the prefix-stripped id `<ns>:<name>`, which didn't match the layer's `<ns>:origins/<name>` reference — so the layer resolved to null, reported "nothing here," and the picker skipped straight past it onto the next page. They're now registered under the id the layer actually references, so the layer shows.
- **Picking a class wiped the origin layer's attribute bonuses.** Selecting or changing a class layer used to purge *all* attribute modifiers, dropping things like an origin's bonus max-health. Modifier cleanup is now layer-aware — it only removes a modifier whose source power is no longer active in *any* layer — so origin and class bonuses coexist.
- **Two powers granting the same attribute didn't stack.** Identical attribute grants (e.g. two powers each adding max-health) collided on a shared modifier id and de-duplicated to a single bonus. Each grant now builds a per-power modifier id, so they add up. (See Breaking / Migration Notes — this can change outcomes for packs that relied on the old behavior.)
- **Mage-style block/item prevention fired unconditionally.** `prevent_item_use` / `prevent_block_use` dropped the power-level holder `condition` and only kept the item/block target condition, so prevention applied the moment the power was granted rather than only when its condition was met (the deanos Mage's "can't place blocks unless holding a spell item" gate). The holder condition is now honored.
- **Elytra / natural-glide start delay.** Gliding engaged only after a multi-second delay (and often a second jump press) because the start was gated behind a fall-distance check that stays zero on the way up. The gate is removed; glide now engages on the first jump press.
- **Water-damage config value of `0` now truly disables** water/rain damage (the override was being applied after the value had already been baked in).
- **Water breathing no longer lets the bubble bar drain while submerged.** The power gated on `isUnderWater()`, which also requires the *body* to be in water; on ticks where the body briefly read as out-of-water while the eye stayed under (surface-swimming, currents, edges), vanilla drained a bubble the power didn't refill — visible bar drift with no actual drowning. It now gates on the **eye** being in water, the exact condition under which air depletes, so the bar holds steady.
- **`pack.mcmeta` pack_format corrected to 48** for MC 1.21.1 packs and editor exports.
- **JSON-preview layer-id namespace leak** and **Evolution Path glyph rendering / transparent parchment background** fixed.
- **Projectile `projectile_action` ran on the shooter, not the projectile.** `spawn_projectile` / `fire_projectile` now apply `projectile_action` to the spawned projectile itself (actor = projectile), with `on_hit_action` routed separately to the hit registry — so "set the arrow on fire" affects the arrow, not the caster.
- **Resource HUD bar didn't update on `change_resource` / `set_resource`.** Those actions mutated server-side state only; they now sync to the client so the bar value visibly updates.
- **`active_self` with `continuous: true` fired every tick.** Holding the key now fires every `<cooldown>` ticks as intended, gated behind the declared cooldown instead of once per tick.
- **`give` ignored the Apoli `amount` alias** and always granted a single item; the requested stack size is now honored.
- **Curios-slot umbrellas weren't detected.** A wrong `ICurioStacksHandler` class path (the class moved package in Curios 9.5.1) failed the reflection block and poisoned the whole Curios scan, so umbrellas worn in Curios slots never shielded against sun or rain (only the hand worked). Corrected — Curios-slot umbrellas shield again.
- **The origin picker could strand the player on a nitwit-only layer.** The auto-skip logic no longer skips (or gets stuck on) a layer whose only visible option is Nitwit.

### Commands & Config

- **Origin-gated recipe conditions** — recipes can be gated on a player's origin.
- **Sort dropdown on the origin selection screen** (re-skinned this release as the parchment scroll button).
- **Config split into CLIENT / SERVER / COMMON specs.** Origin/class enable toggles and the global resource-bar disable moved to a **server** spec, so NeoForge auto-syncs them to connecting clients — server-side origin toggles now correctly apply on remote clients instead of desyncing. Client-only display prefs (hotkey pool size, classic-picker style, show-editor button, hidden HUD bars) moved to a client config.
- **Command-power blacklist (security).** Datapack powers can execute server commands at permission level 2 — an escalation vector (e.g. a power running `/op @s`). A configurable `COMMAND_POWER_BLACKLIST` now guards `execute_command`, `command_along_ray`, `command_at_hit`, and the `command` condition; blocked roots are refused and warned (the guard unwraps `execute … run <cmd>` to the real command token).
- **Per-layer unique origins (server claims).** An optional uniqueness lock: in layers listed under the new `unique_origin_layers` config, an origin can be held by only one player at a time. Includes saved-data + client sync, `/neoorigins claims` / `unlock` admin commands, and pick-time enforcement (the Orb of Origin is **not** consumed on a rejected pick). Creative-mode ops and `/set` bypass the lock and take over the claim.
- **`show_origin_editor` config** — expose the in-game editor button outside Creative mode.
- **`/resource` parity with Apoli** — see Compat Improvements (`has` / `get` / `set` / `change` / `operation`).

### Under the Hood

- **Powers, actions, and conditions are now self-describing.** Nearly the entire power / action / condition layer was migrated onto registry-backed descriptor tables (`BuiltinPowers` field specs, `BuiltinConditions`, and the action descriptor registry): each type declares its own fields in one place — field name, type (string / enum / number / id / string-list / nested object), whether each is optional, and its default — instead of a hand-maintained dispatch switch. This was the single largest body of work in the release. The author-visible payoffs: `power.schema.json` is now **generated from the same registry the game runs on**, so editor/IDE validation can't silently drift from real behavior; closed-set fields surface as **dropdowns** and string-list fields as proper **list widgets** in both the in-game creator and the web editor; and adding or correcting a type is a single registration rather than parallel edits across the loader, the schema, and the editors.

### Documentation

- `neoorigins:simple` documented in `POWER_TYPES.md` and added to `power.schema.json`.
- `mob_origin.schema.json` published for the mob-origin editor and wired into the IDE-validator mapping.
- Hotkey system, theming, the water-damage gate, and evolution authoring documented.
- **Dual-actor spells, projectile visuals, and the new movement / entity-target / block-target verbs documented** — the new `target_action` retargeting and the `swap_positions` / `teleport_*` / `shear` / `dye` / `force_drop` / `steal_item` / `strip` / `till` / `path` / `grow` / `transform_block` / `block_target_action` verbs are in `ACTIONS.md`, and the data-driven projectile visual fields in `CUSTOM_PROJECTILES.md`.
- **Global Power Sets** documented in the new [`GLOBAL_POWERS.md`](docs/GLOBAL_POWERS.md) pack-author reference (linked from the README).
- **`mob_behavior` and `mount` power types fully documented** in `POWER_TYPES.md` — including the target-goal lifecycle (a `NearestAttackableTargetGoal`, plus a `HurtByTargetGoal` when `retaliate` is set, added on grant and stripped on revoke) and mount consent / dismount-on-revoke behavior.
- **Four new cookbook recipes** in `COOKBOOK.md`: "Arcane bolt" (straight-flying `no_gravity` projectile), "Elemental cast" (one-line fireball / wind bolt), "Hunting predator" (conditional mob aggression for mob origins), and "Beast rider" (mount entities on demand).
- **New [`COMPATIBILITY.md`](docs/COMPATIBILITY.md)** cataloguing every out-of-the-box mod integration (EMI, Jade, The One Probe, FTB Quests/Teams/Ultimine, Accessories, Curios, Vampires Need Umbrellas, Ars Nouveau, Pehkui, Epic Fight, JEI/REI, and the Origins/Apoli/Apugli importer), with honest notes on each integration's caveats and which build it targets. Linked from the docs index.
- **Resource-wildcard limitation documented** in `CONDITIONS.md` and `ACTIONS.md`: the `*:` / `*:*` self-reference wildcard is resolved only for `power_active` toggles and `origins:multiple` sub-powers, never for the `resource` condition or `change_resource` / `set_resource` actions — a `*` there reads as `0` and now warns at load.
- **Theme-template download guide** added to the theme-template README (how to grab just that folder from the repo).
- **Attribution:** `LICENSE` now credits the Apoli / apace100 project for the bundled `resource_bar.png` (MIT).

### Breaking / Migration Notes

No hard data-format breaks — existing packs load without edits — but a few of the fixes above correct previously-wrong behavior and can change outcomes:

- **Attribute stacking.** Packs that (knowingly or not) relied on the old de-duplicating behavior will now see two identical attribute grants **stack**. Review any power that double-grants the same attribute.
- **Conditioned prevention.** `prevent_item_use` / `prevent_block_use` now honor the holder `condition`. A pack that leaned on the old unconditional behavior will see prevention fire only when the condition is met.
- **pack_format.** Datapacks/exports generated by older editor builds may need their `pack.mcmeta` corrected to `48` for MC 1.21.1.
- **Config moved between files.** Origin/class enable toggles and the resource-bar disable are now in the **server** config (so they sync to clients); client display prefs (hotkey pool size, classic-picker style, show-editor button, hidden HUD bars) are in the **client** config. Defaults are unchanged, but admins who previously hand-edited these in the common config should set them in their new homes.
- **Command-powers can be blacklisted.** Datapack powers that run server commands now pass through `COMMAND_POWER_BLACKLIST`. The default list blocks obvious escalation roots; a pack that legitimately relies on a blocked command will need it removed from the blacklist.

### Credits

Huge thanks to our testers on the [Discord](https://discord.gg/Ukph2budfy) for burning through the 2.2 betas — catching the compat edge cases, broken hitboxes, and silently-misbehaving powers that this release fixes.

---
