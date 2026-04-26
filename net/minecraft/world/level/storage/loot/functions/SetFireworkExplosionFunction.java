package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetFireworkExplosionFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetFireworkExplosionFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_338149_ -> commonFields(p_338149_)
                .and(
                    p_338149_.group(
                        FireworkExplosion.Shape.CODEC.optionalFieldOf("shape").forGetter(p_333919_ -> p_333919_.shape),
                        FireworkExplosion.COLOR_LIST_CODEC.optionalFieldOf("colors").forGetter(p_333966_ -> p_333966_.colors),
                        FireworkExplosion.COLOR_LIST_CODEC.optionalFieldOf("fade_colors").forGetter(p_334021_ -> p_334021_.fadeColors),
                        Codec.BOOL.optionalFieldOf("trail").forGetter(p_334013_ -> p_334013_.trail),
                        Codec.BOOL.optionalFieldOf("twinkle").forGetter(p_333713_ -> p_333713_.twinkle)
                    )
                )
                .apply(p_338149_, SetFireworkExplosionFunction::new)
    );
    public static final FireworkExplosion DEFAULT_VALUE = new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL, IntList.of(), IntList.of(), false, false);
    final Optional<FireworkExplosion.Shape> shape;
    final Optional<IntList> colors;
    final Optional<IntList> fadeColors;
    final Optional<Boolean> trail;
    final Optional<Boolean> twinkle;

    public SetFireworkExplosionFunction(
        List<LootItemCondition> p_333763_,
        Optional<FireworkExplosion.Shape> p_333814_,
        Optional<IntList> p_333974_,
        Optional<IntList> p_333764_,
        Optional<Boolean> p_333876_,
        Optional<Boolean> p_334022_
    ) {
        super(p_333763_);
        this.shape = p_333814_;
        this.colors = p_333974_;
        this.fadeColors = p_333764_;
        this.trail = p_333876_;
        this.twinkle = p_334022_;
    }

    @Override
    protected ItemStack run(ItemStack p_334066_, LootContext p_333987_) {
        p_334066_.update(DataComponents.FIREWORK_EXPLOSION, DEFAULT_VALUE, this::apply);
        return p_334066_;
    }

    private FireworkExplosion apply(FireworkExplosion p_333781_) {
        return new FireworkExplosion(
            this.shape.orElseGet(p_333781_::shape),
            this.colors.orElseGet(p_333781_::colors),
            this.fadeColors.orElseGet(p_333781_::fadeColors),
            this.trail.orElseGet(p_333781_::hasTrail),
            this.twinkle.orElseGet(p_333781_::hasTwinkle)
        );
    }

    @Override
    public LootItemFunctionType<SetFireworkExplosionFunction> getType() {
        return LootItemFunctions.SET_FIREWORK_EXPLOSION;
    }
}
