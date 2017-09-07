// !LANGUAGE: +ContractEffects

package test

import kotlin.effects.dsl.*

fun foo(x: Any?): Boolean {
    contract {
        returns() implies (x is String)
    }
    return bar(x)
}

fun bar(x: Any?): Boolean {
    contract {
        returns() implies (x is Int)
    }
    return foo(x)
}