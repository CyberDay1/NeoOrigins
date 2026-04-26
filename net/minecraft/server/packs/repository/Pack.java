package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FeatureFlagsMetadataSection;
import net.minecraft.server.packs.OverlayMetadataSection;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.flag.FeatureFlagSet;
import org.slf4j.Logger;

public class Pack {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;
    private final Pack.ResourcesSupplier resources;
    private final Pack.Metadata metadata;
    private final PackSelectionConfig selectionConfig;
    private final boolean hidden; // Neo: Allow packs to be hidden from the UI entirely
    private final List<Pack> children; // Neo: Allows packs to specify packs which will always be placed beneath them; must be hidden
    private static final PackSource CHILD_SOURCE = PackSource.create(
            name -> Component.translatable(
                    "pack.nameAndSource",
                    name,
                    Component.translatable("pack.neoforge.source.child")
            ).withStyle(net.minecraft.ChatFormatting.GRAY),
            false
    ); // Neo: Pack source for child packs; should not be otherwise used

    @Nullable
    public static Pack readMetaAndCreate(PackLocationInfo p_326065_, Pack.ResourcesSupplier p_252210_, PackType p_250595_, PackSelectionConfig p_326429_) {
        int i = SharedConstants.getCurrentVersion().getPackVersion(p_250595_);
        Pack.Metadata pack$metadata = readPackMetadata(p_326065_, p_252210_, i);
        return pack$metadata != null ? new Pack(p_326065_, p_252210_, pack$metadata, p_326429_) : null;
    }

    public Pack(PackLocationInfo p_326123_, Pack.ResourcesSupplier p_249377_, Pack.Metadata p_326375_, PackSelectionConfig p_326282_) {
        this(p_326123_, p_249377_, p_326375_, p_326282_, List.of());
    }

    private Pack(PackLocationInfo p_326123_, Pack.ResourcesSupplier p_249377_, Pack.Metadata p_326375_, PackSelectionConfig p_326282_, List<Pack> children) {
        List<Pack> flattenedChildren = new java.util.ArrayList<>();
        List<Pack> remainingChildren = children;
        // recursively flatten children
        while (!remainingChildren.isEmpty()) {
            List<Pack> oldChildren = remainingChildren;
            remainingChildren = new java.util.ArrayList<>();
            for (Pack child : oldChildren) {
                // Adapts the child pack with the following changes:
                // - Must be hidden
                // - Must have no children
                // - Has a pack source of CHILD_SOURCE, which is not automatically added
                Pack adaptedChild = new Pack(
                        new PackLocationInfo(child.location.id(), child.location.title(), CHILD_SOURCE, child.location.knownPackInfo()),
                        child.resources,
                        new Metadata(child.metadata.description, child.metadata.compatibility, child.metadata.requestedFeatures, child.metadata.overlays, true),
                        new PackSelectionConfig(false, child.selectionConfig.defaultPosition(), child.selectionConfig.fixedPosition()),
                        List.of()
                );
                flattenedChildren.add(adaptedChild);
                remainingChildren.addAll(child.getChildren());
            }
        }
        this.children = List.copyOf(flattenedChildren);
        this.hidden = p_326375_.isHidden();
        this.location = p_326123_;
        this.resources = p_249377_;
        this.metadata = p_326375_;
        this.selectionConfig = p_326282_;
    }

    @Nullable
    public static Pack.Metadata readPackMetadata(PackLocationInfo p_326060_, Pack.ResourcesSupplier p_326166_, int p_326341_) {
        try {
            Pack.Metadata pack$metadata;
            try (PackResources packresources = p_326166_.openPrimary(p_326060_)) {
                PackMetadataSection packmetadatasection = packresources.getMetadataSection(PackMetadataSection.TYPE);
                if (packmetadatasection == null) {
                    LOGGER.warn("Missing metadata in pack {}", p_326060_.id());
                    return null;
                }

                FeatureFlagsMetadataSection featureflagsmetadatasection = packresources.getMetadataSection(FeatureFlagsMetadataSection.TYPE);
                FeatureFlagSet featureflagset = featureflagsmetadatasection != null ? featureflagsmetadatasection.flags() : FeatureFlagSet.of();
                InclusiveRange<Integer> inclusiverange = getDeclaredPackVersions(p_326060_.id(), packmetadatasection);
                PackCompatibility packcompatibility = PackCompatibility.forVersion(inclusiverange, p_326341_);
                OverlayMetadataSection overlaymetadatasection = packresources.getMetadataSection(OverlayMetadataSection.TYPE);
                List<String> list = overlaymetadatasection != null ? overlaymetadatasection.overlaysForVersion(p_326341_) : List.of();
                // Neo: add `neoforge:overlays` overlays as well
                var neoOverlays = packresources.getMetadataSection(OverlayMetadataSection.NEOFORGE_TYPE);
                if (neoOverlays != null) {
                    list = new java.util.ArrayList<>(list);
                    list.addAll(neoOverlays.overlaysForVersion(p_326341_));
                    list = List.copyOf(list);
                }
                pack$metadata = new Pack.Metadata(packmetadatasection.description(), packcompatibility, featureflagset, list, packresources.isHidden());
            }

            return pack$metadata;
        } catch (Exception exception) {
            LOGGER.warn("Failed to read pack {} metadata", p_326060_.id(), exception);
            return null;
        }
    }

