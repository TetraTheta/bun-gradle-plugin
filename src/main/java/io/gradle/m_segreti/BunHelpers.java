package io.gradle.m_segreti;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utility helpers used by the Bun Gradle plugin for downloading, verifying, extracting,
 * and locating the Bun runtime.
 * <p>
 * Responsibilities include:
 * <ul>
 *     <li>Normalizing version strings (including supporting {@code "latest"}).</li>
 *     <li>Building the correct GitHub release asset URL for a platform-specific Bun zip.</li>
 *     <li>Downloading files and computing SHA-256 hashes.</li>
 *     <li>Discovering an expected SHA-256 from the upstream release metadata page.</li>
 *     <li>Unzipping a Bun distribution into a destination directory.</li>
 *     <li>Locating the Bun executable under an installation directory.</li>
 * </ul>
 *
 * <p><strong>Note:</strong> This is a pure utility class and is not meant to be instantiated.</p>
 */
public class BunHelpers {
    private static final String LATEST = "latest";

    private BunHelpers() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Normalizes a Bun version string.
     * <p>
     * If the input is {@code null} or blank after trimming, {@code "latest"} is returned.
     * Otherwise, the trimmed version string is returned.
     *
     * @param version the configured version (may be {@code null} or blank)
     * @return a non-blank version string, or {@code "latest"} when not specified
     */
    public static String normalizeVersion(final String version) {
        if (version == null) {
            return LATEST;
        }

        final String cleaned = version.trim();
        return cleaned.isEmpty() ? LATEST : cleaned;
    }

    /**
     * Removes a trailing {@code ".zip"} suffix from a zip file name, if present.
     *
     * @param zipName the zip file name (e.g., {@code "bun-linux-x64.zip"})
     * @return the base name without {@code ".zip"} when present; otherwise the original value
     */
    public static String stripZip(final String zipName) {
        return zipName.endsWith(".zip") ? zipName.substring(0, zipName.length() - 4) : zipName;
    }

    /**
     * Builds the GitHub download URI for the Bun release zip for the given version and system.
     * <p>
     * For {@code "latest"}, this points to the {@code releases/latest/download} endpoint.
     * For explicit versions, this points to {@code releases/download/bun-v<version>/...}.
     *
     * @param version the Bun version (use {@code "latest"} for newest)
     * @param sys     the target system/platform descriptor used to choose the correct asset name
     * @return the download URI for the Bun zip asset
     */
    public static URI bunZipUrl(final String version, final BunSystem sys) {
        final String url = LATEST.equals(version)
                ? "https://github.com/oven-sh/bun/releases/latest/download/" + sys.zipName()
                : "https://github.com/oven-sh/bun/releases/download/bun-v" + version + "/" + sys.zipName();

        return URI.create(url);
    }

    /**
     * Computes the SHA-256 hash of a file and returns it as a lowercase hexadecimal string.
     *
     * @param file the file to hash
     * @return SHA-256 digest as a lowercase hex string
     * @throws NoSuchAlgorithmException if SHA-256 is not available in the current JVM
     * @throws IOException              if the file cannot be read
     */
    public static String sha256(final File file) throws NoSuchAlgorithmException, IOException {
        final MessageDigest cryptographicHash = MessageDigest.getInstance("SHA-256");

        try (final InputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;

            while ((read = inputStream.read(buffer)) >= 0) {
                cryptographicHash.update(buffer, 0, read);
            }
        }

        final byte[] digest = cryptographicHash.digest();
        final StringBuilder sha = new StringBuilder(digest.length * 2);

        for (final byte b : digest) {
            sha.append(String.format("%02x", b));
        }

        return sha.toString();
    }

