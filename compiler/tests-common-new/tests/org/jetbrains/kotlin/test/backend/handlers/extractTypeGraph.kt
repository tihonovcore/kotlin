/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.backend.handlers

import com.google.gson.Gson
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.resolve.DescriptorUtils
import java.nio.file.Files
import java.nio.file.Paths

fun extractTypeGraph(irFiles: List<IrFile>) = irFiles.forEach { file ->
    val classes = mutableListOf<IrClass>()
    val functions = mutableListOf<IrFunction>()

    file.acceptChildren(object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            element.acceptChildren(this, null)
        }

        override fun visitClass(declaration: IrClass) {
            classes += declaration
            declaration.acceptChildren(this, null)
        }

        override fun visitFunction(declaration: IrFunction) {
            val parent = declaration.parent
            if (parent !is IrClass || parent is IrClass && declaration.symbol in parent.symbol.constructors) {
                functions += declaration
            }

            declaration.acceptChildren(this, null)
        }
    }, null)

    val (functionDescriptions, class2description) = buildDescriptions(classes, functions)

    class2description.forEach { (_, description) ->
        if (description.isBasic) {
            description.dependencies.clear()
        }
    }

    class2description.forEach { (_, description) ->
        removeLoops(description)
    }

    renderTypesDescription(functionDescriptions, class2description)

    createDatasetJson(file.name, functionDescriptions, class2description)
}

private fun removeLoops(first: ClassDescription) {
    val grey = mutableListOf<ClassDescription>()
    val black = mutableListOf<ClassDescription>()

    fun ClassDescription.dfs(from: List<ClassDescription> = emptyList()) {
        if (grey.any { this === it }) {
            //remove loop
            val previous = from.last()
            previous.dependencies.removeIf { it === this }
            previous.functions.removeIf { function -> function.first.any { it === this } || function.second === this }
            previous.properties.removeIf { it === this }
            previous.superTypes.removeIf { it === this }

            return
        }

        if (black.any { this === it }) {
            return
        }

        grey += this
        listOf(*dependencies.toTypedArray()).forEach { it.dfs(from + this) }
        grey.removeAt(grey.size - 1)
        black += this
    }

    first.dfs()
}

data class FunctionDescription(
    val parameters: List<ClassDescription> = mutableListOf(),
    val returnType: ClassDescription,
    val dependencies: List<ClassDescription> = mutableListOf(),
)

data class ClassDescription(
    val name: String,
    val isBasic: Boolean,
    val superTypes: MutableList<ClassDescription> = mutableListOf(),
    val properties: MutableList<ClassDescription> = mutableListOf(),
    val functions: MutableList<Pair<List<ClassDescription>, ClassDescription>> = mutableListOf(),
    val dependencies: MutableList<ClassDescription> = mutableListOf(),
)

private fun buildDescriptions(classes: List<IrClass>, functions: List<IrFunction>): Pair<List<FunctionDescription>, Map<IrClass, ClassDescription>> {
    //TODO: skip private/protected
    val class2description = mutableMapOf<IrClass, ClassDescription>()
    val functionDescriptions = functions.mapNotNull { function -> function.toDescription(class2description) }
    classes.forEach { klass -> klass.toDescription(class2description) }
    return Pair(functionDescriptions, class2description)
}

@OptIn(ObsoleteDescriptorBasedAPI::class)
private fun IrClass.toDescription(ir2description: MutableMap<IrClass, ClassDescription>): ClassDescription {
    val existsDescription = ir2description[this]
    if (existsDescription != null) return existsDescription

    val name = defaultType.classFqName!!.asString()
    val description = ClassDescription(name, name.isBasic())
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
        .filterNot { it.symbol in symbol.constructors }
        .filterNot { it.isFakeOverride || DescriptorUtils.isOverride(it.symbol.descriptor) }
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

    description.superTypes += supertypeDependencies
    description.properties += properties
    description.functions += functions

    description.dependencies += properties
    description.dependencies += functions.flatMap { (parameters, returnType) -> parameters + returnType }
    description.dependencies += supertypeDependencies

    return description
}

