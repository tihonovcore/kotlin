// IGNORE_BACKEND: JS_IR
// IGNORE_BACKEND: JS_IR_ES6
// TODO: muted automatically, investigate should it be ran for JS or not
// IGNORE_BACKEND: JS, NATIVE

package demo_long

fun Long?.inv() : Long = this!!.inv()

fun box() : String {
    val x : Long? = 10
    System.out?.println(x.inv())
    return if(x.inv() == -11.toLong()) "OK" else "fail"
}
