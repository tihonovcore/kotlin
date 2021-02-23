/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.diploma

import org.jetbrains.kotlin.lexer.KotlinLexer
import java.lang.StringBuilder

fun tokenize(text: CharSequence): String {
    val lexer = KotlinLexer().also { it.start(text) }

    val result = StringBuilder()
    while (lexer.tokenType != null) {
        result.append(getTokenText(lexer.tokenType.toString(), lexer.tokenText))
        lexer.advance()
    }
    return result.toString()
}

private fun getTokenText(tokenType: String, tokenText: String): String {
    val tokenDelimiter = "###"

    //TODO: use KtTokens
    val semanticless = listOf("COMMENT", "KDoc", "WHITE_SPACE")
    val sparse = listOf("IDENTIFIER", "REGULAR_STRING_PART", "LITERAL", "CONSTANT")
    fun typeIs(list: List<String>) = list.any { s -> tokenType.contains(s) }

    return when {
        typeIs(semanticless) -> ""
        typeIs(sparse) -> tokenDelimiter + tokenType
        else -> tokenDelimiter + tokenText
    }
}
