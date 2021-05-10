/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.resolve

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.util.io.exists
import org.jetbrains.kotlin.checkers.BaseDiagnosticsTest
import org.jetbrains.kotlin.checkers.ReferenceVariantsProvider
import org.jetbrains.kotlin.checkers.diagnostics.factories.DebugInfoDiagnosticFactory1
import org.jetbrains.kotlin.checkers.utils.CheckerTestUtil
import org.jetbrains.kotlin.checkers.utils.DiagnosticsRenderingConfiguration
import org.jetbrains.kotlin.checkers.utils.ExtractedType
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.diploma.*
import org.jetbrains.kotlin.diploma.analysis.*
import org.jetbrains.kotlin.idea.codeInsight.ReferenceVariantsHelper
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import org.jetbrains.kotlin.idea.project.KotlinMultiplatformAnalysisModeComponent
import org.jetbrains.kotlin.idea.codeMetaInfo.AbstractDiagnosticCodeMetaInfoTest
import org.jetbrains.kotlin.idea.resolve.getDataFlowValueFactory
import org.jetbrains.kotlin.idea.resolve.getLanguageVersionSettings
import org.jetbrains.kotlin.idea.stubs.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestMetadata
import java.io.File
import java.lang.System.exit
import java.nio.file.Files
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
            val (_, class2spec) = extractTypes(sourceKtFile)
            val extractedTypes = checkFile(sourceKtFile, file)

            try {
                val samples = createSamplesForDataset(sourceKtFile, getMapPsiToTypeId(class2spec, extractedTypes), 5..25, 25).skipTooBig()
                output.appendText(samples.json() + System.lineSeparator())
            } catch (e: Exception) {
                println(file.absolutePath)
                println(e.message)
                println()
            }
        }

        randomSampling(stringDatasetDirectory)
        string2integer(stringDatasetDirectory, integerDatasetDirectory)
    }

    private fun getMapPsiToTypeId(
        class2spec: Map<ClassifierDescriptor, JsonClassSpec>,
        extractedTypes: List<ExtractedType>
    ): Map<PsiElement, Int> {
        return extractedTypes.associate {
            val typeDescriptor = it.type.constructor.declarationDescriptor!!
            it.node to class2spec[typeDescriptor]!!.id
        }
    }

    private fun randomSampling(stringDatasetDirectory: String) {
        val prefix = "/home/tihonovcore/diploma/kotlin/idea/tests/org/jetbrains/kotlin/diploma/out"
        val samples = mutableMapOf<String, MutableList<Pair<Int, Int>>>()

        var lineNumber = 0
        File("$stringDatasetDirectory/dataset.json").forEachLine { line ->
            JsonParser.parseString(line).asJsonArray.forEachIndexed { index, it ->
                val sample = Gson().fromJson(it, StringDatasetSample::class.java)

                samples.putIfAbsent(sample.target!!, mutableListOf())
                samples[sample.target]!! += Pair(lineNumber, index)
            }

            lineNumber++
        }

        val positions = mutableSetOf<Pair<Int, Int>>()
        for (kind in samples.keys) {
            positions += samples[kind]!!.shuffled().take(100)
        }

        lineNumber = 0
        val list = mutableListOf<StringDatasetSample>()
        File("$stringDatasetDirectory/dataset.json").forEachLine { line ->
            JsonParser.parseString(line).asJsonArray.forEachIndexed { index, it ->
                val sample = Gson().fromJson(it, StringDatasetSample::class.java)

                if (Pair(lineNumber, index) in positions) {
                    list += sample

                    if (list.size >= 10) {
                        val out = list.json() + System.lineSeparator()
                        list.clear()

                        File("$prefix/string/tmp.json").appendText(out)
                    }
                }
            }

            lineNumber++
        }

        if (list.isNotEmpty()) {
            val out = list.json() + System.lineSeparator()
            list.clear()

            File("$prefix/string/tmp.json").appendText(out)
        }

        File("$prefix/string/dataset.json").delete()
        File("$prefix/string/tmp.json").renameTo(File("$prefix/string/dataset.json"))
    }

    private fun string2integer(
        stringDatasetDirectory: String,
        integerDatasetDirectory: String,
        printDatasetStatistics: Boolean = true
    ) {
        val pathCountStatistics = mutableMapOf<Int, Int>()
        val pathLengthStatistics = mutableMapOf<Int, Int>()
        val indexStatistics = mutableMapOf<Int, Int>()
        val targetFrequency = mutableMapOf<String, Int>()

        fun <K> MutableMap<K, Int>.update(key: K) {
            val old = get(key) ?: 0
            set(key, old + 1)
        }

        val gson = Gson()

        File("$stringDatasetDirectory/dataset.json").forEachLine { line ->
            JsonParser.parseString(line).asJsonArray.forEach { sample ->
                gson.fromJson(sample, StringDatasetSample::class.java).apply {
                    pathCountStatistics.update(leafPaths.size)

                    leafPaths.forEach { path ->
                        pathLengthStatistics.update(path.size)
                    }

                    if (target != null) {
                        targetFrequency.update(target)
                    }

                    indexStatistics.update(indexAmongBrothers)
                }
            }
        }

        val string2integer = mutableMapOf<String, Int>()
        File("$integerDatasetDirectory/string2integer.json").readText().apply {
            for ((string, integer) in JsonParser.parseString(this).asJsonObject.entrySet()) {
                string2integer[string] = integer.asInt
            }
        }

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
                    sample.typesForLeafPaths,
                    sample.typesForRootPath,
                    sample.leftBrothers.map { node -> string2integer[node]!! },
                    sample.indexAmongBrothers,
                    string2integer[sample.target]
                )
            }.json()

            integerDataset.appendText(integerSamples + System.lineSeparator())
        }

        if (printDatasetStatistics) {
            println("#### TARGET FREQUENCY")
            for ((k, f) in targetFrequency.toList().sortedBy { it.second }.reversed()) {
                println("$f\t\t --> $k")
            }

            fun Map<Int, Int>.greatThen(x: Int) {
                val cnt = filter { it.key > x }.map { it.value }.sum()
                println(">$x: $cnt")
            }

            println()
            println("#### PATH COUNT")
            pathCountStatistics.greatThen(0)
            pathCountStatistics.greatThen(300)
            pathCountStatistics.greatThen(500)
            pathCountStatistics.greatThen(800)
            pathCountStatistics.greatThen(1000)

            println()
            println("#### PATH LENGTH")
            pathLengthStatistics.greatThen(0)
            pathLengthStatistics.greatThen(20)
            pathLengthStatistics.greatThen(25)
            pathLengthStatistics.greatThen(30)
            pathLengthStatistics.greatThen(50)
            pathLengthStatistics.greatThen(60)
            pathLengthStatistics.greatThen(70)

            println()
            println("#### INDEX STAT")
            indexStatistics.greatThen(-1)
            indexStatistics.greatThen(5)
            indexStatistics.greatThen(10)
            indexStatistics.greatThen(15)
            indexStatistics.greatThen(20)
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

        val extractedTypes = mutableListOf<ExtractedType>()
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
            moduleDescriptor = moduleDescriptor as ModuleDescriptorImpl,
            extractedTypes = extractedTypes
        ).filter { diagnosticsFilter.value(it.diagnostic) }

        println()

