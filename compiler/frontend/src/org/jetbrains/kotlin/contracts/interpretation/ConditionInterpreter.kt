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

package org.jetbrains.kotlin.contracts.interpretation

import org.jetbrains.kotlin.descriptors.contracts.ContractDescriptorVisitor
import org.jetbrains.kotlin.descriptors.contracts.expressions.*
import org.jetbrains.kotlin.contracts.factories.lift
import org.jetbrains.kotlin.contracts.functors.AndFunctor
import org.jetbrains.kotlin.contracts.functors.IsFunctor
import org.jetbrains.kotlin.contracts.functors.NotFunctor
import org.jetbrains.kotlin.contracts.functors.OrFunctor
import org.jetbrains.kotlin.contracts.impls.*
import org.jetbrains.kotlin.contracts.structure.ESBooleanExpression

internal class ConditionInterpreter(private val dispatcher: ContractInterpretationDispatcher) : ContractDescriptorVisitor<ESBooleanExpression?, Unit> {
    override fun visitLogicalOr(logicalOr: LogicalOr, data: Unit): ESBooleanExpression? {
        val left = logicalOr.left.accept(this, data) ?: return null
        val right = logicalOr.right.accept(this, data) ?: return null
        return ESOr(left, right, OrFunctor())
    }

    override fun visitLogicalAnd(logicalAnd: LogicalAnd, data: Unit): ESBooleanExpression? {
        val left = logicalAnd.left.accept(this, data) ?: return null
        val right = logicalAnd.right.accept(this, data) ?: return null
        return ESAnd(left, right, AndFunctor())
    }

    override fun visitLogicalNot(logicalNot: LogicalNot, data: Unit): ESBooleanExpression? {
        val arg = logicalNot.arg.accept(this, data) ?: return null
        return ESNot(arg, NotFunctor())
    }

    override fun visitIsInstancePredicate(isInstancePredicate: IsInstancePredicate, data: Unit): ESBooleanExpression? {
        val esVariable = dispatcher.interpretVariable(isInstancePredicate.arg) ?: return null
        return ESIs(esVariable, IsFunctor(isInstancePredicate.type, isInstancePredicate.isNegated))
    }

    override fun visitIsNullPredicate(isNullPredicate: IsNullPredicate, data: Unit): ESBooleanExpression? {
        val variable = dispatcher.interpretVariable(isNullPredicate.arg) ?: return null
        return ESEqual(variable, null.lift(), isNullPredicate.isNegated, true)
    }

    override fun visitBooleanConstantDescriptor(booleanConstantDescriptor: BooleanConstantDescriptor, data: Unit): ESBooleanExpression? =
            dispatcher.interpretConstant(booleanConstantDescriptor) as ESBooleanConstant

    override fun visitBooleanVariableReference(booleanVariableReference: BooleanVariableReference, data: Unit): ESBooleanExpression? =
            dispatcher.interpretVariable(booleanVariableReference) as ESBooleanVariable
}