/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.descriptors.WrappedSimpleFunctionDescriptor
import org.jetbrains.kotlin.backend.common.descriptors.WrappedValueParameterDescriptor
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.common.ir.isMethodOfAny
import org.jetbrains.kotlin.backend.common.ir.isStatic
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrValueParameterImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.IrStarProjectionImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.org.objectweb.asm.commons.Method

class BridgeLowering(val context: JvmBackendContext) : ClassLoweringPass {

    private val state = context.state

    private val typeMapper = state.typeMapper

    override fun lower(irClass: IrClass) {
        if (irClass.isInterface || irClass.origin == JvmLoweredDeclarationOrigin.DEFAULT_IMPLS) {
            return
        }

        val functions = irClass.declarations.filterIsInstance<IrSimpleFunction>().filterNot {
            it.isStatic || it.origin == IrDeclarationOrigin.FAKE_OVERRIDE
        }

        functions.forEach {
            generateBridges(it, irClass)
        }

        //additional bridges for inherited interface methods
        if (!irClass.isInterface && irClass.origin != JvmLoweredDeclarationOrigin.DEFAULT_IMPLS) {
            for (member in irClass.declarations.filterIsInstance<IrSimpleFunction>()) {
                if (member.origin != IrDeclarationOrigin.FAKE_OVERRIDE) continue
                if (member.isMethodOfAny()) continue

                // This should run after `PropertiesLowering`, so no need to worry about properties separately.
                val implementation = member.resolveFakeOverride()
                if (implementation != null && !implementation.parentAsClass.isInterface) {
                    generateBridges(member, irClass)
                }
            }
        }
    }


    sealed class BridgeTableElement {
        object AlreadyDefined : BridgeTableElement()
        class NeedsDefinition(val function: IrSimpleFunction) : BridgeTableElement()
    }

    private fun generateBridges(irFunction: IrSimpleFunction, irClass: IrClass) {
        val ourSignature = irFunction.getJvmSignature()
        val ourMethodName = ourSignature.name

        if (irFunction.origin == IrDeclarationOrigin.FAKE_OVERRIDE &&
                irFunction.overriddenSymbols.all { it.owner.modality != Modality.ABSTRACT }) {
            // All needed bridges will be generated where functions are implemented.
            return
        }

        val (specialOverrideSignature, specialOverrideValueGenerator) =
                findSpecialWithOverride(irFunction) ?: Pair(null, null)

        val methodsToBridge = irFunction.getMethodsToBridge()
        for ((signature, method) in methodsToBridge) {
            when (method) {
                is BridgeTableElement.AlreadyDefined -> { /* do nothing */ }
                is BridgeTableElement.NeedsDefinition -> {
                    val bridge = createBridgeHeader(irFunction, method.function)

                    val defaultValueGenerator = if (signature == specialOverrideSignature) specialOverrideValueGenerator else null
                    val isSpecial = (defaultValueGenerator != null) || (signature.name != ourMethodName)

                    bridge.createBridgeBody(irFunction, defaultValueGenerator, isSpecial)
                    irClass.declarations.add(bridge)
                }
            }
        }
    }

    // Cache signatures
    inner class InheritancePathItem(val function: IrSimpleFunction, val signature: Method = function.getJvmSignature())

    private fun IrSimpleFunction.getMethodsToBridge(): Map<Method, BridgeTableElement> {
        val res = mutableMapOf<Method, BridgeTableElement>()

        fun register(signature: Method, irFunction: IrSimpleFunction, inheritancePath: List<InheritancePathItem>) {
            if (res[signature] === BridgeTableElement.AlreadyDefined) return
            if (irFunction.origin === IrDeclarationOrigin.FAKE_OVERRIDE) return

            if (inheritancePath.any {
                    !it.function.parentAsClass.isInterface &&
                            it.function.origin != IrDeclarationOrigin.FAKE_OVERRIDE &&
                            it.function != this@getMethodsToBridge && it.signature != signature
                }) {
                res[signature] = BridgeTableElement.AlreadyDefined
            } else if (!irFunction.parentAsClass.isInterface &&
                        inheritancePath.all { it.function.origin === IrDeclarationOrigin.FAKE_OVERRIDE }) {
                res[signature] = BridgeTableElement.AlreadyDefined
            } else {
                res[signature] = BridgeTableElement.NeedsDefinition(irFunction)
            }
        }

        fun handle(irFunction: IrSimpleFunction, lastJvmName: String, inheritancePath: MutableList<InheritancePathItem>) {
            val signature = irFunction.getJvmSignature()
            register(signature, irFunction, inheritancePath)
            if (signature.name != lastJvmName) {
                val newName = Name.identifier(signature.name)
                inheritancePath.forEachIndexed { i, inheritancePathItem ->
                    val renamedItem = inheritancePathItem.function.copyRenamingTo(newName)
                    register(renamedItem.getJvmSignature(), renamedItem, inheritancePath.slice(0..i))
                }
            }

            inheritancePath.add(InheritancePathItem(irFunction, signature))
            irFunction.overriddenSymbols.forEach {
                handle(it.owner, signature.name, inheritancePath)
            }
            inheritancePath.removeAt(inheritancePath.size - 1)
        }

        handle(this, getJvmName(), mutableListOf())
        return res
    }

