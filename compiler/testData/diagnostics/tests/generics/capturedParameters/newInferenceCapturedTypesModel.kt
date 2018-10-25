// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_VARIABLE

object CapturedTypeWithTypeVariable {
    fun test(i: Inv<out Any?>) {
        foo(i.superclass())
    }

    fun <T> foo(x: T) {}

    class Inv<T>

    fun <T> Inv<T>.superclass(): Inv<in T> = Inv()
}

object InnerCapturedTypeWithTypeVariable {
    fun test(i: Inv<Inv<out Any?>>) {
        i.superclass()
    }

    fun <T> foo(x: T) {}

    class Inv<T>

    fun <T> Inv<Inv<T>>.superclass(): Inv<Inv<in T>> = Inv()
}

object InnerCapturedTypes {
    fun <T> foo(array: Array<Array<T>>): Array<Array<T>> = array

    fun test(array: Array<Array<out Int>>) {
        foo(array)
        val f: Array<out Array<out Int>> = foo(array)
    }
}

object InnerAndTopLevelCapturedTypes {
    class A<T>
    class B<T>

    fun <E> foo(b: B<in A<E>>) {}
    fun <E> baz(b: B<out A<E>>) {}

    // See KT-13950
    fun bar(b: B<in A<out Number>>, bOut: B<out A<out Number>>, bOut2: B<out A<Number>>) {
        foo(b)
        foo<Number>(b)

        baz(bOut)
        baz<Number>(bOut)

        baz(bOut2)
        baz<Number>(bOut2)
    }
}

object CapturedFromSubtyping {
    fun <V, R, M : MutableMap<in R, out V>> mapKeysTo(destination: M): Inv3<R, V, M> {
        val foo = associateByTo(destination)

        return <!TYPE_MISMATCH, TYPE_MISMATCH!>foo<!>
    }

    fun < Y, Z, T : MutableMap<in Y, out Z>> associateByTo(destination: T): Inv3<Y, Z, T> = TODO()

    interface Inv3<A, B, C>
}