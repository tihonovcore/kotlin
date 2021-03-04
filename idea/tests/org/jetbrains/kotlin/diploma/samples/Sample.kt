/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.diploma.samples

import java.lang.Integer

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

val a = A(3, 4)

object Sample {
    fun sample() {
        val (x, y) = a.jjj()
    }
}

fun load() {
    val (a: Int, b: Int) = Pair(3, 4)
    print(a)
    print(b)
}