package net.minecraft.client.resources;

import com.google.common.collect.Lists;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SplashManager extends SimplePreparableReloadListener<List<String>> {
    public static final ResourceLocation SPLASHES_LOCATION = ResourceLocation.withDefaultNamespace("texts/splashes.txt");
    private static final RandomSource RANDOM = RandomSource.create();
    private final List<String> splashes = Lists.newArrayList();
    private final User user;

    public SplashManager(User p_118866_) {
        this.user = p_118866_;
    }

    protected List<String> prepare(ResourceManager p_118869_, ProfilerFiller p_118870_) {
        try {
            var splashes = Minecraft.getInstance().getResourceManager().getResourceOrThrow(SPLASHES_LOCATION);
            if (splashes.sourcePackId().equals("vanilla"))
                return net.neoforged.neoforge.client.resources.NeoForgeSplashHooks.loadSplashes(p_118869_);

            List list;
            try (BufferedReader bufferedreader = splashes.openAsReader()) {
                list = bufferedreader.lines().map(String::trim).filter(p_118876_ -> p_118876_.hashCode() != 125780783).collect(Collectors.toList());
            }

            return list;
        } catch (IOException ioexception) {
            return Collections.emptyList();
        }
    }

    protected void apply(List<String> p_118878_, ResourceManager p_118879_, ProfilerFiller p_118880_) {
        this.splashes.clear();
        this.splashes.addAll(p_118878_);
    }

    @Nullable
    public SplashRenderer getSplash() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        if (calendar.get(2) + 1 == 12 && calendar.get(5) == 24) {
            return SplashRenderer.CHRISTMAS;
        } else if (calendar.get(2) + 1 == 1 && calendar.get(5) == 1) {
            return SplashRenderer.NEW_YEAR;
        } else if (calendar.get(2) + 1 == 10 && calendar.get(5) == 31) {
            return SplashRenderer.HALLOWEEN;
        } else if (this.splashes.isEmpty()) {
            return null;
        } else {
            return this.user != null && RANDOM.nextInt(this.splashes.size()) == 42
                ? new SplashRenderer(this.user.getName().toUpperCase(Locale.ROOT) + " IS YOU")
                : new SplashRenderer(this.splashes.get(RANDOM.nextInt(this.splashes.size())));
        }
    }
}
