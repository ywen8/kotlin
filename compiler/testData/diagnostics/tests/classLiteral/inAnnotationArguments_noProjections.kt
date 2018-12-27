// !LANGUAGE: -ProhibitProjectionsAndNullabilityInArrayClassLiteralsInAnnotationArguments

import kotlin.reflect.KClass

annotation class AnnArray(val kk: Array<KClass<*>>)

@AnnArray(arrayOf(
    <!ANNOTATION_ARGUMENT_KCLASS_LITERAL_WITH_PROJECTION!>Array<String?>::class<!>,
    <!ANNOTATION_ARGUMENT_KCLASS_LITERAL_WITH_PROJECTION!>Array<out Number>::class<!>,
    <!ANNOTATION_ARGUMENT_KCLASS_LITERAL_WITH_PROJECTION!>Array<in Array<String?>>::class<!>,
    <!ANNOTATION_ARGUMENT_KCLASS_LITERAL_WITH_PROJECTION!>Array<Array<Array<String>>?>::class<!>,
    <!ANNOTATION_ARGUMENT_KCLASS_LITERAL_WITH_PROJECTION!>Array<Array<Array<in String>>>::class<!>
))
fun test() {}
