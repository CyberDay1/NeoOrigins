package com.cyberday1.neoorigins.api.power;

import net.minecraft.network.chat.Component;

/**
 * How a power registered in code shows up in the origin screens, the part a JSON power gets from
 * its {@code name}, {@code description} and {@code hidden} fields.
 */
public record PowerDisplay(Component name, Component description, boolean hidden) {

    /** A visible power with this name and description. */
    public static PowerDisplay of(Component name, Component description) {
        return new PowerDisplay(name, description, false);
    }
}
