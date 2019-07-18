/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.execution.filters.ExceptionFilters
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.execution.ui.layout.impl.RunnerContentUi
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.text.DateFormatUtil
import com.sun.jdi.*
import com.sun.tools.jdi.ObjectReferenceImpl
import com.sun.tools.jdi.StringReferenceImpl
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext

class CoroutineDumpAction : AnAction(), AnAction.TransparentUpdate {
    val logger = Logger.getInstance(this::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val context = DebuggerManagerEx.getInstanceEx(project).context
        val suspendContext = context.suspendContext
        val session = context.debuggerSession
        val stackFrame = context.frameProxy
        val evalContext = EvaluationContextImpl(suspendContext ?: return, stackFrame)
        val execContext = ExecutionContext(evalContext, stackFrame ?: return)
        // get StackFrameProxyImpl and inst EvaluationContext, then ExecutionContext
        if (session != null && session.isAttached) {
            val process = context.debugProcess ?: return
            process.managerThread.schedule(object : DebuggerCommandImpl() {
                override fun action() {
                    val states = buildCoroutineStates(execContext)
                    val f = fun() {
                        addCoroutineDump(
                            project,
                            states,
                            session.xDebugSession?.ui ?: return,
                            session.searchScope
                        )
                    }
                    ApplicationManager.getApplication().invokeLater(f, ModalityState.NON_MODAL)
                }
            })
        }
    }

