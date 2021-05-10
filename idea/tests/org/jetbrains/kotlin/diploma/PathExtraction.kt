/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement
import kotlin.random.Random

class AstWithAfterLast(
    val original: PsiElement,
    val parent: AstWithAfterLast?,
    val children: MutableList<AstWithAfterLast> = mutableListOf()
) {
    fun renderTree(tab: Int = 0) {
        if (original is KtElement) {
            val kind = original.kind()

            repeat(tab) { print("    ") }
            println(kind)
        }

        children.forEach { it.renderTree(tab + 1) }
    }

}

private fun List<PsiElement>.typeInfo(psi2typeId: Map<PsiElement, Int>): List<Pair<Int, Int>> {
    return mapIndexedNotNull { index, node ->
        val id = psi2typeId[node] ?: return@mapIndexedNotNull null
        Pair(index, id)
    }
}

fun createSamplesForDataset(
    root: PsiElement,
    psi2typeId: Map<PsiElement, Int>,
    depth: IntRange,
    samplesCount: Int
): List<StringDatasetSample> {
    return buildTree(root, null)
        .addAfterLast()
        .elementsFromDepth(depth)
        .smartlyTake(samplesCount)
        .map { targetElement ->
            val targetIndex = targetElement.parent!!.children.indexOf(targetElement)

            val leafPaths = getLeafPaths(targetElement.parent, targetIndex).unbox()
            val rootPath = getRootPath(targetElement.parent).unbox()
            val typesForLeafPaths = leafPaths.map { path -> path.typeInfo(psi2typeId) }
            val typesForRootPath = rootPath.typeInfo(psi2typeId)

            StringDatasetSample(
                leafPaths = leafPaths.map { path -> path.toDatasetStyle() },
                rootPath = rootPath.toDatasetStyle(),
                typesForLeafPaths = typesForLeafPaths,
                typesForRootPath = typesForRootPath,
                leftBrothers = targetElement.parent.children.take(targetIndex).map { it.original.kind() },
                indexAmongBrothers = targetIndex,
                target = targetElement.original.kind()
            )
        }
}

fun createSampleForPredict(
    root: PsiElement,
    from: PsiElement,
    notFinished: List<KtElement>,
    psi2typeId: Map<PsiElement, Int>,
): StringDatasetSample {
    val targetIndex = from.children.size

    return buildTree(root, null)
        .addAfterLast(notFinished)
        .also { println(root.text); println(from.kind()); it.renderTree(); println() }
        .findNode(from)
        .let { wrappedFrom ->
            val leafPaths = getLeafPaths(wrappedFrom!!, targetIndex).unbox()
            val rootPath = getRootPath(wrappedFrom).unbox()
            val typesForLeafPaths = leafPaths.map { path -> path.typeInfo(psi2typeId) }
            val typesForRootPath = rootPath.typeInfo(psi2typeId)

            StringDatasetSample(
                leafPaths = leafPaths.map { path -> path.toDatasetStyle() },
                rootPath = rootPath.toDatasetStyle(),
                typesForLeafPaths = typesForLeafPaths,
                typesForRootPath = typesForRootPath,
                leftBrothers = wrappedFrom.children.map { it.original.kind() },
                indexAmongBrothers = targetIndex
            )
        }
}

fun buildTree(element: PsiElement, parent: AstWithAfterLast?): AstWithAfterLast {
    val currentTree = AstWithAfterLast(element, parent)
    currentTree.children += element.children.filterIsInstance(KtElement::class.java).map { buildTree(it, currentTree) }

    return currentTree
}

fun AstWithAfterLast.addAfterLast(except: List<KtElement> = emptyList()): AstWithAfterLast = apply {
    children.forEach { it.addAfterLast(except) }

    if (except.all { original !== it }) {
        children += AstWithAfterLast(AfterLast, parent = this)
    }
}

private fun AstWithAfterLast.elementsFromDepth(depth: IntRange): List<AstWithAfterLast> {
    return depth.flatMap { elementsFromDepth(it) }
}

private fun AstWithAfterLast.elementsFromDepth(depth: Int): List<AstWithAfterLast> {
    if (depth == 0) {
        return listOf(this)
    }

    return children.fold(mutableListOf()) { nodes, element ->
        nodes.apply { this += element.elementsFromDepth(depth - 1) }
    }
}

private fun AstWithAfterLast.findNode(element: PsiElement): AstWithAfterLast? {
    if (original === element) {
        return this
    }

    return children.mapNotNull { it.findNode(element) }.singleOrNull()
}

private fun List<AstWithAfterLast>.smartlyTake(samplesCount: Int): List<AstWithAfterLast> {
    val notAfterLast = filterNot { it.original === AfterLast }.shuffled().toMutableList()
    val afterLast = filter { it.original === AfterLast }.shuffled().toMutableList()

    val rankedMerge = mutableListOf<AstWithAfterLast>()
    while (afterLast.isNotEmpty() || notAfterLast.isNotEmpty()) {
        if (afterLast.isEmpty() || notAfterLast.isEmpty()) {
            rankedMerge += notAfterLast
            rankedMerge += afterLast
            break
        }

        rankedMerge += if (Random.nextDouble() < 0.95) notAfterLast.removeLast() else afterLast.removeLast()
    }

    return rankedMerge.take(samplesCount)
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

@JvmName("unboxList")
private fun List<AstWithAfterLast>.unbox(): List<PsiElement> {
    return map { it.original }
}

@JvmName("unboxListList")
private fun List<List<AstWithAfterLast>>.unbox(): List<List<PsiElement>> {
    return map { path -> path.unbox() }
}

fun testOnlyBuildTree(element: PsiElement, parent: AstWithAfterLast?) = buildTree(element, parent)
fun AstWithAfterLast.testOnlyAddAfterLast(except: List<KtElement>) = addAfterLast(except)
fun AstWithAfterLast.testOnlyElementsFromDepth(depth: Int) = elementsFromDepth(depth)
fun AstWithAfterLast.testOnlyFindNode(element: PsiElement) = findNode(element)
fun testOnlyGetLeafPaths(from: AstWithAfterLast, targetIndex: Int) = getLeafPaths(from, targetIndex)
fun testOnlyGetRootPath(from: AstWithAfterLast) = getRootPath(from)
