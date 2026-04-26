package net.minecraft.world.item;

import java.util.List;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

public interface Tier {
    int getUses();

    float getSpeed();

    float getAttackDamageBonus();

    TagKey<Block> getIncorrectBlocksForDrops();

    int getEnchantmentValue();

    Ingredient getRepairIngredient();

    default Tool createToolProperties(TagKey<Block> p_335416_) {
        return new Tool(List.of(Tool.Rule.deniesDrops(this.getIncorrectBlocksForDrops()), Tool.Rule.minesAndDrops(p_335416_, this.getSpeed())), 1.0F, 1);
    }
}
