class A(val x: Int) {
    fun foo(): Int {
        print("hello")
        return x
    }
}

val a = A(50)
val ax = a.foo()
