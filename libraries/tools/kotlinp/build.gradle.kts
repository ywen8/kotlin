import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


val cacheRedirectorEnabled = findProperty("cacheRedirectorEnabled")?.toString()?.toBoolean() == true

description = "kotlinp"

plugins {
    kotlin("jvm")
}

val asmVersion = rootProject.extra["versions.jar.asm-all"] as String

val shadows by configurations.creating

repositories {
    if (findProperty("cacheRedirectorEnabled")?.toString()?.toBoolean() == true) {
        maven("https://cache-redirector.jetbrains.com/jetbrains.bintray.com/intellij-third-party-dependencies/")
    }
    maven("https://jetbrains.bintray.com/intellij-third-party-dependencies/")
}

dependencies {
    compileOnly(project(":kotlinx-metadata"))
    compileOnly(project(":kotlinx-metadata-jvm"))
    compile("org.jetbrains.intellij.deps:asm-all:$asmVersion")

    testCompileOnly(project(":kotlinx-metadata"))
    testCompileOnly(project(":kotlinx-metadata-jvm"))
    testCompile(commonDep("junit:junit"))
    testCompile(projectTests(":generators:test-generator"))

    testRuntime(project(":kotlinx-metadata-jvm", configuration = "runtime"))

    shadows(project(":kotlinx-metadata-jvm", configuration = "runtime"))
    shadows("org.jetbrains.intellij.deps:asm-all:$asmVersion")
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

projectTest {
    workingDir = rootDir
}

val generateTests by generator("org.jetbrains.kotlin.kotlinp.test.GenerateKotlinpTestsKt")

val shadowJar by task<ShadowJar> {
    classifier = "shadow"
    version = null
    configurations = listOf(shadows)
    from(mainSourceSet.output)
    manifest {
        attributes["Main-Class"] = "org.jetbrains.kotlin.kotlinp.Main"
    }
}

tasks {
    "assemble" {
        dependsOn(shadowJar)
    }
    "test" {
        // These dependencies are needed because ForTestCompileRuntime loads jars from dist
        dependsOn(":kotlin-reflect:dist")
        dependsOn(":kotlin-script-runtime:dist")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += listOf("-Xuse-experimental=kotlin.Experimental")
    }
}