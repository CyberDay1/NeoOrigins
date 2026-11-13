package com.cyberday1.neoorigins.smoke;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.api.power.*;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.PowerReloadReconciler;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/** An actual addon loaded by FML, on a dedicated server, without mock registries or worlds. */
@Mod("api_smoke")
public final class ApiSmokeMod {
    private static final ResourceKey<Registry<PowerType<?>>> KEY =
        ResourceKey.createRegistryKey(ResourceLocation.parse("neoorigins:power_type"));
    private static final DeferredRegister<PowerType<?>> TYPES = DeferredRegister.create(KEY, "api_smoke");
    private static final ResourceLocation SOURCE = id("owner");
    private static final ResourceLocation CHILD_SOURCE = id("child_owner");
    private static int actionTotal;
    private static final Path OVERRIDE = Path.of("world/datapacks/api_smoke_override/data/api_smoke/origins/powers/managed.json");

    public ApiSmokeMod(IEventBus modBus) {
        TYPES.register("wrapper", Wrapper::new);
        TYPES.register(modBus);
        NeoOriginsAPI.registerEntityAction(id("heal"), Codec.INT.fieldOf("amount").codec(),
            (player, amount) -> {
                require(player.getServer().isSameThread(), "action must execute on the server thread");
                actionTotal += amount;
                if (Boolean.getBoolean("neoorigins.apiSmoke.client")) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Custom action executed: " + actionTotal));
                }
            });
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, this::login);
        NeoForge.EVENT_BUS.addListener(this::commands);
    }

    private void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("neoorigins.apiSmoke.client") || !(event.getEntity() instanceof ServerPlayer player)) return;
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        NeoOriginsAPI.setOrigin(player, ResourceLocation.parse("neoorigins:origin"), ResourceLocation.parse("neoorigins:human"));
        NeoOriginsAPI.setOrigin(player, ResourceLocation.parse("neoorigins:class"), ResourceLocation.parse("neoorigins:class_nitwit"));
        NeoOriginsAPI.grantPower(player, id("ui_action/_embedded/child"), SOURCE);
        NeoOriginsAPI.grantPower(player, id("ui_resource"), SOURCE);
        NeoOriginsAPI.grantPower(player, id("managed"), SOURCE);
    }

    private void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("neoorigins.apiSmoke.client")) return;
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("api_smoke")
            .then(net.minecraft.commands.Commands.literal("editor").executes(ctx -> {
                com.cyberday1.neoorigins.network.NeoOriginsNetwork.openCreatorFor(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(net.minecraft.commands.Commands.literal("revoke").executes(ctx -> {
                var player = ctx.getSource().getPlayerOrException();
                NeoOriginsAPI.revokePower(player, id("ui_action/_embedded/child"), SOURCE);
                NeoOriginsAPI.revokePower(player, id("ui_resource"), SOURCE);
                NeoOriginsAPI.revokePower(player, id("managed"), SOURCE);
                return 1;
            })));
    }

    public record Config(EmbeddedPower child, boolean managed) implements PowerConfiguration {
        static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
            NeoOriginsAPI.powerCodec("child").fieldOf("child").forGetter(Config::child),
            Codec.BOOL.optionalFieldOf("managed", false).forGetter(Config::managed)
        ).apply(i, Config::new));
    }

    public static final class Wrapper extends PowerType<Config> {
        @Override public Codec<Config> codec() { return Config.CODEC; }
        @Override public boolean isActivePower(Config config) { return true; }
        @Override public void onActivated(ServerPlayer player, Config config) { config.child().holder().onActivated(player); }
        @Override public void onGranted(ServerPlayer player, Config config) {
            if (config.managed()) NeoOriginsAPI.grantPower(player, config.child().id(), CHILD_SOURCE);
        }
        @Override public void onRevoked(ServerPlayer player, Config config) {
            if (config.managed()) NeoOriginsAPI.revokePower(player, config.child().id(), CHILD_SOURCE);
        }
    }

    private void started(ServerStartedEvent event) {
        if (Boolean.getBoolean("neoorigins.apiSmoke.client")) return;
        MinecraftServer server = event.getServer();
        server.execute(() -> {
            try {
                ServerPlayer player = FakePlayerFactory.get(server.overworld(),
                    new GameProfile(UUID.fromString("76398b67-548c-4a44-9166-dcf922450476"), "api_smoke"));
                float initialHealth = player.getMaxHealth();
                holder("inline").onActivated(player);
                holder("named").onActivated(player);
                require(actionTotal == 6, "nested and referenced custom actions must execute exactly once");
                require(!ActiveOriginService.activePowerIds(player).contains(id("inline/_embedded/child")),
                    "forwarding must not implicitly grant a child");
                NeoOriginsAPI.grantPower(player, id("managed"), SOURCE);
                require(player.getMaxHealth() == initialHealth + 6, "parent-owned child grant must apply its modifier");
                var before = PowerDataManager.INSTANCE.definitions();
                var held = Set.copyOf(ActiveOriginService.activePowerIds(player));
                var changed = JsonParser.parseString(Files.readString(OVERRIDE)).getAsJsonObject();
                changed.getAsJsonObject("child").addProperty("amount", 10);
                Files.writeString(OVERRIDE, changed.toString());
                var packs = new ArrayList<>(server.getPackRepository().getSelectedIds());
                if (!packs.contains("file/api_smoke_override")) packs.add("file/api_smoke_override");
                server.reloadResources(packs).whenCompleteAsync((ignored, error) -> {
                    try {
                        if (error != null) throw new IllegalStateException("resource reload failed", error);
                        // Fake players are not online connections: reconcile the real loaded generations explicitly.
                        PowerReloadReconciler.reconcile(player, before, PowerDataManager.INSTANCE.definitions(),
                            held, ActiveOriginService.activePowerIds(player));
                        require(player.getMaxHealth() == initialHealth + 10, "reload must replace old child effects exactly once");
                        holder("inline").onActivated(player);
                        require(actionTotal == 9, "custom action registration must survive reload");
                        NeoOriginsAPI.revokePower(player, id("managed"), SOURCE);
                        require(player.getMaxHealth() == initialHealth, "parent revoke must remove its child effects");
                        require(!ActiveOriginService.activePowerIds(player).contains(id("managed/_embedded/child")),
                            "parent revoke must remove its owned child grant");
                        finish(server, null);
                    } catch (Throwable failure) { finish(server, failure); }
                }, server);
            } catch (Throwable failure) { finish(server, failure); }
        });
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("api_smoke", path); }
    private static PowerHolder<?> holder(String path) {
        PowerHolder<?> power = PowerDataManager.INSTANCE.getPower(id(path));
        require(power != null, "datapack power missing: " + path);
        return power;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void finish(MinecraftServer server, Throwable failure) {
        try {
            Files.writeString(Path.of("result.txt"), failure == null ? "PASS\n" : "FAIL: " + failure + "\n");
            if (failure != null) failure.printStackTrace();
            System.out.println("[API SMOKE] " + (failure == null ? "PASS" : "FAIL"));
        } catch (Exception e) { throw new IllegalStateException(e); }
        finally { server.halt(false); }
    }
}
