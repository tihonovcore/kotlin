package org.jetbrains.kotlin.test.runners.ir

import java.io.File
import java.util.function.Consumer

fun createDataset(runTest: Consumer<String>) {
    val source = "/home/tihonovcore/diploma/kotlin/compiler/testData/codegen/box"

    File(source).walkTopDown().forEach { file ->
        if (file.mustBeSkipped()) return@forEach
        println(file.path)

        runTest.accept(file.path)
    }
}

private fun File.mustBeSkipped(): Boolean {
    if (isDirectory || extension != "kt") return true

    return with(readText()) {
        contains(Regex("//\\s*?FILE:"))
                || contains(Regex("//\\s*?WITH_RUNTIME"))
                || contains(Regex("//\\s*?FILE: .*?\\.java"))
                || name in listOf("kt30402.kt", "crossTypeEquals.kt")
    }
}
