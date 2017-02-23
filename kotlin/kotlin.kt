
//	kotlinc kotlin.kt -include-runtime -d kotlin.jar && java -jar kotlin.jar

package Main

data class Employee(val name: String, val age: Int)

open class Company(val name: String, val employees: List<Employee>) {
	companion object : LoadableStatic<Company> {
		override fun load(iterator: Iterator<String>): Company {
			return Company(
				Main.load(iterator, StringLoadableStatic),
				Main.load(iterator, ListLoadableStatic(EmployeeLoadableStatic))
				)
		}
	}

	override fun toString(): String {
		return "(name=$name, employees=$employees)"
	}
}

interface LoadableStatic<T> {
	fun load(iterator: Iterator<String>): T
}

fun <T> load(string: String, static: LoadableStatic<T>): T {
	val tokens = string.split("/")
	val iterator = tokens.iterator()
	return load(iterator, static)
}

fun <T> load(iterator: Iterator<String>, static: LoadableStatic<T>): T {
	return static.load(iterator)
}

object IntLoadableStatic : LoadableStatic<Int> {
	override fun load(iterator: Iterator<String>): Int {
		val x: String = iterator.next()
		return x.toInt()
	}
}

object StringLoadableStatic : LoadableStatic<String> {
	override fun load(iterator: Iterator<String>): String {
		val x: String = iterator.next()
		return x
	}
}

class ListLoadableStatic<T>(val elementStatic: LoadableStatic<T>) :
	LoadableStatic<List<T>> 
{
	override fun load(iterator: Iterator<String>): List<T> {
		val n = Main.load(iterator, IntLoadableStatic)
		val ret = mutableListOf<T>()
		for (i in 0 until n) {
			ret.add(Main.load(iterator, elementStatic))
		}
		return ret
	}
}

object EmployeeLoadableStatic : LoadableStatic<Employee> {
	override fun load(iterator: Iterator<String>): Employee {
		return Employee(
			Main.load(iterator, StringLoadableStatic),
			Main.load(iterator, IntLoadableStatic)
			)
	}
}

fun main(args: Array<String>) {
	val i = load("33", IntLoadableStatic)
	println(i)
	val s = load("abc", StringLoadableStatic)
	println(s)

	val a = load("3/apple/banana/cherry", ListLoadableStatic(StringLoadableStatic))
	println(a)

	val c: Company = load("CatWorld/3/tama/5/mike/6/kuro/7", Company)
	println(c)
}
