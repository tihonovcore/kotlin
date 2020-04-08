open class A {
    fun test(x: String): Int {}
    fun foo(x: String) {}

    var x = 10
}

class B: A()

val y: A = A()
val y1: B = B()

val z = y