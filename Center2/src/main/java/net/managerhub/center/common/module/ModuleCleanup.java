package net.managerhub.center.common.module;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The cleanup actions one module handed to Center2.
 *
 * <p>Center2 cannot remove what it does not know about. A module that registers a
 * listener, a task or any other resource therefore says right away how it is
 * removed again, and Center2 runs those actions when the module is stopped, when
 * it fails while loading or starting, and when the server shuts down.</p>
 *
 * <p>Actions run in the opposite order of their registration, so a resource that
 * was built on top of another one is removed first. This is deliberately not a
 * general abstraction over listeners, schedulers or services: it is only the
 * small hook the module lifecycle needs to stay safe.</p>
 */
public final class ModuleCleanup {

    private final Deque<Runnable> actions = new ArrayDeque<>();
    private boolean running;

    /**
     * @param action what removes one resource of the module again; {@code null} is ignored
     */
    public void register(final Runnable action) {
        if (action == null || running) {
            // An action that is registered while cleaning up would never run and
            // could otherwise grow the list forever.
            return;
        }
        actions.push(action);
    }

    /** @return {@code true} if this module registered no cleanup at all. */
    public boolean isEmpty() {
        return actions.isEmpty();
    }

    /**
     * Runs every registered action once, newest first, and forgets them.
     *
     * <p>A failing action never stops the remaining ones: the whole point is that
     * as much as possible is removed.</p>
     *
     * @return every failure that happened, in the order they happened
     */
    public List<Throwable> runAll() {
        final List<Throwable> failures = new ArrayList<>();
        running = true;
        try {
            while (!actions.isEmpty()) {
                try {
                    actions.pop().run();
                } catch (final Throwable broken) {
                    failures.add(broken);
                }
            }
        } finally {
            running = false;
        }
        return List.copyOf(failures);
    }
}
