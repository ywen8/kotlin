/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.lower

import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.descriptors.WrappedSimpleFunctionDescriptor
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.isSuspend
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlock
import org.jetbrains.kotlin.backend.common.lower.irBlockBody
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name

private const val INLINE_CLASS_IMPL_SUFFIX = "__ICIMPL"

class InlineClassLowering(val context: BackendContext) {
    private val transformedFunction = mutableMapOf<IrFunctionSymbol, IrSimpleFunctionSymbol>()

    val inlineClassDeclarationRemoving = object : ClassLoweringPass {
        override fun lower(irClass: IrClass) {
            if (!irClass.isInline) return

            irClass.transformDeclarationsFlat { declaration ->
                when (declaration) {
                    is IrSimpleFunction ->
                        if (!declaration.isStaticMethodOfClass && declaration.overriddenSymbols.isEmpty()) {
                            emptyList()
                        } else {
                            listOf(declaration)
                        }

                    is IrConstructor ->
                        if (!declaration.isPrimary) {
                            emptyList()
                        } else {
                            listOf(declaration)
                        }

                    else -> listOf(declaration)
                }
            }
        }
    }

    val inlineClassDeclarationLowering = object : ClassLoweringPass {
        override fun lower(irClass: IrClass) {
            if (!irClass.isInline) return

            irClass.transformDeclarationsFlat { declaration ->
                when (declaration) {
                    is IrConstructor -> transform(declaration)
                    is IrSimpleFunction -> transform(declaration)

                    is IrAnonymousInitializer -> error("Inline classes do not support initializers: $declaration")

                    // Properties getters and setters should already inlined into class list of declaration
                    is IrProperty -> listOf(declaration)

                    is IrField -> listOf(declaration)

                    is IrClass -> listOf(declaration.also { assert(!it.isInner) })
                    else -> error("Unexpected declaration: $declaration")
                }
            }
        }

        private fun transform(irConstructor: IrConstructor): List<IrDeclaration> {
            if (irConstructor.isPrimary) return listOf(irConstructor)

            val result = getOrCreateInlineClassImpl(irConstructor, false)
            val irClass = irConstructor.parentAsClass

            result.body = context.createIrBuilder(result.symbol).irBlockBody(result) {
                lateinit var thisVar: IrVariable
                val parameterMapping = result.valueParameters.associateBy { it ->
                    irConstructor.valueParameters[it.index].symbol
                }

                (irConstructor.body as IrBlockBody).statements.forEach { statement ->
                    +statement.transform(object : IrElementTransformerVoid() {
                        override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall): IrExpression {
                            expression.transformChildrenVoid()
                            return irBlock(expression) {
                                thisVar =
                                        irTemporary(expression, typeHint = irClass.defaultType.toKotlinType(), irType = irClass.defaultType)
                            }
                        }

                        override fun visitGetValue(expression: IrGetValue): IrExpression {
                            expression.transformChildrenVoid()
                            if (expression.symbol == irClass.thisReceiver?.symbol) {
                                return irGet(thisVar)
                            }

                            parameterMapping[expression.symbol]?.let { return irGet(it) }
                            return expression
                        }

                        override fun visitReturn(expression: IrReturn): IrExpression {
                            expression.transformChildrenVoid()
                            if (expression.returnTargetSymbol == irConstructor.symbol) {
                                return irReturn(irBlock(expression.startOffset, expression.endOffset) {
                                    +expression.value
                                    +irGet(thisVar)
                                })
                            }

                            return expression
                        }

                    }, null)
                }
                +irReturn(irGet(thisVar))
            }

            return listOf(irConstructor, result)
        }


