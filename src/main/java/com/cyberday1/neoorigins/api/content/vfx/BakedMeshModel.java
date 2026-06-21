package com.cyberday1.neoorigins.api.content.vfx;

import com.cyberday1.neoorigins.NeoOrigins;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Loads a pre-baked triangle-mesh blob ({@code .bakedmesh}, the {@code "NBM1"}
 * format produced by the offline {@code bake_glb.js} baker) and renders it with
 * the same per-vertex API as {@link GeoJsonModel}.
 *
 * <p>{@link GeoJsonModel} can only bake Bedrock cube-soup; arbitrary triangle
 * meshes (glTF/GLB) have no cube representation. Rather than ship a glTF parser
 * in the mod, the model is converted offline to a flat vertex array — triangles
 * expanded into degenerate quads ({@code v0,v1,v2,v2}) so the output matches the
 * quad-based {@code VertexConsumer} path this and {@code GeoJsonModel} both use.
 *
 * <p>Blob layout (little-endian):
 * <pre>
 *   magic     : 4 bytes ASCII "NBM1"
 *   quadCount : uint32
 *   radius    : float32  (bounding radius in MODEL units, pre-scale)
 *   vertices  : quadCount * 4 * 8 float32 — x,y,z,u,v,nx,ny,nz
 * </pre>
 *
 * <p>Positions are recentered to the origin at bake time but kept in the source
 * model's units, which are typically far larger than a block. {@link #load} takes
 * a {@code scale} that pre-multiplies every position (and the radius) so the
 * baked float[] ends up in block units, matching {@code GeoJsonModel}'s
 * convention — the renderer's own scale stays a final cosmetic fine-tune.
 *
 * <p>API status: stable. Added in 2.2.
 */
public final class BakedMeshModel {

    /** 8 floats per vertex: x, y, z, u, v, nx, ny, nz. */
    public static final int FLOATS_PER_VERTEX = 8;

    /** 4 vertices per quad. */
    public static final int VERTICES_PER_QUAD = 4;

    private final float[] vertexData;
    private final int quadCount;
    private final float modelRadius;

    private BakedMeshModel(float[] vertexData, int quadCount, float modelRadius) {
        this.vertexData = vertexData;
        this.quadCount = quadCount;
        this.modelRadius = modelRadius;
    }

    /**
     * Load a {@code .bakedmesh} blob from the classpath, pre-scaling every
     * position (and the radius) by {@code scale}. Returns a tiny fallback quad
     * if the file is missing or malformed, so the renderer never crashes from a
     * bad asset.
     *
     * @param classpathPath absolute classpath path, e.g. {@code "/assets/mymod/geo/x.bakedmesh"}
     * @param scale         multiplier applied to all vertex positions and the radius
     */
    public static BakedMeshModel load(String classpathPath, float scale) {
        try (InputStream is = BakedMeshModel.class.getResourceAsStream(classpathPath)) {
            if (is == null) {
                NeoOrigins.LOGGER.error("[vfx] baked mesh not found: {}", classpathPath);
                return fallback();
            }
            byte[] bytes = is.readAllBytes();
            ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            if (bb.remaining() < 12
                || bb.get() != 'N' || bb.get() != 'B' || bb.get() != 'M' || bb.get() != '1') {
                NeoOrigins.LOGGER.error("[vfx] {} is not an NBM1 baked mesh", classpathPath);
                return fallback();
            }
            int quadCount = bb.getInt();
            float radius = bb.getFloat();
            int floats = quadCount * VERTICES_PER_QUAD * FLOATS_PER_VERTEX;
            if (bb.remaining() < floats * 4) {
                NeoOrigins.LOGGER.error("[vfx] {} truncated: expected {} floats", classpathPath, floats);
                return fallback();
            }
            float[] arr = new float[floats];
            for (int i = 0; i < quadCount * VERTICES_PER_QUAD; i++) {
                int off = i * FLOATS_PER_VERTEX;
                arr[off]     = bb.getFloat() * scale; // x
                arr[off + 1] = bb.getFloat() * scale; // y
                arr[off + 2] = bb.getFloat() * scale; // z
                arr[off + 3] = bb.getFloat();         // u
                arr[off + 4] = bb.getFloat();         // v
                arr[off + 5] = bb.getFloat();         // nx
                arr[off + 6] = bb.getFloat();         // ny
                arr[off + 7] = bb.getFloat();         // nz
            }
            NeoOrigins.LOGGER.info("[vfx] loaded baked mesh {}: {} quads, radius {} (scale {})",
                classpathPath, quadCount, radius * scale, scale);
            return new BakedMeshModel(arr, quadCount, radius * scale);
        } catch (Exception e) {
            NeoOrigins.LOGGER.error("[vfx] failed to load baked mesh {}: {}", classpathPath, e.getMessage());
            return fallback();
        }
    }

    private static BakedMeshModel fallback() {
        // A single 0.25-block quad facing +Z, so a bad asset is visible but harmless.
        float h = 0.125f;
        float[] arr = {
            -h, -h, 0f, 0.5f, 0.5f, 0f, 0f, 1f,
             h, -h, 0f, 0.5f, 0.5f, 0f, 0f, 1f,
             h,  h, 0f, 0.5f, 0.5f, 0f, 0f, 1f,
            -h,  h, 0f, 0.5f, 0.5f, 0f, 0f, 1f,
        };
        return new BakedMeshModel(arr, 1, h);
    }

    /** Render the baked model into {@code consumer} with the given transform + light values. */
    public void render(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();
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

    /** Render with per-vertex tint color + alpha. */
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

    /** Approximate bounding radius in blocks (post-scale). */
    public float getRadius() { return modelRadius; }

    /** Debugging — quad count in the baked mesh. */
    public int getQuadCount() { return quadCount; }
}
