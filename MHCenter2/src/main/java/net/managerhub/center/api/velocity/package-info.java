/**
 * The Velocity part of the official MHCenter2 module API.
 *
 * <p>This package is supported exactly like
 * {@link net.managerhub.center.api}, with one difference: it uses Velocity types
 * on purpose, because it exists only for modules that really run on the proxy.
 * A module reaches it through
 * {@code context.service(VelocityModuleApi.class)}.</p>
 *
 * <p>A module with {@code platform=BOTH} must keep everything that touches this
 * package in its own class and must only load that class after
 * {@code context.platform()} answered {@code VELOCITY}. Otherwise the class
 * loader would try to resolve a Velocity type while the module is starting on
 * Paper, where it does not exist.</p>
 *
 * <p>The same warning as for the neutral part of the API applies:
 * <strong>modules are not a sandbox.</strong> {@code proxy()} hands out the
 * running proxy, and a module can do anything with it that any other Velocity
 * plugin could do. Install modules only from sources you trust.</p>
 */
package net.managerhub.center.api.velocity;
