package test

import kotlin.effects.dsl.*

class A

fun simpleIsInstace(x: Any?) {
    contract {
        returns(true) implies (x is A)
    }
}

fun Any?.receiverIsInstance() {
    contract {
        returns(true) implies (this@receiverIsInstance is A)
    }
}
