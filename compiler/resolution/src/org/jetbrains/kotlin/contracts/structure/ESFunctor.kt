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

package org.jetbrains.kotlin.contracts.structure

/**
 * An abstraction of effect-generating nature of some compitation.
 *
 * Its difference from [ContractDescriptor] is the same as the difference
 * between function' signature and its body. [ContractDescriptor] describes
 * some rules about how computation behaves, while [ESFunctor] describes, how exactly
 * those rules should be mapped into inner representation of EffectSystem,
 * i.e. which new effects this computation produces and how existing effects
 * are transformed.
 */

interface ESFunctor {
    fun apply(arguments: List<EffectSchema>): EffectSchema?
}

