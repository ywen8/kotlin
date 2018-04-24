/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kythe

import com.google.devtools.kythe.analyzers.base.StreamFactEmitter
import com.google.devtools.kythe.platform.shared.NullStatisticsCollector
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.ir.AbstractIrGeneratorTestCase
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.kythe.indexer.IrBasedKytheIndexer
import org.jetbrains.kotlin.kythe.indexer.PsiBasedSourcesManager
import org.jetbrains.kotlin.kythe.indexer.getCompilationVName
import org.jetbrains.kotlin.kythe.indexer.getRequiredInputs
import org.jetbrains.kotlin.kythe.signatures.KotlinSignaturesProvider
import org.jetbrains.kotlin.psi2ir.Psi2IrTranslator
import org.jetbrains.kotlin.psi2ir.generators.GeneratorContext
import org.jetbrains.kotlin.resolve.lazy.JvmResolveUtil
import org.jetbrains.kotlin.test.KotlinTestUtils
import java.io.File

private const val KYTHE_VERIFIER = "/opt/kythe/tools/verifier"

abstract class AbstractKytheIndexTest : AbstractIrGeneratorTestCase() {
    lateinit var generatedIrRoot: IrElement
    lateinit var analysisResult: AnalysisResult
    lateinit var generatorContext: GeneratorContext

    override fun doTest(wholeFile: File, testFiles: List<TestFile>) {
        val resultingIndex = File(wholeFile.path + ".index")
        // We can't feed 'wholeFile' into verifier in case there were '// FILE:'-directives -- obviously, kythe-verifier
        // doesn't know about those directives and treats 'wholeFile' as one file, which shifts offsets.
        // So we have to generate those files somewhere on the disk to feed them into verifier later.
        val dirForSplittedFiles = KotlinTestUtils.tmpDir("kt-files")

        ensureKytheExists()

        generateIr()

        writeIndexTo(resultingIndex)

        for (psiFile in myFiles.psiFiles) {
            val file = File(dirForSplittedFiles, psiFile.name)
            KotlinTestUtils.mkdirs(file.parentFile)
            file.writeText(psiFile.text, Charsets.UTF_8)
            feedFileIntoVerifier(resultingIndex, file.absolutePath, VerifierFlags.IGNORE_DUPS, VerifierFlags.ANNOTATED_GRAPHVIZ)
        }
    }

    private fun writeIndexTo(output: File) {
        val compilationVName = getCompilationVName(myFiles.psiFiles)

        output.outputStream().buffered().use { stream ->
            val factEmitter = StreamFactEmitter(stream)
            val indexer = IrBasedKytheIndexer(
                compilationVName,
                factEmitter,
                NullStatisticsCollector.getInstance(),
                getRequiredInputs(myFiles.psiFiles),
                PsiBasedSourcesManager(
                    generatorContext.sourceManager,
                    root = output.parent,
                    corpus = "test"
                ),
                KotlinSignaturesProvider(generatorContext.symbolTable)
            )
            indexer.indexIrTree(generatedIrRoot)
        }
    }

    private fun generateIr() {
        analysisResult = JvmResolveUtil.analyze(myFiles.psiFiles, myEnvironment)
        val translator = Psi2IrTranslator()
        generatorContext = translator.createGeneratorContext(analysisResult.moduleDescriptor, analysisResult.bindingContext)
        generatedIrRoot = translator.generateModuleFragment(generatorContext, myFiles.psiFiles)
    }


    // Equivalent to 'cat file | $VERIFIER$ verifierArgs'
    private fun feedFileIntoVerifier(file: File, vararg verifierArgs: String) {
        val output = GeneralCommandLine(KYTHE_VERIFIER, *verifierArgs).withInput(file).execute()

        val graphDumpFile = File(file.path.replace(".index", ".graph"))
        graphDumpFile.writeText(output.stdout, Charsets.US_ASCII)

        val errorMessage = output.wrapErrorsIfAny() ?: return

        TestCase.fail(errorMessage)
    }

    private fun GeneralCommandLine.execute(): ProcessOutput = ExecUtil.execAndGetOutput(this)

    private fun ProcessOutput.successfulOnlyStdout(): String {
        wrapErrorsIfAny()?.let { TestCase.fail(it) }
        return stdout.trim()
    }

    // Throws if stderr was non-empty or if exit code != 0
    private fun ProcessOutput.wrapErrorsIfAny(): String? = when {
        stderr.isNotEmpty() -> "Verifier returned non-empty stderr:\n$stderr"
        isExitCodeSet && exitCode != 0 -> "Verifier returned non-zero exit code: $exitCode\n"
        else -> null
    }

    private fun ensureKytheExists() {
        val result = GeneralCommandLine(KYTHE_VERIFIER, "--version").execute().successfulOnlyStdout()
        TestCase.assertEquals("verifier version 0.1", result)
    }

    object VerifierFlags {
        const val GRAPHVIZ: String = "-graphviz"
        const val ANNOTATED_GRAPHVIZ: String = "-annotated_graphviz"
        const val IGNORE_DUPS: String = "-ignore_dups"
        const val SHOW_GOALS: String = "-show_goals"
        const val SHOW_PROTOS: String = "-show_protos"
    }
}