package io.github.m_segreti;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Gradle extension used to configure the Bun runtime for a project.
 * <p>
 * This extension is exposed to build scripts as {@code bun { ... }} and allows
 * users to control which version of Bun is installed and which platform/system
 * variant is used.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * bun {
 *   version = "1.1.0"          // Optional, defaults to "latest"
 *   system  = BunSystem.detect() // Optional, defaults to auto-detection
 * }
 * }</pre>
 *
 * <p>
 * No defaults are set directly on this extension. This is intentional so that
 * users may explicitly leave values unset or override them later. Defaults are
 * applied during plugin wiring instead of at extension construction time.
 */
public abstract class BunExtension {

    /**
     * The Bun version to install.
     * <p>
     * This value may be:
     * <ul>
     *     <li>An explicit version string (e.g. {@code "1.1.0"})</li>
     *     <li>{@code "latest"} to resolve the most recent release</li>
     *     <li>Unset, in which case the plugin will apply a default</li>
     * </ul>
     *
     * @return a Gradle {@link Property} representing the configured Bun version
     */
    public abstract Property<String> getVersion();

    /**
     * The system/platform variant of Bun to install.
     * <p>
     * This typically corresponds to a combination of operating system and CPU
     * architecture (for example, Windows x64, Linux arm64, etc.).
     * <p>
     * When unset, the plugin will attempt to auto-detect the appropriate
     * {@link BunSystem} for the current build environment.
     *
     * @return a Gradle {@link Property} representing the configured Bun system
     */
    public abstract Property<BunSystem> getSystem();

    /**
     * Whether to disable SSL certificate verification during downloads.
     * <p>
     * This is useful in corporate environments with SSL-intercepting proxies.
     * Defaults to false.
     *
     * @return a Gradle {@link Property} representing whether SSL verification is disabled
     */
    public abstract Property<Boolean> getDisableSslVerification();

    /**
     * Constructs the Bun extension.
     * <p>
     * Defaults are intentionally not applied here so users can explicitly
     * "unset" values if desired. The plugin is responsible for applying
     * fallback defaults during task wiring.
     *
     * @param objects Gradle {@link ObjectFactory} used for creating managed properties
     */
    @Inject
    public BunExtension(ObjectFactory objects) {
        // Intentionally empty
    }
}
