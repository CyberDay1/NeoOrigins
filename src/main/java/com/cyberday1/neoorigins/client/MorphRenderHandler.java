package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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
 *
 * <p><b>26.1 port note.</b> Entity rendering moved to a render-state submit
 * pipeline: {@code RenderPlayerEvent.Pre} now hands us the player's
 * {@link AvatarRenderState} (not the entity) plus a {@link SubmitNodeCollector},
 * and fires inside {@code AvatarRenderer.submit} — i.e. with the pose already
 * translated to the entity's render position by
 * {@code EntityRenderDispatcher.submit}. So instead of calling
 * {@code renderer.render(entity, ...)} we extract a render state from the dummy
 * ({@code dispatcher.extractEntity}) and submit it through the dummy's own
 * renderer. The nameplate comes for free: {@code EntityRenderer.submit} renders
 * it from {@code state.nameTag}/{@code nameTagAttachment}, which we copy across
 * from the player's already-gated avatar state.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public final class MorphRenderHandler {

    private MorphRenderHandler() {}

    /** Cached dummy entities keyed by "<playerEntityId>:<entityTypeId>". */
    private static final Map<String, Entity> DUMMIES = new ConcurrentHashMap<>();

    // ── Player render swap ──────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre<?> event) {
        AvatarRenderState state = event.getRenderState();
        Identifier morphType = ClientMorphState.getMorph(state.id);
        if (morphType == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!(mc.level.getEntity(state.id) instanceof Player player)) return;

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
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        syncDummyState(player, dummy, partialTick);

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        EntityRenderState dummyState = dispatcher.extractEntity(dummy, partialTick);

        // Carry the player's nameplate (already gated by vanilla's
        // shouldShowName rules during avatar-state extraction) and camera-
        // relative data onto the dummy state so EntityRenderer.submit renders
        // the name tag and lights the model exactly like the player would be.
        dummyState.nameTag = state.nameTag;
        dummyState.scoreText = state.scoreText;
        dummyState.nameTagAttachment = state.nameTagAttachment;
        dummyState.lightCoords = state.lightCoords;
        dummyState.distanceToCameraSq = state.distanceToCameraSq;
        dummyState.x = state.x;
        dummyState.y = state.y;
        dummyState.z = state.z;

        @SuppressWarnings("unchecked")
        EntityRenderer<?, EntityRenderState> renderer =
            (EntityRenderer<?, EntityRenderState>) dispatcher.getRenderer(dummyState);
        if (renderer == null) return;

        CameraRenderState camera =
            mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;

        // The pose is already at the player's render position (the dispatcher
        // translated before AvatarRenderer.submit fired this event), so the
        // dummy renders exactly where the player would.
        renderer.submit(dummyState, poseStack, collector, camera);

        // Held item(s) on the morph model (slime has no hand bone).
        renderHeldItem(player, poseStack, collector, dummyState.lightCoords, partialTick);
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

        // 26.1: renderItem dropped the leftHand flag — the display context
        // encodes handedness — and draws through the SubmitNodeCollector.
        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
            player, stack,
            right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            pose, event.getSubmitNodeCollector(), event.getPackedLight());
        pose.popPose();
    }

    // ── Dummy lifecycle ─────────────────────────────────────────────────────

    @Nullable
    private static Entity getOrCreateDummy(Player player, Identifier typeId) {
        String key = player.getId() + ":" + typeId;
        Entity cached = DUMMIES.get(key);
        if (cached != null) return cached;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (type == null || player.level() == null) return null;

        Entity created;
        try {
            // 26.1: EntityType.create requires an EntitySpawnReason; LOAD is the
            // no-side-effects choice for a render-only dummy.
            created = type.create(player.level(), EntitySpawnReason.LOAD);
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
                // (26.1: update() grew a positionScale parameter; 1.0 = adult.)
                float speed = player.walkAnimation.speed();
                living.walkAnimation.update(speed, 1.0f, 1.0f);
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
    private static void renderHeldItem(Player player, PoseStack poseStack,
                                       SubmitNodeCollector collector, int light, float partialTick) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        poseStack.pushPose();
        // Slime body is ~0.5 tall at size 1; float the item above and slightly
        // forward in the direction the player faces.
        float bodyYaw = lerp(partialTick, player.yBodyRotO, player.yBodyRot);
        double rad = Math.toRadians(bodyYaw);
        double forward = 0.35;
        double fx = -Math.sin(rad) * forward;
        double fz = Math.cos(rad) * forward;
        poseStack.translate(fx, 0.55, fz);
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        poseStack.scale(0.6f, 0.6f, 0.6f);

        // 26.1: ItemRenderer.renderStatic is gone; ItemInHandRenderer.renderItem
        // resolves the stack's model and submits it for any LivingEntity + context.
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer()
            .renderItem(player, stack, ItemDisplayContext.FIXED, poseStack, collector, light);
        poseStack.popPose();
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }
}
