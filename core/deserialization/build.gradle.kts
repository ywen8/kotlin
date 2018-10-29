plugins {
    kotlin("jvm")
    id("jps-compatible")
}

jvmTarget = "1.8"
javaHome = rootProject.extra["JDK_18"] as String

dependencies {
    compile(project(":core:metadata"))
    compile(project(":core:util.runtime"))
    compile(project(":core:descriptors"))
    compile(commonDep("javax.inject"))
}

sourceSets {
    "main" { projectDefault() }
    "test" {}
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}
