import java.security.MessageDigest
import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins { java }

group = "org.plugin"
version = "1.19.1"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:26.3-R0.1-SNAPSHOT")
    compileOnly(files("libraries/BetterHud-bukkit-2.2.0-SNAPSHOT-449.jar"))
    compileOnly("net.kyori:adventure-api:5.2.0")
    compileOnly(files("libraries/PlaceholderAPI-2.12.3.jar"))
    testImplementation(files("libraries/PlaceholderAPI-2.12.3.jar"))
    implementation(files("libraries/bstats-relocated-3.0.2.jar"))
    runtimeOnly("org.xerial:sqlite-jdbc:3.47.2.0") { exclude(group = "org.slf4j") }
    testImplementation("io.papermc.paper:paper-api:26.3.build.218-stable")
    testImplementation(files("libraries/BetterHud-bukkit-2.2.0-SNAPSHOT-449.jar"))
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.3:4.118.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // BattlePass declares these as server libraries; only the native progress test uses them.
    testRuntimeOnly("com.j256.ormlite:ormlite-core:6.1")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}
tasks.compileJava { options.release.set(17) }
// Compile against modern optional providers on the JDK 25 build host. Their classes are never
// shaded into the Java 17 core and must be loaded only when that provider is actually available.
configurations.compileClasspath {
    attributes.attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,25)
}
tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
tasks.test {
    useJUnitPlatform()
    exclude("**/BattlePassRealApiTest.class")
    exclude("**/SkilltreeHealthCompatibilityTest.class")
    exclude("**/ServerProvidersRealApiTest.class")
    exclude("**/CompatibilityMatrixTest.class")
    exclude("**/NoSqliteBuildTest.class")
    doFirst { systemProperty("spigot.compat.classpath", sourceSets.main.get().compileClasspath.asPath) }
    addTestListener(object : org.gradle.api.tasks.testing.TestListener {
        override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) {}
        override fun beforeTest(test: org.gradle.api.tasks.testing.TestDescriptor) {}
        override fun afterTest(test: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {}
        override fun afterSuite(suite: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
            if (suite.parent == null && result.skippedTestCount > 0) {
                throw GradleException("${result.skippedTestCount} tests skipped; release verification requires zero skips.")
            }
        }
    })
}
tasks.jar {
    archiveBaseName.set("TheMob")
    archiveClassifier.set("MC1.18-26.3")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/maven/**")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from("API-LICENSE.txt") { into("META-INF"); rename { "themob-api-LICENSE.txt" } }
}

// Keep the existing all-in-one release and offer a separate file-storage-only artifact.
val prepareNoSqliteProfile = tasks.register("prepareNoSqliteProfile") {
    val profile = layout.buildDirectory.file("nosqlite-profile/themob-build.properties")
    outputs.file(profile)
    doLast {
        profile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("sqlite.enabled=false\n", Charsets.UTF_8)
        }
    }
}
val jarWithoutSqlite = tasks.register<Jar>("jarWithoutSqlite") {
    dependsOn(tasks.classes, prepareNoSqliteProfile)
    description = "Builds TheMob with file cooldown storage and without the SQLite driver."
    archiveFileName.set("TheMob-${project.version}-MC1.18-26.3-NoSQLite.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().filter { !it.name.startsWith("sqlite-jdbc-") }
        .map { if (it.isDirectory) it else zipTree(it) })
    from(prepareNoSqliteProfile)
    from("API-LICENSE.txt") { into("META-INF"); rename { "themob-api-LICENSE.txt" } }
    exclude("org/sqlite/**", "META-INF/services/java.sql.Driver")
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/maven/**")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val noSqliteTest = tasks.register<Test>("noSqliteTest") {
    dependsOn(tasks.testClasses, jarWithoutSqlite)
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    val mainOutput = sourceSets.main.get().output.files
    classpath = files(jarWithoutSqlite) + sourceSets.test.get().runtimeClasspath.filter {
        it !in mainOutput && !it.name.startsWith("sqlite-jdbc-") &&
            !it.name.startsWith("BetterHud-") && !it.name.startsWith("PlaceholderAPI-")
    }
    systemProperty("standalone.nooptional", "true")
    systemProperty("themob.nosqlite.jar", jarWithoutSqlite.get().archiveFile.get().asFile.absolutePath)
    include("**/NoSqliteBuildTest.class", "**/OptionalProvidersAbsentTest.class")
}

