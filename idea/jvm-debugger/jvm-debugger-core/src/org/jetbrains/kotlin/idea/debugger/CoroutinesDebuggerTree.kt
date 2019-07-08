package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.settings.ThreadsViewSettings
import com.intellij.debugger.ui.impl.watch.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.ui.tree.TreeModelAdapter
import java.util.*
import javax.swing.SwingUtilities
import javax.swing.event.TreeModelEvent
import javax.swing.tree.TreePath

class CoroutinesDebuggerTree(project: Project) : DebuggerTree(project) {
    private val logger = Logger.getInstance("#com.intellij.debugger.ui.impl.CoroutinesDebuggerTree")

    override fun createNodeManager(project: Project): NodeManagerImpl {
        return object : NodeManagerImpl(project, this) {
            override fun getContextKey(frame: StackFrameProxyImpl?): String? {
                return "CoroutinesView"
            }
        }
    }

    override fun isExpandable(node: DebuggerTreeNodeImpl): Boolean {
        val descriptor = node.descriptor
        return if (descriptor is StackFrameDescriptorImpl) {
            false
        } else descriptor.isExpandable
    }

    override fun build(context: DebuggerContextImpl) {
        val session = context.debuggerSession
        val command = RefreshCoroutinesTreeCommand(session)

        val state = if (session != null) session.state else DebuggerSession.State.DISPOSED
        if (ApplicationManager.getApplication().isUnitTestMode || state == DebuggerSession.State.PAUSED || state == DebuggerSession.State.RUNNING) {
            showMessage(MessageDescriptor.EVALUATING)
            context.debugProcess!!.managerThread.schedule(command)
        } else {
            showMessage(if (session != null) session.stateDescription else DebuggerBundle.message("status.debug.stopped"))
        }
    }

    private inner class RefreshCoroutinesTreeCommand internal constructor(private val mySession: DebuggerSession?) : DebuggerCommandImpl() {

        override fun action() {
            val root = nodeFactory.defaultNode
            mySession ?: return
            val debugProcess = mySession.process
            if (!debugProcess.isAttached) {
                return
            }
            val context = mySession.contextManager.context
            val suspendContext = context.suspendContext
            val suspendContextThread = suspendContext?.thread
            val showGroups = ThreadsViewSettings.getInstance().SHOW_THREAD_GROUPS

            try {
                val currentThread = if (ThreadsViewSettings.getInstance().SHOW_CURRENT_THREAD) suspendContextThread else null
                val vm = debugProcess.virtualMachineProxy

                val evaluationContext = if (suspendContext != null) debuggerContext.createEvaluationContext() else null
                val nodeManager = nodeFactory

                if (showGroups) {
                    var topCurrentGroup: ThreadGroupReferenceProxyImpl? = null

                    if (currentThread != null) {
                        topCurrentGroup = currentThread.threadGroupProxy()
                        if (topCurrentGroup != null) {
                            var parentGroup: ThreadGroupReferenceProxyImpl? = topCurrentGroup.parent()
                            while (parentGroup != null) {
                                topCurrentGroup = parentGroup
                                parentGroup = parentGroup.parent()
                            }
                        }

                        if (topCurrentGroup != null) {
                            root.add(nodeManager.createNode(nodeManager.getThreadGroupDescriptor(null, topCurrentGroup), evaluationContext))
                        } else {
                            root.add(nodeManager.createNode(nodeManager.getThreadDescriptor(null, currentThread), evaluationContext))
                        }
                    }

                    for (group in vm.topLevelThreadGroups()) {
                        if (group !== topCurrentGroup) {
                            val threadGroup = nodeManager.createNode(nodeManager.getThreadGroupDescriptor(null, group), evaluationContext)
                            root.add(threadGroup)
                        }
                    }
                } else {
                    // do not show thread groups
                    if (currentThread != null) {
                        root.insert(nodeManager.createNode(nodeManager.getThreadDescriptor(null, currentThread), evaluationContext), 0)
                    }
                    val allThreads = ArrayList(vm.allThreads())
                    allThreads.sortWith(ThreadReferenceProxyImpl.ourComparator)

                    for (threadProxy in allThreads) {
                        if (threadProxy == currentThread) {
                            continue
                        }
                        root.add(nodeManager.createNode(nodeManager.getThreadDescriptor(null, threadProxy), evaluationContext))
                    }
                }
            } catch (ex: Exception) {
                root.add(MessageDescriptor.DEBUG_INFO_UNAVAILABLE)
                logger.debug(ex)
            }

            val hasCoroutineToSelect = suspendContextThread != null // thread can be null if pause was pressed
            val groups: MutableList<ThreadGroupReferenceProxyImpl>
            if (hasCoroutineToSelect && showGroups) {
                groups = ArrayList()
                var group: ThreadGroupReferenceProxyImpl? = suspendContextThread!!.threadGroupProxy()
                while (group != null) {
                    groups.add(group)
                    group = group.parent()
                }
                groups.reverse()
            } else {
                groups = mutableListOf()
            }

            DebuggerInvocationUtil.swingInvokeLater(project) {
                mutableModel.setRoot(root)
                treeChanged()
                if (hasCoroutineToSelect) {
                    selectCoroutine(groups, suspendContextThread, true)
                }
            }
        }

        private fun selectCoroutine(
            pathToThread: MutableList<ThreadGroupReferenceProxyImpl>,
            thread: ThreadReferenceProxyImpl?,
            expand: Boolean
        ) {
            logger.assertTrue(SwingUtilities.isEventDispatchThread())
            class MyTreeModelAdapter : TreeModelAdapter() {
                fun structureChanged(node: DebuggerTreeNodeImpl) {
                    val enumeration = node.children()
                    while (enumeration.hasMoreElements()) {
                        val child = enumeration.nextElement() as DebuggerTreeNodeImpl
                        nodeChanged(child)
                    }
                }

                private fun nodeChanged(debuggerTreeNode: DebuggerTreeNodeImpl) {
                    if (pathToThread.size == 0) {
                        if (debuggerTreeNode.descriptor is ThreadDescriptorImpl && (debuggerTreeNode.descriptor as ThreadDescriptorImpl).threadReference === thread) {
                            removeListener()
                            val treePath = TreePath(debuggerTreeNode.path)
                            selectionPath = treePath
                            if (expand && !isExpanded(treePath)) {
                                expandPath(treePath)
                            }
                        }
                    } else {
                        if (debuggerTreeNode.descriptor is ThreadGroupDescriptorImpl && (debuggerTreeNode.descriptor as ThreadGroupDescriptorImpl).threadGroupReference === pathToThread[0]) {
                            pathToThread.removeAt(0)
                            expandPath(TreePath(debuggerTreeNode.path))
                        }
                    }
                }

                private fun removeListener() {
                    val listener = this
                    SwingUtilities.invokeLater { model.removeTreeModelListener(listener) }
                }

                override fun treeStructureChanged(event: TreeModelEvent) {
                    if (event.path.size <= 1) {
                        removeListener()
                        return
                    }
                    structureChanged(event.treePath.lastPathComponent as DebuggerTreeNodeImpl)
                }
            }

            val listener = MyTreeModelAdapter()
            listener.structureChanged(model.root as DebuggerTreeNodeImpl)
            model.addTreeModelListener(listener)
        }
    }
}