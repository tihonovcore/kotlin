/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement

fun prepareAndPrintDatasetSample(
    root: PsiElement,
    range2type: Map<TextRange, String>,
    depth: Int,
    countSamples: Int,
    nodeForPrediction: Int
) {
    val maskedNodes = elementsFromDepth(root, depth).shuffled().take(countSamples)

    maskedNodes.forEachIndexed { index, maskedElement ->
        println("########################### $index")
        println("LEAF PATHS: ")
        getAllLeafPaths(root, maskedElement).forEach { path ->
            println(path.filterIsInstance(KtElement::class.java).asPath(range2type, "↓ "))
        }
        println("ROOT PATH: ")
        getRootPath(root, maskedElement).also { path ->
            println(path.filterIsInstance(KtElement::class.java).asPath(range2type, "↓ "))
        }
        println("EXPECTED KIND: ${(maskedElement as KtElement).accept(psi2kind, null)}")
        print("\n\n\n")
    }
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
