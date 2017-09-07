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

package org.jetbrains.kotlin.contracts.parsing

import org.jetbrains.kotlin.descriptors.contracts.EffectDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingTrace

internal interface PSIEffectParser {
    fun tryParseEffect(expression: KtExpression): EffectDeclaration?
}

sealed class EffectParsingResult(val isSuccessful: Boolean) {
    class Success(val effectDeclaration: EffectDeclaration) : EffectParsingResult(true)

    // Parser doesn't know about passed effect
    class Unrecognized : EffectParsingResult(false)

    // Parser knows about passed effect, but there were some errors in description
    // Difference only matters for error reporting (e.g. we should report
    // "Unrecognized effect" only if all parsers responded with "Unrecognized")
    class ParsedWithErrors : EffectParsingResult(false)

    companion object {
        val UNRECOGNIZED = Unrecognized()
        val PARSED_WITH_ERRORS = ParsedWithErrors()
    }
}

internal abstract class AbstractPSIEffectParser(val trace: BindingTrace, val contractParserDispatcher: PSIContractParserDispatcher) : PSIEffectParser