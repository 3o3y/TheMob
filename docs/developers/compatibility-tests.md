# Reproduce compatibility checks

The source project contains `compatibility/README.md`, a pinned manifest of 32 official Spigot API snapshots and a downloader which verifies SHA-256 before writing a local test manifest. Run the matrix task with `-PcompatibilityApiManifest=<manifest>`. Default `check` includes modern regression tests, an optional-provider-free start and the separate 1.18.2 fixture. Provider tests are enabled only with explicitly supplied provider JAR properties. See [validation](../reference/VALIDATION.md) for current results.
