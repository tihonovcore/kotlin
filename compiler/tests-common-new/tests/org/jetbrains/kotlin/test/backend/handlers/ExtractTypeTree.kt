/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.backend.handlers

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import kotlin.system.exitProcess

fun extractTypeTree(irFiles: List<IrFile>) = irFiles.forEach { file ->
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
        val description = descr.memberDependencies.map { it.name }
        println("$name --> $description")
    }
}

data class ClassDescription(
    val name: String,
    val supertypeDependencies: MutableList<ClassDescription> = mutableListOf(),
    val memberDependencies: MutableList<ClassDescription> = mutableListOf(),
    val properties: MutableList<ClassDescription> = mutableListOf(),
//        val functions: MutableList<Pair<List<ClassDescription>, ClassDescription>> = mutableListOf(),
)

private fun buildDescriptions(classes: List<IrClass>): Map<IrClass, ClassDescription> {
    //TODO: allClasses += superClasses
    //TODO: skip private/protected
    val ir2description = mutableMapOf<IrClass, ClassDescription>()
    classes.forEach { klass -> klass.toDescription(ir2description) }
    return ir2description
}

private fun IrClass.toDescription(ir2description: MutableMap<IrClass, ClassDescription>): ClassDescription {
    val existsDescription = ir2description[this]
    if (existsDescription != null) return existsDescription

    val description = ClassDescription(this.defaultType.classFqName?.asString() ?: "null")
    ir2description[this] = description

    val memberDependencies = declarations
        .filterIsInstance(IrProperty::class.java)
        .map { it.getter?.returnType ?: it.backingField!!.type }
        .map { it.classifierOrNull!! }
        .map { it.owner as IrClass }
        .map { it.toDescription(ir2description) }
        .toMutableList()

    description.memberDependencies += memberDependencies
    description.properties += memberDependencies

    return description
}
