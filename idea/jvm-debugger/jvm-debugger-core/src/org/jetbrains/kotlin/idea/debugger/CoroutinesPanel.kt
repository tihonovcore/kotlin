/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.actions.DebuggerAction
import com.intellij.debugger.actions.DebuggerActions
import com.intellij.debugger.actions.GotoFrameSourceAction
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerContextListener
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DebuggerStateManager
import com.intellij.debugger.ui.impl.DebuggerTreePanel
import com.intellij.debugger.ui.impl.watch.DebuggerTree
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl
import com.intellij.debugger.ui.tree.DebuggerTreeNode
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.Alarm
import com.intellij.util.containers.FixedHashMap
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.frame.XVariablesViewBase
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState
//import com.jetbrains.rd.util.catch
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.NoSuchElementException
import javax.swing.JTree

class CoroutinesPanel(project: Project, stateManager: DebuggerStateManager) : DebuggerTreePanel(project, stateManager) {
    @NonNls
    private val HELP_ID = "debugging.debugCoroutines"
    private val myUpdateLabelsAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)
    private val LABELS_UPDATE_DELAY_MS = 200

    init {
        val disposable = installAction(getCoroutinesTree(), DebuggerActions.EDIT_FRAME_SOURCE)
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

    @Suppress("SameParameterValue")
    private fun installAction(tree: JTree, actionName: String): () -> Unit {
        val listener = object : DoubleClickListener() {
            override fun onDoubleClick(e: MouseEvent): Boolean {
                val location = tree.getPathForLocation(e.x, e.y)?.lastPathComponent as? DebuggerTreeNodeImpl ?: return false
                val dataContext = DataManager.getInstance().getDataContext(tree)
//                GotoFrameSourceAction.doAction(dataContext) // TODO
                val context = DebuggerManagerEx.getInstanceEx(project).context
                val view = (context.debuggerSession!!.xDebugSession as XDebugSessionImpl).sessionTab!!.watchesView
                val field = XVariablesViewBase::class.java.getDeclaredField("myTreeStates").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val states = field.get(view) as FixedHashMap<Any, XDebuggerTreeState> // TODO
                // TODO use debugger search scope
                val fileFinder = VirtualFileFinderFactory.getInstance(project).create(GlobalSearchScope.allScope(project))
                val trace = (location.userObject as CoroutinesDebuggerTree.CoroutineStackFrameDescriptor).trace.random() // TODO
                val classFile = fileFinder.findVirtualFileWithHeader(ClassId.topLevel(FqName(trace.className))) // TODO
//                XDebuggerUtil.getInstance().createPosition()
                return true
            }
        }
        listener.installOn(tree)

        val disposable = { listener.uninstall(tree) }
        DebuggerUIUtil.registerActionOnComponent(actionName, tree, disposable)

        return disposable
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

    // TODO
    private fun updateNodeLabels(from: DebuggerTreeNodeImpl) {
        val children = from.children()
        try {
            while (children.hasMoreElements()) {
                val child = children.nextElement() as DebuggerTreeNodeImpl
                child.descriptor.updateRepresentation(
                    null
                ) { child.labelChanged() }
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

    // TODO
    fun getCoroutinesTree(): CoroutinesDebuggerTree = tree as CoroutinesDebuggerTree

}