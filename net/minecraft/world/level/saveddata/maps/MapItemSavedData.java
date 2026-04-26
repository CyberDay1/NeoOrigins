package net.minecraft.world.level.saveddata.maps;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class MapItemSavedData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAP_SIZE = 128;
    private static final int HALF_MAP_SIZE = 64;
    public static final int MAX_SCALE = 4;
    public static final int TRACKED_DECORATION_LIMIT = 256;
    private static final String FRAME_PREFIX = "frame-";
    public final int centerX;
    public final int centerZ;
    public final ResourceKey<Level> dimension;
    private final boolean trackingPosition;
    private final boolean unlimitedTracking;
    public final byte scale;
    public byte[] colors = new byte[16384];
    public final boolean locked;
    private final List<MapItemSavedData.HoldingPlayer> carriedBy = Lists.newArrayList();
    private final Map<Player, MapItemSavedData.HoldingPlayer> carriedByPlayers = Maps.newHashMap();
    private final Map<String, MapBanner> bannerMarkers = Maps.newHashMap();
    final Map<String, MapDecoration> decorations = Maps.newLinkedHashMap();
    private final Map<String, MapFrame> frameMarkers = Maps.newHashMap();
    private int trackedDecorationCount;

    public static SavedData.Factory<MapItemSavedData> factory() {
        return new SavedData.Factory<>(() -> {
            throw new IllegalStateException("Should never create an empty map saved data");
        }, MapItemSavedData::load, DataFixTypes.SAVED_DATA_MAP_DATA);
    }

    private MapItemSavedData(
        int p_164768_, int p_164769_, byte p_164770_, boolean p_164771_, boolean p_164772_, boolean p_164773_, ResourceKey<Level> p_164774_
    ) {
        this.scale = p_164770_;
        this.centerX = p_164768_;
        this.centerZ = p_164769_;
        this.dimension = p_164774_;
        this.trackingPosition = p_164771_;
        this.unlimitedTracking = p_164772_;
        this.locked = p_164773_;
        this.setDirty();
    }

    public static MapItemSavedData createFresh(
        double p_164781_, double p_164782_, byte p_164783_, boolean p_164784_, boolean p_164785_, ResourceKey<Level> p_164786_
    ) {
        int i = 128 * (1 << p_164783_);
        int j = Mth.floor((p_164781_ + 64.0) / (double)i);
        int k = Mth.floor((p_164782_ + 64.0) / (double)i);
        int l = j * i + i / 2 - 64;
        int i1 = k * i + i / 2 - 64;
        return new MapItemSavedData(l, i1, p_164783_, p_164784_, p_164785_, false, p_164786_);
    }

    public static MapItemSavedData createForClient(byte p_164777_, boolean p_164778_, ResourceKey<Level> p_164779_) {
        return new MapItemSavedData(0, 0, p_164777_, false, false, p_164778_, p_164779_);
    }

    public static MapItemSavedData load(CompoundTag p_164808_, HolderLookup.Provider p_324560_) {
        ResourceKey<Level> resourcekey = DimensionType.parseLegacy(new Dynamic<>(NbtOps.INSTANCE, p_164808_.get("dimension")))
            .resultOrPartial(LOGGER::error)
            .orElseThrow(() -> new IllegalArgumentException("Invalid map dimension: " + p_164808_.get("dimension")));
        int i = p_164808_.getInt("xCenter");
        int j = p_164808_.getInt("zCenter");
        byte b0 = (byte)Mth.clamp(p_164808_.getByte("scale"), 0, 4);
        boolean flag = !p_164808_.contains("trackingPosition", 1) || p_164808_.getBoolean("trackingPosition");
        boolean flag1 = p_164808_.getBoolean("unlimitedTracking");
        boolean flag2 = p_164808_.getBoolean("locked");
        MapItemSavedData mapitemsaveddata = new MapItemSavedData(i, j, b0, flag, flag1, flag2, resourcekey);
        byte[] abyte = p_164808_.getByteArray("colors");
        if (abyte.length == 16384) {
            mapitemsaveddata.colors = abyte;
        }

        RegistryOps<Tag> registryops = p_324560_.createSerializationContext(NbtOps.INSTANCE);

        for (MapBanner mapbanner : MapBanner.LIST_CODEC
            .parse(registryops, p_164808_.get("banners"))
            .resultOrPartial(p_323448_ -> LOGGER.warn("Failed to parse map banner: '{}'", p_323448_))
            .orElse(List.of())) {
            mapitemsaveddata.bannerMarkers.put(mapbanner.getId(), mapbanner);
            mapitemsaveddata.addDecoration(
                mapbanner.getDecoration(),
                null,
                mapbanner.getId(),
                (double)mapbanner.pos().getX(),
                (double)mapbanner.pos().getZ(),
                180.0,
                mapbanner.name().orElse(null)
            );
        }

        ListTag listtag = p_164808_.getList("frames", 10);

        for (int k = 0; k < listtag.size(); k++) {
            MapFrame mapframe = MapFrame.load(listtag.getCompound(k));
            if (mapframe != null) {
                mapitemsaveddata.frameMarkers.put(mapframe.getId(), mapframe);
                mapitemsaveddata.addDecoration(
                    MapDecorationTypes.FRAME,
                    null,
                    getFrameKey(mapframe.getEntityId()),
                    (double)mapframe.getPos().getX(),
                    (double)mapframe.getPos().getZ(),
                    (double)mapframe.getRotation(),
                    null
                );
            }
        }

        return mapitemsaveddata;
    }

    @Override
    public CompoundTag save(CompoundTag p_77956_, HolderLookup.Provider p_323858_) {
        ResourceLocation.CODEC
            .encodeStart(NbtOps.INSTANCE, this.dimension.location())
            .resultOrPartial(LOGGER::error)
            .ifPresent(p_77954_ -> p_77956_.put("dimension", p_77954_));
        p_77956_.putInt("xCenter", this.centerX);
        p_77956_.putInt("zCenter", this.centerZ);
        p_77956_.putByte("scale", this.scale);
        p_77956_.putByteArray("colors", this.colors);
        p_77956_.putBoolean("trackingPosition", this.trackingPosition);
        p_77956_.putBoolean("unlimitedTracking", this.unlimitedTracking);
        p_77956_.putBoolean("locked", this.locked);
        RegistryOps<Tag> registryops = p_323858_.createSerializationContext(NbtOps.INSTANCE);
        p_77956_.put("banners", MapBanner.LIST_CODEC.encodeStart(registryops, List.copyOf(this.bannerMarkers.values())).getOrThrow());
        ListTag listtag = new ListTag();

        for (MapFrame mapframe : this.frameMarkers.values()) {
            listtag.add(mapframe.save());
        }

        p_77956_.put("frames", listtag);
        return p_77956_;
    }

    public MapItemSavedData locked() {
        MapItemSavedData mapitemsaveddata = new MapItemSavedData(
            this.centerX, this.centerZ, this.scale, this.trackingPosition, this.unlimitedTracking, true, this.dimension
        );
        mapitemsaveddata.bannerMarkers.putAll(this.bannerMarkers);
        mapitemsaveddata.decorations.putAll(this.decorations);
        mapitemsaveddata.trackedDecorationCount = this.trackedDecorationCount;
        System.arraycopy(this.colors, 0, mapitemsaveddata.colors, 0, this.colors.length);
        mapitemsaveddata.setDirty();
        return mapitemsaveddata;
    }

    public MapItemSavedData scaled() {
        return createFresh(
            (double)this.centerX, (double)this.centerZ, (byte)Mth.clamp(this.scale + 1, 0, 4), this.trackingPosition, this.unlimitedTracking, this.dimension
        );
    }

    private static Predicate<ItemStack> mapMatcher(ItemStack p_316807_) {
        MapId mapid = p_316807_.get(DataComponents.MAP_ID);
        return p_330169_ -> p_330169_ == p_316807_ ? true : p_330169_.is(p_316807_.getItem()) && Objects.equals(mapid, p_330169_.get(DataComponents.MAP_ID));
    }

    public void tickCarriedBy(Player p_77919_, ItemStack p_77920_) {
        if (!this.carriedByPlayers.containsKey(p_77919_)) {
            MapItemSavedData.HoldingPlayer mapitemsaveddata$holdingplayer = new MapItemSavedData.HoldingPlayer(p_77919_);
            this.carriedByPlayers.put(p_77919_, mapitemsaveddata$holdingplayer);
            this.carriedBy.add(mapitemsaveddata$holdingplayer);
        }

        Predicate<ItemStack> predicate = mapMatcher(p_77920_);
        if (!p_77919_.getInventory().contains(predicate)) {
            this.removeDecoration(p_77919_.getName().getString());
        }

        for (int i = 0; i < this.carriedBy.size(); i++) {
            MapItemSavedData.HoldingPlayer mapitemsaveddata$holdingplayer1 = this.carriedBy.get(i);
            String s = mapitemsaveddata$holdingplayer1.player.getName().getString();
            if (!mapitemsaveddata$holdingplayer1.player.isRemoved()
                && (mapitemsaveddata$holdingplayer1.player.getInventory().contains(predicate) || p_77920_.isFramed())) {
                if (!p_77920_.isFramed() && mapitemsaveddata$holdingplayer1.player.level().dimension() == this.dimension && this.trackingPosition) {
                    this.addDecoration(
                        MapDecorationTypes.PLAYER,
                        mapitemsaveddata$holdingplayer1.player.level(),
                        s,
                        mapitemsaveddata$holdingplayer1.player.getX(),
                        mapitemsaveddata$holdingplayer1.player.getZ(),
                        (double)mapitemsaveddata$holdingplayer1.player.getYRot(),
                        null
                    );
                }
            } else {
                this.carriedByPlayers.remove(mapitemsaveddata$holdingplayer1.player);
                this.carriedBy.remove(mapitemsaveddata$holdingplayer1);
                this.removeDecoration(s);
            }
        }

        if (p_77920_.isFramed() && this.trackingPosition) {
            ItemFrame itemframe = p_77920_.getFrame();
            BlockPos blockpos = itemframe.getPos();
            MapFrame mapframe1 = this.frameMarkers.get(MapFrame.frameId(blockpos));
            if (mapframe1 != null && itemframe.getId() != mapframe1.getEntityId() && this.frameMarkers.containsKey(mapframe1.getId())) {
                this.removeDecoration(getFrameKey(mapframe1.getEntityId()));
            }

            MapFrame mapframe = new MapFrame(blockpos, itemframe.getDirection().get2DDataValue() * 90, itemframe.getId());
            this.addDecoration(
                MapDecorationTypes.FRAME,
                p_77919_.level(),
                getFrameKey(itemframe.getId()),
                (double)blockpos.getX(),
                (double)blockpos.getZ(),
                (double)(itemframe.getDirection().get2DDataValue() * 90),
                null
            );
            this.frameMarkers.put(mapframe.getId(), mapframe);
        }

        MapDecorations mapdecorations = p_77920_.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
        if (!this.decorations.keySet().containsAll(mapdecorations.decorations().keySet())) {
            mapdecorations.decorations().forEach((p_352892_, p_352893_) -> {
                if (!this.decorations.containsKey(p_352892_)) {
                    this.addDecoration(p_352893_.type(), p_77919_.level(), p_352892_, p_352893_.x(), p_352893_.z(), (double)p_352893_.rotation(), null);
                }
            });
        }
    }

    public void removeDecoration(String p_164800_) {
        MapDecoration mapdecoration = this.decorations.remove(p_164800_);
        if (mapdecoration != null && mapdecoration.type().value().trackCount()) {
            this.trackedDecorationCount--;
        }

        this.setDecorationsDirty();
    }

    public static void addTargetDecoration(ItemStack p_77926_, BlockPos p_77927_, String p_77928_, Holder<MapDecorationType> p_335759_) {
        MapDecorations.Entry mapdecorations$entry = new MapDecorations.Entry(p_335759_, (double)p_77927_.getX(), (double)p_77927_.getZ(), 180.0F);
        p_77926_.update(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY, p_330166_ -> p_330166_.withDecoration(p_77928_, mapdecorations$entry));
        if (p_335759_.value().hasMapColor()) {
            p_77926_.set(DataComponents.MAP_COLOR, new MapItemColor(p_335759_.value().mapColor()));
        }
    }

    public void addDecoration(
        Holder<MapDecorationType> p_335830_,
        @Nullable LevelAccessor p_77939_,
        String p_77940_,
        double p_77941_,
        double p_77942_,
        double p_77943_,
        @Nullable Component p_77944_
    ) {
        int i = 1 << this.scale;
        float f = (float)(p_77941_ - (double)this.centerX) / (float)i;
        float f1 = (float)(p_77942_ - (double)this.centerZ) / (float)i;
        byte b0 = (byte)((int)((double)(f * 2.0F) + 0.5));
        byte b1 = (byte)((int)((double)(f1 * 2.0F) + 0.5));
        int j = 63;
        byte b2;
        if (f >= -63.0F && f1 >= -63.0F && f <= 63.0F && f1 <= 63.0F) {
            p_77943_ += p_77943_ < 0.0 ? -8.0 : 8.0;
            b2 = (byte)((int)(p_77943_ * 16.0 / 360.0));
            if (this.dimension == Level.NETHER && p_77939_ != null) {
                int l = (int)(p_77939_.getLevelData().getDayTime() / 10L);
                b2 = (byte)(l * l * 34187121 + l * 121 >> 15 & 15);
            }
        } else {
            if (!p_335830_.is(MapDecorationTypes.PLAYER)) {
                this.removeDecoration(p_77940_);
                return;
            }

            int k = 320;
            if (Math.abs(f) < 320.0F && Math.abs(f1) < 320.0F) {
                p_335830_ = MapDecorationTypes.PLAYER_OFF_MAP;
            } else {
                if (!this.unlimitedTracking) {
                    this.removeDecoration(p_77940_);
                    return;
                }

                p_335830_ = MapDecorationTypes.PLAYER_OFF_LIMITS;
            }

            b2 = 0;
            if (f <= -63.0F) {
                b0 = -128;
            }

            if (f1 <= -63.0F) {
                b1 = -128;
            }

            if (f >= 63.0F) {
                b0 = 127;
            }

            if (f1 >= 63.0F) {
                b1 = 127;
            }
        }

        MapDecoration mapdecoration1 = new MapDecoration(p_335830_, b0, b1, b2, Optional.ofNullable(p_77944_));
        MapDecoration mapdecoration = this.decorations.put(p_77940_, mapdecoration1);
        if (!mapdecoration1.equals(mapdecoration)) {
            if (mapdecoration != null && mapdecoration.type().value().trackCount()) {
                this.trackedDecorationCount--;
            }

            if (p_335830_.value().trackCount()) {
                this.trackedDecorationCount++;
            }

            this.setDecorationsDirty();
        }
    }

    @Nullable
    public Packet<?> getUpdatePacket(MapId p_323760_, Player p_164798_) {
        MapItemSavedData.HoldingPlayer mapitemsaveddata$holdingplayer = this.carriedByPlayers.get(p_164798_);
        return mapitemsaveddata$holdingplayer == null ? null : mapitemsaveddata$holdingplayer.nextUpdatePacket(p_323760_);
    }

    private void setColorsDirty(int p_164790_, int p_164791_) {
        this.setDirty();

        for (MapItemSavedData.HoldingPlayer mapitemsaveddata$holdingplayer : this.carriedBy) {
            mapitemsaveddata$holdingplayer.markColorsDirty(p_164790_, p_164791_);
        }
    }

    private void setDecorationsDirty() {
        this.setDirty();
        this.carriedBy.forEach(MapItemSavedData.HoldingPlayer::markDecorationsDirty);
    }

    public MapItemSavedData.HoldingPlayer getHoldingPlayer(Player p_77917_) {
        MapItemSavedData.HoldingPlayer mapitemsaveddata$holdingplayer = this.carriedByPlayers.get(p_77917_);
        if (mapitemsaveddata$holdingplayer == null) {
            mapitemsaveddata$holdingplayer = new MapItemSavedData.HoldingPlayer(p_77917_);
            this.carriedByPlayers.put(p_77917_, mapitemsaveddata$holdingplayer);
            this.carriedBy.add(mapitemsaveddata$holdingplayer);
        }

        return mapitemsaveddata$holdingplayer;
    }

    public boolean toggleBanner(LevelAccessor p_77935_, BlockPos p_77936_) {
        double d0 = (double)p_77936_.getX() + 0.5;
        double d1 = (double)p_77936_.getZ() + 0.5;
        int i = 1 << this.scale;
        double d2 = (d0 - (double)this.centerX) / (double)i;
        double d3 = (d1 - (double)this.centerZ) / (double)i;
        int j = 63;
        if (d2 >= -63.0 && d3 >= -63.0 && d2 <= 63.0 && d3 <= 63.0) {
            MapBanner mapbanner = MapBanner.fromWorld(p_77935_, p_77936_);
            if (mapbanner == null) {
                return false;
            }

            if (this.bannerMarkers.remove(mapbanner.getId(), mapbanner)) {
                this.removeDecoration(mapbanner.getId());
                return true;
            }

            if (!this.isTrackedCountOverLimit(256)) {
                this.bannerMarkers.put(mapbanner.getId(), mapbanner);
                this.addDecoration(mapbanner.getDecoration(), p_77935_, mapbanner.getId(), d0, d1, 180.0, mapbanner.name().orElse(null));
                return true;
            }
        }

        return false;
    }

    public void checkBanners(BlockGetter p_77931_, int p_77932_, int p_77933_) {
        Iterator<MapBanner> iterator = this.bannerMarkers.values().iterator();

        while (iterator.hasNext()) {
            MapBanner mapbanner = iterator.next();
            if (mapbanner.pos().getX() == p_77932_ && mapbanner.pos().getZ() == p_77933_) {
                MapBanner mapbanner1 = MapBanner.fromWorld(p_77931_, mapbanner.pos());
                if (!mapbanner.equals(mapbanner1)) {
                    iterator.remove();
                    this.removeDecoration(mapbanner.getId());
                }
            }
        }
    }

    public Collection<MapBanner> getBanners() {
        return this.bannerMarkers.values();
    }

    public void removedFromFrame(BlockPos p_77948_, int p_77949_) {
        this.removeDecoration(getFrameKey(p_77949_));
        this.frameMarkers.remove(MapFrame.frameId(p_77948_));
    }

    public boolean updateColor(int p_164793_, int p_164794_, byte p_164795_) {
        byte b0 = this.colors[p_164793_ + p_164794_ * 128];
        if (b0 != p_164795_) {
            this.setColor(p_164793_, p_164794_, p_164795_);
            return true;
        } else {
            return false;
        }
    }

    public void setColor(int p_164804_, int p_164805_, byte p_164806_) {
        this.colors[p_164804_ + p_164805_ * 128] = p_164806_;
        this.setColorsDirty(p_164804_, p_164805_);
    }

    public boolean isExplorationMap() {
        for (MapDecoration mapdecoration : this.decorations.values()) {
            if (mapdecoration.type().value().explorationMapElement()) {
                return true;
            }
        }

        return false;
    }

    public void addClientSideDecorations(List<MapDecoration> p_164802_) {
        this.decorations.clear();
        this.trackedDecorationCount = 0;

        for (int i = 0; i < p_164802_.size(); i++) {
            MapDecoration mapdecoration = p_164802_.get(i);
            this.decorations.put("icon-" + i, mapdecoration);
            if (mapdecoration.type().value().trackCount()) {
                this.trackedDecorationCount++;
            }
        }
    }

    public Iterable<MapDecoration> getDecorations() {
        return this.decorations.values();
    }

    public boolean isTrackedCountOverLimit(int p_181313_) {
        return this.trackedDecorationCount >= p_181313_;
    }

    private static String getFrameKey(int p_353065_) {
        return "frame-" + p_353065_;
    }

    public class HoldingPlayer {
        public final Player player;
        private boolean dirtyData = true;
        private int minDirtyX;
        private int minDirtyY;
        private int maxDirtyX = 127;
        private int maxDirtyY = 127;
        private boolean dirtyDecorations = true;
        private int tick;
        public int step;

        HoldingPlayer(Player p_77970_) {
            this.player = p_77970_;
        }

        private MapItemSavedData.MapPatch createPatch() {
            int i = this.minDirtyX;
            int j = this.minDirtyY;
            int k = this.maxDirtyX + 1 - this.minDirtyX;
            int l = this.maxDirtyY + 1 - this.minDirtyY;
            byte[] abyte = new byte[k * l];

            for (int i1 = 0; i1 < k; i1++) {
                for (int j1 = 0; j1 < l; j1++) {
                    abyte[i1 + j1 * k] = MapItemSavedData.this.colors[i + i1 + (j + j1) * 128];
                }
            }

            return new MapItemSavedData.MapPatch(i, j, k, l, abyte);
        }

        @Nullable
        Packet<?> nextUpdatePacket(MapId p_324558_) {
            MapItemSavedData.MapPatch mapitemsaveddata$mappatch;
            if (this.dirtyData) {
                this.dirtyData = false;
                mapitemsaveddata$mappatch = this.createPatch();
            } else {
                mapitemsaveddata$mappatch = null;
            }

            Collection<MapDecoration> collection;
            if (this.dirtyDecorations && this.tick++ % 5 == 0) {
                this.dirtyDecorations = false;
                collection = MapItemSavedData.this.decorations.values();
            } else {
                collection = null;
            }

            return collection == null && mapitemsaveddata$mappatch == null
                ? null
                : new ClientboundMapItemDataPacket(p_324558_, MapItemSavedData.this.scale, MapItemSavedData.this.locked, collection, mapitemsaveddata$mappatch);
        }

        void markColorsDirty(int p_164818_, int p_164819_) {
            if (this.dirtyData) {
                this.minDirtyX = Math.min(this.minDirtyX, p_164818_);
                this.minDirtyY = Math.min(this.minDirtyY, p_164819_);
                this.maxDirtyX = Math.max(this.maxDirtyX, p_164818_);
                this.maxDirtyY = Math.max(this.maxDirtyY, p_164819_);
            } else {
                this.dirtyData = true;
                this.minDirtyX = p_164818_;
                this.minDirtyY = p_164819_;
                this.maxDirtyX = p_164818_;
                this.maxDirtyY = p_164819_;
            }
        }

        private void markDecorationsDirty() {
            this.dirtyDecorations = true;
        }
    }

    public static record MapPatch(int startX, int startY, int width, int height, byte[] mapColors) {
        public static final StreamCodec<ByteBuf, Optional<MapItemSavedData.MapPatch>> STREAM_CODEC = StreamCodec.of(
            MapItemSavedData.MapPatch::write, MapItemSavedData.MapPatch::read
        );

        private static void write(ByteBuf p_323934_, Optional<MapItemSavedData.MapPatch> p_323549_) {
            if (p_323549_.isPresent()) {
                MapItemSavedData.MapPatch mapitemsaveddata$mappatch = p_323549_.get();
                p_323934_.writeByte(mapitemsaveddata$mappatch.width);
                p_323934_.writeByte(mapitemsaveddata$mappatch.height);
                p_323934_.writeByte(mapitemsaveddata$mappatch.startX);
                p_323934_.writeByte(mapitemsaveddata$mappatch.startY);
                FriendlyByteBuf.writeByteArray(p_323934_, mapitemsaveddata$mappatch.mapColors);
            } else {
                p_323934_.writeByte(0);
            }
        }

        private static Optional<MapItemSavedData.MapPatch> read(ByteBuf p_323587_) {
            int i = p_323587_.readUnsignedByte();
            if (i > 0) {
                int j = p_323587_.readUnsignedByte();
                int k = p_323587_.readUnsignedByte();
                int l = p_323587_.readUnsignedByte();
                byte[] abyte = FriendlyByteBuf.readByteArray(p_323587_);
                return Optional.of(new MapItemSavedData.MapPatch(k, l, i, j, abyte));
            } else {
                return Optional.empty();
            }
        }

        public void applyToMap(MapItemSavedData p_164833_) {
            for (int i = 0; i < this.width; i++) {
                for (int j = 0; j < this.height; j++) {
                    p_164833_.setColor(this.startX + i, this.startY + j, this.mapColors[i + j * this.width]);
                }
            }
        }
    }
}
