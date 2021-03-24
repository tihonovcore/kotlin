class A(val x: Int) {
    fun foo(): Int {
        print("hello")
        return x
    }

    fun bar() {
        while(x != 4) {
            if (x == 3) continue
        }
    }
}

val a = A(50)
val ax = a.foo()
