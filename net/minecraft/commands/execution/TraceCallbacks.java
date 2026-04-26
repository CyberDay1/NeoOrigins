package net.minecraft.commands.execution;

import net.minecraft.resources.ResourceLocation;

public interface TraceCallbacks extends AutoCloseable {
    void onCommand(int p_306090_, String p_306150_);

    void onReturn(int p_305827_, String p_306254_, int p_306051_);

    void onError(String p_306008_);

    void onCall(int p_306072_, ResourceLocation p_306313_, int p_306250_);

    @Override
    void close();
}
