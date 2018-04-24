/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kythe

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.ir.AbstractIrGeneratorTestCase
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.kythe.signatures.KotlinSignaturesProvider
import org.jetbrains.kotlin.kythe.signatures.SignaturesProvider
import org.jetbrains.kotlin.psi2ir.Psi2IrTranslator
import org.jetbrains.kotlin.psi2ir.generators.GeneratorContext
import org.jetbrains.kotlin.resolve.lazy.JvmResolveUtil
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.utils.Printer
import java.io.File

typealias IrElementsFilter = (IrElement) -> Boolean

open class AbstractSignaturesGeneratorTest : AbstractIrGeneratorTestCase() {
    lateinit var generatedIrRoot: IrElement
    lateinit var analysisResult: AnalysisResult
    lateinit var generatorContext: GeneratorContext

    override fun doTest(wholeFile: File, testFiles: List<TestFile>) {
        val dir = File(wholeFile.parent)
        val testFile = TestFile(wholeFile.name, wholeFile.readText())
        val elementsFilter = parseFilterFromFileText(testFile)
        val expectedFile = createExpectedTextFile(testFile, dir, wholeFile.name.replace(".kt", ".txt"))

        generateIr()

        val actualText = dumpAllSignatures(generatedIrRoot, elementsFilter)

        KotlinTestUtils.assertEqualsToFile(expectedFile, actualText)
    }

    private fun parseFilterFromFileText(file: TestFile): IrElementsFilter {
        val firstLine = file.content.lines().first()
        if (!firstLine.startsWith(EXCLUDE_DIRECTIVE)) return RENDER_ALL

        val excludedElements = firstLine.removePrefix(EXCLUDE_DIRECTIVE).split(",").map { it.trim() }
        return { irElement ->
            val renderedElement = irElement.render()
            excludedElements.all { it !in renderedElement }
        }
    }

    private fun generateIr() {
        analysisResult = JvmResolveUtil.analyze(myFiles.psiFiles, myEnvironment)
        val translator = Psi2IrTranslator(LanguageVersionSettingsImpl.DEFAULT)
        generatorContext = translator.createGeneratorContext(analysisResult.moduleDescriptor, analysisResult.bindingContext)
        generatedIrRoot = translator.generateModuleFragment(generatorContext, myFiles.psiFiles)
    }

    private fun dumpAllSignatures(root: IrElement, elementsFilter: IrElementsFilter): String {
        val collector =
            SignaturesCollector(
                KotlinSignaturesProvider(generatorContext.symbolTable),
                elementsFilter
            )

        return buildString {
            root.accept(collector, Printer(this))
        }
    }


    private class SignaturesCollector(
        private val signaturesProvider: SignaturesProvider,
        private val elementsFilter: IrElementsFilter
    ) : IrElementVisitor<Unit, Printer> {

        override fun visitElement(element: IrElement, data: Printer) {
            // skip
        }

        override fun visitFile(declaration: IrFile, data: Printer) {
            declaration.acceptChildren(this, data)
        }

        override fun visitModuleFragment(declaration: IrModuleFragment, data: Printer) {
            declaration.acceptChildren(this, data)
        }

        override fun visitPackageFragment(declaration: IrPackageFragment, data: Printer) {
            data.println(declaration.render())
            data.println(signaturesProvider.getFullSignature(declaration))

            data.pushIndent()
            declaration.acceptChildren(this, data)
            data.popIndent()
        }

        override fun visitBody(body: IrBody, data: Printer) {
            body.acceptChildren(this, data)
        }


        override fun visitDeclaration(declaration: IrDeclaration, data: Printer) {
            if (declaration is IrTypeParametersContainer) signaturesProvider.enterScope(declaration)

            if (!elementsFilter(declaration)) return

            data.println(declaration.render())
            if (declaration !is IrAnonymousInitializer) { // No signatures for anonymous initializers
                data.println(signaturesProvider.getFullSignature(declaration))
            }

            data.pushIndent()
            declaration.acceptChildren(this, data)
            data.println()
            data.popIndent()

            if (declaration is IrTypeParametersContainer) signaturesProvider.leaveScope()
        }
    }

    companion object {
        const val EXCLUDE_DIRECTIVE: String = "// !EXCLUDE: "

        val RENDER_ALL: IrElementsFilter = { true }
    }
}

