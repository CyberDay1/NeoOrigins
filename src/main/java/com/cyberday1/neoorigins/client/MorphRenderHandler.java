package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the {@code neoorigins:entity_model} morph: when a player carries an
 * {@code entity_model:*} capability (tracked per-player in {@link ClientMorphState}),
 * the vanilla player render is cancelled and a cached dummy entity of the target
 * type is drawn through its own vanilla renderer instead.
 *
 * <p>v1 implements {@code minecraft:slime}. The dummy cache, per-frame state copy,
 * held-item attachment, and first-person-hand suppression are all written as
 * type-agnostic extension points; only {@link #syncDummyState} and
 * {@link #renderHeldItem} carry slime-specific tuning, isolated for easy reuse.
 *
 * <p>Hitbox/eye-height are intentionally untouched — pair the power with
 * {@code neoorigins:size_scaling} to make the collision box match the silhouette.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public final class MorphRenderHandler {

    private MorphRenderHandler() {}

    /** Cached dummy entities keyed by "<playerEntityId>:<entityTypeId>". */
    private static final Map<String, Entity> DUMMIES = new ConcurrentHashMap<>();

    // ── Player render swap ──────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        ResourceLocation morphType = ClientMorphState.getMorph(player.getId());
        if (morphType == null) return;

        Entity dummy = getOrCreateDummy(player, morphType);
        if (dummy == null) {
            // Unknown / un-creatable entity type — leave the vanilla player
            // rendering intact rather than drawing nothing.
            return;
        }

        // From here we own the render for this player: skip vanilla body+arms.
        event.setCanceled(true);

        float partialTick = event.getPartialTick();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();

        syncDummyState(player, dummy, partialTick);

        // Interpolated body yaw, matching what the player renderer would use.
        float yaw = lerp(partialTick, player.yBodyRotO, player.yBodyRot);

        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> renderer =
            (EntityRenderer<Entity>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
        if (renderer == null) return;

        renderer.render(dummy, yaw, partialTick, poseStack, buffers, light);

        // Held item(s) on the morph model (slime has no hand bone).
        renderHeldItem(player, dummy, poseStack, buffers, light, partialTick);

        // Re-draw the player's nameplate, which the cancelled event would drop.
        renderMorphNameTag(player, poseStack, buffers, light);
    }

    // ── First-person hands ──────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!ClientMorphState.isMorphed(player.getId())) return;

        // While morphed we own first-person rendering. A morph (e.g. a slime)
        // has no humanoid arm, so we always suppress vanilla's arm+item draw and
        // instead render ONLY the held item — letting players still see what
        // they're holding. An empty hand draws nothing.
        event.setCanceled(true);

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND
            ? player.getMainArm()
            : player.getMainArm().getOpposite();
        boolean right = arm == HumanoidArm.RIGHT;
        int i = right ? 1 : -1;
        float equip = event.getEquipProgress();
        float swing = event.getSwingProgress();

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // Replicate the standard (non-use) first-person item placement from
        // ItemInHandRenderer.renderArmWithItem: swing offset, then
        // applyItemArmTransform, then applyItemArmAttackTransform. We
        // deliberately skip the per-use animations (eat/bow/spear/map) and the
        // arm draw — the item still shows, just without those special poses.
        float sx = -0.4F * Mth.sin(Mth.sqrt(swing) * (float) Math.PI);
        float sy = 0.2F * Mth.sin(Mth.sqrt(swing) * (float) (Math.PI * 2));
        float sz = -0.2F * Mth.sin(swing * (float) Math.PI);
        pose.translate(i * sx, sy, sz);
        // applyItemArmTransform
        pose.translate(i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        // applyItemArmAttackTransform
        float a1 = Mth.sin(swing * swing * (float) Math.PI);
        pose.mulPose(Axis.YP.rotationDegrees(i * (45.0F + a1 * -20.0F)));
        float a2 = Mth.sin(Mth.sqrt(swing) * (float) Math.PI);
        pose.mulPose(Axis.ZP.rotationDegrees(i * a2 * -20.0F));
        pose.mulPose(Axis.XP.rotationDegrees(a2 * -80.0F));
        pose.mulPose(Axis.YP.rotationDegrees(i * -45.0F));

        mc.gameRenderer.itemInHandRenderer.renderItem(
            player, stack,
            right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            !right, pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    // ── Dummy lifecycle ─────────────────────────────────────────────────────

    @Nullable
    private static Entity getOrCreateDummy(Player player, ResourceLocation typeId) {
        String key = player.getId() + ":" + typeId;
        Entity cached = DUMMIES.get(key);
        if (cached != null) return cached;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (type == null || player.level() == null) return null;

        Entity created;
        try {
            created = type.create(player.level());
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("entity_model: failed to create dummy '{}': {}", typeId, e.getMessage());
            created = null;
        }
        if (created == null) return null;

        // Per-type one-time setup (extension point).
        if (created instanceof Slime slime) {
            // Size 1 = the smallest vanilla slime, closest to a player footprint.
            slime.setSize(1, false);
        }

        DUMMIES.put(key, created);
        // Opportunistically prune entries for players that no longer morph.
        pruneStale();
        return created;
    }

    private static void pruneStale() {
        if (DUMMIES.size() < 32) return; // cheap guard; only sweep when it grows
        DUMMIES.keySet().removeIf(k -> {
            int colon = k.indexOf(':');
            if (colon <= 0) return true;
            try {
                int id = Integer.parseInt(k.substring(0, colon));
                return !ClientMorphState.isMorphed(id);
            } catch (NumberFormatException e) {
                return true;
            }
        });
    }

    /** Drop the morph cache on world unload so stale dummies don't leak. */
    public static void clearCache() {
        DUMMIES.clear();
    }

    // ── Per-frame state copy (extension point) ──────────────────────────────

    /**
     * Copy the player's orientation, walk and swing animation onto the dummy so
     * it animates in place. Slime additionally needs its squish driven from its
     * own tick so {@code Slime.squish}/{@code oSquish} oscillate.
     */
    private static void syncDummyState(Player player, Entity dummy, float partialTick) {
        dummy.setPos(player.getX(), player.getY(), player.getZ());

        dummy.yRotO = player.yRotO;
        dummy.setYRot(player.getYRot());
        dummy.xRotO = player.xRotO;
        dummy.setXRot(player.getXRot());
        dummy.setPose(player.getPose());
        dummy.setShiftKeyDown(player.isShiftKeyDown());
        dummy.setOnGround(player.onGround());

        if (dummy instanceof LivingEntity living) {
            living.yBodyRot = player.yBodyRot;
            living.yBodyRotO = player.yBodyRotO;
            living.yHeadRot = player.yHeadRot;
            living.yHeadRotO = player.yHeadRotO;

            living.swinging = player.swinging;
            living.swingTime = player.swingTime;
            living.attackAnim = player.attackAnim;
            living.oAttackAnim = player.oAttackAnim;
        }

        // Tick-gated simulation: advance the dummy exactly once per client game
        // tick (20 TPS), NEVER per render frame. RenderPlayerEvent.Pre fires every
        // frame, so driving animation here would run it at the display framerate —
        // limb swing and GeckoLib/Citadel controllers would then play 3-7x too fast
        // (the "gorilla limbs too fast" symptom). Stepping on tick boundaries keeps
        // motion framerate-independent and leaves oState/state one tick apart for the
        // renderer's partial-tick interpolation.
        int playerTick = player.tickCount;
        if (dummy.tickCount != playerTick) {
            // Advancing tickCount is what unfreezes time-driven renderers: GeckoLib
            // seeks its animation controllers off the entity's tick, so a dummy
            // stuck at tick 0 only ever shows head-tracking (a per-frame render
            // param) — the "bear: only the head moves" symptom before this fix.
            dummy.tickCount = playerTick;

            if (dummy instanceof LivingEntity living) {
                // Mirror LivingEntity.tick(): advance walkAnimation.position at
                // 20 TPS from the player's current walk speed. The renderer
                // interpolates with partialTick; GeckoLib also reads
                // walkAnimation.speed() to choose its walk-vs-idle animation.
                float speed = player.walkAnimation.speed();
                living.walkAnimation.update(speed, 1.0f);
            }

            if (dummy instanceof Slime slime) {
                // Mirror the relevant lines of Slime.tick() without AI/physics:
                // squish eases toward targetSquish, which decays each tick.
                slime.oSquish = slime.squish;
                slime.squish += (slime.targetSquish - slime.squish) * 0.5F;
                slime.targetSquish *= 0.6F;
                // Give it an occasional bob so a stationary morph still breathes.
                if (playerTick % 40 == 0) {
                    slime.targetSquish = 1.0F;
                }
            }
        }
    }

    // ── Held item (extension point) ─────────────────────────────────────────

    /**
     * Render the player's main-hand item on the morph. Slime has no hand bone,
     * so the item floats just above/in-front of the body. For future humanoid
     * morphs this method is where you would resolve and translate to a real
     * hand bone instead.
     */
    private static void renderHeldItem(Player player, Entity dummy, PoseStack poseStack,
                                       MultiBufferSource buffers, int light, float partialTick) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        poseStack.pushPose();
        // Slime body is ~0.5 tall at size 1; float the item above and slightly
        // forward in the direction the player faces.
        float bodyYaw = lerp(partialTick, player.yBodyRotO, player.yBodyRot);
        double rad = Math.toRadians(bodyYaw);
        double forward = 0.35;
        double fx = -Math.sin(rad) * forward;
        double fz = Math.cos(rad) * forward;
        poseStack.translate(fx, 0.55, fz);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-bodyYaw));
        poseStack.scale(0.6f, 0.6f, 0.6f);

        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED,
            light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
            poseStack, buffers, player.level(), player.getId());
        poseStack.popPose();
    }

    // ── Nameplate (mirrors EntityRenderer.renderNameTag) ────────────────────

    /**
     * Re-render the player's display name above the morph. Cancelling
     * {@code RenderPlayerEvent.Pre} skips vanilla name rendering, so we replicate
     * it here, gated on the same {@code shouldShowName}-style conditions vanilla
     * uses for players.
     */
    private static void renderMorphNameTag(Player player, PoseStack poseStack,
                                           MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        var dispatcher = mc.getEntityRenderDispatcher();

        // Vanilla gate: name visible, or custom-named + looked at. Also respect
        // the "show own name only in 3rd person / debug" feel by deferring to
        // the entity's own shouldShowName flag.
        boolean show = player.shouldShowName()
            || (player.hasCustomName() && player == dispatcher.crosshairPickEntity);
        if (!show) return;

        double distSq = dispatcher.distanceToSqr(player);
        if (!net.neoforged.neoforge.client.ClientHooks.isNameplateInRenderDistance(player, distSq)) return;

        Component name = player.getDisplayName();
        if (name == null) return;

        Vec3 attach = player.getAttachments().getNullable(
            net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0, player.getViewYRot(1.0f));
        if (attach == null) return;

        boolean seeThrough = !player.isDiscrete();
        int yOffset = "deadmau5".equals(name.getString()) ? -10 : 0;

        poseStack.pushPose();
        poseStack.translate(attach.x, attach.y + 0.5, attach.z);
        poseStack.mulPose(dispatcher.cameraOrientation());
        poseStack.scale(0.025f, -0.025f, 0.025f);
        org.joml.Matrix4f matrix = poseStack.last().pose();
        float bgOpacity = mc.options.getBackgroundOpacity(0.25f);
        int bgColor = (int) (bgOpacity * 255.0f) << 24;
        net.minecraft.client.gui.Font font = mc.font;
        float x = (float) (-font.width(name) / 2);
        font.drawInBatch(name, x, (float) yOffset, 553648127, false, matrix, buffers,
            seeThrough ? net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH
                       : net.minecraft.client.gui.Font.DisplayMode.NORMAL,
            bgColor, light);
        if (seeThrough) {
            font.drawInBatch(name, x, (float) yOffset, -1, false, matrix, buffers,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, light);
        }
        poseStack.popPose();
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }
}
