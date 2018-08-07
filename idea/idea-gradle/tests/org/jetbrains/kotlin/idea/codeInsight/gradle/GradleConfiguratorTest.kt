/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.configuration.*
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.test.testFramework.runWriteAction
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Assert
import org.junit.Test
import java.io.File

class GradleConfiguratorTest : GradleImportingTestCase() {
    private val testDir = PluginTestCaseBase.getTestDataPathBase() + "/gradle/configurator/"

    @Test
    fun testProjectWithModule() {
        configureByFiles()
        importProject()

        runInEdtAndWait {
            runWriteAction {
                // Create not configured build.gradle for project
                myProject.baseDir.createChildData(null, "build.gradle")

                val module = ModuleManager.getInstance(myProject).findModuleByName("app")!!
                val moduleGroup = module.toModuleGroup()
                // We have a Kotlin runtime in build.gradle but not in the classpath, so it doesn't make sense
                // to suggest configuring it
                assertEquals(ConfigureKotlinStatus.BROKEN, findGradleModuleConfigurator().getStatus(moduleGroup))
                // Don't offer the JS configurator if the JVM configuration exists but is broken
                assertEquals(ConfigureKotlinStatus.BROKEN, findJsGradleModuleConfigurator().getStatus(moduleGroup))
            }
        }

        Assert.assertEquals(
            """
            <p>The compiler bundled to Kotlin plugin (1.0.0) is older than external compiler used for building modules:</p>
            <ul>
            <li>app (1.1.0)</li>
            </ul>
            <p>This may cause different set of errors and warnings reported in IDE.</p>
            <p><a href="update">Update</a>  <a href="ignore">Ignore</a></p>
            """.trimIndent().lines().joinToString(separator = ""),
            createOutdatedBundledCompilerMessage(myProject, "1.0.0")
        )
    }

    @TargetVersions("3.5")
    @Test
    fun testConfigure10() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.0.6", collector)

