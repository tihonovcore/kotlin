@file:Suppress("WARNINGS")

package org.jetbrains.kotlin.diploma

import kotlin.math.sqrt
import java.lang.System.`in` as reader

fun <T> foo(t: T): String {
    return "chars$t"
}

public open class A(val a: Int) {
    private val q = 1
    protected val w = 2
    internal val e = this.q + this.w

    var ggg: Int = 1
        get() = 444
        set(value) {
            field = value * 2
        }

    @Annot
    constructor(b: Int, c: Int) : this(b + c)

    init {
        println("hello" as CharSequence)
    }

    companion object {
        const val aaa = 128
    }
}

class B : A(256) {
    val t: Int by lazy {
        w * 2
    }
    val p = super.a
}

fun A.extension() {
    val a: Int = (2 + 2) * 2
    val c = -5
    print(this::class.java)

    val b = object { val x = 2 }
    val kkk = ::lambda
}

fun lambda(body: (Int) -> Long): (A, A) -> A {
    for (i in 0 until 3) {
        body(440)
    }

    mark@while (true) {
        break@mark
    }

    do {
        if (5 < sqrt(x = 5.0)) continue
    } while (false)

    return { a, _ -> a }
}

@Target(AnnotationTarget.CONSTRUCTOR)
annotation class Annot

@Target(AnnotationTarget.FUNCTION)
annotation class AnnotF(val x: IntArray)


typealias Point<T> = Pair<T, T>

@AnnotF([4])
fun fooo(qeer: Int = 400) {
    val list = mapOf<Int, Int>()

    try {
        throw IllegalArgumentException()
    } catch (e: Exception) {
        when (123) {
            124 -> print("nonoon")
            in listOf(1, 2, 3) -> {
                print(3)
                print(4)
            }
            is Int -> print(20)
            in 3..4 -> print("asdasd")
            else -> throw Exception("dffffff")
        }
    } finally {
        when {
            1 == 2 -> print(2)
            else -> print(3)
        }
    }

    val r = if (2 > 0) {
        "t"
    } else {
        "e"
    }
}

fun bar() {
    val (x, y) = Pair(2, 4)

    val r = 4..6
    for (i in r) {
        print("\n \\t \$")
        null?.toString()
        val t = null ?: 123
        val q: Any? = null
        val s = "$i ${t}"
    }

    val map = mapOf<Int, Int>()
    val q = map[102]!!
}

@get:JvmName("mapIntByteHex")
val Map<Int, Byte>.hex: String get() = "K"

object TTT {
    val x = TTT !== TTT
    val y = TTT === TTT
    val z = """qwergthjkl,jmhngfvdc%$$$$$$$"""

    val c = 'c'
    val f = 1.2
    val i = 3
    val l = 0L

    val tttqqq = if (f is Number) 3 else 4
}

enum class E { Q, W, E }
enum class EE(
    name: String,
    ordinal: Int
) {
    FOO("foo", 4),
    BAR("bar", 5);

    val testName = name
    val testOrdinal = ordinal
}

enum class EEE(val i: Int) { ENTRY(1) }

interface QQQ_I {
    fun fromInterface(): String
}
open class QQQ_C

class Derived(val x: Int, var y: Long, z: Char) : QQQ_I, QQQ_C() {
    override fun fromInterface(): String {
        return "retuernre"
    }

    init {
        val tmp = ArrayList<() -> Int>()
        val callLambda = lambda { int -> int.toLong() }

        y++
        ++y

        "sdasd".forEach { if (it == 'd') return@forEach }

        val dd = Derived::class
    }
}

val callFoo = foo<Char>('c')


interface Deleg_A<T> {
    fun foo(): T
}

class Deleg_B : Deleg_A<String> {
    override fun foo() = "OK"
}

class Deleg_C(deleg_a: Deleg_A<String>) : Deleg_A<String> by deleg_a

fun Deleg_box(): String {
    val a: Deleg_A<String> = Deleg_C(Deleg_B())
    return a.foo()
}

fun <T> bar_constraint(x: T): String where T : QQQ_C, T : QQQ_I {
    val t = x
    return ""
}

@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class Annotation

fun bbbbbox(): String {
    var v = 0
    @Annotation v += 1 + 2
    if (v != 3) return "fail1"

    @Annotation v = 4
    if (v != 4) return "fail2"

    return "OK"
}

class Something(val now: String)

fun box(): String {
    val a: Something.() -> String = {
        class MyEvent(val result: String = now)

        MyEvent().result
    }
    return Something("OK").a()
}

annotation class Ann
annotation class AnnRepeat

@setparam:[Ann AnnRepeat]
private var x4 = ""
