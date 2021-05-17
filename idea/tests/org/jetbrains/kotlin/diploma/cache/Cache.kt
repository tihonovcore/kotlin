/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.cache

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.NotNull
import org.jetbrains.kotlin.diploma.NewKind2Psi
import org.jetbrains.kotlin.diploma.json
import org.jetbrains.kotlin.diploma.kind
import org.jetbrains.kotlin.lexer.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.children
import java.io.File

private const val ast = "/home/tihonovcore/diploma/kotlin/idea/tests/org/jetbrains/kotlin/diploma/cache/cacheExample.json"
private const val attempts = "/home/tihonovcore/diploma/kotlin/idea/tests/org/jetbrains/kotlin/diploma/cache/attempts.json"

private val ktTokensInstance = object : KtTokens {}

fun attempts(): Int {
    return JsonParser.parseString(File(attempts).readText()).asJsonObject["attempts"].asInt
}

fun attempts(new: Int) {
    File(attempts).writeText("{ \"attempts\": $new }")
}

fun save(
    file: KtFile,
    except: PsiElement? = null,
    notFinished: List<PsiElement> = emptyList()
) {
    val json = file.encode(except, notFinished).json()
    File(ast).writeText(json)
}

/**
 * @return decoded file and list of not finished elements
 */
fun load(project: Project): Pair<KtFile, MutableList<PsiElement>> {
    val json = File(ast).readText()

    val tree = Gson().fromJson(json, JsonTree::class.java)
    val factory = KtPsiFactory(project)
    val kind2Psi = NewKind2Psi(project)

    val notFinished = mutableListOf<PsiElement>()
    return tree.decode(kind2Psi, factory, notFinished) as KtFile to notFinished
}

private data class JsonTree(
    val kind: String,
    val text: String = "",
    var finished: Boolean = true,
    val children: MutableList<JsonTree> = mutableListOf()
)

private fun PsiElement.encode(
    except: PsiElement? = null,
    notFinished: List<PsiElement>
): JsonTree {
    val tree = JsonTree(kind = kind())
    if (notFinished.any { it === this }) {
        tree.finished = false
    }

    var skipInnerNodes = false
    for (child in node.children()) {
        if (child.psi === except) {
            skipInnerNodes = true
        }

        if (child.psi is LeafPsiElement) {
            val kind = KtTokens::class.java.fields.find { it.get(ktTokensInstance) === child.elementType }!!.name
            val text = child.text

            tree.children += JsonTree(kind, text)
        } else if (!skipInnerNodes) {
            tree.children += child.psi.encode(except, notFinished)
        }
    }

    return tree
}

private fun JsonTree.decode(kind2Psi: NewKind2Psi, factory: KtPsiFactory, notFinished: MutableList<PsiElement>): PsiElement {
    val tokenTypeOrNull = KtTokens::class.java.fields.find { it.name == kind }
    val element = if (tokenTypeOrNull != null) {
        LeafPsiElement(tokenTypeOrNull.get(ktTokensInstance) as @NotNull IElementType, text)
    } else {
        kind2Psi.decode(kind).apply { deleteChildRange(firstChild, lastChild) }
    }

    children.forEach { child ->
        val decodedChild = child.decode(kind2Psi, factory, notFinished)
        element.node.addChild(decodedChild.node)
    }

    if (!finished) {
        notFinished += element
    }

    return element
}
