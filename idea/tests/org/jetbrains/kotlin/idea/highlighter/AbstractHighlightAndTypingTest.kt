/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.completion.test.ExpectedCompletionUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.TagsTestDataUtil

import java.io.File

abstract class AbstractHighlightAndTypingTest : KotlinLightCodeInsightFixtureTestCase() {

    private val INVOCATION_COUNT_PREFIX = "INVOCATION_COUNT:"
    private val LOOKUP_STRING_PREFIX = "ELEMENT:"
    private val ELEMENT_TEXT_PREFIX = "ELEMENT_TEXT:"
    private val TAIL_TEXT_PREFIX = "TAIL_TEXT:"
    private val COMPLETION_CHAR_PREFIX = "CHAR:"
    private val COMPLETION_CHARS_PREFIX = "CHARS:"
    private val CODE_STYLE_SETTING_PREFIX = "CODE_STYLE_SETTING:"

    private val defaultCompletionType: CompletionType = CompletionType.BASIC

    protected fun doTest(filePath: String) {
        val fileText = FileUtil.loadFile(File(filePath), true)
        val checkInfos = !InTextDirectivesUtils.isDirectiveDefined(fileText, "// NO_CHECK_INFOS")
        val checkWeakWarnings = !InTextDirectivesUtils.isDirectiveDefined(fileText, "// NO_CHECK_WEAK_WARNINGS")
        val checkWarnings = !InTextDirectivesUtils.isDirectiveDefined(fileText, "// NO_CHECK_WARNINGS")
        val expectedDuplicatedHighlighting = InTextDirectivesUtils.isDirectiveDefined(fileText, "// EXPECTED_DUPLICATED_HIGHLIGHTING")

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

        // type and check plain result
        doTestWithTextLoaded(
            completionType,
            invocationCount,
            lookupString,
            itemText,
            tailText,
            completionChars,
            File(filePath).name + ".after"
        )

        // form a highlight string (as it is quite inconvenient to reload file and track actual highlight positions)
        val highlightString = highlightString()

        val expectedHighlightString = FileUtil.loadFile(File("${filePath}.expected"), true)
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
        val highlights = highlights()
        return TagsTestDataUtil.insertInfoTagsWithCaretAndSelection(highlights, myFixture.editor)
    }

    private fun highlights(): List<HighlightInfo> =
        DaemonCodeAnalyzerImpl.getHighlights(myFixture.getDocument(myFixture.file), null, myFixture.project)

    protected fun doTestWithTextLoaded(
        completionType: CompletionType,
        time: Int,
        lookupString: String?,
        itemText: String?,
        tailText: String?,
        completionChars: String,
        afterFilePath: String
    ) {
        for (idx in 0 until completionChars.length - 1) {
            myFixture.type(completionChars[idx])
        }

        myFixture.complete(completionType, time)

        if (lookupString != null || itemText != null || tailText != null) {
            val item = getExistentLookupElement(lookupString, itemText, tailText)
            if (item != null) {
                selectItem(item, completionChars.last())
            }
        }
        myFixture.checkResultByFile(afterFilePath)
    }

    protected fun completionChars(char: String?, chars: String?): String =
        when (char) {
            null -> when (chars) {
                null -> "\n"
                else -> chars.replace("\\n", "\n").replace("\\t", "\t")
            }
            "\\n" -> "\n"
            "\\t" -> "\t"
            else -> char.single().toString() ?: error("Incorrect completion char: \"$char\"")
        }

    private fun withExpectedDuplicatedHighlighting(expectedDuplicatedHighlighting: Boolean, runnable: Runnable) {
        if (!expectedDuplicatedHighlighting) {
            runnable.run()
            return
        }

        expectedDuplicatedHighlighting(runnable)
    }

    protected fun getExistentLookupElement(lookupString: String?, itemText: String?, tailText: String?): LookupElement? {
        val lookup = LookupManager.getInstance(project)?.activeLookup as LookupImpl? ?: return null
        val items = lookup.items

        if (lookupString == "*") {
            assert(itemText == null)
            assert(tailText == null)
            return items.firstOrNull()
        }

        var foundElement : LookupElement? = null
        val presentation = LookupElementPresentation()
        for (lookupElement in items) {
            val lookupOk = if (lookupString != null) lookupElement.lookupString == lookupString else true

            if (lookupOk) {
                lookupElement.renderElement(presentation)

                val textOk = if (itemText != null) {
                    val itemItemText = presentation.itemText
                    itemItemText != null && itemItemText == itemText
                }
                else {
                    true
                }

                if (textOk) {
                    val tailOk = if (tailText != null) {
                        val itemTailText = presentation.tailText
                        itemTailText != null && itemTailText == tailText
                    }
                    else {
                        true
                    }

                    if (tailOk) {
                        if (foundElement != null) {
                            val dump = ExpectedCompletionUtils.listToString(ExpectedCompletionUtils.getItemsInformation(arrayOf(foundElement, lookupElement)))
                            fail("Several elements satisfy to completion restrictions:\n$dump")
                        }

                        foundElement = lookupElement
                    }
                }
            }
        }

        if (foundElement == null) {
            val dump = ExpectedCompletionUtils.listToString(ExpectedCompletionUtils.getItemsInformation(items.toTypedArray()))
            error("No element satisfy completion restrictions in:\n$dump")
        }
        return foundElement
    }

    protected fun selectItem(item: LookupElement?, completionChar: Char) {
        val lookup = (myFixture.lookup as LookupImpl)
        if (lookup.currentItem != item) { // do not touch selection if not changed - important for char filter tests
            lookup.currentItem = item
        }
        lookup.focusDegree = LookupImpl.FocusDegree.FOCUSED
        if (LookupEvent.isSpecialCompletionChar(completionChar)) {
            lookup.finishLookup(completionChar)
        } else {
            myFixture.type(completionChar)
        }
    }
}
