plugins {
    kotlin("jvm")
    id("jps-compatible")
}

jvmTarget = "1.8"
javaHome = rootProject.extra["JDK_18"] as String

dependencies {
    compile(project(":core:util.runtime"))
    compile(project(":kotlin-stdlib"))
    compile(project(":kotlin-annotations-jvm"))
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}
