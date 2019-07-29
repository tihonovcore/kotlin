/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger

import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference

/**
 * Represents state of a coroutine.
 * @see kotlinx.coroutines.debug.CoroutineInfo
 */
open class CoroutineState(
    val name: String,
    var state: String,
    var thread: ThreadReference? = null
) {
    var frame: ObjectReference? = null
    var isSuspended: Boolean = state == "SUSPENDED"
    val isEmptyStackTrace: Boolean by lazy { stackTrace.isEmpty() }
    var coroutineStateDetail: String? = state
    lateinit var stackTrace: List<StackTraceElement>

    val stringStackTrace: String by lazy {
        buildString {
            stackTrace.forEach {
                appendln(it)
            }
        }
    }
}