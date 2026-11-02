/**
 * Public Java API for NeoOrigins 2.0.
 *
 * <p>Types under {@code com.cyberday1.neoorigins.api.**} are intended for
 * other mods to depend on. They follow semantic-versioning guarantees:
 *
 * <ul>
 *   <li><b>Major</b> (x.0.0) — breaking changes allowed. Deprecations
 *       honoured for at least one minor cycle beforehand.</li>
 *   <li><b>Minor</b> (2.x.0) — additive only. New methods, new fields,
 *       new power types, new events. Existing signatures do not change.</li>
 *   <li><b>Patch</b> (2.0.x) — bug fixes only. No API surface changes.</li>
 * </ul>
 *
 * <p>Types outside {@code api/} — specifically under {@code service/},
 * {@code event/}, {@code power/builtin/}, {@code compat/} — are
 * <b>internal</b>. They can change between patch releases without notice.
 * Don't depend on them from another mod.
 *
 * <p>Preferred entry point: {@link com.cyberday1.neoorigins.api.NeoOriginsAPI}
 * answers questions about a player's active powers (list them, test for a
 * power type or a capability tag, visit every configuration of a type) and
 * about summoned minions (who summoned an entity, whether it is a minion).
 * It also reads a player's origin per layer ({@code getOrigin}, {@code getOrigins})
 * and changes it ({@code setOrigin}, {@code clearOrigin}). A custom power type is
 * registered through NeoForge's {@code RegisterEvent} against the
 * {@code neoorigins:power_type} registry, and origin changes arrive as the
 * events in {@code api.event} on the NeoForge event bus.
 *
 * <p>Sub-packages:
 * <ul>
 *   <li>{@code api.origin} — data model for Origin / OriginLayer / Impact /
 *       OriginUpgrade, constructed by pack JSON and readable by mods, plus
 *       {@code SetOriginResult}.</li>
 *   <li>{@code api.power} — {@link com.cyberday1.neoorigins.api.power.PowerType}
 *       (extend for custom power types) and {@link com.cyberday1.neoorigins.api.power.PowerConfiguration}.</li>
 *   <li>{@code api.event} — NeoForge-bus events fired by the mod
 *       (OriginChanged, OriginsLoaded, PowerGranted, PowerRevoked).</li>
 *   <li>{@code api.condition} — types useful when implementing custom
 *       conditions.</li>
 *   <li>{@code api.resource} — {@link com.cyberday1.neoorigins.api.resource.ResourceBacking},
 *       an external pool a resource bar can read and write.</li>
 * </ul>
 */
package com.cyberday1.neoorigins.api;
