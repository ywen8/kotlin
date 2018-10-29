import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "Kotlin Mock Runtime for Tests"

plugins {
    kotlin("jvm")
}

jvmTarget = "1.8"
javaHome = rootProject.extra["JDK_18"] as String

dependencies {
    compileOnly(project(":kotlin-stdlib"))
}

sourceSets {
    "main" {
        java.apply {
            srcDir(File(buildDir, "src"))
        }
    }
    "test" {}
}

val copySources by task<Sync> {
    val stdlibProjectDir = project(":kotlin-stdlib").projectDir

    from(stdlibProjectDir.resolve("runtime"))
        .include("kotlin/TypeAliases.kt",
                 "kotlin/text/TypeAliases.kt")
    from(stdlibProjectDir.resolve("src"))
        .include("kotlin/collections/TypeAliases.kt")
    from(stdlibProjectDir.resolve("../src"))
        .include("kotlin/util/Standard.kt",
                 "kotlin/internal/Annotations.kt",
                 "kotlin/contracts/ContractBuilder.kt",
                 "kotlin/contracts/Effect.kt")
    into(File(buildDir, "src"))
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

tasks.withType<KotlinCompile> {
    dependsOn(copySources)
    kotlinOptions {
        freeCompilerArgs += listOf(
            "-module-name",
            "kotlin-stdlib",
            "-Xmulti-platform",
            "-Xuse-experimental=kotlin.contracts.ExperimentalContracts",
            "-Xuse-experimental=kotlin.Experimental"
        )
    }
}

val jar = runtimeJar {
    dependsOn(":core:builtins:serialize")
    from(fileTree("${rootProject.extra["distDir"]}/builtins")) { include("kotlin/**") }
}

val distDir: String by rootProject.extra

dist(targetName = "kotlin-stdlib-minimal-for-test.jar", targetDir = File(distDir))
