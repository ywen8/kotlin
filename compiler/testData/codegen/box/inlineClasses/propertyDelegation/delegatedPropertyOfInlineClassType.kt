// IGNORE_BACKEND: JVM_IR

import kotlin.reflect.KProperty

inline class ICInt(val i: Int)
inline class ICLong(val l: Long)

class Delegate<T>(var f: () -> T) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = f()
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        f = { value }
    }
}

object Demo {
    val i0 by Delegate { ICInt(1) }
    val l0 by Delegate { ICLong(2L) }

    val i1: ICInt by Delegate { ICInt(11) }
    val l1: ICLong by Delegate { ICLong(22) }

    var i2 by Delegate { ICInt(0) }
    var l2 by Delegate { ICLong(0) }
}

fun box(): String {
    if (Demo.i0.i != 1) return "Fail 1"
    if (Demo.l0.l != 2L) return "Fail 2"

    if (Demo.i1.i != 11) return "Fail 2 1"
    if (Demo.l1.l != 22L) return "Fail 2 2"

    Demo.i2 = ICInt(33)
    Demo.l2 = ICLong(33)

    if (Demo.i2.i != 33) return "Fail 3 1"
    if (Demo.l2.l != 33L) return "Fail 3 2"

    val localI by Delegate { ICInt(44) }
    val localL by Delegate { ICLong(44) }

    if (localI.i != 44) return "Fail 4 1"
    if (localL.l != 44L) return "Fail 4 2"

    return "OK"
}