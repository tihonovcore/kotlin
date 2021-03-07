/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement

class DatasetSample(
    val leafPaths: List<String>,
    val rootPath: String,
    val expectation: String
) {
    override fun toString(): String {
        return with(StringBuilder()) {
            fun <T> appendln(t: T) = append(t).append(System.lineSeparator())

            appendln(leafPaths.size)
            leafPaths.forEach { appendln(it) }
            appendln(rootPath)
            appendln(expectation)
        }.toString()
    }

    companion object {
        const val SAMPLE_SEPARATOR = "@@@"
    }
}

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
            getAllLeafPaths(root, maskedElement.parent).map { path -> path.toDatasetStyle(range2type) },
            getRootPath(root, maskedElement.parent).toDatasetStyle(range2type),
            maskedElement.accept(psi2kind, null)
        )
    }

private fun getAllLeafPaths(root: PsiElement, from: PsiElement): List<List<PsiElement>> {
    fun PsiElement.successors(): List<PsiElement> {
        if (children.isEmpty()) return listOf(this)

        return children.fold(mutableListOf(), { successors, element ->
            successors.apply { this += element.successors() }
        })
    }

    fun leafPaths(root: PsiElement, from: PsiElement, currentPath: List<PsiElement> = emptyList()): List<List<PsiElement>> {
        return when {
            from.children.isNotEmpty() -> {
                val actualChildren = if (from === root) from.children else from.children + from.parent
                val previousElementInPath = currentPath.lastOrNull()

                actualChildren.fold(mutableListOf(), { paths, element ->
                    if (element === previousElementInPath) return@fold paths
                    paths.apply { this += leafPaths(root, element, currentPath + from) }
                })
            }
            from is KtElement -> listOf(currentPath + from)
            else -> emptyList() //PsiComment, PsiWhitespace
        }
    }

    val successors = from.successors()
    val allPaths = leafPaths(root, from).map { it.reversed() }
    return allPaths.filter { it.first() !in successors }
}

private fun elementsFromDepth(psiElement: PsiElement, depth: Int): List<PsiElement> {
    if (depth == 0) {
        return listOf(psiElement)
    }

    return psiElement.children.fold(mutableListOf(), { nodes, element ->
        nodes += elementsFromDepth(element, depth - 1)
        nodes
    })
}

private fun getRootPath(root: PsiElement, maskedElement: PsiElement): List<PsiElement> {
    val path = mutableListOf(maskedElement)
    while (path.last() !== root) {
        path += path.last().parent
    }

    return path.reversed()
}
