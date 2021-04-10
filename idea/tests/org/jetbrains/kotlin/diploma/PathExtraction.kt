/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement

data class DatasetSample(
    val leafPaths: List<List<String>>,
    val rootPath: List<String>,
    val indexAmongBrothers: Int,
    val target: String? = null
)

data class IntegerDatasetSample(
    val leafPaths: List<List<Int>>,
    val rootPath: List<Int>,
    val indexAmongBrothers: Int,
    val target: Int? = null
)

/**
 * Берем `countSamples` вершин с глубины `depth`. Необходимо предсказать эти
 * вершины и `nodeForPrediction - 1` их потомков
 */
fun createDatasetSamples(
    root: PsiElement,
    range2type: Map<TextRange, String>,
    depth: Int,
    countSamples: Int,
    nodeForPrediction: Int // TODO: support several prediction
): List<DatasetSample> = elementsFromDepth(root, depth)
    .shuffled()
    .filterIsInstance(KtElement::class.java)
    .take(countSamples)
    .map { maskedElement ->
        DatasetSample(
            getAllLeafPaths(root, maskedElement.parent, dropSuccessors = true).map { path -> path.toDatasetStyle(range2type) },
            getRootPath(root, maskedElement.parent).toDatasetStyle(range2type),
            maskedElement.parent.children.indexOf(maskedElement),
            maskedElement.kind()
        )
    }

fun extractPaths(
    root: PsiElement,
    from: PsiElement,
    range2type: Map<TextRange, String> = emptyMap()
): DatasetSample = DatasetSample(
    getAllLeafPaths(root, from, dropSuccessors = false).map { path -> path.toDatasetStyle(range2type) },
    getRootPath(root, from).toDatasetStyle(range2type),
    from.children.size
)

private fun getAllLeafPaths(root: PsiElement, from: PsiElement, dropSuccessors: Boolean): List<List<PsiElement>> {
    fun PsiElement.successors(): List<PsiElement> {
        if (children.isEmpty()) return listOf(this)

        return children.fold(mutableListOf()) { successors, element ->
            successors.apply { this += element.successors() }
        }
    }

    fun leafPaths(root: PsiElement, from: PsiElement, currentPath: List<PsiElement> = emptyList()): List<List<PsiElement>> {
        return when (from) {
            is KtElement -> {
                val previousElementInPath = currentPath.lastOrNull()

                val actualChildren = from.children.toMutableList()
                if (from !== root) actualChildren += from.parent
                actualChildren -= previousElementInPath

                if (actualChildren.isEmpty() && from !== root) {
                    return listOf(currentPath + from)
                }

                actualChildren.fold(mutableListOf()) { paths, element ->
                    paths.apply { this += leafPaths(root, element, currentPath + from) }
                }
            }
            else -> emptyList() //PsiComment, PsiWhitespace
        }
    }

    val successors = if (dropSuccessors) from.successors() else emptyList()
    val allPaths = leafPaths(root, from).map { it.reversed() }
    return allPaths.filter { it.first() !in successors }
}

private fun elementsFromDepth(psiElement: PsiElement, depth: Int): List<PsiElement> {
    if (depth == 0) {
        return listOf(psiElement)
    }

    return psiElement.children.fold(mutableListOf()) { nodes, element ->
        nodes.apply { this += elementsFromDepth(element, depth - 1) }
    }
}

private fun getRootPath(root: PsiElement, maskedElement: PsiElement): List<PsiElement> {
    val path = mutableListOf(maskedElement)
    while (path.last() !== root) {
        path += path.last().parent
    }

    return path.reversed()
}

val testOnlyGetAllLeafPaths: (PsiElement, PsiElement, Boolean) -> List<List<PsiElement>> = ::getAllLeafPaths
val testOnlyElementsFromDepth: (PsiElement, Int) -> List<PsiElement> = ::elementsFromDepth
val testOnlyGetRootPath: (PsiElement, PsiElement) -> List<PsiElement> = ::getRootPath
