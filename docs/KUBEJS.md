---
title: KubeJS Integration
parent: "Scripting & Java"
nav_order: 1
---

# KubeJS Integration
{: .no_toc }

**Not available on this build.** The Minecraft 26.2 build of NeoOrigins has no
KubeJS integration, and installing KubeJS alongside it does not add one.

---

## Availability

The KubeJS integration ships in the Minecraft 1.21.1 build (from NeoOrigins
2.1.2) and the Minecraft 26.1 build (from 2.2.24). This 26.2 build declares no
KubeJS dependency and contains none of the integration's classes, because
KubeJS has published no NeoForge build for Minecraft 26.2. The scripting
reference lives in the documentation for the 1.21.1 and 26.1 builds.

Everything the integration provides is absent here:

- The `NeoOrigins` and `NeoOriginsEvents` script globals.
- The `neoorigins:js_custom` and `neoorigins:js_active` power types. A power
  file using either type is dropped **whole** at load: the server log records
  the drop, and in game the power simply never appears on the origin.
- The `neoorigins:kubejs_callback` action. An unknown action type is skipped
  when the power is parsed, so the surrounding power still loads, but that
  step of the action never runs.

A pack that uses any of these should gate those files behind a pack that does
not ship on 26.2, or keep that behaviour in Java or in the datapack DSL here.

KeybindJS is a separate mod and is unaffected: the hotkey hand-off described
in [COMPATIBILITY.md](COMPATIBILITY.md#scripting) works on this build.
