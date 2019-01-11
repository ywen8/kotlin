// SKIP_TXT
// !DIAGNOSTICS: -UNUSED_PARAMETER
fun foo(x: String, y: String, z: String) { }

fun test() {
    val string1 = ""
    val string2 = ""
    val string3 = 1

    foo(string1, string2<!SYNTAX!><!> <!TYPE_MISMATCH!>string3<!>)
}
