/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.samples

import java.lang.Integer

fun function() {
    val alpha = "4".toInt()
    val (beta, gamma) = Pair(3, 4)
}

val xxx = (2 + 3 * 4).toString().toInt()
val yyy: Int = Integer.valueOf("234")
val zzz = "START $xxx MID ${xxx + yyy} END"
val asd = """dsdsfds ${'$'}"""
val rww = "sdfsdf\n\n \t dfgdfg"

fun foo(a: Int): String {
    if (a > 2) {
        print(100)
    }

    val x = "stroka"
    return x
}

class A(val s: Int, val ss: Int) {
    init {
        print(s + ss)
    }

    fun bar(t: Int) {
        when (t) {
            s -> print("S")
            ss -> print("SS")
            else -> throw IllegalStateException()
        }
    }

    fun jjj(): Pair<Int, Int> = Pair(3, 4)
}

val a_object = A(3, 4)

object Sample {
    fun sample() {
        val (x, y) = a_object.jjj()
    }
}

fun load() {
    val (a: Int, b: Int) = Pair(3, 4)
    fun Int.foo2() = this

    print(a + b.foo2())
    print(b)


    do {
        val t = A(a, b).jjj()
        val tl = t.first
        val tr = t.second

        fun eleven(q: Int = tl, w: Int = tr) = q / w

        eleven()
        eleven(4)
        eleven(4, 6)
        eleven(w = 6)
    } while (true && false)

    fun <T> render(t: T) = "result: $t"

    val p_int = render(4) //TODO: pass explicitly type parameters in dataset
    val p_str = render("")
    val p_obj = render(a_object)
    val p_lst = render(mutableListOf(4, 5.4, 0x4))
}
