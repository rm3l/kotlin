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

package org.jetbrains.kotlin.contracts.impls

import org.jetbrains.kotlin.contracts.effects.ESCalls
import org.jetbrains.kotlin.contracts.effects.ESReturns
import org.jetbrains.kotlin.contracts.effects.ESThrows
import org.jetbrains.kotlin.contracts.factories.boundSchemaFromClauses
import org.jetbrains.kotlin.contracts.factories.createClause
import org.jetbrains.kotlin.contracts.factories.lift
import org.jetbrains.kotlin.contracts.structure.ESClause
import org.jetbrains.kotlin.contracts.structure.EffectSchema
import org.jetbrains.kotlin.contracts.visitors.Substitutor

class EffectSchemaImpl(override val clauses: List<ESClause>, val parameters: List<ESVariable>) : EffectSchema {
    override fun apply(arguments: List<EffectSchema>): EffectSchema? {
        assert(arguments.size == parameters.size) {
            "Arguments and parameters count mismatch: arguments count is ${arguments.size} but parameters count is ${parameters.size}"
        }
        // Effect Schema as functor can contain only pretty trivial operators (see ESOperator), which all work only
        // with sequential effects. All other effects transparently lift through application.

        // Here we make list of clauses that end with non-sequential effects. They will be added to result as-is
        val irrelevantClauses = arguments.flatMap { schema ->
            schema.clauses.filter { !it.effect.isSequential() }
        }

        // Here we transform arguments so that they contain only relevant clauses (i.e. those that end with sequential effect)
        // Those clauses should be combined properly using schema's structure
        val filteredArgs = arguments.map { schema ->
            boundSchemaFromClauses(schema.clauses.filter { it.effect.isSequential() })
        }
        val substs = parameters.zip(filteredArgs).toMap()
        val substitutor = Substitutor(substs)

        val combinedClauses = mutableListOf<ESClause>()
        for (clause in clauses) {
            if (clause.effect is ESCalls) {
                val callableProvider = substs[clause.effect.callable] ?: continue
                combinedClauses.addAll(callableProvider.clauses.map { callableProviderClause ->
                    val outcome = callableProviderClause.effect as? ESReturns ?: return@map callableProviderClause

                    // If cast fails, it means that something is wrong with types
                    val variable = outcome.value as? ESVariable ?: return null

                    val newEffect = ESCalls(variable, clause.effect.kind)
                    return@map callableProviderClause.replaceEffect(newEffect)
                })

                continue
            }


            // Substitute all args in condition
            val substitutedPremise = clause.condition.accept(substitutor) ?: continue

            for (substitutedClause in substitutedPremise.clauses) {
                val effect = substitutedClause.effect
                when {
                    effect is ESThrows -> combinedClauses += substitutedClause

                    effect == ESReturns(true.lift()) -> combinedClauses += createClause(substitutedClause.condition, clause.effect)

                    // TODO: ugly cludge
                    effect is ESReturns && effect.value is ESBooleanVariable && substitutedClause.condition == true.lift() ->
                            combinedClauses += createClause(effect.value, clause.effect)
                }
            }
        }

        return boundSchemaFromClauses(irrelevantClauses + combinedClauses)
    }
}