        private fun transform(function: IrSimpleFunction): List<IrDeclaration> {
            // TODO: Support fake-overridden methods without boxing
            if (function.isStaticMethodOfClass || !function.isReal) return listOf(function)

            val staticMethod = getOrCreateInlineClassImpl(function, false)
            assert(staticMethod.isReal)

            function.body!!.transformChildrenVoid(object : IrElementTransformerVoid() {
                override fun visitDeclaration(declaration: IrDeclaration): IrStatement {
                    declaration.transformChildrenVoid(this)

                    // TODO: Variable parents might not be initialized
                    if (declaration !is IrVariable && declaration.parent == function)
                        declaration.parent = staticMethod

                    return declaration
                }

                override fun visitGetValue(expression: IrGetValue): IrExpression {
                    val valueDeclaration = expression.symbol.owner as? IrValueParameter ?: return super.visitGetValue(expression)

                    return context.createIrBuilder(staticMethod.symbol).irGet(
                        when (valueDeclaration) {
                            function.dispatchReceiverParameter, function.parentAsClass.thisReceiver ->
                                staticMethod.valueParameters[0]

                            function.extensionReceiverParameter ->
                                staticMethod.extensionReceiverParameter!!

                            in function.valueParameters ->
                                staticMethod.valueParameters[valueDeclaration.index + 1]

                            else -> return expression
                        }
                    )
                }
            })

            staticMethod.body = function.body

            function.body = context.createIrBuilder(function.symbol).irBlockBody {
                +irReturn(
                    irCall(staticMethod).apply {
                        val parameters =
                            listOf(function.dispatchReceiverParameter!!) + function.valueParameters

                        for ((index, valueParameter) in parameters.withIndex()) {
                            putValueArgument(index, irGet(valueParameter))
                        }

                        extensionReceiver = function.extensionReceiverParameter?.let { irGet(it) }
                    }
                )
            }

            return listOf(function, staticMethod)
        }
    }

    val inlineClassUsageLowering = object : FileLoweringPass {
        override fun lower(irFile: IrFile) {
            irFile.transformChildrenVoid(object : IrElementTransformerVoid() {

                override fun visitFunctionReference(expression: IrFunctionReference): IrExpression {
                    val function = expression.symbol.owner
                    function.transformChildrenVoid(this)
                    return expression
                }

                override fun visitCall(call: IrCall): IrExpression {
                    call.transformChildrenVoid(this)
                    val function = call.symbol.owner
                    if (function.isDynamic()) return call // TODO
                    if (function.body == null || function.parent !is IrClass || function.isStaticMethodOfClass) return call
                    val klass = function.parentAsClass
                    if (!klass.isInline) return call
                    if (function is IrSimpleFunction && !function.isReal) return call
                    if (function is IrConstructor && function.isPrimary) return call

                    val newFunction = getOrCreateInlineClassImpl(function)

                    return irCall(call, newFunction, dispatchReceiverAsFirstArgument = (function is IrSimpleFunction))
                }

                override fun visitDelegatingConstructorCall(call: IrDelegatingConstructorCall): IrExpression {
                    call.transformChildrenVoid(this)
                    val function = call.symbol.owner
                    //if (function.isPrimary) return call
                    val klass = function.parentAsClass
                    if (!klass.isInline) return call
                    return lowerConstructorCallToValue(call, function)
                }

                private fun lowerConstructorCallToValue(
                    expression: IrMemberAccessExpression,
                    callee: IrConstructor
                ): IrExpression = if (callee.isPrimary) {
                    irCall(expression, callee)
                } else {
                    irCall(expression, getOrCreateInlineClassImpl(callee)).apply {
                        (0 until expression.valueArgumentsCount).forEach {
                            putValueArgument(it, expression.getValueArgument(it)!!)
                        }
                    }
                }
            })
        }

    }

    fun getOrCreateInlineClassImpl(function: IrFunction, addToClass: Boolean = true): IrSimpleFunction {
        return transformedFunction.getOrPut(function.symbol) {
            val newFunction = createInlineClassStaticMethodBodylessDeclaration(function)
            if (addToClass) {
                function.parentAsClass.declarations.push(newFunction)
            }
            newFunction.symbol
        }.owner
    }

    fun buildFunction(
        originalFunction: IrFunction,
        name: Name,
        visibility: Visibility = originalFunction.visibility,
        modality: Modality = Modality.FINAL,
        isInline: Boolean = originalFunction.isInline,
        isExternal: Boolean = originalFunction.isExternal,
        isTailrec: Boolean = (originalFunction is IrSimpleFunction && originalFunction.isTailrec),
        isSuspend: Boolean = originalFunction.isSuspend,
        origin: IrDeclarationOrigin = originalFunction.origin
    ): IrSimpleFunction {
        val descriptor = WrappedSimpleFunctionDescriptor()
        return IrFunctionImpl(
            originalFunction.startOffset,
            originalFunction.endOffset,
            origin,
            IrSimpleFunctionSymbolImpl(descriptor),
            name,
            visibility,
            modality,
            isInline,
            isExternal,
            isTailrec,
            isSuspend
        ).also { descriptor.bind(it) }
    }


    private fun Name.toInlineClassImplementationName() = when {
        isSpecial -> Name.special(asString() + INLINE_CLASS_IMPL_SUFFIX)
        else -> Name.identifier(asString() + INLINE_CLASS_IMPL_SUFFIX)
    }

    fun createInlineClassStaticMethodBodylessDeclaration(function: IrFunction): IrSimpleFunction {
        val newName = function.name.toInlineClassImplementationName()
        val staticMethod = buildFunction(function, newName).apply {
            returnType = if (function is IrSimpleFunction) {
                function.returnType
            } else {
                function.parentAsClass.defaultType
            }
            typeParameters += function.typeParameters
            annotations += function.annotations
            dispatchReceiverParameter = null
            extensionReceiverParameter = function.extensionReceiverParameter?.copyTo(this)
            if (function is IrSimpleFunction) {
                valueParameters.add(function.dispatchReceiverParameter!!.copyTo(this, shift = 1))
                valueParameters += function.valueParameters.map { p -> p.copyTo(this, shift = 1) }
            } else {
                valueParameters += function.valueParameters.map { p -> p.copyTo(this, shift = 0) }
            }
            parent = function.parent
            assert(isStaticMethodOfClass)
        }

        if (function is IrSimpleFunction) {
            assert(staticMethod.valueParameters.size == function.valueParameters.size + 1)
        }

        assert(staticMethod.isReal)
        return staticMethod
    }

}
