package com.mojang.realmsclient.exception;

import com.mojang.realmsclient.client.RealmsError;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RealmsServiceException extends Exception {
    public final RealmsError realmsError;

    public RealmsServiceException(RealmsError p_87785_) {
        this.realmsError = p_87785_;
    }

    @Override
    public String getMessage() {
        return this.realmsError.logMessage();
    }
}