    companion object {
        /**
         * Invokes DebugProbes from debugged process's classpath and returns states of coroutines
         */
        fun buildCoroutineStates(context: ExecutionContext): List<CoroutineState> {
            val path = "kotlinx.coroutines.debug"
            // kotlinx.coroutines.debug.DebugProbes instance and methods
            val debugProbes = context.findClass("$path.DebugProbes") as ClassType
            val debugProbesImplType = context.findClass("$path.internal.DebugProbesImpl") as ClassType
            val debugProbesImpl = debugProbesImplType.getValue(debugProbesImplType.fieldByName("INSTANCE")) as ObjectReferenceImpl
            val enhanceStackTraceWithThreadDump = debugProbesImplType.methodsByName("enhanceStackTraceWithThreadDump").first()
            val dumpMethod = debugProbes.methodsByName("dumpCoroutinesInfo", "()Ljava/util/List;").first()
            val instance = debugProbes.getValue(debugProbes.fieldByName("INSTANCE")) as ObjectReference
            // CoroutineInfo
            val info = context.findClass("$path.CoroutineInfo") as ClassType
            val getState = info.methodsByName("getState").first()
            val getContext = info.methodsByName("getContext").first()
            val idField = info.fieldByName("sequenceNumber")
            val lastObservedStackTrace = info.methodsByName("lastObservedStackTrace").first()
            val coroutineContext = context.findClass("kotlin.coroutines.CoroutineContext") as InterfaceType
            val getContextElement = coroutineContext.methodsByName("get").first()
            val coroutineName = context.findClass("kotlinx.coroutines.CoroutineName") as ClassType
            val getName = coroutineName.methodsByName("getName").first()
            val nameCompanion = coroutineName.getValue(coroutineName.fieldByName("Key")) as ObjectReferenceImpl
            val toString = (context.findClass("java.lang.Object") as ClassType).methodsByName("toString").first()
            // get dump
            val infoList = context.invokeMethod(instance, dumpMethod, emptyList()) as ObjectReferenceImpl
            // Methods to work with list
            val listType = context.findClass("java.util.List") as InterfaceType
            val getSize = listType.methodsByName("size").first()
            val getElement = listType.methodsByName("get").first()
            val size = (context.invokeMethod(infoList, getSize, emptyList()) as IntegerValue).value()
            val element = context.findClass("java.lang.StackTraceElement") as ClassType

            val result = mutableListOf<CoroutineState>()
            for (i in 0 until size) {
                val index = context.vm.mirrorOf(i)
                val elem = context.invokeMethod(infoList, getElement, listOf(index)) as ObjectReferenceImpl
                result.add(
                    refToState(
                        context, elem,
                        getState, toString,
                        getContext, getContextElement,
                        nameCompanion, getName, idField
                    ).apply {
                        stackTrace = getStackTrace2(
                            elem,
                            lastObservedStackTrace,
                            getSize,
                            getElement,
                            debugProbesImpl,
                            enhanceStackTraceWithThreadDump,
                            element,
                            context
                        )
                    }
                )
            }
            return result
        }

        /**
         * Converts [ObjectReferenceImpl] into [CoroutineState]
         */
        private fun refToState(
            context: ExecutionContext, // Execution context to invoke methods
            info: ObjectReferenceImpl, // CoroutineInfo instance
            getState: Method, // CoroutineInfo.getState()
            toString: Method, // CoroutineInfo.State.toString()
            getContext: Method, // CoroutineInfo.getContext()
            getContextElement: Method, // CoroutineContext.get(Key)
            nameKey: ObjectReferenceImpl, // CoroutineName companion object
            getName: Method, // CoroutineName.getName()
            idField: Field // CoroutineId.idField()
        ): CoroutineState {
            //  stringState = coroutineInfo.state.toString()
            val state = context.invokeMethod(info, getState, emptyList()) as ObjectReferenceImpl
            val stringState = (context.invokeMethod(state, toString, emptyList()) as StringReferenceImpl).value()

            // next lines are equal to `coroutineInfo.context.get(CoroutineName).name`
            val coroutineContextInst = context.invokeMethod(info, getContext, emptyList()) as ObjectReferenceImpl
            val coroutineName = context.invokeMethod(
                coroutineContextInst,
                getContextElement, listOf(nameKey)
            ) as? ObjectReferenceImpl

            // If the coroutine doesn't have a given name, CoroutineContext.get(CoroutineName) returns null
            val name = if (coroutineName != null) (context.invokeMethod(
                coroutineName,
                getName, emptyList()
            ) as StringReferenceImpl).value() else "coroutine"
            val id = (info.getValue(idField) as LongValue).value()
            return CoroutineState("$name#$id", stringState)
        }

        /**
         * Returns string representation of stackFrame for the given coroutine's [ObjectReferenceImpl]
         */
        private fun getStackTrace(
            info: ObjectReferenceImpl,
            lastObservedStackTrace: Method,
            getSize: Method,
            getElement: Method,
            toString: Method,
            context: ExecutionContext
        ): String {
            // info.lastObservedStackTrace.forEach { frame ->
            //            append("\n\tat $frame")
            //        }
            val frameList = context.invokeMethod(info, lastObservedStackTrace, emptyList()) as ObjectReferenceImpl
            val size = (context.invokeMethod(frameList, getSize, emptyList()) as IntegerValue).value()
            return buildString {
                for (i in 0 until size) {
                    val frame = context.invokeMethod(
                        frameList, getElement,
                        listOf(context.vm.virtualMachine.mirrorOf(i))
                    ) as ObjectReferenceImpl
                    val frameString = (context.invokeMethod(frame, toString, emptyList()) as StringReference).value()
                    if (!frameString.contains("kotlinx.coroutines.debug.internal.DebugProbes")) this.appendln("\tat $frameString")
                }
            }
        }

        private fun getStackTrace2(
            info: ObjectReferenceImpl,
            lastObservedStackTrace: Method,
            getSize: Method,
            getElement: Method,
            debugProbesImpl: ObjectReferenceImpl,
            enhanceStackTraceWithThreadDump: Method,
            element: ClassType,
            context: ExecutionContext
        ): List<StackTraceElement> {
            val frameList = context.invokeMethod(info, lastObservedStackTrace, emptyList()) as ObjectReferenceImpl
            val mergedFrameList = context.invokeMethod(
                debugProbesImpl,
                enhanceStackTraceWithThreadDump, listOf(info, frameList)
            ) as ObjectReferenceImpl
            val size = (context.invokeMethod(mergedFrameList, getSize, emptyList()) as IntegerValue).value()

            return List(size) {
                val frame = context.invokeMethod(
                    mergedFrameList, getElement,
                    listOf(context.vm.virtualMachine.mirrorOf(it))
                ) as ObjectReferenceImpl
                with(frame) {
                    StackTraceElement(
                        (getValue(element.fieldByName("declaringClass")) as StringReference).value(),
                        (getValue(element.fieldByName("methodName")) as StringReference).value(),
                        (getValue(element.fieldByName("fileName")) as StringReference?)?.value(),
                        (getValue(element.fieldByName("lineNumber")) as IntegerValue).value()
                    )
                }
            }

        }
    }

