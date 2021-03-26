/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.io.exists
import org.jetbrains.kotlin.checkers.BaseDiagnosticsTest
import org.jetbrains.kotlin.checkers.ReferenceVariantsProvider
import org.jetbrains.kotlin.checkers.diagnostics.factories.DebugInfoDiagnosticFactory1
import org.jetbrains.kotlin.checkers.utils.CheckerTestUtil
import org.jetbrains.kotlin.checkers.utils.DiagnosticsRenderingConfiguration
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.diploma.*
import org.jetbrains.kotlin.idea.codeInsight.ReferenceVariantsHelper
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import org.jetbrains.kotlin.idea.project.KotlinMultiplatformAnalysisModeComponent
import org.jetbrains.kotlin.idea.codeMetaInfo.AbstractDiagnosticCodeMetaInfoTest
import org.jetbrains.kotlin.idea.resolve.getDataFlowValueFactory
import org.jetbrains.kotlin.idea.resolve.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestMetadata
import java.io.File
import java.nio.file.Paths

abstract class AbstractMultiModuleIdeResolveTest : AbstractMultiModuleTest() {

    fun createDataset(
        sourceCodeDirectory: String,
        processedDatasetDirectory: String,
        useTypes: Boolean = false
    ) {
        val output = File("$processedDatasetDirectory/processedDataset.txt").apply {
            parentFile.mkdirs()

            if (exists()) writeText("")
            else createNewFile()
        }

        File(sourceCodeDirectory).walkTopDown().forEach { file ->
            if (file.mustBeSkipped()) return@forEach

            val sourceKtFile = PsiManager.getInstance(project).findFile(file.toVirtualFile()!!) as KtFile
            val range2type = checkFile(sourceKtFile, file)
            if (!useTypes) range2type.clear()

            try {
                createDatasetSamples(sourceKtFile, range2type, 3, 3, 3)
                    .joinToString("") { it.toString() }
                    .also { output.appendText(it) }
            } catch (e: Exception) {
                println(file.absolutePath)
                println(e.message)
                println()
            }
        }
    }

    fun runPipeline() {
        val pipeline = Pipeline(project)
        val file = pipeline.generateFile()

        file.renderTree(emptyMap())
        println("\n\n\n")
        println(file.text)
    }

    private fun KtFile.findCorrespondingFileInTestDir(containingRoot: VirtualFile, testDir: File): File {
        val tempRootPath = Paths.get(containingRoot.path)
        val tempProjectDirPath = tempRootPath.parent
        val tempSourcePath = Paths.get(this.virtualFilePath)

        val relativeToProjectRootPath = tempProjectDirPath.relativize(tempSourcePath)

        val testSourcesProjectDirPath = testDir.toPath()
        val testSourcePath = testSourcesProjectDirPath.resolve(relativeToProjectRootPath)

        require(testSourcePath.exists()) {
            "Can't find file in testdata for copied file $this: checked at path ${testSourcePath.toAbsolutePath()}"
        }

        return testSourcePath.toFile()
    }

    protected open fun checkFile(file: KtFile, expectedFile: File): MutableMap<TextRange, String> {
        val range2type = mutableMapOf<TextRange, String>()

        val resolutionFacade = file.getResolutionFacade()
        val (bindingContext, moduleDescriptor) = resolutionFacade.analyzeWithAllCompilerChecks(listOf(file))

        val directives = KotlinTestUtils.parseDirectives(file.text)
        val diagnosticsFilter = BaseDiagnosticsTest.parseDiagnosticFilterDirective(directives, allowUnderscoreUsage = false)

        ReferenceVariantsProvider.registerInstance(
            ReferenceVariantsHelper(
                bindingContext,
                resolutionFacade,
                moduleDescriptor,
                { _ -> true }
            )
        )

        val actualDiagnostics = CheckerTestUtil.getDiagnosticsIncludingSyntaxErrors(
            bindingContext,
            file,
            markDynamicCalls = false,
            dynamicCallDescriptors = mutableListOf(),
            configuration = DiagnosticsRenderingConfiguration(
                platform = null, // we don't need to attach platform-description string to diagnostic here
                withNewInference = false,
                languageVersionSettings = resolutionFacade.getLanguageVersionSettings(),
            ),
            dataFlowValueFactory = resolutionFacade.getDataFlowValueFactory(),
            moduleDescriptor = moduleDescriptor as ModuleDescriptorImpl
        ).filter { diagnosticsFilter.value(it.diagnostic) }

        val actualTextWithDiagnostics = CheckerTestUtil.addDiagnosticMarkersToText(
            file,
            actualDiagnostics,
            diagnosticToExpectedDiagnostic = emptyMap(),
            getFileText = { it.text },
            uncheckedDiagnostics = emptyList(),
            withNewInferenceDirective = false,
            renderDiagnosticMessages = true,
            range2type
        ).toString()

//        fun write(suffix: String, text: String) {
//            val outputDirName = expectedFile.parentFile.absolutePath
//            val outputFileName = expectedFile.nameWithoutExtension + ".txt"
//
//            File("$outputDirName$suffix/$outputFileName").apply {
//                parentFile.mkdirs()
//                writeText(text)
//            }
//        }
//
//        write("_dumped_types", actualTextWithDiagnostics)
//        write("_ti", DebugInfoDiagnosticFactory1.recordedTypes.map { (type, info) -> "${type}: ${info.first}, ${info.second}" }.joinToString("\n"))

        DebugInfoDiagnosticFactory1.recordedTypes.clear()

        return range2type
    }
}

abstract class AbstractMultiplatformAnalysisTest : AbstractDiagnosticCodeMetaInfoTest() {
    override fun getTestDataPath(): String = "${PluginTestCaseBase.getTestDataPathBase()}/multiplatform"

    override fun setUp() {
        super.setUp()
        KotlinMultiplatformAnalysisModeComponent.setMode(project, KotlinMultiplatformAnalysisModeComponent.Mode.COMPOSITE)
    }

    override fun tearDown() {
        KotlinMultiplatformAnalysisModeComponent.setMode(project, KotlinMultiplatformAnalysisModeComponent.Mode.SEPARATE)
        super.tearDown()
    }
}

class PathExtractor : AbstractMultiModuleIdeResolveTest() {
    override fun getTestDataPath(): String = PluginTestCaseBase.getTestDataPathBase()

    @TestMetadata("createDataset")
    fun testCreateDataset() {
        val sourceCodeDirectory = "/home/tihonovcore/diploma/kotlin/idea/tests/org/jetbrains/kotlin/diploma/samples/small"
        val processedDatasetDirectory = "/home/tihonovcore/diploma/kotlin/idea/tests/org/jetbrains/kotlin/diploma/out/string"

        createDataset(sourceCodeDirectory, processedDatasetDirectory)
    }

    @TestMetadata("generationPipeline")
    fun testGenerationPipeline() = runPipeline()
}
