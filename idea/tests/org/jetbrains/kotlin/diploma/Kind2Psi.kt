/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.*
import java.lang.IllegalArgumentException

class Kind2Psi(private val project: Project) {
    private val factory = KtPsiFactory(project)

    fun decode(predictedNode: String): KtElement = with(factory) {
        when (predictedNode) {
            AFTER_LAST_KIND -> throw Pipeline.AfterLastException
            "BOX_TEMPLATE" -> createFile("fun box() {}").also { it.children.first().delete(); it.children.first().delete() }

            "ANNOTATED_EXPRESSION" -> TODO()
            "ANNOTATION" -> TODO()
            "ANNOTATION_ENTRY" -> TODO()
            "ANNOTATION_TARGET" -> TODO()
            "ARRAY_ACCESS_EXPRESSION" -> TODO()
            "BINARY_EXPRESSION" -> {
                val file = createFile("fun foo() = dropMe != dropMe")
                val func = file.children.last() as KtNamedFunction
                val expr = func.children.last() as KtExpression
                expr.drop()
            }
            "BINARY_WITH_TYPE" -> TODO()
            "BLOCK" -> TODO()
            "BODY" -> TODO()
            "BOOLEAN_CONSTANT" -> createExpression("false")
            "BREAK" -> createExpression("break")
            "CALLABLE_REFERENCE_EXPRESSION" -> TODO()
            "CALL_EXPRESSION" -> createExpression("foo(bar)")
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
            "DOT_QUALIFIED_EXPRESSION" -> {
                val pack = createFile("package dropMe.dropMe").children.first() as KtPackageDirective
                val dot = pack.children.first() as KtDotQualifiedExpression
                dot.drop()
            }
            "DO_WHILE" -> createExpression("do { } while(dropMe)").drop()
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
            "IF" -> createExpression("if (dropMe) { } else { }").drop()
            "IMPORT_ALIAS" -> TODO()
            "IMPORT_DIRECTIVE" -> TODO()
            "IMPORT_LIST" -> TODO()
            "INDICES" -> TODO()
            "INITIALIZER_LIST" -> TODO()
            "INTEGER_CONSTANT" -> createExpression("123")
            "IS_EXPRESSION" -> {
                val expr = createExpression("t is Int")
                expr.apply { children.forEach { it.delete() } }
            }
            "LABEL" -> TODO()
            "LABELED_EXPRESSION" -> TODO()
            "LABEL_QUALIFIER" -> TODO()
            "LAMBDA_ARGUMENT" -> TODO()
            "LITERAL_STRING_TEMPLATE_ENTRY" -> TODO()
            "LAMBDA_EXPRESSION" -> createExpression("{ dropMe }").drop() as KtLambdaExpression
            "LONG_STRING_TEMPLATE_ENTRY" -> TODO()
            "LOOP_RANGE" -> TODO()
            "MODIFIER_LIST" -> TODO()
            "NULL" -> createExpression("null")
            "NULLABLE_TYPE" -> {
                val prop = createProperty("val x: Int? = 3")
                val type = prop.children.first() as KtTypeReference
                val nullable = type.children.single() as KtNullableType
                nullable.apply { children.single().delete() }
            }
            "OBJECT_DECLARATION" -> createObject("object Object {}")
            "OBJECT_LITERAL" -> TODO()
            "OPERATION_REFERENCE" -> {
                val file = createFile("fun foo() = x != y")
                val func = file.children.last() as KtNamedFunction
                val expr = func.children.last() as KtExpression
                expr.children[1] as KtOperationReferenceExpression
            }
            "PACKAGE_DIRECTIVE" -> TODO()
            "PARENTHESIZED" -> TODO()
            "POSTFIX_EXPRESSION" -> TODO()
            "PREFIX_EXPRESSION" -> TODO()
            "PRIMARY_CONSTRUCTOR" -> createPrimaryConstructor("()")
            "PROPERTY" -> createProperty("val prop = dropMe").drop()
            "PROPERTY_ACCESSOR" -> TODO()
            "PROPERTY_DELEGATE" -> TODO()
            "REFERENCE_EXPRESSION" -> createExpression("x")
            "RETURN" -> createExpression("return dropMe").drop()
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
            "TYPE_PARAMETER" -> {
                val func = createFunction("fun <T> foo() = false")
                val list = func.children.first() as KtTypeParameterList
                val type = list.children.first() as KtTypeParameter
                type
            }
            "TYPE_PARAMETER_LIST" -> {
                val func = createFunction("fun <T> foo() = false")
                val list = func.children.first() as KtTypeParameterList
                list.apply { children.forEach { it.delete() } }
            }
            "TYPE_PROJECTION" -> TODO()
            "TYPE_REFERENCE" -> {
                val prop = createProperty("val x: Int = 3")
                val type = prop.children.first() as KtTypeReference
                type.apply { children.single().delete() }
            }
            "USER_TYPE" -> {
                val prop = createProperty("val x: Int = 3")
                val type = prop.children.first() as KtTypeReference
                val userType = type.children.single() as KtUserType
                userType.apply { children.single().delete() }
            }
            "VALUE_ARGUMENT" -> createArgument("arg")
            "VALUE_ARGUMENT_LIST" -> createCallArguments("(x, y, z)")
            "VALUE_ARGUMENT_NAME" -> TODO()
            "VALUE_PARAMETER" -> {
                val file = createFile("fun foo(x) = false")
                val func = file.children.last() as KtNamedFunction
                val params = func.children.first() as KtParameterList
                params.children.first() as KtParameter
            }
            "VALUE_PARAMETER_LIST" -> TODO("generated as child automatically")
            "WHEN" -> TODO()
            "WHEN_CONDITION_IN_RANGE" -> TODO()
            "WHEN_CONDITION_IS_PATTERN" -> TODO()
            "WHEN_CONDITION_WITH_EXPRESSION" -> TODO()
            "WHEN_ENTRY" -> TODO()
            "WHILE" -> createExpression("while(dropMe) { }").drop()
            else -> throw IllegalArgumentException("Unsupported node: $predictedNode")
        }
    }

    private fun KtElement.drop(): KtElement = apply {
        acceptChildren(object : KtVisitorVoid() {
            override fun visitKtElement(element: KtElement) {
                element.acceptChildren(this)
            }

            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                expression.delete()
            }
        })
    }
}
