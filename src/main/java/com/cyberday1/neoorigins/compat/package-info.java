/**
 * NeoOrigins compat subsystem — translates Origins/Apace power packs at load time.
 *
 * <h2>Translation boundaries</h2>
 *
 * <pre>
 * originpacks/ JARs / ZIPs / folders
 *      │
 *      ▼
 * OriginsFormatDetector         — detects origins: / apace: type namespace
 *      │
 *      ├─► OriginsMultipleExpander   — pre-pass: expands origins:multiple into synthetic sub-IDs
 *      │         writes static MULTIPLE_EXPANSION_MAP + MULTIPLE_DISPLAY_MAP
 *      │
 *      ├─► [Route A] OriginsPowerTranslator
 *      │         Static JSON rewrite before codec parsing.
 *      │         ~20 Origins power types → NeoOrigins equivalents.
 *      │         Unknown types → Optional.empty() (fail-closed, logged as [SKIP] or [FAIL]).
 *      │         Lossy translations annotated with // [LOSSY].
 *      │
 *      └─► [Route B] OriginsCompatPowerLoader → ActionParser + ConditionParser
 *                Dynamic compilation of power JSON to CompatPower.Config lambdas.
 *                Handles: active_self, action_over_time, action_on_callback, resource,
 *                         toggle, conditioned_attribute, conditioned_status_effect,
 *                         action_on_being_hit / self_action_when_hit, damage_over_time.
 *                Unknown action types  → CompatPolicy.NOOP_ACTION      (fail-safe no-op, [CompatB] WARN).
 *                Unknown condition types → CompatPolicy.FALSE_CONDITION (fail-closed, [CompatB] WARN).`r`n *                Missing required action/condition fields follow the same policy.
 * </pre>
 *
 * <h2>Origin JSON normalization</h2>
 * {@code OriginsOriginTranslator} normalizes icon/impact/name/description and rewrites
 * the powers list using the expansion map — runs in OriginDataManager, not here.
 *
 * <h2>Translation log</h2>
 * {@code CompatTranslationLog} is opened in {@code PowerDataManager.apply()} and closed in
 * {@code OriginDataManager.apply()}. It writes {@code logs/neoorigins-compat.log} with
 * [PASS] / [FAIL] / [SKIP] per power ID.
 *
 * <h2>Load order</h2>
 * {@code power_data} → {@code origins_compat_b} → {@code origin_data} → {@code layer_data}.
 * This order is required because OriginDataManager reads MULTIPLE_EXPANSION_MAP which is
 * populated during power_data / origins_compat_b.
 */
package com.cyberday1.neoorigins.compat;

