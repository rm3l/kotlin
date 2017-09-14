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

import org.jetbrains.kotlin.contracts.functors.*
import org.jetbrains.kotlin.contracts.impls.ESConstant
import org.jetbrains.kotlin.contracts.impls.lift
import org.jetbrains.kotlin.contracts.interpretation.ContractInterpretationDispatcher
import org.jetbrains.kotlin.contracts.parsing.isEqualsDescriptor
import org.jetbrains.kotlin.contracts.structure.EMPTY_SCHEMA
import org.jetbrains.kotlin.contracts.structure.ESFunctor
import org.jetbrains.kotlin.contracts.structure.calltree.*
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.ValueDescriptor
import org.jetbrains.kotlin.descriptors.contracts.ContractProviderKey
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ExpressionValueArgument
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValue
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactory
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.constants.CompileTimeConstant
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * Visits Psi-tree and builds Call Tree
 */
class CallTreeBuilder(
        private val bindingContext: BindingContext,
        private val moduleDescriptor: ModuleDescriptor
) : KtVisitor<Computation, Unit>() {
    private val contractInterpretationDispatcher = ContractInterpretationDispatcher()

    override fun visitKtElement(element: KtElement, data: Unit): Computation {
        val resolvedCall = element.getResolvedCall(bindingContext) ?: return UNKNOWN_COMPUTATION
        if (resolvedCall.explicitReceiverKind == ExplicitReceiverKind.BOTH_RECEIVERS) return UNKNOWN_COMPUTATION

        val descriptor = resolvedCall.resultingDescriptor
        val arguments = mutableListOf<Computation>()
        if ((resolvedCall.extensionReceiver as? ExpressionReceiver)?.expression?.getResolvedCall(bindingContext) == resolvedCall)
            return UNKNOWN_COMPUTATION
        arguments.addIfNotNull(resolvedCall.extensionReceiver?.toComputation())

        if ((resolvedCall.dispatchReceiver as? ExpressionReceiver)?.expression?.getResolvedCall(bindingContext) == resolvedCall)
            return UNKNOWN_COMPUTATION
        arguments.addIfNotNull(resolvedCall.dispatchReceiver?.toComputation())

        resolvedCall.valueArgumentsByIndex?.mapTo(arguments) {
            val valueArgument = (it as? ExpressionValueArgument)?.valueArgument ?: return UNKNOWN_COMPUTATION
            when (valueArgument) {
                is KtLambdaArgument -> ESLambda(valueArgument.getLambdaExpression())
                else -> valueArgument.getArgumentExpression()?.accept(this, data) ?: return UNKNOWN_COMPUTATION
            }
        } ?: return UNKNOWN_COMPUTATION

        return when {
            descriptor.isEqualsDescriptor() -> BuiltInOperatorCall(BuiltInOperator.EQUALS, EqualsFunctor(false).apply(arguments))
            descriptor is ValueDescriptor -> ESDataFlowValue(descriptor, (element as KtExpression).createDataFlowValue() ?: return UNKNOWN_COMPUTATION)
            descriptor is FunctionDescriptor -> FunctionCall(descriptor, descriptor.getFunctor()?.apply(arguments) ?: EMPTY_SCHEMA)
            else -> UNKNOWN_COMPUTATION
        }
    }

    override fun visitLambdaExpression(expression: KtLambdaExpression, data: Unit?): Computation {
        return UNKNOWN_COMPUTATION
    }

    override fun visitParenthesizedExpression(expression: KtParenthesizedExpression, data: Unit): Computation =
            KtPsiUtil.deparenthesize(expression)?.accept(this, data) ?: UNKNOWN_COMPUTATION

    override fun visitConstantExpression(expression: KtConstantExpression, data: Unit): Computation {
        val bindingContext = bindingContext

        val type: KotlinType = bindingContext.getType(expression) ?: return UNKNOWN_COMPUTATION

        val compileTimeConstant: CompileTimeConstant<*>
                = bindingContext.get(BindingContext.COMPILE_TIME_VALUE, expression) ?: return UNKNOWN_COMPUTATION
        val value: Any? = compileTimeConstant.getValue(type)

        return when (value) {
            is Boolean -> value.lift()
            null -> ESConstant.NULL
            else -> UNKNOWN_COMPUTATION
        }
    }

    override fun visitIsExpression(expression: KtIsExpression, data: Unit): Computation {
        val rightType: KotlinType = bindingContext.get(BindingContext.TYPE, expression.typeReference) ?: return UNKNOWN_COMPUTATION
        val arg = expression.leftHandSide.accept(this, data)
        return BuiltInOperatorCall(BuiltInOperator.IS, IsFunctor(rightType, expression.isNegated).apply(listOf(arg)))
    }

    override fun visitBinaryExpression(expression: KtBinaryExpression, data: Unit): Computation {
        val left = expression.left?.accept(this, data) ?: return UNKNOWN_COMPUTATION
        val right = expression.right?.accept(this, data) ?: return UNKNOWN_COMPUTATION

        val args = listOf(left, right)

        return when (expression.operationToken) {
            KtTokens.EXCLEQ -> BuiltInOperatorCall(BuiltInOperator.EQUALS, EqualsFunctor(true).apply(args))
            KtTokens.EQEQ -> BuiltInOperatorCall(BuiltInOperator.EQUALS, EqualsFunctor(false).apply(args))
            KtTokens.ANDAND -> BuiltInOperatorCall(BuiltInOperator.AND, AndFunctor().apply(args))
            KtTokens.OROR -> BuiltInOperatorCall(BuiltInOperator.OR, OrFunctor().apply(args))
            else -> UNKNOWN_COMPUTATION
        }
    }

    override fun visitUnaryExpression(expression: KtUnaryExpression, data: Unit): Computation {
        val arg = expression.baseExpression?.accept(this, data) ?: return UNKNOWN_COMPUTATION
        return when (expression.operationToken) {
            KtTokens.EXCL -> BuiltInOperatorCall(BuiltInOperator.NOT, NotFunctor().apply(arg))
            else -> UNKNOWN_COMPUTATION
        }
    }

    private fun ReceiverValue.toComputation(): Computation = when (this) {
        is ExpressionReceiver -> this.expression.accept(this@CallTreeBuilder, Unit)
        else -> UNKNOWN_COMPUTATION
    }

    private fun KtExpression.createDataFlowValue(): DataFlowValue? {
        return DataFlowValueFactory.createDataFlowValue(
                expression = this,
                type = bindingContext.getType(this) ?: return null,
                bindingContext = bindingContext,
                containingDeclarationOrModule = moduleDescriptor
        )
    }

    private fun FunctionDescriptor.getFunctor(): ESFunctor? {
        // TODO: Cache functors into descriptors UserData map
        val contractProvider = getUserData(ContractProviderKey) ?: return null
        val contractDescriptor = contractProvider.getContractDescriptor() ?: return null
        return contractInterpretationDispatcher.convertContractDescriptorToFunctor(contractDescriptor)
    }
}