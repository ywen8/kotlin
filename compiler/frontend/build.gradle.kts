
plugins {
    kotlin("jvm")
    id("jps-compatible")
}

jvmTarget = "1.6"

dependencies {
    compile(project(":kotlin-annotations-jvm"))
    compile(project(":core:descriptors"))
    compile(project(":core:deserialization"))
    compile(project(":compiler:util"))
    compile(project(":compiler:container"))
    compile(project(":compiler:resolution"))
    compile(project(":compiler:psi"))
    compile(project(":kotlin-script-runtime"))
    compile(commonDep("io.javaslang","javaslang"))
    compileOnly(intellijCoreDep()) { includeJars("intellij-core") }
    compileOnly(intellijDep()) { includeJars("trove4j", "guava", rootProject = rootProject) }
    compile("org.jetbrains:annotations:16.0.3")
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}
