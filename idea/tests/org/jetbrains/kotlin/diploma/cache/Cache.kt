/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.cache

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.diploma.NewKind2Psi
import org.jetbrains.kotlin.diploma.json
import org.jetbrains.kotlin.diploma.kind
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.children
import java.io.File

private const val cache = "/home/tihonovcore/diploma/kotlin/idea/tests/org/jetbrains/kotlin/diploma/cache/cacheExample.json"

fun save(file: KtFile, except: PsiElement) {
    val json = file.encode(except).json()
    File(cache).writeText(json)
}

fun load(project: Project): KtFile {
    val json = File(cache).readText()

    val tree = Gson().fromJson(json, JsonTree::class.java)
    val factory = KtPsiFactory(project)
    val kind2Psi = NewKind2Psi(project)

    return tree.decode(kind2Psi, factory) as KtFile
}

private data class JsonTree(
    val kind: String,
    val text: String = "",
    val children: MutableList<JsonTree> = mutableListOf()
)

private fun PsiElement.encode(except: PsiElement? = null): JsonTree {
    val tree = JsonTree(kind = kind())

    for (child in node.children()) {
        if (child.psi === except) return tree

        if (child.psi is LeafPsiElement) {
            val kind = child.elementType::class.java.simpleName
            val text = child.text

            tree.children += JsonTree(kind, text)
        } else {
            tree.children += child.psi.encode(except)
        }
    }

    return tree
}

private fun JsonTree.decode(kind2Psi: NewKind2Psi, factory: KtPsiFactory): PsiElement {
    val element = when (kind) {
        "IElementType" -> factory.createWhiteSpace(text)
        "KtModifierKeywordToken" -> LeafPsiElement(KtModifierKeywordToken.keywordModifier(text), text)
        "KtKeywordToken" -> LeafPsiElement(KtKeywordToken.keyword(text), text)
        "KtSingleValueToken" -> LeafPsiElement(KtSingleValueToken("LOAD_CACHE", text), text)
        "KtToken" -> LeafPsiElement(KtToken("LOAD_CACHE"), text)
        else -> kind2Psi.decode(kind).apply { deleteChildRange(firstChild, lastChild) }
    }

    children.forEach { child ->
        val decodedChild = child.decode(kind2Psi, factory)
        element.node.addChild(decodedChild.node)
    }

    return element
}
