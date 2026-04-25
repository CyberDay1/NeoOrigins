package com.cyberday1.neoorigins.compat.pehkui;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Runtime-reflected bridge to Pehkui's scale system.
 *
 * <p>NeoOrigins already scales players via the vanilla {@code minecraft:scale} attribute
 * (see {@code SizeScalingPower}), which covers visual/collision size for the player itself.
 * Pehkui, however, maintains its own independent scale state, so any other mod querying
 * {@code ScaleType.BASE.getScaleData(entity).getBaseScale()} would see {@code 1.0} even
 * when our origin shrinks/enlarges the player.
 *
 * <p>This bridge pushes the origin's scale onto Pehkui's {@code BASE} scale type when the
 * power is granted, and restores {@code 1.0} when it's revoked. Uses reflection so there's
 * no compile-time dependency on Pehkui; gracefully no-ops when Pehkui is absent.
 *
 * <p>Known limitation: directly setting {@code BASE} does not compose with Pehkui's own
 * {@code /scale} command — whichever runs last wins. A proper {@code ScaleModifier}
 * implementation would compose, but requires implementing a Pehkui interface that isn't on
 * the compile classpath. If users need composition, a {@code @Pseudo} mixin against
 * Pehkui's scale data calculation is the follow-up.
 */
public final class PehkuiBridge {

    private static final boolean LOADED;
    private static final Object BASE_SCALE_TYPE;
    private static final Method GET_SCALE_DATA;
    private static final Method SET_BASE_SCALE;
    private static final Method SET_TARGET_SCALE;

    private static volatile boolean warnedOnce = false;

    static {
        boolean loaded = false;
        Object baseType = null;
        Method getScaleData = null;
        Method setBase = null;
        Method setTarget = null;
        try {
            if (ModList.get().isLoaded("pehkui")) {
                // virtuoel.pehkui.api.ScaleTypes.BASE : ScaleType
                Class<?> scaleTypes = Class.forName("virtuoel.pehkui.api.ScaleTypes");
                baseType = scaleTypes.getField("BASE").get(null);
                Class<?> scaleType = Class.forName("virtuoel.pehkui.api.ScaleType");
                getScaleData = scaleType.getMethod("getScaleData", Entity.class);
                Class<?> scaleData = Class.forName("virtuoel.pehkui.api.ScaleData");
                setBase = scaleData.getMethod("setScale", float.class);
                setTarget = scaleData.getMethod("setTargetScale", float.class);
                loaded = true;
                NeoOrigins.LOGGER.info("Pehkui detected — origin scale will be mirrored to ScaleType.BASE.");
            }
        } catch (Throwable t) {
            NeoOrigins.LOGGER.warn("Pehkui present but bridge failed to initialize ({}); falling back to vanilla scale attribute only.", t.toString());
        }
        LOADED = loaded;
        BASE_SCALE_TYPE = baseType;
        GET_SCALE_DATA = getScaleData;
        SET_BASE_SCALE = setBase;
        SET_TARGET_SCALE = setTarget;
    }

    /** True if Pehkui is loaded and the reflective bridge succeeded. */
    public static boolean isAvailable() { return LOADED; }

    /**
     * Mirror an origin scale onto Pehkui's BASE scale for the entity. Safe to call
     * when Pehkui is absent (no-op).
     */
    public static void applyOriginScale(Entity entity, float scale) {
        if (!LOADED || entity == null) return;
        try {
            Object scaleData = GET_SCALE_DATA.invoke(BASE_SCALE_TYPE, entity);
            if (scaleData == null) return;
            SET_BASE_SCALE.invoke(scaleData, scale);
            SET_TARGET_SCALE.invoke(scaleData, scale);
        } catch (Throwable t) {
            if (!warnedOnce) {
                warnedOnce = true;
                NeoOrigins.LOGGER.warn("Pehkui bridge call failed: {}", t.toString());
            }
        }
    }

    /** Restore Pehkui BASE scale to 1.0 for the entity. */
    public static void clearOriginScale(Entity entity) {
        applyOriginScale(entity, 1.0f);
    }

    private PehkuiBridge() {}
}