    public static InclusiveRange<Integer> getDeclaredPackVersions(String p_295795_, PackMetadataSection p_294612_) {
        int i = p_294612_.packFormat();
        if (p_294612_.supportedFormats().isEmpty()) {
            return new InclusiveRange<>(i);
        } else {
            InclusiveRange<Integer> inclusiverange = p_294612_.supportedFormats().get();
            if (!inclusiverange.isValueInRange(i)) {
                LOGGER.warn("Pack {} declared support for versions {} but declared main format is {}, defaulting to {}", p_295795_, inclusiverange, i, i);
                return new InclusiveRange<>(i);
            } else {
                return inclusiverange;
            }
        }
    }

    public PackLocationInfo location() {
        return this.location;
    }

    public Component getTitle() {
        return this.location.title();
    }

    public Component getDescription() {
        return this.metadata.description();
    }

    public Component getChatLink(boolean p_10438_) {
        return this.location.createChatLink(p_10438_, this.metadata.description);
    }

    public PackCompatibility getCompatibility() {
        return this.metadata.compatibility();
    }

    public FeatureFlagSet getRequestedFeatures() {
        return this.metadata.requestedFeatures();
    }

    public PackResources open() {
        return this.resources.openFull(this.location, this.metadata);
    }

    public String getId() {
        return this.location.id();
    }

    public PackSelectionConfig selectionConfig() {
        return this.selectionConfig;
    }

    public boolean isRequired() {
        return this.selectionConfig.required();
    }

    public boolean isFixedPosition() {
        return this.selectionConfig.fixedPosition();
    }

    public Pack.Position getDefaultPosition() {
        return this.selectionConfig.defaultPosition();
    }

    public PackSource getPackSource() {
        return this.location.source();
    }

    public boolean isHidden() {
        return hidden;
    }

    public List<Pack> getChildren() {
        return children;
    }

    public java.util.stream.Stream<Pack> streamSelfAndChildren() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(this), children.stream());
    }

    /**
     * {@return a copy of the pack with the provided children in place of any children this pack currently has}
     */
    public Pack withChildren(List<Pack> children) {
        return new Pack(this.location, this.resources, this.metadata, this.selectionConfig, children);
    }

    /**
     * {@return a copy of the pack that is hidden}
     */
    public Pack hidden() {
        return new Pack(
                new PackLocationInfo(this.location.id(), this.location.title(), this.location.source(), this.location.knownPackInfo()),
                this.resources,
                new Metadata(this.metadata.description, this.metadata.compatibility, this.metadata.requestedFeatures, this.metadata.overlays, true),
                new PackSelectionConfig(this.selectionConfig.required(), this.selectionConfig.defaultPosition(), this.selectionConfig.fixedPosition()),
                this.children
        );
    }

    @Override
    public boolean equals(Object p_10448_) {
        if (this == p_10448_) {
            return true;
        } else {
            return !(p_10448_ instanceof Pack pack) ? false : this.location.equals(pack.location);
        }
    }

    @Override
    public int hashCode() {
        return this.location.hashCode();
    }

    public static record Metadata(Component description, PackCompatibility compatibility, FeatureFlagSet requestedFeatures, List<String> overlays, boolean isHidden) {
        /** @deprecated Neo: use {@link #Metadata(Component,PackCompatibility,FeatureFlagSet,List,boolean)} instead */
        @Deprecated
        public Metadata(Component description, PackCompatibility compatibility, FeatureFlagSet requestedFeatures, List<String> overlays) {
            this(description, compatibility, requestedFeatures, overlays, false);
        }
    }

    public static enum Position {
        TOP,
        BOTTOM;

        public <T> int insert(List<T> p_10471_, T p_10472_, Function<T, PackSelectionConfig> p_10473_, boolean p_10474_) {
            Pack.Position pack$position = p_10474_ ? this.opposite() : this;
            if (pack$position == BOTTOM) {
                int j;
                for (j = 0; j < p_10471_.size(); j++) {
                    PackSelectionConfig packselectionconfig1 = p_10473_.apply(p_10471_.get(j));
                    if (!packselectionconfig1.fixedPosition() || packselectionconfig1.defaultPosition() != this) {
                        break;
                    }
                }

                p_10471_.add(j, p_10472_);
                return j;
            } else {
                int i;
                for (i = p_10471_.size() - 1; i >= 0; i--) {
                    PackSelectionConfig packselectionconfig = p_10473_.apply(p_10471_.get(i));
                    if (!packselectionconfig.fixedPosition() || packselectionconfig.defaultPosition() != this) {
                        break;
                    }
                }

                p_10471_.add(i + 1, p_10472_);
                return i + 1;
            }
        }

        public Pack.Position opposite() {
            return this == TOP ? BOTTOM : TOP;
        }
    }

    public interface ResourcesSupplier {
        PackResources openPrimary(PackLocationInfo p_326301_);

        PackResources openFull(PackLocationInfo p_326241_, Pack.Metadata p_325959_);
    }
}
