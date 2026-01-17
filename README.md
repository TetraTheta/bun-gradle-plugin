# Bun Gradle Plugin

A Gradle plugin that install a portable installation of the Bun JavaScript runtime.

This plugin is designed for build automation and CI usage, avoiding any
dependency on a global/system installations of Bun.

---

## Features

- Downloads Bun directly from official GitHub releases
- Verifies downloads using SHA-256 checksums
- Extracts Bun locally under .gradle/bun/
- Idempotent setup (safe to run multiple times)
- Auto-detects OS and CPU architecture
- Configurable Bun version and platform
- CI-friendly and reproducible builds

---

## Installation

In your project's `build.gradle` reference the plugin:

```
plugins {
    id "net.nexus.bun" version "x.y.z"
}
```

---

## Configuration

Configure Bun using the bun extension:

```
bun {
    version = "1.1.0"   // Optional, defaults to "latest"
    system = BunSystem.LINUX_X64  // Optional, auto-detected by default
}
```

Defaults are intentionally applied during plugin wiring so users may
explicitly leave values unset.

---

## Tasks

All tasks are executed via Gradle.
Use either the Gradlew wrapper `./gradlew {task}` <--- Recommended
Or, use your local installation `gradle {task}`

| Task                              | Bun                     | Description                                                                                                                 |
|-----------------------------------|-------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `bunSetup`                        | n/a                     | Downloads and extracts the bun binaries                                                                                     |
| `bunInstall`                      | `bun install`           | Standard installation of all depdencies from `package.json`                                                                 |
| `bunInstallPkg -PbunPkg={package} | `bun install {package}` | Executes the standard Bun install for an individual package                                                                 |
| `bunTest`                         | `bun test`              | Executes the projects test suite                                                                                            |
| `bunRun -PbunScript={script}`     | `bun run dev`           | Executes Bun's standard run command and allows an argument for the name of the script found in your projects `package.json` |

## Supported Platforms

Automatically detected:

- Windows x64
- macOS (Intel & Apple Silicon)
- Linux x64 (glibc)
- Linux ARM64 (glibc)

Manually configurable (via BunSystem):

- Baseline builds
- musl-based Linux (e.g. Alpine)

---

## Design Philosophy

- No global state - Bun is installed per project and isolated
- Lazy configuration - No filesystem or network work at configuration time
- Explicit verification - corrupted downloads are detected and deleted
- Gradle-native - uses Providers, task inputs/outputs, and idiomatic APIs

---

## Roadmap

- Additional Bun commands (build, dev, etc.)
- Configuration cache optimizations
- Proper Unix permission handling (chmod +x)

---

## License

This project is licensed under the MIT License.
See the LICENSE file for details.
