package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetFireworksFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetFireworksFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_341586_ -> commonFields(p_341586_)
                .and(
                    p_341586_.group(
                        ListOperation.StandAlone.codec(FireworkExplosion.CODEC, 256).optionalFieldOf("explosions").forGetter(p_341585_ -> p_341585_.explosions),
                        ExtraCodecs.UNSIGNED_BYTE.optionalFieldOf("flight_duration").forGetter(p_333834_ -> p_333834_.flightDuration)
                    )
                )
                .apply(p_341586_, SetFireworksFunction::new)
    );
    public static final Fireworks DEFAULT_VALUE = new Fireworks(0, List.of());
    private final Optional<ListOperation.StandAlone<FireworkExplosion>> explosions;
    private final Optional<Integer> flightDuration;

    public SetFireworksFunction(
        List<LootItemCondition> p_333807_, Optional<ListOperation.StandAlone<FireworkExplosion>> p_333866_, Optional<Integer> p_341606_
    ) {
        super(p_333807_);
        this.explosions = p_333866_;
        this.flightDuration = p_341606_;
    }

    @Override
    protected ItemStack run(ItemStack p_334053_, LootContext p_333744_) {
        p_334053_.update(DataComponents.FIREWORKS, DEFAULT_VALUE, this::apply);
        return p_334053_;
    }

    private Fireworks apply(Fireworks p_333768_) {
        return new Fireworks(
            this.flightDuration.orElseGet(p_333768_::flightDuration),
            this.explosions.<List<FireworkExplosion>>map(p_341588_ -> p_341588_.apply(p_333768_.explosions())).orElse(p_333768_.explosions())
        );
    }

    @Override
    public LootItemFunctionType<SetFireworksFunction> getType() {
        return LootItemFunctions.SET_FIREWORKS;
    }
}
