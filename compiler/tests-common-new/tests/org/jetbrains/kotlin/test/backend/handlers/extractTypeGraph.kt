/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.backend.handlers

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.resolve.DescriptorUtils

fun extractTypeGraph(irFiles: List<IrFile>) = irFiles.forEach { file ->
    val classes = mutableListOf<IrClass>()
    file.acceptChildren(object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            element.acceptChildren(this, null)
        }

        override fun visitClass(declaration: IrClass) {
            classes += declaration
            declaration.acceptChildren(this, null)
        }
    }, null)

    val ir2description = buildDescriptions(classes)
    ir2description.forEach { (klass, descr) ->
        val name = klass.defaultType.classFqName?.asString() ?: "null"
        val members = descr.memberDependencies.map { it.name }
        val superclasses = descr.supertypeDependencies.map { it.name }

        val properties = descr.properties.map { it.name }
        val functions = descr.functions.map { (parameters, returnType) ->
            "(" + parameters.map { it.name }.joinToString() + ") -> " + returnType.name
        }

        println("$name --> \n\tmembers:    $members \n\tsuper:      $superclasses \n\tproperties: $properties \n\tfunctions:  $functions")
    }
}

data class ClassDescription(
    val name: String,
    val supertypeDependencies: MutableList<ClassDescription> = mutableListOf(),
    val memberDependencies: MutableList<ClassDescription> = mutableListOf(),
    val properties: MutableList<ClassDescription> = mutableListOf(),
    val functions: MutableList<Pair<List<ClassDescription>, ClassDescription>> = mutableListOf(),
)

private fun buildDescriptions(classes: List<IrClass>): Map<IrClass, ClassDescription> {
    //TODO: skip private/protected
    val ir2description = mutableMapOf<IrClass, ClassDescription>()
    classes.forEach { klass -> klass.toDescription(ir2description) }
    return ir2description
}

@OptIn(ObsoleteDescriptorBasedAPI::class)
private fun IrClass.toDescription(ir2description: MutableMap<IrClass, ClassDescription>): ClassDescription {
    val existsDescription = ir2description[this]
    if (existsDescription != null) return existsDescription

    val description = ClassDescription(name = this.defaultType.classFqName!!.asString())
    ir2description[this] = description

    val supertypeDependencies = getAllSupertypes()
        .mapNotNull { it.getClass() }
        .map { it.toDescription(ir2description) }

    val properties = declarations
        .filterIsInstance(IrProperty::class.java)
        .filterNot { it.isFakeOverride || DescriptorUtils.isOverride(it.symbol.descriptor) }
        .mapNotNull { it.getType().getClass() }
        .map { it.toDescription(ir2description) }

    val functions = declarations
        .filterIsInstance(IrFunction::class.java)
        .filterNot { it.isFakeOverride || DescriptorUtils.isOverride(it.symbol.descriptor) }
        .filterNot { it.returnType.classOrNull === symbol }
        .map { func -> func.valueParameters.map { param -> param.type } + func.returnType }
        .mapNotNull { types ->
            val classes = types.map { it.getClass() }
            if (classes.any { it == null }) return@mapNotNull null

            classes.map { it!!.toDescription(ir2description) }
        }
        .map { descriptions ->
            val parameters = descriptions.dropLast(1)
            val returnType = descriptions.last()

            Pair(parameters, returnType)
        }

    description.supertypeDependencies += supertypeDependencies
    description.memberDependencies += properties + functions.flatMap { (parameters, returnType) -> parameters + returnType }
    description.properties += properties
    description.functions += functions

    return description
}

private fun IrClass.getAllSupertypes(): List<IrType> {
    return superTypes + superTypes.flatMap { (it.classifierOrNull!!.owner as IrClass).getAllSupertypes() }
}

private fun IrProperty.getType(): IrType {
    return getter?.returnType ?: backingField!!.type
}

private fun IrType.getClass(): IrClass? {
    //NOTE: return `null` for generic type
    return classifierOrNull!!.owner as? IrClass
}
