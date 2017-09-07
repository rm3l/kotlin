// !LANGUAGE: +ContractEffects

import kotlin.internal.contracts.*

fun foo(boolean: Boolean) {
    contract {
        (returns() implies (boolean)) <!UNRESOLVED_REFERENCE!>implies<!> (!boolean)
    }
}