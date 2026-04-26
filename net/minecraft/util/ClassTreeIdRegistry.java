package net.minecraft.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.Util;

public class ClassTreeIdRegistry {
    public static final int NO_ID_VALUE = -1;
    private final Object2IntMap<Class<?>> classToLastIdCache = Util.make(new Object2IntOpenHashMap<>(), p_326384_ -> p_326384_.defaultReturnValue(-1));

    public int getLastIdFor(Class<?> p_326222_) {
        int i = this.classToLastIdCache.getInt(p_326222_);
        if (i != -1) {
            return i;
        } else {
            Class<?> oclass = p_326222_;

            while ((oclass = oclass.getSuperclass()) != Object.class) {
                int j = this.classToLastIdCache.getInt(oclass);
                if (j != -1) {
                    return j;
                }
            }

            return -1;
        }
    }

    public int getCount(Class<?> p_325981_) {
        return this.getLastIdFor(p_325981_) + 1;
    }

    public int define(Class<?> p_326354_) {
        int i = this.getLastIdFor(p_326354_);
        int j = i == -1 ? 0 : i + 1;
        this.classToLastIdCache.put(p_326354_, j);
        return j;
    }
}
