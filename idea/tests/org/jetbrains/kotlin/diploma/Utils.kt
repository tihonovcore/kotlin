/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement
import java.io.File

fun PsiElement.renderTree(
    range2type: Map<TextRange, String>,
    tab: Int = 0
) {
    if (this is KtElement) {
        val kind = kind()
        val type = range2type[textRange]

        repeat(tab) { print("    ") }
        print(kind)
        if (type != null) print(" # $type ")
        println()
    }

    children.forEach { it.renderTree(range2type, tab + 1) }
}

fun List<PsiElement>.toDatasetStyle(range2type: Map<TextRange, String>): String {
    // NOTE: вроде бы от PSI там только комменты и пробелы,
    // поэтому можно оставить только KtElement
    val ktElements = filterIsInstance(KtElement::class.java)
    return ktElements.mapIndexed { index, element ->
        val next = ktElements.getOrNull(index + 1)
        val arrow = when {
            next == null -> ""
            element === next.parent -> "↓ "
            element.parent === next -> "↑ "
            else -> throw IllegalStateException("Neighbouring nodes aren't <parent, child> or <child, parent>")
        }

        val kind = element.kind()
        val type = range2type[element.textRange]

        if (type != null) "$kind # $type $arrow" else "$kind $arrow"
    }.joinToString("")
}

fun File.mustBeSkipped(): Boolean {
    if (isDirectory || extension != "kt") return true

    return with(readText()) {
        contains(Regex("//\\s*?FILE:"))
                || contains(Regex("//\\s*?WITH_RUNTIME"))
                || contains(Regex("//\\s*?FILE: .*?\\.java"))
                || name in listOf("kt30402.kt")
    }
}

fun PsiElement.kind(): String {
    if (this is KtElement) {
        return accept(psi2kind, null)
    }

    return "UNKNOWN_KIND: $this"
}
