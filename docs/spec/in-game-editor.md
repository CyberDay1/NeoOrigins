---
title: In-Game Editor Spec
nav_exclude: true
---

# In-Game Origin & Power Editor — Design Spec

**Status:** planning, no code yet. Living document — update as decisions firm up.
**Targets:** NeoOrigins on MC 1.21.1 (NeoForge 21.1) **and** MC 26.1+ (NeoForge 27.x).

A creative-mode in-game tool for authoring origins, powers, conditions, actions, and events. Output is a real datapack at `config/originpacks/<pack-name>/` — the same shape pack authors hand-edit, shareable as a `.zip`. Fills a real gap: today, building a custom origin requires editing JSON in an external editor and `/reload`-ing repeatedly.

---

## TL;DR — key decisions

| Decision | Pick | Why |
|---|---|---|
| **UI library** | **YACL 3.8** + custom tree widget | Only Minecraft-native config lib that ships NeoForge builds for both 1.21.1 + 26.1, has dropdowns / tooltips / dark theme / collapsible groups out of the box, supports custom widget extension. LGPL v3, actively maintained (488 commits, last release Apr 2026). |
| **Schemas** | Promote `docs/schema/*` to runtime resources at `data/neoorigins/schemas/`. Hand-roll a thin `SchemaReader` (~400 LOC). Don't bundle a JSON Schema validator. | Form generation only needs `type` / `enum` / `oneOf` / `required` / `description`. Heavy validators (everit, networknt) are ~MB and overkill. |
| **Tree widget** | Hand-built `DslNodeWidget` extending `AbstractContainerWidget`. Reference patterns from FTB Library's panel system (LGPL v2.1). | No existing Minecraft lib ships a recursive tree editor. ~1500-2000 LOC custom — bounded effort. |
| **Tree UX** | **Breadcrumb + focus**, not deeply indented | Recursive UIs in MC's 320×240 effective viewport get unreadable past ~3 nesting levels. Click a child → swap pane to focus on it, parent path shown as breadcrumb. |
| **Render API** | **Stay above `GuiGraphics`** (no raw `BufferBuilder` / `RenderSystem`) | The render-pipeline rework in 1.21.6 only bites code below `GuiGraphics`. Stay above and the per-version diff is mechanical type-renames. |
| **Form lib coupling** | Single `FormRenderer` interface; `VanillaFormRenderer` ships in Phase 1, `YaclFormRenderer` swaps in for Phase 2+ | Decouples lib decision from architecture. Phase 1 has zero new deps. |
| **Persistence** | Atomic write (tempfile + fsync + rename) into `config/originpacks/<name>/`, sandboxed | Existing `OriginsPackFinder` already loads from there. No new loading infra needed. |
| **Live reload** | Server-side `MinecraftServer.reloadResources()` via packet, plus a post-reload sweep that revoke+regrants powers for online players | Existing `OriginDataManager`/`PowerDataManager` reload listeners pick up changes; player active-power state needs an explicit refresh. |
| **Multiplayer scope (v1)** | **SP creative only** — defer dedicated-server support to phase 4+ | Massively simplifies the security/permission story. The post-reload sweep + op-gating can be implemented after the SP path is proven. |

---

## Architecture overview

### Output target

```
config/originpacks/<pack-name>/
├── pack.mcmeta                                 (auto-generated)
├── pack.png                                    (optional, user-supplied)
├── data/<ns>/origins/origins/<id>.json         (origin definitions)
├── data/<ns>/origins/powers/<id>.json          (power definitions)
├── data/<ns>/origins/origin_layers/<id>.json   (layer definitions)
└── assets/<ns>/lang/en_us.json                 (display names + descriptions)
```

`<pack-name>` is user-chosen, sanitized to `^[a-zA-Z0-9_\-]{1,64}$`.
`<ns>` defaults to a sanitization of the pack name; user can override.

### Persistence flow

