package test

import kotlin.internal.contracts.*

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