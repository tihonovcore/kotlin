/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.analysis

import com.google.gson.Gson
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.typeUtil.supertypes

data class ExtractedTypes(
    val functionDescriptors: List<FunctionDescriptor>,
    val functionSpecifications: List<JsonFunctionSpec>,
    val class2spec:Map<ClassifierDescriptor, JsonClassSpec>
)

fun extractTypes(file: KtFile): ExtractedTypes {
    val classes = mutableListOf<ClassifierDescriptor>()
    val functions = mutableListOf<FunctionDescriptor>()

    file.acceptChildren(object : KtVisitorVoid() {
        override fun visitKtElement(element: KtElement) {
            when (element) {
                is KtClass -> classes += element.descriptor as ClassifierDescriptor
                is KtConstructor<*> -> element.descriptor as FunctionDescriptor
                is KtFunction -> {
                    if (element.containingClassOrObject == null) {
                        functions += element.descriptor as FunctionDescriptor
                    }
                }
            }

            element.acceptChildren(this, null)
        }
    }, null)

    val (functionSpecs, class2spec) = buildSpecifications(classes, functions)

    class2spec.forEach { (_, specification) ->
        if (specification.isBasic) {
            specification.dependencies.clear()
        }
    }

    removeLoops(class2spec)

    val (jsonFunctionSpecs, jsonClass2spec) = Pair(functionSpecs, class2spec).withIntSpecification()
    return ExtractedTypes(functions, jsonFunctionSpecs, jsonClass2spec)
}

private fun buildSpecifications(classes: List<ClassifierDescriptor>, functions: List<FunctionDescriptor>): Pair<List<FunctionSpec>, Map<ClassifierDescriptor, ClassSpec>> {
    val class2description = mutableMapOf<ClassifierDescriptor, ClassSpec>()
    val functionDescriptions = functions.mapNotNull { function -> function.toSpecification(class2description) }
    classes.forEach { klass -> klass.toSpecification(class2description) }
    return Pair(functionDescriptions, class2description)
}

private fun ClassifierDescriptor.toSpecification(class2spec: MutableMap<ClassifierDescriptor, ClassSpec>): ClassSpec {
    val existsDescription = class2spec[this]
    if (existsDescription != null) return existsDescription

    val name = defaultType.fqName?.asString() ?: "null_name"
    val specification = ClassSpec(name, name.isBasic())
    class2spec[this] = specification

    val supertypeDependencies = defaultType
        .supertypes()
        .mapNotNull { it.constructor.declarationDescriptor }
        .map { it.toSpecification(class2spec) }

    val properties = defaultType.memberScope
        .getDescriptorsFiltered { true }
        .filterIsInstance(PropertyDescriptor::class.java)
        .filter { it.visibility !== DescriptorVisibilities.PRIVATE && it.visibility !== DescriptorVisibilities.PROTECTED }
        .filter { it.overriddenDescriptors.isEmpty() }
        .mapNotNull { it.type.constructor.declarationDescriptor }
        .map { it.toSpecification(class2spec) }

    val functions = defaultType.memberScope
        .getDescriptorsFiltered { true }
        .filterIsInstance(FunctionDescriptor::class.java)
        .filter { it.visibility !== DescriptorVisibilities.PRIVATE && it.visibility !== DescriptorVisibilities.PROTECTED }
        .filter { it.overriddenDescriptors.isEmpty() }
        .mapNotNull { it.toSpecification(class2spec) }

    specification.superTypes += supertypeDependencies
    specification.properties += properties
    specification.functions += functions

    specification.dependencies += supertypeDependencies
    specification.dependencies += properties
    specification.dependencies += functions.flatMap { it.dependencies }

    return specification
}

private fun FunctionDescriptor.toSpecification(class2spec: MutableMap<ClassifierDescriptor, ClassSpec>): FunctionSpec? {
    val descriptorsForParameters = valueParameters.map { it.type.constructor.declarationDescriptor }
    val descriptorForReturnType = returnType?.constructor?.declarationDescriptor

    if (descriptorsForParameters.any { it == null } || descriptorForReturnType == null) return null

    val specificationsForParameters = descriptorsForParameters.map { it!!.toSpecification(class2spec) }
    val specificationForReturnType = descriptorForReturnType.toSpecification(class2spec)

    return FunctionSpec(
        specificationsForParameters,
        specificationForReturnType,
        dependencies = specificationsForParameters + specificationForReturnType
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

private fun removeLoops(class2spec: Map<ClassifierDescriptor, ClassSpec>) {
    val grey = mutableListOf<ClassSpec>()
    val black = mutableListOf<ClassSpec>()

    fun ClassSpec.dfs(path: List<ClassSpec> = emptyList()) {
        if (grey.any { this === it }) {
            //remove loop
            val previous = path.last()
            previous.dependencies.removeIf { it === this }
            previous.functions.removeIf { function -> function.dependencies.any { it === this } }
            previous.properties.removeIf { it === this }
            previous.superTypes.removeIf { it === this }

            return
        }

        if (black.any { this === it }) {
            return
        }

        grey += this
        listOf(*dependencies.toTypedArray()).forEach { it.dfs(path = path + this) }
        grey.removeAt(grey.size - 1)
        black += this
    }

    class2spec.forEach { (_, specification) ->
        specification.dfs()
    }
}

fun Pair<List<FunctionSpec>, Map<ClassifierDescriptor, ClassSpec>>.withIntSpecification(): Pair<List<JsonFunctionSpec>, Map<ClassifierDescriptor, JsonClassSpec>> {
    val ids = mutableListOf<ClassSpec>()
    second.forEach { (_, specification) ->
        ids += specification
    }

    fun id(specification: ClassSpec): Int {
        return ids.indexOfFirst { it === specification }
    }

    val classes = mutableMapOf<ClassifierDescriptor, JsonClassSpec>()
    second.forEach { (classifier, description) ->
        classes[classifier] = JsonClassSpec(
            id(description),
            description.name,
            description.isBasic,
            description.superTypes.map { id(it) }.toHashSet(),
            description.properties.map { id(it) },
            description.functions.map { function ->
                val intParameters = function.parameters.map { id(it) }
                val intReturnType = id(function.returnType)
                val intDependencies = function.dependencies.map { id(it) }.toHashSet()
                JsonFunctionSpec(intParameters, intReturnType, intDependencies)
            },
            description.dependencies.map { id(it) }.toHashSet()
        )
    }

    val functions = first.map { function ->
        JsonFunctionSpec(
            function.parameters.map { id(it) },
            id(function.returnType),
            function.dependencies.map { id(it) }.toHashSet()
        )
    }

    return Pair(functions, classes)
}

fun ExtractedTypes.convertToJson(): String {
    val f = Gson().toJson(functionSpecifications)
    val c = Gson().toJson(class2spec.values.sortedBy { it.id })

    return "{\n    \"classes\":$c,\n    \"functions\":$f\n}"
}
