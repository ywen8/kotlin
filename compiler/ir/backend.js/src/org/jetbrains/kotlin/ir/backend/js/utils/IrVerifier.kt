/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.utils

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.isReal
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import java.io.File

fun dumpIrFile(file: IrFile, dir: String) {
    val sourceName = file.name
    if (true || "JS_TESTS" in sourceName) {
        // println("Trying to dump: $sourceName")
        val irDump = file.dump()
        val irFilename = "/Users/jetbrains/irs/$dir/" + sourceName.substringAfterLast('/') + ".ir"
        File(irFilename).apply {
            parentFile.mkdirs()
            createNewFile()
            writeText(irDump)
        }
        println("Ir saved: $irFilename $sourceName")
    }
}

fun IrElement.checkIr() {
    acceptVoid(
        object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitDeclaration(declaration: IrDeclaration) {
                declaration.acceptChildrenVoid(this)

                if (declaration is IrVariable) return

                try {
                    val x = declaration.parent
                } catch (e: UninitializedPropertyAccessException) {
                    println("\n\n!!! Declaration parent is not initialized !!!\n\n")
                    println(declaration.dump())
                    TODO()
                }
            }

            override fun visitSimpleFunction(declaration: IrSimpleFunction) {
                declaration.acceptChildrenVoid(this)
                if (declaration.isStaticMethodOfClass && !declaration.isReal) {
                    TODO()
                }
            }
        }
    )
    acceptVoid(
        object : IrElementVisitorVoid {
            val expressions = mutableSetOf<IrExpression>()

            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitExpression(expression: IrExpression) {
                expression.acceptChildrenVoid(this)

                if (expression is IrConst<*>) return
                if (expression is IrGetObjectValue) return
                if (expression is IrGetValue) return

                if (expression in expressions) {
                    error("\n\nExpression is already used:\n\n ${expression.dump()} \n\n")
                } else {
                    expressions.add(expression)
                }
            }
        }
    )
    acceptVoid(
        object : IrElementVisitorVoid {
            val expressions = mutableSetOf<IrExpression>()

            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitExpression(expression: IrExpression) {
                expression.acceptChildrenVoid(this)

                if (expression is IrConst<*>) return
                if (expression is IrGetObjectValue) return
                if (expression is IrGetValue) return

                if (expression in expressions) {
                    error("\n\nExpression is already used:\n\n ${expression.dump()} \n\n")
                } else {
                    expressions.add(expression)
                }
            }
        }
    )
}

fun IrElement.checkIrValParams() = acceptVoid(
    object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }

        override fun visitSimpleFunction(function: IrSimpleFunction) {
            function.acceptChildrenVoid(this)

            if ("\$init\$" in function.name.asString()) return

            function.body?.acceptVoid(object : IrElementVisitorVoid {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitGetValue(expression: IrGetValue) {
                    expression.acceptChildrenVoid(this)
                    val valueDeclaration = expression.symbol.owner as? IrValueParameter ?: return super.visitGetValue(expression)

                    val parentClass = function.parent as? IrClass

                    if (valueDeclaration.parent !in listOf(function, parentClass)) {
                        error(
                            "Wrong value declaration:($valueDeclaration)\n\t " +
                                    "parent:(${valueDeclaration.parent})\n\t " +
                                    "is not function:(${function.name}) "
                        )
                    }
                }
            })
        }
    }
)

