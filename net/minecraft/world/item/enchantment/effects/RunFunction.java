package net.minecraft.world.item.enchantment.effects;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public record RunFunction(ResourceLocation function) implements EnchantmentEntityEffect {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<RunFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_346347_ -> p_346347_.group(ResourceLocation.CODEC.fieldOf("function").forGetter(RunFunction::function)).apply(p_346347_, RunFunction::new)
    );

    @Override
    public void apply(ServerLevel p_346152_, int p_344788_, EnchantedItemInUse p_344801_, Entity p_346210_, Vec3 p_345413_) {
        MinecraftServer minecraftserver = p_346152_.getServer();
        ServerFunctionManager serverfunctionmanager = minecraftserver.getFunctions();
        Optional<CommandFunction<CommandSourceStack>> optional = serverfunctionmanager.get(this.function);
        if (optional.isPresent()) {
            CommandSourceStack commandsourcestack = minecraftserver.createCommandSourceStack()
                .withPermission(2)
                .withSuppressedOutput()
                .withEntity(p_346210_)
                .withLevel(p_346152_)
                .withPosition(p_345413_)
                .withRotation(p_346210_.getRotationVector());
            serverfunctionmanager.execute(optional.get(), commandsourcestack);
        } else {
            LOGGER.error("Enchantment run_function effect failed for non-existent function {}", this.function);
        }
    }

    @Override
    public MapCodec<RunFunction> codec() {
        return CODEC;
    }
}
