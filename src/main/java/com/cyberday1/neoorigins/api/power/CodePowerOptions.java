package com.cyberday1.neoorigins.api.power;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Loader-side setup for a power registered in code: the parts a JSON power gets from its
 * {@code power_condition}, {@code power_condition_mode}, upkeep and {@code toggle_messages} fields.
 * Build with {@link #builder()}; {@link #NONE} sets nothing.
 */
public final class CodePowerOptions {

    public static final CodePowerOptions NONE = builder().build();

    private final Predicate<ServerPlayer> condition;
    private final PowerHolder.ConditionMode conditionMode;
    private final boolean upkeepSet;
    private final int hungerCost;
    private final ResourceLocation resourceCost;
    private final int resourceCostAmount;
    private final int upkeepInterval;
    private final Predicate<ServerPlayer> upkeepCondition;
    private final Boolean toggleMessages;

    private CodePowerOptions(Builder b) {
        this.condition = b.condition;
        this.conditionMode = b.conditionMode;
        this.upkeepSet = b.upkeepSet;
        this.hungerCost = b.hungerCost;
        this.resourceCost = b.resourceCost;
        this.resourceCostAmount = b.resourceCostAmount;
        this.upkeepInterval = b.upkeepInterval;
        this.upkeepCondition = b.upkeepCondition;
        this.toggleMessages = b.toggleMessages;
    }

    public static Builder builder() { return new Builder(); }

    /** The top-level gate, if one was set. */
    public Optional<Predicate<ServerPlayer>> condition() { return Optional.ofNullable(condition); }
    /** How {@link #condition()} gates the power: {@code ALLOW} unless set otherwise. */
    public PowerHolder.ConditionMode conditionMode()      { return conditionMode; }
    /** True when any upkeep field was set. */
    public boolean hasUpkeep()                            { return upkeepSet; }
    public int hungerCost()                               { return hungerCost; }
    public Optional<ResourceLocation> resourceCost()      { return Optional.ofNullable(resourceCost); }
    public int resourceCostAmount()                       { return resourceCostAmount; }
    public int upkeepInterval()                           { return upkeepInterval; }
    public Optional<Predicate<ServerPlayer>> upkeepCondition() { return Optional.ofNullable(upkeepCondition); }
    /** Whether a toggle's on/off chat line is sent, if set. */
    public Optional<Boolean> toggleMessages()             { return Optional.ofNullable(toggleMessages); }

    public static final class Builder {
        private Predicate<ServerPlayer> condition;
        private PowerHolder.ConditionMode conditionMode = PowerHolder.ConditionMode.ALLOW;
        private boolean upkeepSet;
        private int hungerCost;
        private ResourceLocation resourceCost;
        private int resourceCostAmount;
        private int upkeepInterval = 20;
        private Predicate<ServerPlayer> upkeepCondition;
        private Boolean toggleMessages;

        private Builder() {}

        /** The power only works while {@code condition} is true (mode {@code ALLOW}). */
        public Builder condition(Predicate<ServerPlayer> condition) {
            return condition(condition, PowerHolder.ConditionMode.ALLOW);
        }

        /** {@code ALLOW}: works only while true. {@code DENY}: blocked while true. */
        public Builder condition(Predicate<ServerPlayer> condition, PowerHolder.ConditionMode mode) {
            this.condition = Objects.requireNonNull(condition, "condition");
            this.conditionMode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        /** Toggle upkeep: hunger points charged every {@link #upkeepInterval} ticks while on. */
        public Builder hungerCost(int points) {
            this.hungerCost = points;
            this.upkeepSet = true;
            return this;
        }

        /** Toggle upkeep: {@code amount} taken from the resource power {@code resource} every interval while on. */
        public Builder resourceCost(ResourceLocation resource, int amount) {
            this.resourceCost = Objects.requireNonNull(resource, "resource");
            this.resourceCostAmount = amount;
            this.upkeepSet = true;
            return this;
        }

        /** Ticks between upkeep charges. Default 20. */
        public Builder upkeepInterval(int ticks) {
            this.upkeepInterval = ticks;
            this.upkeepSet = true;
            return this;
        }

        /** Upkeep is only charged on ticks where this is true. */
        public Builder upkeepCondition(Predicate<ServerPlayer> condition) {
            this.upkeepCondition = Objects.requireNonNull(condition, "condition");
            this.upkeepSet = true;
            return this;
        }

        /** False silences the toggle's on/off chat line. Default true. */
        public Builder toggleMessages(boolean messages) {
            this.toggleMessages = messages;
            return this;
        }

        public CodePowerOptions build() { return new CodePowerOptions(this); }
    }
}
