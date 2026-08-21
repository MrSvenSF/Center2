package net.managerhub.center.common.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.managerhub.center.Center;
import net.managerhub.center.api.ModuleCommand;

/**
 * The commands the loaded modules have registered.
 *
 * <p>Both platforms use this registry. It only decides <em>whether</em> a path is
 * free; how a command is registered afterwards is the job of the platform, which
 * is completely different on Paper and on Velocity.</p>
 *
 * <p>The decision is made right away, so
 * {@link net.managerhub.center.api.ModuleContext#registerCommand} can answer
 * honestly: a path that belongs to a MHCenter2 core command or to one of its
 * aliases, and a path another module already uses, is refused here and never
 * silently dropped later.</p>
 */
public final class ModuleCommandRegistry {

    /**
     * One command of one module.
     *
     * @param moduleId id of the module the command belongs to
     * @param path     complete command path
     * @param command  what the command does
     */
    public record Registered(String moduleId, CommandPath path, ModuleCommand command) {
    }

    private final List<Registered> commands = new ArrayList<>();

    /** The paths the core owns right now. */
    private Supplier<Set<String>> reserved = Set::of;

    /** Whether a command name already belongs to another plugin of the platform. */
    private Predicate<String> foreignRoot = name -> false;

    /**
     * Connects the registry with the command layout of the core.
     *
     * @param reservedPaths every path of the core, including aliases and system commands
     */
    public void reserve(final Supplier<Set<String>> reservedPaths) {
        this.reserved = reservedPaths;
    }

    /**
     * Connects the registry with the command list of the platform.
     *
     * <p>Paper and Velocity both know exactly which command names are taken and
     * by whom, so a module can be told right away that the name it wants belongs
     * to another plugin - instead of getting a {@code true} and finding out later
     * that its command never appeared.</p>
     *
     * @param foreignRootNames answers {@code true} for a command name another
     *                         plugin owns; a name MHCenter2 itself registered is
     *                         not foreign
     */
    public void platform(final Predicate<String> foreignRootNames) {
        this.foreignRoot = foreignRootNames;
    }

    /**
     * Claims one command path for one module.
     *
     * @param moduleId id of the module
     * @param path     complete command path
     * @param command  what the command does
     * @return empty if the path was accepted, otherwise the reason it was refused
     */
    public Optional<String> register(final String moduleId, final CommandPath path, final ModuleCommand command) {
        final String display = path.display();
        if (reserved.get().stream().anyMatch(taken -> taken.equalsIgnoreCase(display))) {
            return Optional.of("the command path \"" + display + "\" belongs to "
                    + Center.PRODUCT_NAME + " itself.");
        }
        if (commands.stream().anyMatch(entry -> entry.path().display().equalsIgnoreCase(display))) {
            return Optional.of("the command path \"" + display + "\" is already used by another module.");
        }
        // The last check is the platform itself. A name another plugin owns can
        // never be served, so answering "accepted" here would be a lie.
        if (foreignRoot.test(path.rootName())) {
            return Optional.of("the command \"" + path.rootName() + "\" is already registered by another "
                    + "plugin on this server, so \"" + display + "\" cannot be served.");
        }
        commands.add(new Registered(moduleId, path, command));
        return Optional.empty();
    }

    /**
     * Removes exactly one command of one module again.
     *
     * @param moduleId id of the module
     * @param path     complete command path
     */
    public void unregister(final String moduleId, final CommandPath path) {
        commands.removeIf(entry -> sameModule(entry, moduleId)
                && entry.path().display().equalsIgnoreCase(path.display()));
    }

    /**
     * Removes every command of one module.
     *
     * @param moduleId id of the module
     */
    public void unregisterModule(final String moduleId) {
        commands.removeIf(entry -> sameModule(entry, moduleId));
    }

    /** Removes every module command. Belongs to the shutdown of the platform. */
    public void clear() {
        commands.clear();
    }

    /** @return every registered module command, in registration order. */
    public List<Registered> all() {
        return List.copyOf(commands);
    }

    private static boolean sameModule(final Registered entry, final String moduleId) {
        return entry.moduleId().toLowerCase(Locale.ROOT).equals(moduleId.toLowerCase(Locale.ROOT));
    }
}
