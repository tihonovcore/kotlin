/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.analysis

data class FunctionSpec(
    val parameters: List<ClassSpec> = mutableListOf(),
    val returnType: ClassSpec,
    val dependencies: List<ClassSpec> = mutableListOf(),
)

data class ClassSpec(
    val name: String,
    val isBasic: Boolean,
    val superTypes: MutableList<ClassSpec> = mutableListOf(),
    val properties: MutableList<ClassSpec> = mutableListOf(),
    val functions: MutableList<FunctionSpec> = mutableListOf(),
    val dependencies: MutableList<ClassSpec> = mutableListOf(),
)

data class JsonClassSpec(
    val id: Int,
    val name: String,
    val isBasic: Boolean,
    val superTypes: Set<Int>,
    val properties: List<Int>,
    val functions: List<JsonFunctionSpec>,
    val dependencies: Set<Int>,
)

data class JsonFunctionSpec(
    val parameters: List<Int>,
    val returnType: Int,
    val dependencies: Set<Int>,
)
