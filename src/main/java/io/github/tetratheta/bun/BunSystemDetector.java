package io.github.tetratheta.bun;

import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;

/// [ValueSource] that detects the current [BunSystem] by reading JVM system properties.
///
/// Using a [ValueSource] (rather than calling [System#getProperty] directly during the
/// configuration phase) makes detection compatible with Gradle's Configuration Cache:
/// Gradle tracks the value as an external input and can re-evaluate it when the
/// operating system or architecture reported by the JVM changes.
public abstract class BunSystemDetector implements ValueSource<BunSystem, ValueSourceParameters.None> {
  @Override
  public BunSystem obtain() {
    return BunSystem.detect();
  }
}