```
Editor screen
  ↓ C2S EditorWriteFiles(packName, files{path → bytes}, reloadAfter)
Server
  ├─ permission check (op + creative)
  ├─ PackPathSandbox.validate (path traversal, size caps, file-count caps)
  ├─ EditorFileService.writeAtomic per file (tempfile + fsync + rename)
  ├─ if reloadAfter: server.reloadResources(currentSelectedIds)
  ├─ if reloaded: ActiveOriginService.refreshAfterReload() per online player
  └─ S2C EditorWriteAck(ok, error)
```

### Screen tree

```
PackBrowserScreen                       — list packs in config/originpacks/
└── PackWorkspaceScreen(packDir)        — chosen-pack hub: Origins/Powers/Layers tabs
    ├── OriginFormScreen                — schema-driven origin form
    │   ├── PowerPickerScreen           — pick existing power for origin's powers list
    │   └── IconPickerScreen            — registry browser for icon field
    ├── PowerFormScreen                 — schema-driven power form (type discriminator)
    │   └── DslTreeScreen(field)        — recursive condition/action editor
    └── LayerFormScreen                 — origin layer form
```

State held in `PackWorkspaceScreen.draft: PackDraft` — an in-memory `Map<Identifier, JsonObject>` per resource type plus a dirty-set. Sub-screens mutate `draft` and pop. "Save All" diffs against disk and writes only changed files.

### Entry points

1. **Keybind** `key.neoorigins.open_pack_editor` (default unbound) — client-side only, opens directly in SP.
2. **Command** `/origin pack-editor` — gated by `Permissions.COMMANDS_GAMEMASTER` + `gameMode.isCreative()`. On dedicated server, sends `OpenPackEditorPayload` to client (trampolines through `ClientOriginState` to keep `Screen` references out of common-side code per the existing dist-cleaner pattern).
3. **Creative-tab item** (optional polish) — phase 4.

---

## UI library landscape

### Why YACL

| Feature | Vanilla | **YACL 3.8** | Cloth Config | owo-lib | Configured | ImGui |
|---|---|---|---|---|---|---|
| NeoForge 1.21.1 | n/a | **3.8.0** | v15.0.140 | 0.12.15 | yes | dep only |
| NeoForge 26.1 | n/a | **3.7.1+1.21.6** | v18-v21 | 0.12.21+ | 2.7.5 | dep only |
| Active maintenance | n/a | **488 commits, Apr 2026** | porting only, frozen on features | Fabric-first | active | upstream only |
| Dropdown | no | **yes** (`DropdownStringControllerBuilder`) | limited | yes (undocumented) | limited | yes |
| Rich tooltip | yes (`Tooltip.create`) | **yes** (rich text + WebP) | yes | yes | yes | yes |
| Collapsible groups | no | **yes** | sub-categories only | yes | yes | yes |
| Native dark theme | vanilla-only | **yes** | yes | yes | themeable | native |
| Scrollable forms | `ContainerObjectSelectionList` | **yes** | yes | yes | yes | yes |
| Custom widget hooks | n/a | **yes** | limited | yes | limited | n/a |
| License | n/a | **LGPL v3** | LGPL v3 | MIT | LGPL v3 | Apache 2.0 |

