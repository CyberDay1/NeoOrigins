package net.minecraft.world.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.DimensionTransition;

public class PortalProcessor {
    private Portal portal;
    private BlockPos entryPosition;
    private int portalTime;
    private boolean insidePortalThisTick;

    public PortalProcessor(Portal p_350931_, BlockPos p_350699_) {
        this.portal = p_350931_;
        this.entryPosition = p_350699_;
        this.insidePortalThisTick = true;
    }

    public boolean processPortalTeleportation(ServerLevel p_350279_, Entity p_350646_, boolean p_350822_) {
        if (!this.insidePortalThisTick) {
            this.decayTick();
            return false;
        } else {
            this.insidePortalThisTick = false;
            return p_350822_ && this.portalTime++ >= this.portal.getPortalTransitionTime(p_350279_, p_350646_);
        }
    }

    @Nullable
    public DimensionTransition getPortalDestination(ServerLevel p_350593_, Entity p_350987_) {
        return this.portal.getPortalDestination(p_350593_, p_350987_, this.entryPosition);
    }

    public Portal.Transition getPortalLocalTransition() {
        return this.portal.getLocalTransition();
    }

    private void decayTick() {
        this.portalTime = Math.max(this.portalTime - 4, 0);
    }

    public boolean hasExpired() {
        return this.portalTime <= 0;
    }

    public BlockPos getEntryPosition() {
        return this.entryPosition;
    }

    public void updateEntryPosition(BlockPos p_350726_) {
        this.entryPosition = p_350726_;
    }

    public int getPortalTime() {
        return this.portalTime;
    }

    public boolean isInsidePortalThisTick() {
        return this.insidePortalThisTick;
    }

    public void setAsInsidePortalThisTick(boolean p_350796_) {
        this.insidePortalThisTick = p_350796_;
    }

    public boolean isSamePortal(Portal p_350902_) {
        return this.portal == p_350902_;
    }
}