//        val actualTextWithDiagnostics = CheckerTestUtil.addDiagnosticMarkersToText(
//            file,
//            actualDiagnostics,
//            diagnosticToExpectedDiagnostic = emptyMap(),
//            getFileText = { it.text },
//            uncheckedDiagnostics = emptyList(),
//            withNewInferenceDirective = false,
//            renderDiagnosticMessages = true,
//            range2type
//        ).toString()

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

    private val root = "/home/tihonovcore/diploma"
    val sourceCodeDirectory = "$root/kotlin/compiler/testData/codegen/box"
    val stringDatasetDirectory = "$root/kotlin/idea/tests/org/jetbrains/kotlin/diploma/out/string"
    val integerDatasetDirectory = "$root/kotlin/idea/tests/org/jetbrains/kotlin/diploma/out/integer"
    val typesDatasetDirectory = "$root/kotlin/idea/tests/org/jetbrains/kotlin/diploma/out/types"

    @TestMetadata("createMapping")
    fun testCreateMapping() {
        val vocabulary = mutableSetOf(AFTER_LAST_KIND, UP_ARROW, DOWN_ARROW)

        File(sourceCodeDirectory).walkTopDown().forEach { file ->
            if (file.mustBeSkipped()) return@forEach

            val sourceKtFile = PsiManager.getInstance(project).findFile(file.toVirtualFile()!!) as KtFile
            fun KtElement.dfs(): List<KtElement> {
                return children.filterIsInstance(KtElement::class.java).fold(mutableListOf(this)) { accum, child ->
                    accum.apply { this += child.dfs() }
                }
            }

            vocabulary += sourceKtFile.dfs().map { it.kind() }
        }

        val string2integer = mutableMapOf<String, Int>()
        val integer2string = mutableMapOf<Int, String>()
        vocabulary.sorted().forEachIndexed { integer, string ->
            string2integer[string] = integer
            integer2string[integer] = string
        }

        File(integerDatasetDirectory).mkdirs()
        File("$integerDatasetDirectory/string2integer.json").writeText(string2integer.json())
        File("$integerDatasetDirectory/integer2string.json").writeText(integer2string.json())
    }

    @TestMetadata("extractTypes")
    fun testExtractTypes() {
        File(typesDatasetDirectory).listFiles()!!.forEach { it.delete() }

        File(sourceCodeDirectory).walkTopDown().forEach { file ->
            if (file.mustBeSkipped()) return@forEach
            println(file.path)

            val sourceKtFile = PsiManager.getInstance(project).findFile(file.toVirtualFile()!!) as KtFile
            val json = extractTypes(sourceKtFile).convertToJson()
            val path = Files.createTempFile(Paths.get(typesDatasetDirectory), file.name, ".json")
            path.toFile().writeText(json)
        }
    }

    @TestMetadata("createDataset")
    fun testCreateDataset() = createDataset(sourceCodeDirectory, stringDatasetDirectory, integerDatasetDirectory)

    @TestMetadata("generationPipeline")
    fun testGenerationPipeline() = runPipeline()

    @TestMetadata("showKindExamples")
    fun testShowKindExamples() {
        val vocabulary = mutableSetOf<String>()

        File(sourceCodeDirectory).walkTopDown().forEach { file ->
            if (file.mustBeSkipped()) return@forEach

            val sourceKtFile = PsiManager.getInstance(project).findFile(file.toVirtualFile()!!) as KtFile
            fun KtElement.dfs(): List<KtElement> {
                return children.filterIsInstance(KtElement::class.java).fold(mutableListOf(this)) { accum, child ->
                    accum.apply { this += child.dfs() }
                }
            }

            sourceKtFile.dfs().forEach ff@{
                val kind = it.kind()
                if (kind in vocabulary) return@ff

                println("### $kind")
                println(it.text)
                println("### AT")
                println(it.parent.text)
                println()
                println()
                println()

                vocabulary += kind
            }

            if (vocabulary.size == 109) exit(0)
        }
    }

    /**
     * Creates mapping from kind to shortest syntactically correct subtree
     * with that kind as root
     */
    @TestMetadata("fastFinishSearch")
    fun testFastFinishSearch() {
        val kind2finishList = mutableMapOf<String, List<String>>()

        fun AstWithAfterLast.toList(): List<String> {
            return listOf(original.kind()) + children.flatMap { it.toList() }
        }

        fun KtElement.dfs(): Int {
            val size = children.filterIsInstance(KtElement::class.java).sumBy { it.dfs() } + 1
            val sizeWithAfterLast = 2 * size

            val kind = kind()
            if (kind !in kind2finishList || kind2finishList[kind]!!.size > sizeWithAfterLast) {
                val astWithAfterLast = buildTree(this, null).addAfterLast()
                kind2finishList[kind] = astWithAfterLast.toList()
            }

            return size
        }

        File(sourceCodeDirectory).walkTopDown().forEach { file ->
            if (file.mustBeSkipped()) return@forEach

            val sourceKtFile = PsiManager.getInstance(project).findFile(file.toVirtualFile()!!) as KtFile
            sourceKtFile.dfs()
        }

        File("/home/tihonovcore/diploma/kotlin/idea/tests/org/jetbrains/kotlin/diploma/kind2finishList.json").writeText(kind2finishList.json())
    }

    @TestMetadata("learnParentChildRelation")
    fun testLearnParentChildRelation() {
        val parentChild = mutableMapOf<String, MutableSet<String>>()

        fun KtElement.dfs() {
            val parentKind = kind()

            children.filterIsInstance(KtElement::class.java).forEach { element ->
                val old = parentChild[parentKind] ?: mutableSetOf()
                old += element.kind()
                parentChild[parentKind] = old

                element.dfs()
            }

            val old = parentChild[parentKind] ?: mutableSetOf()
            old += AFTER_LAST_KIND
            parentChild[parentKind] = old
        }

        File(sourceCodeDirectory).walkTopDown().forEach { file ->
            if (file.mustBeSkipped()) return@forEach

            val sourceKtFile = PsiManager.getInstance(project).findFile(file.toVirtualFile()!!) as KtFile
            sourceKtFile.dfs()
        }

        File("/home/tihonovcore/diploma/kotlin/idea/tests/org/jetbrains/kotlin/diploma/parentChild.json").writeText(parentChild.json())
    }
}
