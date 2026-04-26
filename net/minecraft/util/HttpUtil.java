package net.minecraft.util;

import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class HttpUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    private HttpUtil() {
    }

    public static Path downloadFile(
        Path p_314509_,
        URL p_314583_,
        Map<String, String> p_314414_,
        HashFunction p_314462_,
        @Nullable HashCode p_314495_,
        int p_314514_,
        Proxy p_314631_,
        HttpUtil.DownloadProgressListener p_314610_
    ) {
        HttpURLConnection httpurlconnection = null;
        InputStream inputstream = null;
        p_314610_.requestStart();
        Path path;
        if (p_314495_ != null) {
            path = cachedFilePath(p_314509_, p_314495_);

            try {
                if (checkExistingFile(path, p_314462_, p_314495_)) {
                    LOGGER.info("Returning cached file since actual hash matches requested");
                    p_314610_.requestFinished(true);
                    updateModificationTime(path);
                    return path;
                }
            } catch (IOException ioexception1) {
                LOGGER.warn("Failed to check cached file {}", path, ioexception1);
            }

            try {
                LOGGER.warn("Existing file {} not found or had mismatched hash", path);
                Files.deleteIfExists(path);
            } catch (IOException ioexception) {
                p_314610_.requestFinished(false);
                throw new UncheckedIOException("Failed to remove existing file " + path, ioexception);
            }
        } else {
            path = null;
        }

        Path $$18;
        try {
            httpurlconnection = (HttpURLConnection)p_314583_.openConnection(p_314631_);
            httpurlconnection.setInstanceFollowRedirects(true);
            p_314414_.forEach(httpurlconnection::setRequestProperty);
            inputstream = httpurlconnection.getInputStream();
            long i = httpurlconnection.getContentLengthLong();
            OptionalLong optionallong = i != -1L ? OptionalLong.of(i) : OptionalLong.empty();
            FileUtil.createDirectoriesSafe(p_314509_);
            p_314610_.downloadStart(optionallong);
            if (optionallong.isPresent() && optionallong.getAsLong() > (long)p_314514_) {
                throw new IOException("Filesize is bigger than maximum allowed (file is " + optionallong + ", limit is " + p_314514_ + ")");
            }

            if (path == null) {
                Path path3 = Files.createTempFile(p_314509_, "download", ".tmp");

                try {
                    HashCode hashcode1 = downloadAndHash(p_314462_, p_314514_, p_314610_, inputstream, path3);
                    Path path2 = cachedFilePath(p_314509_, hashcode1);
                    if (!checkExistingFile(path2, p_314462_, hashcode1)) {
                        Files.move(path3, path2, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        updateModificationTime(path2);
                    }

                    p_314610_.requestFinished(true);
                    return path2;
                } finally {
                    Files.deleteIfExists(path3);
                }
            }

            HashCode hashcode = downloadAndHash(p_314462_, p_314514_, p_314610_, inputstream, path);
            if (!hashcode.equals(p_314495_)) {
                throw new IOException("Hash of downloaded file (" + hashcode + ") did not match requested (" + p_314495_ + ")");
            }

            p_314610_.requestFinished(true);
            $$18 = path;
        } catch (Throwable throwable) {
            if (httpurlconnection != null) {
                InputStream inputstream1 = httpurlconnection.getErrorStream();
                if (inputstream1 != null) {
                    try {
                        LOGGER.error("HTTP response error: {}", IOUtils.toString(inputstream1, StandardCharsets.UTF_8));
                    } catch (Exception exception) {
                        LOGGER.error("Failed to read response from server");
                    }
                }
            }

            p_314610_.requestFinished(false);
            throw new IllegalStateException("Failed to download file " + p_314583_, throwable);
        } finally {
            IOUtils.closeQuietly(inputstream);
        }

        return $$18;
    }

    private static void updateModificationTime(Path p_314996_) {
        try {
            Files.setLastModifiedTime(p_314996_, FileTime.from(Instant.now()));
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to update modification time of {}", p_314996_, ioexception);
        }
    }

    private static HashCode hashFile(Path p_314478_, HashFunction p_314630_) throws IOException {
        Hasher hasher = p_314630_.newHasher();

        try (
            OutputStream outputstream = Funnels.asOutputStream(hasher);
            InputStream inputstream = Files.newInputStream(p_314478_);
        ) {
            inputstream.transferTo(outputstream);
        }

        return hasher.hash();
    }

    private static boolean checkExistingFile(Path p_314420_, HashFunction p_314503_, HashCode p_314584_) throws IOException {
        if (Files.exists(p_314420_)) {
            HashCode hashcode = hashFile(p_314420_, p_314503_);
            if (hashcode.equals(p_314584_)) {
                return true;
            }

            LOGGER.warn("Mismatched hash of file {}, expected {} but found {}", p_314420_, p_314584_, hashcode);
        }

        return false;
    }

    private static Path cachedFilePath(Path p_314479_, HashCode p_314627_) {
        return p_314479_.resolve(p_314627_.toString());
    }

    private static HashCode downloadAndHash(
        HashFunction p_314429_, int p_314497_, HttpUtil.DownloadProgressListener p_314419_, InputStream p_314557_, Path p_314618_
    ) throws IOException {
        HashCode hashcode;
        try (OutputStream outputstream = Files.newOutputStream(p_314618_, StandardOpenOption.CREATE)) {
            Hasher hasher = p_314429_.newHasher();
            byte[] abyte = new byte[8196];
            long j = 0L;

            int i;
            while ((i = p_314557_.read(abyte)) >= 0) {
                j += (long)i;
                p_314419_.downloadedBytes(j);
                if (j > (long)p_314497_) {
                    throw new IOException("Filesize was bigger than maximum allowed (got >= " + j + ", limit was " + p_314497_ + ")");
                }

                if (Thread.interrupted()) {
                    LOGGER.error("INTERRUPTED");
                    throw new IOException("Download interrupted");
                }

                outputstream.write(abyte, 0, i);
                hasher.putBytes(abyte, 0, i);
            }

            hashcode = hasher.hash();
        }

        return hashcode;
    }

    public static int getAvailablePort() {
        try {
            int i;
            try (ServerSocket serversocket = new ServerSocket(0)) {
                i = serversocket.getLocalPort();
            }

            return i;
        } catch (IOException ioexception) {
            return 25564;
        }
    }

    public static boolean isPortAvailable(int p_259872_) {
        if (p_259872_ >= 0 && p_259872_ <= 65535) {
            try {
                boolean flag;
                try (ServerSocket serversocket = new ServerSocket(p_259872_)) {
                    flag = serversocket.getLocalPort() == p_259872_;
                }

                return flag;
            } catch (IOException ioexception) {
                return false;
            }
        } else {
            return false;
        }
    }

    public interface DownloadProgressListener {
        void requestStart();

        void downloadStart(OptionalLong p_314436_);

        void downloadedBytes(long p_314605_);

        void requestFinished(boolean p_314958_);
    }
}
