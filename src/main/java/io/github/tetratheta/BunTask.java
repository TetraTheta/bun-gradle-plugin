package io.github.tetratheta;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/// Gradle [Exec] task specialized for invoking the Bun executable.
///
/// This task acts as a thin wrapper around [Exec] that:
///   - Defers resolution of the Bun executable until execution time.
///   - Accumulates Bun command-line arguments independently of Gradle’s built-in argument handling.
///   - Provides a clear failure mode if Bun has not been set up prior to execution.
///
/// Typical usage is internal to the plugin. Tasks such as `bunInstall`,
/// `bunTest`, and `bunRun` configure an instance of this task by:
///   - Declaring a dependency on `bunSetup`.
///   - Setting the Bun executable during `doFirst`.
///   - Providing Bun-specific arguments via [#args(String...)].
///
/// This design keeps configuration-time logic minimal and ensures the Bun
/// executable path is resolved only after installation has completed.
public abstract class BunTask extends Exec {
  /// Adds one or more command-line arguments to be passed to Bun.
  ///
  /// Arguments are appended in the order provided and are not modified
  /// or validated by this task.
  ///
  /// @param args the arguments to pass to Bun
  public void args(String... args) {
    for (String a : args) getBunArgs().add(a);
  }

  // TODO: Add proper JavaDoc
  @Input
  public abstract ListProperty<String> getBunArgs();

  /// Executes the Bun command.
  ///
  /// This method:
  ///   - Verifies that the Bun executable has been set.
  ///   - Configures the underlying [Exec] task with the executable path.
  ///   - Applies the collected Bun arguments.
  ///   - Delegates execution to [Exec#exec()].
  ///
  /// @throws IllegalStateException if the Bun executable has not been set
  @Override
  protected void exec() {
    File resolved;
    if (!getBunExecutableProperty().isPresent()) {
      final String version = BunHelpers.normalizeVersion(getVersion().getOrNull());
      final BunSystem system = getSystem().get();
      final File bunRoot = getBunRootDir().get().getAsFile();

      final File installDir = new File(bunRoot, version + File.separator + BunHelpers.stripZip(system.zipName()));
      resolved = BunHelpers.findBunExecutable(installDir, system.exeName()).orElse(null);

      if (resolved == null) {
        throw new IllegalStateException("bunExecutable not set (did you dependOn bunSetup?)");
      }
    } else {
      resolved = getBunExecutableProperty().get().getAsFile();
    }

    List<String> finalArgs = new ArrayList<>(getBunArgs().get());

    if (getForceBun().getOrElse(false)) {
      finalArgs.remove("--bun");
      finalArgs.add(0, "--bun");
      File ghostNode = createGhostNode(resolved);
      getLogger().lifecycle("Force Bun enabled. Ghost Node: {}", ghostNode.getAbsolutePath());
    }

    // Add Bun to PATH for child processes
    // Identify correct key (Path or PATH)
    final String bunDir = resolved.getParentFile().getAbsolutePath();
    String pathKey = "PATH";
    Object existingPath = null;
    for (String key : getEnvironment().keySet()) {
      if (key.equalsIgnoreCase("PATH")) {
        pathKey = key;
        existingPath = getEnvironment().get(key);
        break;
      }
    }
    // Fallback to system env if Gradle's own map doesn't have it yet
    if (existingPath == null) {
      existingPath = System.getenv("PATH"); // case insensitive
      if (existingPath == null) existingPath = System.getenv("Path");
    }

    final String newPath = bunDir + File.pathSeparator + existingPath;
    getLogger().lifecycle("Updated PATH: {}", newPath);

    environment(pathKey, newPath);

    getLogger().lifecycle("Bun executable: [{}]", resolved.getAbsolutePath());
    getLogger().lifecycle("Bun arguments : {}", getBunArgs().get());

    setExecutable(resolved.getAbsolutePath());
    super.setArgs(finalArgs);

    super.exec();
  }

  private File createGhostNode(File resolved) {
    String nodeName = System.getProperty("os.name").toLowerCase().contains("win") ? "node.exe" : "node";
    File ghostNode = new File(resolved.getParentFile(), nodeName);

    if (!ghostNode.exists()) {
      getLogger().lifecycle("Creating ghost node alias at {}", ghostNode.getAbsolutePath());
      try {
        Path target = resolved.toPath();
        Path link = ghostNode.toPath();

        try {
          Files.createLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
          Files.copy(target, link, StandardCopyOption.REPLACE_EXISTING);
        }

        // WINDOWS FIX: Poll until the OS 'sees' the file and it's not locked
        int attempt = 0;
        while (attempt < 20) {
          if (Files.exists(link) && Files.isReadable(link)) {
            Thread.sleep(50);
            break;
          }
          Thread.sleep(50);
          attempt++;
        }
      } catch (IOException | InterruptedException e) {
        getLogger().warn("Failed to create ghost node alias: {}", e.getMessage());
      }
    }
    return ghostNode;
  }

  @Internal
  public abstract RegularFileProperty getBunExecutableProperty();

  @Internal
  public abstract Property<String> getVersion();

  @Internal
  public abstract Property<BunSystem> getSystem();

  @Internal
  public abstract DirectoryProperty getBunRootDir();

  @Input
  public abstract Property<Boolean> getForceBun();

  /// Sets the command-line arguments to be passed to Bun.
  ///
  /// This overrides the parent [Exec#setArgs(List)] to ensure
  /// arguments are captured in the bunArgs list.
  ///
  /// @param args the arguments to pass to Bun
  @SuppressWarnings("NullableProblems")
  @Override
  public Exec setArgs(@Nullable List<String> args) {
    getBunArgs().set(args);
    return this;
  }
}