                checkFiles(files)
            }
        }
    }

    @TargetVersions("4.5+")
    @Test
    fun testConfigure1045() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.0.6", collector)

                checkFiles(files)
            }
        }
    }

    @TargetVersions("3.5")
    @Test
    fun testConfigureKotlinWithPluginsBlock() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.0.6", collector)

                checkFiles(files)
            }
        }
    }

    @TargetVersions("4.5+")
    @Test
    fun testConfigureKotlinWithPluginsBlock45() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.0.6", collector)

                checkFiles(files)
            }
        }
    }

    @TargetVersions("4.4+")
    @Test
    fun testConfigureKotlinDevVersion() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.2.60-dev-286", collector)

                checkFiles(files)
            }
        }
    }

    @TargetVersions("4.4+")
    @Test
    fun testConfigureGradleKtsKotlinDevVersion() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.2.60-dev-286", collector)

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("4.4+")
    fun testConfigureJvmWithBuildGradle() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.2.40", collector)

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("4.4+")
    fun testConfigureJvmWithBuildGradleKts() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.2.40", collector)

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("4.4+")
    fun testConfigureJvmEAPWithBuildGradle() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.2.40-eap-62", collector)

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("4.4+")
    fun testConfigureJvmEAPWithBuildGradleKts() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.2.40-eap-62", collector)

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("4.4+")
    fun testConfigureJsWithBuildGradle() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findJsGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.2.40", collector)

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("4.4+")
    fun testConfigureJsWithBuildGradleKts() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findJsGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.2.40", collector)

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("4.4+")
    fun testConfigureJsEAPWithBuildGradle() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findJsGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.2.40-eap-62", collector)

                checkFiles(files)
            }
        }
    }

    @Test
    @TargetVersions("4.4+")
    fun testConfigureJsEAPWithBuildGradleKts() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findJsGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.2.40-eap-62", collector)

                checkFiles(files)
            }
        }
    }

    private fun findGradleModuleConfigurator() = Extensions.findExtension(
        KotlinProjectConfigurator.EP_NAME,
        KotlinGradleModuleConfigurator::class.java
    )

    private fun findJsGradleModuleConfigurator() = Extensions.findExtension(
        KotlinProjectConfigurator.EP_NAME,
        KotlinJsGradleModuleConfigurator::class.java
    )

    @TargetVersions("3.5")
    @Test
    fun testConfigureGSK() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.1.2", collector)

                checkFiles(files)
            }
        }
    }

    @TargetVersions("4.5+")
    @Test
    fun testConfigureGSK45() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            runWriteAction {
                val configurator = findGradleModuleConfigurator()
                val collector = createConfigureKotlinNotificationCollector(myProject)
                configurator.configureWithVersion(myProject, listOf(myTestFixture.module), "1.1.2", collector)

                checkFiles(files)
            }
        }
    }

    @Test
    fun testListNonConfiguredModules() {
        configureByFiles()

        importProject()

        runReadAction {
            val configurator = findGradleModuleConfigurator()

            val (modules, ableToRunConfigurators) = getConfigurationPossibilitiesForConfigureNotification(myProject)
            assertTrue(ableToRunConfigurators.any { it is KotlinGradleModuleConfigurator })
            assertTrue(ableToRunConfigurators.any { it is KotlinJsGradleModuleConfigurator })
            val moduleNames = modules.map { it.baseModule.name }
            assertSameElements(moduleNames, "app")

            val moduleNamesFromConfigurator = getCanBeConfiguredModules(myProject, configurator).map { it.name }
            assertSameElements(moduleNamesFromConfigurator, "app")

            val moduleNamesWithKotlinFiles = getCanBeConfiguredModulesWithKotlinFiles(myProject, configurator).map { it.name }
            assertSameElements(moduleNamesWithKotlinFiles, "app")
        }
    }

    @Test
    fun testListNonConfiguredModulesConfigured() {
        configureByFiles()

        importProject()

        runReadAction {
            assertEmpty(getConfigurationPossibilitiesForConfigureNotification(myProject).first)
        }
    }

    @Test
    fun testListNonConfiguredModulesConfiguredWithImplementation() {
        configureByFiles()

        importProject()

        runReadAction {
            assertEmpty(getConfigurationPossibilitiesForConfigureNotification(myProject).first)
        }
    }

    @Test
    fun testListNonConfiguredModulesConfiguredOnlyTest() {
        configureByFiles()

        importProject()

        runReadAction {
            assertEmpty(getConfigurationPossibilitiesForConfigureNotification(myProject).first)
        }
    }

    @Test
    fun testAddNonKotlinLibraryGSK() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                    myTestFixture.module,
                    DependencyScope.COMPILE,
                    object : ExternalLibraryDescriptor("org.a.b", "lib", "1.0.0", "1.0.0") {
                        override fun getLibraryClassesRoots() = emptyList<String>()
                    })
            }

            checkFiles(files)
        }
    }

    @Test
    fun testAddLibraryGSKWithKotlinVersion() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                val stdLibVersion = KotlinWithGradleConfigurator.getKotlinStdlibVersion(myTestFixture.module)
                KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                    myTestFixture.module,
                    DependencyScope.COMPILE,
                    object : ExternalLibraryDescriptor("org.jetbrains.kotlin", "kotlin-reflect", stdLibVersion, stdLibVersion) {
                        override fun getLibraryClassesRoots() = emptyList<String>()
                    })
            }

            checkFiles(files)
        }
    }

    @TargetVersions("3.5")
    @Test
    fun testAddTestLibraryGSK() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                    myTestFixture.module,
                    DependencyScope.TEST,
                    object : ExternalLibraryDescriptor("junit", "junit", "4.12", "4.12") {
                        override fun getLibraryClassesRoots() = emptyList<String>()
                    })

                KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                    myTestFixture.module,
                    DependencyScope.TEST,
                    object : ExternalLibraryDescriptor("org.jetbrains.kotlin", "kotlin-test", "1.1.2", "1.1.2") {
                        override fun getLibraryClassesRoots() = emptyList<String>()
                    })
            }

            checkFiles(files)
        }
    }

    @TargetVersions("4.5+")
    @Test
    fun testAddTestLibraryGSK45() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                    myTestFixture.module,
                    DependencyScope.TEST,
                    object : ExternalLibraryDescriptor("junit", "junit", "4.12", "4.12") {
                        override fun getLibraryClassesRoots() = emptyList<String>()
                    })

                KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                    myTestFixture.module,
                    DependencyScope.TEST,
                    object : ExternalLibraryDescriptor("org.jetbrains.kotlin", "kotlin-test", "1.1.2", "1.1.2") {
                        override fun getLibraryClassesRoots() = emptyList<String>()
                    })
            }

            checkFiles(files)
        }
    }

    @Test
    fun testAddLibraryGSK() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                    myTestFixture.module,
                    DependencyScope.COMPILE,
                    object : ExternalLibraryDescriptor("org.jetbrains.kotlin", "kotlin-reflect", "1.0.0", "1.0.0") {
                        override fun getLibraryClassesRoots() = emptyList<String>()
                    })
            }

            checkFiles(files)
        }
    }

    @Test
    fun testAddCoroutinesSupport() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.changeCoroutineConfiguration(myTestFixture.module, "enable")
            }

            checkFiles(files)
        }
    }

    @Test
    fun testAddCoroutinesSupportGSK() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.changeCoroutineConfiguration(myTestFixture.module, "enable")
            }

            checkFiles(files)
        }
    }

    @Test
    fun testChangeCoroutinesSupport() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.changeCoroutineConfiguration(myTestFixture.module, "enable")
            }

            checkFiles(files)
        }
    }

    @Test
    fun testChangeCoroutinesSupportGSK() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.changeCoroutineConfiguration(myTestFixture.module, "enable")
            }

            checkFiles(files)
        }
    }

    @Test
    fun testAddLanguageVersion() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.changeLanguageVersion(myTestFixture.module, "1.1", null, false)
            }

            checkFiles(files)
        }
    }

    @Test
    fun testAddLanguageVersionGSK() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.changeLanguageVersion(myTestFixture.module, "1.1", null, false)
            }

            checkFiles(files)
        }
    }

    @Test
    fun testChangeLanguageVersion() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.changeLanguageVersion(myTestFixture.module, "1.1", null, false)
            }

            checkFiles(files)
        }
    }

    @Test
    fun testChangeLanguageVersionGSK() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.changeLanguageVersion(myTestFixture.module, "1.1", null, false)
            }

            checkFiles(files)
        }
    }

    @Test
    fun testAddLibrary() {
        val files = configureByFiles()

        importProject()

        runInEdtAndWait {
            myTestFixture.project.executeWriteCommand("") {
                KotlinWithGradleConfigurator.addKotlinLibraryToModule(
                    myTestFixture.module,
                    DependencyScope.COMPILE,
                    object : ExternalLibraryDescriptor("org.jetbrains.kotlin", "kotlin-reflect", "1.0.0", "1.0.0") {
                        override fun getLibraryClassesRoots() = emptyList<String>()
                    })
            }

            checkFiles(files)
        }
    }

    private fun configureByFiles(): List<VirtualFile> {
        val rootDir = rootDir()
        assert(rootDir.exists()) { "Directory ${rootDir.path} doesn't exist" }

        return rootDir.walk().mapNotNull {
            when {
                it.isDirectory -> null
                !it.name.endsWith(SUFFIX) -> {
                    createProjectSubFile(it.path.substringAfter(rootDir.path + File.separator), it.readText())
                }
                else -> null
            }
        }.toList()
    }

    private fun checkFiles(files: List<VirtualFile>) {
        FileDocumentManager.getInstance().saveAllDocuments()

        files.filter {
            it.name == GradleConstants.DEFAULT_SCRIPT_NAME
                    || it.name == GradleConstants.KOTLIN_DSL_SCRIPT_NAME
                    || it.name == GradleConstants.SETTINGS_FILE_NAME
        }
            .forEach {
                if (it.name == GradleConstants.SETTINGS_FILE_NAME && !File(rootDir(), it.name + SUFFIX).exists()) return@forEach
                val actualText = LoadTextUtil.loadText(it).toString()
                KotlinTestUtils.assertEqualsToFile(File(rootDir(), it.name + SUFFIX), actualText)
            }
    }

    private fun rootDir() = File(testDir, getTestName(true).substringBefore("_"))

    companion object {
        private val SUFFIX = ".after"
    }
}
