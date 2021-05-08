/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.analysis

import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.typeUtil.supertypes

fun extractTypes(file: KtFile): String {
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

    val (functionDescriptions, class2description) = buildSpecifications(classes, functions)

    return ""
}

private fun buildSpecifications(classes: List<ClassifierDescriptor>, functions: List<FunctionDescriptor>): Pair<List<FunctionSpec>, Map<ClassifierDescriptor, ClassSpec>> {
    //TODO: skip private/protected
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
        .supertypes() //TODO: is transitive??
        .mapNotNull { it.constructor.declarationDescriptor }
        .map { it.toSpecification(class2spec) }

    val properties = defaultType.memberScope
        .getDescriptorsFiltered { true }
        .filterIsInstance(PropertyDescriptor::class.java)
        .filter { it.overriddenDescriptors.isEmpty() }
        .mapNotNull { it.type.constructor.declarationDescriptor }
        .map { it.toSpecification(class2spec) }

    val functions = defaultType.memberScope
        .getDescriptorsFiltered { true }
        .filterIsInstance(FunctionDescriptor::class.java)
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

