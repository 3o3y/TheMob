plugins { java }
group = "example"
version = "1.0.0"
repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}
dependencies {
    compileOnly(files("libraries/TheMob-API-1.3.1.jar"))
    compileOnly(files("libraries/TheMob-Dungeon-API-1.3.1.jar"))
    compileOnly("org.spigotmc:spigot-api:1.18-R0.1-SNAPSHOT")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(17) }
tasks.jar {
    archiveBaseName.set("TheMobAddonExample")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from("API-LICENSE.txt") { into("META-INF"); rename { "LICENSE.txt" } }
}
