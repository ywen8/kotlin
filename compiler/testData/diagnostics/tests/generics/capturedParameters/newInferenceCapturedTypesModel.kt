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
        <!NEW_INFERENCE_ERROR, NEW_INFERENCE_ERROR!>i<!>.superclass()
    }

    fun <T> foo(x: T) {}

    class Inv<T>

    fun <T> Inv<Inv<T>>.superclass(): Inv<Inv<in T>> = Inv()
}

object InnerCapturedTypes {
    fun <T> foo(array: Array<Array<T>>): Array<Array<T>> = array

    fun test(array: Array<Array<out Int>>) {
        foo(<!NEW_INFERENCE_ERROR, NEW_INFERENCE_ERROR!>array<!>)
        val f: Array<out Array<out Int>> = foo(<!NEW_INFERENCE_ERROR, NEW_INFERENCE_ERROR!>array<!>)
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

        baz(<!NEW_INFERENCE_ERROR, NEW_INFERENCE_ERROR!>bOut<!>)
        baz<Number>(<!NEW_INFERENCE_ERROR, NEW_INFERENCE_ERROR!>bOut<!>)

        baz(bOut2)
        baz<Number>(bOut2)
    }
}

object CapturedFromSubtyping {
    fun <V, R, M : MutableMap<in R, out V>> mapKeysTo(destination: M): Inv3<R, V, M> {
        val foo = <!NEW_INFERENCE_ERROR, NEW_INFERENCE_ERROR!>associateByTo(<!NEW_INFERENCE_ERROR, NEW_INFERENCE_ERROR!>destination<!>)<!>

        return <!TYPE_MISMATCH, TYPE_MISMATCH!>foo<!>
    }

    fun < Y, Z, T : MutableMap<in Y, out Z>> associateByTo(destination: T): Inv3<Y, Z, T> = TODO()

    interface Inv3<A, B, C>
}