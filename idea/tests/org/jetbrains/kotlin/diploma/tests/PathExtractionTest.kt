/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.tests

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diploma.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.test.TestMetadata

class PathExtractionTest : DiplomaTests() {
    private fun createFile(): KtElement {
        return KtPsiFactory(project).createFile(
            """
            |class X {
            |    fun bar() {
            |        while(x < y) {
            |            if (x == 100) {
            |                print(x)
            |            }
            |        }
            |    }
            |}
            |""".trimMargin()
        )
    }

    @TestMetadata("getRootPath")
    fun testGetRootPath() {
        val file = createFile()
        val ids = file.dfs().filterIsInstance(KtReferenceExpression::class.java)

        val loop = listOf("FILE", "CLASS", "CLASS_BODY", "FUN", "BLOCK", "WHILE")
        val cond = listOf("CONDITION", "BINARY_EXPRESSION", "REFERENCE_EXPRESSION")
        val call = listOf("BODY", "BLOCK", "IF", "THEN", "BLOCK", "CALL_EXPRESSION")
        val expectedPaths = listOf(
            loop + cond, //x
            loop + cond, //y
            loop + listOf("BODY", "BLOCK", "IF", "CONDITION", "BINARY_EXPRESSION", "REFERENCE_EXPRESSION"), //x
            loop + call + "REFERENCE_EXPRESSION", //print
            loop + call + listOf("VALUE_ARGUMENT_LIST", "VALUE_ARGUMENT", "REFERENCE_EXPRESSION") //x
        )

        val actualPaths = ids
            .map { testOnlyGetRootPath(file, it) }
            .map { it.kinds() }
            .filter { it.last() == "REFERENCE_EXPRESSION" }

        assertEquals(expectedPaths, actualPaths)
    }

    @TestMetadata("elementsFromDepth")
    fun testElementsFromDepth() {
        val file = createFile()
        val actualElements = listOf(
            testOnlyElementsFromDepth(file, 2),
            testOnlyElementsFromDepth(file, 3),
            testOnlyElementsFromDepth(file, 4),
        ).map { it.kinds() }

        val expectedElements = listOf(
            listOf("CLASS_BODY"),
            listOf("FUN"),
            listOf("VALUE_PARAMETER_LIST", "BLOCK")
        )

        assertEquals(expectedElements, actualElements)
    }

    @TestMetadata("getAllLeafPaths")
    fun testGetAllLeafPaths() {
        val file = createFile()
        val ifExpr = file.dfs().find { it is KtIfExpression }!!
        val actualPaths = testOnlyGetAllLeafPaths(file, ifExpr, false).map { it.kinds() }

        val expectedPaths = listOf(
            listOf("REFERENCE_EXPRESSION", "BINARY_EXPRESSION", "CONDITION", "IF"),
            listOf("OPERATION_REFERENCE", "BINARY_EXPRESSION", "CONDITION", "IF"),
            listOf("INTEGER_CONSTANT", "BINARY_EXPRESSION", "CONDITION", "IF"),
            listOf("REFERENCE_EXPRESSION", "CALL_EXPRESSION", "BLOCK", "THEN", "IF"),
            listOf("REFERENCE_EXPRESSION", "VALUE_ARGUMENT", "VALUE_ARGUMENT_LIST", "CALL_EXPRESSION", "BLOCK", "THEN", "IF"),
            listOf("REFERENCE_EXPRESSION", "BINARY_EXPRESSION", "CONDITION", "WHILE", "BODY", "BLOCK", "IF"),
            listOf("OPERATION_REFERENCE", "BINARY_EXPRESSION", "CONDITION", "WHILE", "BODY", "BLOCK", "IF"),
            listOf("REFERENCE_EXPRESSION", "BINARY_EXPRESSION", "CONDITION", "WHILE", "BODY", "BLOCK", "IF"),
            listOf("VALUE_PARAMETER_LIST", "FUN", "BLOCK", "WHILE", "BODY", "BLOCK", "IF"),
            listOf("PACKAGE_DIRECTIVE", "FILE", "CLASS", "CLASS_BODY", "FUN", "BLOCK", "WHILE", "BODY", "BLOCK", "IF"),
            listOf("IMPORT_LIST", "FILE", "CLASS", "CLASS_BODY", "FUN", "BLOCK", "WHILE", "BODY", "BLOCK", "IF")
        )

        assertEquals(expectedPaths, actualPaths)
    }


