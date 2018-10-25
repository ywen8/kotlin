inline class IC(val storage: IntArray) {
    fun get(index: Int): Int = storage[index]
}

fun take(u: IC) {
    foo(u::get)
}

inline fun foo(init: (Int) -> Int) {
    init(1)
}

fun box(): String {
    val u = IC(intArrayOf(1, 2, 3))
    take(u)
    return "OK"
}