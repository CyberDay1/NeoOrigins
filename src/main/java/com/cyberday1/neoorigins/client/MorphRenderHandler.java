package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.mixin.client.EntityRendererShadowInvoker;
import com.cyberday1.neoorigins.power.morph.MorphSpec;
import com.cyberday1.neoorigins.power.morph.MorphVariants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
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
 * visual scale, and what the morphed player sees of their own hands.
 * The dummy cache, per-frame state copy, held-item attachment and first-person
 * hand are all type-agnostic: the only per-type code left is the handful of
 * tick-driven animation states in {@link #syncDummyState} that their entities
 * normally start from AI this dummy never runs.
 *
 * <p>This class only draws. The collision box that goes with the silhouette is
 * server-authoritative and lives in {@code MorphHitboxEvents}.
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
    private static final Set<ResourceLocation> UNRENDERABLE = ConcurrentHashMap.newKeySet();

    /**
     * Per renderer class: does it draw held items through its own layers? See
     * {@link #drawsHeldItems}. Keyed by class because the layer list is built
     * once, in the renderer's constructor, and never changes afterwards.
     */
    private static final Map<Class<?>, Boolean> HELD_ITEM_RENDERERS = new ConcurrentHashMap<>();

    // ── Player render swap ──────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        MorphSpec spec = ClientMorphState.getSpec(player.getId());
        // A morph without an entity_type carries only non-model tweaks, so the
        // vanilla player render stands.
        if (spec == null || !spec.hasModel()) return;
        ResourceLocation morphType = spec.entityType().orElseThrow();

        Entity dummy = getOrCreateDummy(player, spec, morphType);
        if (dummy == null) {
            // Unknown / un-creatable entity type — leave the vanilla player
            // rendering intact rather than drawing nothing.
            return;
        }

        // Resolve the renderer BEFORE cancelling. Cancelling first and then
        // bailing on a null renderer leaves the player completely invisible,
        // with no way back short of dropping the power.
        EntityRenderer<Entity> renderer = resolveRenderer(dummy, morphType);
        if (renderer == null) return;

        // From here we own the render for this player: skip vanilla body+arms.
        event.setCanceled(true);

        float partialTick = event.getPartialTick();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();

        syncDummyState(player, dummy, spec, partialTick);

        // Interpolated body yaw, matching what the player renderer would use.
        float yaw = lerp(partialTick, player.yBodyRotO, player.yBodyRot);

        // The visual scale wraps the model AND the held item so the two stay
        // proportional. The nameplate stays outside it, anchored to the
        // player's own attachment point.
        poseStack.pushPose();
        float scale = spec.scale();
        if (scale != 1.0f) poseStack.scale(scale, scale, scale);

        renderer.render(dummy, yaw, partialTick, poseStack, buffers, light);

        // Held items are copied onto the dummy, so any morph whose renderer has
        // a held-item layer already drew them in the right hand. This fallback
        // is for the ones that don't — a slime has no hand bone — and floats the
        // item in front of the body instead. Running both would draw it twice.
        if (spec.renderHeldItem() && !drawsHeldItems(renderer)) {
            renderHeldItem(player, dummy, poseStack, buffers, light, partialTick);
        }
        poseStack.popPose();

        // Re-draw the player's nameplate, which the cancelled event would drop.
        renderMorphNameTag(player, poseStack, buffers, light);
    }

    // ── Shadow ───────────────────────────────────────────────────

    /**
     * The shadow radius a morphed player should cast, or a negative number to
     * leave the player's own shadow alone.
     *
     * <p>Called from {@code LivingEntityRendererShadowMixin}, because the shadow
     * is the one piece of the player render that cancelling
     * {@code RenderPlayerEvent.Pre} cannot take over: the dispatcher draws it
     * after the renderer has returned, from the <em>player's</em> renderer, at a
     * radius that knows nothing about the morph.
     *
     * <p>The answer comes from the morph's own renderer rather than from its
     * hitbox, so it is the shadow the mob would have cast, complete with the
     * baby-size tweaks a handful of renderers apply. The morph's visual
     * {@code scale} multiplies it for the same reason it wraps the model: a
     * silhouette drawn at twice the size casts a shadow of twice the size.
     */
    public static float morphShadowRadius(Player player) {
        MorphSpec spec = ClientMorphState.getSpec(player.getId());
        // A skin-only morph still renders the player's own model, so its shadow
        // is already right.
        if (spec == null || !spec.hasModel()) return -1.0f;
        ResourceLocation typeId = spec.entityType().orElseThrow();

        // A morph that cannot be drawn falls back to the vanilla player render,
        // and has to fall back to the vanilla shadow with it.
        Entity dummy = getOrCreateDummy(player, spec, typeId);
        if (dummy == null) return -1.0f;
        EntityRenderer<Entity> renderer = resolveRenderer(dummy, typeId);
        if (renderer == null) return -1.0f;

        try {
            float radius = ((EntityRendererShadowInvoker) renderer).neoorigins$getShadowRadius(dummy);
            // Clamped at zero rather than passed through: a negative radius is
            // this method's "leave it alone" signal, and a morph scaled to
            // nothing should cast nothing, not invert.
            return Math.max(0.0f, radius * spec.scale());
        } catch (Exception e) {
            // A renderer that will not answer for this entity is not one we can
            // borrow a shadow from either.
            return -1.0f;
        }
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

        mc.gameRenderer.itemInHandRenderer.renderItem(
            player, stack,
            right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            !right, pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    /**
     * Draw the morph's own arm bone in the first-person view, in place of the
     * player's. Everything up to the bone render is
     * {@code ItemInHandRenderer.renderPlayerArm} verbatim, so the arm sits where
     * a player's would; only the bone that gets drawn differs. That means an arm
     * of a very different length than a player's will read as too long or too
     * short, which is the honest result of borrowing another mob's geometry.
     *
     * <p>Bails out — leaving the view empty, as the default mode does for an
     * empty hand — for anything it can't safely drive: a morph with no dummy or
     * renderer, a renderer that isn't model-based (GeckoLib's is not a
     * {@link LivingEntityRenderer}), or a model with no arm bone.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void renderMorphArm(Player player, MorphSpec spec, RenderHandEvent event) {
        ResourceLocation typeId = spec.entityType().orElseThrow();
        Entity dummy = getOrCreateDummy(player, spec, typeId);
        if (dummy == null) return;
        EntityRenderer<Entity> renderer = resolveRenderer(dummy, typeId);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)) return;

        EntityModel<?> model = livingRenderer.getModel();
        HumanoidArm side = player.getMainArm();
        ModelPart armPart = MorphArms.resolve(model, spec, side);
        if (armPart == null) return;

        // Neutralise the pose the third-person pass left on this shared model:
        // it is the renderer's single instance, already posed for whatever the
        // morph was doing this frame. Mirrors PlayerRenderer.renderHand.
        try {
            model.attackTime = 0.0F;
            model.riding = false;
            model.young = false;
            if (model instanceof HumanoidModel<?> humanoid) {
                humanoid.crouching = false;
                humanoid.swimAmount = 0.0F;
            }
            ((EntityModel) model).setupAnim(dummy, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        } catch (Exception e) {
            // A model that can't be posed against its own entity is not one we
            // can borrow an arm from; fall back to drawing nothing.
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
        armPart.render(pose, event.getMultiBufferSource().getBuffer(
                model.renderType(renderer.getTextureLocation(dummy))),
            event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    // ── Dummy lifecycle ─────────────────────────────────────────────────────

    @Nullable
    private static Entity getOrCreateDummy(Player player, MorphSpec spec, ResourceLocation typeId) {
        if (UNRENDERABLE.contains(typeId)) return null;

        String key = player.getId() + "|" + typeId + "|" + spec.nbt().map(CompoundTag::hashCode).orElse(0);
        Entity cached = DUMMIES.get(key);
        if (cached != null) return cached;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (type == null) {
            markUnrenderable(typeId, "no such entity type is registered");
            return null;
        }
        // Players have no renderer in the dispatcher's by-type map — they are
        // resolved through a separate skin-keyed path. Asking for one here would
        // hand back nothing at best and blow up inside the dispatcher at worst.
        if (type == EntityType.PLAYER) {
            markUnrenderable(typeId, "players cannot be used as a morph target");
            return null;
        }
        if (player.level() == null) return null;

        Entity created;
        try {
            created = type.create(player.level());
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
     * The dummy entity currently standing in for {@code player}'s morph, creating
     * it if this client has not drawn the morph yet; {@code null} when the player
     * is not morphed, carries a model-less morph, or the type is unrenderable.
     *
     * <p>Exists for the morph action verbs ({@code neoorigins:trigger_morph_animation},
     * {@code neoorigins:morph_entity_event}), which have to reach the same cached
     * instance the renderer draws — triggering an animation on a fresh throwaway
     * entity would key it under a different entity id and nothing would ever show
     * it. Deliberately create-if-absent rather than lookup-only: the actions are
     * server-driven and can land on a client that has the morph synced but has not
     * yet had a frame in which to render it (out of view, or the very tick the
     * power was granted), and dropping the trigger in that window would be an
     * intermittent no-op with no diagnosis path.
     */
    @Nullable
    public static Entity activeDummy(Player player) {
        if (player == null) return null;
        MorphSpec spec = ClientMorphState.getSpec(player.getId());
        if (spec == null || !spec.hasModel()) return null;
        return getOrCreateDummy(player, spec, spec.entityType().orElseThrow());
    }

    /**
     * Look up the vanilla renderer for a dummy, treating any failure as a
     * permanent "this type can't be morphed into" verdict.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static EntityRenderer<Entity> resolveRenderer(Entity dummy, ResourceLocation typeId) {
        EntityRenderer<Entity> renderer;
        try {
            renderer = (EntityRenderer<Entity>)
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
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
    private static void markUnrenderable(ResourceLocation typeId, String reason) {
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
    }

    /** Drop the morph cache on world unload so stale dummies don't leak. */
    public static void clearCache() {
        DUMMIES.clear();
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

    // ── Equipment ───────────────────────────────────────────────────────────

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
    private static boolean drawsHeldItems(EntityRenderer<Entity> renderer) {
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
     * Render the player's main-hand item on a morph whose renderer has no
     * held-item layer — a slime, a bee, a bat. There is no hand bone to attach
     * to, so the item floats just above and in front of the body instead.
     *
     * <p>The offsets come from the dummy's own bounding box rather than fixed
     * numbers, so the item tracks the silhouette of whatever the morph is: it
     * sits fractionally above the top of a short, wide slime and out in front of
     * a tall, narrow one, instead of being buried inside the second. The scale
     * is deliberately left alone — the surrounding pose stack already carries
     * the morph's {@code scale}, and shrinking the item with the mob as well
     * would make it unreadable on anything small.
     */
    private static void renderHeldItem(Player player, Entity dummy, PoseStack poseStack,
                                       MultiBufferSource buffers, int light, float partialTick) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        poseStack.pushPose();
        // Just clear of the top of the body, and clear of its front face.
        double height = Math.max(dummy.getBbHeight(), 0.25) * 1.05;
        double forward = Math.max(dummy.getBbWidth(), 0.25) * 0.6;
        float bodyYaw = lerp(partialTick, player.yBodyRotO, player.yBodyRot);
        double rad = Math.toRadians(bodyYaw);
        poseStack.translate(-Math.sin(rad) * forward, height, Math.cos(rad) * forward);
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        poseStack.scale(0.6f, 0.6f, 0.6f);

        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED,
            light, OverlayTexture.NO_OVERLAY,
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
