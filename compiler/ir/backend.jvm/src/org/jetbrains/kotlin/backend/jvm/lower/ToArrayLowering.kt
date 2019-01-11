/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.descriptors.*
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
import org.jetbrains.kotlin.backend.jvm.codegen.isJvmInterface
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.*
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.impl.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

class ToArrayLowering(private val context: JvmBackendContext) : ClassLoweringPass {

    override fun lower(irClass: IrClass) {
        if (irClass.isJvmInterface) return

        val irBuiltIns = context.irBuiltIns

        if (!irClass.hasSuperClass { it.defaultType.isCollection() }) return

        if (irClass.hasSuperClass {
                it != irClass &&
                        it.isClass &&
                        it.origin != IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB &&
                        it.isCollectionSubClass()
            }) return

        val toArrayName = Name.identifier("toArray")
        val genericToArray = irClass.declarations.find { it.isGenericToArray() }
        val nonGenericToArray = irClass.declarations.find { it.isNonGenericToArray() }

        if (genericToArray == null) {
            val functionDescriptor = WrappedSimpleFunctionDescriptor()
            val irFunction = IrFunctionImpl(
                UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                JvmLoweredDeclarationOrigin.TO_ARRAY,
                IrSimpleFunctionSymbolImpl(functionDescriptor),
                toArrayName,
                Visibilities.PUBLIC,
                Modality.OPEN,
                isInline = false,
                isExternal = false,
                isTailrec = false,
                isSuspend = false
            )
            functionDescriptor.bind(irFunction)
            irFunction.parent = irClass

            val typeParameterDescriptor = WrappedTypeParameterDescriptor()
            val typeParameter = IrTypeParameterImpl(
                UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                JvmLoweredDeclarationOrigin.TO_ARRAY,
                IrTypeParameterSymbolImpl(typeParameterDescriptor),
                Name.identifier("T"),
                index = 0,
                variance = Variance.INVARIANT,
                isReified = false
            ).apply {
                typeParameterDescriptor.bind(this)
                superTypes.add(irBuiltIns.anyNType)
                parent = irFunction
            }
            irFunction.typeParameters.add(typeParameter)

            val substitutedArrayType = irBuiltIns.getArrayType(Variance.INVARIANT, typeParameter.defaultType)

            irFunction.returnType = substitutedArrayType
            val dispatchReceiverParameterDescriptor = WrappedValueParameterDescriptor()
            irFunction.dispatchReceiverParameter = IrValueParameterImpl(
                UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                JvmLoweredDeclarationOrigin.TO_ARRAY,
                IrValueParameterSymbolImpl(dispatchReceiverParameterDescriptor),
                Name.special("<this>"),
                index = -1,
                type = irClass.defaultType,
                varargElementType = null,
                isCrossinline = false,
                isNoinline = false
            ).apply {
                parent = irFunction
            }
            val valueParameterDescriptor = WrappedValueParameterDescriptor()
            irFunction.valueParameters.add(
                IrValueParameterImpl(
                    UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                    JvmLoweredDeclarationOrigin.TO_ARRAY,
                    IrValueParameterSymbolImpl(valueParameterDescriptor),
                    Name.identifier("array"),
                    index = 0,
                    varargElementType = null,
                    type = substitutedArrayType,
                    isCrossinline = false,
                    isNoinline = false
                ).apply {
                    valueParameterDescriptor.bind(this)
                    parent = irFunction
                }
            )

            irFunction.body = context.createIrBuilder(irFunction.symbol).irBlockBody {
                +irReturn(
                    irCall(genericToArrayUtilFunction.symbol, genericToArrayUtilFunction.returnType).apply {
                        putValueArgument(
                            0,
                            IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irFunction.dispatchReceiverParameter!!.symbol)
                        )
                        putValueArgument(1, IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irFunction.valueParameters[0].symbol))
                    })
            }

