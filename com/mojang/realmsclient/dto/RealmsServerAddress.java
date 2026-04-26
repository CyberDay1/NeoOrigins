package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RealmsServerAddress extends ValueObject {
    private static final Logger LOGGER = LogUtils.getLogger();
    public String address;
    public String resourcePackUrl;
    public String resourcePackHash;

    public static RealmsServerAddress parse(String p_87572_) {
        JsonParser jsonparser = new JsonParser();
        RealmsServerAddress realmsserveraddress = new RealmsServerAddress();

        try {
            JsonObject jsonobject = jsonparser.parse(p_87572_).getAsJsonObject();
            realmsserveraddress.address = JsonUtils.getStringOr("address", jsonobject, null);
            realmsserveraddress.resourcePackUrl = JsonUtils.getStringOr("resourcePackUrl", jsonobject, null);
            realmsserveraddress.resourcePackHash = JsonUtils.getStringOr("resourcePackHash", jsonobject, null);
        } catch (Exception exception) {
            LOGGER.error("Could not parse RealmsServerAddress: {}", exception.getMessage());
        }

        return realmsserveraddress;
    }
}
