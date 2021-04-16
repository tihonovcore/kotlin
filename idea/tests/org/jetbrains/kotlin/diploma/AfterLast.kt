/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import com.intellij.extapi.psi.ASTDelegatePsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitor

const val AFTER_LAST_KIND = "AFTER_LAST"

private val instances = mutableListOf<PsiElement>()

fun PsiElement.isAfterLast(): Boolean = instances.any { it === this }

fun newAfterLast(project: Project): KtElement {
    return KtPsiFactory(project).createObject("object AfterLast")
}

fun PsiElement.addAfterLastEverywhere(): PsiElement {
    children.forEach { it.addAfterLastEverywhere() }
    if (this is KtElement) {
        instances += append(newAfterLast(project))
    }

    return this
}

fun `update instances if newChild is AfterLast`(predictedNode: String, newChild: KtElement) {
    if (predictedNode == AFTER_LAST_KIND) {
        instances += newChild
    }
}

fun `clear instances for new file`() {
    instances.clear()
}

object AfterLast : KtElement, ASTDelegatePsiElement() {
    override fun <D> acceptChildren(visitor: KtVisitor<Void, D>, data: D) {
        TODO("Not yet implemented")
    }

    override fun <R, D> accept(visitor: KtVisitor<R, D>, data: D): R {
        TODO("Not yet implemented")
    }

    override fun getNode(): ASTNode {
        TODO("Not yet implemented")
    }

    override fun getPsiOrParent(): KtElement {
        TODO("Not yet implemented")
    }

    override fun getContainingKtFile(): KtFile {
        TODO("Not yet implemented")
    }

    override fun getParent(): PsiElement {
        TODO("Not yet implemented")
    }

    override fun getTextRange(): TextRange {
        return TextRange.EMPTY_RANGE
    }
}
