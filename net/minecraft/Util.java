package net.minecraft;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.MoreExecutors;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceImmutableList;
import it.unimi.dsi.fastutil.objects.ReferenceList;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SingleKeyCache;
import net.minecraft.util.TimeSource;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;

public class Util {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_MAX_THREADS = 255;
    private static final int DEFAULT_SAFE_FILE_OPERATION_RETRIES = 10;
    private static final String MAX_THREADS_SYSTEM_PROPERTY = "max.bg.threads";
    private static final ExecutorService BACKGROUND_EXECUTOR = makeExecutor("Main");
    private static final ExecutorService IO_POOL = makeIoExecutor("IO-Worker-", false);
    private static final ExecutorService DOWNLOAD_POOL = makeIoExecutor("Download-", true);
    private static final DateTimeFormatter FILENAME_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);
    public static final int LINEAR_LOOKUP_THRESHOLD = 8;
    private static final Set<String> ALLOWED_UNTRUSTED_LINK_PROTOCOLS = Set.of("http", "https");
    public static final long NANOS_PER_MILLI = 1000000L;
    public static TimeSource.NanoTimeSource timeSource = System::nanoTime;
    public static final Ticker TICKER = new Ticker() {
        @Override
        public long read() {
            return Util.timeSource.getAsLong();
        }
    };
    public static final UUID NIL_UUID = new UUID(0L, 0L);
    public static final FileSystemProvider ZIP_FILE_SYSTEM_PROVIDER = FileSystemProvider.installedProviders()
        .stream()
        .filter(p_201865_ -> p_201865_.getScheme().equalsIgnoreCase("jar"))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No jar file system provider found"));
    private static Consumer<String> thePauser = p_201905_ -> {
    };

    public static <K, V> Collector<Entry<? extends K, ? extends V>, ?, Map<K, V>> toMap() {
        return Collectors.toMap(Entry::getKey, Entry::getValue);
    }

    public static <T> Collector<T, ?, List<T>> toMutableList() {
        return Collectors.toCollection(Lists::newArrayList);
    }

    public static <T extends Comparable<T>> String getPropertyName(Property<T> p_137454_, Object p_137455_) {
        return p_137454_.getName((T)p_137455_);
    }

    public static String makeDescriptionId(String p_137493_, @Nullable ResourceLocation p_137494_) {
        return p_137494_ == null
            ? p_137493_ + ".unregistered_sadface"
            : p_137493_ + "." + p_137494_.getNamespace() + "." + p_137494_.getPath().replace('/', '.');
    }

    public static long getMillis() {
        return getNanos() / 1000000L;
    }

    public static long getNanos() {
        return timeSource.getAsLong();
    }

    public static long getEpochMillis() {
        return Instant.now().toEpochMilli();
    }

    public static String getFilenameFormattedDateTime() {
        return FILENAME_DATE_TIME_FORMATTER.format(ZonedDateTime.now());
    }

    private static ExecutorService makeExecutor(String p_137478_) {
        int i = Mth.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, getMaxThreads());
        ExecutorService executorservice;
        if (i <= 0) {
            executorservice = MoreExecutors.newDirectExecutorService();
        } else {
            AtomicInteger atomicinteger = new AtomicInteger(1);
            executorservice = new ForkJoinPool(i, p_314383_ -> {
                ForkJoinWorkerThread forkjoinworkerthread = new ForkJoinWorkerThread(p_314383_) {
                    @Override
                    protected void onTermination(Throwable p_211561_) {
                        if (p_211561_ != null) {
                            Util.LOGGER.warn("{} died", this.getName(), p_211561_);
                        } else {
                            Util.LOGGER.debug("{} shutdown", this.getName());
                        }

                        super.onTermination(p_211561_);
                    }
                };
                forkjoinworkerthread.setName("Worker-" + p_137478_ + "-" + atomicinteger.getAndIncrement());
                return forkjoinworkerthread;
            }, Util::onThreadException, true);
        }

        return executorservice;
    }

    private static int getMaxThreads() {
        String s = System.getProperty("max.bg.threads");
        if (s != null) {
            try {
                int i = Integer.parseInt(s);
                if (i >= 1 && i <= 255) {
                    return i;
                }

                LOGGER.error("Wrong {} property value '{}'. Should be an integer value between 1 and {}.", "max.bg.threads", s, 255);
            } catch (NumberFormatException numberformatexception) {
                LOGGER.error("Could not parse {} property value '{}'. Should be an integer value between 1 and {}.", "max.bg.threads", s, 255);
            }
        }

        return 255;
    }

    public static ExecutorService backgroundExecutor() {
        return BACKGROUND_EXECUTOR;
    }

    public static ExecutorService ioPool() {
        return IO_POOL;
    }

    public static ExecutorService nonCriticalIoPool() {
        return DOWNLOAD_POOL;
    }

    public static void shutdownExecutors() {
        shutdownExecutor(BACKGROUND_EXECUTOR);
        shutdownExecutor(IO_POOL);
    }

    private static void shutdownExecutor(ExecutorService p_137532_) {
        p_137532_.shutdown();

        boolean flag;
        try {
            flag = p_137532_.awaitTermination(3L, TimeUnit.SECONDS);
        } catch (InterruptedException interruptedexception) {
            flag = false;
        }

        if (!flag) {
            p_137532_.shutdownNow();
        }
    }

    private static ExecutorService makeIoExecutor(String p_314465_, boolean p_314461_) {
        AtomicInteger atomicinteger = new AtomicInteger(1);
        return Executors.newCachedThreadPool(p_314387_ -> {
            Thread thread = new Thread(p_314387_);
            thread.setName(p_314465_ + atomicinteger.getAndIncrement());
            thread.setDaemon(p_314461_);
            thread.setUncaughtExceptionHandler(Util::onThreadException);
            return thread;
        });
    }

    public static void throwAsRuntime(Throwable p_137560_) {
        throw p_137560_ instanceof RuntimeException ? (RuntimeException)p_137560_ : new RuntimeException(p_137560_);
    }

    private static void onThreadException(Thread p_137496_, Throwable p_137497_) {
        pauseInIde(p_137497_);
        if (p_137497_ instanceof CompletionException) {
            p_137497_ = p_137497_.getCause();
        }

        if (p_137497_ instanceof ReportedException reportedexception) {
            Bootstrap.realStdoutPrintln(reportedexception.getReport().getFriendlyReport(ReportType.CRASH));
            System.exit(-1);
        }

        LOGGER.error(String.format(Locale.ROOT, "Caught exception in thread %s", p_137496_), p_137497_);
    }

    @Nullable
    public static Type<?> fetchChoiceType(TypeReference p_137457_, String p_137458_) {
        return !SharedConstants.CHECK_DATA_FIXER_SCHEMA ? null : doFetchChoiceType(p_137457_, p_137458_);
    }

    @Nullable
    private static Type<?> doFetchChoiceType(TypeReference p_137552_, String p_137553_) {
        Type<?> type = null;

        try {
            type = DataFixers.getDataFixer()
                .getSchema(DataFixUtils.makeKey(SharedConstants.getCurrentVersion().getDataVersion().getVersion()))
                .getChoiceType(p_137552_, p_137553_);
        } catch (IllegalArgumentException illegalargumentexception) {
            LOGGER.debug("No data fixer registered for {}", p_137553_);
            if (SharedConstants.IS_RUNNING_IN_IDE && false) {
                throw illegalargumentexception;
            }
        }

        return type;
    }

    public static Runnable wrapThreadWithTaskName(String p_143788_, Runnable p_143789_) {
        return SharedConstants.IS_RUNNING_IN_IDE ? () -> {
            Thread thread = Thread.currentThread();
            String s = thread.getName();
            thread.setName(p_143788_);

            try {
                p_143789_.run();
            } finally {
                thread.setName(s);
            }
        } : p_143789_;
    }

    public static <V> Supplier<V> wrapThreadWithTaskName(String p_183947_, Supplier<V> p_183948_) {
        return SharedConstants.IS_RUNNING_IN_IDE ? () -> {
            Thread thread = Thread.currentThread();
            String s = thread.getName();
            thread.setName(p_183947_);

            Object object;
            try {
                object = p_183948_.get();
            } finally {
                thread.setName(s);
            }

            return (V)object;
        } : p_183948_;
    }

    public static <T> String getRegisteredName(Registry<T> p_331026_, T p_331334_) {
        ResourceLocation resourcelocation = p_331026_.getKey(p_331334_);
        return resourcelocation == null ? "[unregistered]" : resourcelocation.toString();
    }

    public static <T> Predicate<T> allOf(List<? extends Predicate<T>> p_323679_) {
        return switch (p_323679_.size()) {
            case 0 -> p_323042_ -> true;
            case 1 -> (Predicate)p_323679_.get(0);
            case 2 -> p_323679_.get(0).and((Predicate<? super T>)p_323679_.get(1));
            default -> {
                Predicate<T>[] predicate = p_323679_.toArray(Predicate[]::new);
                yield p_352651_ -> {
                    for (Predicate<T> predicate1 : predicate) {
                        if (!predicate1.test((T)p_352651_)) {
                            return false;
                        }
                    }

                    return true;
                };
            }
        };
    }

    public static <T> Predicate<T> anyOf(List<? extends Predicate<T>> p_323722_) {
        return switch (p_323722_.size()) {
            case 0 -> p_323047_ -> false;
            case 1 -> (Predicate)p_323722_.get(0);
            case 2 -> p_323722_.get(0).or((Predicate<? super T>)p_323722_.get(1));
            default -> {
                Predicate<T>[] predicate = p_323722_.toArray(Predicate[]::new);
                yield p_352655_ -> {
                    for (Predicate<T> predicate1 : predicate) {
                        if (predicate1.test((T)p_352655_)) {
                            return true;
                        }
                    }

                    return false;
                };
            }
        };
    }

    public static <T> boolean isSymmetrical(int p_345051_, int p_345034_, List<T> p_346318_) {
        if (p_345051_ == 1) {
            return true;
        } else {
            int i = p_345051_ / 2;

            for (int j = 0; j < p_345034_; j++) {
                for (int k = 0; k < i; k++) {
                    int l = p_345051_ - 1 - k;
                    T t = p_346318_.get(k + j * p_345051_);
                    T t1 = p_346318_.get(l + j * p_345051_);
                    if (!t.equals(t1)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public static Util.OS getPlatform() {
        String s = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (s.contains("win")) {
            return Util.OS.WINDOWS;
        } else if (s.contains("mac")) {
            return Util.OS.OSX;
        } else if (s.contains("solaris")) {
            return Util.OS.SOLARIS;
        } else if (s.contains("sunos")) {
            return Util.OS.SOLARIS;
        } else if (s.contains("linux")) {
            return Util.OS.LINUX;
        } else {
            return s.contains("unix") ? Util.OS.LINUX : Util.OS.UNKNOWN;
        }
    }

    public static URI parseAndValidateUntrustedUri(String p_352394_) throws URISyntaxException {
        URI uri = new URI(p_352394_);
        String s = uri.getScheme();
        if (s == null) {
            throw new URISyntaxException(p_352394_, "Missing protocol in URI: " + p_352394_);
        } else {
            String s1 = s.toLowerCase(Locale.ROOT);
            if (!ALLOWED_UNTRUSTED_LINK_PROTOCOLS.contains(s1)) {
                throw new URISyntaxException(p_352394_, "Unsupported protocol in URI: " + p_352394_);
            } else {
                return uri;
            }
        }
    }

    public static Stream<String> getVmArguments() {
        RuntimeMXBean runtimemxbean = ManagementFactory.getRuntimeMXBean();
        return runtimemxbean.getInputArguments().stream().filter(p_201903_ -> p_201903_.startsWith("-X"));
    }

    public static <T> T lastOf(List<T> p_137510_) {
        return p_137510_.get(p_137510_.size() - 1);
    }

    public static <T> T findNextInIterable(Iterable<T> p_137467_, @Nullable T p_137468_) {
        Iterator<T> iterator = p_137467_.iterator();
        T t = iterator.next();
        if (p_137468_ != null) {
            T t1 = t;

            while (t1 != p_137468_) {
                if (iterator.hasNext()) {
                    t1 = iterator.next();
                }
            }

            if (iterator.hasNext()) {
                return iterator.next();
            }
        }

        return t;
    }

    public static <T> T findPreviousInIterable(Iterable<T> p_137555_, @Nullable T p_137556_) {
        Iterator<T> iterator = p_137555_.iterator();
        T t = null;

        while (iterator.hasNext()) {
            T t1 = iterator.next();
            if (t1 == p_137556_) {
                if (t == null) {
                    t = iterator.hasNext() ? Iterators.getLast(iterator) : p_137556_;
                }
                break;
            }

            t = t1;
        }

        return t;
    }

    public static <T> T make(Supplier<T> p_137538_) {
        return p_137538_.get();
    }

    public static <T> T make(T p_137470_, Consumer<? super T> p_137471_) {
        p_137471_.accept(p_137470_);
        return p_137470_;
    }

    public static <V> CompletableFuture<List<V>> sequence(List<? extends CompletableFuture<V>> p_137568_) {
        if (p_137568_.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        } else if (p_137568_.size() == 1) {
            return p_137568_.get(0).thenApply(List::of);
        } else {
            CompletableFuture<Void> completablefuture = CompletableFuture.allOf(p_137568_.toArray(new CompletableFuture[0]));
            return completablefuture.thenApply(p_203746_ -> p_137568_.stream().map(CompletableFuture::join).toList());
        }
    }

    public static <V> CompletableFuture<List<V>> sequenceFailFast(List<? extends CompletableFuture<? extends V>> p_143841_) {
        CompletableFuture<List<V>> completablefuture = new CompletableFuture<>();
        return fallibleSequence(p_143841_, completablefuture::completeExceptionally).applyToEither(completablefuture, Function.identity());
    }

    public static <V> CompletableFuture<List<V>> sequenceFailFastAndCancel(List<? extends CompletableFuture<? extends V>> p_214685_) {
        CompletableFuture<List<V>> completablefuture = new CompletableFuture<>();
        return fallibleSequence(p_214685_, p_274642_ -> {
            if (completablefuture.completeExceptionally(p_274642_)) {
                for (CompletableFuture<? extends V> completablefuture1 : p_214685_) {
                    completablefuture1.cancel(true);
                }
            }
        }).applyToEither(completablefuture, Function.identity());
    }

    private static <V> CompletableFuture<List<V>> fallibleSequence(List<? extends CompletableFuture<? extends V>> p_214632_, Consumer<Throwable> p_214633_) {
        List<V> list = Lists.newArrayListWithCapacity(p_214632_.size());
        CompletableFuture<?>[] completablefuture = new CompletableFuture[p_214632_.size()];
        p_214632_.forEach(p_214641_ -> {
            int i = list.size();
            list.add(null);
            completablefuture[i] = p_214641_.whenComplete((p_214650_, p_214651_) -> {
                if (p_214651_ != null) {
                    p_214633_.accept(p_214651_);
                } else {
                    list.set(i, (V)p_214650_);
                }
            });
        });
        return CompletableFuture.allOf(completablefuture).thenApply(p_214626_ -> list);
    }

    public static <T> Optional<T> ifElse(Optional<T> p_137522_, Consumer<T> p_137523_, Runnable p_137524_) {
        if (p_137522_.isPresent()) {
            p_137523_.accept(p_137522_.get());
        } else {
            p_137524_.run();
        }

        return p_137522_;
    }

    public static <T> Supplier<T> name(Supplier<T> p_214656_, Supplier<String> p_214657_) {
        return p_214656_;
    }

    public static Runnable name(Runnable p_137475_, Supplier<String> p_137476_) {
        return p_137475_;
    }

    public static void logAndPauseIfInIde(String p_143786_) {
        LOGGER.error(p_143786_);
        if (SharedConstants.IS_RUNNING_WITH_JDWP) {
            doPause(p_143786_);
        }
    }

    public static void logAndPauseIfInIde(String p_200891_, Throwable p_200892_) {
        LOGGER.error(p_200891_, p_200892_);
        if (SharedConstants.IS_RUNNING_WITH_JDWP) {
            doPause(p_200891_);
        }
    }

    public static <T extends Throwable> T pauseInIde(T p_137571_) {
        if (SharedConstants.IS_RUNNING_WITH_JDWP) {
            LOGGER.error("Trying to throw a fatal exception, pausing in IDE", p_137571_);
            doPause(p_137571_.getMessage());
        }

        return p_137571_;
    }

    public static void setPause(Consumer<String> p_183970_) {
        thePauser = p_183970_;
    }

    private static void doPause(String p_183985_) {
        Instant instant = Instant.now();
        LOGGER.warn("Did you remember to set a breakpoint here?");
        boolean flag = Duration.between(instant, Instant.now()).toMillis() > 500L;
        if (!flag) {
            thePauser.accept(p_183985_);
        }
    }

    public static String describeError(Throwable p_137576_) {
        if (p_137576_.getCause() != null) {
            return describeError(p_137576_.getCause());
        } else {
            return p_137576_.getMessage() != null ? p_137576_.getMessage() : p_137576_.toString();
        }
    }

    public static <T> T getRandom(T[] p_214671_, RandomSource p_214672_) {
        return p_214671_[p_214672_.nextInt(p_214671_.length)];
    }

    public static int getRandom(int[] p_214668_, RandomSource p_214669_) {
        return p_214668_[p_214669_.nextInt(p_214668_.length)];
    }

    public static <T> T getRandom(List<T> p_214622_, RandomSource p_214623_) {
        return p_214622_.get(p_214623_.nextInt(p_214622_.size()));
    }

    public static <T> Optional<T> getRandomSafe(List<T> p_214677_, RandomSource p_214678_) {
        return p_214677_.isEmpty() ? Optional.empty() : Optional.of(getRandom(p_214677_, p_214678_));
    }

    private static BooleanSupplier createRenamer(final Path p_137503_, final Path p_137504_) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    Files.move(p_137503_, p_137504_);
                    return true;
                } catch (IOException ioexception) {
                    Util.LOGGER.error("Failed to rename", (Throwable)ioexception);
                    return false;
                }
            }

            @Override
            public String toString() {
                return "rename " + p_137503_ + " to " + p_137504_;
            }
        };
    }

    private static BooleanSupplier createDeleter(final Path p_137501_) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                try {
                    Files.deleteIfExists(p_137501_);
                    return true;
                } catch (IOException ioexception) {
                    Util.LOGGER.warn("Failed to delete", (Throwable)ioexception);
                    return false;
                }
            }

            @Override
            public String toString() {
                return "delete old " + p_137501_;
            }
        };
    }

    private static BooleanSupplier createFileDeletedCheck(final Path p_137562_) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return !Files.exists(p_137562_);
            }

            @Override
            public String toString() {
                return "verify that " + p_137562_ + " is deleted";
            }
        };
    }

    private static BooleanSupplier createFileCreatedCheck(final Path p_137573_) {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return Files.isRegularFile(p_137573_);
            }

            @Override
            public String toString() {
                return "verify that " + p_137573_ + " is present";
            }
        };
    }

    private static boolean executeInSequence(BooleanSupplier... p_137549_) {
        for (BooleanSupplier booleansupplier : p_137549_) {
            if (!booleansupplier.getAsBoolean()) {
                LOGGER.warn("Failed to execute {}", booleansupplier);
                return false;
            }
        }

        return true;
    }

    private static boolean runWithRetries(int p_137450_, String p_137451_, BooleanSupplier... p_137452_) {
        for (int i = 0; i < p_137450_; i++) {
            if (executeInSequence(p_137452_)) {
                return true;
            }

            LOGGER.error("Failed to {}, retrying {}/{}", p_137451_, i, p_137450_);
        }

        LOGGER.error("Failed to {}, aborting, progress might be lost", p_137451_);
        return false;
    }

    public static void safeReplaceFile(Path p_137506_, Path p_137507_, Path p_137508_) {
        safeReplaceOrMoveFile(p_137506_, p_137507_, p_137508_, false);
    }

    public static boolean safeReplaceOrMoveFile(Path p_307599_, Path p_307197_, Path p_307407_, boolean p_212228_) {
        if (Files.exists(p_307599_)
            && !runWithRetries(
                10, "create backup " + p_307407_, createDeleter(p_307407_), createRenamer(p_307599_, p_307407_), createFileCreatedCheck(p_307407_)
            )) {
            return false;
        } else if (!runWithRetries(10, "remove old " + p_307599_, createDeleter(p_307599_), createFileDeletedCheck(p_307599_))) {
            return false;
        } else if (!runWithRetries(10, "replace " + p_307599_ + " with " + p_307197_, createRenamer(p_307197_, p_307599_), createFileCreatedCheck(p_307599_))
            && !p_212228_) {
            runWithRetries(10, "restore " + p_307599_ + " from " + p_307407_, createRenamer(p_307407_, p_307599_), createFileCreatedCheck(p_307599_));
            return false;
        } else {
            return true;
        }
    }

    public static int offsetByCodepoints(String p_137480_, int p_137481_, int p_137482_) {
        int i = p_137480_.length();
        if (p_137482_ >= 0) {
            for (int j = 0; p_137481_ < i && j < p_137482_; j++) {
                if (Character.isHighSurrogate(p_137480_.charAt(p_137481_++)) && p_137481_ < i && Character.isLowSurrogate(p_137480_.charAt(p_137481_))) {
                    p_137481_++;
                }
            }
        } else {
            for (int k = p_137482_; p_137481_ > 0 && k < 0; k++) {
                p_137481_--;
                if (Character.isLowSurrogate(p_137480_.charAt(p_137481_)) && p_137481_ > 0 && Character.isHighSurrogate(p_137480_.charAt(p_137481_ - 1))) {
                    p_137481_--;
                }
            }
        }

        return p_137481_;
    }

    public static Consumer<String> prefix(String p_137490_, Consumer<String> p_137491_) {
        return p_214645_ -> p_137491_.accept(p_137490_ + p_214645_);
    }

    public static DataResult<int[]> fixedSize(IntStream p_137540_, int p_137541_) {
        int[] aint = p_137540_.limit((long)(p_137541_ + 1)).toArray();
        if (aint.length != p_137541_) {
            Supplier<String> supplier = () -> "Input is not a list of " + p_137541_ + " ints";
            return aint.length >= p_137541_ ? DataResult.error(supplier, Arrays.copyOf(aint, p_137541_)) : DataResult.error(supplier);
        } else {
            return DataResult.success(aint);
        }
    }

    public static DataResult<long[]> fixedSize(LongStream p_287579_, int p_287631_) {
        long[] along = p_287579_.limit((long)(p_287631_ + 1)).toArray();
        if (along.length != p_287631_) {
            Supplier<String> supplier = () -> "Input is not a list of " + p_287631_ + " longs";
            return along.length >= p_287631_ ? DataResult.error(supplier, Arrays.copyOf(along, p_287631_)) : DataResult.error(supplier);
        } else {
            return DataResult.success(along);
        }
    }

    public static <T> DataResult<List<T>> fixedSize(List<T> p_143796_, int p_143797_) {
        if (p_143796_.size() != p_143797_) {
            Supplier<String> supplier = () -> "Input is not a list of " + p_143797_ + " elements";
            return p_143796_.size() >= p_143797_ ? DataResult.error(supplier, p_143796_.subList(0, p_143797_)) : DataResult.error(supplier);
        } else {
            return DataResult.success(p_143796_);
        }
    }

    public static void startTimerHackThread() {
        Thread thread = new Thread("Timer hack thread") {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(2147483647L);
                    } catch (InterruptedException interruptedexception) {
                        Util.LOGGER.warn("Timer hack thread interrupted, that really should not happen");
                        return;
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        thread.start();
    }

    public static void copyBetweenDirs(Path p_137564_, Path p_137565_, Path p_137566_) throws IOException {
        Path path = p_137564_.relativize(p_137566_);
        Path path1 = p_137565_.resolve(path);
        Files.copy(p_137566_, path1);
    }

    public static String sanitizeName(String p_137484_, CharPredicate p_137485_) {
        return p_137484_.toLowerCase(Locale.ROOT)
            .chars()
            .mapToObj(p_214666_ -> p_137485_.test((char)p_214666_) ? Character.toString((char)p_214666_) : "_")
            .collect(Collectors.joining());
    }

    public static <K, V> SingleKeyCache<K, V> singleKeyCache(Function<K, V> p_270326_) {
        return new SingleKeyCache<>(p_270326_);
    }

    public static <T, R> Function<T, R> memoize(final Function<T, R> p_143828_) {
        return new Function<T, R>() {
            private final Map<T, R> cache = new ConcurrentHashMap<>();

            @Override
            public R apply(T p_214691_) {
                return this.cache.computeIfAbsent(p_214691_, p_143828_);
            }

            @Override
            public String toString() {
                return "memoize/1[function=" + p_143828_ + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T, U, R> BiFunction<T, U, R> memoize(final BiFunction<T, U, R> p_143822_) {
        return new BiFunction<T, U, R>() {
            private final Map<Pair<T, U>, R> cache = new ConcurrentHashMap<>();

            @Override
            public R apply(T p_214700_, U p_214701_) {
                return this.cache.computeIfAbsent(Pair.of(p_214700_, p_214701_), p_214698_ -> p_143822_.apply(p_214698_.getFirst(), p_214698_.getSecond()));
            }

            @Override
            public String toString() {
                return "memoize/2[function=" + p_143822_ + ", size=" + this.cache.size() + "]";
            }
        };
    }

    public static <T> List<T> toShuffledList(Stream<T> p_214662_, RandomSource p_214663_) {
        ObjectArrayList<T> objectarraylist = p_214662_.collect(ObjectArrayList.toList());
        shuffle(objectarraylist, p_214663_);
        return objectarraylist;
    }

    public static IntArrayList toShuffledList(IntStream p_214659_, RandomSource p_214660_) {
        IntArrayList intarraylist = IntArrayList.wrap(p_214659_.toArray());
        int i = intarraylist.size();

        for (int j = i; j > 1; j--) {
            int k = p_214660_.nextInt(j);
            intarraylist.set(j - 1, intarraylist.set(k, intarraylist.getInt(j - 1)));
        }

        return intarraylist;
    }

    public static <T> List<T> shuffledCopy(T[] p_214682_, RandomSource p_214683_) {
        ObjectArrayList<T> objectarraylist = new ObjectArrayList<>(p_214682_);
        shuffle(objectarraylist, p_214683_);
        return objectarraylist;
    }

    public static <T> List<T> shuffledCopy(ObjectArrayList<T> p_214612_, RandomSource p_214613_) {
        ObjectArrayList<T> objectarraylist = new ObjectArrayList<>(p_214612_);
        shuffle(objectarraylist, p_214613_);
        return objectarraylist;
    }

    public static <T> void shuffle(List<T> p_309187_, RandomSource p_214675_) {
        int i = p_309187_.size();

        for (int j = i; j > 1; j--) {
            int k = p_214675_.nextInt(j);
            p_309187_.set(j - 1, p_309187_.set(k, p_309187_.get(j - 1)));
        }
    }

    public static <T> CompletableFuture<T> blockUntilDone(Function<Executor, CompletableFuture<T>> p_214680_) {
        return blockUntilDone(p_214680_, CompletableFuture::isDone);
    }

    public static <T> T blockUntilDone(Function<Executor, T> p_214653_, Predicate<T> p_214654_) {
        BlockingQueue<Runnable> blockingqueue = new LinkedBlockingQueue<>();
        T t = p_214653_.apply(blockingqueue::add);

        while (!p_214654_.test(t)) {
            try {
                Runnable runnable = blockingqueue.poll(100L, TimeUnit.MILLISECONDS);
                if (runnable != null) {
                    runnable.run();
                }
            } catch (InterruptedException interruptedexception) {
                LOGGER.warn("Interrupted wait");
                break;
            }
        }

        int i = blockingqueue.size();
        if (i > 0) {
            LOGGER.warn("Tasks left in queue: {}", i);
        }

        return t;
    }

    public static <T> ToIntFunction<T> createIndexLookup(List<T> p_214687_) {
        int i = p_214687_.size();
        if (i < 8) {
            return p_214687_::indexOf;
        } else {
            Object2IntMap<T> object2intmap = new Object2IntOpenHashMap<>(i);
            object2intmap.defaultReturnValue(-1);

            for (int j = 0; j < i; j++) {
                object2intmap.put(p_214687_.get(j), j);
            }

            return object2intmap;
        }
    }

    public static <T> ToIntFunction<T> createIndexIdentityLookup(List<T> p_304758_) {
        int i = p_304758_.size();
        if (i < 8) {
            ReferenceList<T> referencelist = new ReferenceImmutableList<>(p_304758_);
            return referencelist::indexOf;
        } else {
            Reference2IntMap<T> reference2intmap = new Reference2IntOpenHashMap<>(i);
            reference2intmap.defaultReturnValue(-1);

            for (int j = 0; j < i; j++) {
                reference2intmap.put(p_304758_.get(j), j);
            }

            return reference2intmap;
        }
    }

    public static <A, B> Typed<B> writeAndReadTypedOrThrow(Typed<A> p_311873_, Type<B> p_312554_, UnaryOperator<Dynamic<?>> p_311990_) {
        Dynamic<?> dynamic = (Dynamic<?>)p_311873_.write().getOrThrow();
        return readTypedOrThrow(p_312554_, p_311990_.apply(dynamic), true);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> p_312508_, Dynamic<?> p_312911_) {
        return readTypedOrThrow(p_312508_, p_312911_, false);
    }

    public static <T> Typed<T> readTypedOrThrow(Type<T> p_313853_, Dynamic<?> p_313851_, boolean p_313933_) {
        DataResult<Typed<T>> dataresult = p_313853_.readTyped(p_313851_).map(Pair::getFirst);

        try {
            return p_313933_ ? dataresult.getPartialOrThrow(IllegalStateException::new) : dataresult.getOrThrow(IllegalStateException::new);
        } catch (IllegalStateException illegalstateexception) {
            CrashReport crashreport = CrashReport.forThrowable(illegalstateexception, "Reading type");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Info");
            crashreportcategory.setDetail("Data", p_313851_);
            crashreportcategory.setDetail("Type", p_313853_);
            throw new ReportedException(crashreport);
        }
    }

    public static <T> List<T> copyAndAdd(List<T> p_331978_, T p_331554_) {
        return ImmutableList.<T>builderWithExpectedSize(p_331978_.size() + 1).addAll(p_331978_).add(p_331554_).build();
    }

    public static <T> List<T> copyAndAdd(T p_335459_, List<T> p_335538_) {
        return ImmutableList.<T>builderWithExpectedSize(p_335538_.size() + 1).add(p_335459_).addAll(p_335538_).build();
    }

    public static <K, V> Map<K, V> copyAndPut(Map<K, V> p_330429_, K p_330814_, V p_330949_) {
        return ImmutableMap.<K, V>builderWithExpectedSize(p_330429_.size() + 1).putAll(p_330429_).put(p_330814_, p_330949_).buildKeepingLast();
    }

    public static enum OS {
        LINUX("linux"),
        SOLARIS("solaris"),
        WINDOWS("windows") {
            @Override
            protected String[] getOpenUriArguments(URI p_352177_) {
                return new String[]{"rundll32", "url.dll,FileProtocolHandler", p_352177_.toString()};
            }
        },
        OSX("mac") {
            @Override
            protected String[] getOpenUriArguments(URI p_352407_) {
                return new String[]{"open", p_352407_.toString()};
            }
        },
        UNKNOWN("unknown");

        private final String telemetryName;

        OS(String p_183998_) {
            this.telemetryName = p_183998_;
        }

        public void openUri(URI p_137649_) {
            try {
                Process process = AccessController.doPrivileged(
                    (PrivilegedExceptionAction<Process>)(() -> Runtime.getRuntime().exec(this.getOpenUriArguments(p_137649_)))
                );
                process.getInputStream().close();
                process.getErrorStream().close();
                process.getOutputStream().close();
            } catch (IOException | PrivilegedActionException privilegedactionexception) {
                Util.LOGGER.error("Couldn't open location '{}'", p_137649_, privilegedactionexception);
            }
        }

        public void openFile(File p_137645_) {
            this.openUri(p_137645_.toURI());
        }

        public void openPath(Path p_352210_) {
            this.openUri(p_352210_.toUri());
        }

        protected String[] getOpenUriArguments(URI p_352257_) {
            String s = p_352257_.toString();
            if ("file".equals(p_352257_.getScheme())) {
                s = s.replace("file:", "file://");
            }

            return new String[]{"xdg-open", s};
        }

        public void openUri(String p_137647_) {
            try {
                this.openUri(new URI(p_137647_));
            } catch (IllegalArgumentException | URISyntaxException urisyntaxexception) {
                Util.LOGGER.error("Couldn't open uri '{}'", p_137647_, urisyntaxexception);
            }
        }

        public String telemetryName() {
            return this.telemetryName;
        }
    }
}
