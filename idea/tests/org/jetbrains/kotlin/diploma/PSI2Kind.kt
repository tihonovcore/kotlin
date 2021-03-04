/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import org.jetbrains.kotlin.psi.*

// TODO: probably PSI contains some more information
// TODO: `*.text` and `*.name` should be embedded, marked for doing this later or removed
val psi2kind = object : KtVisitor<String, Void?>() {

    override fun visitKtElement(element: KtElement, data: Void?) = element.node.elementType.toString()

    override fun visitNamedDeclaration(declaration: KtNamedDeclaration, data: Void?): String {
        return super.visitNamedDeclaration(declaration, data) + " " + declaration.name
    }

    override fun visitConstantExpression(expression: KtConstantExpression, data: Void?): String {
        return super.visitConstantExpression(expression, data) + " " + expression.text
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression, data: Void?): String {
        return super.visitSimpleNameExpression(expression, data) + " " + expression.getReferencedName()
    }

    override fun visitLiteralStringTemplateEntry(entry: KtLiteralStringTemplateEntry, data: Void?): String {
        return super.visitLiteralStringTemplateEntry(entry, data) + " " + "\"${entry.text}\""
    }

    override fun visitEscapeStringTemplateEntry(entry: KtEscapeStringTemplateEntry, data: Void?): String {
        return super.visitEscapeStringTemplateEntry(entry, data) + " " + "\"${entry.text}\""
    }

    // NOTE: there are some more StringEntry
    // > visitStringTemplateEntry
    // > visitStringTemplateEntryWithExpression
    // > visitBlockStringTemplateEntry
    // > visitSimpleNameStringTemplateEntry
}
