/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations

import org.jetbrains.kotlin.fir.FirPureAbstractElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirDelegatedConstructorCall
import org.jetbrains.kotlin.fir.references.FirControlFlowGraphReference
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.fir.visitors.*

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

abstract class FirConstructor : FirPureAbstractElement(), FirFunction<FirConstructor>, FirCallableMemberDeclaration<FirConstructor> {
    abstract override val source: FirSourceElement?
    abstract override val session: FirSession
    abstract override val resolvePhase: FirResolvePhase
    abstract override val returnTypeRef: FirTypeRef
    abstract override val receiverTypeRef: FirTypeRef?
    abstract override val typeParameters: List<FirTypeParameter>
    abstract override val controlFlowGraphReference: FirControlFlowGraphReference
    abstract override val valueParameters: List<FirValueParameter>
    abstract override val status: FirDeclarationStatus
    abstract override val containerSource: DeserializedContainerSource?
    abstract override val annotations: List<FirAnnotationCall>
    abstract override val symbol: FirConstructorSymbol
    abstract val delegatedConstructor: FirDelegatedConstructorCall?
    abstract override val body: FirBlock?
    abstract val isPrimary: Boolean

    override fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R = visitor.visitConstructor(this, data)

    abstract override fun <D> transformReturnTypeRef(transformer: FirTransformer<D>, data: D): FirConstructor

    abstract override fun <D> transformReceiverTypeRef(transformer: FirTransformer<D>, data: D): FirConstructor

    abstract override fun <D> transformControlFlowGraphReference(transformer: FirTransformer<D>, data: D): FirConstructor

    abstract override fun <D> transformValueParameters(transformer: FirTransformer<D>, data: D): FirConstructor

    abstract override fun <D> transformStatus(transformer: FirTransformer<D>, data: D): FirConstructor

    abstract override fun <D> transformAnnotations(transformer: FirTransformer<D>, data: D): FirConstructor
}
