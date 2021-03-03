/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import org.jetbrains.kotlin.psi.*

val psi2kind = object : KtVisitor<String, Void?>() {

    override fun visitKtElement(element: KtElement, data: Void?) = element.node.elementType.toString()

    // TODO: probably PSI contains some more information
    override fun visitNamedDeclaration(declaration: KtNamedDeclaration, data: Void?): String {
        return super.visitNamedDeclaration(declaration, data) + " " + declaration.name
    }

    override fun visitConstantExpression(expression: KtConstantExpression, data: Void?): String {
        return super.visitConstantExpression(expression, data) + " " + expression.text
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression, data: Void?): String {
        return super.visitSimpleNameExpression(expression, data) + " " + expression.name
    }
}