// Ant directory scans omit these two names by default. Stage them with ordinary names,
// then rename inside the ZIP so the exported project really includes its Git settings.
val prepareGitMetadata=tasks.register("prepareGitMetadata") {
    inputs.files(".gitignore", ".gitattributes")
    outputs.dir(layout.buildDirectory.dir("git-metadata"))
    doLast {
        val output=layout.buildDirectory.dir("git-metadata").get().asFile
        output.mkdirs()
        for(name in listOf("gitignore", "gitattributes")) file(".$name").copyTo(output.resolve("$name.txt"),overwrite=true)
    }
}
val sourceBundle = tasks.register<Zip>("sourceBundle") {
    dependsOn(prepareGitMetadata)
    description = "Packs the restored IntelliJ/Gradle project and regression tests."
    archiveFileName.set("TheMob-${project.version}-MC1.18-26.3-Quellprojekt.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(prepareGitMetadata) { rename { "."+it.removeSuffix(".txt") } }
    from(projectDir) {
        exclude("compatibility/cache/**", "**/build/**", "**/.gradle/**", "**/.idea/**")
        include("ADMIN-MENUE.md", "INTEGRATIONEN-20.md", "KOMPATIBILITAET.md", "GIT-START.md", ".gitattributes", ".gitbook.yaml", "docs/**", "compatibility/**")
        include("LICENSE.md")
        include("API-LICENSE.txt", "DEVELOPER-GUIDE.md", "ADMIN-VERBINDUNGEN.md")
        include("SPIELERWERTE.md")
        include("FAEHIGKEITEN.md")
        include("ZOMBIE-KOENIG.md")
        include("src/**", "gradle/**", "libraries/**", "client-pack/**", "client-pack-coinoverlay/**", "gradlew", "gradlew.bat", "build.gradle.kts", "settings.gradle.kts", "build.ps1", ".gitignore", "README.md", "UPDATE.md", "CHANGELOG.md", "VALIDATION.md", "KOMPASS.md", "API.md", "INTEGRATIONEN.md", "BATTLEPASS.md", "MOBS-UND-ITEMS.md", "PERFORMANCE.md", "DUNGEONS.md", "DUNGEON-API.md", "AUSGANGSALTAR.md", "examples/**", "CLIENT-PACK-HERKUNFT.md")
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val clientPack = tasks.register<Zip>("clientPack") {
    from("client-pack")
    archiveFileName.set("TheMob-Compass-26.3.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val combinedClientPack = tasks.register<Zip>("combinedClientPack") {
    from("client-pack-coinoverlay")
    archiveFileName.set("TheMob-CoinOverlay-26.3.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val clientPackHash = tasks.register("clientPackHash") {
    dependsOn(clientPack, combinedClientPack)
    val archives = listOf(clientPack, combinedClientPack).map { it.flatMap { task -> task.archiveFile } }
    inputs.files(archives)
    outputs.files(archives.map { it.map { file -> file.asFile.resolveSibling(file.asFile.name + ".sha1") } })
    doLast {
        for (archive in archives) {
            val file = archive.get().asFile
            val hash = MessageDigest.getInstance("SHA-1").digest(file.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            file.resolveSibling(file.name + ".sha1").writeText(hash + "\n")
        }
    }
}
val serverBundle = tasks.register<Zip>("serverBundle") {
    dependsOn(tasks.jar)
    archiveFileName.set("TheMob-${project.version}-MC1.18-26.3-Serverpaket.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(tasks.jar) { into("plugins"); rename { "TheMob.jar" } }
    from("ZOMBIE-KOENIG.md")
    from("SPIELERWERTE.md")
    from("ADMIN-MENUE.md", "INTEGRATIONEN-20.md")
    from("src/main/resources/zombie-boss.yml") { into("examples/bosses") }
    from("UPDATE.md", "CHANGELOG.md", "VALIDATION.md", "KOMPASS.md", "API.md", "INTEGRATIONEN.md", "BATTLEPASS.md", "MOBS-UND-ITEMS.md", "PERFORMANCE.md", "DUNGEONS.md", "DUNGEON-API.md", "AUSGANGSALTAR.md")
    from("examples") { into("examples") }
    from("src/main/resources") {
        include("battlepass.yml", "battlepass-examples.yml", "battlepass-combat-examples.yml", "battlepass-dungeons.yml")
        into("examples/battlepass")
    }
    from("src/main/resources") { include("catalog.yml", "equipment.yml"); into("examples/rpg") }
    from("src/main/resources") { include("dungeon.yml"); into("examples/dungeons") }
    from(layout.buildDirectory.dir("prepared-client")) {
        include("CoinOverlay-HUD-26.3-Kompass-oben.zip", "CoinOverlay-HUD-26.3-Kompass-oben.zip.sha1", "CoinOverlay-HUD-26.3-Kompass-oben.zip.sha256", "CoinOverlay-HUD-26.3-Kompass-oben.report.json")
        into("client-resource-pack")
    }
    // Runtime providers are installed separately in versions matching the server.
    from("KOMPATIBILITAET.md")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val apiJar = tasks.register<Jar>("apiJar") {
    dependsOn(tasks.classes)
    archiveFileName.set("TheMob-API-1.3.1.jar")
    from(sourceSets.main.get().output) {
        include("org/plugin/theMob/api/**")
        exclude("org/plugin/theMob/api/internal/**")
        exclude("org/plugin/theMob/api/dungeon/**")
    }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from("API-LICENSE.txt") { into("META-INF"); rename { "LICENSE.txt" } }
}
val dungeonApiJar = tasks.register<Jar>("dungeonApiJar") {
    dependsOn(tasks.classes)
    archiveFileName.set("TheMob-Dungeon-API-1.3.1.jar")
    from(sourceSets.main.get().output) { include("org/plugin/theMob/api/dungeon/**") }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from("API-LICENSE.txt") { into("META-INF"); rename { "LICENSE.txt" } }
}
serverBundle.configure { from(dungeonApiJar) { into("developer") } }
val compileApiExample = tasks.register<JavaCompile>("compileApiExample") {
    dependsOn(apiJar, dungeonApiJar)
    options.release.set(17)
    source(fileTree("examples/addon/src/main/java") { include("**/*.java") })
    classpath = configurations.compileClasspath.get() + files(apiJar.flatMap { it.archiveFile }, dungeonApiJar.flatMap { it.archiveFile })
    destinationDirectory.set(layout.buildDirectory.dir("api-example-classes"))
}

val apiSourcesJar = tasks.register<Jar>("apiSourcesJar") {
    archiveFileName.set("TheMob-API-1.3.1-sources.jar")
    from("src/main/java") { include("org/plugin/theMob/api/**"); exclude("org/plugin/theMob/api/internal/**", "org/plugin/theMob/api/dungeon/**") }
    from("API-LICENSE.txt") { into("META-INF"); rename { "LICENSE.txt" } }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val dungeonApiSourcesJar = tasks.register<Jar>("dungeonApiSourcesJar") {
    archiveFileName.set("TheMob-Dungeon-API-1.3.1-sources.jar")
    from("src/main/java") { include("org/plugin/theMob/api/dungeon/**") }
    from("API-LICENSE.txt") { into("META-INF"); rename { "LICENSE.txt" } }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val publicApiJavadoc = tasks.register<Javadoc>("publicApiJavadoc") {
    source(fileTree("src/main/java") { include("org/plugin/theMob/api/**"); exclude("org/plugin/theMob/api/internal/**") })
    classpath = configurations.compileClasspath.get()
    destinationDir = layout.buildDirectory.dir("docs/public-api").get().asFile
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        charSet = "UTF-8"
        docEncoding = "UTF-8"
        windowTitle = "TheMob public API 1.3.1"
        docTitle = "TheMob public API 1.3.1 – Java 17+ / Minecraft 1.18–26.3"
        addBooleanOption("Xdoclint:all,-missing", true)
    }
}
val apiJavadocJar = tasks.register<Jar>("apiJavadocJar") {
    dependsOn(publicApiJavadoc)
    archiveFileName.set("TheMob-API-1.3.1-javadoc.jar")
    from(publicApiJavadoc.map { it.destinationDir })
    from("API-LICENSE.txt") { into("META-INF"); rename { "LICENSE.txt" } }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val dungeonApiJavadocJar = tasks.register<Jar>("dungeonApiJavadocJar") {
    dependsOn(publicApiJavadoc)
    archiveFileName.set("TheMob-Dungeon-API-1.3.1-javadoc.jar")
    from(publicApiJavadoc.map { it.destinationDir })
    from("API-LICENSE.txt") { into("META-INF"); rename { "LICENSE.txt" } }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val apiExampleJar = tasks.register<Jar>("apiExampleJar") {
    dependsOn(compileApiExample)
    archiveFileName.set("TheMobAddonExample-1.0.0.jar")
    destinationDirectory.set(layout.buildDirectory.dir("example"))
    from(compileApiExample.flatMap { it.destinationDirectory })
    from("examples/addon/src/main/resources")
    from("API-LICENSE.txt") { into("META-INF"); rename { "LICENSE.txt" } }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
val sdkArtifacts = listOf(apiJar, dungeonApiJar, apiSourcesJar, dungeonApiSourcesJar, apiJavadocJar, dungeonApiJavadocJar)
val apiRepository = tasks.register("apiRepository") {
    dependsOn(sdkArtifacts)
    inputs.files(sdkArtifacts.map { it.flatMap { jar -> jar.archiveFile } })
    val output = layout.buildDirectory.dir("api-repository")
    outputs.dir(output)
    doLast {
        for (dungeon in listOf(false, true)) {
            val artifact = if (dungeon) "themob-dungeon-api" else "themob-api"
            val folder = output.get().asFile.resolve("org/plugin/themob/$artifact/1.3.1")
            folder.mkdirs()
            val jars = if (dungeon) listOf(dungeonApiJar, dungeonApiSourcesJar, dungeonApiJavadocJar) else listOf(apiJar, apiSourcesJar, apiJavadocJar)
            for ((index, jar) in jars.withIndex()) {
                val suffix = listOf("", "-sources", "-javadoc")[index]
                jar.get().archiveFile.get().asFile.copyTo(folder.resolve("$artifact-1.3.1$suffix.jar"), overwrite = true)
            }
            val rootDependency = if (dungeon) "<dependency><groupId>org.plugin.themob</groupId><artifactId>themob-api</artifactId><version>1.3.1</version><scope>provided</scope></dependency>" else ""
            folder.resolve("$artifact-1.3.1.pom").writeText("""
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion><groupId>org.plugin.themob</groupId><artifactId>$artifact</artifactId><version>1.3.1</version><packaging>jar</packaging>
                  <name>TheMob public API</name><description>Compile-only Bukkit API; implemented by TheMob 1.19.0.</description>
                  <properties><maven.compiler.release>17</maven.compiler.release></properties>
                  <licenses><license><name>MIT License (public API and SDK only)</name><url>https://opensource.org/licenses/MIT</url></license></licenses>
                  <repositories><repository><id>spigot-snapshots</id><url>https://hub.spigotmc.org/nexus/content/repositories/snapshots/</url></repository></repositories>
                  <dependencies>$rootDependency<dependency><groupId>org.spigotmc</groupId><artifactId>spigot-api</artifactId><version>1.18-R0.1-SNAPSHOT</version><scope>provided</scope></dependency></dependencies>
                </project>
            """.trimIndent() + "\n")
            for (file in folder.listFiles().orEmpty().filter { it.extension == "jar" || it.extension == "pom" }) {
                for ((algorithm, extension) in listOf("SHA-1" to "sha1", "SHA-256" to "sha256", "SHA-512" to "sha512")) {
                    val hash = MessageDigest.getInstance(algorithm).digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
                    file.resolveSibling(file.name + ".$extension").writeText(hash + "\n")
                }
            }
        }
    }
}
val developerSdk = tasks.register<Zip>("developerSdk") {
    dependsOn(sdkArtifacts, apiRepository, apiExampleJar)
    archiveFileName.set("TheMob-Developer-SDK-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from("DEVELOPER-GUIDE.md") { rename { "README.md" } }
    from("ADMIN-MENUE.md", "INTEGRATIONEN-20.md")
    from("API-LICENSE.txt", "API.md", "DUNGEON-API.md", "ADMIN-VERBINDUNGEN.md", "BATTLEPASS.md", "FAEHIGKEITEN.md", "INTEGRATIONEN.md", "VALIDATION.md", "MOBS-UND-ITEMS.md", "DUNGEONS.md", "AUSGANGSALTAR.md", "ZOMBIE-KOENIG.md", "SPIELERWERTE.md", "KOMPASS.md", "PERFORMANCE.md")
    from("src/main/resources/battlepass-combat-examples.yml") { into("examples") }
    from(sdkArtifacts) { into("api") }
    from(layout.buildDirectory.dir("docs/public-api")) { into("javadoc") }
    from(layout.buildDirectory.dir("api-repository")) { include("**/1.3.1/**"); includeEmptyDirs = false; into("maven-repository") }
    from("examples/addon") { into("example"); exclude("build/**", ".gradle/**", "libraries/**") }
    from(listOf("gradlew", "gradlew.bat")) { into("example") }
    from("gradle") { into("example/gradle") }
    from(listOf(apiJar, dungeonApiJar)) { into("example/libraries") }
    from("API-LICENSE.txt") { into("example") }
    from(apiExampleJar) { into("test-addon") }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
serverBundle.configure { from("ADMIN-VERBINDUNGEN.md", "API-LICENSE.txt", "FAEHIGKEITEN.md")
    from("src/main/resources/abilities.yml") { into("examples/skills") }
}
tasks.test {
    dependsOn(compileApiExample)
    classpath += files(compileApiExample.flatMap { it.destinationDirectory })
    systemProperty("themob.example.classes", layout.buildDirectory.dir("api-example-classes").get().asFile.absolutePath)
    systemProperty("themob.example.resources", file("examples/addon/src/main/resources").absolutePath)
}
serverBundle.configure {
    dependsOn(apiJar)
    from(apiJar) { into("developer") }
}
val standaloneTest = tasks.register<Test>("standaloneTest") {
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    systemProperty("standalone.nooptional", "true")
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath.filter {
        !it.name.startsWith("BetterHud-") && !it.name.startsWith("PlaceholderAPI-")
    }
    include("**/OptionalProvidersAbsentTest.class")
}
tasks.named("check") { dependsOn(compileApiExample, standaloneTest) }

// The purchased provider is read only for an explicit API test and never redistributed.
val battlePassApiTest = tasks.register<Test>("battlePassApiTest") {
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/BattlePassRealApiTest.class")
    val provider = providers.gradleProperty("battlePassJar")
    inputs.file(provider).withPropertyName("battlePassJar")
    doFirst {
        require(provider.isPresent) { "Pass -PbattlePassJar=<absolute path to your purchased BattlePass JAR>" }
        systemProperty("battlepass.test.jar", file(provider.get()).absolutePath)
    }
}
if (providers.gradleProperty("battlePassJar").isPresent) {
    tasks.named("check") { dependsOn(battlePassApiTest) }
}

val skilltreeCompatibilityTest = tasks.register<Test>("skilltreeCompatibilityTest") {
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/SkilltreeHealthCompatibilityTest.class")
    val provider = providers.gradleProperty("skilltreeJar")
    inputs.file(provider).withPropertyName("skilltreeJar")
    doFirst {
        require(provider.isPresent) { "Pass -PskilltreeJar=<path to the built Skilltree JAR>" }
        systemProperty("skilltree.test.jar", file(provider.get()).absolutePath)
    }
}
if (providers.gradleProperty("skilltreeJar").isPresent) {
    tasks.named("check") { dependsOn(skilltreeCompatibilityTest) }
}
val serverProvidersApiTest = tasks.register<Test>("serverProvidersApiTest") {
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/ServerProvidersRealApiTest.class")
    val names = listOf("luckpermsApi", "auraSkillsApi", "advancedCratesJar", "economyShopJar")
    for (name in names) {
        val provider = providers.gradleProperty(name)
        inputs.file(provider).withPropertyName(name)
        doFirst {
            require(provider.isPresent) { "Pass -P$name=<path to the provider API/JAR>" }
            systemProperty("provider.test.$name", file(provider.get()).absolutePath)
        }
    }
}
if (providers.gradleProperty("luckpermsApi").isPresent) {
    tasks.named("check") { dependsOn(serverProvidersApiTest) }
}
val updateBundle = tasks.register<Zip>("updateBundle") {
    dependsOn(tasks.jar)
    archiveFileName.set("TheMob-${project.version}-MC1.18-26.3-Update.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(tasks.jar) { into("plugins"); rename { "TheMob.jar" } }
    from("ADMIN-MENUE.md", "INTEGRATIONEN-20.md")
    from("KOMPATIBILITAET.md")
    from("UPDATE.md", "BATTLEPASS.md", "FAEHIGKEITEN.md", "ADMIN-VERBINDUNGEN.md", "API.md", "DEVELOPER-GUIDE.md", "API-LICENSE.txt", "VALIDATION.md", "CHANGELOG.md")
    from("DUNGEON-API.md", "DUNGEONS.md", "AUSGANGSALTAR.md", "MOBS-UND-ITEMS.md", "INTEGRATIONEN.md", "SPIELERWERTE.md", "ZOMBIE-KOENIG.md", "KOMPASS.md", "PERFORMANCE.md")
    from("src/main/resources/battlepass-combat-examples.yml") { into("examples/battlepass") }
    from("src/main/resources/abilities.yml") { into("examples/skills") }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.named("build") { dependsOn(sourceBundle, serverBundle, apiJar, dungeonApiJar, developerSdk, updateBundle) }

val gitbookBundle=tasks.register<Zip>("gitbookBundle") {
    archiveFileName.set("TheMob-GitBook-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from("docs") { into("docs") }
    from(".gitbook.yaml", "GIT-START.md")
    isPreserveFileTimestamps=false
    isReproducibleFileOrder=true
}
tasks.named("build") { dependsOn(gitbookBundle) }
developerSdk.configure { from("KOMPATIBILITAET.md") }

val compatibilityMatrixTest = tasks.register<Test>("compatibilityMatrixTest") {
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    testClassesDirs=sourceSets.test.get().output.classesDirs
    classpath=sourceSets.test.get().runtimeClasspath
    include("**/CompatibilityMatrixTest.class")
    val manifest=providers.gradleProperty("compatibilityApiManifest")
    inputs.file(manifest).withPropertyName("compatibilityApiManifest")
    doFirst {
        require(manifest.isPresent) { "Pass -PcompatibilityApiManifest=<verified API manifest>" }
        systemProperty("compat.api.manifest",file(manifest.get()).absolutePath)
        systemProperty("compat.compile.classpath",sourceSets.main.get().compileClasspath.asPath)
    }
}

val legacy118 = sourceSets.create("legacy118")
dependencies {
    add(legacy118.implementationConfigurationName,sourceSets.main.get().output)
    add(legacy118.implementationConfigurationName,"com.github.seeseemelk:MockBukkit-v1.18:2.85.2")
    add(legacy118.implementationConfigurationName,platform("org.junit:junit-bom:5.12.2"))
    add(legacy118.implementationConfigurationName,"org.junit.jupiter:junit-jupiter")
    add(legacy118.runtimeOnlyConfigurationName,"org.junit.platform:junit-platform-launcher")
    add(legacy118.runtimeOnlyConfigurationName,files(configurations.runtimeClasspath))
}
val legacy118Test=tasks.register<Test>("legacy118Test") {
    dependsOn(tasks.classes,tasks.named(legacy118.classesTaskName))
    useJUnitPlatform()
    testClassesDirs=legacy118.output.classesDirs
    classpath=legacy118.runtimeClasspath
    addTestListener(object:org.gradle.api.tasks.testing.TestListener {
        override fun beforeSuite(suite:org.gradle.api.tasks.testing.TestDescriptor){}
        override fun beforeTest(test:org.gradle.api.tasks.testing.TestDescriptor){}
        override fun afterTest(test:org.gradle.api.tasks.testing.TestDescriptor,result:org.gradle.api.tasks.testing.TestResult){}
        override fun afterSuite(suite:org.gradle.api.tasks.testing.TestDescriptor,result:org.gradle.api.tasks.testing.TestResult){if(suite.parent==null && result.skippedTestCount>0)throw GradleException("Legacy startup cannot be certified with skipped tests")}
    })
}

tasks.named("check") { dependsOn(legacy118Test) }
val noSqliteLegacy118Test = tasks.register<Test>("noSqliteLegacy118Test") {
    dependsOn(tasks.named(legacy118.classesTaskName), jarWithoutSqlite)
    useJUnitPlatform()
    testClassesDirs = legacy118.output.classesDirs
    val mainOutput = sourceSets.main.get().output.files
    classpath = files(jarWithoutSqlite) + legacy118.runtimeClasspath.filter {
        it !in mainOutput && !it.name.startsWith("sqlite-jdbc-")
    }
}
tasks.named("check") { dependsOn(noSqliteTest, noSqliteLegacy118Test) }
if(providers.gradleProperty("compatibilityApiManifest").isPresent) { tasks.named("check") { dependsOn(compatibilityMatrixTest) } }
