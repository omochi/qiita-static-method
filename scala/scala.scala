import scala.collection.immutable
    
object Main {
    trait Loader[T]  {
        def run(tokens: Iterator[String]): T
    }
    
    def load[T](tokens: Iterator[String])(implicit loader: Loader[T]): T = {
        return loader.run(tokens)
    }
    
    implicit object IntLoader extends Loader[Int] {
        def run(tokens: Iterator[String]): Int = {
            val x = tokens.next
            return x.toInt
        }
    }
    
    implicit def createListLoader[T](implicit elementLoader: Loader[T]): Loader[List[T]] = {
        return new ListLoader[T](elementLoader)
    }
    
    class ListLoader[T](elementLoader: Loader[T]) extends Loader[List[T]] {
        def run(tokens: Iterator[String]): List[T] = {
            val n = load[Int](tokens)
            var ret = List[T]()
            for (i <- 0 until n) {
                val x = load[T](tokens)(elementLoader)
                ret = ret :+ x
            }
            return ret
        }
    } 
    
    def main(args: Array[String]) {
        val a = load[List[Int]](List("2", "33", "44").iterator)
        println(a)
    }
}

