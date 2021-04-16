/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement

private class AstWithAfterLast(
    val original: PsiElement,
    val parent: AstWithAfterLast?,
    val children: MutableList<AstWithAfterLast> = mutableListOf()
)

fun new_createDatasetSamples(
    root: PsiElement,
    range2type: Map<TextRange, String>,
    depth: Int,
    countSamples: Int
): List<DatasetSample> {
    return buildTree(root, null)
        .addAfterLast()
        .elementsFromDepth(depth)
        .shuffled()
        .take(countSamples)
        .map { targetElement ->
            val targetIndex = targetElement.parent!!.children.indexOf(targetElement)

            DatasetSample(
                getLeafPaths(targetElement.parent, targetIndex).unbox().map { path -> path.toDatasetStyle(range2type) },
                getRootPath(targetElement.parent).unbox().toDatasetStyle(range2type),
                targetIndex,
                targetElement.original.kind()
            )
        }
}

private fun buildTree(element: PsiElement, parent: AstWithAfterLast?): AstWithAfterLast {
    val currentTree = AstWithAfterLast(element, parent)
    currentTree.children += element.children.filterIsInstance(KtElement::class.java).map { buildTree(it, currentTree) }

    return currentTree
}

private fun AstWithAfterLast.addAfterLast(): AstWithAfterLast = apply {
    children.forEach { it.addAfterLast() }
    children += AstWithAfterLast(AfterLast, parent = this)
}

private fun AstWithAfterLast.elementsFromDepth(depth: Int): List<AstWithAfterLast> {
    if (depth == 0) {
        return listOf(this)
    }

    return children.fold(mutableListOf()) { nodes, element ->
        nodes.apply { this += element.elementsFromDepth(depth - 1) }
    }
}

private fun getLeafPaths(from: AstWithAfterLast, targetIndex: Int): List<List<AstWithAfterLast>> {
    fun AstWithAfterLast.successors(): List<AstWithAfterLast> {
        if (children.isEmpty()) return listOf(this)

        return children.fold(mutableListOf()) { successors, element ->
            successors.apply { this += element.successors() }
        }
    }

    fun leafPaths(from: AstWithAfterLast, currentPath: List<AstWithAfterLast> = emptyList()): List<List<AstWithAfterLast>> {
        val previousElementInPath = currentPath.lastOrNull()

        val actualChildren = from.children.toMutableList()
        if (from.parent != null) actualChildren += from.parent
        actualChildren.remove(previousElementInPath)

        if (actualChildren.isEmpty() && from.parent != null) {
            return listOf(currentPath + from)
        }

        return actualChildren.fold(mutableListOf()) { paths, element ->
            paths.apply { this += leafPaths(element, currentPath + from) }
        }
    }

    val successors = from.children.toList().drop(targetIndex).flatMap { it.successors() }
    val allPaths = leafPaths(from).map { it.reversed() }
    return allPaths.filter { it.first() !in successors }
}

private fun getRootPath(from: AstWithAfterLast): List<AstWithAfterLast> {
    val path = mutableListOf(from)
    while (true) {
        val parent = path.last().parent
        if (parent != null) path += parent else break
    }

    return path.reversed()
}

@JvmName("unboxAstWithAfterLast")
private fun List<AstWithAfterLast>.unbox(): List<PsiElement> {
    return map { it.original }
}

private fun List<List<AstWithAfterLast>>.unbox(): List<List<PsiElement>> {
    return map { path -> path.unbox() }
}