**Cloth Config** — the maintainer publicly froze feature work (porting only). Limited dropdown support.
**owo-lib** — Fabric-first; NeoForge is secondary. Heavier footprint, weaker docs (issue #53 calls out the dropdown is undocumented).
**Configured (MrCrayfish)** — config-screen oriented, doesn't aim at custom complex UIs.
**ImGui** — permanent maintenance tax: per-OS native binaries, font-rendering breaks (issue #235), GLFW input collisions, no Minecraft look. Skip.
**Vanilla only** — possible but means hand-rolling dropdown / collapsible / tree / scrollable-form widgets from scratch. We'd basically rewrite YACL.

### Cross-version friction (1.21.1 → 26.1+)

Per the NeoForge primers:

- **Render pipeline rework (1.21.5 → 1.21.6):** `GuiGraphics` now submits to a `GuiRenderState` instead of rendering directly. Code calling raw `BufferBuilder`/`RenderSystem` won't compile on newer versions. **Mitigation:** stay inside `GuiGraphics.fill/blit/drawString` everywhere; those are forward-compatible.
- **Tooltip API shift (1.21.5):** `Item#appendHoverText` deprecated for a new signature. **Widget tooltips** (the `setTooltip(Tooltip.create(...))` flow) are unaffected.
- **`GuiGraphics#renderTooltip` deferred path** has a bug in 1.21.6+ (tooltipStack resets before `renderDeferredTooltip()`). Use the synchronous tooltip path.
- **`Component.translatable/literal`** — stable since 1.19.3.
- **`EditBox`** — stable, but multiline custom text editors that drew their own glyphs will break.
- **Type renames** — already cataloged in memory:

| 26.1 | 1.21.1 |
|---|---|
| `Identifier` | `ResourceLocation` |
| `GuiGraphicsExtractor` | `GuiGraphics` (where they diverge in named utility helpers) |
| `ClientPacketDistributor.sendToServer` | `PacketDistributor.sendToServer` |
| `Identifier.fromNamespaceAndPath(...)` | `ResourceLocation.fromNamespaceAndPath(...)` |
| `FMLEnvironment.getDist()` | `FMLEnvironment.dist` |

**Strategy: author once on 26.1, port mechanically to 1.21.1.** No shared common module — that'd require a new gradle subproject and isn't worth the bloat. ~20-line substitution checklist covers the diff.

---

## Schema runtime promotion

### Layout
- **Move** `docs/schema/{origin,power,condition,action,layer}.schema.json` → `src/main/resources/data/neoorigins/schemas/`.
- Keep `docs/schema/` as a published copy for human reference (build task copies from `src/main/resources/...`, or hand-sync — it's docs).

### `SchemaRegistry`

Singleton, lazy-loaded at mod init:

```java
public final class SchemaRegistry {
    public static final SchemaRegistry INSTANCE;

    JsonObject originSchema();
    JsonObject powerSchema();                            // top-level with all oneOf branches
    JsonObject powerSchemaFor(Identifier typeId);        // single oneOf branch
    JsonObject conditionSchema();
    JsonObject actionSchema();
    JsonObject layerSchema();

    FormSpec formFor(String resourceKind);               // "origin" | "power" | "layer"
    FormSpec formForPowerType(Identifier typeId);

    Set<Identifier> knownPowerTypes();                   // pulled from power.schema.json's enum
}
```

Reads schemas via `getResourceAsStream` (mod JAR resources, not datapack indirection).

### Schema↔codec drift

Hand-maintain. Add an integration test (`dev/PowerSchemaCoverageTest`) that walks `PowerTypes.values()` and asserts every typeId has a corresponding `oneOf` branch in `power.schema.json`. ~30 lines. CONTRIBUTING.md note: new power type → update both files.

**Rejected alternative:** generating schemas from codecs at build time. Codec introspection is incomplete (can't recover `optionalFieldOf` defaults, can't recover descriptions or `oneOf` constraints without significant infra). Re-evaluate after Phase 4.

---

## Schema-driven form layer (UI-lib agnostic)

### Form-spec data structures

In `editor/form/`:

```java
public record FormSpec(String title, List<FieldSpec> fields, List<String> errors) {}

public sealed interface FieldSpec permits
    StringField, IntField, FloatField, BoolField, EnumField,
    IdentifierField, ItemIdField, ListField, ObjectField,
    OneOfField, DslField, PowerRefField {

    String name();
    String label();
    String helpText();        // → tooltip + question-mark hover
    boolean optional();       // → "Optional" tag in UI
    boolean immutable();      // discriminator fields after they're set
    Validation validation();
}
```

Concrete records cover the field types: `EnumField` carries dropdown options, `IdentifierField` carries an optional ID-shape filter, `OneOfField` carries discriminator + branches, `DslField` flags "open `DslTreeScreen`", `PowerRefField` flags "open `PowerPickerScreen`".

### JSON Schema → FormSpec adapter

`JsonSchemaToFormSpec` handles:
- `type: string | integer | number | boolean | array | object`
- `enum` → `EnumField`
- `oneOf` at top level → `OneOfField` with discriminator detection (the field whose value is `const` per branch — matches how `power.schema.json` is structured)
- `$ref` to sibling files (`condition.schema.json`, `action.schema.json`) → `DslField` (recursive trees aren't inlined into flat forms)
- `pattern`, `minimum/maximum`, `required`, `description` → validation + helpText + optional flag

Ignored for v1: `if/then/else`, `dependentSchemas`, `patternProperties`, `not`. None appear in our schemas at depth.

### Renderer interface

The only lib-coupled class:

```java
public interface FormRenderer {
    int renderField(FieldSpec spec,
                    Object currentValue,
                    Consumer<Object> onChange,
                    Screen ownerScreen,
                    int x, int y, int w);
}
```

Implementations:
- `VanillaFormRenderer` — vanilla widgets only. Phase 1 default.
- `YaclFormRenderer` — YACL widgets. Lands in Phase 2 once form-gen plumbing is in place.

`OriginFormScreen.init()`:
```java
FormRenderer r = FormRenderers.current();
for (FieldSpec spec : formSpec.fields()) { r.renderField(...); }
```

Swap by config flag. **No other class touches the lib choice.**

---

## DSL tree composition

### Mutable tree

```java
public final class DslNode {
    Identifier typeId;                                 // "neoorigins:and", "neoorigins:in_water", ...
    Map<String, Object> fields = new LinkedHashMap<>();// preserves order on round-trip
    List<DslNode> children = new ArrayList<>();        // for and/or/not; empty on leaves
    DslNode parent;
}
```

### JSON ↔ tree round-trip

`DslCodec.parse(JsonObject, dslKind)` walks the tree using schema lookup; `write(DslNode)` is the inverse.

**Trade-off accepted:** comments and trailing-comma JSON are not preserved (Gson strips them). Generate clean canonical JSON. Author-side comments in third-party packs are lost on edit-then-save — surface this in a "Open existing pack" warning.

Field/key order: preserved via `LinkedHashMap`. New fields default to schema-declared order, appended at end.

### UX: breadcrumb + focus, not indented tree

A fully-expanded indented tree gets unreadable in Minecraft's effective viewport past ~3 nesting levels. Real origins have deeper nesting (e.g. `condition_passive` → `and` → `not` → `in_water` plus `exposed_to_sun` plus a numeric range check is already 4-5 levels).

```
Editing: condition_passive → condition → and → conditions[1] → not
                                                                  └── condition: in_water

[in_water]                            ← current node, full editor
  ▶ no fields

  [↑ back to: not]                    ← parent breadcrumb (clickable)
```

Clicking a child swaps the editor pane to focus on that child; breadcrumb shows the path back. Sidesteps "horizontal scroll inside vertical scroll" widget pain entirely.

### Validation

`SchemaValidator.validate(json, schema)` returns `List<Diagnostic>` with severity, JSON-pointer path, and message. Hand-rolled (~400 LOC) for the subset we use: type, required, enum, pattern, min/max, oneOf-with-discriminator, `$ref` to sibling schemas. Runs debounced on every change (~150ms) and unconditionally before save. Save with errors is allowed but warns.

---

## Server-side write path

### Packet shape

All in `network/payload/`, registered alongside existing payloads in `NeoOriginsNetwork.register`:

```
EditorListPacksPayload                  C2S — request pack list
EditorPackListResponsePayload           S2C — reply
EditorReadPackPayload                   C2S — request all JSON bytes for one pack
EditorPackContentsPayload               S2C — reply
EditorWriteFilesPayload                 C2S — packName + Map<path, byte[]> + reloadAfter
EditorWriteAckPayload                   S2C — ok / error
EditorReloadPayload                     C2S — fire reloadResources without writing
OpenPackEditorPayload                   S2C — server tells client to open the screen (for /origin pack-editor on dedicated)
```

Listing/reading is needed because on dedicated server the client can't see the server's filesystem. SP works the same way through the integrated server; no special-casing.

`Map<String, byte[]>` rather than per-file packets: save-all commonly touches 5-30 files and we want all-or-nothing. `byte[]` preserves exact bytes (encoding, line endings) the user authored.

### Handler placement

Handlers in `NeoOriginsNetwork` delegate immediately to a new `service/editor/` package:
- `EditorFileService` — write/read/list operations, server-side only, no `Screen` references.
- `PackPathSandbox` — path validation.

### Path sandboxing — defense in depth

1. `packName` regex `^[a-zA-Z0-9_\-]{1,64}$`. Reject anything else.
2. Resolve `originpacksDir.resolve(packName).normalize().toAbsolutePath()` and assert `result.startsWith(originpacksDir.normalize().toAbsolutePath())`. Defends against `..` even if rule 1 is bypassed.
3. File path keys must match `^data/[a-z0-9_.\-]+/origins/(origins|powers|origin_layers)/[a-z0-9_.\-/]+\.json$` plus `pack\.mcmeta` and `assets/[a-z0-9_.\-]+/lang/en_us\.json`. No traversal, no other extensions.
4. Per-file size cap (256 KB), per-write total cap (4 MB), per-pack file-count cap (2000).
5. Refuse if any path component is a symlink (`Files.isSymbolicLink` walk).

### Op + creative gating

```java
if (!(ctx.player() instanceof ServerPlayer sp)) return;
if (!sp.gameMode.isCreative()) { reject("creative required"); return; }
if (!sp.hasPermissions(Permissions.COMMANDS_GAMEMASTER)) { reject("op required"); return; }
```

Mirrors the existing `OriginCommand` pattern. On integrated server, host has perms automatically. Config toggle `EDITOR_REQUIRE_OP` (default `true`) lets SP host loosen for cooperative play.

### Atomic write

```java
Path tmp = target.resolveSibling(target.getFileName() + ".tmp." + UUID.randomUUID());
Files.createDirectories(target.getParent());
Files.write(tmp, bytes, CREATE_NEW, WRITE);
try (FileChannel fc = FileChannel.open(tmp, WRITE)) { fc.force(true); }
Files.move(tmp, target, REPLACE_EXISTING, ATOMIC_MOVE);  // fallback to non-atomic on Win failure
```

Multi-file save: write all to `.tmp.<uuid>` first, then move in sequence. Failure mid-batch → roll back already-moved files from a backup snapshot. Not strictly cross-file atomic, but recoverable by re-saving.

### Reload + post-reload sweep

`MinecraftServer.reloadResources(currentSelectedIds)` returns a `CompletableFuture` that runs off-thread; cleanup must be in `.whenComplete` on the server thread.

**Subtlety:** after reload, `OriginDataManager`/`PowerDataManager` versions bump → cache invalidates per-player on next read. But `applyOriginPowers` doesn't re-run automatically. If a power was removed or its definition changed, the player's resolved active-power set could include stale entries until next origin change or login.

Fix: new `ActiveOriginService.refreshAfterReload(ServerPlayer)` that, for each layer, revokes all current powers and re-applies from the (now-fresh) origin definition. Sweep all online players in `whenComplete`. ~30-50 LOC.

This is **real new code**, not a free lunch — call out as a phase 4 risk.

---

## Live preview (phase 4)

`ActiveOriginService.beginPreview(player, layerId, draftOrigin, draftPowers)` — stashes draft in a per-player override map keyed on UUID, bumps `data.version()` to invalidate cache, applies draft powers without writing to disk.

`endPreview(player)` reverts.

Cache key in `ActiveOriginService.CacheEntry` already includes `data.version()` — version bump invalidates correctly. Override map consulted in `getOrBuild`'s loop: for previewed `layerId`, use draft instead of registry lookup.

Two players previewing different drafts don't collide — UUID-keyed.

Client-side: new `ClientPreviewState` parallel to `ClientOriginState` / `ClientPowerCache`, consulted first with fall-through. Server pushes `SyncPreviewPayload(Origin draft, Map<Id, PowerEntry> draftPowers)` rather than overwriting registry sync.

---

## Phasing

### Phase 1 — Origin assembly (pack-editor MVP)

**Goal:** pick an existing or new pack folder, edit `origins/origins/<id>.json` (powers list, name, icon, impact, order, description), save to disk, manual `/reload` for changes to take effect.

**Files to add** (full paths under `src/main/java/com/cyberday1/neoorigins/`):

| Path | Purpose |
|---|---|
| `screen/editor/PackBrowserScreen.java` | List packs in `config/originpacks/` |
| `screen/editor/PackWorkspaceScreen.java` | Chosen-pack hub |
| `screen/editor/OriginFormScreen.java` | Origin form |
| `screen/editor/PowerPickerScreen.java` | Pick existing power |
| `screen/editor/IconPickerScreen.java` | Item registry browser |
| `screen/editor/ConfirmDiscardScreen.java` | Unsaved-changes prompt |
| `editor/PackDraft.java` | In-memory edit buffer |
| `editor/form/FormSpec.java`, `FieldSpec.java` (sealed) + concrete records | Form IR |
| `editor/form/render/FormRenderer.java` (interface) | Lib-swap point |
| `editor/form/render/VanillaFormRenderer.java` | Phase-1 default |
| `editor/form/render/FormRenderers.java` | Resolver |
| `editor/schema/SchemaRegistry.java` | Schema lookup |
| `editor/schema/JsonSchemaToFormSpec.java` | Adapter |
| `editor/validation/SchemaValidator.java` | Hand-rolled validator |
| `service/editor/EditorFileService.java` | Disk I/O |
| `service/editor/PackPathSandbox.java` | Path validation |
| `network/payload/EditorListPacksPayload.java` | C2S |
| `network/payload/EditorPackListResponsePayload.java` | S2C |
| `network/payload/EditorReadPackPayload.java` | C2S |
| `network/payload/EditorPackContentsPayload.java` | S2C |
| `network/payload/EditorWriteFilesPayload.java` | C2S |
| `network/payload/EditorWriteAckPayload.java` | S2C |
| `network/payload/EditorReloadPayload.java` | C2S |
| `network/payload/OpenPackEditorPayload.java` | S2C |
| `dev/PowerSchemaCoverageTest.java` | Schema↔codec drift coverage |
| `src/main/resources/data/neoorigins/schemas/{origin,power,condition,action,layer}.schema.json` | Promoted from docs/schema |

**Files to modify:**
- `network/NeoOriginsNetwork.java` — register new packets, handlers delegate to `EditorFileService`.
- `client/ClientOriginState.java` — add `openPackEditor()` trampoline (keeps `Screen` refs out of common-side).
- `command/OriginCommand.java` — add `pack-editor` subcommand.
- `client/NeoOriginsKeybindings.java` — `OPEN_PACK_EDITOR` keybind.
- `client/NeoOriginsClientEvents.java` — keybind dispatch.
- `NeoOrigins.java` — warm `SchemaRegistry.INSTANCE` at startup.
- `assets/neoorigins/lang/en_us.json` — editor UI keys.

**Risks gating Phase 1:** none. Vanilla widgets cover the small Phase-1 feature set.

### Phase 2 — Power authoring (simple types)

**Goal:** create + edit `origins/powers/<id>.json` for non-DSL power types (attribute_modifier, flight, water_breathing, starting_equipment, top-level numerics). `type` discriminator dropdown.

**Add:**
- `screen/editor/PowerFormScreen.java`
- `screen/editor/PowerListScreen.java` — workspace tab for powers
- `editor/form/OneOfFieldHelper.java` — preserve fields across discriminator changes
- `editor/form/render/YaclFormRenderer.java` — swap in YACL for polished forms

**Modify:**
- `PackWorkspaceScreen` — add Powers tab
- `SchemaRegistry` / `JsonSchemaToFormSpec` — `formForPowerType(typeId)` extracting the matching `oneOf` branch and merging top-level common fields

**Risks:**
- Compat-namespace powers (`origins:*`, `apoli:*`, `apace:*`, `apugli:*`) translate via `OriginsPowerTranslator`; the reverse path doesn't exist. **Decision: only `neoorigins:*` powers are editable in the form. Compat powers display read-only with explanation.**

### Phase 3 — DSL composition

**Goal:** edit `condition` / `*_action` fields recursively. Wrap in and/or/not.

**Add:**
- `screen/editor/dsl/DslTreeScreen.java`
- `screen/editor/dsl/DslNodeRowWidget.java` — single-row renderer in the tree
- `screen/editor/dsl/DslTypePickerScreen.java` — pick condition/action type with search
- `editor/dsl/DslNode.java`
- `editor/dsl/DslCodec.java`
- `editor/dsl/DslDefaults.java` — default field values per type
- `editor/schema/DslTypeCatalog.java` — canonical condition/action type catalogue (see prerequisite below)

**Modify:**
- `JsonSchemaToFormSpec` — emit `DslField` for `$ref` to condition/action schemas
- `VanillaFormRenderer` / `YaclFormRenderer` — render `DslField` as "Edit tree…" button opening `DslTreeScreen`

**Tech-debt prerequisite — DslTypeCatalog:**

Today there is **no canonical registry of condition/action type IDs.** Their factories live scattered across `compat/condition/`, `compat/action/`, and `power/builtin/`. The DSL editor needs an enumerable catalogue with display names, categories, and field info per type.

Options:
1. **Reflection-walk** at startup — scan `compat/condition/` and `compat/action/` packages, build a list. Brittle (depends on package layout).
2. **New `DslTypeRegistry`** — a `DeferredRegister` of condition/action types, every existing parser file gets a one-line registration. ~1 day of churn but the right long-term answer.

Either way, this is a Phase-3 prerequisite, **not a free part of Phase 3.**

### Phase 4 — Polish: live preview, validation overlay, dedicated server, 1.21.1 port

**Add:**
- `network/payload/EditorBeginPreviewPayload.java`, `EditorEndPreviewPayload.java`, `SyncPreviewPayload.java`, `EditorPreviewErrorPayload.java`
- `client/ClientPreviewState.java`
- `screen/editor/preview/PreviewBar.java` — overlay for "preview mode active"
- `screen/editor/ValidationOverlay.java` — diagnostics widget on every form

**Modify:**
- `service/ActiveOriginService.java` — `beginPreview`, `endPreview`, override map, `refreshAfterReload`. Major-ish change.
- `network/NeoOriginsNetwork.java` — preview handlers, post-reload sweep helper.
- `client/ClientOriginState.java`, `client/ClientPowerCache.java` — fall through to `ClientPreviewState` first.

**Then:** mechanical port to `2.0-dev-1.21.1` worktree. ~20-line substitution checklist (Identifier → ResourceLocation, etc.)

**Risks:**
- `reloadResources` mid-game on dedicated server with active players — the post-reload sweep is real new code, and tickable power state (e.g. `phantom_form` persistent effect) may carry stale state across reload. **Test matrix needs every power category.**
- If we stay SP-only for v1, this risk drops away entirely.

---

## Open questions / decisions needed

| # | Question | Lean |
|---|---|---|
| 1 | **SP-only for v1 or full op-gated MP?** | Architecture agent leans **SP-only**. The post-reload sweep + dedicated-server permission story is its own large project. Defer MP to v2 or phase 5. |
| 2 | YACL coupling: hard dep, or optional with vanilla fallback? | **Optional with vanilla fallback** — Phase 1 ships zero YACL dep. YACL becomes optional dep in Phase 2. Keeps download size small for non-author users. |
| 3 | Existing `OriginEditorScreen` (creative-only relayer/toggle) — retire or keep? | **Keep for one release**, demote to "/origin editor" runtime tool. Don't conflate with the new pack editor. Audit for retirement after the new tool proves out. |
| 4 | Lang-key handling — auto-write `en_us.json` entries or require user to edit by hand? | **Auto-write.** "Display name" field writes both the JSON `"name"` lang key reference and the matching `en_us.json` entry. |
| 5 | After save, auto-grant the edited origin to the editing player, or require re-pick? | **Re-pick.** Avoids state surprises; the picker is one click away. |
| 6 | `origins:multiple` synthetic power expansion — round-trip? | Editor refuses to save `origins:multiple` directly; user edits the contained children. View of "loaded powers" filters out synthetics via `OriginsMultipleExpander.isSynthetic(id)` (method to add). |
| 7 | JSON comment preservation on edit-then-save? | **Lost.** Surface in "Open existing pack" warning. Comment preservation requires switching JSON parser (e.g. Jankson) — not worth it for v1. |

---

## Top risks

1. **UI-lib lifecycle ownership.** If a future lib choice (or YACL itself in a major rev) wants to **own** the `Screen` lifecycle (extend lib-supplied base class), the lib coupling promotes from one class (`FormRenderer`) to ~6 (every form screen). Mitigation: forbid that class of lib at evaluation time; YACL in particular supports custom widget extension without screen ownership.

2. **Render-pipeline rework on 1.21.6+.** Already mitigated by "stay above `GuiGraphics`" rule. Risk re-emerges if someone tries to draw connector lines in the DSL tree using raw `BufferBuilder`. Mitigation: code rule + per-source-set `GuiRenderCompat` shim if any raw rendering proves unavoidable.

3. **Schema-to-codec drift.** Hand-maintained, with coverage test for power types only. Conditions/actions don't have unified type registry today (Phase 3 prerequisite). If a power's schema lags its codec, form generation produces a broken form. Mitigation: integration test in Phase 1 + CONTRIBUTING.md note + DslTypeRegistry in Phase 3.

4. **DSL tree UX past 3-4 nesting levels.** Real origins exceed this. Mitigation: breadcrumb-and-focus pattern (not indented tree).

5. **Post-reload power refresh on dedicated server.** New code (`refreshAfterReload`); tickable state may not survive reload cleanly. Mitigation: defer dedicated-server support to phase 4+ behind explicit testing; SP-only for v1.

---

## Cost sketch

| Phase | LOC est. (rough) | Calendar (1-2 sessions/week) |
|---|---|---|
| 1 — Origin assembly | ~3,000 | 2-3 weeks |
| 2 — Power authoring | ~2,000 | 2-3 weeks (form-gen plumbing lands) |
| 3 — DSL composition | ~3,500 | 4-6 weeks (tree widget + DslTypeRegistry) |
| 4 — Polish + 1.21.1 port | ~1,500 | 1-2 weeks |
| **Total** | **~10,000** | **3 months** |

Phases ship independently. Phase 1 alone covers the most-asked-for workflow ("custom origin from existing powers") and is worth landing solo before committing to the rest.

---

## Appendix A — sources for library research

- [YACL releases](https://github.com/isXander/YetAnotherConfigLib/releases)
- [YACL Modrinth versions](https://modrinth.com/mod/yacl/versions)
- [Cloth Config Modrinth versions](https://modrinth.com/mod/cloth-config/versions)
- [owo-lib GitHub](https://github.com/wisp-forest/owo-lib)
- [owo-ui dropdown undocumented (issue #53)](https://github.com/wisp-forest/owo-lib/issues/53)
- [Configured (MrCrayfish)](https://github.com/MrCrayfish/Configured)
- [FTB Library](https://github.com/FTBTeam/FTB-Library)
- [imgui-java (SpaiR)](https://github.com/SpaiR/imgui-java)
- [imgui Minecraft black-text bug #235](https://github.com/SpaiR/imgui-java/issues/235)
- [NeoForge 1.21.5 primer](https://github.com/neoforged/.github/blob/main/primers/1.21.5/index.md)
- [NeoForge 1.21.6 primer](https://docs.neoforged.net/primer/docs/1.21.6/)
- [networknt/json-schema-validator](https://github.com/networknt/json-schema-validator)
- [everit-org/json-schema](https://github.com/everit-org/json-schema)
- [NeoForge Screens docs (1.21.1)](https://docs.neoforged.net/docs/1.21.1/gui/screens/)

---

## Appendix B — port checklist (26.1 → 1.21.1)

When porting code authored on master to `2.0-dev-1.21.1`:

| Substitution | From | To |
|---|---|---|
| Type | `Identifier` | `ResourceLocation` |
| Method | `Identifier.fromNamespaceAndPath(...)` | `ResourceLocation.fromNamespaceAndPath(...)` |
| Type | `GuiGraphicsExtractor` (where it appears) | `GuiGraphics` (mostly the same path; spot-check) |
| Method | `ClientPacketDistributor.sendToServer(...)` | `PacketDistributor.sendToServer(...)` |
| Method | `FMLEnvironment.getDist()` | `FMLEnvironment.dist` |
| `BlockEvent.BreakEvent` | (n/a — 26.1 only had this issue) | (no change) |

Spot-check `OriginEditorScreen.java` diff between branches for the canonical substitution rules.