    private fun IrSimpleFunction.copyRenamingTo(newName: Name): IrSimpleFunction =
        WrappedSimpleFunctionDescriptor(descriptor.annotations).let { newDescriptor ->
            IrFunctionImpl(
                startOffset, endOffset, origin,
                IrSimpleFunctionSymbolImpl(newDescriptor),
                newName,
                visibility, modality, isInline, isExternal, isTailrec, isSuspend
            ).apply {
                newDescriptor.bind(this)
                parent = this@copyRenamingTo.parent
                returnType = this@copyRenamingTo.returnType
                dispatchReceiverParameter = this@copyRenamingTo.dispatchReceiverParameter?.copyTo(this)
                extensionReceiverParameter = this@copyRenamingTo.extensionReceiverParameter?.copyTo(this)
                valueParameters.addAll(this@copyRenamingTo.valueParameters.map { it.copyTo(this) })
            }
        }

    private fun createBridgeHeader(target: IrSimpleFunction, interfaceFunction: IrSimpleFunction): IrSimpleFunction {
        val descriptor = WrappedSimpleFunctionDescriptor()
        return IrFunctionImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
            IrDeclarationOrigin.BRIDGE,
            IrSimpleFunctionSymbolImpl(descriptor),
            Name.identifier(interfaceFunction.getJvmName()),
            interfaceFunction.visibility,
            Modality.OPEN,
            isInline = false,
            isExternal = false,
            isTailrec = false,
            isSuspend = interfaceFunction.isSuspend
        ).apply {
            descriptor.bind(this)
            parent = target.parentAsClass
            returnType = interfaceFunction.returnType.eraseTypeParameters()
            dispatchReceiverParameter = target.dispatchReceiverParameter?.copyTo(this)
            extensionReceiverParameter = interfaceFunction.extensionReceiverParameter?.copyWithTypeErasure(this)
            interfaceFunction.valueParameters.mapIndexed { i, param ->
                valueParameters.add(i, param.copyWithTypeErasure(this))
            }
        }
    }

    private fun IrSimpleFunction.createBridgeBody(
        target: IrSimpleFunction,
        defaultValueGenerator: ((IrSimpleFunction) -> IrExpression)?,
        isSpecial: Boolean
    ) {
        val maybeOrphanedTarget = if (isSpecial && target.origin != IrDeclarationOrigin.FAKE_OVERRIDE)
            target.orphanedCopy()
        else
            target

        context.createIrBuilder(symbol).run {
            body = irBlockBody() {
                if (defaultValueGenerator != null) {
                    valueParameters.forEach {
                        +irIfThen(
                            context.irBuiltIns.unitType,
                            irNot(irIs(irGet(it), maybeOrphanedTarget.valueParameters[it.index].type)),
                            irReturn(defaultValueGenerator(this@createBridgeBody))
                        )
                    }
                }
                +irReturn(
                    irImplicitCast(
                        IrCallImpl(
                            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                            maybeOrphanedTarget.returnType,
                            maybeOrphanedTarget.symbol, maybeOrphanedTarget.descriptor,
                            origin = IrStatementOrigin.BRIDGE_DELEGATION
                        ).apply {
                            dispatchReceiver = irImplicitCast(irGet(dispatchReceiverParameter!!), dispatchReceiverParameter!!.type)
                            extensionReceiverParameter?.let {
                                extensionReceiver = irImplicitCast(irGet(it), extensionReceiverParameter!!.type)
                            }
                            valueParameters.forEach {
                                putValueArgument(it.index, irImplicitCast(irGet(it), maybeOrphanedTarget.valueParameters[it.index].type))
                            }
                        },
                        returnType
                    )
                )
            }
        }
    }

    /* A hacky way to make sure the code generator calls the right function, and not some standard interface it implements. */
    private fun IrSimpleFunction.orphanedCopy() =
        WrappedSimpleFunctionDescriptor(descriptor.annotations).let { wrappedDescriptor ->
            IrFunctionImpl(
                startOffset, endOffset, origin,
                IrSimpleFunctionSymbolImpl(wrappedDescriptor),
                Name.identifier(getJvmName()),
                visibility, modality, isInline, isExternal, isTailrec, isSuspend
            ).apply {
                wrappedDescriptor.bind(this)
                returnType = this@orphanedCopy.returnType
                parent = this@orphanedCopy.parent
                copyTypeParametersFrom(this@orphanedCopy)
                this@orphanedCopy.dispatchReceiverParameter?.let { dispatchReceiverParameter = it.copyTo(this) }
                this@orphanedCopy.extensionReceiverParameter?.let { extensionReceiverParameter = it.copyTo(this) }
                this@orphanedCopy.valueParameters.forEachIndexed { index, param ->
                    valueParameters.add(index, param.copyTo(this))
                }
                /* Do NOT copy overriddenSymbols */
            }
        }

    private fun IrValueParameter.copyWithTypeErasure(target: IrSimpleFunction): IrValueParameter {
        val descriptor = WrappedValueParameterDescriptor(this.descriptor.annotations)
        return IrValueParameterImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
            IrDeclarationOrigin.BRIDGE,
            IrValueParameterSymbolImpl(descriptor),
            name,
            index,
            type.eraseTypeParameters(),
            varargElementType?.eraseTypeParameters(),
            isCrossinline,
            isNoinline
        ).apply {
            descriptor.bind(this)
            parent = target
        }
    }

    /* Perform type erasure as much as is significant for JVM signature generation. */
    private fun IrType.eraseTypeParameters() = when (this) {
        is IrErrorType -> this
        is IrSimpleType -> {
            val owner = classifier.owner
            when (owner) {
                is IrClass -> this
                is IrTypeParameter -> {
                    val upperBound = owner.upperBoundClass()
                    IrSimpleTypeImpl(
                        upperBound.symbol,
                        hasQuestionMark,
                        List(upperBound.typeParameters.size) { IrStarProjectionImpl },    // Should not affect JVM signature, but may result in an invalid type object
                        owner.annotations
                    )
                }
                else -> error("Unknown IrSimpleType classifier kind: $owner")
            }
        }
        else -> error("Unknown IrType kind: $this")
    }

    private fun IrTypeParameter.upperBoundClass(): IrClass {
        val simpleSuperClassifiers = superTypes.asSequence().filterIsInstance<IrSimpleType>().map { it.classifier }
        return simpleSuperClassifiers
            .filterIsInstance<IrClassSymbol>()
            .let {
                it.firstOrNull { !it.owner.isInterface } ?: it.firstOrNull()
            }?.owner ?: simpleSuperClassifiers.filterIsInstance<IrTypeParameterSymbol>()
            .map { it.owner.upperBoundClass() }.firstOrNull() ?: context.irBuiltIns.anyClass.owner
    }

    data class SpecialMethodDescription(val fqName: String, val arity: Int)

    fun IrSimpleFunction.fqName() =
        "${getPackageFragment()?.fqName?.asString()}.${parentAsClass.name.asString()}.${name.asString()}"

    fun IrSimpleFunction.toDescription() = SpecialMethodDescription(fqName(), valueParameters.size)

    fun constFalse(bridge: IrSimpleFunction) =
        IrConstImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.booleanType, IrConstKind.Boolean, false)

    fun constNull(bridge: IrSimpleFunction) =
        IrConstImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.anyNType, IrConstKind.Null, null)

    fun constMinusOne(bridge: IrSimpleFunction) =
        IrConstImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.intType, IrConstKind.Int, -1)

    fun getSecondArg(bridge: IrSimpleFunction) = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, bridge.valueParameters[1].symbol)

    val specialMethodWithDefaultsMap = mapOf<SpecialMethodDescription, (IrSimpleFunction) -> IrExpression>(
        SpecialMethodDescription("kotlin.collections.Collection.contains", 1) to ::constFalse,
        SpecialMethodDescription("kotlin.collections.Collection.remove", 1) to ::constFalse,
        SpecialMethodDescription("kotlin.collections.Map.containsKey", 1) to ::constFalse,
        SpecialMethodDescription("kotlin.collections.Map.containsValue", 1) to ::constFalse,
        SpecialMethodDescription("kotlin.collections.MutableMap.remove", 2) to ::constFalse,
        SpecialMethodDescription("kotlin.collections.Map.getOrDefault", 1) to ::getSecondArg,
        SpecialMethodDescription("kotlin.collections.Map.get", 1) to ::constNull,
        SpecialMethodDescription("kotlin.collections.MutableMap.remove", 1) to ::constNull,
        SpecialMethodDescription("kotlin.collections.List.indexOf", 1) to ::constMinusOne,
        SpecialMethodDescription("kotlin.collections.List.lastIndexOf", 1) to ::constMinusOne
    )

    private fun findSpecialWithOverride(irFunction: IrSimpleFunction): Pair<Method, (IrSimpleFunction) -> IrExpression>? {
        val alreadyVisited = mutableSetOf<IrSimpleFunction>()
        fun search(irFunction: IrSimpleFunction): Pair<Method, (IrSimpleFunction) -> IrExpression>? {
            if (irFunction in alreadyVisited) return null
            val description = irFunction.toDescription()
            specialMethodWithDefaultsMap[description]?.let {
                return Pair(irFunction.getJvmSignature(), it)
            }
            alreadyVisited.add(irFunction)
            return irFunction.overriddenSymbols.asSequence().mapNotNull { search(it.owner) }.firstOrNull()
        }
        return search(irFunction)
    }

    private fun IrFunction.getJvmName() = getJvmSignature().name
    private fun IrFunction.getJvmSignature() = typeMapper.mapAsmMethod(descriptor)
}
