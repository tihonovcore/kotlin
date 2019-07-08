/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger

/**
 * Represents the state of a coroutine.
 * It's not tied with debug implementation of kotlinx.coroutines.debug
 */
open class CoroutineState(val name: String, val state: String) {
    var isSleeping: Boolean = state == "SUSPENDED"
    var isEmptyStackTrace = false // same
    var coroutineStateDetail: String? = state
    var extraState: String? = null
    var stackTrace: String = ""

//    class CompoundCoroutineState(val originalState: CoroutineState) : CoroutineState(originalState.name, originalState.state) {
//        var counter = 1
//        fun add(state: CoroutineState): Boolean {
//            /*   if (myOriginalState.isEDT()) return false
//               if (!Comparing.equal(state.myState, myOriginalState.myState)) return false
//               if (state.myEmptyStackTrace != myOriginalState.myEmptyStackTrace) return false
//               if (state.isDaemon != myOriginalState.isDaemon) return false
//               if (!Comparing.equal(state.myJavaThreadState, myOriginalState.myJavaThreadState)) return false
//               if (!Comparing.equal(state.myThreadStateDetail, myOriginalState.myThreadStateDetail)) return false
//               if (!Comparing.equal(state.myExtraState, myOriginalState.myExtraState)) return false
//               if (!Comparing.haveEqualElements(state.myThreadsWaitingForMyLock, myOriginalState.myThreadsWaitingForMyLock)) return false
//               if (!Comparing.haveEqualElements(state.myDeadlockedThreads, myOriginalState.myDeadlockedThreads)) return false
//               if (!Comparing.equal(
//                       getMergeableStackTrace(state.myStackTrace, true),
//                       getMergeableStackTrace(myOriginalState.myStackTrace, true)
//                   )
//               ) return false
//               counter++*/
//               return true
////            TODO()
//        }
//    }
}