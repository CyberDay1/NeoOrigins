package com.cyberday1.neoorigins.screen.creator.widget;

import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormModel;
import com.cyberday1.neoorigins.screen.creator.widget.FieldWidgetFactory.FieldRow;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A MIXED field is normally a raw-JSON box, and that is what {@code render_elytra}
 * became when it widened from a boolean to the never/flying/always tri-state: the
 * third state was typeable but undiscoverable, and a typo only failed at power load.
 *
 * <p>The fix is general, so this pins the general rule rather than the field. A MIXED
 * spec whose arms are exactly boolean+string AND which names its string values is a
 * CLOSED choice — the options cover the whole value space — so it renders as the enum
 * dropdown. Every other MIXED (the {@code key} field's integer|string|object, the
 * {@code string | object} idiom) is an open union and must keep the raw-JSON box, or
 * the dropdown would hide legal values.
 *
 * <p>The legacy spellings still have to load: an existing file holding {@code true}
 * must select "flying", not miss every entry and silently drop the key back to the
 * power's own default — which differs per power type, so the drop would be a real
 * behaviour change on {@code natural_glide}.
 */
class MixedChoiceRowTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** The named top-level field of the named power's real, schema-derived form. */
    private static FormFieldSpec field(String powerId, String name) {
        FormFieldSpec f = FormModel.forPower(ResourceLocation.parse(powerId)).stream()
            .filter(s -> s.name().equals(name)).findFirst().orElse(null);
        assertNotNull(f, powerId + " has no '" + name + "' field");
        return f;
    }

    /** Seed a fresh row from {@code el} (null = key absent) and re-serialise it. */
    private static JsonElement roundTrip(FormFieldSpec spec, JsonElement el) {
        FieldRow row = FieldWidgetFactory.create(spec);
        row.fromJson(el);
        return row.toJson();
    }

    private static final List<String> WING_POWERS = List.of(
        "neoorigins:elytra_flight", "neoorigins:natural_glide", "neoorigins:flight");

    // ── the spec side: what the schema carries into the form model ──────────

    @Test
    void renderElytraIsAClosedBooleanStringChoice() {
        for (String power : WING_POWERS) {
            FormFieldSpec f = field(power, "render_elytra");
            assertEquals(FormFieldSpec.Kind.MIXED, f.kind(), power + " render_elytra kind");
            assertEquals(List.of("boolean", "string"), f.mixedTypes(), power + " arms");
            assertEquals(List.of("never", "flying", "always"), f.enumValues(), power + " options");
            assertTrue(f.isBooleanStringChoice(), power + " render_elytra should be a choice");
            // The declared order IS the boolean mapping — options[0]/[1].
            assertEquals("never", f.choiceFor(false));
            assertEquals("flying", f.choiceFor(true));
        }
    }

    @Test
    void openMixedUnionsAreNotChoices() {
        // key: integer | string | object, no options — three arms, nothing named.
        FormFieldSpec key = field("neoorigins:active_ability", "key");
        assertEquals(FormFieldSpec.Kind.MIXED, key.kind());
        assertTrue(key.enumValues().isEmpty(), "key must not have gained options");
        assertFalse(key.isBooleanStringChoice(), "a 3-arm union is not a closed choice");
    }

    // ── the widget side: which row the factory builds ───────────────────────

    @Test
    void aClosedChoiceRendersAsADropdownAndAnOpenUnionStaysRawJson() {
        for (String power : WING_POWERS) {
            assertEquals("EnumRow",
                FieldWidgetFactory.create(field(power, "render_elytra")).getClass().getSimpleName(),
                power + " render_elytra should get the enum dropdown");
        }
        assertEquals("TextRow",
            FieldWidgetFactory.create(field("neoorigins:active_ability", "key"))
                .getClass().getSimpleName(),
            "an open MIXED union keeps the raw-JSON escape hatch");
    }

    // ── loading and saving ─────────────────────────────────────────────────

    @Test
    void legacyBooleansLoadOntoTheRightEntry() {
        for (String power : WING_POWERS) {
            FormFieldSpec f = field(power, "render_elytra");
            assertEquals("flying", roundTrip(f, new JsonPrimitive(true)).getAsString(),
                power + ": true is the wings-while-flying state");
            assertEquals("never", roundTrip(f, new JsonPrimitive(false)).getAsString(),
                power + ": false is the no-wings state");
        }
    }

    @Test
    void allThreeStringSpellingsSurviveARoundTrip() {
        FormFieldSpec f = field("neoorigins:elytra_flight", "render_elytra");
        for (String v : List.of("never", "flying", "always")) {
            assertEquals(v, roundTrip(f, new JsonPrimitive(v)).getAsString());
        }
    }

    @Test
    void anUntouchedFieldStillWritesNothing() {
        // Optional, so absent must stay absent: the three power types have
        // different defaults, and writing one of them would change behaviour.
        for (String power : WING_POWERS) {
            assertNull(roundTrip(field(power, "render_elytra"), null), power + " untouched");
        }
    }

    // ── the tooltip: it has to describe the widget the author is looking at ─

    @Test
    void aChoiceTooltipDescribesTheDropdownAndSpellsItsDefault() {
        // elytra_flight defaults to wings while flying, the other two to none.
        // The declared default is the legacy boolean, which is not an entry in the
        // dropdown, so the tooltip has to spell it the way the dropdown does.
        List<String> defaults = List.of("flying", "never", "never");
        for (int i = 0; i < WING_POWERS.size(); i++) {
            String power = WING_POWERS.get(i);
            String all = String.join("\n",
                FieldWidgetFactory.create(field(power, "render_elytra")).tooltip());
            assertFalse(all.contains("edit this as JSON"), power + " tooltip sends the author to JSON:\n" + all);
            assertFalse(all.contains("(JSON)"), power + " tooltip names a JSON box:\n" + all);
            assertTrue(all.contains("type: pick one"), power + " tooltip kind:\n" + all);
            assertTrue(all.contains("default " + defaults.get(i)), power + " tooltip default:\n" + all);
        }
        // The open union keeps the JSON guidance, because it really is a JSON box.
        String key = String.join("\n",
            FieldWidgetFactory.create(field("neoorigins:active_ability", "key")).tooltip());
        assertTrue(key.contains("edit this as JSON"), "open MIXED tooltip:\n" + key);
    }
}