    /**
     * Analog of [DebuggerUtilsEx.addThreadDump]. Doesn't need to be a [DebuggerUtilsEx] member.
     */
    fun addCoroutineDump(project: Project, coroutines: List<CoroutineState>, ui: RunnerLayoutUi, searchScope: GlobalSearchScope) {
        val consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        consoleBuilder.filters(ExceptionFilters.getFilters(searchScope))
        val consoleView = consoleBuilder.console
        val toolbarActions = DefaultActionGroup()
        consoleView.allowHeavyFilters()
        val panel = CoroutineDumpPanel(project, consoleView, toolbarActions, coroutines)

        val id = "DumpKt " + DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis())
        val content = ui.createContent(id, panel, id, null, null)
        content.putUserData(RunnerContentUi.LIGHTWEIGHT_CONTENT_MARKER, java.lang.Boolean.TRUE)
        content.isCloseable = true
        content.description = "Coroutine Dump"
        ui.addContent(content)
        ui.selectAndFocus(content, true, true)
        Disposer.register(content, consoleView)
        ui.selectAndFocus(content, true, false)
        if (coroutines.isNotEmpty()) panel.selectStackFrame(0)
    }

//    private fun threadStatusToJavaThreadState(status: Int): String {
//        when (status) {
//            ThreadReference.THREAD_STATUS_MONITOR -> return Thread.State.BLOCKED.name
//            ThreadReference.THREAD_STATUS_NOT_STARTED -> return Thread.State.NEW.name
//            ThreadReference.THREAD_STATUS_RUNNING -> return Thread.State.RUNNABLE.name
//            ThreadReference.THREAD_STATUS_SLEEPING -> return Thread.State.TIMED_WAITING.name
//            ThreadReference.THREAD_STATUS_WAIT -> return Thread.State.WAITING.name
//            ThreadReference.THREAD_STATUS_ZOMBIE -> return Thread.State.TERMINATED.name
//            ThreadReference.THREAD_STATUS_UNKNOWN -> return "unknown"
//            else -> return "undefined"
//        }
//    }

//    private fun threadStatusToState(status: Int): String {
//        when (status) {
//            ThreadReference.THREAD_STATUS_MONITOR -> return "waiting for monitor entry"
//            ThreadReference.THREAD_STATUS_NOT_STARTED -> return "not started"
//            ThreadReference.THREAD_STATUS_RUNNING -> return "runnable"
//            ThreadReference.THREAD_STATUS_SLEEPING -> return "sleeping"
//            ThreadReference.THREAD_STATUS_WAIT -> return "waiting"
//            ThreadReference.THREAD_STATUS_ZOMBIE -> return "zombie"
//            ThreadReference.THREAD_STATUS_UNKNOWN -> return "unknown"
//            else -> return "undefined"
//        }
//    }

//    fun renderLocation(location: Location): String {
//        return DebuggerBundle.message(
//            "export.threads.stackframe.format",
//            DebuggerUtilsEx.getLocationMethodQName(location),
//            DebuggerUtilsEx.getSourceName(location) { e -> "Unknown Source" },
//            DebuggerUtilsEx.getLineNumber(location, false)
//        )
//    }

    override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        val project = e.project
        if (project == null) {
            presentation.isEnabled = false
            return
        }
        if (DebuggerManagerEx.getInstanceEx(project).context.suspendContext == null) {
            presentation.isEnabled = false
            return
        }
        val debuggerSession = DebuggerManagerEx.getInstanceEx(project).context.debuggerSession
        presentation.isEnabled = debuggerSession != null && debuggerSession.isAttached
    }
}