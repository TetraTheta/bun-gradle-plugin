package io.github.tetratheta.bun;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/// Gradle extension used to configure the Bun runtime for a project.
///
/// This extension is exposed to build scripts as `bun {...}` and allows
/// users to control which version of Bun is installed and which platform/system
/// variant is used.
/// ## Usage
/// ```
/// bun {
///   forceBun = false // Optional, force usage of Bun when Node is required in script
///   system  = BunSystem.detect() // Optional, defaults to auto-detection
///   version = "1.1.0" // Optional, defaults to "latest"
/// }
/// ```
/// No defaults are set directly on this extension. This is intentional so that
/// users may explicitly leave values unset or override them later. Defaults are
/// applied during plugin wiring instead of at extension construction time.
public abstract class BunExtension {
  /// Constructs the Bun extension.
  ///
  /// Defaults are intentionally not applied here so users can explicitly
  /// "unset" values if desired. The plugin is responsible for applying
  /// fallback defaults during task wiring.
  ///
  /// @param objects Gradle [ObjectFactory] used for creating managed properties
  @Inject
  public BunExtension(ObjectFactory objects) {
    // Intentionally empty
  }

  /// Whether to disable SSL certificate verification during downloads.
  ///
  /// This is useful in corporate environments with SSL-intercepting proxies.
  /// Defaults to false.
  ///
  /// @return a Gradle [Property] representing whether SSL verification is disabled
  public abstract Property<Boolean> getDisableSslVerification();

  /// Whether to force usage of Bun when Node is required to run script.
  ///
  /// This is the only way of forcing Bun usage because `bunfig.toml` and `--bun` are both ignored.
  /// Defaults to false.
  ///
  /// @return a Gradle [Property] representing the configured 'Ghost Node' option
  public abstract Property<Boolean> getForceBun();

  /// The system/platform variant of Bun to install.
  ///
  /// This typically corresponds to a combination of operating system and CPU
  /// architecture (for example, Windows x64, Linux arm64, etc.).
  ///
  /// When unset, the plugin will attempt to auto-detect the appropriate
  /// [BunSystem] for the current build environment.
  ///
  /// @return a Gradle [Property] representing the configured Bun system
  public abstract Property<BunSystem> getSystem();

  /// The Bun version to install.
  ///
  /// This value may be:
  ///   - An explicit version string (e.g. `"1.1.0"`)
  ///   - `"latest"` to resolve the most recent release
  ///   - Unset, in which case the plugin will apply a default
  ///
  /// @return a Gradle [Property] representing the configured Bun version
  public abstract Property<String> getVersion();

  /// The working directory for Bun tasks.
  ///
  /// Default is project root directory.
  ///
  /// @return a [DirectoryProperty] representing the configured working directory
  public abstract DirectoryProperty getWorkingDir();
}
