package net.minecraft.network;

public enum ConnectionProtocol {
    HANDSHAKING("handshake"),
    PLAY("play"),
    STATUS("status"),
    LOGIN("login"),
    CONFIGURATION("configuration");

    private final String id;

    private ConnectionProtocol(String p_295335_) {
        this.id = p_295335_;
    }

    public String id() {
        return this.id;
    }

    public boolean isPlay() {
        return this == PLAY;
    }
    public boolean isConfiguration() {
        return this == CONFIGURATION;
    }
}
