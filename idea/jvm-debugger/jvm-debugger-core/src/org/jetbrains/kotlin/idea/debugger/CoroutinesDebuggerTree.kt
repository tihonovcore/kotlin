package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.JavaValue
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
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
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
import com.intellij.xdebugger.frame.XNamedValue
import com.sun.jdi.ArrayReference
import com.sun.jdi.ClassType
import com.sun.jdi.StringReference
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext
import com.sun.jdi.ObjectReference
import javax.swing.Icon
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener

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
                        if (debuggerTreeNode.descriptor is CoroutineDescriptorImpl) {
                            addChildren(myChildren, debugProcess, debuggerTreeNode.descriptor, evalContext)
                            DebuggerInvocationUtil.swingInvokeLater(project) {
                                updateUI(true)
                            }
                        }
                    }
                })
            }

            override fun isExpandable(builderNode: TreeBuilderNode): Boolean {
                return this@CoroutinesDebuggerTree.isExpandable(builderNode as DebuggerTreeNodeImpl)
            }
        }
        model.setRoot(nodeFactory.defaultNode)
        model.addTreeModelListener(createListener())

        setModel(model)

        val search = TreeSpeedSearch(this)
        search.comparator = SpeedSearchComparator(false)
    }

    private fun addChildren(
        children: MutableList<DebuggerTreeNodeImpl>,
        debugProcess: DebugProcessImpl,
        descriptor: NodeDescriptorImpl,
        evalContext: EvaluationContextImpl
    ) {
        when ((descriptor as CoroutineDescriptorImpl).state.state) {
            "RUNNING" -> {
                val proxy = ThreadReferenceProxyImpl(
                    debugProcess.virtualMachineProxy,
                    descriptor.state.thread
                )
                val frames = proxy.forceFrames()
                frames.forEach { frame ->
                    children.add(createFrameDescriptor(descriptor, evalContext, frame))
                }
            }
            "SUSPENDED" -> {
                descriptor.state.stackTrace.forEach {
                    children.add(createCoroutineFrameDescriptor(descriptor, evalContext, it))
                }
                // TODO add vars to other place, here should be stacktrace
            }
        }
    }


    private fun createFrameDescriptor(
        descriptor: NodeDescriptorImpl,
        evalContext: EvaluationContextImpl,
        frame: StackFrameProxyImpl
    ): DebuggerTreeNodeImpl {
        return myNodeManager.createNode(
            myNodeManager.getStackFrameDescriptor(
                descriptor,
                frame
            ),
            evalContext
        )
    }

    private fun createCoroutineFrameDescriptor(
        descriptor: CoroutineDescriptorImpl,
        evalContext: EvaluationContextImpl,
        frame: StackTraceElement
    ): DebuggerTreeNodeImpl {
        return myNodeManager.createNode(
            myNodeManager.getDescriptor(
                descriptor,
                CoroutineStackFrameData(descriptor.state, frame)
            ), evalContext
        )
    }

    private fun createListener() = object : TreeModelListener {
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
            return "${state.name}: ${state.state}"
        }

        override fun isExpandable(): Boolean {
            return state.state != "CREATED" // TODO
        }

        override fun setContext(context: EvaluationContextImpl?) {

        }
    }

    class CoroutineStackFrameData(val state: CoroutineState, val frame: StackTraceElement) :
        DescriptorData<NodeDescriptorImpl>() {

        override fun hashCode() = frame.hashCode()

        override fun equals(other: Any?): Boolean {
            return if (other is CoroutineStackFrameData) {
                other.frame == frame
            } else false
        }

        override fun createDescriptorImpl(project: Project): NodeDescriptorImpl {
            return SuspendStackFrameDescriptor(state, frame) // TODO
        }

        override fun getDisplayKey(): DisplayKey<NodeDescriptorImpl> = SimpleDisplayKey(frame)
    }

    class SuspendStackFrameDescriptor(val state: CoroutineState, val frame: StackTraceElement) :
        NodeDescriptorImpl() {
        override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener?): String {
            return "${frame.methodName}:${frame.lineNumber}, ${frame.className}" // TODO package
        }

        override fun setContext(context: EvaluationContextImpl?) {
//            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun isExpandable() = false
    }

    /**
     * For the case when no data inside frame is available
     */
    class EmptyStackFrameDescriptor(val frame: StackTraceElement) : NodeDescriptorImpl() {
        override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener?): String {
            return "${frame.methodName}:${frame.lineNumber}, ${frame.className}" // TODO package
        }

        override fun setContext(context: EvaluationContextImpl?) {
        }

        override fun isExpandable() = false
    }

}