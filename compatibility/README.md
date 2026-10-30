# Reproduce the Minecraft API matrix

The production core compiles with `--release 17`; build/test tooling uses JDK 25. The default `check` task runs the current server simulations, a provider-free start and the separate 1.18.2 fixture. Neither fixture starts a live Minecraft server.

For the wider link check, use Python 3 and the pinned official Spigot snapshot manifest:

```powershell
python compatibility/fetch_apis.py
.\gradlew.bat compatibilityMatrixTest -PcompatibilityApiManifest=compatibility/cache/apis.json
```

`versions.json` pins 32 release APIs, their snapshot URL and SHA-256. The downloader stores only verified files under the ignored `compatibility/cache/`; the API JARs are never included in a release. An existing mismatching file fails rather than being silently accepted. Run the matrix with an explicit manifest; `check` also includes it when that property is present.

The checker inspects actual compiled core classes, fields, methods, constructors and class/interface invocation kinds. New display implementations are skipped only where that runtime lacks Display entities and the production availability guard selects the old implementation. Missing newer constants in compiler-generated enum switch tables are permitted; unguarded field references are checked strictly.

Selected native provider tests are opt-in, using your own JARs:

```powershell
.\gradlew.bat check -PbattlePassJar=C:/providers/BattlePass.jar
.\gradlew.bat check -PskilltreeJar=C:/providers/SkilltreePlugin.jar
.\gradlew.bat serverProvidersApiTest -PluckpermsApi=C:/providers/luckperms-api.jar -PauraSkillsApi=C:/providers/auraskills-api.jar -PadvancedCratesJar=C:/providers/AdvancedCrates.jar -PeconomyShopJar=C:/providers/EconomyShopGUI-Premium.jar
```

Provider tests validate API contracts and prepared native progress pipelines. They do not initialize complete premium plugins, databases, seasons or networks. Never add purchased provider JARs to Git or source/SDK bundles.
