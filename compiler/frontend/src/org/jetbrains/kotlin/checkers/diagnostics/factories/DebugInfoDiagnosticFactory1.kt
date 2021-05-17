/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.checkers.diagnostics.factories

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.checkers.ReferenceVariantsProvider
import org.jetbrains.kotlin.checkers.utils.CheckerTestUtil
import org.jetbrains.kotlin.checkers.utils.TypedNode
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.PositioningStrategies
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.Renderers
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactory
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassDescriptor
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

class DebugInfoDiagnosticFactory1 : DiagnosticFactory1<PsiElement, String>,
    DebugInfoDiagnosticFactory {
    private val privateName: String

    override val name: String
        get() = "INFO"

    override val withExplicitDefinitionOnly: Boolean

    override val typedNodes: MutableList<TypedNode> = mutableListOf()

    @ExperimentalStdlibApi
    override fun createDiagnostic(
        expression: KtExpression,
        bindingContext: BindingContext,
        dataFlowValueFactory: DataFlowValueFactory?,
        languageVersionSettings: LanguageVersionSettings?,
        moduleDescriptor: ModuleDescriptorImpl?
    ) = when (privateName) {
        EXPRESSION_TYPE.privateName -> {
            val types = ReferenceVariantsProvider.instance.getAvailableReferences(expression)!!

            val result = types.map {
                val type = when (it) {
                    is LocalVariableDescriptor -> it.type.toString()
                    is ValueParameterDescriptor -> it.type.toString()
                    is PropertyDescriptorImpl -> it.type.toString()
                    else -> "null"
                }

                "${it.name}: $type"
            }.joinToString("; ")

            val type = CheckerTestUtil.getTypeInfo(
                expression,
                bindingContext,
                dataFlowValueFactory,
                languageVersionSettings,
                moduleDescriptor
            ).first

            typedNodes += TypedNode(type, expression, types.toList())

            val declarationDescriptor = type?.constructor?.declarationDescriptor
            val supertypes = type?.constructor?.supertypes?.toString() ?: ""

            if (declarationDescriptor is LazyClassDescriptor) {
                val members = declarationDescriptor.declaredCallableMembers.joinToString {
                    it.toString().replace(Regex(" defined in .*?$"), "")
                }

                val md = MessageDigest.getInstance("MD5")
                md.update(members.encodeToByteArray())

                val membersHash = DatatypeConverter.printHexBinary(md.digest()).toLowerCase()

                recordedTypes[type.constructor.toString()] = membersHash to supertypes
            }

            this.on(expression, "$type$supertypes${if (result.isNotEmpty()) " | $result" else ""}")
        }
        CALL.privateName -> {
            val (fqName, typeCall) = CheckerTestUtil.getCallDebugInfo(expression, bindingContext)
            this.on(expression, Renderers.renderCallInfo(fqName, typeCall))
        }
        else -> throw NotImplementedError("Creation diagnostic '$name' isn't supported.")
    }

    private constructor(name: String, severity: Severity) : super(severity, PositioningStrategies.DEFAULT) {
        this.privateName = name
        this.withExplicitDefinitionOnly = false
    }

    private constructor(name: String, severity: Severity, withExplicitDefinitionOnly: Boolean) : super(
        severity,
        PositioningStrategies.DEFAULT
    ) {
        this.privateName = name
        this.withExplicitDefinitionOnly = withExplicitDefinitionOnly
    }

    companion object {
        val EXPRESSION_TYPE = create(
            "EXPRESSION_TYPE",
            Severity.INFO,
            true
        )

        var recordedTypes = mutableMapOf<String, Pair<String, String>>()

        val CALL = create(
            "CALL",
            Severity.INFO,
            true
        )

        fun create(name: String, severity: Severity): DebugInfoDiagnosticFactory1 {
            return DebugInfoDiagnosticFactory1(name, severity)
        }

        fun create(name: String, severity: Severity, withExplicitDefinitionOnly: Boolean): DebugInfoDiagnosticFactory1 {
            return DebugInfoDiagnosticFactory1(name, severity, withExplicitDefinitionOnly)
        }
    }
}
