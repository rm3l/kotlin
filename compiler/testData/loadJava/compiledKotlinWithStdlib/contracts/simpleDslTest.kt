package test

import kotlin.effects.dsl.*

fun box(): String {
    if (test("Hello") != null) return "OK" else return "fail"
}

fun test(x: Any?): Int? {
    if (isString(x)) {
        return x.length
    } else {
        return null
    }
}

fun isString(x: Any?): Boolean {
    contract {
        returns(true) implies (x is String)
    }
    return x is String
}


