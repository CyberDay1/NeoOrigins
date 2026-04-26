package net.minecraft.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raid;

class BadOmenMobEffect extends MobEffect {
    protected BadOmenMobEffect(MobEffectCategory p_296418_, int p_296408_) {
        super(p_296418_, p_296408_);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int p_295828_, int p_295171_) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity p_296327_, int p_294357_) {
        if (p_296327_ instanceof ServerPlayer serverplayer && !serverplayer.isSpectator()) {
            ServerLevel serverlevel = serverplayer.serverLevel();
            if (serverlevel.getDifficulty() != Difficulty.PEACEFUL && serverlevel.isVillage(serverplayer.blockPosition())) {
                Raid raid = serverlevel.getRaidAt(serverplayer.blockPosition());
                if (raid == null || raid.getRaidOmenLevel() < raid.getMaxRaidOmenLevel()) {
                    serverplayer.addEffect(new MobEffectInstance(MobEffects.RAID_OMEN, 600, p_294357_));
                    serverplayer.setRaidOmenPosition(serverplayer.blockPosition());
                    return false;
                }
            }
        }

        return true;
    }
}
