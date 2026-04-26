package net.minecraft.nbt;

import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.Util;
import net.minecraft.util.DelegateDataOutput;
import net.minecraft.util.FastBufferedInputStream;

public class NbtIo {
    private static final OpenOption[] SYNC_OUTPUT_OPTIONS = new OpenOption[]{
        StandardOpenOption.SYNC, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
    };

    public static CompoundTag readCompressed(Path p_309661_, NbtAccounter p_307351_) throws IOException {
        CompoundTag compoundtag;
        try (
            InputStream inputstream = Files.newInputStream(p_309661_);
            InputStream inputstream1 = new FastBufferedInputStream(inputstream);
        ) {
            compoundtag = readCompressed(inputstream1, p_307351_);
        }

        return compoundtag;
    }

    private static DataInputStream createDecompressorStream(InputStream p_202494_) throws IOException {
        return new DataInputStream(new FastBufferedInputStream(new GZIPInputStream(p_202494_)));
    }

    private static DataOutputStream createCompressorStream(OutputStream p_309656_) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(p_309656_)));
    }

    public static CompoundTag readCompressed(InputStream p_307406_, NbtAccounter p_307479_) throws IOException {
        CompoundTag compoundtag;
        try (DataInputStream datainputstream = createDecompressorStream(p_307406_)) {
            compoundtag = read(datainputstream, p_307479_);
        }

        return compoundtag;
    }

    public static void parseCompressed(Path p_309564_, StreamTagVisitor p_202489_, NbtAccounter p_302378_) throws IOException {
        try (
            InputStream inputstream = Files.newInputStream(p_309564_);
            InputStream inputstream1 = new FastBufferedInputStream(inputstream);
        ) {
            parseCompressed(inputstream1, p_202489_, p_302378_);
        }
    }

    public static void parseCompressed(InputStream p_202491_, StreamTagVisitor p_202492_, NbtAccounter p_302334_) throws IOException {
        try (DataInputStream datainputstream = createDecompressorStream(p_202491_)) {
            parse(datainputstream, p_202492_, p_302334_);
        }
    }

    public static void writeCompressed(CompoundTag p_128945_, Path p_309705_) throws IOException {
        try (
            OutputStream outputstream = Files.newOutputStream(p_309705_, SYNC_OUTPUT_OPTIONS);
            OutputStream outputstream1 = new BufferedOutputStream(outputstream);
        ) {
            writeCompressed(p_128945_, outputstream1);
        }
    }

    public static void writeCompressed(CompoundTag p_128948_, OutputStream p_128949_) throws IOException {
        try (DataOutputStream dataoutputstream = createCompressorStream(p_128949_)) {
            write(p_128948_, dataoutputstream);
        }
    }

    public static void write(CompoundTag p_128956_, Path p_309549_) throws IOException {
        try (
            OutputStream outputstream = Files.newOutputStream(p_309549_, SYNC_OUTPUT_OPTIONS);
            OutputStream outputstream1 = new BufferedOutputStream(outputstream);
            DataOutputStream dataoutputstream = new DataOutputStream(outputstream1);
        ) {
            write(p_128956_, dataoutputstream);
        }
    }

    @Nullable
    public static CompoundTag read(Path p_309563_) throws IOException {
        if (!Files.exists(p_309563_)) {
            return null;
        } else {
            CompoundTag compoundtag;
            try (
                InputStream inputstream = Files.newInputStream(p_309563_);
                DataInputStream datainputstream = new DataInputStream(inputstream);
            ) {
                compoundtag = read(datainputstream, NbtAccounter.unlimitedHeap());
            }

            return compoundtag;
        }
    }

    public static CompoundTag read(DataInput p_128929_) throws IOException {
        return read(p_128929_, NbtAccounter.unlimitedHeap());
    }

    public static CompoundTag read(DataInput p_128935_, NbtAccounter p_128936_) throws IOException {
        Tag tag = readUnnamedTag(p_128935_, p_128936_);
        if (tag instanceof CompoundTag) {
            return (CompoundTag)tag;
        } else {
            throw new IOException("Root tag must be a named compound tag");
        }
    }

    public static void write(CompoundTag p_128942_, DataOutput p_128943_) throws IOException {
        writeUnnamedTagWithFallback(p_128942_, p_128943_);
    }

    public static void parse(DataInput p_197510_, StreamTagVisitor p_197511_, NbtAccounter p_302331_) throws IOException {
        TagType<?> tagtype = TagTypes.getType(p_197510_.readByte());
        if (tagtype == EndTag.TYPE) {
            if (p_197511_.visitRootEntry(EndTag.TYPE) == StreamTagVisitor.ValueResult.CONTINUE) {
                p_197511_.visitEnd();
            }
        } else {
            switch (p_197511_.visitRootEntry(tagtype)) {
                case HALT:
                default:
                    break;
                case BREAK:
                    StringTag.skipString(p_197510_);
                    tagtype.skip(p_197510_, p_302331_);
                    break;
                case CONTINUE:
                    StringTag.skipString(p_197510_);
                    tagtype.parse(p_197510_, p_197511_, p_302331_);
            }
        }
    }

    public static Tag readAnyTag(DataInput p_294481_, NbtAccounter p_294287_) throws IOException {
        byte b0 = p_294481_.readByte();
        return (Tag)(b0 == 0 ? EndTag.INSTANCE : readTagSafe(p_294481_, p_294287_, b0));
    }

    public static void writeAnyTag(Tag p_294166_, DataOutput p_295552_) throws IOException {
        p_295552_.writeByte(p_294166_.getId());
        if (p_294166_.getId() != 0) {
            p_294166_.write(p_295552_);
        }
    }

    public static void writeUnnamedTag(Tag p_128951_, DataOutput p_128952_) throws IOException {
        p_128952_.writeByte(p_128951_.getId());
        if (p_128951_.getId() != 0) {
            p_128952_.writeUTF("");
            p_128951_.write(p_128952_);
        }
    }

    public static void writeUnnamedTagWithFallback(Tag p_311861_, DataOutput p_312306_) throws IOException {
        writeUnnamedTag(p_311861_, new NbtIo.StringFallbackDataOutput(p_312306_));
    }

    private static Tag readUnnamedTag(DataInput p_128931_, NbtAccounter p_128933_) throws IOException {
        byte b0 = p_128931_.readByte();
        p_128933_.accountBytes(1); // Forge: Count everything!
        if (b0 == 0) {
            return EndTag.INSTANCE;
        } else {
            p_128933_.readUTF(p_128931_.readUTF()); //Forge: Count this string.
            p_128933_.accountBytes(4); //Forge: 4 extra bytes for the object allocation.
            return readTagSafe(p_128931_, p_128933_, b0);
        }
    }

    private static Tag readTagSafe(DataInput p_295228_, NbtAccounter p_294806_, byte p_294992_) {
        try {
            return TagTypes.getType(p_294992_).load(p_295228_, p_294806_);
        } catch (IOException ioexception) {
            CrashReport crashreport = CrashReport.forThrowable(ioexception, "Loading NBT data");
            CrashReportCategory crashreportcategory = crashreport.addCategory("NBT Tag");
            crashreportcategory.setDetail("Tag type", p_294992_);
            throw new ReportedNbtException(crashreport);
        }
    }

    public static class StringFallbackDataOutput extends DelegateDataOutput {
        public StringFallbackDataOutput(DataOutput p_312308_) {
            super(p_312308_);
        }

        @Override
        public void writeUTF(String p_312136_) throws IOException {
            try {
                super.writeUTF(p_312136_);
            } catch (UTFDataFormatException utfdataformatexception) {
                Util.logAndPauseIfInIde("Failed to write NBT String", utfdataformatexception);
                super.writeUTF("");
            }
        }
    }
}
