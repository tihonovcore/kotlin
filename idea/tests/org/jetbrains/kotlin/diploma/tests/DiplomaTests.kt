/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.tests

import org.jetbrains.kotlin.idea.caches.resolve.AbstractMultiModuleIdeResolveTest
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.psi.KtElement

abstract class DiplomaTests : AbstractMultiModuleIdeResolveTest() {
    override fun getTestDataPath(): String = PluginTestCaseBase.getTestDataPathBase()

    fun KtElement.dfs(): List<KtElement> {
        return children.filterIsInstance(KtElement::class.java).fold(mutableListOf(this), { acc, child ->
            acc.apply { addAll(child.dfs()) }
        })
    }
}
