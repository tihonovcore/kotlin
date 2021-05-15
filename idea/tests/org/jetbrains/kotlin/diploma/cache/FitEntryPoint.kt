/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.cache

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.diploma.*
import org.jetbrains.kotlin.diploma.analysis.convertToJson
import org.jetbrains.kotlin.diploma.analysis.extractTypes
import org.jetbrains.kotlin.idea.caches.resolve.checkFile
import org.jetbrains.kotlin.idea.caches.resolve.getMapPsiToTypeId
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * @return integer dataset sample and types info at json
 */
fun extractPathsFrom(path: String, project: Project): Pair<String, String> {
    val file = File(path)

    val ktFile = PsiManager.getInstance(project).findFile(file.toVirtualFile()!!) as KtFile
    val typesFromFile = extractTypes(ktFile)
    val class2spec = typesFromFile.second
    val typedNodes = checkFile(ktFile, file)

    val (stringSample, from) = createSampleForFit(ktFile, getMapPsiToTypeId(class2spec, typedNodes), 5..25, 25)
    val integerSample = stringSample.toIntegerSample()

    save(ktFile, from)

    return Pair(integerSample.json(), typesFromFile.convertToJson())
}

//fun workWithPrediction(kind, type)