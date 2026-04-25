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
 * exposes the common integration operations (query a player's origin,
 * check if they have a power, register a custom power type, listen for
 * origin-change events).
 *
 * <p>Sub-packages:
 * <ul>
 *   <li>{@code api.origin} — data model for Origin / OriginLayer / Impact /
 *       OriginUpgrade. Constructed by pack JSON; readable by mods.</li>
 *   <li>{@code api.power} — {@link com.cyberday1.neoorigins.api.power.PowerType}
 *       (extend for custom power types) and {@link com.cyberday1.neoorigins.api.power.PowerConfiguration}.</li>
 *   <li>{@code api.event} — NeoForge-bus events fired by the mod
 *       (OriginChanged, OriginsLoaded, PowerGranted, PowerRevoked).</li>
 *   <li>{@code api.condition} — types useful when implementing custom
 *       conditions.</li>
 * </ul>
 */
package com.cyberday1.neoorigins.api;
