package com.cyberday1.neoorigins.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code ServerFunctionLibrary} takes its {@code IoSupplier} from
 * {@code FileToIdConverter.listMatchingResources}, i.e. from
 * {@link PackResources#listResources} — {@code FallbackResourceManager} forwards
 * each pack's listing supplier straight into the {@code Resource} it builds, and
 * never calls {@code getResource} for those. So the mcfunction rewrite has to
 * hang off the listing path or it silently no-ops; this pins that down along
 * with the stream's re-runnability and encoding behaviour.
 */
class LegacyFolderPackFunctionStreamTest {

    /** Minimal in-memory pack: a path → UTF-8 body map, listed under its own prefix. */
    private static final class FakePack implements PackResources {
        private final Map<String, String> files = new HashMap<>();
        private int opens = 0;

        @Override public IoSupplier<InputStream> getRootResource(String... elements) { return null; }

        @Override public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
            String body = files.get(location.getPath());
            if (body == null) return null;
            return () -> {
                opens++;
                return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            };
        }

        @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            files.forEach((p, body) -> {
                if (!p.startsWith(path + "/")) return;
                Identifier loc = Identifier.fromNamespaceAndPath(namespace, p);
                output.accept(loc, getResource(type, loc));
            });
        }

        @Override public Set<String> getNamespaces(PackType type) { return Set.of("fairytale"); }
        @Override public <T> T getMetadataSection(MetadataSectionType<T> serializer) { return null; }
        @Override public PackLocationInfo location() {
            return new PackLocationInfo("fake", Component.literal("fake"), PackSource.WORLD, Optional.empty());
        }
        @Override public boolean isHidden() { return false; }
        @Override public void close() {}
    }

    private static String read(IoSupplier<InputStream> io) throws Exception {
        try (InputStream in = io.get()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static IoSupplier<InputStream> listOne(PackResources pack, String path) {
        var holder = new IoSupplier[1];
        pack.listResources(PackType.SERVER_DATA, "fairytale", path, (loc, io) -> holder[0] = io);
        assertNotNull(holder[0], "nothing listed under " + path);
        @SuppressWarnings("unchecked")
        IoSupplier<InputStream> io = (IoSupplier<InputStream>) holder[0];
        return io;
    }

    /**
     * Covers the whole listing contract in one pass: content is rewritten, the
     * file's layout survives, the supplier is re-runnable, the 1.20 plural-folder
     * remap composes with the rewrite, and non-mcfunction data is untouched.
     */
    @Test
    void listedFunctionsAreRewrittenWithoutDisturbingAnythingElse() throws Exception {
        FakePack inner = new FakePack();
        // CRLF, a comment, a blank line and a trailing newline — all must survive.
        inner.files.put("function/powers/remove_vine_segment.mcfunction",
            "# Play sound and particles\r\n"
                + "\r\n"
                + "particle minecraft:block minecraft:twisting_vines ~ ~ ~ 0.2 0.2 0.2 0.05 5\r\n");
        PackResources pack = new LegacyFolderPackResources(inner);

        IoSupplier<InputStream> io = listOne(pack, "function");
        String first = read(io);
        assertEquals("# Play sound and particles\r\n"
                + "\r\n"
                + "particle minecraft:block{block_state:\"minecraft:twisting_vines\"} ~ ~ ~ 0.2 0.2 0.2 0.05 5\r\n",
            first);
        assertEquals(first, read(io), "the supplier must be re-runnable, not consume-once");
        assertEquals(2, inner.opens, "each get() must re-read the delegate");

        // A legacy pack's functions/ dir is listed under function/ AND its
        // contents rewritten — the two shims have to compose. Meanwhile
        // non-mcfunction data must come through byte-identical.
        FakePack legacy = new FakePack();
        legacy.files.put("functions/powers/magic_beans.mcfunction",
            "give @s minecraft:warped_fungus{Enchantments:[{id:\"minecraft:unbreaking\",lvl:1}]} 1");
        legacy.files.put("powers/forest_stealth.json", "{ \"type\": \"apoli:particle\" }");
        PackResources legacyPack = new LegacyFolderPackResources(legacy);

        assertEquals("give @s minecraft:warped_fungus[minecraft:enchantments={\"minecraft:unbreaking\":1}] 1",
            read(listOne(legacyPack, "function")));
        assertEquals("{ \"type\": \"apoli:particle\" }", read(listOne(legacyPack, "powers")));
    }
}
