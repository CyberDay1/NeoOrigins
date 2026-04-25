# NeoOrigins JSON Schemas

Machine-readable schemas for pack JSON. Point your IDE or datapack validator at these to get red squigglies on typos before the pack hits the game.

## Files

| Schema | Apply to |
|---|---|
| `origin_layer.schema.json` | `data/<namespace>/origins/origin_layers/*.json` |
| `origin.schema.json` | `data/<namespace>/origins/origins/*.json` |
| `power.schema.json` | `data/<namespace>/origins/powers/*.json` |
| `condition.schema.json` | Referenced internally by `power.schema.json` — no direct file mapping. |
| `action.schema.json` | Referenced internally by `power.schema.json` — no direct file mapping. |

## Wiring up VS Code

`.vscode/settings.json`:

```json
{
  "json.schemas": [
    {
      "fileMatch": ["data/*/origins/powers/*.json"],
      "url": "./docs/schema/power.schema.json"
    },
    {
      "fileMatch": ["data/*/origins/origins/*.json"],
      "url": "./docs/schema/origin.schema.json"
    },
    {
      "fileMatch": ["data/*/origins/origin_layers/*.json"],
      "url": "./docs/schema/origin_layer.schema.json"
    }
  ]
}
```

## Wiring up IntelliJ IDEA

1. Settings → Languages & Frameworks → Schemas and DTDs → JSON Schema Mappings
2. Add three mappings, one per schema file, matching the file patterns above.

## Coverage

Schemas enumerate every valid `type` value so autocomplete and
typo-detection work across the full surface. ~17 power types, ~15
condition verbs, and ~19 action verbs carry full field constraints
(required/optional, types, enums). The rest fall through to a permissive
fallback branch that still catches typos in the `type` discriminator.

Additions welcome — a pull request that tightens a fallback branch with a
real field table costs nothing at runtime and saves pack authors from
silent misconfigurations.

## Source of truth

Schemas are derived from the Java Config records. If a schema disagrees
with a prose doc, the schema wins; if a schema disagrees with the Java
record, the record wins. Open an issue.
