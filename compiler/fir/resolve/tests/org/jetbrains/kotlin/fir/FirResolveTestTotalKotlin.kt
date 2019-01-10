/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.builtins.jvm.JvmBuiltIns
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.SimpleGlobalContext
import org.jetbrains.kotlin.context.withModule
import org.jetbrains.kotlin.context.withProject
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.fir.builder.RawFirBuilder
import org.jetbrains.kotlin.fir.resolve.FirProvider
import org.jetbrains.kotlin.fir.resolve.impl.FirProviderImpl
import org.jetbrains.kotlin.fir.resolve.transformers.FirTotalResolveTransformer
import org.jetbrains.kotlin.frontend.java.di.createContainerForTopDownAnalyzerForJvm
import org.jetbrains.kotlin.frontend.java.di.initJvmBuiltInsForTopDownAnalysis
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.load.java.lazy.SingleModuleClassResolver
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.MultiTargetPlatform
import org.jetbrains.kotlin.resolve.jvm.JavaDescriptorResolver
import org.jetbrains.kotlin.resolve.lazy.KotlinCodeAnalyzer
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.storage.ExceptionTracker
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.test.ConfigurationKind
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestJdkKind
import org.jetbrains.kotlin.util.slicedMap.WritableSlice
import java.io.File

@TestDataPath("/")
class FirResolveTestTotalKotlin : AbstractFirResolveWithSessionTestCase() {

    private val forbiddenDirectories = listOf("testdata", "resources")

    override fun createEnvironment(): KotlinCoreEnvironment {

        val configurationKind = ConfigurationKind.ALL
        val testJdkKind = TestJdkKind.FULL_JDK


        val javaFiles = File(".").walkTopDown().onEnter {
            it.name.toLowerCase() !in forbiddenDirectories
        }.filter {
            it.isDirectory
        }.mapNotNull { dir ->
            if (dir.name in setOf("src", "test", "tests")) {
                if (dir.walkTopDown().any { it.extension == "java" }) {
                    return@mapNotNull dir
                }
            }
            null
        }.toList()


        val configuration = KotlinTestUtils.newConfiguration(configurationKind, testJdkKind, emptyList(), javaFiles)
        return KotlinCoreEnvironment.createForTests(testRootDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
    }

    private class ThrowingCliBindingTrace : CliBindingTrace() {
        override fun <K, V> record(slice: WritableSlice<K, V>, key: K, value: V) {
            if (slice in allowedSlices) {
                super.record(slice, key, value)
                return
            }
            throw IllegalStateException("Attempt to write into trace slice: $slice !!!")
        }

        override fun toString(): String {
            return ThrowingCliBindingTrace::class.java.name
        }

        companion object {
            val allowedSlices = setOf(BindingContext.CLASS, BindingContext.FUNCTION, BindingContext.CONSTRUCTOR, BindingContext.VARIABLE)
        }
    }

    private fun initializeDefaultContainerForJvmAnalysis(files: Collection<KtFile>) {
        val tracker = ExceptionTracker()
        val storageManager = LockBasedStorageManager.createWithExceptionHandling(tracker)
        val context = SimpleGlobalContext(storageManager, tracker)
        val module = ModuleDescriptorImpl(
            Name.special("<FIR-total-kotlin>"), storageManager, JvmBuiltIns(storageManager), MultiTargetPlatform.Specific("JVM")
        )
        module.setDependencies(listOf(module))
        val moduleContext = context.withProject(project).withModule(module)
        val moduleTrace = ThrowingCliBindingTrace()
        val moduleContentScope = GlobalSearchScope.allScope(project)
        val moduleClassResolver = SingleModuleClassResolver()
        val jvmTarget = JvmTarget.DEFAULT
        val languageVersionSettings = LanguageVersionSettingsImpl.DEFAULT

        val container = createContainerForTopDownAnalyzerForJvm(
            moduleContext,
            moduleTrace,
            FileBasedDeclarationProviderFactory(moduleContext.storageManager, files),
            moduleContentScope,
            LookupTracker.DO_NOTHING,
            ExpectActualTracker.DoNothing,
            environment.createPackagePartProvider(moduleContentScope),
            moduleClassResolver,
            jvmTarget,
            languageVersionSettings
        )
        container.initJvmBuiltInsForTopDownAnalysis()
        moduleClassResolver.resolver = container.get()

        module.initialize(
            CompositePackageFragmentProvider(
                listOf(
                    container.get<KotlinCodeAnalyzer>().packageFragmentProvider,
                    container.get<JavaDescriptorResolver>().packageFragmentProvider
                )
            )
        )
    }

    fun testTotalKotlin() {

        val testDataPath = "."
        val root = File(testDataPath)

        println("BASE PATH: ${root.absolutePath}")

        val allFiles = root.walkTopDown().filter { file ->
            !file.isDirectory && forbiddenDirectories.none { it in file.path.toLowerCase() } && file.extension == "kt"
        }

        val ktFiles = allFiles.map {
            val text = KotlinTestUtils.doLoadFile(it)
            KotlinTestUtils.createFile(it.path, text, project)
        }

        val scope = ProjectScope.getContentScope(project)
        val session = createSession(scope)
        val builder = RawFirBuilder(session)

        val totalTransformer = FirTotalResolveTransformer()
        val firFiles = ktFiles.map {
            val firFile = builder.buildFirFile(it)
            (session.service<FirProvider>() as FirProviderImpl).recordFile(firFile)
            firFile
        }.toList()


        println("Raw FIR up, files: ${firFiles.size}")

        initializeDefaultContainerForJvmAnalysis(ktFiles.toList())
        doFirResolveTestBench(firFiles, totalTransformer.transformers)
    }
}