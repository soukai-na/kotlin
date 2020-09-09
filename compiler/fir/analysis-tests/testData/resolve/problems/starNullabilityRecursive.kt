interface A<T : A<T?>?> {
    fun foo(): T?
}
fun testA(a: A<*>) {
    val res = a.foo()
}
