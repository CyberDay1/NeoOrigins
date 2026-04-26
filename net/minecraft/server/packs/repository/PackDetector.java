package net.minecraft.server.packs.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;

public abstract class PackDetector<T> {
    private final DirectoryValidator validator;

    protected PackDetector(DirectoryValidator p_295530_) {
        this.validator = p_295530_;
    }

    @Nullable
    public T detectPackResources(Path p_294493_, List<ForbiddenSymlinkInfo> p_295548_) throws IOException {
        Path path = p_294493_;

        BasicFileAttributes basicfileattributes;
        try {
            basicfileattributes = Files.readAttributes(p_294493_, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException nosuchfileexception) {
            return null;
        }

        if (basicfileattributes.isSymbolicLink()) {
            this.validator.validateSymlink(p_294493_, p_295548_);
            if (!p_295548_.isEmpty()) {
                return null;
            }

            path = Files.readSymbolicLink(p_294493_);
            basicfileattributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }

        if (basicfileattributes.isDirectory()) {
            this.validator.validateKnownDirectory(path, p_295548_);
            if (!p_295548_.isEmpty()) {
                return null;
            } else {
                return !Files.isRegularFile(path.resolve("pack.mcmeta")) ? null : this.createDirectoryPack(path);
            }
        } else {
            return basicfileattributes.isRegularFile() && path.getFileName().toString().endsWith(".zip") ? this.createZipPack(path) : null;
        }
    }

    @Nullable
    protected abstract T createZipPack(Path p_296057_) throws IOException;

    @Nullable
    protected abstract T createDirectoryPack(Path p_296184_) throws IOException;
}