@OptIn(ObsoleteDescriptorBasedAPI::class)
private fun IrFunction.toDescription(ir2description: MutableMap<IrClass, ClassDescription>): FunctionDescription? {
    //use overridable only in supertype
    if (isFakeOverride || DescriptorUtils.isOverride(symbol.descriptor)) return null

    val irClassesForParameters = valueParameters.map { param -> param.type }.map { it.getClass() }
    val irClassForReturnType = returnType.getClass()

    if (irClassesForParameters.any { it == null } || irClassForReturnType == null) return null

    val descriptionsForParameters = irClassesForParameters.map { it!!.toDescription(ir2description) }
    val descriptionForReturnType = irClassForReturnType.toDescription(ir2description)

    return FunctionDescription(
        descriptionsForParameters,
        descriptionForReturnType,
        dependencies = descriptionsForParameters + descriptionForReturnType
    )
}

private fun String.isBasic(): Boolean {
    return this in listOf(
        "kotlin.Any",
        "kotlin.Byte",
        "kotlin.Char",
        "kotlin.Double",
        "kotlin.Float",
        "kotlin.Int",
        "kotlin.Long",
        "kotlin.Short",
        "kotlin.CharSequence",
        "kotlin.Boolean",
        "kotlin.Unit"
    )
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

private fun renderTypesDescription(functionDescriptions: List<FunctionDescription>, class2description: Map<IrClass, ClassDescription>) {
    functionDescriptions.forEach { function ->
        println("(" + function.parameters.joinToString { it.name } + ") -> " + function.returnType.name)
    }

    println()
    println()

    class2description.forEach { (klass, description) ->
        val name = klass.defaultType.classFqName?.asString() ?: "null"
        val dependencies = description.dependencies.map { it.name }
        val supertypes = description.superTypes.map { it.name }

        val properties = description.properties.map { it.name }
        val functions = description.functions.map { (parameters, returnType) ->
            "(" + parameters.joinToString { it.name } + ") -> " + returnType.name
        }

        val basic = if (description.isBasic) "(is basic) " else ""
        println("$name $basic--> \n\tdependencies: $dependencies \n\tsupertypes:   $supertypes \n\tproperties:   $properties \n\tfunctions:    $functions")
    }
}

data class JsonClassDescription(
    val id: Int,
    val name: String,
    val isBasic: Boolean,
    val superTypes: Set<Int>,
    val properties: List<Int>,
    val functions: List<JsonFunctionDescription>,
    val dependencies: Set<Int>,
)

data class JsonFunctionDescription(
    val parameters: List<Int>,
    val returnType: Int,
    val dependencies: Set<Int>,
)

private fun createDatasetJson(
    fileName: String,
    functionDescriptions: List<FunctionDescription>,
    class2description: Map<IrClass, ClassDescription>
) {
    val ids = mutableListOf<ClassDescription>()
    class2description.forEach { (_, description) ->
        ids += description
    }

    fun id(description: ClassDescription): Int {
        return ids.indexOfFirst { it === description }
    }

    val classes = mutableListOf<JsonClassDescription>()
    class2description.forEach { (_, description) ->
        classes += JsonClassDescription(
            id(description),
            description.name,
            description.isBasic,
            description.superTypes.map { id(it) }.toHashSet(),
            description.properties.map { id(it) },
            description.functions.map { (parameters, returnType) ->
                val intParameters = parameters.map { id(it) }
                val intReturnType = id(returnType)
                JsonFunctionDescription(intParameters, intReturnType, (intParameters + intReturnType).toHashSet())
            },
            description.dependencies.map { id(it) }.toHashSet()
        )
    }

    val functions = functionDescriptions.map { function ->
        JsonFunctionDescription(
            function.parameters.map { id(it) },
            id(function.returnType),
            function.dependencies.map { id(it) }.toHashSet()
        )
    }

    val c = Gson().toJson(classes)
    val f = Gson().toJson(functions)

    val dataset = Paths.get("/home/tihonovcore/diploma/kotlin/compiler/tests-common-new/tests/org/jetbrains/kotlin/test/backend/handlers/dataset")
    val path = Files.createTempFile(dataset, fileName, ".json")
    path.toFile().writeText(text = "{\n    \"classes\":$c,\n    \"functions\":$f\n}")
}
