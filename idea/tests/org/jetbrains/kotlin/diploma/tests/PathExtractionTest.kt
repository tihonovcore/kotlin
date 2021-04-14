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
        ).addAfterLastEverywhere() as KtElement
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
            listOf("AFTER_LAST", "AFTER_LAST", "CLASS_BODY", "AFTER_LAST"),
            listOf("FUN", "AFTER_LAST"),
            listOf("VALUE_PARAMETER_LIST", "BLOCK", "AFTER_LAST")
        )

        assertEquals(expectedElements, actualElements)
    }

    @TestMetadata("getAllLeafPaths")
    fun testGetAllLeafPaths() {
        val file = createFile()
        val ifExpr = file.dfs().find { it is KtIfExpression }!!
        val actualPaths = testOnlyGetAllLeafPaths(file, ifExpr, false).map { it.kinds() }

        val expectedPaths = load("getAllLeafPaths.txt")

        assertEquals(expectedPaths.toString(), actualPaths.toString())
    }


    @TestMetadata("getAllLeafPathsDropSuccessors")
    fun testGetAllLeafPathsDropSuccessors() {
        val file = createFile()
        val ifExpr = file.dfs().find { it is KtIfExpression }!!
        val actualPaths = testOnlyGetAllLeafPaths(file, ifExpr, true).map { it.kinds() }

        val expectedPaths = load("getAllLeafPathsDropSuccessors.txt")

        assertEquals(expectedPaths.toString(), actualPaths.toString())
    }

    @TestMetadata("createDatasetSamples")
    fun testCreateDatasetSamples() {
        val file = createFile()

        //AFTER_LAST - single child of REFERENCE_EXPRESSION(x) at `print(x)`
        val sample = createDatasetSamples(file, emptyMap(), 15, 1, 1).single()

        val (actualLeafPaths, actualRootPath, actualIndexAmongBrothers, actualTarget) = sample

        val expectedLeafPaths = load("createDatasetSamples_leafPaths.txt")
        val expectedRootPath = load("createDatasetSamples_rootPath.txt").single().split(" ")
        val expectedIndexAmongBrothers = 0 //REFERENCE_EXPRESSION.children = { AFTER_LAST }, we predict AFTER_LAST
        val expectedTarget = "AFTER_LAST"

        assertEquals(expectedLeafPaths.toString(), actualLeafPaths.toString())
        assertEquals(expectedRootPath, actualRootPath)
        assertEquals(expectedIndexAmongBrothers, actualIndexAmongBrothers)
        assertEquals(expectedTarget, actualTarget)
    }

    @TestMetadata("extractPaths")
    fun testExtractPaths() {
        val file = createFile()

        //VALUE_ARGUMENT(x) at `print(x)`
        val from = testOnlyElementsFromDepth(file, 13).filterIsInstance(KtValueArgument::class.java).single()

        val (actualLeafPaths, actualRootPath, actualIndexAmongBrothers, _) = extractPaths(file, from)

        val expectedLeafPaths = load("extractPaths_leafPaths.txt")
        val expectedRootPath = load("extractPaths_rootPath.txt").single().split(" ")

        // Create sample for prediction *
        //
        //  from:      VALUE_ARGUMENT
        //     0:          REFERENCE_EXPRESSION
        //                     AFTER_LAST
        //     1:          AFTER_LAST
        //     2:          *
        val expectedIndexAmongBrothers = 2

        assertEquals(expectedLeafPaths.toString(), actualLeafPaths.toString())
        assertEquals(expectedRootPath, actualRootPath)
        assertEquals(expectedIndexAmongBrothers, actualIndexAmongBrothers)
    }

    //TODO: add extraction with types test

    private fun List<PsiElement>.kinds(): List<String> = map { it.kind() }
}
