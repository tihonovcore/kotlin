/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.cache

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.checkers.utils.TypedNode
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.diploma.*
import org.jetbrains.kotlin.diploma.analysis.convertToJson
import org.jetbrains.kotlin.diploma.analysis.extractTypes
import org.jetbrains.kotlin.idea.caches.resolve.checkFile
import org.jetbrains.kotlin.idea.caches.resolve.getMapPsiToTypeId
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.children
import java.io.File

/**
 * @return integer dataset sample and types info at json
 */
fun extractPathsFrom(path: String, project: Project): Pair<String, String> {
    val file = File(path)

    val ktFile = PsiManager.getInstance(project).findFile(file.toVirtualFile()!!) as KtFile
    val typesFromFile = extractTypes(ktFile)
    val class2spec = typesFromFile.second
    val (typedNodes, hasCompileErrors) = checkFile(ktFile)

    val (stringSample, from) = createSampleForFit(ktFile, getMapPsiToTypeId(class2spec, typedNodes), 5..25, 25)
    val integerSample = stringSample.toIntegerSample()

    val notFinished = mutableListOf(from.parent)
    while (notFinished.last() !== ktFile) {
        notFinished += notFinished.last().parent
    }

    save(ktFile, from, notFinished)
    attempts(new = 0)

    return Pair(integerSample.json(), typesFromFile.convertToJson())
}

private const val MAX_ATTEMPTS = 6

fun workWithPrediction(kind: String, type: Int, project: Project): KotlinResponse {
    val attempts = attempts()
    val (file, notFinished) = load(project)
    //TODO: saving and loading `typedNodes` is good practice, because
    // analysing some AST is impossible (e.g. binary_expression without operation node)
    // In this case `checkFile` says, that there are no one typed node
    val nodeForChildAddition = findNodeForChildAddition(file, notFinished) as KtElement

    try {
        val kind2Psi = NewKind2Psi(project)
        val decodedChild = kind2Psi.decode(kind).apply { children.forEach { it.delete() } }
        val appended = nodeForChildAddition.append(decodedChild)
        notFinished += appended

        //TODO: if current node is KtExpression and has NOT type, set predicted
        val (typedNodes, _) = checkFile(file)
        val typesFromFile = extractTypes(file)
        val class2spec = typesFromFile.second

        if (appended is KtReferenceExpression) {
            val typedChild = typedNodes.find { it.node === appended } ?: throw Exception("expression hasn't type :(")
            val suitableIdentifiers = typedChild.context.filter { class2spec[it.original as? ClassifierDescriptor]?.id == type }

            if (suitableIdentifiers.isNotEmpty()) {
                val name = (suitableIdentifiers.shuffled().first() as PropertyDescriptor).name.toString()
                val old = appended.node.children().find { it is KtToken && it.elementType === KtTokens.IDENTIFIER }!!

                appended.node.replaceChild(old, LeafPsiElement(KtTokens.IDENTIFIER, name))
            }
        }
        save(file, notFinished = notFinished)
        return extractPaths(file, notFinished, typedNodes)
    } catch (_: Pipeline.AfterLastException) {
        notFinished.removeIf { it === nodeForChildAddition }

        val (typedNodes, hasCompileErrors) = checkFile(file)
        if (hasCompileErrors) {
            if (attempts < MAX_ATTEMPTS) {
                attempts(new = attempts + 1)
                save(file, notFinished = notFinished)
                return extractPaths(file, notFinished, typedNodes)
            } else {
                return Fail
            }
        } else {
            return Success
        }
    }
}

private fun findNodeForChildAddition(element: PsiElement, notFinished: List<PsiElement>): PsiElement {
    for (child in element.children) {
        if (notFinished.any { child === it }) {
            return findNodeForChildAddition(child, notFinished)
        }
    }

    return element
}

private fun extractPaths(file: KtFile, notFinished: List<PsiElement>, typedNodes: List<TypedNode>): Paths {
    val typesFromFile = extractTypes(file)
    val class2spec = typesFromFile.second
    val stringSample = createSampleForPredict(
        file,
        findNodeForChildAddition(file, notFinished),
        notFinished.filterIsInstance(KtElement::class.java),
        getMapPsiToTypeId(class2spec, typedNodes)
    )
    val integerSample = stringSample.toIntegerSample()
    return Paths(integerSample.json(), typesFromFile.convertToJson())
}

sealed class KotlinResponse
object Success : KotlinResponse()
object Fail : KotlinResponse()
class Paths(val integerDatasetJson: String, val typesInfoJson: String) : KotlinResponse()
