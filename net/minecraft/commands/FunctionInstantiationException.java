package net.minecraft.commands;

import net.minecraft.network.chat.Component;

public class FunctionInstantiationException extends Exception {
    private final Component messageComponent;

    public FunctionInstantiationException(Component p_294836_) {
        super(p_294836_.getString());
        this.messageComponent = p_294836_;
    }

    public Component messageComponent() {
        return this.messageComponent;
    }
}
