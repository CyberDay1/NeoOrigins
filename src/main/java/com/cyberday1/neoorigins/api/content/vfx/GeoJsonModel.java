package com.cyberday1.neoorigins.api.content.vfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads a Bedrock-format {@code .geo.json} model at classload time and bakes
 * its cubes into a flat {@code float[]} vertex array for fast rendering.
 *
 * <p>No GeckoLib dependency — the model loader and the renderer are hand-rolled.
 * Performs occupancy-based face culling (cubes that share a face with an
 * adjacent cube skip the hidden face) so filled shapes don't pay for interior
 * vertices.
 *
 * <p>Typical usage — subclass or instantiate at renderer creation time, store
 * the result as a {@code static final}, call {@link #render(PoseStack, VertexConsumer, int, int)}
 * during each render pass:
 *
 * <pre>{@code
 * private static final GeoJsonModel MODEL = GeoJsonModel.load(
 *     "/assets/mymod/geo/my_model.geo.json");
 *
 * @Override
 * public void submit(MyRenderState state, PoseStack poseStack, ...) {
 *     MODEL.render(poseStack, vertexConsumer, 0xF000F0, OverlayTexture.NO_OVERLAY);
 * }
 * }</pre>
 *
 * <p>Expects the geo file to contain at least one bone with cubes. Multi-bone
 * skeletal models with animations are beyond this class — use a single-bone
 * cube soup (the common VFX pattern) or invest in GeckoLib.
 *
 * <p>API status: stable. Added in 2.0.
 */
public final class GeoJsonModel {

    /** 8 floats per vertex: x, y, z, u, v, nx, ny, nz. */
    public static final int FLOATS_PER_VERTEX = 8;

    /** 4 vertices per quad. */
    public static final int VERTICES_PER_QUAD = 4;

    private final float[] vertexData;
    private final int quadCount;
    private final float modelRadius;

    private GeoJsonModel(float[] vertexData, int quadCount, float modelRadius) {
        this.vertexData = vertexData;
        this.quadCount = quadCount;
        this.modelRadius = modelRadius;
    }

    /**
     * Load a {@code .geo.json} from the classpath. Returns a fallback
     * minimal cube if the file is missing or malformed, so the renderer
     * never crashes from a bad asset.
     *
     * @param classpathPath absolute classpath path, e.g. {@code "/assets/mymod/geo/my.geo.json"}
     */
    public static GeoJsonModel load(String classpathPath) {
        try (InputStream is = GeoJsonModel.class.getResourceAsStream(classpathPath)) {
            if (is == null) {
                NeoOrigins.LOGGER.error("[vfx] geo model not found: {}", classpathPath);
                return fallback();
            }
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            return parse(root, classpathPath);
        } catch (Exception e) {
            NeoOrigins.LOGGER.error("[vfx] failed to load geo model {}: {}", classpathPath, e.getMessage());
            return fallback();
        }
    }

    private static GeoJsonModel parse(JsonObject root, String path) {
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            NeoOrigins.LOGGER.error("[vfx] {} has no minecraft:geometry array", path);
            return fallback();
        }
        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        float texW = description != null && description.has("texture_width")
            ? description.get("texture_width").getAsFloat() : 64f;
        float texH = description != null && description.has("texture_height")
            ? description.get("texture_height").getAsFloat() : 64f;

        JsonArray bones = geometry.getAsJsonArray("bones");
        List<int[]> cubes = new ArrayList<>();
        if (bones != null) {
            for (JsonElement boneEl : bones) {
                JsonObject bone = boneEl.getAsJsonObject();
                if (!bone.has("cubes")) continue;
                for (JsonElement cubeEl : bone.getAsJsonArray("cubes")) {
                    JsonObject cube = cubeEl.getAsJsonObject();
                    JsonArray origin = cube.getAsJsonArray("origin");
                    JsonArray size = cube.getAsJsonArray("size");
                    JsonArray uv = cube.has("uv") ? cube.getAsJsonArray("uv") : null;
                    if (origin == null || size == null) continue;
                    int ox = origin.get(0).getAsInt();
                    int oy = origin.get(1).getAsInt();
                    int oz = origin.get(2).getAsInt();
                    int w = size.get(0).getAsInt();
                    int h = size.get(1).getAsInt();
                    int d = size.get(2).getAsInt();
                    int uvX = uv != null ? uv.get(0).getAsInt() : 0;
                    int uvY = uv != null ? uv.get(1).getAsInt() : 0;
                    cubes.add(new int[]{ox, oy, oz, w, h, d, uvX, uvY});
                }
            }
        }
        if (cubes.isEmpty()) {
            NeoOrigins.LOGGER.error("[vfx] {} has no cubes", path);
            return fallback();
        }

        // Occupancy set for face-culling.
        Set<Long> occupancy = new HashSet<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] c : cubes) {
            for (int x = c[0]; x < c[0] + c[3]; x++) {
                for (int y = c[1]; y < c[1] + c[4]; y++) {
                    for (int z = c[2]; z < c[2] + c[5]; z++) {
                        occupancy.add(key(x, y, z));
                        if (x < minX) minX = x;
                        if (y < minY) minY = y;
                        if (z < minZ) minZ = z;
                        if (x + 1 > maxX) maxX = x + 1;
                        if (y + 1 > maxY) maxY = y + 1;
                        if (z + 1 > maxZ) maxZ = z + 1;
                    }
                }
            }
        }
        int cx = (minX + maxX) / 2, cy = (minY + maxY) / 2, cz = (minZ + maxZ) / 2;
        float maxDist = 0f;
        for (int[] c : cubes) {
            float dx = c[0] + c[3] * 0.5f - cx;
            float dy = c[1] + c[4] * 0.5f - cy;
            float dz = c[2] + c[5] * 0.5f - cz;
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > maxDist) maxDist = d;
        }

        // Bake unit-cube faces (scaled to Minecraft units: 1 cube pixel = 1/16 block).
        List<Float> verts = new ArrayList<>();
        int quadCount = 0;
        for (int[] c : cubes) {
            for (int dx = 0; dx < c[3]; dx++) {
                for (int dy = 0; dy < c[4]; dy++) {
                    for (int dz = 0; dz < c[5]; dz++) {
                        int x = c[0] + dx, y = c[1] + dy, z = c[2] + dz;
                        // Six faces — skip if the neighbouring cell is occupied.
                        if (!occupancy.contains(key(x, y, z + 1))) { emitFace(verts, x, y, z, 0, 0, 1, c[6], c[7], texW, texH); quadCount++; }
                        if (!occupancy.contains(key(x, y, z - 1))) { emitFace(verts, x, y, z, 0, 0, -1, c[6], c[7], texW, texH); quadCount++; }
                        if (!occupancy.contains(key(x + 1, y, z))) { emitFace(verts, x, y, z, 1, 0, 0, c[6], c[7], texW, texH); quadCount++; }
                        if (!occupancy.contains(key(x - 1, y, z))) { emitFace(verts, x, y, z, -1, 0, 0, c[6], c[7], texW, texH); quadCount++; }
                        if (!occupancy.contains(key(x, y + 1, z))) { emitFace(verts, x, y, z, 0, 1, 0, c[6], c[7], texW, texH); quadCount++; }
                        if (!occupancy.contains(key(x, y - 1, z))) { emitFace(verts, x, y, z, 0, -1, 0, c[6], c[7], texW, texH); quadCount++; }
                    }
                }
            }
        }

        float[] arr = new float[verts.size()];
        for (int i = 0; i < verts.size(); i++) arr[i] = verts.get(i);
        return new GeoJsonModel(arr, quadCount, maxDist / 16f);
    }

    private static long key(int x, int y, int z) {
        return (((long) (x + 512)) << 40) | (((long) (y + 512)) << 20) | ((long) (z + 512));
    }

    /** Emit one 4-vertex quad face for a 1×1×1 unit cube at (x,y,z) with normal (nx,ny,nz). */
    private static void emitFace(List<Float> out, int x, int y, int z,
                                 int nx, int ny, int nz, int uvX, int uvY, float texW, float texH) {
        // Convert from Blockbench pixel units to Minecraft block units.
        final float s = 1f / 16f;
        float fx = x * s, fy = y * s, fz = z * s;
        final float unit = s;
        float u0 = uvX / texW, v0 = uvY / texH, u1 = (uvX + 1) / texW, v1 = (uvY + 1) / texH;

        // 4 corners of the face in CCW winding when looking along the normal
        float[][] corners;
        if (nz == 1)        corners = new float[][]{{fx, fy, fz + unit}, {fx + unit, fy, fz + unit}, {fx + unit, fy + unit, fz + unit}, {fx, fy + unit, fz + unit}};
        else if (nz == -1)  corners = new float[][]{{fx + unit, fy, fz}, {fx, fy, fz}, {fx, fy + unit, fz}, {fx + unit, fy + unit, fz}};
        else if (nx == 1)   corners = new float[][]{{fx + unit, fy, fz + unit}, {fx + unit, fy, fz}, {fx + unit, fy + unit, fz}, {fx + unit, fy + unit, fz + unit}};
        else if (nx == -1)  corners = new float[][]{{fx, fy, fz}, {fx, fy, fz + unit}, {fx, fy + unit, fz + unit}, {fx, fy + unit, fz}};
        else if (ny == 1)   corners = new float[][]{{fx, fy + unit, fz + unit}, {fx + unit, fy + unit, fz + unit}, {fx + unit, fy + unit, fz}, {fx, fy + unit, fz}};
        else                corners = new float[][]{{fx, fy, fz}, {fx + unit, fy, fz}, {fx + unit, fy, fz + unit}, {fx, fy, fz + unit}};

        float[][] uvs = new float[][]{{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        for (int i = 0; i < 4; i++) {
            out.add(corners[i][0]); out.add(corners[i][1]); out.add(corners[i][2]);
            out.add(uvs[i][0]); out.add(uvs[i][1]);
            out.add((float) nx); out.add((float) ny); out.add((float) nz);
        }
    }

    private static GeoJsonModel fallback() {
        // 1×1×1 box centered at origin as the crash-proof fallback.
        List<Float> v = new ArrayList<>();
        emitFace(v, 0, 0, 0, 0, 0, 1, 0, 0, 16, 16);
        emitFace(v, 0, 0, 0, 0, 0, -1, 0, 0, 16, 16);
        emitFace(v, 0, 0, 0, 1, 0, 0, 0, 0, 16, 16);
        emitFace(v, 0, 0, 0, -1, 0, 0, 0, 0, 16, 16);
        emitFace(v, 0, 0, 0, 0, 1, 0, 0, 0, 16, 16);
        emitFace(v, 0, 0, 0, 0, -1, 0, 0, 0, 16, 16);
        float[] arr = new float[v.size()];
        for (int i = 0; i < v.size(); i++) arr[i] = v.get(i);
        return new GeoJsonModel(arr, 6, 0.125f);
    }

    /** Render the baked model into {@code consumer} with the given transform + light values. */
    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay) {
        render(poseStack.last(), consumer, packedLight, packedOverlay);
    }

    /**
     * Render given an already-resolved {@link PoseStack.Pose}. Use this when
     * inside a {@code submitCustomGeometry} lambda (the lambda receives a
     * {@code Pose} directly — passing that keeps the transforms your outer
     * PoseStack already applied).
     */
    public void render(PoseStack.Pose pose, VertexConsumer consumer, int packedLight, int packedOverlay) {
        for (int i = 0; i < quadCount; i++) {
            for (int v = 0; v < VERTICES_PER_QUAD; v++) {
                int off = (i * VERTICES_PER_QUAD + v) * FLOATS_PER_VERTEX;
                consumer.addVertex(pose, vertexData[off], vertexData[off + 1], vertexData[off + 2])
                    .setColor(255, 255, 255, 255)
                    .setUv(vertexData[off + 3], vertexData[off + 4])
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(pose, vertexData[off + 5], vertexData[off + 6], vertexData[off + 7]);
            }
        }
    }

    /**
     * Render with per-vertex tint color + alpha. Useful for "pulse an
     * effect color through the model" animations.
     */
    public void renderTinted(PoseStack poseStack, VertexConsumer consumer,
                             int r, int g, int b, int a, int packedLight, int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < quadCount; i++) {
            for (int v = 0; v < VERTICES_PER_QUAD; v++) {
                int off = (i * VERTICES_PER_QUAD + v) * FLOATS_PER_VERTEX;
                consumer.addVertex(pose, vertexData[off], vertexData[off + 1], vertexData[off + 2])
                    .setColor(r, g, b, a)
                    .setUv(vertexData[off + 3], vertexData[off + 4])
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(pose, vertexData[off + 5], vertexData[off + 6], vertexData[off + 7]);
            }
        }
    }

    /** Approximate bounding radius in blocks. Useful for visual scale adjustments. */
    public float getRadius() { return modelRadius; }

    /** Debugging — quad count in the baked mesh. */
    public int getQuadCount() { return quadCount; }
}
