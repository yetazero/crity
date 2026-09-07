import groovy.json.JsonSlurper

plugins {
    kotlin("jvm") version "2.4.10"
}

group = "yetazero"
version = (JsonSlurper().parse(file("src/main/resources/manifest.json")) as Map<*, *>)["Version"].toString()

repositories {
    mavenCentral()
    maven("https://maven.hytale.com/pre-release") {
        content { includeGroup("com.hypixel.hytale") }
    }
}

val hytaleServerJar = providers.gradleProperty("hytaleServerJar")
    .orElse(providers.environmentVariable("HYTALE_SERVER_JAR"))
val hytaleApi: Any = if (hytaleServerJar.isPresent) {
    val jar = file(hytaleServerJar.get())
    require(jar.isFile) { "Hytale server JAR does not exist: $jar" }
    files(jar)
} else {
    "com.hypixel.hytale:Server:0.7.0-pre.1"
}

dependencies {
    compileOnly(hytaleApi)
    testImplementation(hytaleApi)
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
    archiveBaseName.set("Crity")
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({ configurations.runtimeClasspath.get().filter { it.isFile }.map { zipTree(it) } })
}

val checkCombat = tasks.register<JavaExec>("checkCombat") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.yetazero.crity.CombatDisplayCheckKt")
    jvmArgs("-ea", "--enable-native-access=ALL-UNNAMED")
}

tasks.check {
    dependsOn(checkCombat)
}


tasks.test {
    failOnNoDiscoveredTests.set(false)
}
