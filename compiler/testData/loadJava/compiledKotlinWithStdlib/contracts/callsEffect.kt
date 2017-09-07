package test

import kotlin.effects.dsl.*

fun <X, Y, Z, R> callsEffectWithKind(block: (X, Y, Z) -> R) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
}

inline fun callsEffectImplicitUnknown(x: Int, y: Int, block: () -> Unit) {
    contract {
        callsInPlace(block)
    }
}

inline fun callsEffectExplicitUnknown(x: Int, block: () -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.UNKNOWN)
    }
}