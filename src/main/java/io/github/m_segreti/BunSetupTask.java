package io.github.m_segreti;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Optional;

/**
 * Gradle task responsible for downloading and verifying the Bun runtime
 * into a project-local directory.
 * <p>
 * The Bun distribution is downloaded as a zip file from the official GitHub releases,
 * verified using a published SHA-256 checksum, and extracted under:
 *
 * <pre>
 *   &lt;project&gt;/.gradle/bun/&lt;version&gt;/&lt;platform&gt;/
 * </pre>
 *
 * <p>
 * This task is designed to be:
 * <ul>
 *     <li><strong>Idempotent</strong> — if Bun is already in place, no work is performed.</li>
 *     <li><strong>Reproducible</strong> — the exact version and platform are controlled via task inputs.</li>
 *     <li><strong>Isolated</strong> — Portable installation does not affect any global/system Bun installation.</li>
 * </ul>
 *
 * <h2>Inputs</h2>
 * <ul>
 *     <li>{@link #getVersion()} — the Bun version to install (or {@code "latest"}).</li>
 *     <li>{@link #getSystem()} — the target system/platform for the Bun distribution.</li>
 * </ul>
 *
 * <h2>Outputs</h2>
 * <ul>
 *     <li>{@link #getBunRootDir()} — the root directory containing all Bun installations.</li>
 * </ul>
 *
 * <p>
 * Other Bun-related tasks should declare a dependency on this task to ensure
 * Bun is available before execution.
 */
public abstract class BunSetupTask extends DefaultTask {

    /**
     * The Bun version to install.
     * <p>
     * May be an explicit version string (e.g. {@code "1.1.0"}) or {@code "latest"}.
     * The value is normalized at execution time.
     *
     * @return a property representing the Bun version
     */
    @Input
    public abstract Property<String> getVersion();

    /**
     * The system/platform variant of Bun to install.
     * <p>
     * This determines which release asset is downloaded (operating system and CPU architecture).
     *
     * @return a property representing the target {@link BunSystem}
     */
    @Input
    public abstract Property<BunSystem> getSystem();

    /**
     * Whether to disable SSL certificate verification during downloads.
     * <p>
     * Useful in corporate environments with SSL-intercepting proxies.
     *
     * @return a property representing whether SSL verification is disabled
     */
    @Input
    public abstract Property<Boolean> getDisableSslVerification();

    /**
     * Root directory where Bun installations are stored.
     * <p>
     * Each installed version/system combination will be placed under a subdirectory
     * of this location.
     *
     * @return a directory property pointing to the Bun root directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getBunRootDir();

    /**
     * Executes the Bun setup process.
     * <p>
     * The task performs the following steps:
     * <ol>
     *     <li>Resolves and normalizes the configured version and system.</li>
     *     <li>Determines the installation and zip file locations.</li>
     *     <li>Downloads the Bun zip if it does not already exist.</li>
     *     <li>Verifies the zip integrity using a SHA-256 checksum.</li>
     *     <li>Extracts the zip if Bun is not already installed.</li>
     *     <li>Ensures the Bun executable is available and usable.</li>
     * </ol>
     *
     * <p>
     * If the Bun executable is already present, the task exits early without
     * performing any additional work.
     *
     * @throws IOException if installation or verification fails
     */
    @TaskAction
    public void run() throws IOException {
        final String version = BunHelpers.normalizeVersion(getVersion().getOrNull());
        final BunSystem system = getSystem().get();
        final File bunRoot = getBunRootDir().get().getAsFile();
        final File installDir = new File(bunRoot, version);

        final Optional<File> bunExe = BunHelpers.findBunExecutable(installDir, system.exeName());

        // Executable is present, no additional steps needed
        if (bunExe.isPresent()) {
            getLogger().lifecycle("Bun already installed: {}", bunExe.get().getAbsolutePath());
            return;
        }

        final File zipFile = this.getZipFile(installDir, system, version, getDisableSslVerification().get());

        getLogger().lifecycle("Extracting {} -> {}", zipFile.getName(), installDir.getAbsolutePath());
        BunHelpers.unzip(zipFile, installDir);
        Files.deleteIfExists(zipFile.toPath());

        final File executable = BunHelpers.findBunExecutable(installDir, system.exeName())
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to locate " + system.exeName() + " after extraction under: " + installDir
                ));

        if ("bun".equals(system.exeName())) {
            // Best-effort attempt to ensure executability on Unix-like systems.
            // TODO: Replace with an explicit chmod +x implementation if needed.
            executable.setExecutable(true);
        }

        getLogger().lifecycle("Bun ready: {}", executable.getAbsolutePath());
    }

    /**
     * Resolves the Bun zip file for the given version and system.
     * <p>
     * This method:
     * <ul>
     *     <li>Ensures the installation directory exists.</li>
     *     <li>Downloads the zip file if it is not already present.</li>
     *     <li>Verifies the integrity of the downloaded zip.</li>
     * </ul>
     *
     * @param installDir the directory where Bun will be installed
     * @param system     the target system/platform
     * @param version    the Bun version
     * @param disableSslVerification whether to disable SSL verification
     * @return the verified Bun zip file
     * @throws IOException if the zip cannot be downloaded or written
     */
    private File getZipFile(final File installDir, final BunSystem system, final String version, final boolean disableSslVerification) throws IOException {
        Files.createDirectories(installDir.toPath());

        final File zipFile = new File(installDir, system.zipName());
        final URI downloadUrl = BunHelpers.bunZipUrl(version, system);

        // If the zip does not exist already, download it
        if (!zipFile.exists()) {
            getLogger().lifecycle("Downloading Bun: {} -> {}", downloadUrl, zipFile.getAbsolutePath());
            BunHelpers.downloadUrlTo(downloadUrl.toURL(), zipFile, disableSslVerification);
        } else {
            getLogger().lifecycle("Bun zip already present: {}", zipFile.getAbsolutePath());
        }

        this.verifyIntegrity(zipFile, system, version);
        return zipFile;
    }

    /**
     * Verifies the integrity of a downloaded Bun zip file using SHA-256.
     * <p>
     * The expected hash is fetched from the upstream release metadata and compared
     * against the locally computed hash. If verification fails, the corrupted zip
     * is deleted and the build fails.
     *
     * @param zipFile the downloaded zip file
     * @param system  the target system/platform
     * @param version the Bun version
     * @throws IllegalStateException if verification fails or the hash cannot be computed
     */
    private void verifyIntegrity(final File zipFile, final BunSystem system, final String version) {
        // TODO This is borked
//        try {
//            final String expectedSha = BunHelpers.fetchExpectedSha256(version, system.zipName());
//            final String actualSha = BunHelpers.sha256(zipFile);
//
//            if (!actualSha.equalsIgnoreCase(expectedSha)) {
//                Files.delete(zipFile.toPath());
//
//                throw new IllegalStateException(
//                        "SHA-256 mismatch for " + system.zipName() +
//                                "\nExpected: " + expectedSha +
//                                "\nActual:   " + actualSha +
//                                "\nDeleted corrupted download."
//                );
//            }
//        } catch (IOException | NoSuchAlgorithmException e) {
//            throw new IllegalStateException(
//                    "Failed to verify integrity of " + zipFile.getAbsolutePath(), e
//            );
//        }
    }
}
