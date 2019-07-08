/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.debugger

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings


@Suppress("IncompatibleAPI")
class CoroutinesDebugConfigurationExtension : RunConfigurationExtension() {
    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        return true
    }

    override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
        configuration: T,
        params: JavaParameters?,
        runnerSettings: RunnerSettings?
    ) {
//        if (runnerSettings != null || !isApplicableFor(configuration)) {
//            return
//        }
//        val agentPath = JavaExecutionUtil.handleSpacesInAgentPath(
//            PathUtil.getJarPathForClass(TestDiscoveryProjectData::class.java),
//            "testDiscovery",
//            TEST_DISCOVERY_AGENT_PATH
//        )
//            ?: return
        if (params != null && params.classPath != null && params.classPath.pathList.isNotEmpty()) {
            params.classPath.pathList.forEach {
                if (it.contains("kotlinx-coroutines-debug")) {
                    params.vmParametersList?.add("-javaagent:$it")
                    return
                }
            }
        }
//        val agentPath =
//            "/Users/aleksandr.prokopyev/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-debug/1.3.0-M2/299988c9fdbbf550e08b5aaf99a0c102a1a7d31a/kotlinx-coroutines-debug-1.3.0-M2.jar"

    }
}
