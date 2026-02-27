package io.github.tetratheta.bun;

import java.util.Locale;

/// Enumeration of supported Bun platform/system combinations.
///
/// Each enum constant represents a specific operating system and CPU architecture
/// pairing and defines:
///   - The name of the Bun release zip asset to download.
///   - The expected name of the Bun executable within the extracted distribution.
///
/// This enum is used by the Bun Gradle plugin to:
///   - Select the correct Bun release asset for the current build environment.
///   - Locate the executable after extraction.
///   - Allow users to explicitly override platform detection if necessary.
///
/// ## Platform Variants
/// Some platforms provide multiple builds (e.g. baseline vs non-baseline, glibc vs musl).
/// Only a subset is automatically detected; others may be selected explicitly by users
/// via the `bun{system = ...}` configuration.
public enum BunSystem {

  /// Windows x64 build using the standard runtime.
  WINDOWS_X64("bun-windows-x64.zip", "bun.exe"),

  /// Windows x64 baseline build (more compatible, potentially slower).
  WINDOWS_X64_BASELINE("bun-windows-x64-baseline.zip", "bun.exe"),

  /// macOS (Darwin) ARM64 / Apple Silicon build.
  DARWIN_AARCH64("bun-darwin-aarch64.zip", "bun"),

  /// macOS (Darwin) x64 / Intel build.
  DARWIN_X64("bun-darwin-x64.zip", "bun"),

  /// Linux x64 build targeting glibc.
  LINUX_X64("bun-linux-x64.zip", "bun"),

  /// Linux x64 baseline build targeting glibc.
  LINUX_X64_BASELINE("bun-linux-x64-baseline.zip", "bun"),

  /// Linux ARM64 build targeting glibc.
  LINUX_AARCH64("bun-linux-aarch64.zip", "bun"),

  /// Linux x64 build targeting musl (e.g. Alpine Linux).
  LINUX_X64_MUSL("bun-linux-x64-musl.zip", "bun"),

  /// Linux x64 baseline build targeting musl.
  LINUX_X64_MUSL_BASELINE("bun-linux-x64-musl-baseline.zip", "bun"),

  /// Linux ARM64 build targeting musl.
  LINUX_AARCH64_MUSL("bun-linux-aarch64-musl.zip", "bun");

  private final String exeName;
  private final String zipName;

  BunSystem(String zipName, String exeName) {
    this.zipName = zipName;
    this.exeName = exeName;
  }

  /// Detects the current operating system and CPU architecture and selects
  /// the most appropriate [BunSystem].
  ///
  /// Detection is based on the standard JVM system properties:
  ///   - `os.name`
  ///   - `os.arch`
  ///
  /// Only the most common system combinations are auto-detected. More specialized
  /// variants (such as musl or baseline builds) must be selected explicitly by
  /// configuring the plugin.
  ///
  /// @return the detected [BunSystem]
  /// @throws IllegalStateException if the current OS/architecture combination
  ///                                                             is not supported by this plugin
  public static BunSystem detect() {
    final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    final String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

    boolean isArm64 = arch.contains("aarch64") || arch.contains("arm64");
    boolean isX64 = arch.contains("x86_64") || arch.contains("amd64");

    if (os.contains("win") && isX64) return WINDOWS_X64;
    if (os.contains("mac") && isArm64) return DARWIN_AARCH64;
    if (os.contains("mac") && isX64) return DARWIN_X64;
    if (os.contains("linux") && isArm64) return LINUX_AARCH64;
    if (os.contains("linux") && isX64) return LINUX_X64;

    throw new IllegalStateException("Unsupported OS/arch: os=" + os + " arch=" + arch);
  }

  /// Returns the expected name of the Bun executable for this system.
  ///
  /// @return the executable file name (e.g. `bun` or `bun.exe`)
  public String exeName() {
    return exeName;
  }

  /// Returns the name of the Bun zip asset associated with this system.
  ///
  /// @return the Bun zip file name (as published on GitHub releases)
  public String zipName() {
    return zipName;
  }
}
