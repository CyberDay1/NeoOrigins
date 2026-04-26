package net.minecraft.client.gui.screens.worldselection;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.DatapackLoadFailureScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.NoticeWithLinkScreen;
import net.minecraft.client.gui.screens.RecoverWorldDataScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.util.MemoryReserve;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.validation.ContentValidationException;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class WorldOpenFlows { // TODO 1.20.3 PORTING: re-add the autoconfirm code
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID WORLD_PACK_ID = UUID.fromString("640a6a92-b6cb-48a0-b391-831586500359");
    private final Minecraft minecraft;
    private final LevelStorageSource levelSource;

    public WorldOpenFlows(Minecraft p_233093_, LevelStorageSource p_233094_) {
        this.minecraft = p_233093_;
        this.levelSource = p_233094_;
    }

    public void createFreshLevel(
        String p_233158_, LevelSettings p_233159_, WorldOptions p_249243_, Function<RegistryAccess, WorldDimensions> p_249252_, Screen p_307305_
    ) {
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));
        LevelStorageSource.LevelStorageAccess levelstoragesource$levelstorageaccess = this.createWorldAccess(p_233158_);
        if (levelstoragesource$levelstorageaccess != null) {
            PackRepository packrepository = ServerPacksSource.createPackRepository(levelstoragesource$levelstorageaccess);
            WorldDataConfiguration worlddataconfiguration = p_233159_.getDataConfiguration();

            try {
                WorldLoader.PackConfig worldloader$packconfig = new WorldLoader.PackConfig(packrepository, worlddataconfiguration, false, false);
                WorldStem worldstem = this.loadWorldDataBlocking(
                    worldloader$packconfig,
                    p_258145_ -> {
                        WorldDimensions.Complete worlddimensions$complete = p_249252_.apply(p_258145_.datapackWorldgen())
                            .bake(p_258145_.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM));
                        return new WorldLoader.DataLoadOutput<>(
                            new PrimaryLevelData(p_233159_, p_249243_, worlddimensions$complete.specialWorldProperty(), worlddimensions$complete.lifecycle()),
                            worlddimensions$complete.dimensionsRegistryAccess()
                        );
                    },
                    WorldStem::new
                );
                this.minecraft.doWorldLoad(levelstoragesource$levelstorageaccess, packrepository, worldstem, true);
            } catch (Exception exception) {
                LOGGER.warn("Failed to load datapacks, can't proceed with server load", (Throwable)exception);
                levelstoragesource$levelstorageaccess.safeClose();
                this.minecraft.setScreen(p_307305_);
            }
        }
    }

    @Nullable
    private LevelStorageSource.LevelStorageAccess createWorldAccess(String p_233156_) {
        try {
            return this.levelSource.validateAndCreateAccess(p_233156_);
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to read level {} data", p_233156_, ioexception);
            SystemToast.onWorldAccessFailure(this.minecraft, p_233156_);
            this.minecraft.setScreen(null);
            return null;
        } catch (ContentValidationException contentvalidationexception) {
            LOGGER.warn("{}", contentvalidationexception.getMessage());
            this.minecraft.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> this.minecraft.setScreen(null)));
            return null;
        }
    }

    public void createLevelFromExistingSettings(
        LevelStorageSource.LevelStorageAccess p_250919_,
        ReloadableServerResources p_248897_,
        LayeredRegistryAccess<RegistryLayer> p_250801_,
        WorldData p_251654_
    ) {
        PackRepository packrepository = ServerPacksSource.createPackRepository(p_250919_);
        CloseableResourceManager closeableresourcemanager = new WorldLoader.PackConfig(packrepository, p_251654_.getDataConfiguration(), false, false)
            .createResourceManager()
            .getSecond();
        this.minecraft.doWorldLoad(p_250919_, packrepository, new WorldStem(closeableresourcemanager, p_248897_, p_250801_, p_251654_), true);
    }

    public WorldStem loadWorldStem(Dynamic<?> p_307491_, boolean p_233124_, PackRepository p_233125_) throws Exception {
        WorldLoader.PackConfig worldloader$packconfig = LevelStorageSource.getPackConfig(p_307491_, p_233125_, p_233124_);
        return this.loadWorldDataBlocking(
            worldloader$packconfig,
            p_307082_ -> {
                Registry<LevelStem> registry = p_307082_.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM);
                LevelDataAndDimensions leveldataanddimensions = LevelStorageSource.getLevelDataAndDimensions(
                    p_307491_, p_307082_.dataConfiguration(), registry, p_307082_.datapackWorldgen()
                );
                return new WorldLoader.DataLoadOutput<>(leveldataanddimensions.worldData(), leveldataanddimensions.dimensions().dimensionsRegistryAccess());
            },
            WorldStem::new
        );
    }

    public Pair<LevelSettings, WorldCreationContext> recreateWorldData(LevelStorageSource.LevelStorageAccess p_249540_) throws Exception {
        PackRepository packrepository = ServerPacksSource.createPackRepository(p_249540_);
        Dynamic<?> dynamic = p_249540_.getDataTag();
        WorldLoader.PackConfig worldloader$packconfig = LevelStorageSource.getPackConfig(dynamic, packrepository, false);

        @OnlyIn(Dist.CLIENT)
        record Data(LevelSettings levelSettings, WorldOptions options, Registry<LevelStem> existingDimensions) {
        }

        return this.<Data, Pair<LevelSettings, WorldCreationContext>>loadWorldDataBlocking(
            worldloader$packconfig,
            p_307097_ -> {
                Registry<LevelStem> registry = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable()).freeze();
                LevelDataAndDimensions leveldataanddimensions = LevelStorageSource.getLevelDataAndDimensions(
                    dynamic, p_307097_.dataConfiguration(), registry, p_307097_.datapackWorldgen()
                );
                return new WorldLoader.DataLoadOutput<>(
                    new Data(
                        leveldataanddimensions.worldData().getLevelSettings(),
                        leveldataanddimensions.worldData().worldGenOptions(),
                        leveldataanddimensions.dimensions().dimensions()
                    ),
                    p_307097_.datapackDimensions()
                );
            },
            (p_247840_, p_247841_, p_247842_, p_247843_) -> {
                p_247840_.close();
                return Pair.of(
                    p_247843_.levelSettings,
                    new WorldCreationContext(
                        p_247843_.options,
                        new WorldDimensions(p_247843_.existingDimensions),
                        p_247842_,
                        p_247841_,
                        p_247843_.levelSettings.getDataConfiguration()
                    )
                );
            }
        );
    }

    private <D, R> R loadWorldDataBlocking(
        WorldLoader.PackConfig p_250997_, WorldLoader.WorldDataSupplier<D> p_251759_, WorldLoader.ResultFactory<D, R> p_249635_
    ) throws Exception {
        WorldLoader.InitConfig worldloader$initconfig = new WorldLoader.InitConfig(p_250997_, Commands.CommandSelection.INTEGRATED, 2);
        CompletableFuture<R> completablefuture = WorldLoader.load(worldloader$initconfig, p_251759_, p_249635_, Util.backgroundExecutor(), this.minecraft);
        this.minecraft.managedBlock(completablefuture::isDone);
        return completablefuture.get();
    }

    private void askForBackup(LevelStorageSource.LevelStorageAccess p_307627_, boolean p_233143_, Runnable p_233144_, Runnable p_307323_) {
        Component component;
        Component component1;
        if (p_233143_) {
            component = Component.translatable("selectWorld.backupQuestion.customized");
            component1 = Component.translatable("selectWorld.backupWarning.customized");
        } else {
            component = Component.translatable("selectWorld.backupQuestion.experimental");
            // Neo: Add a line saying that the message won't show again.
            component1 = Component.translatable("selectWorld.backupWarning.experimental")
                    .append("\n\n")
                    .append(Component.translatable("neoforge.selectWorld.backupWarning.experimental.additional"));
        }

        this.minecraft.setScreen(new BackupConfirmScreen(p_307323_, (p_307085_, p_307086_) -> {
            if (p_307085_) {
                EditWorldScreen.makeBackupAndShowToast(p_307627_);
            }

            p_233144_.run();
        }, component, component1, false));
    }

    public static void confirmWorldCreation(Minecraft p_270593_, CreateWorldScreen p_270733_, Lifecycle p_270539_, Runnable p_270158_, boolean p_270709_) {
        BooleanConsumer booleanconsumer = p_233154_ -> {
            if (p_233154_) {
                p_270158_.run();
            } else {
                p_270593_.setScreen(p_270733_);
            }
        };
        if (p_270709_ || p_270539_ == Lifecycle.stable()) {
            p_270158_.run();
        } else if (p_270539_ == Lifecycle.experimental()) {
            p_270593_.setScreen(
                new ConfirmScreen(
                    booleanconsumer,
                    Component.translatable("selectWorld.warning.experimental.title"),
                    Component.translatable("selectWorld.warning.experimental.question")
                )
            );
        } else {
            p_270593_.setScreen(
                new ConfirmScreen(
                    booleanconsumer,
                    Component.translatable("selectWorld.warning.deprecated.title"),
                    Component.translatable("selectWorld.warning.deprecated.question")
                )
            );
        }
    }

    public void openWorld(String p_330611_, Runnable p_331729_) {
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));
        LevelStorageSource.LevelStorageAccess levelstoragesource$levelstorageaccess = this.createWorldAccess(p_330611_);
        if (levelstoragesource$levelstorageaccess != null) {
            this.openWorldLoadLevelData(levelstoragesource$levelstorageaccess, p_331729_);
        }
    }

    private void openWorldLoadLevelData(LevelStorageSource.LevelStorageAccess p_330608_, Runnable p_331368_) {
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));

        Dynamic<?> dynamic;
        LevelSummary levelsummary;
        try {
            dynamic = p_330608_.getDataTag();
            levelsummary = p_330608_.getSummary(dynamic);
            p_330608_.readAdditionalLevelSaveData(false);
        } catch (NbtException | ReportedNbtException | IOException ioexception) {
            this.minecraft.setScreen(new RecoverWorldDataScreen(this.minecraft, p_329781_ -> {
                if (p_329781_) {
                    this.openWorldLoadLevelData(p_330608_, p_331368_);
                } else {
                    p_330608_.safeClose();
                    p_331368_.run();
                }
            }, p_330608_));
            return;
        } catch (OutOfMemoryError outofmemoryerror1) {
            MemoryReserve.release();
            System.gc();
            String s = "Ran out of memory trying to read level data of world folder \"" + p_330608_.getLevelId() + "\"";
            LOGGER.error(LogUtils.FATAL_MARKER, s);
            OutOfMemoryError outofmemoryerror = new OutOfMemoryError("Ran out of memory reading level data");
            outofmemoryerror.initCause(outofmemoryerror1);
            CrashReport crashreport = CrashReport.forThrowable(outofmemoryerror, s);
            CrashReportCategory crashreportcategory = crashreport.addCategory("World details");
            crashreportcategory.setDetail("World folder", p_330608_.getLevelId());
            throw new ReportedException(crashreport);
        }

        this.openWorldCheckVersionCompatibility(p_330608_, levelsummary, dynamic, p_331368_);
    }

    private void openWorldCheckVersionCompatibility(
        LevelStorageSource.LevelStorageAccess p_331650_, LevelSummary p_331090_, Dynamic<?> p_331358_, Runnable p_331242_
    ) {
        if (!p_331090_.isCompatible()) {
            p_331650_.safeClose();
            this.minecraft
                .setScreen(
                    new AlertScreen(
                        p_331242_,
                        Component.translatable("selectWorld.incompatible.title").withColor(-65536),
                        Component.translatable("selectWorld.incompatible.description", p_331090_.getWorldVersionName())
                    )
                );
        } else {
            LevelSummary.BackupStatus levelsummary$backupstatus = p_331090_.backupStatus();
            if (levelsummary$backupstatus.shouldBackup()) {
                String s = "selectWorld.backupQuestion." + levelsummary$backupstatus.getTranslationKey();
                String s1 = "selectWorld.backupWarning." + levelsummary$backupstatus.getTranslationKey();
                MutableComponent mutablecomponent = Component.translatable(s);
                if (levelsummary$backupstatus.isSevere()) {
                    mutablecomponent.withColor(-2142128);
                }

                Component component = Component.translatable(s1, p_331090_.getWorldVersionName(), SharedConstants.getCurrentVersion().getName());
                this.minecraft.setScreen(new BackupConfirmScreen(() -> {
                    p_331650_.safeClose();
                    p_331242_.run();
                }, (p_329770_, p_329771_) -> {
                    if (p_329770_) {
                        EditWorldScreen.makeBackupAndShowToast(p_331650_);
                    }

                    this.openWorldLoadLevelStem(p_331650_, p_331358_, false, p_331242_);
                }, mutablecomponent, component, false));
            } else {
                this.openWorldLoadLevelStem(p_331650_, p_331358_, false, p_331242_);
            }
        }
    }

    private void openWorldLoadLevelStem(LevelStorageSource.LevelStorageAccess p_331886_, Dynamic<?> p_332037_, boolean p_330245_, Runnable p_330289_) {
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.resource_load")));
        PackRepository packrepository = ServerPacksSource.createPackRepository(p_331886_);

        WorldStem worldstem;
        try {
            worldstem = this.loadWorldStem(p_332037_, p_330245_, packrepository);

            for (LevelStem levelstem : worldstem.registries().compositeAccess().registryOrThrow(Registries.LEVEL_STEM)) {
                levelstem.generator().validate();
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to load level data or datapacks, can't proceed with server load", (Throwable)exception);
            if (!p_330245_) {
                this.minecraft.setScreen(new DatapackLoadFailureScreen(() -> {
                    p_331886_.safeClose();
                    p_330289_.run();
                }, () -> this.openWorldLoadLevelStem(p_331886_, p_332037_, true, p_330289_)));
            } else {
                p_331886_.safeClose();
                this.minecraft
                    .setScreen(
                        new AlertScreen(
                            p_330289_,
                            Component.translatable("datapackFailure.safeMode.failed.title"),
                            Component.translatable("datapackFailure.safeMode.failed.description"),
                            CommonComponents.GUI_BACK,
                            true
                        )
                    );
            }

            return;
        }

        this.openWorldCheckWorldStemCompatibility(p_331886_, worldstem, packrepository, p_330289_);
    }

    private void openWorldCheckWorldStemCompatibility(
        LevelStorageSource.LevelStorageAccess p_331469_, WorldStem p_330774_, PackRepository p_330989_, Runnable p_332128_
    ) {
        WorldData worlddata = p_330774_.worldData();
        boolean flag = worlddata.worldGenOptions().isOldCustomizedWorld();
        boolean flag1 = worlddata.worldGenSettingsLifecycle() != Lifecycle.stable();
        boolean skipConfirmation = worlddata instanceof PrimaryLevelData pld && pld.hasConfirmedExperimentalWarning();
        if (skipConfirmation || (!flag && !flag1)) {
            this.openWorldLoadBundledResourcePack(p_331469_, p_330774_, p_330989_, p_332128_);
        } else {
            this.askForBackup(p_331469_, flag, () -> {
                if (!flag) {
                    // Neo: Prevent the message from showing again
                    if (p_330774_.worldData() instanceof PrimaryLevelData pld) {
                        pld.withConfirmedWarning(true);
                    }
                }
                this.openWorldLoadBundledResourcePack(p_331469_, p_330774_, p_330989_, p_332128_);
            }, () -> {
                p_330774_.close();
                p_331469_.safeClose();
                p_332128_.run();
            });
        }
    }

    private void openWorldLoadBundledResourcePack(
        LevelStorageSource.LevelStorageAccess p_331323_, WorldStem p_330675_, PackRepository p_332043_, Runnable p_330403_
    ) {
        DownloadedPackSource downloadedpacksource = this.minecraft.getDownloadedPackSource();
        this.loadBundledResourcePack(downloadedpacksource, p_331323_).thenApply(p_233177_ -> true).exceptionallyComposeAsync(p_233183_ -> {
            LOGGER.warn("Failed to load pack: ", p_233183_);
            return this.promptBundledPackLoadFailure();
        }, this.minecraft).thenAcceptAsync(p_329766_ -> {
            if (p_329766_) {
                this.openWorldCheckDiskSpace(p_331323_, p_330675_, downloadedpacksource, p_332043_, p_330403_);
            } else {
                downloadedpacksource.popAll();
                p_330675_.close();
                p_331323_.safeClose();
                p_330403_.run();
            }
        }, this.minecraft).exceptionally(p_233175_ -> {
            this.minecraft.delayCrash(CrashReport.forThrowable(p_233175_, "Load world"));
            return null;
        });
    }

    private void openWorldCheckDiskSpace(
        LevelStorageSource.LevelStorageAccess p_330894_, WorldStem p_331981_, DownloadedPackSource p_331902_, PackRepository p_330360_, Runnable p_330719_
    ) {
        if (p_330894_.checkForLowDiskSpace()) {
            this.minecraft
                .setScreen(
                    new ConfirmScreen(
                        p_329757_ -> {
                            if (p_329757_) {
                                this.openWorldDoLoad(p_330894_, p_331981_, p_330360_);
                            } else {
                                p_331902_.popAll();
                                p_331981_.close();
                                p_330894_.safeClose();
                                p_330719_.run();
                            }
                        },
                        Component.translatable("selectWorld.warning.lowDiskSpace.title").withStyle(ChatFormatting.RED),
                        Component.translatable("selectWorld.warning.lowDiskSpace.description"),
                        CommonComponents.GUI_CONTINUE,
                        CommonComponents.GUI_BACK
                    )
                );
        } else {
            this.openWorldDoLoad(p_330894_, p_331981_, p_330360_);
        }
    }

    private void openWorldDoLoad(LevelStorageSource.LevelStorageAccess p_330420_, WorldStem p_331123_, PackRepository p_331620_) {
        this.minecraft.doWorldLoad(p_330420_, p_331620_, p_331123_, false);
    }

    private CompletableFuture<Void> loadBundledResourcePack(DownloadedPackSource p_314527_, LevelStorageSource.LevelStorageAccess p_314569_) {
        Path path = p_314569_.getLevelPath(LevelResource.MAP_RESOURCE_FILE);
        if (Files.exists(path) && !Files.isDirectory(path)) {
            p_314527_.configureForLocalWorld();
            CompletableFuture<Void> completablefuture = p_314527_.waitForPackFeedback(WORLD_PACK_ID);
            p_314527_.pushLocalPack(WORLD_PACK_ID, path);
            return completablefuture;
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Boolean> promptBundledPackLoadFailure() {
        CompletableFuture<Boolean> completablefuture = new CompletableFuture<>();
        this.minecraft
            .setScreen(
                new ConfirmScreen(
                    completablefuture::complete,
                    Component.translatable("multiplayer.texturePrompt.failure.line1"),
                    Component.translatable("multiplayer.texturePrompt.failure.line2"),
                    CommonComponents.GUI_PROCEED,
                    CommonComponents.GUI_CANCEL
                )
            );
        return completablefuture;
    }
}