            irClass.declarations.add(irFunction)
        } else {
            //TODO patch visibility
        }

        if (nonGenericToArray == null) {
            val functionDescriptor = WrappedSimpleFunctionDescriptor()
            val irFunction = IrFunctionImpl(
                UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                JvmLoweredDeclarationOrigin.TO_ARRAY,
                IrSimpleFunctionSymbolImpl(functionDescriptor),
                toArrayName,
                Visibilities.PUBLIC,
                Modality.OPEN,
                isInline = false,
                isExternal = false,
                isTailrec = false,
                isSuspend = false
            )
            functionDescriptor.bind(irFunction)
            irFunction.parent = irClass

            irFunction.returnType = irBuiltIns.getArrayType(Variance.INVARIANT, irBuiltIns.anyType)
            val dispatchReceiverParameterDescriptor = WrappedValueParameterDescriptor()
            irFunction.dispatchReceiverParameter = IrValueParameterImpl(
                UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                JvmLoweredDeclarationOrigin.TO_ARRAY,
                IrValueParameterSymbolImpl(dispatchReceiverParameterDescriptor),
                Name.special("<this>"),
                index = -1,
                type = irClass.defaultType,
                varargElementType = null,
                isCrossinline = false,
                isNoinline = false
            ).apply {
                parent = irFunction
            }

            irFunction.body = context.createIrBuilder(irFunction.symbol).irBlockBody {
                +irReturn(
                    irCall(nonGenericToArrayUtilFunction.symbol, nonGenericToArrayUtilFunction.returnType).apply {
                        putValueArgument(
                            0,
                            IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irFunction.dispatchReceiverParameter!!.symbol)
                        )
                    })
            }

            irClass.declarations.add(irFunction)
        } else {
            //TODO patch visibility
        }
    }

    private val kotlinJvmInternalPackageFragment: IrPackageFragment by lazy {
        val descriptor = WrappedPackageFragmentDescriptor(context.builtIns.builtInsModule) // TODO: what module should we pass here?
        IrExternalPackageFragmentImpl(
            IrExternalPackageFragmentSymbolImpl(descriptor),
            FqName("kotlin.jvm.internal")
        ).apply {
            descriptor.bind(this)
        }
    }

    private val collectionUtilClass: IrClass by lazy {
        val descriptor = WrappedClassDescriptor()
        IrClassImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
            JvmLoweredDeclarationOrigin.TO_ARRAY,
            IrClassSymbolImpl(descriptor),
            Name.identifier("CollectionToArray"),
            ClassKind.CLASS,
            Visibilities.PUBLIC,
            Modality.FINAL,
            isCompanion = false,
            isInner = false,
            isInline = false,
            isData = false,
            isExternal = false
        ).apply {
            descriptor.bind(this)
            parent = kotlinJvmInternalPackageFragment
            superTypes.add(context.irBuiltIns.anyType)
            kotlinJvmInternalPackageFragment.declarations.add(this)
        }
    }

    private fun createToArrayUtilFunction(isGeneric: Boolean): IrSimpleFunction {
        val irBuiltIns = context.irBuiltIns

        val utilFunctionDescriptor = WrappedSimpleFunctionDescriptor()
        return IrFunctionImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
            JvmLoweredDeclarationOrigin.TO_ARRAY,
            IrSimpleFunctionSymbolImpl(utilFunctionDescriptor),
            Name.identifier("toArray"),
            Visibilities.PUBLIC,
            Modality.FINAL,
            isInline = false,
            isExternal = false,
            isTailrec = false,
            isSuspend = false
        ).also { irFunction ->
            utilFunctionDescriptor.bind(irFunction)
            irFunction.parent = collectionUtilClass

            val arrayType = irBuiltIns.getArrayType(Variance.INVARIANT, irBuiltIns.anyNType)
            irFunction.returnType = arrayType

            val collectionParameterDescriptor = WrappedValueParameterDescriptor()
            irFunction.valueParameters.add(
                IrValueParameterImpl(
                    UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                    JvmLoweredDeclarationOrigin.TO_ARRAY,
                    IrValueParameterSymbolImpl(collectionParameterDescriptor),
                    Name.identifier("collection"),
                    0,
                    irBuiltIns.collectionClass.owner.defaultType,
                    varargElementType = null,
                    isCrossinline = false,
                    isNoinline = false
                ).apply {
                    collectionParameterDescriptor.bind(this)
                    parent = irFunction
                }
            )
            if (isGeneric) {
                val arrayParameterDescriptor = WrappedValueParameterDescriptor()
                irFunction.valueParameters.add(
                    IrValueParameterImpl(
                        UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                        JvmLoweredDeclarationOrigin.TO_ARRAY,
                        IrValueParameterSymbolImpl(arrayParameterDescriptor),
                        Name.identifier("array"),
                        1,
                        arrayType,
                        varargElementType = null,
                        isCrossinline = false,
                        isNoinline = false
                    ).apply {
                        arrayParameterDescriptor.bind(this)
                        parent = irFunction
                    }
                )
            }
            collectionUtilClass.declarations.add(irFunction)
        }
    }

    private val nonGenericToArrayUtilFunction: IrSimpleFunction by lazy { createToArrayUtilFunction(false) }
    private val genericToArrayUtilFunction: IrSimpleFunction by lazy { createToArrayUtilFunction(true) }
}


