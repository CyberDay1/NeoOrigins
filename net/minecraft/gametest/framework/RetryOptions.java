package net.minecraft.gametest.framework;

public record RetryOptions(int numberOfTries, boolean haltOnFailure) {
    private static final RetryOptions NO_RETRIES = new RetryOptions(1, true);

    public static RetryOptions noRetries() {
        return NO_RETRIES;
    }

    public boolean unlimitedTries() {
        return this.numberOfTries < 1;
    }

    public boolean hasTriesLeft(int p_320828_, int p_320137_) {
        boolean flag = p_320828_ != p_320137_;
        boolean flag1 = this.unlimitedTries() || p_320828_ < this.numberOfTries;
        return flag1 && (!flag || !this.haltOnFailure);
    }

    public boolean hasRetries() {
        return this.numberOfTries != 1;
    }
}
