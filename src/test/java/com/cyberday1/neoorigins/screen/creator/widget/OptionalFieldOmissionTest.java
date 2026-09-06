package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormModel;
import com.cyberday1.neoorigins.screen.creator.CreatorHost;
import com.cyberday1.neoorigins.screen.creator.widget.FieldWidgetFactory.FieldRow;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An optional field the author never touched must serialise as ABSENT.
 * {@code BoolRow} used to return a {@code JsonPrimitive} unconditionally and
 * {@code EnumRow} used to return its seeded schema default, so merely OPENING a
 * power wrote keys the author never chose — and for several powers absent and
 * present-with-that-value mean different things:
 *
 * <ul>
 *   <li>{@code attribute_modifier.equipment_condition.slot} defaults to
 *       {@code mainhand}, which materialised the whole optional
 *       {@code equipment_condition} gate. {@code AttributeModifierPower} then
 *       switches the modifier off whenever the main hand is empty.</li>
 *   <li>{@code entity_model.skin.model} has no default at all — "unset keeps the
 *       player's own" — yet the first enum value was written, overriding the
 *       referenced morph's skin.</li>
 *   <li>{@code effect_over_time.default_off} and {@code can_see_sky} are
 *       genuinely tri-state; an explicit {@code false} is not the same as absent.</li>
 * </ul>
 *
 * <p>The container was already correct: {@code ObjectRow.toJson()} drops an
 * empty body and {@code PowerFormPanel.push()} removes the key on a null. Both
 * guards were simply unreachable while every leaf insisted on a value.
 */
class OptionalFieldOmissionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Widget host stub — rows only ever call register(); nothing here draws. */
    private static final class Host implements CreatorHost {
        @Override public <T extends GuiEventListener & Renderable & NarratableEntry> T register(T w) { return w; }
        @Override public <T extends GuiEventListener & Renderable & NarratableEntry> T registerInputOnly(T w) { return w; }
        @Override public Font font() { return null; }
        @Override public void requestRebuild() { }
        @Override public int hostWidth() { return 400; }
        @Override public int hostHeight() { return 300; }
        @Override public void queueTooltip(List<String> lines, int mx, int my) { }
    }

    private static FormFieldSpec field(List<FormFieldSpec> form, String name) {
        return form.stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
    }

    /** The named child of the named OBJECT field of the named power. */
    private static FormFieldSpec child(String powerId, String objectField, String childName) {
        FormFieldSpec obj = field(FormModel.forPower(ResourceLocation.parse(powerId)), objectField);
        assertNotNull(obj, powerId + " has no '" + objectField + "' field");
        FormFieldSpec c = field(obj.children(), childName);
        assertNotNull(c, objectField + " has no '" + childName + "' child");
        return c;
    }

    private static FormFieldSpec enumSpec(String name, boolean required, Object def, String... values) {
        return new FormFieldSpec(name, FormFieldSpec.Kind.ENUM, required, def,
            List.of(values), null, null, null, null);
    }

    private static FormFieldSpec boolSpec(String name, boolean required, Object def) {
        return new FormFieldSpec(name, FormFieldSpec.Kind.BOOLEAN, required, def,
            List.of(), null, null, null, null);
    }

    /** Seed the row from {@code el} (null = key absent) and re-serialise it. */
    private static com.google.gson.JsonElement roundTrip(FormFieldSpec spec, com.google.gson.JsonElement el) {
        FieldRow row = FieldWidgetFactory.create(spec);
        row.fromJson(el);
        return row.toJson();
    }

    // ── the two reported CRITICAL cases, against the real form model ─────────

    @Test
    void untouchedEquipmentConditionSlotIsOmitted() {
        FormFieldSpec slot = child("neoorigins:attribute_modifier", "equipment_condition", "slot");
        assertEquals("mainhand", String.valueOf(slot.defaultValue()),
            "precondition: this is the schema default that used to leak");
        assertNull(roundTrip(slot, null),
            "an untouched optional slot must write nothing — writing 'mainhand' arms the "
                + "equipment gate and kills the modifier whenever the main hand is empty");
    }

    @Test
    void authoredEquipmentConditionSlotRoundTripsUnchanged() {
        FormFieldSpec slot = child("neoorigins:attribute_modifier", "equipment_condition", "slot");
        assertEquals("head", roundTrip(slot, new JsonPrimitive("head")).getAsString());
    }

    @Test
    void untouchedEntityModelSkinModelIsOmitted() {
        FormFieldSpec model = child("neoorigins:entity_model", "skin", "model");
        assertNull(model.defaultValue(), "precondition: unset keeps the player's own");
        assertNull(roundTrip(model, null));
        assertEquals("slim", roundTrip(model, new JsonPrimitive("slim")).getAsString());
    }

    // ── the leaf contract ───────────────────────────────────────────────────

    @Test
    void optionalBooleanIsTriStateAndStartsUnset() {
        FormFieldSpec flag = boolSpec("default_off", false, null);
        assertNull(roundTrip(flag, null));
        // Both explicit values survive — false is NOT the same as absent.
        assertEquals(false, roundTrip(flag, new JsonPrimitive(false)).getAsBoolean());
        assertEquals(true, roundTrip(flag, new JsonPrimitive(true)).getAsBoolean());
    }

    @Test
    void optionalBooleanWithSchemaDefaultStillOmitsWhenUntouched() {
        assertNull(roundTrip(boolSpec("inverted", false, Boolean.TRUE), null));
    }

    @Test
    void requiredBooleanAlwaysWritesAValue() {
        assertEquals(true, roundTrip(boolSpec("on", true, Boolean.TRUE), null).getAsBoolean());
        assertEquals(false, roundTrip(boolSpec("on", true, Boolean.FALSE), null).getAsBoolean());
    }

    @Test
    void requiredEnumKeepsItsDefaultAndNeverGoesUnset() {
        FormFieldSpec pose = enumSpec("pose", true, "standing", "standing", "crouching", "swimming");
        assertEquals("standing", roundTrip(pose, null).getAsString());
        FieldRow row = FieldWidgetFactory.create(pose);
        row.fromJson(new JsonPrimitive("crouching"));
        assertNull(row.validationError(), "a required enum holding a real value must not block Save");
    }

    @Test
    void requiredEnumWithNoValuesStillBlocksSave() {
        // enumValues empty → the "" placeholder; required must still fail.
        FieldRow row = FieldWidgetFactory.create(enumSpec("mode", true, null));
        row.fromJson(null);
        assertEquals("required", row.validationError());
        assertNull(row.toJson());
    }

    @Test
    void optionalEnumNeverBlocksSave() {
        FieldRow row = FieldWidgetFactory.create(enumSpec("mode", false, "blacklist", "blacklist", "whitelist"));
        row.fromJson(null);
        assertNull(row.validationError());
        assertNull(row.toJson());
    }

    // ── the container falls out for free ────────────────────────────────────

    @Test
    void untouchedOptionalObjectIsDroppedEntirely() {
        FormFieldSpec obj = new FormFieldSpec("equipment_condition", FormFieldSpec.Kind.OBJECT,
            false, null, List.of(), null, null, null, null, null,
            List.of(enumSpec("slot", false, "mainhand", "mainhand", "offhand", "head"),
                    boolSpec("inverted", false, Boolean.FALSE)));
        FieldRow row = FieldWidgetFactory.create(obj);
        row.build(new Host(), null, 120, 16);
        row.fromJson(null);
        assertNull(row.toJson(), "no child produced a value, so the whole optional object goes");
    }

    @Test
    void authoredOptionalObjectRoundTripsOnlyTheKeysItHas() {
        FormFieldSpec obj = new FormFieldSpec("equipment_condition", FormFieldSpec.Kind.OBJECT,
            false, null, List.of(), null, null, null, null, null,
            List.of(enumSpec("slot", false, "mainhand", "mainhand", "offhand", "head"),
                    boolSpec("inverted", false, Boolean.FALSE)));
        FieldRow row = FieldWidgetFactory.create(obj);
        row.build(new Host(), null, 120, 16);
        JsonObject body = new JsonObject();
        body.addProperty("slot", "offhand");
        row.fromJson(body);
        assertEquals(body, row.toJson(), "an authored object must survive verbatim — no default padding");
    }
}
