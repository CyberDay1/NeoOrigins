package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.screen.creator.model.OriginTemplate;
import com.cyberday1.neoorigins.service.OriginTemplates;

import java.util.List;

/**
 * Client-side cache of the template list the server pushes on creator open
 * (via {@code OriginTemplatesPayload}). Read by the "Load template" picker;
 * cleared when the player disconnects so the next session starts blank.
 *
 * <p>Templates are large but read-mostly, so we store them as an immutable
 * list and let the picker filter by layer at render time.
 */
public final class ClientTemplateCache {

    private static List<OriginTemplate> templates = List.of();

    private ClientTemplateCache() {}

    /** Parse the server-sent JSON and replace the cache. Called on the client
     *  thread off {@code OriginTemplatesPayload}. */
    public static void setFromJson(String json) {
        templates = List.copyOf(OriginTemplates.fromJson(json));
    }

    public static List<OriginTemplate> all() { return templates; }

    public static void clear() { templates = List.of(); }
}
