package net.minecraft.world.level;

public class FoliageColor {
    private static int[] pixels = new int[65536];

    public static void init(int[] p_46111_) {
        pixels = p_46111_;
    }

    public static int get(double p_46108_, double p_46109_) {
        p_46109_ *= p_46108_;
        int i = (int)((1.0 - p_46108_) * 255.0);
        int j = (int)((1.0 - p_46109_) * 255.0);
        int k = j << 8 | i;
        return k >= pixels.length ? getDefaultColor() : pixels[k];
    }

    public static int getEvergreenColor() {
        return -10380959;
    }

    public static int getBirchColor() {
        return -8345771;
    }

    public static int getDefaultColor() {
        return -12012264;
    }

    public static int getMangroveColor() {
        return -7158200;
    }
}
