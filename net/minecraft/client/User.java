package net.minecraft.client;

import com.mojang.util.UndashedUuid;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class User {
    private final String name;
    private final UUID uuid;
    private final String accessToken;
    private final Optional<String> xuid;
    private final Optional<String> clientId;
    private final User.Type type;

    public User(String p_193799_, UUID p_296409_, String p_193800_, Optional<String> p_193802_, Optional<String> p_193803_, User.Type p_193804_) {
        if (p_193799_ == null || p_193799_.isEmpty()) {
            p_193799_ = "MissingName";
            p_296409_ = UUID.fromString("00000000-0000-0000-0000-000000000000");
            p_193800_ = "NotValid";
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass().getName());
            logger.warn("=========================================================");
            logger.warn("WARNING!! the username was not set for this session, typically");
            logger.warn("this means you installed Forge incorrectly. We have set your");
            logger.warn("name to \"MissingName\" and your session to nothing. Please");
            logger.warn("check your installation and post a console log from the launcher");
            logger.warn("when asking for help!");
            logger.warn("=========================================================");
        }
        this.name = p_193799_;
        this.uuid = p_296409_;
        this.accessToken = p_193800_;
        this.xuid = p_193802_;
        this.clientId = p_193803_;
        this.type = p_193804_;
    }

    public String getSessionId() {
        return "token:" + this.accessToken + ":" + UndashedUuid.toString(this.uuid);
    }

    public UUID getProfileId() {
        return this.uuid;
    }

    public String getName() {
        return this.name;
    }

    public String getAccessToken() {
        return this.accessToken;
    }

    public Optional<String> getClientId() {
        return this.clientId;
    }

    public Optional<String> getXuid() {
        return this.xuid;
    }

    public User.Type getType() {
        return this.type;
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Type {
        LEGACY("legacy"),
        MOJANG("mojang"),
        MSA("msa");

        private static final Map<String, User.Type> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(p_92560_ -> p_92560_.name, Function.identity()));
        private final String name;

        private Type(String p_92558_) {
            this.name = p_92558_;
        }

        @Nullable
        public static User.Type byName(String p_92562_) {
            return BY_NAME.get(p_92562_.toLowerCase(Locale.ROOT));
        }

        public String getName() {
            return this.name;
        }
    }
}
