package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.descriptors.data.DescriptorData
import com.intellij.debugger.impl.descriptors.data.DisplayKey
import com.intellij.debugger.impl.descriptors.data.SimpleDisplayKey
import com.intellij.debugger.impl.descriptors.data.StackFrameData
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.settings.ThreadsViewSettings
import com.intellij.debugger.ui.impl.tree.TreeBuilder
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode
import com.intellij.debugger.ui.impl.watch.*
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.SpeedSearchComparator
import com.intellij.ui.TreeSpeedSearch
import com.intellij.util.ui.tree.TreeModelAdapter
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext
import javax.swing.Icon
import javax.swing.SwingUtilities
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
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

    init {
        setScrollsOnExpand(false)
        val context = DebuggerManagerEx.getInstanceEx(project).context
        val debugProcess = context.debugProcess
        val model = object : TreeBuilder(this) {
            override fun buildChildren(node: TreeBuilderNode) {
                val debuggerTreeNode = node as DebuggerTreeNodeImpl
                if (debuggerTreeNode.descriptor is DefaultNodeDescriptor) {
                    return
                }

                node.add(myNodeManager.createMessageNode(MessageDescriptor.EVALUATING))
                debugProcess?.managerThread?.schedule(object : BuildNodeCommand(debuggerTreeNode) {
                    override fun threadAction(suspendContext: SuspendContextImpl) {
                        val evalContext = debuggerContext.createEvaluationContext() ?: return
                        (debuggerTreeNode.descriptor as CoroutineDescriptorImpl).state.stackTrace.forEach { frame ->
                            if (frame.methodName != "\b")
                                myChildren.add(
                                    myNodeManager.createNode(
                                        myNodeManager.getDescriptor(debuggerTreeNode.descriptor, CoroutineStackFrameData(frame)),
                                        evalContext
                                    )
                                )
                        }
                        DebuggerInvocationUtil.swingInvokeLater(project) {
                            updateUI(true)
                        }
                    }
                })
            }

            override fun isExpandable(builderNode: TreeBuilderNode): Boolean {
                return this@CoroutinesDebuggerTree.isExpandable(builderNode as DebuggerTreeNodeImpl)
            }
        }
        model.setRoot(nodeFactory.defaultNode)
        model.addTreeModelListener(
            object : TreeModelListener {
                override fun treeNodesChanged(event: TreeModelEvent) {
                    hideTooltip()
                }

                override fun treeNodesInserted(event: TreeModelEvent) {
                    hideTooltip()
                }

                override fun treeNodesRemoved(event: TreeModelEvent) {
                    hideTooltip()
                }

                override fun treeStructureChanged(event: TreeModelEvent) {
                    hideTooltip()
                }
            })

        setModel(model)

        val search = TreeSpeedSearch(this)
        search.comparator = SpeedSearchComparator(false)
    }

    override fun isExpandable(node: DebuggerTreeNodeImpl): Boolean {
        val descriptor = node.descriptor
        return descriptor.isExpandable
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
            val evaluationContext = debuggerContext.createEvaluationContext() ?: return
            val executionContext = ExecutionContext(evaluationContext, context.frameProxy ?: return)

            for (state in CoroutineDumpAction.buildCoroutineStates(executionContext)) {
                root.add(nodeFactory.createNode(nodeFactory.getDescriptor(null, CoroutineData(state)), evaluationContext))
            }

            DebuggerInvocationUtil.swingInvokeLater(project) {
                mutableModel.setRoot(root)
                treeChanged()
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

    class CoroutineData(private val state: CoroutineState) : DescriptorData<CoroutineDescriptorImpl>() {

        override fun createDescriptorImpl(project: Project): CoroutineDescriptorImpl {
            return CoroutineDescriptorImpl(state)
        }

        override fun equals(other: Any?): Boolean {
            return if (other !is CoroutineState) {
                false
            } else state == other
        }

        override fun hashCode(): Int {
            return state.hashCode()
        }

        override fun getDisplayKey(): DisplayKey<CoroutineDescriptorImpl> {
            return SimpleDisplayKey(state)
        }
    }

    class CoroutineDescriptorImpl(val state: CoroutineState) : NodeDescriptorImpl() {
        private var myName: String? = null
        var suspendContext: SuspendContextImpl? = null
        val icon: Icon
            get() = when {
                state.isSuspended -> AllIcons.Debugger.ThreadSuspended
                state.state == "CREATED" -> AllIcons.Debugger.ThreadStates.Idle
                else -> AllIcons.Debugger.ThreadRunning
            }

        override fun getName(): String? {
            return myName
        }

        // TODO
        @Throws(EvaluateException::class)
        override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener): String {
            DebuggerManagerThreadImpl.assertIsManagerThread()
            return state.name
        }

        override fun isExpandable(): Boolean {
            return !state.isEmptyStackTrace
        }

        override fun setContext(context: EvaluationContextImpl?) {
//            val coroutineState = state
//            val suspendManager = context?.debugProcess?.suspendManager
//            val suspendContext = context?.suspendContext

//            myIsExpandable = calcExpandable(state.isSuspended)
//            this.suspendContext =
//                if (suspendManager != null) SuspendManagerUtil.findContextByThread(suspendManager, coroutineState) else suspendContext
//            isAtBreakpoint = coroutineState.isAtBreakpoint
//            isCurrent = if (suspendContext != null) suspendContext.thread === coroutineState else false
//            isFrozen = suspendManager?.isFrozen(coroutineState) ?: isSuspended
        }
    }

    class CoroutineStackFrameData(val info: StackTraceElement) : DescriptorData<CoroutineStackFrameDescriptor>() {
        override fun hashCode(): Int {
//            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            return 0
        }

        override fun equals(other: Any?): Boolean {
//            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            return true
        }

        override fun createDescriptorImpl(project: Project): CoroutineStackFrameDescriptor {
//            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            return CoroutineStackFrameDescriptor(info)
        }

        override fun getDisplayKey(): DisplayKey<CoroutineStackFrameDescriptor> {
//            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            return SimpleDisplayKey(info)
        }

    }

    class CoroutineStackFrameDescriptor(val info: StackTraceElement) : NodeDescriptorImpl() {
        override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener?): String {
            return "${info.methodName}:${info.lineNumber}, ${info.className}" // TODO
        }

        override fun setContext(context: EvaluationContextImpl?) {
//            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun isExpandable() = false
    }
}