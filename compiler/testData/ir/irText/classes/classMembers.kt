// FIR_IDENTICAL
// WITH_RUNTIME

object Obj {
    val t: Int
    val o: Obj = this
}

//NOTE: all enums enherits kotlin.Enum and hasn't differences
enum class EEENum { E1, E2, E3, E4 }

//NOTE: Inner and nested classes are considered as top-level classes
//
//Expected solution is
// 1. move constructors of nested class to outer's companion
// 2. move constructors of inner class to outer's body
class Outer(val x: Int) {
    fun foo(a: A) = EEENum.E1

    //(Char) -> Inner
    inner class Inner(val y: Char) {
        val t = (x + y.toInt()).toString()

        fun bar(a: A) = foo(a)
    }

    class NotInner()
}

val x = Outer.NotInner()

//NOTE: nullable types have not supported: its the same as not-nullable
fun nullable(a: Int?, b: Char?): Any? = null

fun topLevel(a: Int, b: Long): Char {
    fun inner(c: A, d: B): Unit {

    }

    val lambda = { p: B -> p.a }

    return 'c'
}

fun function(int: Int, body: (Int) -> Unit) {

}

class A(val i: Int) {
    lateinit var m: MutableMap<Int, Long>
    lateinit var l: List<Int>
    lateinit var a: Array<Char>
    lateinit var r: IntRange
}

class B(val a: A) {
    fun foo(): String = ""
}

class C(val d: Double): A(d.toInt())



open class Ov(open val a: Int)
class Ch(override val a: Int): Ov(a + 3)



interface Q {
    val x: Int
}
open class W : Q {
    override val x: Int = 4
}
class E : Q, W()



class XXX(val long: Long, val string: String)

class YYY(x: Int, val y: Int, var z: Int = 1) {
    constructor() : this(0, 0, 0) {}

    val ppp = XXX(3, "4, 5")

    val property: Int = 0

    val propertyWithGet: Int
        get() = 42

    var propertyWithGetAndSet: Int
        get() = z
        set(value) {
            z = value
        }

    fun function() {
        println("1")
    }

    fun Int.memberExtensionFunction() {
        println("2")
    }

    class NestedClass {
        fun function() {
            println("3")
        }

        fun Int.memberExtensionFunction() {
            println("4")
        }
    }

    interface NestedInterface {
        fun foo()
        fun bar() = foo()
    }

    companion object
}