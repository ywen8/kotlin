// IGNORE_BACKEND: JS_IR
// EXPECTED_REACHABLE_NODES: 1126
package foo

class MyException(m: String? = null): Exception(m)
class MyException2(m: String? = null): Throwable(m)

// KT-22053
class MyThrowable(message: String?) :  Throwable("through primary: " + message) {
    public var initOrder = ""

    constructor() : this(message = "secondary") {
        initOrder += "2"
    }
    constructor(i: Int) : this() {
        initOrder += "3"
    }

    init { initOrder += "1" }
}


// TODO: add direct inheritors of Throwable:
// - with cause only, in the primary constructor

fun check(e: Throwable, expectedString: String) {
    try {
        throw e
    }
    catch (e: Throwable) {
        assertEquals(expectedString, e.toString())
    }
}

fun box(): String {
    check(Throwable(), "Throwable: null")
    check(Throwable("ccc"), "Throwable: ccc")
    check(Throwable(Throwable("ddd")), "Throwable: Throwable: ddd")
    check(Exception(), "Exception: null")
    check(Exception("bbb"), "Exception: bbb")
    check(Exception(Exception("ccc")), "Exception: Exception: ccc")
    check(AssertionError(), "AssertionError: null")
    check(AssertionError(null), "AssertionError: null")
    check(AssertionError("bbb"), "AssertionError: bbb")
    check(AssertionError(Exception("ccc")), "AssertionError: Exception: ccc")
    check(MyException(), "MyException: null")
    check(MyException("aaa"), "MyException: aaa")
    check(MyException2(), "MyException2: null")
    check(MyException2("aaa"), "MyException2: aaa")

    // KT-22053
    val mt1 = MyThrowable("primary")
    assertEquals(mt1.toString(), "MyThrowable: through primary: primary")
    assertEquals(mt1.initOrder, "1")

    val mt2 = MyThrowable()
    assertEquals(mt2.toString(), "MyThrowable: through primary: secondary")
    assertEquals(mt2.initOrder, "12")

    val mt3 = MyThrowable(1)
    assertEquals(mt3.toString(), "MyThrowable: through primary: secondary")
    assertEquals(mt3.initOrder, "123")

    return "OK"
}
