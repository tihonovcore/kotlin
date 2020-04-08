/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.checkers

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtExpression

interface ReferenceVariantsProvider {
    fun getAvailableReferences(expression: KtExpression): Collection<DeclarationDescriptor>?

    companion object {
        lateinit var instance: ReferenceVariantsProvider

        fun registerInstance(newInstance: ReferenceVariantsProvider) {
            instance = newInstance
        }
    }
}