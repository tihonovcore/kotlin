/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement

fun PsiElement.renderTree(
    range2type: Map<TextRange, String>,
    tab: Int = 0
) {
    if (this is KtElement) {
        val kind = accept(psi2kind, null)
        val type = range2type[textRange]

        repeat(tab) { print("    ") }
        print(kind)
        if (type != null) print(" # $type ")
        println()
    }

    children.forEach { it.renderTree(range2type, tab + 1) }
}

//TODO: add arrows ↓ ↑
fun List<KtElement>.asPath(
    range2type: Map<TextRange, String>,
    separator: String
) = joinToString(separator) { element ->
    val kind = element.accept(psi2kind, null)
    val type = range2type[element.textRange]

    if (type != null) "$kind # $type " else "$kind "
}

fun getAllLeafPaths(root: PsiElement, from: PsiElement): List<List<PsiElement>> {
    val actualChild = from.parent
    return actualChild.getAllLeafPaths(root, from, listOf(from))
}

private fun PsiElement.getAllLeafPaths(
    root: PsiElement,
    actualParent: PsiElement,
    currentPath: List<PsiElement>
): List<List<PsiElement>> {
    return if (children.isEmpty()) {
        listOf(currentPath + this)
    } else {
        val paths = mutableListOf<List<PsiElement>>()
        val actualChildren = if (root !== this) children + parent else children

        actualChildren.forEach { element ->
            if (element === actualParent) return@forEach
            paths += element.getAllLeafPaths(root, this, currentPath + this)
        }

        paths
    }
}
