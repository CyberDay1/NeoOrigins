// TODO: Port of `FormFieldSpec.java`.
//
// Discriminated union of field kinds:
//   BOOLEAN | INTEGER | NUMBER | ENUM | STRING       (Tier-A — rendered)
//   OBJECT | ARRAY | REF | MIXED | UNKNOWN           (Tier-B — raw-JSON fallback)
//
// Each variant carries its widget-relevant metadata (default, range,
// enum values, $ref target, etc.) plus a doc lookup key.

export {};
