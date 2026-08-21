/**
 * The official MHCenter2 module API.
 *
 * <p>This package - together with {@link net.managerhub.center.api.velocity} for
 * proxy modules - is the supported contract for external modules. Everything a
 * module needs is here: the lifecycle, the context it is handed, its log, the
 * platform it runs on, the two types a module command is built from, and the
 * network access every module gets.</p>
 *
 * <p>Every other MHCenter2 package is internal. Loader, menu, database, command
 * registration, configuration and the platform bootstraps may change in any
 * release without notice, even when a class there happens to be {@code public}.
 * A module that reaches into them is not supported.</p>
 *
 * <p>The network part - {@link net.managerhub.center.api.ModuleNetwork},
 * {@link net.managerhub.center.api.ModuleStorage} and the action types - only
 * works when an administrator switched the optional remote database on. It never
 * falls back to the local database of one server: data a module puts there is
 * meant to travel between servers, and silently keeping it on one of them would
 * look successful and still lose it.</p>
 *
 * <p><strong>Modules are not a sandbox.</strong> A module is normal Java code in
 * the same process as the server, with the same possibilities as any other code
 * there. The cleanup registry is lifecycle management, not security isolation.
 * Install modules only from sources you trust.</p>
 */
package net.managerhub.center.api;
