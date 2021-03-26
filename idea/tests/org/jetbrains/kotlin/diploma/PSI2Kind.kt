/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import org.jetbrains.kotlin.psi.*

// TODO: probably PSI contains some more information
val psi2kind = object : KtVisitor<String, Void?>() {
    val strategy: SparseDataStrategy = Drop

    override fun visitKtElement(element: KtElement, data: Void?) = element.node.elementType.toString()

    override fun visitKtFile(file: KtFile, data: Void?): String = "FILE"

    override fun visitNamedDeclaration(declaration: KtNamedDeclaration, data: Void?): String {
        return super.visitNamedDeclaration(declaration, data) + strategy.process(" " + declaration.name)
    }

    override fun visitConstantExpression(expression: KtConstantExpression, data: Void?): String {
        return super.visitConstantExpression(expression, data) + strategy.process(" " + expression.text)
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression, data: Void?): String {
        return super.visitSimpleNameExpression(expression, data) + strategy.process(" " + expression.getReferencedName())
    }

    override fun visitLiteralStringTemplateEntry(entry: KtLiteralStringTemplateEntry, data: Void?): String {
        return super.visitLiteralStringTemplateEntry(entry, data) + strategy.process(" \"${entry.text}\"")
    }

    override fun visitEscapeStringTemplateEntry(entry: KtEscapeStringTemplateEntry, data: Void?): String {
        return super.visitEscapeStringTemplateEntry(entry, data) + strategy.process(" \"${entry.text}\"")
    }

    // NOTE: there are some more StringEntry
    // > visitStringTemplateEntry
    // > visitStringTemplateEntryWithExpression
    // > visitBlockStringTemplateEntry
    // > visitSimpleNameStringTemplateEntry
}

private sealed class SparseDataStrategy {
    abstract fun process(data: String): String
}

private object NoProcess : SparseDataStrategy() {
    override fun process(data: String): String = data
}

private object Embeds : SparseDataStrategy() {
    override fun process(data: String): String = TODO("implement embedding")
}

private class MarkWith(val marker: String) : SparseDataStrategy() {
    override fun process(data: String): String = "$marker$data"
}

private object Drop : SparseDataStrategy() {
    override fun process(data: String): String = ""
}
