package net.minecraft.server;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.TickRateManager;

public class ServerTickRateManager extends TickRateManager {
    private long remainingSprintTicks = 0L;
    private long sprintTickStartTime = 0L;
    private long sprintTimeSpend = 0L;
    private long scheduledCurrentSprintTicks = 0L;
    private boolean previousIsFrozen = false;
    private final MinecraftServer server;

    public ServerTickRateManager(MinecraftServer p_309004_) {
        this.server = p_309004_;
    }

    public boolean isSprinting() {
        return this.scheduledCurrentSprintTicks > 0L;
    }

    @Override
    public void setFrozen(boolean p_309002_) {
        super.setFrozen(p_309002_);
        this.updateStateToClients();
    }

    private void updateStateToClients() {
        this.server.getPlayerList().broadcastAll(ClientboundTickingStatePacket.from(this));
    }

    private void updateStepTicks() {
        this.server.getPlayerList().broadcastAll(ClientboundTickingStepPacket.from(this));
    }

    public boolean stepGameIfPaused(int p_308975_) {
        if (!this.isFrozen()) {
            return false;
        } else {
            this.frozenTicksToRun = p_308975_;
            this.updateStepTicks();
            return true;
        }
    }

    public boolean stopStepping() {
        if (this.frozenTicksToRun > 0) {
            this.frozenTicksToRun = 0;
            this.updateStepTicks();
            return true;
        } else {
            return false;
        }
    }

    public boolean stopSprinting() {
        if (this.remainingSprintTicks > 0L) {
            this.finishTickSprint();
            return true;
        } else {
            return false;
        }
    }

    public boolean requestGameToSprint(int p_308951_) {
        boolean flag = this.remainingSprintTicks > 0L;
        this.sprintTimeSpend = 0L;
        this.scheduledCurrentSprintTicks = (long)p_308951_;
        this.remainingSprintTicks = (long)p_308951_;
        this.previousIsFrozen = this.isFrozen();
        this.setFrozen(false);
        return flag;
    }

    private void finishTickSprint() {
        long i = this.scheduledCurrentSprintTicks - this.remainingSprintTicks;
        double d0 = Math.max(1.0, (double)this.sprintTimeSpend) / (double)TimeUtil.NANOSECONDS_PER_MILLISECOND;
        int j = (int)((double)(TimeUtil.MILLISECONDS_PER_SECOND * i) / d0);
        String s = String.format("%.2f", i == 0L ? (double)this.millisecondsPerTick() : d0 / (double)i);
        this.scheduledCurrentSprintTicks = 0L;
        this.sprintTimeSpend = 0L;
        this.server.createCommandSourceStack().sendSuccess(() -> Component.translatable("commands.tick.sprint.report", j, s), true);
        this.remainingSprintTicks = 0L;
        this.setFrozen(this.previousIsFrozen);
        this.server.onTickRateChanged();
    }

    public boolean checkShouldSprintThisTick() {
        if (!this.runGameElements) {
            return false;
        } else if (this.remainingSprintTicks > 0L) {
            this.sprintTickStartTime = System.nanoTime();
            this.remainingSprintTicks--;
            return true;
        } else {
            this.finishTickSprint();
            return false;
        }
    }

    public void endTickWork() {
        this.sprintTimeSpend = this.sprintTimeSpend + (System.nanoTime() - this.sprintTickStartTime);
    }

    @Override
    public void setTickRate(float p_309003_) {
        super.setTickRate(p_309003_);
        this.server.onTickRateChanged();
        this.updateStateToClients();
    }

    public void updateJoiningPlayer(ServerPlayer p_309205_) {
        p_309205_.connection.send(ClientboundTickingStatePacket.from(this));
        p_309205_.connection.send(ClientboundTickingStepPacket.from(this));
    }
}
