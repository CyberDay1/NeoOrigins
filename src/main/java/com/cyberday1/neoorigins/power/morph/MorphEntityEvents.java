package com.cyberday1.neoorigins.power.morph;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The named vanilla entity-event bytes {@code neoorigins:morph_entity_event}
 * accepts, and the two safety rules that govern them.
 *
 * <p><b>Where the names come from.</b> Straight off {@code net.minecraft.world.entity.EntityEvent},
 * lowercased — {@code EntityEvent.LOVE_HEARTS} is authored as {@code "love_hearts"}.
 * Reading Mojang's own constant table rather than hand-listing it is what makes
 * the vocabulary survive version drift: a byte that changes meaning, or a new one
 * added in a later MC, arrives here for free on the port instead of silently
 * meaning the wrong thing. Names are sorted so the generated JSON schema is
 * deterministic ({@code Class#getFields} order is unspecified, and
 * {@code schemaDriftVerify} is a byte comparison).
 *
 * <p><b>Safety limit, not balance: byte 3 is rejected.</b> {@code LivingEntity}'s
 * {@code case 3} calls {@code die()}, which fires NeoForge's {@code LivingDeathEvent}
 * on the <em>client</em> for an entity that was never added to the world. Any mod
 * listening for client-side deaths would see a phantom kill for an entity it cannot
 * look up. That is a correctness hazard for third-party code, so it is refused at
 * parse time with a warning rather than exposed and documented. The morph already
 * mirrors real death through {@code deathTime}/health in {@code MorphRenderHandler},
 * so nothing is lost.
 *
 * <p><b>Safety limit, not balance: the id range.</b> {@code Entity#handleEntityEvent}
 * takes a {@code byte}; anything outside signed-byte range would silently wrap to a
 * different event. Refused at parse time.
 *
 * <p>There is deliberately no "hurt" event. Vanilla dropped {@code case 2} in
 * 1.19.4 — hurt moved to {@code ClientboundHurtAnimationPacket}/{@code handleDamageEvent} —
 * and {@code MorphRenderHandler} already mirrors {@code hurtTime} onto the dummy, so
 * the damage flash works without any action.
 *
 * <p>Common-side: {@code EntityEvent} is a plain constant holder in
 * {@code net.minecraft.world.entity}, present on a dedicated server.
 */
public final class MorphEntityEvents {

    private MorphEntityEvents() {}

    /** {@code LivingEntity} case 3 → {@code die()}. Never dispatched. See the class doc. */
    public static final byte DEATH = net.minecraft.world.entity.EntityEvent.DEATH;

    /** Author-facing name → byte, sorted by name for deterministic schema output. */
    private static final Map<String, Byte> BY_NAME = buildTable();

    private static Map<String, Byte> buildTable() {
        List<Field> fields = new ArrayList<>();
        for (Field field : net.minecraft.world.entity.EntityEvent.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() != byte.class) continue;
            fields.add(field);
        }
        fields.sort(Comparator.comparing(Field::getName));
        Map<String, Byte> table = new LinkedHashMap<>();
        for (Field field : fields) {
            byte value;
            try {
                value = field.getByte(null);
            } catch (IllegalAccessException e) {
                continue;
            }
            // DEATH is excluded from the vocabulary entirely rather than accepted
            // and then dropped, so the schema, the editors and the parser all agree
            // that it is not an authorable value.
            if (value == DEATH) continue;
            table.put(field.getName().toLowerCase(java.util.Locale.ROOT), value);
        }
        return Map.copyOf(table);
    }

    /** Every authorable event name, sorted. Feeds the {@code event} field's schema enum. */
    public static List<String> names() {
        return BY_NAME.keySet().stream().sorted().toList();
    }

    /** The byte for a name, or {@code null} if it is not a known vanilla event. */
    public static Byte byName(String name) {
        return name == null ? null : BY_NAME.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * True if a raw numeric id may be dispatched: in signed-byte range and not
     * {@link #DEATH}. Both are safety checks — see the class doc — and neither is
     * a balance restriction, so nothing else about the value is judged. Modded
     * mobs define their own bytes freely and every one of them is allowed.
     */
    public static boolean isDispatchable(int id) {
        return id >= Byte.MIN_VALUE && id <= Byte.MAX_VALUE && id != DEATH;
    }
}
