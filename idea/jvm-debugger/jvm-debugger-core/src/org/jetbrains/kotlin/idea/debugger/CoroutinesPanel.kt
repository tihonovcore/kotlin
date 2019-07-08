/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.actions.DebuggerAction
import com.intellij.debugger.actions.DebuggerActions
import com.intellij.debugger.actions.GotoFrameSourceAction
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerContextListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DebuggerStateManager
import com.intellij.debugger.ui.impl.DebuggerTreePanel
import com.intellij.debugger.ui.impl.ThreadsDebuggerTree
import com.intellij.debugger.ui.impl.watch.DebuggerTree
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.Alarm
//import com.jetbrains.rd.util.catch
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.NoSuchElementException
import javax.swing.JTree

class CoroutinesPanel(project: Project, stateManager: DebuggerStateManager) : DebuggerTreePanel(project, stateManager) {
    @NonNls
    private val HELP_ID = "debugging.debugCoroutines"
    private val myUpdateLabelsAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)
    private val LABELS_UPDATE_DELAY_MS = 200

    init {
        val disposable = DebuggerAction.installEditAction(getCoroutinesTree(), DebuggerActions.EDIT_FRAME_SOURCE)
        registerDisposable(disposable)
        getCoroutinesTree().addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                if (e!!.keyCode == KeyEvent.VK_ENTER && getCoroutinesTree().selectionCount == 1) {
                    GotoFrameSourceAction.doAction(DataManager.getInstance().getDataContext(getCoroutinesTree()))
                }
            }
        })
        add(ScrollPaneFactory.createScrollPane(getCoroutinesTree()), BorderLayout.CENTER)
        stateManager.addListener(object : DebuggerContextListener {
            override fun changeEvent(newContext: DebuggerContextImpl, event: DebuggerSession.Event) {
                if (DebuggerSession.Event.ATTACHED == event || DebuggerSession.Event.RESUME == event) {
                    startLabelsUpdate()
                } else if (DebuggerSession.Event.PAUSE == event || DebuggerSession.Event.DETACHED == event || DebuggerSession.Event.DISPOSE == event) {
                    myUpdateLabelsAlarm.cancelAllRequests()
                }
                if (DebuggerSession.Event.DETACHED == event || DebuggerSession.Event.DISPOSE == event) {
                    stateManager.removeListener(this)
                }
            }
        })
        startLabelsUpdate()
    }

    private fun startLabelsUpdate() {
        if (myUpdateLabelsAlarm.isDisposed) return
        myUpdateLabelsAlarm.cancelAllRequests()
        myUpdateLabelsAlarm.addRequest(object : Runnable {
            override fun run() {
                var updateScheduled = false
                try {
                    if (isUpdateEnabled) {
                        val tree = getCoroutinesTree()
                        val root = tree.model.root as DebuggerTreeNodeImpl
                        if (root != null) {
                            val process = context.debugProcess
                            if (process != null) {
                                process.managerThread.invoke(object : DebuggerCommandImpl() {
                                    override fun action() {
                                        try {
                                            updateNodeLabels(root)
                                        } finally {
                                            reschedule()
                                        }
                                    }

                                    override fun commandCancelled() {
                                        reschedule()
                                    }
                                })
                                updateScheduled = true
                            }
                        }
                    }
                } finally {
                    if (!updateScheduled) {
                        reschedule()
                    }
                }
            }

            private fun reschedule() {
                val session = context.debuggerSession
                if (session != null && session.isAttached && !session.isPaused && !myUpdateLabelsAlarm.isDisposed) {
                    myUpdateLabelsAlarm.addRequest(this, LABELS_UPDATE_DELAY_MS, ModalityState.NON_MODAL)
                }
            }

        }, LABELS_UPDATE_DELAY_MS, ModalityState.NON_MODAL)
    }

    // ok
    override fun dispose() {
        Disposer.dispose(myUpdateLabelsAlarm)
        super.dispose()
    }

    // ok
    private fun updateNodeLabels(from: DebuggerTreeNodeImpl) {
        val children = from.children()
        try {
            while (children.hasMoreElements()) {
                val child = children.nextElement() as DebuggerTreeNodeImpl
                child.descriptor.updateRepresentation(null) { child.labelChanged() }
                updateNodeLabels(child)
            }
        } catch (ignored: NoSuchElementException) { // children have changed - just skip
        }

    }

    // ok
    override fun createTreeView(): DebuggerTree {
        return CoroutinesDebuggerTree(project)
    }

    // TODO
    override fun createPopupMenu(): ActionPopupMenu {
        val group = ActionManager.getInstance().getAction(DebuggerActions.THREADS_PANEL_POPUP) as DefaultActionGroup
        return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.THREADS_PANEL_POPUP, group)
    }

    // ok
    override fun getData(dataId: String): Any? {
        return if (PlatformDataKeys.HELP_ID.`is`(dataId)) {
            HELP_ID
        } else super.getData(dataId)
    }

    // ok
    fun getCoroutinesTree(): CoroutinesDebuggerTree = tree as CoroutinesDebuggerTree

}