/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.contracts

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.contracts.effects.ESCalls
import org.jetbrains.kotlin.contracts.effects.ESReturns
import org.jetbrains.kotlin.contracts.factories.UNKNOWN_CONSTANT
import org.jetbrains.kotlin.contracts.factories.lift
import org.jetbrains.kotlin.contracts.structure.ESEffect
import org.jetbrains.kotlin.contracts.structure.EffectSchema
import org.jetbrains.kotlin.contracts.structure.calltree.CTNode
import org.jetbrains.kotlin.contracts.visitors.InfoCollector
import org.jetbrains.kotlin.contracts.visitors.Reducer
import org.jetbrains.kotlin.contracts.visitors.SchemaBuilder
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.smartcasts.ConditionalDataFlowInfo
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactory

class EffectSystem(val languageVersionSettings: LanguageVersionSettings) {
    fun getDataFlowInfoForFinishedCall(
            resolvedCall: ResolvedCall<*>,
            bindingTrace: BindingTrace,
            moduleDescriptor: ModuleDescriptor
    ): DataFlowInfo {
        if (!languageVersionSettings.supportsFeature(LanguageFeature.ContractEffects)) return DataFlowInfo.EMPTY

        // Prevent launch of effect system machinery on pointless cases (constants/enums/constructors/etc.)
        val callExpression = resolvedCall.call.callElement as? KtCallExpression ?: return DataFlowInfo.EMPTY
        if (callExpression is KtDeclaration) return DataFlowInfo.EMPTY

        val resultContextInfo = getContextInfoWhen(ESReturns(UNKNOWN_CONSTANT), callExpression, bindingTrace, moduleDescriptor)

        return resultContextInfo.toDataFlowInfo(languageVersionSettings)
    }

    fun getDataFlowInfoWhenEquals(
            leftExpression: KtExpression?,
            rightExpression: KtExpression?,
            bindingTrace: BindingTrace,
            moduleDescriptor: ModuleDescriptor
    ): ConditionalDataFlowInfo {
        if (leftExpression == null || rightExpression == null) return ConditionalDataFlowInfo.EMPTY

        val leftCallTree = getCallTree(leftExpression, bindingTrace, moduleDescriptor) ?: return ConditionalDataFlowInfo.EMPTY
        val rightCallTree = getCallTree(rightExpression, bindingTrace, moduleDescriptor) ?: return ConditionalDataFlowInfo.EMPTY

        val context = bindingTrace.bindingContext
        val callToEquals = CallTreeBuilder(context, moduleDescriptor)
                .createCallToEquals(leftCallTree, leftExpression.getType(context), rightCallTree, rightExpression.getType(context), false)

        val schema = buildSchema(callToEquals) ?: return ConditionalDataFlowInfo.EMPTY

        val equalsContextInfo = InfoCollector(ESReturns(true.lift())).collectFromSchema(schema)
        val notEqualsContextInfo = InfoCollector(ESReturns(false.lift())).collectFromSchema(schema)

        return ConditionalDataFlowInfo(
                equalsContextInfo.toDataFlowInfo(languageVersionSettings),
                notEqualsContextInfo.toDataFlowInfo(languageVersionSettings)
        )
    }

    fun recordDefiniteInvocations(resolvedCall: ResolvedCall<*>, bindingTrace: BindingTrace, moduleDescriptor: ModuleDescriptor) {
        if (!languageVersionSettings.supportsFeature(LanguageFeature.CalledInPlaceEffect)) return

        // Prevent launch of effect system machinery on pointless cases (constants/enums/constructors/etc.)
        val callExpression = resolvedCall.call.callElement as? KtCallExpression ?: return
        if (callExpression is KtDeclaration) return

        val resultingContextInfo = getContextInfoWhen(ESReturns(UNKNOWN_CONSTANT), callExpression, bindingTrace, moduleDescriptor)
        for (effect in resultingContextInfo.firedEffects) {
            val callsEffect = effect as? ESCalls ?: continue
            val id = callsEffect.callable.id as DataFlowValueID

            // Could be also IdentifierInfo.Variable when call passes non-anonymous lambda for callable parameter
            val lambdaExpr = (id.dfv.identifierInfo as? DataFlowValueFactory.ExpressionIdentifierInfo)?.expression ?: continue
            assert(lambdaExpr is KtLambdaExpression) { "Unexpected argument of Calls-effect: expected KtLambdaExpression, got $lambdaExpr" }

            bindingTrace.record(BindingContext.LAMBDA_INVOCATIONS, lambdaExpr as KtLambdaExpression, callsEffect.kind)
        }
    }

    fun extractDataFlowInfoFromCondition(
            condition: KtExpression?,
            value: Boolean,
            bindingTrace: BindingTrace,
            moduleDescriptor: ModuleDescriptor
    ): DataFlowInfo {
        if (!languageVersionSettings.supportsFeature(LanguageFeature.ContractEffects)) return DataFlowInfo.EMPTY
        if (condition == null) return DataFlowInfo.EMPTY

        return getContextInfoWhen(ESReturns(value.lift()), condition, bindingTrace, moduleDescriptor)
                .toDataFlowInfo(languageVersionSettings)
    }

    private fun getContextInfoWhen(
            observedEffect: ESEffect,
            expression: KtExpression,
            bindingTrace: BindingTrace,
            moduleDescriptor: ModuleDescriptor
    ): MutableContextInfo {
        val schema = getSchema(expression, bindingTrace, moduleDescriptor) ?: return MutableContextInfo.EMPTY

        val extractedContextInfo = InfoCollector(observedEffect).collectFromSchema(schema)

        return extractedContextInfo
    }

    private fun getCallTree(expression: KtExpression, bindingTrace: BindingTrace, moduleDescriptor: ModuleDescriptor): CTNode? {
        if (bindingTrace[BindingContext.EXPRESSION_CALL_TREE, expression] == null) {
            val callTree = buildCallTree(expression, bindingTrace.bindingContext, moduleDescriptor) ?: return null
            bindingTrace.record(BindingContext.EXPRESSION_CALL_TREE, expression, callTree)
        }

        return bindingTrace[BindingContext.EXPRESSION_CALL_TREE, expression]
    }

    private fun getSchema(expression: KtExpression, bindingTrace: BindingTrace, moduleDescriptor: ModuleDescriptor): EffectSchema? {
        if (bindingTrace[BindingContext.EXPRESSION_EFFECTS, expression] == null) {
            val callTree = getCallTree(expression, bindingTrace, moduleDescriptor) ?: return null
            val schema = buildSchema(callTree) ?: return null
            bindingTrace.record(BindingContext.EXPRESSION_EFFECTS, expression, schema)
        }

        return bindingTrace[BindingContext.EXPRESSION_EFFECTS, expression]
    }

    private fun buildSchema(callTree: CTNode): EffectSchema? {
        val schema = SchemaBuilder().let { callTree.accept(it) } ?: return null
        return Reducer().reduceSchema(schema)
    }

    private fun buildCallTree(expression: KtExpression, bindingContext: BindingContext, moduleDescriptor: ModuleDescriptor): CTNode? =
            CallTreeBuilder(bindingContext, moduleDescriptor).let { expression.accept(it, Unit) }
}