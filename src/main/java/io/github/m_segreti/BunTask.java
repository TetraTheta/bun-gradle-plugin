package io.github.m_segreti;

import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Internal;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gradle {@link Exec} task specialized for invoking the Bun executable.
 * <p>
 * This task acts as a thin wrapper around {@link Exec} that:
 * <ul>
 *     <li>Defers resolution of the Bun executable until execution time.</li>
 *     <li>Accumulates Bun command-line arguments independently of Gradle’s built-in argument handling.</li>
 *     <li>Provides a clear failure mode if Bun has not been set up prior to execution.</li>
 * </ul>
 *
 * <p>
 * Typical usage is internal to the plugin. Tasks such as {@code bunInstall},
 * {@code bunTest}, and {@code bunRun} configure an instance of this task by:
 * <ol>
 *     <li>Declaring a dependency on {@code bunSetup}.</li>
 *     <li>Setting the Bun executable during {@code doFirst}.</li>
 *     <li>Providing Bun-specific arguments via {@link #args(String...)}.</li>
 * </ol>
 *
 * <p>
 * This design keeps configuration-time logic minimal and ensures the Bun
 * executable path is resolved only after installation has completed.
 */
public abstract class BunTask extends Exec {

    /**
     * Collected command-line arguments to pass to Bun.
     * <p>
     * Arguments are accumulated during task configuration and applied
     * immediately before execution.
     */
    private final List<String> bunArgs = new ArrayList<>();

    /**
     * Path to the Bun executable to invoke.
     * <p>
     * This is intentionally not modeled as a task input because it is
     * resolved dynamically at execution time after {@code bunSetup}
     * has run.
     */
    private File bunExecutable;

    /**
     * Returns the Bun executable for this task.
     * <p>
     * This property is marked {@link Internal} so it does not participate
     * in Gradle's up-to-date checks or task graph inputs.
     *
     * @return the Bun executable file, or {@code null} if not yet set
     */
    @Internal
    public File getBunExecutable() {
        return bunExecutable;
    }

    /**
     * Sets the Bun executable to be used when this task executes.
     * <p>
     * This is typically called from a {@code doFirst} action to ensure
     * the executable is resolved after {@code bunSetup} completes.
     *
     * @param bunExecutable the Bun executable file
     */
    public void setBunExecutable(File bunExecutable) {
        this.bunExecutable = bunExecutable;
    }

    /**
     * Adds one or more command-line arguments to be passed to Bun.
     * <p>
     * Arguments are appended in the order provided and are not modified
     * or validated by this task.
     *
     * @param args the arguments to pass to Bun
     */
    public void args(String... args) {
        Collections.addAll(bunArgs, args);
    }

    /**
     * Sets the command-line arguments to be passed to Bun.
     * <p>
     * This overrides the parent {@link Exec#setArgs(List)} to ensure
     * arguments are captured in the bunArgs list.
     *
     * @param args the arguments to pass to Bun
     */
    @Override
    public Exec setArgs(List<String> args) {
        bunArgs.clear();

        if (args != null) {
            bunArgs.addAll(args);
        }

        return this;
    }

    /**
     * Executes the Bun command.
     * <p>
     * This method:
     * <ol>
     *     <li>Verifies that the Bun executable has been set.</li>
     *     <li>Configures the underlying {@link Exec} task with the executable path.</li>
     *     <li>Applies the collected Bun arguments.</li>
     *     <li>Delegates execution to {@link Exec#exec()}.</li>
     * </ol>
     *
     * @throws IllegalStateException if the Bun executable has not been set
     */
    @Override
    protected void exec() {
        if (bunExecutable == null) {
            final BunSetupTask setup = (BunSetupTask) getProject().getTasks().getByName("bunSetup");
            final String version = BunHelpers.normalizeVersion(setup.getVersion().getOrNull());
            final BunSystem system = setup.getSystem().get();
            final File bunRoot = setup.getBunRootDir().get().getAsFile();
            final File installDir = new File(bunRoot, version + File.separator + BunHelpers.stripZip(system.zipName()));

            bunExecutable = BunHelpers.findBunExecutable(installDir, system.exeName()).orElse(null);

            if (bunExecutable == null) {
                throw new IllegalStateException("bunExecutable not set (did you dependOn bunSetup?)");
            }
        }

        getLogger().lifecycle("Bun executable: [{}]", bunExecutable.getAbsolutePath());
        getLogger().lifecycle("Bun arguments : {}", bunArgs);

        setExecutable(bunExecutable.getAbsolutePath());
        super.setArgs(bunArgs);
        super.exec();
    }
}
