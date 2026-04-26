package net.minecraft.world.level.storage.loot;

import java.util.Set;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;

public interface LootContextUser {
    default Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of();
    }

    default void validate(ValidationContext p_79022_) {
        p_79022_.validateUser(this);
    }
}
