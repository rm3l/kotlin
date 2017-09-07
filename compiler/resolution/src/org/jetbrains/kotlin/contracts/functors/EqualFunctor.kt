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

package org.jetbrains.kotlin.contracts.functors

import org.jetbrains.kotlin.contracts.effects.ESReturns
import org.jetbrains.kotlin.contracts.factories.NOT_NULL_CONSTANT
import org.jetbrains.kotlin.contracts.factories.UNKNOWN_CONSTANT
import org.jetbrains.kotlin.contracts.factories.createClause
import org.jetbrains.kotlin.contracts.factories.lift
import org.jetbrains.kotlin.contracts.impls.ESConstant
import org.jetbrains.kotlin.contracts.impls.ESEqual
import org.jetbrains.kotlin.contracts.impls.ESVariable
import org.jetbrains.kotlin.contracts.structure.ConstantID
import org.jetbrains.kotlin.contracts.structure.ESClause
import org.jetbrains.kotlin.contracts.structure.NOT_NULL_ID
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class EqualsToConstantFunctor(val isNegated: Boolean, val constant: ESConstant, val isBinaryTypeComparison: Boolean) : AbstractSequentialUnaryFunctor() {
    override fun combineClauses(list: List<ESClause>): List<ESClause> {
        // Corner-case when left is variable
        if (list.size == 1 && list.single().effect.safeAs<ESReturns>()?.value is ESVariable) {
            val variable = (list.single().effect as ESReturns).value as ESVariable
            return listOf(createClause(ESEqual(variable, constant, isNegated, isBinaryTypeComparison), ESReturns(true.lift())))
        }


        val result = mutableListOf<ESClause>()
        val (equal, notEqual) = list.partition { it.effect == ESReturns(constant) || (it.effect as ESReturns).value == UNKNOWN_CONSTANT }

        val whenArgReturnsSameConstant = foldConditionsWithOr(equal)
        if (whenArgReturnsSameConstant != null) {
            val returnValue = isNegated.not().lift() // true when not negated, false otherwise
            result.add(createClause(whenArgReturnsSameConstant, ESReturns(returnValue)))
        }

        if (isSafeToProduceFalse(list)) {
            val whenArgReturnsOtherConstant = foldConditionsWithOr(notEqual)
            if (whenArgReturnsOtherConstant != null) {
                val returnValue = isNegated.lift()       // false when not negated, true otherwise
                result.add(createClause(whenArgReturnsOtherConstant, ESReturns(returnValue)))
            }
        }

        return result
    }

    fun negated(): EqualsToConstantFunctor = EqualsToConstantFunctor(isNegated.not(), constant, isBinaryTypeComparison)

    // It is safe to produce Returns(false) only if all cases that return value != 'constant' are described
    private fun isSafeToProduceFalse(clauses: List<ESClause>): Boolean {
        // Currently, we restrict this only to two simple cases: when expression has at most binary type
        // and when it is nullability comparison
        if (isBinaryTypeComparison) return true

        if (constant.value != null && constant != NOT_NULL_CONSTANT) return false

        // Now we're comparing with null, but it's not nullability comparison yet:
        // we have to ensure that clauses defined in terms of Returns(NOT_NULL/NULL)
        return clauses.all {
            val value = (it.effect as ESReturns).value
            value.id == NOT_NULL_ID || value.id == ConstantID(null)
        }
    }
}