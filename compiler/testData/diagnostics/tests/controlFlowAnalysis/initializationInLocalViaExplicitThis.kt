// !LANGUAGE: +CalledInPlaceEffect
// See KT-17479

class Test {
    val str: String
    init {
        run {
            // No captured val initialization diagnostic, because we know that run has 'callsInPlace' effect
            this@Test.str = "A"
        }

        run {
            // Val reassignment because of the same reasons
            <!VAL_REASSIGNMENT!>this@Test.str<!> = "B"
        }

        str = "C"
    }
}