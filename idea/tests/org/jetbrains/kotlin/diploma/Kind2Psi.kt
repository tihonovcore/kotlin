/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.*
import java.lang.IllegalArgumentException

class Kind2Psi(project: Project) {
    private val factory = KtPsiFactory(project)

    fun decode(predictedNode: String): KtElement = with(factory) {
        when (predictedNode) {
            "BOX_TEMPLATE" -> createFile("fun box() {}")

            "ANNOTATED_EXPRESSION" -> TODO()
            "ANNOTATION" -> TODO()
            "ANNOTATION_ENTRY" -> TODO()
            "ANNOTATION_TARGET" -> TODO()
            "ARRAY_ACCESS_EXPRESSION" -> TODO()
            "BINARY_EXPRESSION" -> {
                val file = createFile("fun foo() = x != y")
                val func = file.children.last()
                val expr = func.children.last()
                expr.children.forEach { it.delete() }
                expr as KtElement
            }
            "BINARY_WITH_TYPE" -> TODO()
            "BLOCK" -> TODO()
            "BODY" -> TODO()
            "BOOLEAN_CONSTANT" -> TODO()
            "BREAK" -> TODO()
            "CALLABLE_REFERENCE_EXPRESSION" -> TODO()
            "CALL_EXPRESSION" -> TODO()
            "CATCH" -> TODO()
            "CHARACTER_CONSTANT" -> createExpression("'q'")
            "CLASS" -> createClass("class A {}")
            "CLASS_BODY" -> createEmptyClassBody()
            "CLASS_INITIALIZER" -> TODO()
            "CLASS_LITERAL_EXPRESSION" -> TODO()
            "COLLECTION_LITERAL_EXPRESSION" -> TODO()
            "CONDITION" -> TODO("generated as child automatically")
            "CONSTRUCTOR_CALLEE" -> TODO()
            "CONSTRUCTOR_DELEGATION_CALL" -> TODO()
            "CONSTRUCTOR_DELEGATION_REFERENCE" -> TODO()
            "CONTINUE" -> createExpression("continue")
            "DELEGATED_SUPER_TYPE_ENTRY" -> TODO()
            "DESTRUCTURING_DECLARATION" -> TODO()
            "DESTRUCTURING_DECLARATION_ENTRY" -> TODO()
            "DOT_QUALIFIED_EXPRESSION" -> TODO()
            "DO_WHILE" -> TODO()
            "DYNAMIC_TYPE" -> TODO()
            "ELSE" -> TODO("generated as child automatically")
            "ENUM_ENTRY" -> TODO()
            "ENUM_ENTRY_SUPERCLASS_REFERENCE_EXPRESSION" -> TODO()
            "ESCAPE_STRING_TEMPLATE_ENTRY" -> TODO()
            "FILE" -> createFile("") //.apply { children.forEach { it.delete() } }
            "FILE_ANNOTATION_LIST" -> TODO()
            "FINALLY" -> TODO()
            "FLOAT_CONSTANT" -> TODO()
            "FOR" -> TODO()
            "FUN" -> createFunction("fun foo() {}")
            "FUNCTION_LITERAL" -> TODO()
            "FUNCTION_TYPE" -> TODO()
            "FUNCTION_TYPE_RECEIVER" -> TODO()
            "IF" -> {
                createExpression("if (dropMe) { } else { }").apply {
                    acceptChildren(object : KtVisitorVoid() {
                        override fun visitKtElement(element: KtElement) {
                            element.acceptChildren(this)
                        }

                        override fun visitCallExpression(expression: KtCallExpression) {
                            expression.delete()
                        }
                    })
                }
            }
            "IMPORT_ALIAS" -> TODO()
            "IMPORT_DIRECTIVE" -> TODO()
            "IMPORT_LIST" -> TODO()
            "INDICES" -> TODO()
            "INITIALIZER_LIST" -> TODO()
            "INTEGER_CONSTANT" -> createExpression("123")
            "IS_EXPRESSION" -> TODO()
            "LABEL" -> TODO()
            "LABELED_EXPRESSION" -> TODO()
            "LABEL_QUALIFIER" -> TODO()
            "LAMBDA_ARGUMENT" -> TODO()
            "LAMBDA_EXPRESSION" -> TODO()
            "LITERAL_STRING_TEMPLATE_ENTRY" -> TODO()
            "LONG_STRING_TEMPLATE_ENTRY" -> TODO()
            "LOOP_RANGE" -> TODO()
            "MODIFIER_LIST" -> TODO()
            "NULL" -> TODO()
            "NULLABLE_TYPE" -> TODO()
            "OBJECT_DECLARATION" -> TODO()
            "OBJECT_LITERAL" -> TODO()
            "OPERATION_REFERENCE" -> {
                val file = createFile("fun foo() = x != y")
                val func = file.children.last()
                val expr = func.children.last()
                expr.children[1] as KtElement
            }
            "PACKAGE_DIRECTIVE" -> TODO()
            "PARENTHESIZED" -> TODO()
            "POSTFIX_EXPRESSION" -> TODO()
            "PREFIX_EXPRESSION" -> TODO()
            "PRIMARY_CONSTRUCTOR" -> TODO()
            "PROPERTY" -> TODO()
            "PROPERTY_ACCESSOR" -> TODO()
            "PROPERTY_DELEGATE" -> TODO()
            "REFERENCE_EXPRESSION" -> createExpression("x")
            "RETURN" -> TODO()
            "SAFE_ACCESS_EXPRESSION" -> TODO()
            "SECONDARY_CONSTRUCTOR" -> TODO()
            "SHORT_STRING_TEMPLATE_ENTRY" -> TODO()
            "STRING_TEMPLATE" -> TODO()
            "SUPER_EXPRESSION" -> TODO()
            "SUPER_TYPE_CALL_ENTRY" -> TODO()
            "SUPER_TYPE_ENTRY" -> TODO()
            "SUPER_TYPE_LIST" -> TODO()
            "THEN" -> TODO("generated as child automatically")
            "THIS_EXPRESSION" -> TODO()
            "THROW" -> TODO()
            "TRY" -> TODO()
            "TYPEALIAS" -> TODO()
            "TYPE_ARGUMENT_LIST" -> TODO()
            "TYPE_CONSTRAINT" -> TODO()
            "TYPE_CONSTRAINT_LIST" -> TODO()
            "TYPE_PARAMETER" -> TODO()
            "TYPE_PARAMETER_LIST" -> TODO()
            "TYPE_PROJECTION" -> TODO()
            "TYPE_REFERENCE" -> TODO()
            "USER_TYPE" -> TODO()
            "VALUE_ARGUMENT" -> TODO()
            "VALUE_ARGUMENT_LIST" -> TODO()
            "VALUE_ARGUMENT_NAME" -> TODO()
            "VALUE_PARAMETER" -> TODO()
            "VALUE_PARAMETER_LIST" -> TODO()
            "WHEN" -> TODO()
            "WHEN_CONDITION_IN_RANGE" -> TODO()
            "WHEN_CONDITION_IS_PATTERN" -> TODO()
            "WHEN_CONDITION_WITH_EXPRESSION" -> TODO()
            "WHEN_ENTRY" -> TODO()
            "WHILE" -> createExpression("while(dropMe) { }").apply {
                acceptChildren(object : KtVisitorVoid() {
                    override fun visitKtElement(element: KtElement) {
                        element.acceptChildren(this)
                    }

                    override fun visitReferenceExpression(expression: KtReferenceExpression) {
                        expression.delete()
                    }
                })
            }
            else -> throw IllegalArgumentException("Unsupported node: $predictedNode")
        }
    }
}
