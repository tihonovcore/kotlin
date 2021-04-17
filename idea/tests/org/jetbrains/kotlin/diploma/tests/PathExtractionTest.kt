/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.tests

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

        val wrappedFile = testOnlyBuildTree(file, null)
        val actualPaths = ids
            .map { testOnlyGetRootPath(wrappedFile.testOnlyFindNode(it)!!) }
            .map { it.kinds() }
            .filter { it.last() == "REFERENCE_EXPRESSION" }

        assertEquals(expectedPaths, actualPaths)
    }

    @TestMetadata("elementsFromDepth")
    fun testElementsFromDepth() {
        val file = testOnlyBuildTree(createFile(), null)
        val actualElements = listOf(
            file.testOnlyElementsFromDepth(2),
            file.testOnlyElementsFromDepth(3),
            file.testOnlyElementsFromDepth(4),
        ).map { it.kinds() }

        val expectedElements = listOf(
            listOf("CLASS_BODY"),
            listOf("FUN"),
            listOf("VALUE_PARAMETER_LIST", "BLOCK")
        )

        assertEquals(expectedElements, actualElements)
    }

    @TestMetadata("getLeafPaths")
    fun testGetAllLeafPaths() {
        val file = createFile()
        val ifExpr = file.dfs().find { it is KtIfExpression }!!

        val wrappedFile = testOnlyBuildTree(file, null)
        val actualPaths = testOnlyGetLeafPaths(wrappedFile.testOnlyFindNode(ifExpr)!!, 1).map { it.kinds() }

        val expectedPaths = load("getLeafPaths.txt")

        assertEquals(expectedPaths.toString(), actualPaths.toString())
    }

    @TestMetadata("createSamplesForDataset")
    fun testCreateDatasetSamples() {
        val file = createFile()

        //AFTER_LAST - single child of REFERENCE_EXPRESSION(x) at `print(x)`
        val sample = createSamplesForDataset(file, emptyMap(), 15, 1).single()

        val (actualLeafPaths, actualRootPath, actualIndexAmongBrothers, actualTarget) = sample

        val expectedLeafPaths = load("createSamplesForDataset_leafPaths.txt")
        val expectedRootPath = load("createSamplesForDataset_rootPath.txt").single().split(" ")
        val expectedIndexAmongBrothers = 0 //REFERENCE_EXPRESSION.children = { AFTER_LAST }, we predict AFTER_LAST
        val expectedTarget = "AFTER_LAST"

        assertEquals(expectedLeafPaths.toString(), actualLeafPaths.toString())
        assertEquals(expectedRootPath, actualRootPath)
        assertEquals(expectedIndexAmongBrothers, actualIndexAmongBrothers)
        assertEquals(expectedTarget, actualTarget)
    }

    @TestMetadata("createSampleForPredict")
    fun testExtractPaths() {
        val file = createFile()

        //VALUE_ARGUMENT(x) at `print(x)`
        val from = file.dfs().single { it is KtValueArgument }

        val path = mutableListOf<KtElement>()
        var current = from
        while (current !== file) {
            path += current
            current = current.parent as KtElement
        }
        path += file

        val (actualLeafPaths, actualRootPath, actualIndexAmongBrothers, _) = createSampleForPredict(file, from, path)

        val expectedLeafPaths = load("createSampleForPredict_leafPaths.txt")
        val expectedRootPath = load("createSampleForPredict_rootPath.txt").single().split(" ")

        // Create sample for prediction *
        //
        //  from:      VALUE_ARGUMENT
        //     0:          REFERENCE_EXPRESSION
        //                     AFTER_LAST
        //     1:          *
        val expectedIndexAmongBrothers = 1

        assertEquals(expectedLeafPaths.toString(), actualLeafPaths.toString())
        assertEquals(expectedRootPath, actualRootPath)
        assertEquals(expectedIndexAmongBrothers, actualIndexAmongBrothers)
    }

    //TODO: add extraction with types test

    private fun List<AstWithAfterLast>.kinds(): List<String> = map { it.original.kind() }
}
