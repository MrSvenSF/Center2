package net.managerhub.center.common.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import net.managerhub.center.Center;
import net.managerhub.center.api.ModuleLogger;
import net.managerhub.center.common.language.Language;
import net.managerhub.center.common.language.MessageKey;

/**
 * The one background thread of the remote system.
 *
 * <p>Everything that talks to the remote database runs here and nowhere else: the
 * heartbeat, the poll for new actions, the cleanup of expired rows and every
 * operation a module or a command starts. The main server thread of Paper and the
 * event loop of Velocity are never used for a database call, not even for a
 * moment.</p>
 *
 * <p>One thread is enough and is also the point: the tasks of one node never run
 * next to each other, so there is no second heartbeat and no second poller, not
 * even for the short time of a reload.</p>
 */
public final class RemoteWorker implements AutoCloseable {

    /** How often expired rows are removed. */
    private static final long PURGE_INTERVAL_SECONDS = 60L;

    private final RemoteService remote;
    private final ModuleLogger log;
    private final Supplier<Language> language;
    private final AtomicInteger threads = new AtomicInteger();

    private ScheduledExecutorService executor;
    private final List<ScheduledFuture<?>> tasks = new ArrayList<>();

    /**
     * @param remote   the remote system this worker drives
     * @param log      where a failure of the worker itself is written
     * @param language the texts that are currently active
     */
    public RemoteWorker(final RemoteService remote,
                        final ModuleLogger log,
                        final Supplier<Language> language) {
        this.remote = remote;
        this.log = log;
        this.language = language;
    }

    /**
     * Applies a configuration and starts or restarts the tasks.
     *
     * <p>Called at startup and after every reload. The old tasks are always
     * cancelled first, so a reload can never leave a second heartbeat or a second
     * poller behind.</p>
     *
     * @param settings the settings of the configuration that is active now
     */
    public synchronized void apply(final RemoteSettings settings) {
        cancelTasks();
        remote.apply(settings);
        if (!settings.usable()) {
            return;
        }
        final ScheduledExecutorService open = executor();
        tasks.add(open.scheduleWithFixedDelay(guarded("heartbeat", remote::heartbeatTick),
                0L, settings.heartbeat().intervalSeconds(), TimeUnit.SECONDS));
        tasks.add(open.scheduleWithFixedDelay(guarded("poll", remote::pollTick),
                settings.polling().intervalMs(), settings.polling().intervalMs(), TimeUnit.MILLISECONDS));
        tasks.add(open.scheduleWithFixedDelay(guarded("cleanup", remote::purgeTick),
                PURGE_INTERVAL_SECONDS, PURGE_INTERVAL_SECONDS, TimeUnit.SECONDS));
    }

    /**
     * Runs something on the remote thread.
     *
     * <p>This is how a command reaches the database: the main thread hands the
     * work over and goes on, and the answer comes back the same way the platform
     * usually returns to its own thread.</p>
     *
     * @param name what is running, used if it fails
     * @param work what to do
     */
    public synchronized void submit(final String name, final Runnable work) {
        executor().execute(guarded(name, work));
    }

    /** Stops every task, removes this node from the network list and closes the thread. */
    @Override
    public synchronized void close() {
        cancelTasks();
        final ScheduledExecutorService open = executor;
        executor = null;
        if (open == null) {
            remote.stop();
            return;
        }
        // The last thing on the remote thread is the clean goodbye, so the row of
        // this node disappears right away instead of expiring minutes later.
        open.execute(remote::stop);
        open.shutdown();
        try {
            if (!open.awaitTermination(5L, TimeUnit.SECONDS)) {
                open.shutdownNow();
            }
        } catch (final InterruptedException interrupted) {
            open.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private ScheduledExecutorService executor() {
        if (executor == null) {
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                final Thread thread = new Thread(runnable,
                        Center.PRODUCT_NAME + "-remote-" + threads.incrementAndGet());
                // A background thread must never keep a stopping server alive.
                thread.setDaemon(true);
                return thread;
            });
        }
        return executor;
    }

    private void cancelTasks() {
        for (final ScheduledFuture<?> task : tasks) {
            task.cancel(false);
        }
        tasks.clear();
    }

    /**
     * Keeps one failing task from killing the whole schedule.
     *
     * <p>A {@link ScheduledExecutorService} silently stops a repeating task that
     * throws. That would switch the remote system off without a word, so nothing
     * is ever allowed to leave a task uncaught.</p>
     */
    private Runnable guarded(final String name, final Runnable work) {
        return () -> {
            try {
                work.run();
            } catch (final Throwable broken) {
                log.error(language.get().get(MessageKey.REMOTE_TASK_FAILED,
                        "product", Center.PRODUCT_NAME, "task", name), broken);
            }
        };
    }
}
