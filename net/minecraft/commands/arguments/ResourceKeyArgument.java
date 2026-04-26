package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class ResourceKeyArgument<T> implements ArgumentType<ResourceKey<T>> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012");
    private static final DynamicCommandExceptionType ERROR_INVALID_FEATURE = new DynamicCommandExceptionType(
        p_304101_ -> Component.translatableEscape("commands.place.feature.invalid", p_304101_)
    );
    private static final DynamicCommandExceptionType ERROR_INVALID_STRUCTURE = new DynamicCommandExceptionType(
        p_304100_ -> Component.translatableEscape("commands.place.structure.invalid", p_304100_)
    );
    private static final DynamicCommandExceptionType ERROR_INVALID_TEMPLATE_POOL = new DynamicCommandExceptionType(
        p_304102_ -> Component.translatableEscape("commands.place.jigsaw.invalid", p_304102_)
    );
    final ResourceKey<? extends Registry<T>> registryKey;

    public ResourceKeyArgument(ResourceKey<? extends Registry<T>> p_212367_) {
        this.registryKey = p_212367_;
    }

    public static <T> ResourceKeyArgument<T> key(ResourceKey<? extends Registry<T>> p_212387_) {
        return new ResourceKeyArgument<>(p_212387_);
    }

    private static <T> ResourceKey<T> getRegistryKey(
        CommandContext<CommandSourceStack> p_212374_, String p_212375_, ResourceKey<Registry<T>> p_212376_, DynamicCommandExceptionType p_212377_
    ) throws CommandSyntaxException {
        ResourceKey<?> resourcekey = p_212374_.getArgument(p_212375_, ResourceKey.class);
        Optional<ResourceKey<T>> optional = resourcekey.cast(p_212376_);
        return optional.orElseThrow(() -> p_212377_.create(resourcekey));
    }

    private static <T> Registry<T> getRegistry(CommandContext<CommandSourceStack> p_212379_, ResourceKey<? extends Registry<T>> p_212380_) {
        return p_212379_.getSource().getServer().registryAccess().registryOrThrow(p_212380_);
    }

    private static <T> Holder.Reference<T> resolveKey(
        CommandContext<CommandSourceStack> p_248662_, String p_252172_, ResourceKey<Registry<T>> p_249701_, DynamicCommandExceptionType p_249790_
    ) throws CommandSyntaxException {
        ResourceKey<T> resourcekey = getRegistryKey(p_248662_, p_252172_, p_249701_, p_249790_);
        return getRegistry(p_248662_, p_249701_).getHolder(resourcekey).orElseThrow(() -> p_249790_.create(resourcekey.location()));
    }

    public static Holder.Reference<ConfiguredFeature<?, ?>> getConfiguredFeature(CommandContext<CommandSourceStack> p_249310_, String p_250729_) throws CommandSyntaxException {
        return resolveKey(p_249310_, p_250729_, Registries.CONFIGURED_FEATURE, ERROR_INVALID_FEATURE);
    }

    public static Holder.Reference<Structure> getStructure(CommandContext<CommandSourceStack> p_248804_, String p_251331_) throws CommandSyntaxException {
        return resolveKey(p_248804_, p_251331_, Registries.STRUCTURE, ERROR_INVALID_STRUCTURE);
    }

    public static Holder.Reference<StructureTemplatePool> getStructureTemplatePool(CommandContext<CommandSourceStack> p_252203_, String p_250407_) throws CommandSyntaxException {
        return resolveKey(p_252203_, p_250407_, Registries.TEMPLATE_POOL, ERROR_INVALID_TEMPLATE_POOL);
    }

    public ResourceKey<T> parse(StringReader p_212369_) throws CommandSyntaxException {
        ResourceLocation resourcelocation = ResourceLocation.read(p_212369_);
        return ResourceKey.create(this.registryKey, resourcelocation);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> p_212399_, SuggestionsBuilder p_212400_) {
        return p_212399_.getSource() instanceof SharedSuggestionProvider sharedsuggestionprovider
            ? sharedsuggestionprovider.suggestRegistryElements(this.registryKey, SharedSuggestionProvider.ElementSuggestionType.ELEMENTS, p_212400_, p_212399_)
            : p_212400_.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static class Info<T> implements ArgumentTypeInfo<ResourceKeyArgument<T>, ResourceKeyArgument.Info<T>.Template> {
        public void serializeToNetwork(ResourceKeyArgument.Info<T>.Template p_233278_, FriendlyByteBuf p_233279_) {
            p_233279_.writeResourceKey(p_233278_.registryKey);
        }

        public ResourceKeyArgument.Info<T>.Template deserializeFromNetwork(FriendlyByteBuf p_233289_) {
            return new ResourceKeyArgument.Info.Template(p_233289_.readRegistryKey());
        }

        public void serializeToJson(ResourceKeyArgument.Info<T>.Template p_233275_, JsonObject p_233276_) {
            p_233276_.addProperty("registry", p_233275_.registryKey.location().toString());
        }

        public ResourceKeyArgument.Info<T>.Template unpack(ResourceKeyArgument<T> p_233281_) {
            return new ResourceKeyArgument.Info.Template(p_233281_.registryKey);
        }

        public final class Template implements ArgumentTypeInfo.Template<ResourceKeyArgument<T>> {
            final ResourceKey<? extends Registry<T>> registryKey;

            Template(ResourceKey<? extends Registry<T>> p_233296_) {
                this.registryKey = p_233296_;
            }

            public ResourceKeyArgument<T> instantiate(CommandBuildContext p_233299_) {
                return new ResourceKeyArgument<>(this.registryKey);
            }

            @Override
            public ArgumentTypeInfo<ResourceKeyArgument<T>, ?> type() {
                return Info.this;
            }
        }
    }
}
