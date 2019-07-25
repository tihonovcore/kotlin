/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.ui.DebuggerContentInfo
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.icons.AllIcons
import com.intellij.ui.content.ContentManagerAdapter
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener


@Suppress("IncompatibleAPI")
class CoroutinesDebugConfigurationExtension : RunConfigurationExtension() {

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
//        TODO check if project is kotlin
        return true
    }

    override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
        configuration: T,
        params: JavaParameters?,
        runnerSettings: RunnerSettings?
    ) {
        if (params != null && params.classPath != null && params.classPath.pathList.isNotEmpty()) {
            params.classPath.pathList.forEach {
                if (it.contains("kotlinx-coroutines-debug")) {
                    // if debug library is included into project, add agent which installs probes
                    params.vmParametersList?.add("-javaagent:$it")
                    params.vmParametersList?.add("-ea")

                    // add listener to put coroutines tab into debugger tab
                    (configuration as RunConfigurationBase<*>).project.messageBus.connect().subscribe(
                        XDebuggerManager.TOPIC,
                        object : XDebuggerManagerListener {
                            var tabCreated = false

                            override fun processStarted(debugProcess: XDebugProcess) {
                                if (!tabCreated) {
                                    val project = debugProcess.session.project
                                    val session = DebuggerManagerEx.getInstanceEx(project).context.debuggerSession
                                    DebuggerInvocationUtil.swingInvokeLater(project) {
                                        registerCoroutinesPanel(session?.xDebugSession?.ui ?: return@swingInvokeLater, session)
                                        // some weird shit happens here (callback is called n times where n is number of project's runs), so
                                        tabCreated = true
                                    }
                                }
                            }
                        })
                    return
                }
            }

        }
    }

    private fun registerCoroutinesPanel(ui: RunnerLayoutUi, session: DebuggerSession) {
        val panel = CoroutinesPanel(session.project, session.contextManager)
        val threadsContent = ui.createContent(
            DebuggerContentInfo.THREADS_CONTENT, panel, "Coroutines", // TODO
            AllIcons.Debugger.AttachToProcess, null
        )
        threadsContent.isCloseable = false
        ui.addContent(threadsContent, 0, PlaceInGrid.left, true)
        ui.addListener(object : ContentManagerAdapter() {
            override fun selectionChanged(event: ContentManagerEvent) {
                if (event.content === threadsContent) {
                    if (threadsContent.isSelected) {
                        panel.setUpdateEnabled(true)
                        if (panel.isRefreshNeeded) {
                            panel.rebuildIfVisible(DebuggerSession.Event.CONTEXT)
                        }
                    } else {
                        panel.setUpdateEnabled(false)
                    }
                }
            }
        }, threadsContent)
    }
}
