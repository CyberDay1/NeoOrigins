package net.minecraft.util;

public class FastColor {
    public static int as8BitChannel(float p_341399_) {
        return Mth.floor(p_341399_ * 255.0F);
    }

    public static class ABGR32 {
        public static int alpha(int p_267257_) {
            return p_267257_ >>> 24;
        }

        public static int red(int p_267160_) {
            return p_267160_ & 0xFF;
        }

        public static int green(int p_266784_) {
            return p_266784_ >> 8 & 0xFF;
        }

        public static int blue(int p_267087_) {
            return p_267087_ >> 16 & 0xFF;
        }

        public static int transparent(int p_267248_) {
            return p_267248_ & 16777215;
        }

        public static int opaque(int p_268288_) {
            return p_268288_ | 0xFF000000;
        }

        public static int color(int p_267196_, int p_266895_, int p_266779_, int p_267206_) {
            return p_267196_ << 24 | p_266895_ << 16 | p_266779_ << 8 | p_267206_;
        }

        public static int color(int p_267230_, int p_266708_) {
            return p_267230_ << 24 | p_266708_ & 16777215;
        }

        public static int fromArgb32(int p_350301_) {
            return p_350301_ & -16711936 | (p_350301_ & 0xFF0000) >> 16 | (p_350301_ & 0xFF) << 16;
        }
    }

    public static class ARGB32 {
        public static int alpha(int p_13656_) {
            return p_13656_ >>> 24;
        }

        public static int red(int p_13666_) {
            return p_13666_ >> 16 & 0xFF;
        }

        public static int green(int p_13668_) {
            return p_13668_ >> 8 & 0xFF;
        }

        public static int blue(int p_13670_) {
            return p_13670_ & 0xFF;
        }

        public static int color(int p_13661_, int p_13662_, int p_13663_, int p_13664_) {
            return p_13661_ << 24 | p_13662_ << 16 | p_13663_ << 8 | p_13664_;
        }

        public static int color(int p_331799_, int p_331285_, int p_331246_) {
            return color(255, p_331799_, p_331285_, p_331246_);
        }

        public static int multiply(int p_13658_, int p_13659_) {
            return color(
                alpha(p_13658_) * alpha(p_13659_) / 255,
                red(p_13658_) * red(p_13659_) / 255,
                green(p_13658_) * green(p_13659_) / 255,
                blue(p_13658_) * blue(p_13659_) / 255
            );
        }

        public static int lerp(float p_270972_, int p_270081_, int p_270150_) {
            int i = Mth.lerpInt(p_270972_, alpha(p_270081_), alpha(p_270150_));
            int j = Mth.lerpInt(p_270972_, red(p_270081_), red(p_270150_));
            int k = Mth.lerpInt(p_270972_, green(p_270081_), green(p_270150_));
            int l = Mth.lerpInt(p_270972_, blue(p_270081_), blue(p_270150_));
            return color(i, j, k, l);
        }

        public static int opaque(int p_330851_) {
            return p_330851_ | 0xFF000000;
        }

        public static int color(int p_333835_, int p_334036_) {
            return p_333835_ << 24 | p_334036_ & 16777215;
        }

        public static int colorFromFloat(float p_340940_, float p_341107_, float p_341323_, float p_341138_) {
            return color(
                FastColor.as8BitChannel(p_340940_), FastColor.as8BitChannel(p_341107_), FastColor.as8BitChannel(p_341323_), FastColor.as8BitChannel(p_341138_)
            );
        }

        public static int average(int p_350662_, int p_350786_) {
            return color(
                (alpha(p_350662_) + alpha(p_350786_)) / 2,
                (red(p_350662_) + red(p_350786_)) / 2,
                (green(p_350662_) + green(p_350786_)) / 2,
                (blue(p_350662_) + blue(p_350786_)) / 2
            );
        }
    }
}