    /**
     * Downloads the content at {@code url} and writes it to {@code destination}.
     * <p>
     * This method ensures the destination parent directory exists before writing.
     *
     * @param url         the URL to download
     * @param destination the destination file to write (will be overwritten if it exists)
     * @throws IOException if the download fails or the destination cannot be written
     */
    public static void downloadUrlTo(final URL url, final File destination) throws IOException {
        Files.createDirectories(destination.getParentFile().toPath());
        writeStream(url.openStream(), destination);
    }

    /**
     * Fetches the expected SHA-256 for a given Bun asset zip from the release metadata page.
     * <p>
     * The SHA values are published on the {@code bun-releases-for-updater} repository release pages
     * in a {@code sha256: <hex>} format. This method scrapes the HTML for the given asset name
     * and extracts the first SHA-256 match.
     *
     * @param version      the Bun version (use {@code "latest"} for newest)
     * @param assetZipName the exact asset file name to locate (e.g., {@code bun-linux-x64.zip})
     * @return the expected SHA-256 digest as a lowercase hex string
     * @throws IOException           if the release page cannot be downloaded
     * @throws IllegalStateException if the SHA-256 cannot be found for the given asset on the page
     */
    public static String fetchExpectedSha256(final String version, final String assetZipName) throws IOException {
        final String page = LATEST.equals(version)
                ? "https://github.com/Jarred-Sumner/bun-releases-for-updater/releases/latest"
                : "https://github.com/Jarred-Sumner/bun-releases-for-updater/releases/tag/bun-v" + version;

        final String html = readAll(URI.create(page).toURL());
        final Pattern shaPattern = Pattern.compile(Pattern.quote(assetZipName) + "[\\s\\S]{0,400}?sha256:\\s*([0-9a-fA-F]{64})");
        final Matcher shaMatcher = shaPattern.matcher(html);

        if (!shaMatcher.find()) {
            throw new IllegalStateException("Could not find sha256 for " + assetZipName + " on " + page);
        }

        return shaMatcher.group(1).toLowerCase();
    }

    /**
     * Extracts a zip file into the given destination directory.
     * <p>
     * Directory entries are created as directories, file entries are written to disk.
     * Parent directories are created as needed.
     *
     * @param zip         the zip file to extract
     * @param destination the destination directory where the zip contents will be written
     * @throws IOException if the zip cannot be read or any entry cannot be written
     */
    public static void unzip(final File zip, final File destination) throws IOException {
        try (final ZipFile zipFile = new ZipFile(zip)) {
            final Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                final ZipEntry contents = entries.nextElement();
                final File output = new File(destination, contents.getName());

                // If the content is a directory, make it and move on
                if (contents.isDirectory()) {
                    Files.createDirectories(output.toPath());
                    continue;
                }

                // Ensure all parent directories are present before extracting a file
                Files.createDirectories(output.getParentFile().toPath());

                // Extraction
                writeStream(zipFile.getInputStream(contents), output);
            }
        }
    }

    /**
     * Recursively searches for a Bun executable file under the provided root directory.
     * <p>
     * The search is case-insensitive to better support Windows file systems, and returns the
     * first match found during a depth-first traversal.
     *
     * @param root       the directory to search
     * @param executable the executable file name to find (e.g., {@code "bun"} or {@code "bun.exe"})
     * @return an {@link Optional} containing the first matching file, or empty if none is found
     */
    public static Optional<File> findBunExecutable(final File root, final String executable) {
        final File[] files = root.listFiles();

        if (files == null) {
            return Optional.empty();
        }

        for (File file : files) {
            if (file.isDirectory()) {
                final Optional<File> found = findBunExecutable(file, executable);

                if (found.isPresent()) {
                    return found;
                }
            } else if (file.isFile() && file.getName().equalsIgnoreCase(executable)) {
                return Optional.of(file);
            }
        }

        return Optional.empty();
    }

    private static void writeStream(final InputStream source, final File destination) throws IOException {
        try (final OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(destination))) {
            source.transferTo(outputStream);
        }
    }

    private static String readAll(final URL url) throws IOException {
        try (InputStream inputStream = url.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
