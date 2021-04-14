/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.tests

import org.jetbrains.kotlin.diploma.addAfterLastEverywhere
import org.jetbrains.kotlin.diploma.kind
import org.jetbrains.kotlin.diploma.renderTree
import org.jetbrains.kotlin.psi.KtElement
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
        ).addAfterLastEverywhere() as KtElement

        file.renderTree(emptyMap())

        val expected = load("getKind.txt").map { line -> line.dropWhile { it == ' ' } }
        val actual = file.dfs().map { it.kind() }

        assertEquals(expected, actual)
    }
}
