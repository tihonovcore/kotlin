/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import org.jetbrains.kotlin.idea.completion.test.ExpectedCompletionUtils
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionHandlerTest.Companion.COMPLETION_CHARS_PREFIX
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionHandlerTest.Companion.COMPLETION_CHAR_PREFIX
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionHandlerTest.Companion.ELEMENT_TEXT_PREFIX
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionHandlerTest.Companion.INVOCATION_COUNT_PREFIX
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionHandlerTest.Companion.LOOKUP_STRING_PREFIX
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionHandlerTest.Companion.TAIL_TEXT_PREFIX
import org.jetbrains.kotlin.idea.completion.test.handlers.CompletionHandlerTestBase.Companion.completionChars
import org.jetbrains.kotlin.idea.completion.test.handlers.CompletionHandlerTestBase.Companion.doTestWithTextLoaded
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingTest.*
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.TagsTestDataUtil

import java.io.File

abstract class AbstractHighlightAndTypingTest : KotlinLightCodeInsightFixtureTestCase() {

    private val defaultCompletionType: CompletionType = CompletionType.BASIC

    protected fun doTest(filePath: String) {
        val fileText = FileUtil.loadFile(File(filePath), true)
        val checkInfos = !InTextDirectivesUtils.isDirectiveDefined(fileText, NO_CHECK_INFOS_PREFIX)
        val checkWeakWarnings = !InTextDirectivesUtils.isDirectiveDefined(fileText, NO_CHECK_WEAK_WARNINGS_PREFIX)
        val checkWarnings = !InTextDirectivesUtils.isDirectiveDefined(fileText, NO_CHECK_WARNINGS_PREFIX)
        val expectedDuplicatedHighlighting = InTextDirectivesUtils.isDirectiveDefined(fileText, EXPECTED_DUPLICATED_HIGHLIGHTING_PREFIX)

        val invocationCount = InTextDirectivesUtils.getPrefixedInt(fileText, INVOCATION_COUNT_PREFIX) ?: 1
        val lookupString = InTextDirectivesUtils.findStringWithPrefixes(fileText, LOOKUP_STRING_PREFIX)
        val itemText = InTextDirectivesUtils.findStringWithPrefixes(fileText, ELEMENT_TEXT_PREFIX)
        val tailText = InTextDirectivesUtils.findStringWithPrefixes(fileText, TAIL_TEXT_PREFIX)
        val completionType = ExpectedCompletionUtils.getCompletionType(fileText) ?: defaultCompletionType

        val completionChars = completionChars(
            InTextDirectivesUtils.findStringWithPrefixes(fileText, COMPLETION_CHAR_PREFIX),
            InTextDirectivesUtils.findStringWithPrefixes(fileText, COMPLETION_CHARS_PREFIX)
        )

        myFixture.configureByFile(filePath)

        // check initial highlight
        checkHighlighting(expectedDuplicatedHighlighting, checkWarnings, checkInfos, checkWeakWarnings)

        val highlightStrings = mutableListOf<String>()
        // type and check plain result
        doTestWithTextLoaded(
            myFixture,
            completionType,
            invocationCount,
            lookupString,
            itemText,
            tailText,
            completionChars,
            File(filePath).name + ".after"
        ) {
            highlightStrings.add(highlightString())
        }

        // form a highlight string (as it is quite inconvenient to reload file and track actual highlight positions)
        val highlightString = highlightStrings.last()

        val expectedHighlightString = FileUtil.loadFile(File("$filePath.expected"), true)
        assertEquals(expectedHighlightString, highlightString)
    }

    private fun checkHighlighting(
        expectedDuplicatedHighlighting: Boolean,
        checkWarnings: Boolean,
        checkInfos: Boolean,
        checkWeakWarnings: Boolean
    ) {
        withExpectedDuplicatedHighlighting(expectedDuplicatedHighlighting, Runnable {
            try {
                myFixture.checkHighlighting(checkWarnings, checkInfos, checkWeakWarnings)
            } catch (e: Throwable) {
                reportActualHighlighting(e)
            }
        })
    }

    private fun reportActualHighlighting(e: Throwable) {
        println(highlightString())
        throw e
    }

    private fun highlightString(): String {
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        psiDocumentManager.commitAllDocuments()
        val highlights = highlights()
        return TagsTestDataUtil.insertInfoTagsWithCaretAndSelection(highlights, myFixture.editor)
    }

    private fun highlights(): List<HighlightInfo> =
        myFixture.doHighlighting()
        //DaemonCodeAnalyzerImpl.getHighlights(myFixture.getDocument(myFixture.file), null, myFixture.project)

    private fun withExpectedDuplicatedHighlighting(expectedDuplicatedHighlighting: Boolean, runnable: Runnable) {
        if (!expectedDuplicatedHighlighting) {
            runnable.run()
            return
        }

        expectedDuplicatedHighlighting(runnable)
    }

}