private fun IrType.hasSuperType(pred: (IrType) -> Boolean): Boolean {
    val alreadyVisited = mutableSetOf<IrType>()

    fun search(irType: IrType): Boolean = when {
        irType in alreadyVisited -> false
        pred(irType) -> true
        irType is IrSimpleType -> {
            alreadyVisited.add(irType)
            irType.classifier.owner.let { classifier ->
                when (classifier) {
                    is IrClass -> classifier.superTypes.asSequence().any { search(it) }
                    is IrTypeParameter -> classifier.superTypes.asSequence().any { search(it) }
                    else -> error("Unknown classifier kind $classifier")
                }
            }
        }
        else -> false
    }

    return search(this)
}

private fun IrClass.hasSuperClass(pred: (IrClass) -> Boolean): Boolean =
    defaultType.hasSuperType { type ->
        type is IrSimpleType && type.classifier.owner.let {
            it is IrClass && pred(it)
        }
    }

// Have to check by name, since irBuiltins is unreliable.
private fun IrClass.isCollectionSubClass() =
    hasSuperClass {
        it.defaultType.isCollection()
    }

private fun IrDeclaration.isGenericToArray(): Boolean {
    if (this !is IrSimpleFunction) return false
    if (this.name.asString() != "toArray") return false
    if (typeParameters.size != 1 || valueParameters.size != 1) return false
    if (!returnType.isArray()) return false

    val paramType = valueParameters[0].type
    if (!paramType.isArray()) return false

    val elementType = typeParameters[0].defaultType

    // TODO: check type equality directly on IrTypes
    if (elementType.toKotlinType() != ((returnType as IrSimpleType).arguments[0] as? IrTypeProjection)?.type?.toKotlinType()) return false
    if (elementType.toKotlinType() != ((paramType as IrSimpleType).arguments[0] as? IrTypeProjection)?.type?.toKotlinType()) return false
    return true
}

private fun IrDeclaration.isNonGenericToArray(): Boolean {
    if (this !is IrSimpleFunction) return false
    if (this.name.asString() != "toArray") return false
    if (!typeParameters.isEmpty() || !valueParameters.isEmpty()) return false
    if (!returnType.isArray()) return false

    return true
}

private fun IrBuiltIns.getArrayType(variance: Variance, elemType: IrType) =
    IrSimpleTypeImpl(
        arrayClass,
        hasQuestionMark = false,
        arguments = listOf(makeTypeProjection(elemType, variance)),
        annotations = emptyList()
    )