    @TestMetadata("getAllLeafPathsDropSuccessors")
    fun testGetAllLeafPathsDropSuccessors() {
        val file = createFile()
        val ifExpr = file.dfs().find { it is KtIfExpression }!!
        val actualPaths = testOnlyGetAllLeafPaths(file, ifExpr, true).map { it.kinds() }

        val expectedPaths = listOf(
            listOf("REFERENCE_EXPRESSION", "BINARY_EXPRESSION", "CONDITION", "WHILE", "BODY", "BLOCK", "IF"),
            listOf("OPERATION_REFERENCE", "BINARY_EXPRESSION", "CONDITION", "WHILE", "BODY", "BLOCK", "IF"),
            listOf("REFERENCE_EXPRESSION", "BINARY_EXPRESSION", "CONDITION", "WHILE", "BODY", "BLOCK", "IF"),
            listOf("VALUE_PARAMETER_LIST", "FUN", "BLOCK", "WHILE", "BODY", "BLOCK", "IF"),
            listOf("PACKAGE_DIRECTIVE", "FILE", "CLASS", "CLASS_BODY", "FUN", "BLOCK", "WHILE", "BODY", "BLOCK", "IF"),
            listOf("IMPORT_LIST", "FILE", "CLASS", "CLASS_BODY", "FUN", "BLOCK", "WHILE", "BODY", "BLOCK", "IF")
        )

        assertEquals(expectedPaths, actualPaths)
    }

    @TestMetadata("createDatasetSamples")
    fun testCreateDatasetSamples() {
        val file = createFile()

        //x as VALUE_ARGUMENT at `print(x)`
        val sample = createDatasetSamples(file, emptyMap(), 13, 1, 1).single()

        val (actualLeafPaths, actualRootPath, actualIndexAmongBrothers, actualTarget) = sample

        val fromIf = listOf("IF", "↓", "THEN", "↓", "BLOCK", "↓", "CALL_EXPRESSION", "↓", "VALUE_ARGUMENT_LIST")
        val fromWhile = listOf("WHILE", "↓", "BODY", "↓", "BLOCK", "↓")
        val expectedLeafPaths = listOf(
            listOf("REFERENCE_EXPRESSION", "↑", "CALL_EXPRESSION", "↓", "VALUE_ARGUMENT_LIST"),
            listOf("REFERENCE_EXPRESSION", "↑", "BINARY_EXPRESSION", "↑", "CONDITION", "↑") + fromIf,
            listOf("OPERATION_REFERENCE", "↑", "BINARY_EXPRESSION", "↑", "CONDITION", "↑") + fromIf,
            listOf("INTEGER_CONSTANT", "↑", "BINARY_EXPRESSION", "↑", "CONDITION", "↑") + fromIf,
            listOf("REFERENCE_EXPRESSION", "↑", "BINARY_EXPRESSION", "↑", "CONDITION", "↑") + fromWhile + fromIf,
            listOf("OPERATION_REFERENCE", "↑", "BINARY_EXPRESSION", "↑", "CONDITION", "↑") + fromWhile + fromIf,
            listOf("REFERENCE_EXPRESSION", "↑", "BINARY_EXPRESSION", "↑", "CONDITION", "↑") + fromWhile + fromIf,
            listOf("VALUE_PARAMETER_LIST", "↑", "FUN", "↓", "BLOCK", "↓") + fromWhile + fromIf,
            listOf("PACKAGE_DIRECTIVE", "↑", "FILE", "↓", "CLASS", "↓", "CLASS_BODY", "↓", "FUN", "↓", "BLOCK", "↓") + fromWhile + fromIf,
            listOf("IMPORT_LIST", "↑", "FILE", "↓", "CLASS", "↓", "CLASS_BODY", "↓", "FUN", "↓", "BLOCK", "↓") + fromWhile + fromIf
        )
        val expectedRootPath = listOf("FILE", "↓", "CLASS", "↓", "CLASS_BODY", "↓", "FUN", "↓", "BLOCK", "↓") + fromWhile + fromIf
        val expectedIndexAmongBrothers = 0 //VALUE_ARGUMENT_LIST.children = { VALUE_ARGUMENT }, we predict VALUE_ARGUMENT
        val expectedTarget = "VALUE_ARGUMENT"

        assertEquals(expectedLeafPaths, actualLeafPaths)
        assertEquals(expectedRootPath, actualRootPath)
        assertEquals(expectedIndexAmongBrothers, actualIndexAmongBrothers)
        assertEquals(expectedTarget, actualTarget)
    }

