package test

import kotlin.effects.dsl.*

fun Any?.isNotNull(): Boolean {
    contract {
        returns(true) implies (this@isNotNull != null)
    }
    return this != null
}

fun test(x: String?) {
    if (x.isNotNull()) {
        x.length
    }
}