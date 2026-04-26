package net.minecraft.network.syncher;

import java.util.List;

public interface SyncedDataHolder {
    void onSyncedDataUpdated(EntityDataAccessor<?> p_326288_);

    void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> p_326334_);
}
