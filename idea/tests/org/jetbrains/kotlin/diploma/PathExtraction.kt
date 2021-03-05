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
    .take(countSamples)
    .map { maskedElement ->
        DatasetSample(
            getAllLeafPaths(root, maskedElement).map { path -> path.toDatasetStyle(range2type) },
            getRootPath(root, maskedElement).toDatasetStyle(range2type),
            (maskedElement as KtElement).accept(psi2kind, null)
        )
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
