package org.jetbrains.kotlin.diploma

import kotlin.math.sqrt


fun <T> foo(t: T): String {
    return "chars$t"
}

open class A(val a: Int) {
    private val q = 1
    protected val w = 2
    internal val e = 3

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
}

fun A.extension() {
    val a = (2 + 2) * 2
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
        if (5 < sqrt(5.0)) continue
    } while (false)

    return { a, _ -> a }
}

@Target(AnnotationTarget.CONSTRUCTOR)
annotation class Annot

typealias Point<T> = Pair<T, T>

fun fooo() {
    try {
        throw IllegalArgumentException()
    } catch (e: Exception) {
        when(123) {
            in listOf(1, 2, 3) -> {
                print(3)
                print(4)
            }
            is Int -> print(20)
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
        print("\n \\t")
        null?.toString()
        val t = null ?: 123
        val s = "$i ${t}"
    }

    val map = mapOf<Int, Int>()
    val q = map[102]!!
}

object TTT {
    val x = TTT !== TTT
    val y = TTT === TTT
    val z = """qwergthjkl,jmhngfvdc%$$$$$$$"""

    val c = 'c'
    val f = 1.2
    val i = 3
    val l = 0L

}

enum class E { Q, W, E }
