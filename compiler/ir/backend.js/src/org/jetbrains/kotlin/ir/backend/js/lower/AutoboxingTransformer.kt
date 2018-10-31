/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.ir.isSuspend
import org.jetbrains.kotlin.backend.common.lower.AbstractValueUsageTransformer
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.getInlinedClass
import org.jetbrains.kotlin.ir.util.isInlined

class AutoboxingTransformer(val context: JsIrBackendContext) : AbstractValueUsageTransformer(context.irBuiltIns), FileLoweringPass {
    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid()
    }

    override fun IrExpression.useAs(type: IrType): IrExpression {
        //val actualType = this.type
        val expectedType = type

        val actualType = when (this) {
            is IrCall -> {
                if (this.symbol.owner.isSuspend) irBuiltIns.anyNType
                else try {
                    this.callTarget.returnType
                } catch (e: kotlin.UninitializedPropertyAccessException) {
                    this.type
                }
            }
            is IrGetField -> this.symbol.owner.type

            is IrTypeOperatorCall -> when (this.operator) {
                IrTypeOperator.IMPLICIT_INTEGER_COERCION ->
                    // TODO: is it a workaround for inconsistent IR?
                    this.typeOperand

                IrTypeOperator.CAST, IrTypeOperator.IMPLICIT_CAST -> context.irBuiltIns.anyNType

                else -> this.type
            }

            is IrGetValue -> {
                val value = this.symbol.owner
                if (value is IrValueParameter && value.isDispatchReceiver) {
                    irBuiltIns.anyNType
                } else {
                    this.type
                }
            }

            else -> this.type
        }

        val actualInlinedClass = actualType.getInlinedClass()

        if (actualType.makeNotNull().isNothing())
            return this

        val expectedInlinedClass = expectedType.getInlinedClass()

        val function = when {
            actualInlinedClass == null && expectedInlinedClass == null -> return this
            actualInlinedClass != null && expectedInlinedClass == null -> context.intrinsics.jsBoxIntrinsic
            actualInlinedClass == null && expectedInlinedClass != null -> context.intrinsics.jsUnboxIntrinsic
            else -> return this
        }

        return JsIrBuilder.buildCall(
            function,
            expectedType,
            typeArguments = listOf(actualType, expectedType)
        ).also {
            it.putValueArgument(0, this)
        }
    }

    override fun IrExpression.useAsVarargElement(expression: IrVararg): IrExpression {
        if (this.type.isInlined()) {
            return this.useAs(irBuiltIns.anyNType)
        }
        return this.useAs(expression.varargElementType)
    }

    private val IrValueParameter.isDispatchReceiver: Boolean
        get() {
            val parent = this.parent
            if (parent is IrClass)
                return true
            if (parent is IrFunction && parent.dispatchReceiverParameter == this)
                return true
            return false
        }

    private val IrCall.callTarget: IrFunction
        get() = symbol.owner

}
