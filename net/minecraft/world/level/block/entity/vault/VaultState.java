package net.minecraft.world.level.block.entity.vault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public enum VaultState implements StringRepresentable {
    INACTIVE("inactive", VaultState.LightLevel.HALF_LIT) {
        @Override
        protected void onEnter(ServerLevel p_324512_, BlockPos p_324300_, VaultConfig p_323552_, VaultSharedData p_324096_, boolean p_338586_) {
            p_324096_.setDisplayItem(ItemStack.EMPTY);
            p_324512_.levelEvent(3016, p_324300_, p_338586_ ? 1 : 0);
        }
    },
    ACTIVE("active", VaultState.LightLevel.LIT) {
        @Override
        protected void onEnter(ServerLevel p_324513_, BlockPos p_324445_, VaultConfig p_323855_, VaultSharedData p_323750_, boolean p_338489_) {
            if (!p_323750_.hasDisplayItem()) {
                VaultBlockEntity.Server.cycleDisplayItemFromLootTable(p_324513_, this, p_323855_, p_323750_, p_324445_);
            }

            p_324513_.levelEvent(3015, p_324445_, p_338489_ ? 1 : 0);
        }
    },
    UNLOCKING("unlocking", VaultState.LightLevel.LIT) {
        @Override
        protected void onEnter(ServerLevel p_324077_, BlockPos p_323729_, VaultConfig p_323520_, VaultSharedData p_323550_, boolean p_338182_) {
            p_324077_.playSound(null, p_323729_, SoundEvents.VAULT_INSERT_ITEM, SoundSource.BLOCKS);
        }
    },
    EJECTING("ejecting", VaultState.LightLevel.LIT) {
        @Override
        protected void onEnter(ServerLevel p_324167_, BlockPos p_324285_, VaultConfig p_324106_, VaultSharedData p_324596_, boolean p_338590_) {
            p_324167_.playSound(null, p_324285_, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS);
        }

        @Override
        protected void onExit(ServerLevel p_323987_, BlockPos p_324064_, VaultConfig p_323588_, VaultSharedData p_324224_) {
            p_323987_.playSound(null, p_324064_, SoundEvents.VAULT_CLOSE_SHUTTER, SoundSource.BLOCKS);
        }
    };

    private static final int UPDATE_CONNECTED_PLAYERS_TICK_RATE = 20;
    private static final int DELAY_BETWEEN_EJECTIONS_TICKS = 20;
    private static final int DELAY_AFTER_LAST_EJECTION_TICKS = 20;
    private static final int DELAY_BEFORE_FIRST_EJECTION_TICKS = 20;
    private final String stateName;
    private final VaultState.LightLevel lightLevel;

    VaultState(String p_323637_, VaultState.LightLevel p_324597_) {
        this.stateName = p_323637_;
        this.lightLevel = p_324597_;
    }

    @Override
    public String getSerializedName() {
        return this.stateName;
    }

    public int lightLevel() {
        return this.lightLevel.value;
    }

    public VaultState tickAndGetNext(ServerLevel p_324582_, BlockPos p_323837_, VaultConfig p_323770_, VaultServerData p_324291_, VaultSharedData p_324388_) {
        return switch (this) {
            case INACTIVE -> updateStateForConnectedPlayers(p_324582_, p_323837_, p_323770_, p_324291_, p_324388_, p_323770_.activationRange());
            case ACTIVE -> updateStateForConnectedPlayers(p_324582_, p_323837_, p_323770_, p_324291_, p_324388_, p_323770_.deactivationRange());
            case UNLOCKING -> {
                p_324291_.pauseStateUpdatingUntil(p_324582_.getGameTime() + 20L);
                yield EJECTING;
            }
            case EJECTING -> {
                if (p_324291_.getItemsToEject().isEmpty()) {
                    p_324291_.markEjectionFinished();
                    yield updateStateForConnectedPlayers(p_324582_, p_323837_, p_323770_, p_324291_, p_324388_, p_323770_.deactivationRange());
                } else {
                    float f = p_324291_.ejectionProgress();
                    this.ejectResultItem(p_324582_, p_323837_, p_324291_.popNextItemToEject(), f);
                    p_324388_.setDisplayItem(p_324291_.getNextItemToEject());
                    boolean flag = p_324291_.getItemsToEject().isEmpty();
                    int i = flag ? 20 : 20;
                    p_324291_.pauseStateUpdatingUntil(p_324582_.getGameTime() + (long)i);
                    yield EJECTING;
                }
            }
        };
    }

    private static VaultState updateStateForConnectedPlayers(
        ServerLevel p_324451_, BlockPos p_324085_, VaultConfig p_323780_, VaultServerData p_323896_, VaultSharedData p_323954_, double p_324489_
    ) {
        p_323954_.updateConnectedPlayersWithinRange(p_324451_, p_324085_, p_323896_, p_323780_, p_324489_);
        p_323896_.pauseStateUpdatingUntil(p_324451_.getGameTime() + 20L);
        return p_323954_.hasConnectedPlayers() ? ACTIVE : INACTIVE;
    }

    public void onTransition(
        ServerLevel p_323698_, BlockPos p_324545_, VaultState p_324339_, VaultConfig p_324218_, VaultSharedData p_323812_, boolean p_338417_
    ) {
        this.onExit(p_323698_, p_324545_, p_324218_, p_323812_);
        p_324339_.onEnter(p_323698_, p_324545_, p_324218_, p_323812_, p_338417_);
    }

    protected void onEnter(ServerLevel p_323591_, BlockPos p_324053_, VaultConfig p_324561_, VaultSharedData p_323516_, boolean p_338369_) {
    }

    protected void onExit(ServerLevel p_324093_, BlockPos p_324256_, VaultConfig p_324614_, VaultSharedData p_324591_) {
    }

    private void ejectResultItem(ServerLevel p_324066_, BlockPos p_324594_, ItemStack p_324065_, float p_324283_) {
        DefaultDispenseItemBehavior.spawnItem(p_324066_, p_324065_, 2, Direction.UP, Vec3.atBottomCenterOf(p_324594_).relative(Direction.UP, 1.2));
        p_324066_.levelEvent(3017, p_324594_, 0);
        p_324066_.playSound(null, p_324594_, SoundEvents.VAULT_EJECT_ITEM, SoundSource.BLOCKS, 1.0F, 0.8F + 0.4F * p_324283_);
    }

    static enum LightLevel {
        HALF_LIT(6),
        LIT(12);

        final int value;

        private LightLevel(int p_324585_) {
            this.value = p_324585_;
        }
    }
}
