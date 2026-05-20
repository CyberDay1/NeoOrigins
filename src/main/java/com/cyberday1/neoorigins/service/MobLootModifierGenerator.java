package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.data.MobOriginDataManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes the two static "carrier" files that activate
 * {@link com.cyberday1.neoorigins.event.MobOriginDropsLootModifier} inside the
 * world's custom datapack:
 *
 * <ol>
 *   <li>{@code data/neoorigins/loot_modifiers/mob_origin_drops.json} — one
 *       modifier instance of type {@code neoorigins:mob_origin_drops}</li>
 *   <li>{@code data/neoforge/loot_modifiers/global_loot_modifiers.json} — the
 *       entry list activating the above (stacks additively with any other
 *       pack's entry list, so we never clobber third-party modifiers).</li>
 * </ol>
 *
 * <p>The runtime modifier reads the per-entity {@link
 * com.cyberday1.neoorigins.attachment.MobOriginData} attachment and looks up
 * {@link com.cyberday1.neoorigins.api.mob_origin.DropRules} from
 * {@link MobOriginDataManager} — these carrier files are deliberately
 * data-free, so this generator is idempotent and cheap. They're written into
 * the world datapack (alongside the authored mob origins) so the pack stays
 * portable: copy {@code neoorigins_custom/} to another instance with
 * NeoOrigins installed and the drops keep working.
 */
public final class MobLootModifierGenerator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Always the same content — the modifier carries no instance data. */
    private static final String MODIFIER_JSON = GSON.toJson(modifierJson());
    private static final String ENTRIES_JSON = GSON.toJson(entriesJson());

    private MobLootModifierGenerator() {}

    /**
     * Ensure the two carrier files exist with the expected content. Skips
     * writing if a byte-for-byte identical file is already there (so repeated
     * calls in the same session don't fight the filesystem). Atomic write +
     * rename via {@link StandardCopyOption#ATOMIC_MOVE}.
     */
    public static void ensureCarriers(MinecraftServer server) {
        Path root = CustomPackWriter.packDir(server).normalize();
        try {
            Files.createDirectories(root);
            writeIfChanged(root.resolve("data/neoorigins/loot_modifiers/mob_origin_drops.json"),
                MODIFIER_JSON);
            writeIfChanged(root.resolve("data/neoforge/loot_modifiers/global_loot_modifiers.json"),
                ENTRIES_JSON);
        } catch (IOException | RuntimeException e) {
            NeoOrigins.LOGGER.error("[mob-drops] failed writing loot-modifier carriers to {}",
                root, e);
        }
    }

    /** True iff any loaded mob origin has non-empty drops. */
    public static boolean anyMobOriginHasDrops() {
        for (var mo : MobOriginDataManager.INSTANCE.getMobOrigins().values()) {
            if (!mo.dropRules().isEmpty()) return true;
        }
        return false;
    }

    private static void writeIfChanged(Path file, String content) throws IOException {
        if (Files.isRegularFile(file)) {
            String existing = Files.readString(file, StandardCharsets.UTF_8);
            if (existing.equals(content)) return;
        }
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static JsonObject modifierJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", NeoOrigins.MOD_ID + ":mob_origin_drops");
        o.add("conditions", new JsonArray());
        return o;
    }

    private static JsonObject entriesJson() {
        JsonObject o = new JsonObject();
        o.addProperty("replace", false);
        JsonArray entries = new JsonArray();
        entries.add(NeoOrigins.MOD_ID + ":mob_origin_drops");
        o.add("entries", entries);
        return o;
    }
}
