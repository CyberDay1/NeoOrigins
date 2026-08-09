package com.cyberday1.neoorigins.network.payload;

import com.cyberday1.neoorigins.power.builtin.ModifyFoodNutritionPower;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server → client. Carries the owning player's currently-granted
 * {@code neoorigins:modify_food_nutrition} overrides (nutrition value + the
 * optional {@code food_item} / {@code food_tag} filter), so the client can work
 * out what a held food is worth to <em>this</em> origin without eating it.
 *
 * <p>Why this exists: the power never rewrites an item's {@code FOOD} data
 * component - it lets vanilla eat the item and then rewrites the player's
 * food/saturation server-side from a pre-eat baseline
 * ({@code ModifyFoodNutritionPower.applyOverride}). AppleSkin builds its tooltip
 * and held-food HUD preview by reading {@code DataComponents.FOOD} off the stack
 * on the client, so it could never see the override: an aquatic origin holding a
 * cod was shown the vanilla 2/0.4 while eating it actually gave the elevated
 * value. {@code compat.appleskin.AppleSkinBridge} answers AppleSkin's
 * {@code FoodValuesEvent} from this data.
 *
 * <p>Like {@link SyncActivePowersPayload} (and unlike {@link SyncElytraFlightPayload}
 * or {@link SyncInvisibilityArmorPayload}, which are broadcast to every tracking
 * client), this reaches only the owning player - nobody else's HUD needs it. It is
 * sent from {@code NeoOriginsNetwork.syncActivePowersToPlayer}, so it inherits that
 * method's trigger set for free: login, respawn, dimension change, origin change,
 * toggle flip and datapack reload.
 *
 * <p>An empty list is a meaningful payload: it means "this origin overrides
 * nothing", and clears whatever the client was holding from a previous origin.
 */
public record SyncFoodNutritionPayload(List<Entry> overrides) implements CustomPacketPayload {

    /**
     * One {@code modify_food_nutrition} power, flattened for the wire.
     * {@code foodItem} / {@code foodTag} are {@code ""} to mean "absent"
     * (i.e. no filter on that axis), matching the {@code Optional} fields on
     * {@link ModifyFoodNutritionPower.Config}.
     */
    public record Entry(int nutrition, String foodItem, String foodTag) {

        public static Entry of(ModifyFoodNutritionPower.Config config) {
            return new Entry(
                config.nutrition(),
                config.foodItem().orElse(""),
                config.foodTag().orElse("")
            );
        }

        /**
         * Rebuild the power config so the client can run the <em>real</em>
         * {@code ModifyFoodNutritionPower.matchesFilter} against it rather than a
         * re-implementation that could drift from the server's filtering.
         */
        public ModifyFoodNutritionPower.Config toConfig() {
            return new ModifyFoodNutritionPower.Config(
                nutrition,
                foodItem.isEmpty() ? Optional.empty() : Optional.of(foodItem),
                foodTag.isEmpty() ? Optional.empty() : Optional.of(foodTag),
                ""
            );
        }
    }

    /**
     * Hard cap on decoded entries. A player realistically holds a handful of diet
     * overrides; this just stops a malformed or hostile packet from asking the
     * client to allocate an unbounded list before anything validates it.
     */
    private static final int MAX_ENTRIES = 256;

    public static final Type<SyncFoodNutritionPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("neoorigins", "sync_food_nutrition"));

    public static final StreamCodec<FriendlyByteBuf, SyncFoodNutritionPayload> STREAM_CODEC =
        StreamCodec.of(SyncFoodNutritionPayload::encode, SyncFoodNutritionPayload::decode);

    private static void encode(FriendlyByteBuf buf, SyncFoodNutritionPayload payload) {
        List<Entry> entries = payload.overrides();
        int count = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            buf.writeVarInt(entry.nutrition());
            buf.writeUtf(entry.foodItem());
            buf.writeUtf(entry.foodTag());
        }
    }

    private static SyncFoodNutritionPayload decode(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int nutrition = buf.readVarInt();
            String item = buf.readUtf();
            String tag = buf.readUtf();
            entries.add(new Entry(nutrition, item, tag));
        }
        return new SyncFoodNutritionPayload(List.copyOf(entries));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
