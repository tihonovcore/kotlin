/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations

import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.visitors.*

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

interface FirTypeParametersOwner : FirTypeParameterRefsOwner {
    override val source: FirSourceElement?
    override val typeParameters: List<FirTypeParameter>

    override fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R = visitor.visitTypeParametersOwner(this, data)

    override fun replaceSource(newSource: FirSourceElement?)

    override fun <D> transformTypeParameters(transformer: FirTransformer<D>, data: D): FirTypeParametersOwner
}
