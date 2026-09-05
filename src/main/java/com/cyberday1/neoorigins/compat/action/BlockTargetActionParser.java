package com.cyberday1.neoorigins.compat.action;

import com.google.gson.JsonObject;

/**
 * Parses a block-targeted action JSON into a {@link BlockTargetAction} — the
 * block-side sibling of {@link TargetActionParser}. It handles the six
 * block-target verbs whose effect is a state swap / growth at a single
 * {@link net.minecraft.core.BlockPos}: {@code strip}, {@code till}, {@code path},
 * {@code grow}, {@code set_block}, and the generic {@code transform_block}.
 *
 * <p>Returns {@code null} for any verb that is not a block-target verb. The
 * caller ({@code block_target_action} in {@link BuiltinActions}) then no-ops for
 * that inner action, exactly as {@code target_action} no-ops a non-generalizable
 * verb on a mob target.
 *
 * <p><b>Additive by design.</b> The actual per-verb behaviour lives in
 * {@link BuiltinActions} (the {@code applyStrip}/{@code applyTill}/… helpers), so
 * this parser and the context-resolving {@link EntityAction} registrations in
 * {@link BuiltinActions} share one implementation — an author sees identical
 * behaviour whether they write {@code {"type":"neoorigins:strip"}} directly as a
 * projectile {@code on_hit_action} (self-resolving the block from context) or wrap
 * it in {@code block_target_action}.
 */
public final class BlockTargetActionParser {

    private BlockTargetActionParser() {}

    /**
     * @return a {@link BlockTargetAction} for a block-target verb, or {@code null}
     *         if the verb is not a block-target verb.
     */
    public static BlockTargetAction parse(JsonObject json, String contextId) {
        if (json == null) return null;
        String type = json.has("type") ? json.get("type").getAsString() : "";
        // Same canonicalization as ActionParser (bare -> neoorigins:, ecosystem
        // aliases -> neoorigins:). No legacy warning here — ActionParser warns on
        // the primary path, so we avoid double-warning.
        if (!type.isEmpty() && type.indexOf(':') < 0) {
            type = "neoorigins:" + type;
        } else if (type.startsWith("origins:") || type.startsWith("apace:") || type.startsWith("apoli:")) {
            type = "neoorigins:" + type.substring(type.indexOf(':') + 1);
        }

        return switch (type) {
            case "neoorigins:strip" -> BuiltinActions::applyStrip;
            case "neoorigins:till"  -> BuiltinActions::applyTill;
            case "neoorigins:path"  -> BuiltinActions::applyPath;
            case "neoorigins:grow",
                 // Apoli block-action name for the same verb (Fairy Origin's
                 // verdant_touch authors `origins:bonemeal`).
                 "neoorigins:bonemeal" -> BuiltinActions::applyGrow;
            case "neoorigins:transform_block" -> {
                final net.minecraft.world.level.block.Block from =
                    BuiltinActions.resolveBlockOrNull(json.has("from") ? json.get("from").getAsString() : null, "transform_block.from");
                final net.minecraft.world.level.block.Block to =
                    BuiltinActions.resolveBlockOrNull(json.has("to") ? json.get("to").getAsString() : null, "transform_block.to");
                yield (level, pos, actor) -> BuiltinActions.applyTransformBlock(level, pos, from, to);
            }
            // Apoli's set_block is both an entity action (place at the actor)
            // and a block action (place at the targeted pos). Only the entity
            // form was registered, so a `block_action` of set_block — Beholder
            // Origin carving a clicked sculk block to air — fell through to
            // null and silently no-opped.
            case "neoorigins:set_block" -> {
                final net.minecraft.world.level.block.Block block =
                    BuiltinActions.resolveBlockOrNull(json.has("block") ? json.get("block").getAsString() : null, "set_block.block");
                final boolean keep = json.has("keep") && json.get("keep").getAsBoolean();
                yield (level, pos, actor) -> BuiltinActions.applySetBlock(level, pos, block, keep);
            }
            // Not a block-target verb — caller no-ops.
            default -> null;
        };
    }
}
