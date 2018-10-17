// !LANGUAGE: +AllowContractsForCustomFunctions +UseReturnsEffect
// !USE_EXPERIMENTAL: kotlin.contracts.ExperimentalContracts
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER

import kotlin.contracts.*

fun myIf(cond: Boolean): Any {
    contract {
        returns(true) implies cond
    }
    return cond
}

fun test(x: Any?) {
    if (myIf(x is String) is Boolean) {
        x.<!UNRESOLVED_REFERENCE!>length<!>
    }
}