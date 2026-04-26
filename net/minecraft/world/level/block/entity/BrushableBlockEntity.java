package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class BrushableBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOOT_TABLE_TAG = "LootTable";
    private static final String LOOT_TABLE_SEED_TAG = "LootTableSeed";
    private static final String HIT_DIRECTION_TAG = "hit_direction";
    private static final String ITEM_TAG = "item";
    private static final int BRUSH_COOLDOWN_TICKS = 10;
    private static final int BRUSH_RESET_TICKS = 40;
    private static final int REQUIRED_BRUSHES_TO_BREAK = 10;
    private int brushCount;
    private long brushCountResetsAtTick;
    private long coolDownEndsAtTick;
    private ItemStack item = ItemStack.EMPTY;
    @Nullable
    private Direction hitDirection;
    @Nullable
    private ResourceKey<LootTable> lootTable;
    private long lootTableSeed;

    public BrushableBlockEntity(BlockPos p_277558_, BlockState p_278093_) {
        super(BlockEntityType.BRUSHABLE_BLOCK, p_277558_, p_278093_);
    }

    public boolean brush(long p_277786_, Player p_277520_, Direction p_277424_) {
        if (this.hitDirection == null) {
            this.hitDirection = p_277424_;
        }

        this.brushCountResetsAtTick = p_277786_ + 40L;
        if (p_277786_ >= this.coolDownEndsAtTick && this.level instanceof ServerLevel) {
            this.coolDownEndsAtTick = p_277786_ + 10L;
            this.unpackLootTable(p_277520_);
            int i = this.getCompletionState();
            if (++this.brushCount >= 10) {
                this.brushingCompleted(p_277520_);
                return true;
            } else {
                this.level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 2);
                int j = this.getCompletionState();
                if (i != j) {
                    BlockState blockstate = this.getBlockState();
                    BlockState blockstate1 = blockstate.setValue(BlockStateProperties.DUSTED, Integer.valueOf(j));
                    this.level.setBlock(this.getBlockPos(), blockstate1, 3);
                }

                return false;
            }
        } else {
            return false;
        }
    }

    public void unpackLootTable(Player p_277940_) {
        if (this.lootTable != null && this.level != null && !this.level.isClientSide() && this.level.getServer() != null) {
            LootTable loottable = this.level.getServer().reloadableRegistries().getLootTable(this.lootTable);
            if (p_277940_ instanceof ServerPlayer serverplayer) {
                CriteriaTriggers.GENERATE_LOOT.trigger(serverplayer, this.lootTable);
            }

            LootParams lootparams = new LootParams.Builder((ServerLevel)this.level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.worldPosition))
                .withLuck(p_277940_.getLuck())
                .withParameter(LootContextParams.THIS_ENTITY, p_277940_)
                .create(LootContextParamSets.CHEST);
            ObjectArrayList<ItemStack> objectarraylist = loottable.getRandomItems(lootparams, this.lootTableSeed);

            this.item = switch (objectarraylist.size()) {
                case 0 -> ItemStack.EMPTY;
                case 1 -> (ItemStack)objectarraylist.get(0);
                default -> {
                    LOGGER.warn("Expected max 1 loot from loot table {}, but got {}", this.lootTable.location(), objectarraylist.size());
                    yield objectarraylist.get(0);
                }
            };
            this.lootTable = null;
            this.setChanged();
        }
    }

    private void brushingCompleted(Player p_277549_) {
        if (this.level != null && this.level.getServer() != null) {
            this.dropContent(p_277549_);
            BlockState blockstate = this.getBlockState();
            this.level.levelEvent(3008, this.getBlockPos(), Block.getId(blockstate));
            Block block;
            if (this.getBlockState().getBlock() instanceof BrushableBlock brushableblock) {
                block = brushableblock.getTurnsInto();
            } else {
                block = Blocks.AIR;
            }

            this.level.setBlock(this.worldPosition, block.defaultBlockState(), 3);
        }
    }

    private void dropContent(Player p_278006_) {
        if (this.level != null && this.level.getServer() != null) {
            this.unpackLootTable(p_278006_);
            if (!this.item.isEmpty()) {
                double d0 = (double)EntityType.ITEM.getWidth();
                double d1 = 1.0 - d0;
                double d2 = d0 / 2.0;
                Direction direction = Objects.requireNonNullElse(this.hitDirection, Direction.UP);
                BlockPos blockpos = this.worldPosition.relative(direction, 1);
                double d3 = (double)blockpos.getX() + 0.5 * d1 + d2;
                double d4 = (double)blockpos.getY() + 0.5 + (double)(EntityType.ITEM.getHeight() / 2.0F);
                double d5 = (double)blockpos.getZ() + 0.5 * d1 + d2;
                ItemEntity itementity = new ItemEntity(this.level, d3, d4, d5, this.item.split(this.level.random.nextInt(21) + 10));
                itementity.setDeltaMovement(Vec3.ZERO);
                this.level.addFreshEntity(itementity);
                this.item = ItemStack.EMPTY;
            }
        }
    }

    public void checkReset() {
        if (this.level != null) {
            if (this.brushCount != 0 && this.level.getGameTime() >= this.brushCountResetsAtTick) {
                int i = this.getCompletionState();
                this.brushCount = Math.max(0, this.brushCount - 2);
                int j = this.getCompletionState();
                if (i != j) {
                    this.level.setBlock(this.getBlockPos(), this.getBlockState().setValue(BlockStateProperties.DUSTED, Integer.valueOf(j)), 3);
                }

                int k = 4;
                this.brushCountResetsAtTick = this.level.getGameTime() + 4L;
            }

            if (this.brushCount == 0) {
                this.hitDirection = null;
                this.brushCountResetsAtTick = 0L;
                this.coolDownEndsAtTick = 0L;
            } else {
                this.level.scheduleTick(this.getBlockPos(), this.getBlockState().getBlock(), 2);
            }
        }
    }

    private boolean tryLoadLootTable(CompoundTag p_277740_) {
        if (p_277740_.contains("LootTable", 8)) {
            this.lootTable = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(p_277740_.getString("LootTable")));
            this.lootTableSeed = p_277740_.getLong("LootTableSeed");
            return true;
        } else {
            return false;
        }
    }

    private boolean trySaveLootTable(CompoundTag p_277591_) {
        if (this.lootTable == null) {
            return false;
        } else {
            p_277591_.putString("LootTable", this.lootTable.location().toString());
            if (this.lootTableSeed != 0L) {
                p_277591_.putLong("LootTableSeed", this.lootTableSeed);
            }

            return true;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider p_323531_) {
        CompoundTag compoundtag = super.getUpdateTag(p_323531_);
        if (this.hitDirection != null) {
            compoundtag.putInt("hit_direction", this.hitDirection.ordinal());
        }

        if (!this.item.isEmpty()) {
            compoundtag.put("item", this.item.save(p_323531_));
        }

        return compoundtag;
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag p_338786_, HolderLookup.Provider p_338480_) {
        super.loadAdditional(p_338786_, p_338480_);
        if (!this.tryLoadLootTable(p_338786_) && p_338786_.contains("item")) {
            this.item = ItemStack.parse(p_338480_, p_338786_.getCompound("item")).orElse(ItemStack.EMPTY);
        } else {
            this.item = ItemStack.EMPTY;
        }

        if (p_338786_.contains("hit_direction")) {
            this.hitDirection = Direction.values()[p_338786_.getInt("hit_direction")];
        }
    }

    @Override
    protected void saveAdditional(CompoundTag p_277339_, HolderLookup.Provider p_323885_) {
        super.saveAdditional(p_277339_, p_323885_);
        if (!this.trySaveLootTable(p_277339_) && !this.item.isEmpty()) {
            p_277339_.put("item", this.item.save(p_323885_));
        }
    }

    public void setLootTable(ResourceKey<LootTable> p_335433_, long p_277991_) {
        this.lootTable = p_335433_;
        this.lootTableSeed = p_277991_;
    }

    private int getCompletionState() {
        if (this.brushCount == 0) {
            return 0;
        } else if (this.brushCount < 3) {
            return 1;
        } else {
            return this.brushCount < 6 ? 2 : 3;
        }
    }

    @Nullable
    public Direction getHitDirection() {
        return this.hitDirection;
    }

    public ItemStack getItem() {
        return this.item;
    }
}
