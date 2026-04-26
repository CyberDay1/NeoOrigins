package net.minecraft.commands.functions;

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record PlainTextFunction<T>(ResourceLocation id, List<UnboundEntryAction<T>> entries) implements CommandFunction<T>, InstantiatedFunction<T> {
    @Override
    public InstantiatedFunction<T> instantiate(@Nullable CompoundTag p_306181_, CommandDispatcher<T> p_306210_) throws FunctionInstantiationException {
        return this;
    }
}
