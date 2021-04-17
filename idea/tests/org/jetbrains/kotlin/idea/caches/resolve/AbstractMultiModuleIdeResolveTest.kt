/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.resolve

import com.google.gson.Gson
import com.google.gson.JsonParser
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
        stringDatasetDirectory: String,
        integerDatasetDirectory: String,
        useTypes: Boolean = false
    ) {
        File(stringDatasetDirectory).mkdirs()
        File(integerDatasetDirectory).mkdirs()

        val output = File("$stringDatasetDirectory/dataset.json").apply {
            if (exists()) writeText("")
            else createNewFile()
        }

        File(sourceCodeDirectory).walkTopDown().forEach { file ->
            if (file.mustBeSkipped()) return@forEach
            println(file.path)

            val sourceKtFile = PsiManager.getInstance(project).findFile(file.toVirtualFile()!!) as KtFile
            val range2type = checkFile(sourceKtFile, file)
            if (!useTypes) range2type.clear()

            try {
                val samples = createSamplesForDataset(sourceKtFile, range2type, 3, 3).skipTooBig()
                output.appendText(samples.json() + System.lineSeparator())
            } catch (e: Exception) {
                println(file.absolutePath)
                println(e.message)
                println()
            }
        }

        string2integer(stringDatasetDirectory, integerDatasetDirectory)
    }

    private fun string2integer(stringDatasetDirectory: String, integerDatasetDirectory: String) {
        val vocab = mutableSetOf<String>()
        val gson = Gson()

        File("$stringDatasetDirectory/dataset.json").forEachLine { line ->
            JsonParser.parseString(line).asJsonArray.forEach { sample ->
                gson.fromJson(sample, StringDatasetSample::class.java).apply {
                    leafPaths.forEach { path ->
                        path.forEach { node -> vocab.add(node) }
                    }
                    rootPath.forEach { node -> vocab.add(node) }
                    if (target != null) vocab.add(target)
                }
            }
        }

        val string2integer = mutableMapOf<String, Int>()
        val integer2string = mutableMapOf<Int, String>()
        vocab.sorted().forEachIndexed { integer, string ->
            string2integer[string] = integer
            integer2string[integer] = string
        }
        File("$integerDatasetDirectory/string2integer.json").writeText(string2integer.json())
        File("$integerDatasetDirectory/integer2string.json").writeText(integer2string.json())

        val integerDataset = File("$integerDatasetDirectory/dataset.json").apply {
            if (exists()) writeText("")
            else createNewFile()
        }

        File("$stringDatasetDirectory/dataset.json").forEachLine { line ->
            val integerSamples = JsonParser.parseString(line).asJsonArray.map {
                val sample = gson.fromJson(it, StringDatasetSample::class.java)
                IntegerDatasetSample(
                    sample.leafPaths.map { path -> path.map { node -> string2integer[node]!! } },
                    sample.rootPath.map { node -> string2integer[node]!! },
                    sample.indexAmongBrothers,
                    string2integer[sample.target]
                )
            }.json()

            integerDataset.appendText(integerSamples + System.lineSeparator())
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
//        val sourceCodeDirectory = "/home/tihonovcore/diploma/kotlin/idea/tests/org/jetbrains/kotlin/diploma/samples/small"
        val sourceCodeDirectory = "/home/tihonovcore/diploma/kotlin/compiler/testData/codegen/box"
        val stringDatasetDirectory = "/home/tihonovcore/diploma/kotlin/idea/tests/org/jetbrains/kotlin/diploma/out/string"
        val integerDatasetDirectory = "/home/tihonovcore/diploma/kotlin/idea/tests/org/jetbrains/kotlin/diploma/out/integer"

        createDataset(sourceCodeDirectory, stringDatasetDirectory, integerDatasetDirectory)
    }

    @TestMetadata("generationPipeline")
    fun testGenerationPipeline() = runPipeline()
}
