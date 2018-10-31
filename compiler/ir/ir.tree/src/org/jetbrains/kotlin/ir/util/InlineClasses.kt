/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.util

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe

fun IrType.getInlinedClass(): IrClass? = if (this is IrDynamicType) null else IrTypeInlineClassesSupport.getInlinedClass(this)

fun IrType.isInlined(): Boolean = IrTypeInlineClassesSupport.isInlined(this)
fun IrClass.isInlined(): Boolean = IrTypeInlineClassesSupport.isInlined(this)

fun IrClass.inlinedClassIsNullable(): Boolean = this.defaultType.makeNullable().getInlinedClass() == this // TODO: optimize
fun IrClass.isUsedAsBoxClass(): Boolean = IrTypeInlineClassesSupport.isUsedAsBoxClass(this)

private fun IrClass.getAllSuperClassifiers(): List<IrClass> =
    listOf(this) + this.superTypes.flatMap { (it.classifierOrFail.owner as IrClass).getAllSuperClassifiers() }

private object IrTypeInlineClassesSupport {
    fun isInlined(clazz: IrClass): Boolean = getInlinedClass(clazz) != null

    fun isInlined(type: IrType): Boolean = getInlinedClass(type) != null

    fun isUsedAsBoxClass(clazz: IrClass) = getInlinedClass(clazz) == clazz // To handle NativePointed subclasses.

    fun getInlinedClass(type: IrType): IrClass? =
        try {
            getInlinedClass(erase(type), isNullable(type))
        } catch (e: java.lang.IllegalStateException) {
            // TODO:
            null
        } catch (e: java.lang.ClassCastException) {
            null
        }

    private fun getInlinedClass(erased: IrClass, isNullable: Boolean): IrClass? {
        val inlinedClass = getInlinedClass(erased) ?: return null
        return if (!isNullable /* || representationIsNonNullReferenceOrPointer(inlinedClass)*/) {
            inlinedClass
        } else {
            null
        }
    }

    @JvmName("classGetInlinedClass")
    private fun getInlinedClass(clazz: IrClass): IrClass? =
        if (hasInlineModifier(clazz)) clazz else null

    fun unwrapInlinedClass(type: IrType): IrType? {
        val inlinedClass = getInlinedClass(type) ?: return null
        val underlyingType = getInlinedClassUnderlyingType(inlinedClass)
        return if (isNullable(type)) makeNullable(underlyingType) else underlyingType
    }

    // TODO: optimize.
    fun unwrappingInlinedClasses(type: IrType): Sequence<IrType> = generateSequence(type, { unwrapInlinedClass(it) })

    fun isNullable(type: IrType): Boolean = type.isMarkedNullable()

    fun makeNullable(type: IrType): IrType = type.makeNullable()

    tailrec fun erase(type: IrType): IrClass {
        val classifier = type.classifierOrFail
        return when (classifier) {
            is IrClassSymbol -> classifier.owner
            is IrTypeParameterSymbol -> erase(classifier.owner.superTypes.first())
            else -> error(classifier)
        }
    }

    fun computeFullErasure(type: IrType): Sequence<IrClass> {
        val classifier = type.classifierOrFail
        return when (classifier) {
            is IrClassSymbol -> sequenceOf(classifier.owner)
            is IrTypeParameterSymbol -> classifier.owner.superTypes.asSequence().flatMap { computeFullErasure(it) }
            else -> error(classifier)
        }
    }

    fun getFqName(clazz: IrClass): FqNameUnsafe = clazz.descriptor.fqNameUnsafe
    fun hasInlineModifier(clazz: IrClass): Boolean = clazz.isInline

    fun getInlinedClassUnderlyingType(clazz: IrClass): IrType =
        clazz.constructors.first { it.isPrimary }.valueParameters.single().type
}

fun getInlineClassBackingField(irClass: IrClass): IrField =
    irClass.declarations.flatMap { it ->
        when (it) {
            is IrField -> listOf(it)
            is IrProperty -> listOfNotNull(it.backingField)
            else -> listOf()
        }
    }.distinct().also {
        if (it.isEmpty()) {
            TODO()
        }
    }.single()

fun getInlineClassConstructor(irClass: IrClass): IrConstructor =
    irClass.declarations.filterIsInstance<IrConstructor>().single { it.isPrimary }

