# Development Guide

Technical reference for working with the NeoOrigins codebase.

## Build Commands

```bash
./gradlew build          # Compile and package → build/libs/neoorigins-1.0.0.jar
./gradlew runClient      # Launch Minecraft client with the mod
./gradlew runServer      # Launch a dedicated server (headless)
```

Build requires Java 25. Output JAR goes to `build/libs/`. Deploy to the test instance at `C:\Users\conno\curseforge\minecraft\Instances\26.1 Test Build\mods\`.

## Key Technical Facts

- **Stack:** NeoForge 26.1.0.1-beta / MC 26.1 / Java 25
- **NeoForge API quirks:** Use `MobEffectEvent.Applicable.getEffectInstance()` (not `getEffect()`); use `MobEffectEvent.Applicable.Result.DO_NOT_APPLY` (not `DENY`); `Event.Result` does not exist in this version.
- **MC 26.1 GUI changes:** `GuiGraphics` → `GuiGraphicsExtractor`; `render()` → `extractRenderState()`; `renderWidget()` → `extractWidgetRenderState()`; `drawString` → `text`; `drawCenteredString` → `centeredText`; `renderOutline` → `outline`; `renderItem` → `item`.
- **MC 26.1 Level changes:** `level.random` is now protected, so use `level.getRandom()`; `getDayTime()` replaced by `getDefaultClockTime()` (World Clock system).
- **Data reload order is load-order-sensitive** (declared in `NeoOrigins.java`): `power_data` → `origins_compat_b` → `origin_data` → `layer_data` → `mob_origin_data`. This order is required because `OriginDataManager` reads `OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP` which is populated during `power_data`. `MobOriginDataManager` (2.1) loads last; it only needs power data, so its position is just convention.

## Architecture

### Data Pipeline (server-side, hot-reloadable via `/reload`)

All three data managers extend `SimplePreparableReloadListener`. Each scans two path formats:

| Manager | NeoOrigins path | Origins-compat path |
|---|---|---|
| `PowerDataManager` | `data/<ns>/origins/powers/` | `data/<ns>/powers/` |
| `OriginDataManager` | `data/<ns>/origins/origins/` | `data/<ns>/origins/` |
| `LayerDataManager` | `data/<ns>/origins/origin_layers/` | `data/<ns>/origin_layers/` |
| `MobOriginDataManager` (2.1) | `data/<ns>/origins/mob_origins/` | _(none: NeoOrigins-native)_ |

Native-format files win on ID collision. After `power_data` loads, `OriginsCompatPowerLoader` (Route B) injects additional `PowerHolder` entries via `PowerDataManager.injectExternalPowers()`.

### Compat Translation Layer (`com.cyberday1.neoorigins.compat`)

When a power JSON has an `origins:` or `apace:` type namespace, it goes through translation before parsing:

1. `OriginsFormatDetector`: detects Origins-format JSON
2. `OriginsMultipleExpander`: expands `origins:multiple` into synthetic sub-power IDs (e.g. `ns:path/subkey`); writes to static `MULTIPLE_EXPANSION_MAP`
3. `OriginsPowerTranslator`: maps ~20 Origins power types to NeoOrigins equivalents
4. `OriginsOriginTranslator`: normalizes origin JSON fields (icon, impact int→string, hidden→unchoosable, powers list rewritten using expansion map)
5. `CompatTranslationLog`: opened in `PowerDataManager.apply()`, closed in `OriginDataManager.apply()`; writes `logs/neoorigins-compat.log`

### Power Type System

- `PowerType<C extends PowerConfiguration>`: registered via `PowerTypes` (DeferredRegister)
- `PowerHolder<C>`: pairs a type with its parsed config
- `PowerDataManager.getPower(id)`: checks native powers first, then Route B injected powers
- Adding a new built-in power type: implement in `power/builtin/`, register in `PowerTypes`, handle in `OriginEventHandler`

### Player State

- `PlayerOriginData` stored as a NeoForge attachment (`OriginAttachments`): survives respawn
- `ClientOriginState` caches the synced origin on the client side
- Network: `SyncOriginsPayload` (server→client), `ChooseOriginPayload` (client→server), `OpenOriginScreenPayload`, `ActivatePowerPayload`

### Mob State (2.1)

- `MobOriginData` stored as a NeoForge attachment (`EntityAttachments`): present on every non-player `LivingEntity`; no `copyOnDeath` (mob death re-rolls). Exposes `MAP_CODEC` (primary, for the 26.1 attachment-builder signature) and `CODEC = MAP_CODEC.codec()` for the 1.21.1 form.
- `MobOriginDataManager` loads `data/<ns>/origins/mob_origins/<id>.json` and falls back to `MobOrigin.CODEC` parse with the id injected from the file path.
- `event/MobOriginEventHandler` listens on `@FinalizeSpawnEvent` (spawn-rule evaluation + spawn-egg-marker check) and `@PlayerInteractEvent.RightClickBlock` (survival spawn-egg path).
- `service/MobOriginService.applyMobOriginPowers(LivingEntity, oldId, newId)` is the mob-side analogue of `ActiveOriginService` power application; uses `PowerType.appliesToMobs(C)` to filter player-only powers.
- Drops: `event/MobOriginDropsLootModifier` (registered via `MobOriginLootModifiers` on `GLOBAL_LOOT_MODIFIER_SERIALIZERS`) reads the attachment off the killed entity at loot-table generation, looks up `DropRules` from the manager, and rolls per strategy. Carrier files are generated by `service/MobLootModifierGenerator` into the world datapack at Save time + on `ServerStartingEvent`.

### Content Packs (`originpacks/`)

`OriginsPackFinder` mounts the `originpacks/` game-directory folder as both a server-data and client-resources pack source. Packs can be JARs, ZIPs, or folders. No `pack.mcmeta` required.

## JSON Data Formats

**NeoOrigins origin** (`data/<ns>/origins/origins/<name>.json`):
```json
{
  "name": "Merling",
  "description": "Adapted to life underwater.",
  "icon": "minecraft:prismarine_shard",
  "impact": "medium",
  "powers": ["neoorigins:merling_water_breathing"]
}
```

**NeoOrigins power** (`data/<ns>/origins/powers/<name>.json`):
```json
{
  "type": "neoorigins:status_effect",
  "effect": "minecraft:water_breathing",
  "amplifier": 0
}
```

**Layer** (`data/<ns>/origins/origin_layers/<name>.json`):
```json
{
  "id": "neoorigins:origin",
  "order": 0,
  "origins": [{"type": "origins", "origins": ["neoorigins:merling"]}]
}
```
