# NeoOrigins 2.2 — Patch Notes

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
