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
import org.jetbrains.kotlin.contracts.factories.createClause
import org.jetbrains.kotlin.contracts.factories.lift
import org.jetbrains.kotlin.contracts.impls.ESIs
import org.jetbrains.kotlin.contracts.impls.and
import org.jetbrains.kotlin.contracts.structure.ESClause
import org.jetbrains.kotlin.types.KotlinType

class IsFunctor(val type: KotlinType, val isNegated: Boolean) : AbstractSequentialUnaryFunctor() {
    override fun combineClauses(list: List<ESClause>): List<ESClause> = list.flatMap {
        // Cast to ESReturns should succeed as per AbstractSequentialUnaryFunctor contract
        val outcome = it.effect as ESReturns
        val premise = it.condition

        val trueResult = createClause(premise.and(ESIs(outcome.value, this)), ESReturns(true.lift()))
        val falseResult = createClause(premise.and(ESIs(outcome.value, negated())), ESReturns(false.lift()))
        return listOf(trueResult, falseResult)
    }

    fun negated(): IsFunctor = IsFunctor(type, isNegated.not())
}