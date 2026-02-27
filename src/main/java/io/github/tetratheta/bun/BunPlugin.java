package io.github.tetratheta.bun;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;

import java.io.File;

/// Gradle plugin that downloads and runs the [Bun](https://bun.sh/) runtime in a local project.
///
/// This plugin:
///   - Creates a `bun` extension ([BunExtension]) so builds can configure the Bun `version` and `system`.
///   - Registers a `bunSetup` task that downloads/unpacks Bun into `<project>/.gradle/bun/...`.
///   - Registers a small set of convenience tasks (`bunInstall`, `bunTest`, `bunRun`, `bunInstallPkg`)
///     that execute Bun commands using the installed executable.
///
/// Installation is isolated per project and per version/system combination to avoid interfering with any global Bun install.
/// ## Configuration
/// ```
/// bun {
///   version = "1.1.0" // defaults to "latest"
///   system = BunSystem.WINDOWS_X64 // defaults to auto-detect
/// }
/// ```
/// ## Tasks
///   - `bunSetup`: Downloads and installs Bun (dependency of all Bun execution tasks).
///   - `bunInstall`: Runs `bun install` in the project directory.
///   - `bunTest`: Runs `bun test` in the project directory.
///   - `bunRun`: Runs `bun run <script>` where `<script>` comes from `-PbunScript=...`.
///   - `bunInstallPkg`: Runs `bun add <package>` where `<package>` comes from `-PbunPkg=...`.
///
/// ## Notes
///   - This plugin currently wires the Bun executable lazily via [Provider]s to keep configuration time fast.
///   - Only a subset of Bun commands are implemented as tasks at the moment. I'll get to more eventually.
public class BunPlugin implements Plugin<Project> {
  /// Applies the plugin to a Gradle [Project].
  ///
  /// This method is responsible for:
  ///   - Creating the `bun` extension.
  ///   - Deriving the resolved Bun version and platform/system.
  ///   - Registering `bunSetup` and other Bun execution tasks.
  ///   - Computing the installation directory and executable location using lazy providers.
  ///
  /// @param project the project this plugin is being applied to
  @Override
  public void apply(final Project project) {
    // Extension used by build scripts to configure version/system
    final BunExtension extension = project.getExtensions().create("bun", BunExtension.class);

    // Resolve configured version with a safe default. "latest" is treated as a valid value by this plugin
    final Provider<String> version = extension.getVersion().map(String::trim).orElse("latest");

    // Resolve configured system with an auto-detect fallback
    final Provider<BunSystem> system = extension.getSystem().orElse(project.provider(BunSystem::detect));

    // Resolve SSL verification setting with a default of true
    final Provider<Boolean> disableSslVerification = extension.getDisableSslVerification().orElse(true);

    // Root folder under the project where Bun artifacts are stored
    final Directory bunRoot = project.getLayout().getProjectDirectory().dir(".gradle/bun");

    final Provider<File> bunExeProvider = system.zip(version, (s, v) -> {
      final String zipBase = s.zipName().endsWith(".zip") ? s.zipName().substring(0, s.zipName().length() - 4) : s.zipName();
      File installDir = new File(bunRoot.getAsFile(), BunHelpers.normalizeVersion(v) + File.separator + zipBase);
      return BunHelpers.findBunExecutable(installDir, s.exeName()).orElse(new File(installDir, s.exeName()));
    });

    /*
     * Below are all the tasks being registered.
     *
     * This is intentionally "flat" registration code because Gradle task registration is usually most readable
     * when tasks are declared in one place. If this grows significantly, consider extracting helpers or a small
     * task registry method per command.
     *
     * TODO: Support additional Bun operations (e.g., bun build, bun dev, bun lint, etc.)
     * TODO: Consider modeling a single generic "bun" Exec task with command-line parameters instead of many tasks.
     */
    // --- bun setup ---
    project.getTasks().register("bunSetup", BunSetupTask.class, task -> {
      task.setGroup("bun");
      task.setDescription("Downloads and installs the configured Bun runtime into .gradle/bun for this project.");
      task.getVersion().set(version);
      task.getSystem().set(system);
      task.getDisableSslVerification().set(disableSslVerification);
      task.getBunRootDir().set(bunRoot);
    });

    // --- bun install ---
    project.getTasks().register("bunInstall", BunTask.class, task -> {
      configureBaseTask(task, project, bunExeProvider, bunRoot, version, system);
      task.setDescription("Installs dependencies using Bun (runs 'bun install' in the project directory).");

      task.args("install");
    });

    // --- bun build ---
    project.getTasks().register("bunBuild", BunTask.class, task -> {
      configureBaseTask(task, project, bunExeProvider, bunRoot, version, system);
      task.setDescription("Builds project using Bun (runs 'bun run build' in the project directory).");
      task.dependsOn("bunInstall");

      task.args("run", "build");
    });

    // --- bun test ---
    project.getTasks().register("bunTest", BunTask.class, task -> {
      configureBaseTask(task, project, bunExeProvider, bunRoot, version, system);
      task.setDescription("Runs tests using Bun (runs 'bun test' in the project directory).");

      task.args("test");
    });

    // --- bun run <script> ---
    project.getTasks().register("bunRun", BunTask.class, task -> {
      configureBaseTask(task, project, bunExeProvider, bunRoot, version, system);
      task.setDescription("Runs a package.json script using Bun (requires -PbunScript=<name>).");

      Provider<String> script = project.getProviders().gradleProperty("bunScript");
      task.getBunArgs().add("run");
      task.getBunArgs().add(script.orElse(project.provider(() -> {
        throw new IllegalStateException("Provide -PbunScript=<name> for bunRun");
      })));
    });

    // --- bun add <package> ---
    project.getTasks().register("bunInstallPkg", BunTask.class, task -> {
      configureBaseTask(task, project, bunExeProvider, bunRoot, version, system);
      task.setDescription("Installs a package using Bun (runs 'bun add <package>' and requires -PbunPkg=<name>).");

      Provider<String> pkg = project.getProviders().gradleProperty("bunPkg");
      task.getBunArgs().add("add");
      task.getBunArgs().add(pkg.orElse(project.provider(() -> {
        throw new IllegalStateException("Provide -PbunPkg=<name> for bunInstallPkg");
      })));
    });
  }

  private void configureBaseTask(BunTask task, Project project, Provider<File> exe, Directory root, Provider<String> v, Provider<BunSystem> s) {
    BunExtension ext = project.getExtensions().getByType(BunExtension.class);

    task.setGroup("bun");
    task.dependsOn("bunSetup");
    task.getBunExecutableProperty().set(project.getLayout().getProjectDirectory().file(exe.map(File::getAbsolutePath)));
    task.getBunRootDir().set(root);
    task.getForceBun().convention(ext.getForceBun().getOrElse(false));
    task.getSystem().set(s);
    task.getVersion().set(v);
    task.getWorkingDirProperty().convention(ext.getWorkingDir().getOrElse(project.getLayout().getProjectDirectory()));
  }
}
