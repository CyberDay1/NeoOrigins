package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.morph.MorphSpec;
import com.cyberday1.neoorigins.power.morph.MorphVariants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the {@code neoorigins:entity_model} morph: when a player has a morph
 * recorded in {@link ClientMorphState}, the vanilla player render is cancelled
 * and a cached dummy entity of the target type is drawn through its own vanilla
 * renderer instead.
 *
 * <p>Everything about the morph's appearance arrives in the synced
 * {@link MorphSpec}: which entity to draw, partial NBT to pick a variant, a
 * visual scale, and whether to draw the held item in third and first person.
 * The dummy cache, per-frame state copy, held-item attachment, and
 * first-person-hand suppression are type-agnostic extension points; only
 * {@link #syncDummyState} and {@link #renderHeldItem} carry slime-specific
 * tuning, isolated for easy reuse.
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

    /**
     * Cached dummy entities keyed by {@code <playerEntityId>|<entityTypeId>|<nbtHash>}.
     * The NBT is part of the key because it is applied once at creation — a
     * player whose morph switches from a white sheep to a black one must get a
     * fresh dummy rather than the stale cached one.
     */
    private static final Map<String, Entity> DUMMIES = new ConcurrentHashMap<>();

    /**
     * Entity types we have already proven cannot back a morph — unknown ids,
     * types that throw on creation, and types with no registered renderer.
     * Without this, a bad {@code entity_type} would retry (and re-log) every
     * single frame for as long as the power is held.
     */
    private static final Set<Identifier> UNRENDERABLE = ConcurrentHashMap.newKeySet();

    /**
     * Per renderer class: does it draw held items through its own layers? See
     * {@link #drawsHeldItems}. Keyed by class because the layer list is built
     * once, in the renderer's constructor, and never changes afterwards.
     */
    private static final Map<Class<?>, Boolean> HELD_ITEM_RENDERERS = new ConcurrentHashMap<>();

    /**
     * Shadow radius of each morphed player's dummy, by player entity id, as
     * vanilla worked it out while extracting the dummy's render state.
     *
     * <p>Recorded rather than asked for, because the two happen in different
     * phases: a shadow radius is decided during extraction, and this class only
     * gets to see a morph during submission. So the value read back is the one
     * from the previous frame — invisible in practice, since it only changes
     * when the morph itself does, and the frame it changes on falls back to the
     * player's own shadow.
     */
    private static final Map<Integer, Float> SHADOW_RADII = new ConcurrentHashMap<>();

    // ── Player render swap ──────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre<?> event) {
        AvatarRenderState state = event.getRenderState();
        MorphSpec spec = ClientMorphState.getSpec(state.id);
        // A morph without an entity_type carries only non-model tweaks, so the
        // vanilla player render stands.
        if (spec == null || !spec.hasModel()) return;
        Identifier morphType = spec.entityType().orElseThrow();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!(mc.level.getEntity(state.id) instanceof Player player)) return;

        Entity dummy = getOrCreateDummy(player, spec, morphType);
        if (dummy == null) {
            // Unknown / un-creatable entity type — leave the vanilla player
            // rendering intact rather than drawing nothing.
            return;
        }

        float partialTick = event.getPartialTick();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        syncDummyState(player, dummy, spec, partialTick);

        // Extract and resolve BEFORE cancelling. extractEntity looks the
        // renderer up itself and rethrows anything that goes wrong as a
        // ReportedException — i.e. a hard crash — so a type with no renderer
        // must never reach it. Cancelling first would also leave the player
        // invisible on any early return below.
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        EntityRenderState dummyState = extractDummyState(dispatcher, dummy, morphType, partialTick);
        if (dummyState == null) return;
        // Vanilla filled this in for the dummy on the way through; it is the
        // shadow the morph would cast, and the only place it can be had.
        SHADOW_RADII.put(state.id, dummyState.shadowRadius);

        @SuppressWarnings("unchecked")
        EntityRenderer<?, EntityRenderState> renderer =
            (EntityRenderer<?, EntityRenderState>) resolveRenderer(dispatcher, dummyState, morphType);
        if (renderer == null) return;

        // From here we own the render for this player: skip vanilla body+arms.
        event.setCanceled(true);

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

        CameraRenderState camera =
            mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;

        // The pose is already at the player's render position (the dispatcher
        // translated before AvatarRenderer.submit fired this event), so the
        // dummy renders exactly where the player would. The visual scale wraps
        // the model AND the held item so the two stay proportional. Unlike on
        // 1.21.1 it also takes the nameplate with it, because here the name is
        // drawn from inside EntityRenderer.submit rather than by us — a name
        // that grows with a giant morph is the more sensible of the two
        // behaviours anyway, and at the default scale of 1.0 nothing moves.
        poseStack.pushPose();
        float scale = spec.scale();
        if (scale != 1.0f) poseStack.scale(scale, scale, scale);

        renderer.submit(dummyState, poseStack, collector, camera);

        // Held items are copied onto the dummy, so any morph whose renderer has
        // a held-item layer already drew them in the right hand. This fallback
        // is for the ones that don't — a slime has no hand bone — and floats the
        // item in front of the body instead. Running both would draw it twice.
        if (spec.renderHeldItem() && !drawsHeldItems(renderer)) {
            renderHeldItem(player, poseStack, collector, dummyState.lightCoords, partialTick);
        }
        poseStack.popPose();
    }

    // ── Shadow ───────────────────────────────────────────────────

    /**
     * The shadow radius a morphed player should cast, or a negative number to
     * leave the player's own shadow alone.
     *
     * <p>Called from {@code LivingEntityRendererShadowMixin}, because the shadow
     * is the one piece of the player render that cancelling
     * {@code RenderPlayerEvent.Pre} cannot take over: it is submitted by the
     * dispatcher after the renderer has returned, from the radius on the
     * <em>player's</em> render state, which knows nothing about the morph.
     *
     * <p>The answer comes from the morph's own renderer rather than from its
     * hitbox, so it is the shadow the mob would have cast, complete with the
     * baby-size tweaks a handful of renderers apply. The morph's visual
     * {@code scale} multiplies it for the same reason it wraps the model: a
     * silhouette drawn at twice the size casts a shadow of twice the size.
     */
    public static float morphShadowRadius(AvatarRenderState state) {
        MorphSpec spec = ClientMorphState.getSpec(state.id);
        // A skin-only morph still renders the player's own model, so its shadow
        // is already right.
        if (spec == null || !spec.hasModel()) return -1.0f;
        // Nothing recorded yet, or a morph that couldn't be drawn: either way the
        // player is being rendered normally and keeps their own shadow.
        Float radius = SHADOW_RADII.get(state.id);
        if (radius == null) return -1.0f;
        // Clamped at zero rather than passed through: a negative radius is this
        // method's "leave it alone" signal, and a morph scaled to nothing should
        // cast nothing, not invert.
        return Math.max(0.0f, radius * spec.scale());
    }
    // ── First-person hands ──────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        MorphSpec spec = ClientMorphState.getSpec(player.getId());
        if (spec == null || !spec.hasModel()) return;
        // A morph whose model never resolved isn't drawn at all, so the vanilla
        // arms are all the player has — don't take those away too.
        if (UNRENDERABLE.contains(spec.entityType().orElseThrow())) return;

        // While morphed we own first-person rendering: vanilla would draw the
        // player's own arm, which is exactly the thing the morph replaces.
        event.setCanceled(true);
        if (spec.hidesFirstPerson()) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            // Vanilla only draws a bare arm for the main hand, so this does too.
            // "arm" mode swaps in the morph's own arm bone; a morph with no arm
            // to draw — a slime, a bat — falls through to nothing, which is
            // what the default "item" mode does for an empty hand anyway.
            if (spec.wantsFirstPersonArm() && event.getHand() == InteractionHand.MAIN_HAND
                    && !player.isInvisible()) {
                renderMorphArm(player, spec, event);
            }
            return;
        }

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

    /**
     * Draw the morph's own arm bone in the first-person view, in place of the
     * player's. Everything up to the bone submit is
     * {@code ItemInHandRenderer.renderPlayerArm} verbatim, so the arm sits where
     * a player's would; only the bone that gets drawn differs. That means an arm
     * of a very different length than a player's will read as too long or too
     * short, which is the honest result of borrowing another mob's geometry.
     *
     * <p>Bails out — leaving the view empty, as the default mode does for an
     * empty hand — for anything it can't safely drive: a morph with no dummy or
     * renderer, a renderer that isn't model-based (GeckoLib's is not a
     * {@link LivingEntityRenderer}), or a model with no arm bone.
     *
     * <p><b>26.1 port note.</b> The bone is handed to the
     * {@link SubmitNodeCollector} rather than drawn on the spot, exactly as
     * {@code AvatarRenderer.renderHand} hands over the player's own arm.
     * Neutralising the shared model's pose also works the other way round from
     * 1.21.1: there the animation inputs are arguments to {@code setupAnim},
     * here they are fields on a render state, so we blank a freshly extracted
     * state and pose the model from that.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void renderMorphArm(Player player, MorphSpec spec, RenderHandEvent event) {
        Identifier typeId = spec.entityType().orElseThrow();
        Entity dummy = getOrCreateDummy(player, spec, typeId);
        if (dummy == null) return;

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderState dummyState =
            extractDummyState(dispatcher, dummy, typeId, event.getPartialTick());
        if (!(dummyState instanceof LivingEntityRenderState livingState)) return;
        EntityRenderer<?, ? super EntityRenderState> resolved =
            resolveRenderer(dispatcher, dummyState, typeId);
        if (!(resolved instanceof LivingEntityRenderer livingRenderer)) return;

        EntityModel<?> model = livingRenderer.getModel();
        HumanoidArm side = player.getMainArm();
        ModelPart armPart = MorphArms.resolve(model, spec, side);
        if (armPart == null) return;

        RenderType renderType;
        try {
            renderType = model.renderType(livingRenderer.getTextureLocation(livingState));
        } catch (Exception e) {
            // A renderer that can't name a texture for its own state is not one
            // we can borrow an arm from; fall back to drawing nothing.
            return;
        }

        // Neutralise the pose the third-person pass left on this shared model:
        // it is the renderer's single instance, already posed for whatever the
        // morph was doing this frame. The 1.21.1 side passes zeroes straight to
        // setupAnim; here the same zeroes go on the render state first.
        try {
            livingState.walkAnimationPos = 0.0F;
            livingState.walkAnimationSpeed = 0.0F;
            livingState.yRot = 0.0F;
            livingState.xRot = 0.0F;
            livingState.isBaby = false;
            if (livingState instanceof ArmedEntityRenderState armed) {
                armed.attackTime = 0.0F;
            }
            if (livingState instanceof HumanoidRenderState humanoid) {
                humanoid.isCrouching = false;
                humanoid.isPassenger = false;
                humanoid.swimAmount = 0.0F;
            }
            ((EntityModel) model).setupAnim(livingState);
        } catch (Exception e) {
            // A model that can't be posed against its own render state is not one
            // we can borrow an arm from; fall back to drawing nothing.
            return;
        }

        boolean right = side != HumanoidArm.LEFT;
        float f = right ? 1.0F : -1.0F;
        float swing = event.getSwingProgress();
        float equip = event.getEquipProgress();
        float f1 = Mth.sqrt(swing);
        float f2 = -0.3F * Mth.sin(f1 * (float) Math.PI);
        float f3 = 0.4F * Mth.sin(f1 * (float) (Math.PI * 2));
        float f4 = -0.4F * Mth.sin(swing * (float) Math.PI);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(f * (f2 + 0.64000005F), f3 + -0.6F + equip * -0.6F, f4 + -0.71999997F);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        float f5 = Mth.sin(swing * swing * (float) Math.PI);
        float f6 = Mth.sin(f1 * (float) Math.PI);
        pose.mulPose(Axis.YP.rotationDegrees(f * f6 * 70.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(f * f5 * -20.0F));
        pose.translate(f * -1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        pose.translate(f * 5.6F, 0.0F, 0.0F);

        armPart.xRot = 0.0F;
        armPart.visible = true;
        event.getSubmitNodeCollector().submitModelPart(armPart, pose, renderType,
            event.getPackedLight(), OverlayTexture.NO_OVERLAY, null);
        pose.popPose();
    }

    // ── Dummy lifecycle ─────────────────────────────────────────────────────

    @Nullable
    private static Entity getOrCreateDummy(Player player, MorphSpec spec, Identifier typeId) {
        if (UNRENDERABLE.contains(typeId)) return null;

        String key = player.getId() + "|" + typeId + "|" + spec.nbt().map(CompoundTag::hashCode).orElse(0);
        Entity cached = DUMMIES.get(key);
        if (cached != null) return cached;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (type == null) {
            markUnrenderable(typeId, "no such entity type is registered");
            return null;
        }
        // EntityRenderers deliberately skips players and mannequins when it
        // registers renderers — they are resolved through a separate skin-keyed
        // path instead. Extracting a render state for one would dereference a
        // null renderer inside the dispatcher and crash the game.
        if (type == EntityTypes.PLAYER || type == EntityTypes.MANNEQUIN) {
            markUnrenderable(typeId, "players and mannequins cannot be used as a morph target");
            return null;
        }
        if (player.level() == null) return null;

        Entity created;
        try {
            // 26.1: EntityType.create requires an EntitySpawnReason; LOAD is the
            // no-side-effects choice for a render-only dummy.
            created = type.create(player.level(), EntitySpawnReason.LOAD);
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("entity_model: failed to create dummy '{}': {}", typeId, e.getMessage());
            created = null;
        }
        if (created == null) {
            markUnrenderable(typeId, "the entity type refused to be created");
            return null;
        }

        // Variant NBT (sheep colour, cat type, villager profession, slime size…)
        // is applied once, here, rather than per frame — it's picking a variant,
        // not animating one.
        if (spec.nbt().isPresent()) MorphVariants.apply(created, spec.nbt().get(), typeId);

        DUMMIES.put(key, created);
        // Opportunistically prune entries for players that no longer morph.
        pruneStale();
        return created;
    }

    /**
     * Build the dummy's render state, treating any failure as a permanent
     * "this type can't be morphed into" verdict rather than a crash.
     */
    @Nullable
    private static EntityRenderState extractDummyState(EntityRenderDispatcher dispatcher, Entity dummy,
                                                       Identifier typeId, float partialTick) {
        try {
            return dispatcher.extractEntity(dummy, partialTick);
        } catch (Throwable t) {
            markUnrenderable(typeId, "extracting its render state failed: " + t.getMessage());
            return null;
        }
    }

    /** Look up the renderer for a dummy state, negative-caching any failure. */
    @Nullable
    private static EntityRenderer<?, ? super EntityRenderState> resolveRenderer(
            EntityRenderDispatcher dispatcher, EntityRenderState dummyState, Identifier typeId) {
        EntityRenderer<?, ? super EntityRenderState> renderer;
        try {
            renderer = dispatcher.getRenderer(dummyState);
        } catch (Exception e) {
            markUnrenderable(typeId, "looking up its renderer failed: " + e.getMessage());
            return null;
        }
        if (renderer == null) {
            markUnrenderable(typeId, "no renderer is registered for it");
            return null;
        }
        return renderer;
    }

    /** Log once, then never retry this type until the cache is cleared. */
    private static void markUnrenderable(Identifier typeId, String reason) {
        if (UNRENDERABLE.add(typeId)) {
            NeoOrigins.LOGGER.warn(
                "entity_model: cannot morph into '{}' — {}. Falling back to the normal player model.",
                typeId, reason);
        }
        DUMMIES.keySet().removeIf(k -> k.contains("|" + typeId + "|"));
    }

    private static void pruneStale() {
        if (DUMMIES.size() < 32) return; // cheap guard; only sweep when it grows
        DUMMIES.keySet().removeIf(k -> {
            int bar = k.indexOf('|');
            if (bar <= 0) return true;
            try {
                int id = Integer.parseInt(k.substring(0, bar));
                return !ClientMorphState.isMorphed(id);
            } catch (NumberFormatException e) {
                return true;
            }
        });
        SHADOW_RADII.keySet().removeIf(id -> !ClientMorphState.isMorphed(id));
    }

    /** Drop the morph cache on world unload so stale dummies don't leak. */
    public static void clearCache() {
        DUMMIES.clear();
        SHADOW_RADII.clear();
        // Renderers are re-registered on a resource reload, so a type that had
        // none last session deserves another chance.
        UNRENDERABLE.clear();
        // Models are rebuilt on reload too, so the bones resolved off the old
        // ones no longer belong to anything being drawn.
        MorphArms.clearCache();
    }

    // ── Per-frame state copy (extension point) ──────────────────────────────

    /**
     * Copy the player's orientation, walk and swing animation onto the dummy so
     * it animates in place. Slime additionally needs its squish driven from its
     * own tick so {@code Slime.squish}/{@code oSquish} oscillate.
     *
     * <p>The renderer reads everything it draws off the entity in front of it,
     * never off the player, so any state that has a visual consequence has to be
     * mirrored here or the morph simply ignores it — a morphed player would take
     * damage without flashing red, die without toppling, and drink an invisibility
     * potion with no effect.
     */
    private static void syncDummyState(Player player, Entity dummy, MorphSpec spec, float partialTick) {
        dummy.setPos(player.getX(), player.getY(), player.getZ());

        dummy.yRotO = player.yRotO;
        dummy.setYRot(player.getYRot());
        dummy.xRotO = player.xRotO;
        dummy.setXRot(player.getXRot());
        dummy.setPose(player.getPose());
        dummy.setShiftKeyDown(player.isShiftKeyDown());
        dummy.setOnGround(player.onGround());
        dummy.setInvisible(player.isInvisible());
        dummy.setTicksFrozen(player.getTicksFrozen());

        // Movement-driven models need an actual velocity, not just onGround:
        // BeeModel stops flapping entirely below a squared length of 1e-7, so a
        // dummy left at zero motion hangs in the air perfectly still.
        dummy.setDeltaMovement(player.getDeltaMovement());

        if (dummy instanceof LivingEntity living) {
            living.yBodyRot = player.yBodyRot;
            living.yBodyRotO = player.yBodyRotO;
            living.yHeadRot = player.yHeadRot;
            living.yHeadRotO = player.yHeadRotO;

            living.swinging = player.swinging;
            living.swingTime = player.swingTime;
            living.attackAnim = player.attackAnim;
            living.oAttackAnim = player.oAttackAnim;

            // Damage and death. hurtTime drives the red damage overlay; the
            // death topple needs deathTime AND a health of zero, because the
            // renderer reads the angle from one and gates it (along with limb
            // swing) on isAlive() — set only deathTime and a dead morph keeps
            // walking while lying on its side.
            living.hurtTime = player.hurtTime;
            living.hurtDuration = player.hurtDuration;
            living.deathTime = player.deathTime;
            living.setHealth(player.isDeadOrDying() ? 0.0f : living.getMaxHealth());

            // "Aggressive" is what makes a skeleton raise its bow and a humanoid
            // hold its arms out; using an item is the nearest thing a player has.
            if (living instanceof Mob mob) {
                mob.setAggressive(player.isUsingItem());
                syncEquipment(player, mob, spec);
            }
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

            if (dummy instanceof Bat bat) {
                // BatModel animates purely from these two states, and both are
                // started inside Bat.tick — which never runs on a dummy. Without
                // this a bat morph is a motionless T-pose. A morphed player is
                // always "flying"; resting is an AI state they can't be in.
                bat.restAnimationState.stop();
                bat.flyAnimationState.startIfStopped(playerTick);
            }
        }
    }

    // ── Equipment ───────────────────────────────────────────────

    /**
     * Slots copied from the player onto the morph. Spelled out rather than
     * taken from {@code EquipmentSlot.values()} because that also carries
     * animal-only slots ({@code BODY}, and {@code SADDLE} on 26.x) that a player
     * can never fill.
     */
    private static final EquipmentSlot[] HAND_SLOTS = {
        EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /**
     * Dress the dummy in whatever the player is wearing and holding, so armour
     * and weapons carry across the morph.
     *
     * <p>Nothing extra is needed to make this render: the layers that draw
     * armour and held items are already on the renderers of the mobs that have
     * them ({@code HumanoidArmorLayer} is added by the zombie/skeleton/piglin
     * <em>subclass</em> renderers, not by the shared humanoid one), and mobs
     * without those layers simply ignore the equipment. Villagers, for instance,
     * are not humanoid-rendered at all and will only ever show a main-hand item.
     *
     * <p>Equipping is safe to do on a client-side stand-in: the equip sound and
     * game event in {@code LivingEntity.onEquipItem} are both behind a
     * server-side check, so this cannot spam either.
     */
    private static void syncEquipment(Player player, Mob mob, MorphSpec spec) {
        copySlots(player, mob, HAND_SLOTS, spec.renderHeldItem());
        copySlots(player, mob, ARMOR_SLOTS, spec.renderArmor());
    }

    private static void copySlots(Player player, Mob mob, EquipmentSlot[] slots, boolean wanted) {
        for (EquipmentSlot slot : slots) {
            ItemStack target = wanted ? player.getItemBySlot(slot) : ItemStack.EMPTY;
            // Only write on an actual change: setItemSlot re-validates the stack,
            // and this runs for every morphed player every frame.
            if (!ItemStack.matches(mob.getItemBySlot(slot), target)) {
                mob.setItemSlot(slot, target.copy());
            }
        }
    }

    /**
     * Whether a renderer draws held items itself, through one of its layers.
     *
     * <p>Answers the question "did the morph already show the item?", which
     * decides whether the floating-item fallback should run. The layer list is
     * fixed once a renderer is built, so the verdict is cached per renderer
     * class; the reflection-free read of {@code layers} is why this mod carries
     * an access transformer.
     */
    @SuppressWarnings("rawtypes")
    private static boolean drawsHeldItems(EntityRenderer<?, EntityRenderState> renderer) {
        return HELD_ITEM_RENDERERS.computeIfAbsent(renderer.getClass(), c -> {
            if (!(renderer instanceof LivingEntityRenderer living)) return false;
            for (Object layer : living.layers) {
                if (layer instanceof ItemInHandLayer || layer instanceof CrossedArmsItemLayer) {
                    return true;
                }
            }
            return false;
        });
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
