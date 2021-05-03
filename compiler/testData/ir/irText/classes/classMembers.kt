// FIR_IDENTICAL
// WITH_RUNTIME

class A(val i: Int) {
    lateinit var m: MutableMap<Int, Long>
    lateinit var l: List<Int>
    lateinit var a: Array<Char>
//    lateinit var r: IntRange
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