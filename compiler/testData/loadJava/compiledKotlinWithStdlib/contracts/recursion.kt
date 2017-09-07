// !LANGUAGE: +ContractEffects

package test

import kotlin.effects.dsl.*

fun foo(n: Int, x: Any?): Boolean {
    contract {
        returns(true) implies (x is String)
    }
    return if (n == 0) x is String else foo(n - 1, x)
}