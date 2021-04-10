/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.tests

import org.jetbrains.kotlin.diploma.kind
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.test.TestMetadata

class Psi2KindTest : DiplomaTests() {

    @TestMetadata("getKinds")
    fun testGetKind() {
        val file = KtPsiFactory(project).createFile(
            """
            |class A(val name: String) {
            |    fun foo(val x: Int): String {
            |        return "name_${'$'}x"
            |    }
            |
            |    fun bar() {
            |        loop@while(x < y) {
            |            if (x == 100) {
            |                print(x)
            |                break@loop
            |            } else {
            |                print(y)
            |            }
            |        }
            |    }
            |}
            |
            |val x = A("John")
            |val y = x.bar()
            |""".trimMargin()
        )

        val expected = listOf(
            "FILE", "PACKAGE_DIRECTIVE", "IMPORT_LIST", "CLASS", "PRIMARY_CONSTRUCTOR", "VALUE_PARAMETER_LIST", "VALUE_PARAMETER",
            "TYPE_REFERENCE", "USER_TYPE", "REFERENCE_EXPRESSION", "CLASS_BODY", "FUN", "VALUE_PARAMETER_LIST", "VALUE_PARAMETER",
            "TYPE_REFERENCE", "USER_TYPE", "REFERENCE_EXPRESSION", "TYPE_REFERENCE", "USER_TYPE", "REFERENCE_EXPRESSION", "BLOCK",
            "RETURN", "STRING_TEMPLATE", "LITERAL_STRING_TEMPLATE_ENTRY", "SHORT_STRING_TEMPLATE_ENTRY", "REFERENCE_EXPRESSION",
            "FUN", "VALUE_PARAMETER_LIST", "BLOCK", "LABELED_EXPRESSION", "LABEL_QUALIFIER", "LABEL", "WHILE", "CONDITION",
            "BINARY_EXPRESSION", "REFERENCE_EXPRESSION", "OPERATION_REFERENCE", "REFERENCE_EXPRESSION", "BODY", "BLOCK", "IF",
            "CONDITION", "BINARY_EXPRESSION", "REFERENCE_EXPRESSION", "OPERATION_REFERENCE", "INTEGER_CONSTANT", "THEN", "BLOCK",
            "CALL_EXPRESSION", "REFERENCE_EXPRESSION", "VALUE_ARGUMENT_LIST", "VALUE_ARGUMENT", "REFERENCE_EXPRESSION", "BREAK",
            "LABEL_QUALIFIER", "LABEL", "ELSE", "BLOCK", "CALL_EXPRESSION", "REFERENCE_EXPRESSION", "VALUE_ARGUMENT_LIST",
            "VALUE_ARGUMENT", "REFERENCE_EXPRESSION", "PROPERTY", "CALL_EXPRESSION", "REFERENCE_EXPRESSION", "VALUE_ARGUMENT_LIST",
            "VALUE_ARGUMENT", "STRING_TEMPLATE", "LITERAL_STRING_TEMPLATE_ENTRY", "PROPERTY", "DOT_QUALIFIED_EXPRESSION",
            "REFERENCE_EXPRESSION", "CALL_EXPRESSION", "REFERENCE_EXPRESSION", "VALUE_ARGUMENT_LIST"
        )
        val actual = file.dfs().map { it.kind() }

        assertEquals(expected, actual)
    }
}
