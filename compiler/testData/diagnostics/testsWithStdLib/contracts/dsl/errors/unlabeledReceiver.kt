// !LANGUAGE: +ContractEffects

import kotlin.internal.contracts.*

fun Any?.foo(): Boolean {
    contract {
        returns(true) implies (<!SENSELESS_COMPARISON!><!ERROR_IN_CONTRACT_DESCRIPTION(only references to parameters are allowed. Did you missed label on <this>?)!>this<!> != null<!>)
    }
    return this != null
}