    @TestMetadata("extractPaths")
    fun testExtractPaths() {
        val file = createFile()

        //x as VALUE_ARGUMENT at `print(x)`
        val from = testOnlyElementsFromDepth(file, 13).single()

        val (actualLeafPaths, actualRootPath, actualIndexAmongBrothers, _) = extractPaths(file, from)

        val fromIf = listOf("IF", "↓", "THEN", "↓", "BLOCK", "↓", "CALL_EXPRESSION", "↓", "VALUE_ARGUMENT_LIST", "↓", "VALUE_ARGUMENT")
        val fromWhile = listOf("WHILE", "↓", "BODY", "↓", "BLOCK", "↓")
        val expectedLeafPaths = listOf(
            listOf("REFERENCE_EXPRESSION", "↑", "VALUE_ARGUMENT"),
            listOf("REFERENCE_EXPRESSION", "↑", "CALL_EXPRESSION", "↓", "VALUE_ARGUMENT_LIST", "↓", "VALUE_ARGUMENT"),
            listOf("REFERENCE_EXPRESSION", "↑", "BINARY_EXPRESSION", "↑", "CONDITION", "↑") + fromIf,
            listOf("OPERATION_REFERENCE", "↑", "BINARY_EXPRESSION", "↑", "CONDITION", "↑") + fromIf,
            listOf("INTEGER_CONSTANT", "↑", "BINARY_EXPRESSION", "↑", "CONDITION", "↑") + fromIf,
            listOf("REFERENCE_EXPRESSION", "↑", "BINARY_EXPRESSION", "↑", "CONDITION", "↑") + fromWhile + fromIf,
            listOf("OPERATION_REFERENCE", "↑", "BINARY_EXPRESSION", "↑", "CONDITION", "↑") + fromWhile + fromIf,
            listOf("REFERENCE_EXPRESSION", "↑", "BINARY_EXPRESSION", "↑", "CONDITION", "↑") + fromWhile + fromIf,
            listOf("VALUE_PARAMETER_LIST", "↑", "FUN", "↓", "BLOCK", "↓") + fromWhile + fromIf,
            listOf("PACKAGE_DIRECTIVE", "↑", "FILE", "↓", "CLASS", "↓", "CLASS_BODY", "↓", "FUN", "↓", "BLOCK", "↓") + fromWhile + fromIf,
            listOf("IMPORT_LIST", "↑", "FILE", "↓", "CLASS", "↓", "CLASS_BODY", "↓", "FUN", "↓", "BLOCK", "↓") + fromWhile + fromIf
        )
        val expectedRootPath = listOf("FILE", "↓", "CLASS", "↓", "CLASS_BODY", "↓", "FUN", "↓", "BLOCK", "↓") + fromWhile + fromIf
        val expectedIndexAmongBrothers = 1 //VALUE_ARGUMENT.children = { REFERENCE_EXPRESSION, * } and we predict *

        assertEquals(expectedLeafPaths, actualLeafPaths)
        assertEquals(expectedRootPath, actualRootPath)
        assertEquals(expectedIndexAmongBrothers, actualIndexAmongBrothers)
    }

    //TODO: add extraction with types test

    private fun List<PsiElement>.kinds(): List<String> = map { it.kind() }
}
