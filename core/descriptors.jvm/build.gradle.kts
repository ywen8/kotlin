plugins {
    kotlin("jvm")
    id("jps-compatible")
}

jvmTarget = "1.8"
javaHome = rootProject.extra["JDK_18"] as String

dependencies {
    compile(project(":kotlin-annotations-jvm"))
    compile(project(":core:descriptors"))
    compile(project(":core:deserialization"))
    compile(project(":core:metadata.jvm"))
    compile(project(":core:util.runtime"))
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
