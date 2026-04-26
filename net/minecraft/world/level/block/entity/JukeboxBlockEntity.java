package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.ContainerSingleItem;

public class JukeboxBlockEntity extends BlockEntity implements Clearable, ContainerSingleItem.BlockContainerSingleItem {
    public static final String SONG_ITEM_TAG_ID = "RecordItem";
    public static final String TICKS_SINCE_SONG_STARTED_TAG_ID = "ticks_since_song_started";
    private ItemStack item = ItemStack.EMPTY;
    private final JukeboxSongPlayer jukeboxSongPlayer = new JukeboxSongPlayer(this::onSongChanged, this.getBlockPos());

    public JukeboxBlockEntity(BlockPos p_155613_, BlockState p_155614_) {
        super(BlockEntityType.JUKEBOX, p_155613_, p_155614_);
    }

    public JukeboxSongPlayer getSongPlayer() {
        return this.jukeboxSongPlayer;
    }

    public void onSongChanged() {
        this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        this.setChanged();
    }

    private void notifyItemChangedInJukebox(boolean p_350455_) {
        if (this.level != null && this.level.getBlockState(this.getBlockPos()) == this.getBlockState()) {
            this.level.setBlock(this.getBlockPos(), this.getBlockState().setValue(JukeboxBlock.HAS_RECORD, Boolean.valueOf(p_350455_)), 2);
            this.level.gameEvent(GameEvent.BLOCK_CHANGE, this.getBlockPos(), GameEvent.Context.of(this.getBlockState()));
        }
    }

    public void popOutTheItem() {
        if (this.level != null && !this.level.isClientSide) {
            BlockPos blockpos = this.getBlockPos();
            ItemStack itemstack = this.getTheItem();
            if (!itemstack.isEmpty()) {
                this.removeTheItem();
                Vec3 vec3 = Vec3.atLowerCornerWithOffset(blockpos, 0.5, 1.01, 0.5).offsetRandom(this.level.random, 0.7F);
                ItemStack itemstack1 = itemstack.copy();
                ItemEntity itementity = new ItemEntity(this.level, vec3.x(), vec3.y(), vec3.z(), itemstack1);
                itementity.setDefaultPickUpDelay();
                this.level.addFreshEntity(itementity);
            }
        }
    }

    public static void tick(Level p_273615_, BlockPos p_273143_, BlockState p_273372_, JukeboxBlockEntity p_350984_) {
        p_350984_.jukeboxSongPlayer.tick(p_273615_, p_273372_);
    }

    public int getComparatorOutput() {
        return JukeboxSong.fromStack(this.level.registryAccess(), this.item).map(Holder::value).map(JukeboxSong::comparatorOutput).orElse(0);
    }

    @Override
    protected void loadAdditional(CompoundTag p_155616_, HolderLookup.Provider p_324026_) {
        super.loadAdditional(p_155616_, p_324026_);
        if (p_155616_.contains("RecordItem", 10)) {
            this.item = ItemStack.parse(p_324026_, p_155616_.getCompound("RecordItem")).orElse(ItemStack.EMPTY);
        } else {
            this.item = ItemStack.EMPTY;
        }

        if (p_155616_.contains("ticks_since_song_started", 4)) {
            JukeboxSong.fromStack(p_324026_, this.item)
                .ifPresent(
                    p_350445_ -> this.jukeboxSongPlayer.setSongWithoutPlaying((Holder<JukeboxSong>)p_350445_, p_155616_.getLong("ticks_since_song_started"))
                );
        }
    }

    @Override
    protected void saveAdditional(CompoundTag p_187507_, HolderLookup.Provider p_323723_) {
        super.saveAdditional(p_187507_, p_323723_);
        if (!this.getTheItem().isEmpty()) {
            p_187507_.put("RecordItem", this.getTheItem().save(p_323723_));
        }

        if (this.jukeboxSongPlayer.getSong() != null) {
            p_187507_.putLong("ticks_since_song_started", this.jukeboxSongPlayer.getTicksSinceSongStarted());
        }
    }

    @Override
    public ItemStack getTheItem() {
        return this.item;
    }

    @Override
    public ItemStack splitTheItem(int p_304604_) {
        ItemStack itemstack = this.item;
        this.setTheItem(ItemStack.EMPTY);
        return itemstack;
    }

    @Override
    public void setTheItem(ItemStack p_304781_) {
        this.item = p_304781_;
        boolean flag = !this.item.isEmpty();
        Optional<Holder<JukeboxSong>> optional = JukeboxSong.fromStack(this.level.registryAccess(), this.item);
        this.notifyItemChangedInJukebox(flag);
        if (flag && optional.isPresent()) {
            this.jukeboxSongPlayer.play(this.level, optional.get());
        } else {
            this.jukeboxSongPlayer.stop(this.level, this.getBlockState());
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public BlockEntity getContainerBlockEntity() {
        return this;
    }

    @Override
    public boolean canPlaceItem(int p_273369_, ItemStack p_273689_) {
        return p_273689_.has(DataComponents.JUKEBOX_PLAYABLE) && this.getItem(p_273369_).isEmpty();
    }

    @Override
    public boolean canTakeItem(Container p_273497_, int p_273168_, ItemStack p_273785_) {
        return p_273497_.hasAnyMatching(ItemStack::isEmpty);
    }

    @VisibleForTesting
    public void setSongItemWithoutPlaying(ItemStack p_350615_) {
        this.item = p_350615_;
        JukeboxSong.fromStack(this.level.registryAccess(), p_350615_)
            .ifPresent(p_350672_ -> this.jukeboxSongPlayer.setSongWithoutPlaying((Holder<JukeboxSong>)p_350672_, 0L));
        this.level.updateNeighborsAt(this.getBlockPos(), this.getBlockState().getBlock());
        this.setChanged();
    }

    @VisibleForTesting
    public void tryForcePlaySong() {
        JukeboxSong.fromStack(this.level.registryAccess(), this.getTheItem())
            .ifPresent(p_350319_ -> this.jukeboxSongPlayer.play(this.level, (Holder<JukeboxSong>)p_350319_));
    }
}
