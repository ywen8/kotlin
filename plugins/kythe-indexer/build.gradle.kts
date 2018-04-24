plugins {
    kotlin("jvm")
    id("jps-compatible")
}

jvmTarget = "1.8"

dependencies {
    compile(project(":compiler:frontend.java"))
    compile(project(":compiler:backend-common"))
    compile(project(":compiler:ir.tree"))
    compile(project(":compiler:ir.psi2ir"))
    compileOnly(project(":compiler:plugin-api"))
    compileOnly(intellijCoreDep()) { includeJars("intellij-core") }

    compile(project(":plugins:kythe-indexer:kythe-common"))

    testCompile(project(":compiler:tests-common"))
    testCompile(projectTests(":compiler:tests-common"))
    testCompile(projectTests(":generators:test-generator"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

projectTest {
    workingDir = rootDir
}