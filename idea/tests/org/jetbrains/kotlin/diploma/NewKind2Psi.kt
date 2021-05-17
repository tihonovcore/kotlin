/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

class NewKind2Psi(project: Project) {
    private val template = File("/home/tihonovcore/diploma/kotlin/idea/tests/org/jetbrains/kotlin/diploma/Kind2PsiTemplate.kt").readText()
    private val factory = KtPsiFactory(project)
    private val file = factory.createFile(template)

    fun decode(predictedNodeKind: String): KtElement {
        return when (predictedNodeKind) {
            AFTER_LAST_KIND -> throw Pipeline.AfterLastException
            "BOX_TEMPLATE" -> {
                val emptyFile = factory.createPhysicalFile("context", "")
                factory.createAnalyzableFile("from_kind_2_psi", "fun box() {}", emptyFile).also {
                    it.children.first().delete(); it.children.first().delete()
                }
            }
            "FILE" -> {
                val emptyFile = factory.createPhysicalFile("context.kt", "")
                factory.createAnalyzableFile("from_kind_2_psi.kt", "", emptyFile)
            }
            else -> {
                val element = file.find(predictedNodeKind) ?: throw IllegalArgumentException("<!! expected $predictedNodeKind !!>")
                val copy = element.copy() as KtElement
                copy
            }
        }
    }

    private fun KtElement.find(expectedKind: String): KtElement? {
        if (kind() == expectedKind) {
            return this
        }

        return children.filterIsInstance(KtElement::class.java).mapNotNull { it.find(expectedKind) }.firstOrNull()
    }
}
