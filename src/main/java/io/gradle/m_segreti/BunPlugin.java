package io.gradle.m_segreti;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;

/**
 * Gradle plugin that downloads and runs the <a href="https://bun.sh/">Bun</a> runtime in a local project.
 * <p>
 * This plugin:
 * <ul>
 *     <li>Creates a {@code bun} extension ({@link BunExtension}) so builds can configure the Bun {@code version} and {@code system}.</li>
 *     <li>Registers a {@code bunSetup} task that downloads/unpacks Bun into {@code <project>/.gradle/bun/...}.</li>
 *     <li>Registers a small set of convenience tasks ({@code bunInstall}, {@code bunTest}, {@code bunRun}, {@code bunInstallPkg})
 *     that execute Bun commands using the installed executable.</li>
 * </ul>
 * <p>
 * Installation is isolated per project and per version/system combination to avoid interfering with any global Bun install.
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * bun {
 *   version = "1.1.0" // defaults to "latest"
 *   system = BunSystem.WINDOWS_X64 // defaults to auto-detect
 * }
 * }</pre>
 *
 * <h2>Tasks</h2>
 * <ul>
 *     <li>{@code bunSetup}: Downloads and installs Bun (dependency of all Bun execution tasks).</li>
 *     <li>{@code bunInstall}: Runs {@code bun install} in the project directory.</li>
 *     <li>{@code bunTest}: Runs {@code bun test} in the project directory.</li>
 *     <li>{@code bunRun}: Runs {@code bun run <script>} where {@code <script>} comes from {@code -PbunScript=...}.</li>
 *     <li>{@code bunInstallPkg}: Runs {@code bun add <package>} where {@code <package>} comes from {@code -PbunPkg=...}.</li>
 * </ul>
 *
 * <h2>Notes</h2>
 * <ul>
 *     <li>This plugin currently wires the Bun executable lazily via {@link Provider}s to keep configuration time fast.</li>
 *     <li>Only a subset of Bun commands are implemented as tasks at the moment. I'll get to more eventually.</li>
 * </ul>
 */
public class BunPlugin implements Plugin<Project> {

    /**
     * Applies the plugin to a Gradle {@link Project}.
     * <p>
     * This method is responsible for:
     * <ul>
     *     <li>Creating the {@code bun} extension.</li>
     *     <li>Deriving the resolved Bun version and platform/system.</li>
     *     <li>Registering {@code bunSetup} and other Bun execution tasks.</li>
     *     <li>Computing the installation directory and executable location using lazy providers.</li>
     * </ul>
     *
     * @param project the project this plugin is being applied to
     */
    @Override
    public void apply(final Project project) {
        // Extension used by build scripts to configure version/system
        final BunExtension extension = project.getExtensions().create("bun", BunExtension.class);

        // Resolve configured version with a safe default. "latest" is treated as a valid value by this plugin
        final Provider<String> version = extension.getVersion().map(String::trim).orElse("latest");

        // Resolve configured system with an auto-detect fallback
        final Provider<BunSystem> system = extension.getSystem().orElse(project.provider(BunSystem::detect));

        // Root folder under the project where Bun artifacts are stored
        final File bunRoot = project.getProjectDir().toPath().resolve(".gradle/bun").toFile();

        // Task that ensures Bun is installed locally
        final TaskProvider<BunSetupTask> bunSetup = this.registerBunSetup(project, system, version);

        // Computes the Bun installation directory
        final Provider<File> installDirProvider = project.provider(() -> {
            final BunSystem bun = system.get();
            final String zipBase = bun.zipName().endsWith(".zip")
                    ? bun.zipName().substring(0, bun.zipName().length() - 4)
                    : bun.zipName();

            return new File(bunRoot, BunHelpers.normalizeVersion(version.get()) + File.separator + zipBase);
        });

        // Computes the Bun executable path under the installation directory
        final Provider<File> bunExeProvider = project.provider(() -> {
            final File installDir = installDirProvider.get();
            final BunSystem s = system.get();

            return BunHelpers.findBunExecutable(installDir, s.exeName())
                    .orElseThrow(() -> new IllegalStateException(
                            "Bun executable not found under " + installDir.getAbsolutePath() + " (run bunSetup)"
                    ));
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

        // --- bun install ---
        project.getTasks().register("bunInstall", BunTask.class, task -> {
            task.setGroup("bun");
            task.setDescription("Installs dependencies using Bun (runs 'bun install' in the project directory).");
            task.dependsOn(bunSetup);
            task.setWorkingDir(project.getProjectDir());

            // Bind the executable right before execution so it resolves after bunSetup has completed.
            task.doFirst(_ -> task.setBunExecutable(bunExeProvider.get()));

            task.args("install");
        });

        // --- bun test ---
        project.getTasks().register("bunTest", BunTask.class, task -> {
            task.setGroup("bun");
            task.setDescription("Runs tests using Bun (runs 'bun test' in the project directory).");
            task.dependsOn(bunSetup);
            task.setWorkingDir(project.getProjectDir());
            task.doFirst(_ -> task.setBunExecutable(bunExeProvider.get()));
            task.args("test");
        });

        // --- bun run <script> ---
        project.getTasks().register("bunRun", BunTask.class, task -> {
            task.setGroup("bun");
            task.setDescription("Runs a package.json script using Bun (requires -PbunScript=<name>).");
            task.dependsOn(bunSetup);
            task.setWorkingDir(project.getProjectDir());

            task.doFirst(_ -> {
                task.setBunExecutable(bunExeProvider.get());

                // Script name passed from Gradle via -PbunScript=<name>
                final Object prop = project.findProperty("bunScript");
                if (prop == null) {
                    throw new IllegalStateException("Provide -PbunScript=<name> for bunRun");
                }

                task.args("run", prop.toString());
            });
        });

        // --- bun add <package> ---
        project.getTasks().register("bunInstallPkg", BunTask.class, task -> {
            task.setGroup("bun");
            task.setDescription("Installs a package using Bun (runs 'bun add <package>' and requires -PbunPkg=<name>).");
            task.dependsOn(bunSetup);
            task.setWorkingDir(project.getProjectDir());

            task.doFirst(_ -> {
                task.setBunExecutable(bunExeProvider.get());

                // Package name passed from Gradle via -PbunPkg=<name>
                final Object prop = project.findProperty("bunPkg");
                if (prop == null) {
                    throw new IllegalStateException("Provide -PbunPkg=<name> for bunInstallPkg");
                }

                task.args("add", prop.toString());
            });
        });
    }

    private TaskProvider<BunSetupTask> registerBunSetup(final Project project,
                                                        final Provider<BunSystem> system,
                                                        final Provider<String> version) {
        return project.getTasks().register("bunSetup", BunSetupTask.class, task -> {
            task.setGroup("bun");
            task.setDescription("Downloads and installs the configured Bun runtime into .gradle/bun for this project.");
            task.getVersion().set(version);
            task.getSystem().set(system);
            task.getBunRootDir().set(project.getLayout().getProjectDirectory().dir(".gradle/bun"));
        });
    }
}
