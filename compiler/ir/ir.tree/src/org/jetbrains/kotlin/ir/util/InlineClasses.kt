/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.util

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.*
import java.lang.IllegalStateException

fun IrType.getInlinedClass(): IrClass? = when (this) {
    is IrDynamicType -> null
    is IrSimpleType -> try {
        getInlinedClass(erase(this), this.isMarkedNullable())
    } catch (e: IllegalStateException) {
        // TODO: Fix unbound symbols
        null
    }
    else -> error("Unknown type")
}

fun IrType.isInlined(): Boolean = this.getInlinedClass() != null

private fun getInlinedClass(erased: IrClass, isNullable: Boolean): IrClass? =
    if (!isNullable && erased.isInline) erased else null

private tailrec fun erase(type: IrType): IrClass {
    val classifier = type.classifierOrFail
    return when (classifier) {
        is IrClassSymbol -> classifier.owner
        is IrTypeParameterSymbol -> erase(classifier.owner.superTypes.first())
        else -> error(classifier)
    }
}

fun getInlineClassBackingField(irClass: IrClass): IrField {
    for (declaration in irClass.declarations) {
        if (declaration is IrField)
            return declaration

        if (declaration is IrProperty)
            return declaration.backingField ?: continue
    }
    error("Inline class has not field")
}
