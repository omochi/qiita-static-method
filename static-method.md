# 先に結論

- Swiftのstaticメソッド制約はちょっとしたシンプルな機能に見えるけど便利で深い
- Kotlin, TypeScript, Java で同じことをすると冗長で間接的なコードになってしまう
- TypeScriptのstaticメソッド注入は独特のテクニックで面白い
- 提示するJavaの実装は「`T` を `new` する」方法でもあるので参考にして
- Scala は間接的ではあるもののKotlinより良い形でかける
- SwiftはScalaの形をさらにより良くした形とみなすことができる
- C++はSwiftと同じ事ができるがわかりにくい書き方になってしまう



# 導入

Swiftはprotocolという機能で、Javaのinterfaceのように型がインスタンスメソッドをもつ事を強制する事ができます。そしてSwiftではそのメソッド制約として、インスタンスメソッドだけではなくスタティックメソッドをもつ事を強制する事ができます。[このstaticメソッドを強制する機能に公式な名前はありませんが](https://developer.apple.com/library/prerelease/content/documentation/Swift/Conceptual/Swift_Programming_Language/Protocols.html#//apple_ref/doc/uid/TP40014097-CH25-ID267)、この記事ではこれを「staticメソッド制約」と呼ぶことにします。理論的にはHaskellにおける型クラスのようなものであり新しいものではありませんが、クラスベースのデザインをベースにこれを取り込んだSwiftの仕様はなかなか強力であると僕は考えています。

この記事では、架空の要件を考えた上で、それをstaticメソッド制約を活用して実装する例を示します。そして、同等のコードをその他の言語でも似たような形で実装することで、何が起こっているどういう機能なのかをわかりやすくします。

また、同じ目的のプログラムを複数の言語で構成することで、母語の異なるプログラマの方に、他の言語を紹介する効果もあったら良いなと思います。

ところで、この記事で Java を取り上げている理由としては、定期的に 「 `T` を `new` したい 」という話題を見るからです。この記事の内容はその話題に直結しています。詳しくは後述。



# 要件

以下のような架空の要件を考えます。

- 文字列からオブジェクトを生成するタスクを考えます。JSONが一般的ですが、JSONは例としては複雑にすぎるので、ここではシンプルにスラッシュを区切りの文字列を考えます。

- エスケープやエラー処理は無視します。

- `load` 関数を実装し、これに文字列を渡すとデコードされたオブジェクトが返るようにします。

- デコード処理と型システムを組み合わせて、必要な処理が実装されている事がコンパイラチェックされるようにします。これにより、特定の型に対するデコード処理の実装忘れを防ぎます。

- デコード対象の型は事前に定義済みとします。すなわち、言語組み込みの型や、事前定義済みの型のコードを想定し、それを書き換えること無しに追記のみでデコードを実装します。

- Employee型の配列をもつCompany型のデコードを行います。

- 配列のデコードについては、デコードできる型の配列のデコードを一般化して定義します。



# Swiftでの実装

バージョン: `Apple Swift version 3.0.2 (swiftlang-800.0.63 clang-800.0.42.1)`

Swiftでの実装を示します。段階的に実装していきます。まず、対象となるオブジェクトの型を定義します。

```swift
//	swift

struct Employee: CustomStringConvertible {
	var name: String
	var age: Int
	init(name: String, age: Int) {
		self.name = name
		self.age = age
	}

    var description: String {
        return "(name=\(name), age=\(age))"
    }
}

struct Company: CustomStringConvertible {
	var name: String
	var employees: [Employee]
	init(name: String, employees: [Employee]) {
		self.name = name
		self.employees = employees
	}

    var description: String {
        return "(name=\(name), employees=\(employees))"
    }
}
```

Employee型とCompany型を定義しました。このCompany型をデコードするのが目標になります。また前提として、これらのクラス定義部は以降編集はしません。次に、load関数を示します。

```swift
//	swift

func load<T>(string: String) -> T {
	let tokens = string.components(separatedBy: "/")
	let iterator = AnyIterator<String>(tokens.makeIterator())
	return load(iterator: iterator)
}

func load<T>(iterator: AnyIterator<String>) -> T {
	//	ここにIteratorからTをデコードする処理を書く
}

```

1つめに定義しているのが目標の `load` 関数です。引数として文字列を受け取り、返り値としてデコード結果の値を返します。型パラメータを取るジェネリック関数とすることで、T型をデコードできるようにします。実装では、文字列をスラッシュで区切って `Iterator` にします。この `Iterator` から `T` をデコードする処理は 2つめの `load` としてオーバーロードしています。あとはこの中身を書けば完成です。

このデコード処理は型 `T` の実体によって異なる処理が必要になります。型ごとに異なる処理といえばstaticメソッドだという事で、次のようなプロトコルを定義します。

```swift
//	swift

protocol Loadable {
	static func load(iterator: AnyIterator<String>) -> Self
}

//{}
```

このように定義することで、 `Loadable` プロトコルを満たす型は、 `Iterator` を受け取って自身の型を返すstaticメソッド `load` を持つ事が制約されます。 `Self` は自身の型を示す特別なキーワードです。さて、これを定義すれば、先程の `load` メソッドの実装は簡単に完成します。

```swift
//	swift

func load<T: Loadable>(string: String) -> T {
	let tokens = string.components(separatedBy: "/")
	let iterator = AnyIterator<String>(tokens.makeIterator())
	return load(iterator: iterator)
}

func load<T: Loadable>(iterator: AnyIterator<String>) -> T {
	return T.load(iterator: iterator)
}
```

`load` の型パラメータ `T` のところに、 `Loadable` プロトコル制約を追記しました。これによって、 `T.load(iterator: iterator)` という呼び出しが書けるようになります。

次に、 `Int` 型と `String` 型のデコードを実装します。Swiftは拡張メソッドという機能を使って、既存の型に対して外部からメソッドを追加する事ができます。また、それと同時に外部からプロトコルを適用させる事ができます。

```swift
//	swift

extension Int: Loadable {
	static func load(iterator: AnyIterator<String>) -> Int {
		let x: String = iterator.next()!
		return Int(x)!
	}
}

extension String: Loadable {
	static func load(iterator: AnyIterator<String>) -> String {
		let x: String = iterator.next()!
		return x
	}
}
```

型が具象化されているので、返り値の型は `Self` から実際の型に書き換えます。デコード仕様については、Intは単純に文字列から変換、Stringはそのまま取り出すだけとします。

テストしてみます。

```swift
//	swift

	let i: Int = load(string: "33")
	print(i)
	let s: String = load(string: "abc")
	print(s)
```

出力

```text
33
abc
```

うまく動いています。

ここで、 `load` の型パラメータ `T` が、 `load` の書かれている代入文の左辺の変数の型により推論されていることがポイントです。ちなみにSwiftではジェネリック関数の型パラメータに対して明示的に型アノテートする事ができません。なのでこの `load` 関数の場合、必ず返り値の `T` を何かしら確定させる必要があります。この場合は代入文の両辺の型制約でそこを決定させています。

次に、配列のデコードを実装します。 `Int` や `String` と同様にして、 `Array` に対して extensionで実装します。ここで、Arrayの要素の型としては、 `Loadable` な型に限定する必要があります。なぜなら、そうでない型が来たところで、配列の要素のデコードができないからです。

しかし残念ながら下記のように書くことはできません。

```swift
//	swift

extension Array: Loadable where Element: Loadable {
	...
}
```

現状のSwiftはextensionによるプロトコルの適用と、型パラメータ条件つきのextensionのそれぞれの機能はあるのですが、これを同時に使うことができません。幸い、将来的には対応する方針のようです。([GenericsManifesto / Conditional conformances](https://github.com/apple/swift/blob/master/docs/GenericsManifesto.md#conditional-conformances-))

さて、妥協して下記のように実装します。

```swift
//	swift

extension Array where Element: Loadable {
	static func load(iterator: AnyIterator<String>) -> [Element] {
		let n: Int = Main.load(iterator: iterator)
		var ret: [Element] = []
		for _ in 0..<n {
			ret.append(Main.load(iterator: iterator))
		}
		return ret
	}
}
```

これで、要素の型が `Loadable` であるときのみ、 `Array.load` メソッドが定義されます。残念ながら `Array` 自体は `Loadable` になっていないので、単にメソッドが定義されるだけです。そのため、既に定義した `Main.load` から `Array` をデコードさせることはできません。

メソッド本体の実装について述べます。staticメソッドの `load` と、トップレベルの `load` を区別するため、トップレベルのものを `Main.load` として呼び出しています。そのためにコンパイラにソースコードのモジュール名として `Main` を指定しています。

```
$ swift -module-name Main swift.swift
```

デコード方式としては、はじめに整数をデコードしてこれが要素数となり、その要素数の数だけ要素をデコードしています。要素数のデコードにおいては、先程のテストと同様に左辺値での型指定により `Int` をデコードさせます。要素のデコードにおいては、 `ret.append()` の引数の型が、 `ret` の要素の型 `Element` を受け取るようになっているため、 `Element` をデコードするように推論されます。この `Element` というのは、 `Array` にもともと定義されている 要素の型のパラメータ名です。

さて、残念ながら `Array` を `Loadable` にできなかったので、 `load` からこれを呼び出す事ができません。そこで、同じ `load` メソッドを、返り値の型が違う `load` メソッドとしてオーバロードします。

```swift
//	swift

func load<T: Loadable>(string: String) -> [T] {
	let tokens = string.components(separatedBy: "/")
	let iterator = AnyIterator<String>(tokens.makeIterator())
	return load(iterator: iterator)
}

func load<T: Loadable>(iterator: AnyIterator<String>) -> [T] {
	return Array<T>.load(iterator: iterator)
}
```

もともとある `load` は 型 `T` が `Loadble` であるときに 返り値の型が `T` となる `load` メソッドですが、今回定義したのは 型 `T` が `Loadble` であるときに 返り値の型が `[T]` となる `load` メソッドです。既に述べた理由により、 `Array` が `Loadable` になることはありえないため、返り値が `Array` である場合 `T` を返す `load` がマッチすることはありません。そのため、この `[T]` を返す `load` が、 `T` を返す `load` と曖昧になることはありません。

`Array` のデコードをテストしてみます。

```swift
//	swift

	let a: [String] = load(string: "3/apple/banana/cherry")
	print(a)
```

出力

```text
["apple", "banana", "cherry"]
```

うまく動きました。

ここまでできれば後は簡単です。EmployeeとCompanyは下記のように実装できます。

```swift
//	swift

extension Employee: Loadable {
	static func load(iterator: AnyIterator<String>) -> Employee {
		return Employee(
			name: Main.load(iterator: iterator),
			age: Main.load(iterator: iterator)
			)
	}
}

extension Company: Loadable {
	static func load(iterator: AnyIterator<String>) -> Company {
		return Company(
			name: Main.load(iterator: iterator),
			employees: Main.load(iterator: iterator))
	}
}
```

`Array.load` を実装した時と同じように、 `Main.load` がデコードする型のコンストラクタ引数のところに直接書かれているので、型推論器がコンストラクタ引数の型を参照してくれるおかげで、 `Main.load` についての型指定が不要になっています。 `employees:` のところは、引数の型として `[Employee]` が期待されているため、 `[T]` を返す方の `Main.load` がオーバーロードとして解決されます。

テストしてみます。

```swift
//	swift

	let c: Company = load(string: "CatWorld/3/tama/5/mike/6/kuro/7")
	print(c)
```

出力

```text
(name=CatWorld, employees=[(name=tama, age=5), (name=mike, age=6), (name=kuro, age=7)])
```

この通り、目的の `load` 関数がうまく動きました。

いかがでしょうか。比較的直感的な構文によって、型推論を活かした複雑な記述が簡潔に記述できていると思います。逆に、あまりに自然な記述すぎて、すごく思えないかもしれません。そのあたりは後述する他の言語の場合を見ていくと感じ方が変わってくると思います。

## Swift実装の修正

実は残念な事に、実用上の利便性を考えるともう少し修正が必要になります。ためしに、 `Company` を `struct` から `class` に変更すると下記のようにコンパイルエラーになります。

```
swift.swift:92:14: error: method 'load(iterator:)' in non-final class 'Company' must return `Self` to conform to protocol 'Loadable'
        static func load(iterator: AnyIterator<String>) -> Company {
```

このエラーメッセージは、 `Company` が `Loadable` を適用できていないと言っています。理由は、 `Company` が継承可能なために、 `Self` が満たせないからです。

Swiftではあるクラスのサブクラスも、親クラスのstaticメソッドが呼び出し可能です。つまり下記が許されます。

```swift
//	swift

class PaperCompany : Company { }

PaperCompnay.load(string: "aaa")
```

しかしここで問題が起きます。 `Company` は `Loadable` ですから、 `PaperCompnay` も `Loadable` です。 `PaperCompnay` が `Loadable` であるということは、 `PaperCompany` も staticメソッドの `load` を持っていて、その返り値の型は `PaperCompany` ということになります。 しかしここで実装は親クラスの `Company` にあるので、返ってくるのは `Company` です。よって型の矛盾が起こってしまいます。

これは `Comapny` を継承した瞬間に一般に生じる問題です。そのため、 `Company` 単体でコンパイルエラーとなったわけです。

これを回避するためには、 `Company.load` も `PaperCompany.load` も同じ型を返す必要があります。 よってもはや `Loadable` は `Self` を返せません。これは `Loadable` 実装時の型パラメータとなるので、 `Loadable` をそのようにパラメータを取るように修正します。

```swift
//	swift

protocol Loadable {
	associatedtype Loaded
	static func load(iterator: AnyIterator<String>) -> Loaded
}

//{}
```

`associatedtype` はプロトコルの型パラメータを宣言する命令です。ここでは `load` によってデコードされる型を `Loaded` と命名しました。幸い、Swiftのプロトコルの型パラメータは、パラメータが出現しているところの具象化された実装から推論して決定してくれます。そのため、 `Int` や `String` などを `Loadable` に適用させているところのコードは修正不要です。 `load` メソッドの返り値が `Int` や `String` となっていることから解決してくれるのです。

しかし今度は、 `Main.load` の実装部分でエラーとなってしまいます。

```
swift.swift:45:11: error: cannot convert return expression of type 'T.Loaded' to return type 'T'
        return T.load(iterator: iterator)
```

これは、 `T: Loadable` を型パラメータとして受け取り、 `T` を返す関数 `load` が、受け取っている `T` の `Loaded` パラメータが `T` とは限らない事によるエラーです。 `Loaded` パラメータは今さっき定義した `associatedtype` の事です。 そこで、 `T` と `T.Loaded` が同一である場合に限って `load` が呼び出し可能である、と制約を追加します。

```swift
//	swift

func load<T: Loadable>(string: String) -> T 
	where T.Loaded == T
{ ... }

func load<T: Loadable>(iterator: AnyIterator<String>) -> T
	where T.Loaded == T 
{ ... }

func load<T: Loadable>(string: String) -> [T] 
	where T.Loaded == T 
{ ... }

func load<T: Loadable>(iterator: AnyIterator<String>) -> [T] 
	where T.Loaded == T 
{ ... }

extension Array where Element: Loadable,
	Element.Loaded == Element
{ ... }
```

このように `where` による制約の追記を行います。 `Array` の extension 部分にも必要です。

これで修正は完了です。

そして以下の記述はコンパイルできなくなります。

```swift
//	swift

let p: PaperCompany = load(string: "")
```

`PaperCompany.Loaded` が `Company` のため、 `T.Loaded == T` を満たさないため `Main.load` が定義されないからです。

運用上、このようなサブクラス化するケースでは、エンコードする時に型名をヘッダとして入れておいて、 `Company.load` の実装においてその型名をまず読み取り、それに応じて `PaperCompany` など、どのサブクラスをデコードするのかを分岐する、といったコードになるでしょう。
もしくは、JSON APIに型付けするためだけの型である場合は、継承はさせずに `has-a` 関係で取り扱うのも良いでしょう。



# kotlinでの実装

バージョン: `Kotlin Compiler version 1.0.6-release-127`

次にkotlinでの実装を示します。はじめに `Employee` と `Company` を定義します。

```kotlin
//	kotlin

data class Employee(val name: String, val age: Int)

open class Company(val name: String, val employees: List<Employee>) {
	override fun toString(): String {
		return "(name=$name, employees=$employees)"
	}
}
```

swift版の最終状態にあわせて、 `Employee` を `data class` 、 `Company` を `open class` にしています。 `data class` は `toString()` を自動実装してくれるので助かります。

さて、早速インターフェースの定義ですが、kotlinのインターフェースに staticメソッドは書けないので、とりあえずstatic側のインターフェースを定義します。

```kotlin
//	kotlin

interface LoadableStatic<T> {
	fun load(iterator: Iterator<String>): T
}
```

次に目的の `load` 関数です。あとで名前を区別できるよう、一番上でパッケージも指定しておきます。

```kotlin
//	kotlin

package Main

...

fun <T> load(iterator: Iterator<String>): T {
	//	ここに LoadableStatic のメソッド呼び出しが必要
}
```

しかしここで困ることになります。swiftの場合は制約をかけた `T` に対して `T.load` という形でstaticメソッド呼び出しを書くことができました。しかし今 `T` に対してstaticメソッドの制約を書くことはできません。肝心の `load` の実体は、 `LoadableStatic` インターフェースに定義されています。インターフェースのメソッドを呼び出すためには、そのインターフェースを満たすオブジェクトのインスタンスメソッドを呼ぶしかありません。 `T` に対応する `LoadableStatic<T>` のインスタンスがなんらかの方法で呼び出せれば良いですが、そのような機能もありません(厳密にはあるんですがここでは無視します。詳細は注釈に書きました。[^1])。そうすると結局のところ、 `LoadableStatic<T>` のインスタンスを引数として受け取るしかありません。よって下記のようになります。

```kotlin
//	kotlin

fun <T> load(string: String, static: LoadableStatic<T>): T {
	val tokens = string.split("/")
	val iterator = tokens.iterator()
	return load(iterator, static)
}

fun <T> load(iterator: Iterator<String>, static: LoadableStatic<T>): T {
	return static.load(iterator)
}
```

Swiftと同様、文字列を受けるオーバーロードと、 `Iterator` を受けるオーバーロードを定義します。

そして、 `Int` のデコード、 `String` のデコードは以下のようになります。

```kotlin
//	kotlin

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
```

`object` はその場で匿名型を作りつつ、インスタンスを静的に定義する構文です。これを `Int` と `String` それぞれのために定義しつつ、 `LoadableStatic` を適用する事で型付けします。

そして呼び出し側は下記のようになります。

```kotlin
//	kotlin

	val i = load("33", IntLoadableStatic)
	println(i)
	val s = load("abc", StringLoadableStatic)
	println(s)
```

swiftでは左辺の返り値の型から推論させましたが、今回は型の推論だけでなく、型に対応した適切な `LoadableStatic` のインスタンスを渡さねばならないため、これを手動で書きます。よって型の決定手順としては、手書きした `IntLoadableStatic` を起点に決定しています。

次に配列のデコーダを作ります。配列のデコーダは要素の型がデコード可能であるならデコード可能であるように作る必要があります。しかし今、 `Int` と `IntLoadableStatic` は特に何の結びつきもされていません。つまり、 `Int` という型名だけから、これに対応する `LoadableStatic<Int>` なオブジェクトが存在するかどうかは判定できないのです。よって、配列の要素の型があるときに、それがデコード可能かどうか、言語的に判定する方法はありません。とりあえずその点はおいておいて実装すると下記のようになります。

```kotlin
//	kotlin

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
```

基本的な作りはswiftの場合と同じです。違いとして、要素数を読み取るところで `IntLoadableStatic` 明示しています。そして、実装してみればわかることですが、要素を読み取るところの `Main.load` の呼び出しで、要素の型の `LoadableStatic` のインスタンスが必要になります。これは先程も説明したとおり、要素の型 `T` から静的に取得する事はできません。となると、 `ListLoadableStatic` のコンストラクタ引数で、要素の型の `LoadableStatic<T>` のインスタンスを受け取る必要があるのです。このようにして、 `Int` や `String` の場合と違い、 `List` の場合は `object` ではなく `class` での定義となり、必要な箇所でこのインスタンスを生成するという使い方になります。

呼び出しは下記のようになります。

```kotlin
//	kotlin

	val a = load("3/apple/banana/cherry", ListLoadableStatic(StringLoadableStatic))
	println(a)
```

`load` の第2引数のところで、 `List<String>` を表現しているのがわかります。なお、この場合は swiftの場合と違って `load` 関数は `T` を返すもの1つだけでよく、 `List<T>` を返す `load` の実装は不要です。その代わりに、 `ListLoadableStatic` を書かねばならなくなっているわけです。

また、ここで自ずと先程の、要素の型がデコード可能である事の制約も満たされます。要素の型がデコード不可能である場合は、 `ListLoadableStatic` のコンストラクタに渡す `LoadableStatic` のインスタンスが存在しないからです。

最後に `Employee` と `Company` のデコーダを作ります。これまでの流れを踏襲して実装できます。前提ではこれらは事前定義クラスで、変更できないとしましたが、例えば仮に `Company` は事前定義では無かったとすれば、 `CompanyLoadableStatic` を定義せずとも、 `Company` の Companion Object を `LoadableStatic` とする事ができます。ここではそのようにしてみましょう。

```kotlin
//	kotlin

object EmployeeLoadableStatic : LoadableStatic<Employee> {
	override fun load(iterator: Iterator<String>): Employee {
		return Employee(
			Main.load(iterator, StringLoadableStatic),
			Main.load(iterator, IntLoadableStatic)
			)
	}
}

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
```

`Company` については既に定義してる class 定義に追記する形で companion object を実装しました。 `Company` の employee をデコードする部分は、 `ListLoadableStatic` を `EmployeeLoadableStatic` と合わせて指定することになります。

こうすると `Company` のデコードは以下のようになります。

```kotlin
//	kotlin

	val c: Company = load("CatWorld/3/tama/5/mike/6/kuro/7", Company)
	println(c)
```

出力

```
(name=CatWorld, employees=[Employee(name=tama, age=5), Employee(name=mike, age=6), Employee(name=kuro, age=7)])
```

これでうまく動きました。 `Company` のデコードにあたっては、 `Company` の companion objectを `LoadableStatic` としているので、 `load` の第2引数には `Company` を渡しています。

いかがでしょうか。swiftの場合と比較すると、swiftでは推論器が `T` を決定した上で `T` からコードが生成される部分で、kotlinでは明示的にその動作を司るオブジェクトを渡す必要があります。このオブジェクトとして何を渡すべきかは、型 `T` から固定的に決まります。そして `T` は周辺の式と共に本来は静的に決まっているので、決まりきった冗長なコードを書く必要があることがわかります。



# typescriptでの実装

バージョン: `Version 2.2.1`

TypeScriptの型システムはこのテーマに限って言えばkotlinとほとんど同じです。ジェネリックパラメータの具象化された型 `T` に対してstaticメソッドを呼び出したり、紐付いたオブジェクトを取得する事ができないため、 `LoadableStatic` を実装していく方針になります。

なので概要はあまり変わらないですが、せっかくなのでTypeScriptならではの小技を織り込んでやっていこうと思います。

LoadableStaticクラスの実装ですが、TypeScriptはJavaScriptと同様に事前定義済みのクラスに対してメソッドを外から追加することができます。それを使えば、kotlinでCompanyのときにやった、対象のクラスそれ自体を LoadableStatic 化する書き方を、既存定義クラスに対してできます。なのでその方針で行きます。

ちなみにJavaScriptでこれをやると、そのようなメソッド追加を複数のライブラリから行ってしまって上書き合戦になって壊れるリスクがありますが、完全TypeScript環境ならその際の型衝突は静的に検出してくれるため、まだマシです。(同じ型での値の上書き合戦はありえる)。

とりあえずまずはじめに `Iterator` が無いので適当に作ります。

```typescript
//	typescript

interface Iterator<T> {
	next(): T | null;
}

function makeIterator<T>(array: Array<T>): Iterator<T> {
	let i = 0;
	return {
		next: (): T | null => {
			if (i >= array.length) {
				return null;
			}
			const ret = array[i];
			i += 1;
			return ret;
		}
	};
}
```

言語設定で Strict Null Check は有効にしています。なのでnextは `T | null` として、終端に到達したら null が返るものとします。
makeIteratorは 渡したArrayの要素を読み取るIteratorを生成します。このIteratorの実装部分ですがオブジェクトリテラルとローカル変数のラムダキャプチャによる状態変更で実装しています。TypeScriptはこのような即値オブジェクトに対してinterfaceの型チェックが効くのが面白いですね。

次に `Employee` と `Company` を定義します。

```typescript
//	typescript

class Employee {
	name: string;
	age: number;
	constructor(name: string, age: number) {
		this.name = name;
		this.age = age;
	}
	toString(): string {
		return `(name=${this.name}, age=${this.age})`;
	}
}

class Company {
	name: string;
	employees: Employee[];
	constructor(name: string, employees: Employee[]) {
		this.name = name;
		this.employees = employees;
	}
	toString(): string {
		return `(name=${this.name}, employees=${this.employees})`;
	}
}
```

特に変わった点はありません。kotlinではEmployeeをdata classにしましたが、ここをfinal classにしたところで特に差がでないので普通のclassにしています。

`LoadStatic` と `load` は下記のようになります。

```typescript
//	typescript

interface LoadableStatic<T> {
	load(iterator: Iterator<string>): T;	
}

function load<T>(string: string, static: LoadableStatic<T>): T;
function load<T>(iterator: Iterator<string>, static: LoadableStatic<T>): T;
function load<T>(a1: string | Iterator<string>, static: LoadableStatic<T>): T {
	if (typeof a1 == "string") {
		const string: string = a1;
		const tokens: Array<string> = string.split("/");
		return load(makeIterator(tokens), static);
	} else {
		const iterator: Iterator<string> = a1;
		return static.load(iterator);
	}
}
```

これまでと同じようにやったので 2つの `load` はオーバーロードさせています。TypeScriptのオーバーロードは実行時ディスパッチを手動で書くのが残念ですが、幸いな事に Union Type と フローベースのキャストが入っているので、このifとelseのブロックでは引数の型が静的に検証されています。

さて、まずは `number` のデコードです。おもむろに `lib.d.ts` を確認すると、標準の `Number` はこんな感じになっています。


```typescript
//	typescript

interface NumberConstructor {
    new (value?: any): Number;
    (value?: any): number;
    readonly prototype: Number;
	...
}

declare const Number: NumberConstructor;
```

クラス構文での定義ではなく、クラスそれ自体の静的なインターフェースを `NumberConstructor` という名前で定義して、その型を持つ定数として `Number` が存在する、という宣言になっています。

こういうケースでは、同名interfaceの再定義をするとinterfaceの定義がマージされて、プロパティを追加することができます。なので、今追加したい staticメソッド `load` をクロージャ型のプロパティとして追加定義した上で、クロージャをそこに代入する事で staticメソッドの注入ができます。実装すると下記のようになります。

```typescript
//	typescript

interface NumberConstructor {
	load: (iterator: Iterator<string>) => number;
}

Number.load = (iterator: Iterator<string>): number => {
	const x: string = iterator.next()!!;
	return parseFloat(x);	
};
```

さらに、TypeScriptはinterfaceの充足性を単純にメソッドを持っているかどうかで判定してくれるため、明示的に宣言していませんが、現時点で `NumberConstructor` is `LoadableStatic<number>` として代入可能になっています。

これで、以下のようにして呼び出せます。

```typescript
//	typescript

	const i = load("33", Number);
	console.log(i);
```

同様にしてStringもやります。

```typescript
//	typescript

interface StringConstructor {
	load: (iterator: Iterator<string>) => string;
}

String.load = (iterator: Iterator<string>): string => {
	const x: string = iterator.next()!!;
	return x;
};
```

次にArrayです。Arrayはkotlinのときと同様に要素の型を司る `LoadableStatic` を保持しないといけないため、組み込みの Arrayクラスのオブジェクトを `LoadableStatic` 化する事ができません。専用に定義するします。

```typescript
//	typescript

class ArrayLoadableStatic<T> implements LoadableStatic<T[]> {
	constructor(elementStatic: LoadableStatic<T>) {
		this.elementStatic = elementStatic;
	}
	load(iterator: Iterator<string>): T[] {
		const n = load(iterator, Number);
		const ret: T[] = [];
		for (let i = 0; i < n; i++) {
			ret.push(load(iterator, this.elementStatic));
		}
		return ret;
	}
	private elementStatic: LoadableStatic<T>;
}
```

kotlinのときとほぼ同じ形です。kotlinでは `Main.load` と書いていた部分で `load` とかけるのは、typescriptの場合、逆にインスタンスメソッドを `this.load` と書かなければならないからです。シンボル解決のルールの違いですね。またkotlinではprimary constructorがあったのでフィールド定義が不要でしたが、typescriptではフィールドの定義とコンストラクタでの代入が必要になっています。

これの呼び出しは以下のようになります。

```typescript
//	typescript

	const a = load("3/apple/banana/cherry", new ArrayLoadableStatic(String));
	console.log(a)
```

最後に、 `Employee` と `Company` の `LoadableStatic` 化を行います。これまでの `Number` と `String` は interfaceの合成によって注入が可能でしたが、クラスに対して staticメソッドを注入する場合はまた異なるテクニックを使います。

typescriptでは名前の宣言は `namespace` と `type` と `value` の3種類に分かれています。そして、この異なる宣言を同じ名前で行ってマージさせることができます。詳しくはこちらに書いてあります。([Declaration Merging
](https://www.typescriptlang.org/docs/handbook/declaration-merging.html))

クラスのstaticメソッドは、ネームスペースに属する関数と同じ見え方になるので、これを利用して、クラスと同名のネームスペースにクロージャ型のプロパティを作り、そこにクロージャを代入してやると、実行時にはクラスのstaticメソッドが生えるような動作になります。

```typescript
//	typescript

namespace Employee {
	export let load: (iterator: Iterator<string>) => Employee;
}

Employee.load = (iterator: Iterator<string>): Employee => {
	return new Employee(
		load(iterator, String), 
		load(iterator, Number));
}
```

呼び出しは下記のようになります。

```typescript
	const e = load("taro/3", Employee);
	console.log(`${e}`);
```

この時、 `Employee` という名前はクラスとネームスペースの両方についているのですが、 typescriptがネームスペースの方に定義してある `load` によって `Employee` オブジェクトが `LoadableStatic` インターフェースを満たす事を検出してコンパイル可能となります。

`Company` も同様にいけます。

```typescript
//	typescript

namespace Company {
	export let load: (iterator: Iterator<string>) => Company;
}

Company.load = (iterator: Iterator<string>): Company => {
	return new Company(
		load(iterator, String),
		load(iterator, new ArrayLoadableStatic(Employee)));
}
```

Arrayのところは例によって `ArrayLoadableStatic`　に `Employee` を渡します。呼び出しは以下のようになります。

```typescript
//	typescript

	const c = load("CatWorld/3/tama/5/mike/6/kuro/7", Company)
	console.log(`${c}`);
```

いかがでしょうか。概ねkotlinと変わりませんでした。typescriptの変わった魅力を紹介できたかなと思います。



# Javaでの実装

バージョン: `javac 1.8.0_45`

Javaでの実装ですが、細かい事は無視して `Main.java` のワンファイルで実装します。

Javaの型システムもだいたいこれらと同じなので、ほぼ冗長なkotlin版みたいになります。

まず `Employee` と `Company` を定義します。

```java
//	java

class Employee {
	public Employee(String name, int age) {
		this.name = name;
		this.age = age;
	}
	public String name;
	public int age;
	@Override
	public String toString() {
		return String.format("(name=%s, age=%d)", name, age);
	}
}

class Company {
	public Company(String name, List<Employee> employees) {
		this.name = name;
		this.employees = employees;
	}
	public String name;
	public List<Employee> employees;
	@Override
	public String toString() {
		return String.format("(name=%s, employees=%s)", name, employees);	
	}
}
```

特に言うことはありません。

`LoadableStatic` と `load` を定義します。

```java
//	java

interface LoadableStatic<T> {
	public T load(Iterator<String> iterator);
}

class Load {
	public static <T> T load(String string, LoadableStatic<T> statik) {
		String[] tokens = string.split("/");
		return load(Arrays.asList(tokens).iterator(), statik);
	}

	public static <T> T load(Iterator<String> iterator, LoadableStatic<T> statik) {
		return statik.load(iterator);
	}
}
```

`load` を定義するための空間として `Load` クラスを定義しました。`static` が変数名に使えなかったので `statik` にしています。

intのデコードを実装します。

```java
//	java

class IntLoadableStatic implements LoadableStatic<Integer> {
	@Override
	public Integer load(Iterator<String> iterator) {
		String x = iterator.next();
		return Integer.parseInt(x);
	}
}
```

型パラメータのところで `int` が書けないので `Integer` を使います。呼び出し例は以下のとおり。

```java
//	java

		int i = Load.load("33", new IntLoadableStatic());
		println(Integer.toString(i));
```

`IntLoadableStatic` をその場で `new` しています。これは本当はstaticなオブジェクトを用意すればインスタンスをいちいち作らなくて良いので負荷軽減になりますが、実装が面倒なので省略です。kotlinのobject構文は便利です。

Stringはこのとおり。

```java
//	java

class StringLoadableStatic implements LoadableStatic<String> {
	@Override
	public String load(Iterator<String> iterator) {
		String x = iterator.next();
		return x;
	}
}
```

Listもこれまで通りです。

```java
//	java

class ListLoadableStatic<T> implements LoadableStatic<List<T>> {
	public ListLoadableStatic(LoadableStatic<T> elementStatic) {
		this.elementStatic = elementStatic;
	}
	@Override
	public List<T> load(Iterator<String> iterator) {
		int n = Load.load(iterator, new IntLoadableStatic());
		ArrayList<T> ret = new ArrayList<T>();
		for (int i = 0; i < n; i++) {
			ret.add(Load.load(iterator, elementStatic));
		}
		return ret;
	}
	private LoadableStatic<T> elementStatic;
}
```

呼び出しは下記の通り。

```java
//	java

		List<String> a = Load.load("3/apple/banana/cherry", new ListLoadableStatic<>(new StringLoadableStatic()));
		println(a.toString());
```

横に長いですね。一応、 `ListLoadableStatic` の型パラメータを `<>` にできて、ここは推論してくれます。

`Employee` と `Company` を実装します。

```java
//	java

class EmployeeLoadableStatic implements LoadableStatic<Employee> {
	@Override
	public Employee load(Iterator<String> iterator) {
		return new Employee(
			Load.load(iterator, new StringLoadableStatic()),
			Load.load(iterator, new IntLoadableStatic())
			);
	}
}

class CompanyLoadableStatic implements LoadableStatic<Company> {
	@Override
	public Company load(Iterator<String> iterator) {
		return new Company(
			Load.load(iterator, new StringLoadableStatic()),
			Load.load(iterator, new ListLoadableStatic<>(new EmployeeLoadableStatic()))
			);
	}
}
```

素直な感じです。最終的に `Company` のデコードは下記のようになります。

```java
//	java

		Company c = Load.load("CatWorld/3/tama/5/mike/6/kuro/7", new CompanyLoadableStatic());
		println(c.toString());
```

以上です。

型推論も無ければnull安全でもなく完全に冗長という感じですが、余計なものが無いのでそもそもどういうことなのかはわかりやすいかと思います。

また、この記事でJavaを取り上げているのは、定期的にJavaの「 `T` を `new` したい」という話題を見かけるからです。この記事の内容はその話題に直結しています。専用の演算子があるお陰で `new` というのは特別な機能に見えますが、型システム的には本質的にただの型 `T` のstaticメソッドです。型 `T` があれば呼び出せて、引数の型はいろいろな場合があってオーバーロードできて、同じなのです。返り値の型が `T` に制約されているという点と、呼び出しに型名と `new` 演算子を使うという見た目だけが変わっています。実際、swiftではprotocolに対して `init` エントリを書くことができます。 `init` というのはswiftにおけるコンストラクタを表すキーワードです。またtypescriptでは、 `NumberConstructor` の定義のところで見たように、オブジェクトに対する `new` 呼び出しをインターフェースのメソッドの一つとして記述できます。これらの事からもコンストラクタは所詮ただのスタティックメソッドの亜種だとわかります。

さて、そうすると「 `T` を `new` したい」というのは、今回swiftで作った `T.load` を呼び出したい、という話と等価だとわかります。しかし、kotlinやtypescriptと同様、結局javaでは `T.load` はかけません。それどころか、インターフェース制約も置けません。なので結局、その静的な振る舞いを直接インターフェース化して、静的な振る舞いをするためのオブジェクトを `T` が解決される箇所に直接インスタンスとして渡してやる必要が出てくるのです。また、リストのような構造化型の場合は、その要素の型をデコードするために、リストのデコーダが要素のデコーダを持つ、というように、型の入れ子構造を、デコーダオブジェクトの入れ子構造として再現する必要があります。

逆に言えば、それがやりたければこのように実装したら良いのです。 `T` が確定する時には、その具象化された `T` に対応する静的な振る舞いを持つオブジェクトを引数に渡せば良いのですから、プログラマが機械的な操作を一手間行うだけです。また、作り込んでみるとわかりますが、実際には `<T>`　を持つ関数は、さらに外側の `<T>` を持つ関数から呼び出されていたりして、そういう場合はこれらのオブジェクトは既に引数で受けているので、さらに内側に転送していくだけで良く、意外とそれで済む箇所がたくさんあります。結局アプリサイドで手間が増えるのは、この `T` を決定する一番外側の呼び出し部分だけになるのです。

ちなみに、この議論に対して C# が与えてる回答が興味深いです。 C#には `new()` 制約というのが型に対して書けて、引数無しコンストラクタの保持が矯正されます。つまり、staticメソッド制約がピンポイントで1つだけ定義できるのです。ちなみに引数なしコンストラクタをデフォルトコンストラクタといいます。また、 C#の `struct` は デフォルトコンストラクタの自動実装が強制されています。そのため、 `struct` は先の `new()` 制約を満たします。個人的にはこれは中途半端な対応で、メリット以上に持ち込むデメリットも多く微妙な仕様だと思っています。 `struct` のデフォルトコンストラクタの保持が強制されていることで、フィールドの値が全てデフォルトコンストラクタで埋まる可能性が常に残されます。つまり、参照型が全てnullで埋まり、数値類は0等で埋まっている、という状態が絶対に存在してしまいます。これのおかげで、swiftのように参照型のフィールド値を全てコンストラクタで受けて、nullが無いことを保証するというnull safeプログラミングができません。また参照型にかぎらず、何らかの値を持たないと意味がない値型も考えられます。

おそらくこの仕様が設計された背景として、値型の配列をメモリ直列に定義できる事を保証したかったのではないかと思っています。配列に要素が追加される際に、後々のために余裕を持って多めにメモリスペースを確保するという最適化があります。しかしこれをやるためには、余裕として確保したメモリをその型で初期化しなければなりません。ここで `T` で `new` したい、つまり、デフォルトコンストラクタが欲しくなってしまうのです。そして、値型にその制約があるのであれば、クラス型にも同じ制約を追加する機能があってもいいだろう、という順序ではないかと思います。しかしこれは、特定のユースケースに限った話で、一般的な解決になっていません。今回のように文字列にもとづいて `new` したい場合がありますし、staticメソッド一般で言えば返る型として `T` 以外のものが欲しいこともありえます。だからこの話題において、 「C#なら解決してるのに」という目線でJavaを批判するのは筋が悪いと思っています。C#の解決法自体の筋が悪いからです。これからは「swiftなら解決してるのに」と言うと良いのではないでしょうか。

# Scalaでの実装

バージョン: `Scala code runner version 2.12.1`

さて、kotlin, typescript, javaと苦しい感じが続きましたが、ここで真打ちの登場です。scalaです。scalaには `trait` というインターフェースみたいなものがありますが、残念ながらこれにもstaticメソッド制約は書けません。
なので基本的にはこれまでと同じように `LoadableStatic` なオブジェクトを引数に渡していく必要があります。しかしそこで、scalaには `implicit parameter` という、なんと自動的に引数にオブジェクトを渡してくれる機能があるのです。この機能は、関数の引数宣言に `implicit` という印を付けておくと、同じく `implicit` という印がついた値のうち、型がマッチする値がただ1つだけ見つかる場合に、それを自動で渡してくれるのです。この機能を使うことで、 `LoadableStatic` を渡していた箇所を `implicit` 化して、実際には渡しているんだけど渡すコードを書く必要がない、というスタイルにできるのです。

早速やっていきましょう。

```scala
//	scala

case class Employee(name: String, age: Int)

class Company(name: String, employees: List[Employee]) {
    override def toString(): String = {
        return s"name=${name}, employees=${employees}"
    }
}
```

`Employee` と `Company` は特に変わったことはありません。 `case class` は `toString` が自動実装されます。kotlinの `data class` みたいなやつです。まあ、後発のkotlinがscalaをパクっているんですけど。

さて、次の `load` が注目です。

```scala
//	scala

trait LoadableStatic[T] {
    def load(iterator: Iterator[String]): T
}

object Load {
    def load[T](string: String)(implicit static: LoadableStatic[T]): T = {
        val tokens = string.split("/")
        return load(tokens.iterator)
    }
    def load[T](iterator: Iterator[String])(implicit static: LoadableStatic[T]): T = {
        return static.load(iterator)
    }
}
```

トップレベル関数が置けないため `load` メソッドは `Load` の中に書きました。さて、この `load` メソッドの `(implicit static: LoadableStatic[T])` の部分が `implicit parameter` です。丸括弧が2つ並んでいるのは、 `implicit parameter` の部分はカリー化してバラすというルールがあるためです。1つ目の `load` から 2つ目の `load` を呼び出すところでは、早速この `implicit` 渡しが適用されています。具象化された `T` の元で、 `LoadableStatic[T]` な値がちょうど関数仮引数の `static` に用意されているので、じゃあこれを渡しておこうという流れなわけです。これまでswift以外はこの 1つ目から2つ目の呼び出して明示的な転送が必要でした。

では、 `Int` と `String` のデコーダの実装を見ていきましょう。

```scala
//	scala

package object Implicits {
    implicit object IntLoadableStatic extends LoadableStatic[Int] {
        def load(iterator: Iterator[String]): Int = {
            val x = iterator.next
            return x.toInt
        }
    }

    implicit object StringLoadableStatic extends LoadableStatic[String] {
        def load(iterator: Iterator[String]): String = {
            val x = iterator.next
            return x
        }
    }
}
```

実装自体は特に変わった点はありませんが、object文の左に `implicit` がついています。これが `implicit parameter` の探索対象であるという目印になります。本当は kotlinのときのようにトップレベルに定義したかったのですが、仕様上それができないようなので `package` を切っています。

そして使う側は下記のようになります。

```scala
//	scala

object Main {
    import Implicits._

    def main(args: Array[String]) {
        val i = Load.load[Int]("33")
        println(i)
        val s = Load.load[String]("abc")
        println(s)
    }
}
```

`Main` の中に `import` を書いて、先程の `implicit` を探索対象にさせています。 `Load.load` の呼び出しのところでは、何も書いていませんが、先程の `IntLoadableStatic` と `StringLoadableStatic` が第2引数として渡されているのです。見かけ上 `LoadableStatic` を渡さない形でのコードで書くことができました。

これでswiftのときと同じように、余計な事は書かずにジェネリック関数の型 `T` を決定するだけで後は対応した動作をする、という目的が果たせています。swiftの場合を再掲します。

```swift
//	swift

	let i: Int = load(string: "33")
	print(i)
	let s: String = load(string: "abc")
	print(s)
```

ところでwiftの時は、ジェネリック関数の型アノテーションができないという都合上、左辺の変数の型を定義して推論させました。今回のscalaの場合は、 `load` の型アノテーションとして型を指定しており、逆に左辺値の型は右辺値から推論させています。実はこれをswiftのときのように左辺値に指定するようにすると、 `implicit parameter` の探索がうまくいかなくなります。Scalaの型推論の仕様でそのような事になっています。なので、swiftは左側必須、scalaが右側必須という逆の縛りが生まれているのが面白いです。ちなみにScalaも基本的には返り値からの推論は効くのですが、このような `implicit` とジェネリクスの組み合わせの場合に制限があるという話です。

さて、リストのデコーダを作ります。これまでのように定義すると下記となります。

```scala
//	scala

    class ListLoadableStatic[T](elementStatic: LoadableStatic[T]) extends LoadableStatic[List[T]] {
        def load(iterator: Iterator[String]): List[T] = {
            val n = Load.load[Int](iterator)
            var ret = List[T]()
            for (i <- 0 until n) {
                val x = Load.load[T](iterator)(elementStatic)
                ret = ret :+ x
            }
            return ret
        }
    }
```

しかしここで問題が生じます。このクラスはこれまでの如く引数に要素型の `LoadableStatic` が必要です。そのような場合でも `implicit` に解決できるのでしょうか。ここでは以下のように補助関数を定義します。

```scala
//	scala

    implicit def createListLoadableStatic[T](implicit elementStatic: LoadableStatic[T]): LoadableStatic[List[T]] = {
        return new ListLoadableStatic[T](elementStatic)
    }
```

そうです、 `implicit` な引数を取る、それ自身が `implicit` な関数を定義するのです。こうすると、パラメータのところにこの関数の呼び出しが埋め込まれ、さらにこの関数の呼び出しのところには要素型の `LoadableStatic` が埋め込まれるように探索されるのです。実際に呼び出し例を見てみます。

```scala
//	scala

        val a = Load.load[List[String]]("3/apple/banana/cherry")
        println(a)
```

このように推論させる事ができました。 `List[String]` という入れ子の型にも、特別な記述無しで対応できています。

最後に、 `Employee` と `Company` を見ていきましょう。

```scala
//	scala

    implicit object EmployeeLoadableStatic extends LoadableStatic[Employee] {
        def load(iterator: Iterator[String]): Employee = {
            return new Employee(
                Load.load[String](iterator),
                Load.load[Int](iterator)
                )
        }
    }

    implicit object CompanyLoadableStatic extends LoadableStatic[Company] {
        def load(iterator: Iterator[String]): Company = {
            return new Company(
                Load.load[String](iterator),
                Load.load[List[Employee]](iterator))
        }
    }
```

同じように実装します。実装自体は特に変わった点はありません。しかしswiftとの対比でみると、 `Load.load` に型アノテーションを渡している点が少し違います。swiftの場合を再掲します。

```swift
//	siwft

extension Employee: Loadable {
	static func load(iterator: AnyIterator<String>) -> Employee {
		return Employee(
			name: Main.load(iterator: iterator),
			age: Main.load(iterator: iterator)
			)
	}
}

extension Company: Loadable {
	static func load(iterator: AnyIterator<String>) -> Company {
		return Company(
			name: Main.load(iterator: iterator),
			employees: Main.load(iterator: iterator))
	}
}
```

swiftの場合はこのように、型指定をしないで記述できています。 これはswiftは返り値からの型推論が効くために型が省略できたのに対して、Scalaはそれができないために型を書かなければならないことによる違いです。

scalaでの呼び出しは下記のようになります。

```scala
//	scala

        val c = Load.load[Company]("CatWorld/3/tama/5/mike/6/kuro/7")
        println(c)
```

いかがでしたでしょうか。scalaでは `implicit parameter` という言語機能によって、`T` についての振る舞いを定義するオブジェクトを渡す部分をコンパイラに自動的に任せる事ができました。このような事ができるために、scalaの `implicit parameter` が型クラスのようなものだと言われています。起きている事を抽象的に捉えなおしてみれば、ある型に対して専用の実装を用意する、という動作のディスパッチを、コンパイル時に決定するジェネリックな型 `T` に応じて切り替える事ができる、そして、その動作を提供するオブジェクトを `T` の具象化先の型ごとに用意しておく事で、動作を用意している型としていない型を制御する、という事が起きています。これは、型クラスによって定義されるメソッドが、今回のようなジェネリックな関数に対応していて、ある型をある型クラスのインスタンスにする、という事が、その型で特殊化されたオブジェクトを定義すると同時に動作ロジックを記述する、という事と対応しています。そして、型がある型クラスのインスタンスであるかどうかの判定が、具象化されたオブジェクトが定義されているかどうかの判定と対応しています。これらがコンパイル時に静的にチェックできて、コードの見た目には見えない形で行われるので、型クラスのようなもの、と考えられるわけです。なお捕捉ですが、この一緒に渡す振る舞い用のオブジェクト自体は、今回は `T` を返すメソッドでしたが、引数として `T` を受け取るメソッドも作れるわけなので、staticメソッドを注入する今回のようなパターンにかぎらず、既存の型 `T` に対してある名前で規定される機能の振る舞いを追加するといった形で使えます。

# SwiftとScalaの比較

SwiftとScalaはどちらも良い感じに実装する事ができました。ここではSwiftとScalaの違いを整理します。

Scalaでは `LoadableStatic` の存在は見た目に隠蔽する事ができました。しかし、型パラメータを指定の必要については少しずつswiftとscalaで仕様が違っていました。これを整理すると下記のような事がありました。

- 初期化代入文では、Swiftでは左辺、Scalaでは右辺の型指定が必要 
- 関数呼び出し部分ではSwiftは不要、Scalaでは型指定が必要
- SwiftはArray用の `load` の実装が必要、Scalaでは汎用の `load` で全て対応

まず1つ目については、原理上左にせよ右にせよ、何かしら1箇所は指定が必要な場面です。どちらも無かったら何をデコードすれば良いか決められません。そして左と右は同じように思えます。しかし、これが初期化代入文ではなく、変数への再代入文だった場合を考えてみると、swiftは型指定を無くす事ができます。その点では左側指定の仕様の方が便利かもしれません。しかし、変数より定数を初期化代入する事のほうが一般に多いので、あまり変わらないでしょう。

2つ目については、Swiftのほうが便利だと思います。関数を呼び出しているところでは、その関数の名前を書くという行為によって、その関数の引数部分の型をプログラマが暗黙に指定している事になります。なので、Scalaの仕様では、既に指定している事を改めて書き下していて無駄な手間がかかってしまいます。

3つ目についてはScalaの方が綺麗です。そもそもSwiftでこのような事になってしまったのは、「配列の要素の型がデコード可能なときだけその配列自体がデコード可能である」という条件付きのプロトコル適用が書けなかったからでした。一方Scalaの場合は、配列のデコーダオブジェクトを探索する時に、それを返す関数の引数として要素のデコーダオブジェクトを探索する、という形で、きちんと要素の型の判定に依存して、配列の型の判定が連動するようになっています。この点ではScalaの方が完全に優れています。

そんな感じで型指定周りについては一長一短です。幸いSwiftが劣っている3つ目の点については今後の対応が予定されているので、そこが対応されれば、Swiftはかなり良いと言えるようになりそうです。

また、利用側の型指定ではなく、デコーダの実装側の方式も結構違っています。

- Swiftは `Loadable` という `protocol` に `load` staticメソッド制約を書き、それを拡張メソッドとして注入する
- Scalaは `LoadableStatic` という `trait` に `load` メソッド制約を書き、 `LoadableStatic` を実装したオブジェクトを `implicit` つきで定義する

という形になっています。やりたいことは 「`T` をデコードしたい」であると考えると、staticメソッド制約のあるプロトコルを書いて、 `T` にそれを適用させる、というswiftのやり方はかなり自然でわかりやすいと思います。もちろんこのプロトコルはインスタンスメソッドについても制約できるので、型 `T` とそのプロトコル適用、という登場人物だけで、インスタンスメソッドの話もstaticメソッドの話も同じように取り扱えます。一方Scalaやkotlinのやり方だと、 `T` 自体には手を入れず、 `T` に対応したそれぞれの `LoadableStatic` クラスのサブクラスとそのインスタンスを用意するところが、間接的な定義になっていてわかりにくいと思います。

Scalaのような間接的なやり方が必要になるのは、インターフェースにインスタンスメソッドしか書けない、という事が主な理由だと思います。その結果、型だけで何かをすることができず、必ずその型のインスタンスがあって初めて何かする、という形になっています。それに対して、Swiftが static制約を拡張した事は、逆に何かおかしな事にならないのか考えてみました。まず、インスタンスメソッドというものは単に見えない第0引数としての `this` があるだけのstaticメソッドと考える事ができます。仮想関数テーブルのディスパッチについてはそのstaticメソッドの突入直後に、第0引数にもとづいて行う処理であって、メソッド呼び出しのエントリ自体は単に引数がメソッド呼び出しのドットの左側にあるだけです。ということは、そもそもインスタンスメソッドの制約を書く機能は、第0引数が強制されたstaticメソッドだけがかける機能だったと考えることができて、その制約を取り払うことでstaticメソッドもかけるようになる、というのは自然な一般化のように思えます。



# Swiftの裏側

Swiftではこのような `load<T>`　の `T` に対するstaticメソッド呼び出しをするコードは、実際に具象化された `T` のコードが生成されます。つまり、実行時には例えば型 `Int` のオブジェクトを渡して `Int` のメソッドを呼び出す、という形ではなくて、初めから `Int` の時の場合の関数をコンパイルしておいて、そこには直接 `Int` のメソッドを呼び出すコードが書かれているのです。しかし一方で、Swiftは外部モジュールに対してこのようなジェネリック関数を公開した場合、呼び出し側は関数を具象化して再コンパイルすることができませんから、その時のように本当に型 `T` を受け取る柔軟なバージョンのコードも生成するはずです。それを今回のコードをコンパイルしてLLVM-IRを見て確認してみましょう。下記コマンドでコンパイルして、最適化した版のLLVM-IRを出力します。

```
$ swiftc -emit-ir -O -module-name Main swift.swift
```

なお、SILでなくLLVM-IRを一気に見に行くのは、SILの段階だとまだ型パラメータは型パラメータとして表現されているために、実際の関数のエントリポイントのABIがどうなっているかはわからないからです。ただ、シンボル名のヒントとしてSILでの出力は参考になるので、実際にはSILも並行して参照します。

さて、SILを探すと下記が見つかります。

```
// load<A where ...> (iterator : AnyIterator<String>) -> A
sil hidden @_TF4Main4loaduRxS_8Loadablexzwx6LoadedrFT8iteratorGVs11AnyIteratorSS__x : $@convention(thin) <T where T : Loadable, T.Loaded == T> (@owned AnyIterator<String>) -> @out T {
	...
}
```

コメントがついているのでジェネリックな `T` 用の `load` だとわかります。本文は省略しました。ここから、関数名をコピペしてIRを見ると、下記が見つかります。

```
define hidden void @_TF4Main4loaduRxS_8Loadablexzwx6LoadedrFT8iteratorGVs11AnyIteratorSS__x(%swift.opaque* noalias nocapture sret, %GCs19_AnyIteratorBoxBaseSS_*, %swift.type* %T, i8** %T.Loadable) #0 {
entry:
  %2 = getelementptr inbounds i8*, i8** %T.Loadable, i64 1
  %3 = bitcast i8** %2 to void (%swift.opaque*, %GCs19_AnyIteratorBoxBaseSS_*, %swift.type*, %swift.type*, i8**)**
  %4 = load void (%swift.opaque*, %GCs19_AnyIteratorBoxBaseSS_*, %swift.type*, %swift.type*, i8**)*, void (%swift.opaque*, %GCs19_AnyIteratorBoxBaseSS_*, %swift.type*, %swift.type*, i8**)** %3, align 8, !invariant.load !29
  tail call void %4(%swift.opaque* noalias nocapture sret %0, %GCs19_AnyIteratorBoxBaseSS_* %1, %swift.type* %T, %swift.type* %T, i8** %T.Loadable)
  ret void
}
```

これが気になっているバイナリなので詳しく見ていきます。

第1引数は 名前が `%0`、型が `%swift.opaque* noalias nocapture sret` です。 `noalias`、`nocapture`、`sret` はヒントなので、要するに `%swift.opaque` へのポインタです。関数自体の返り値の型が `void` になっていることもポイントで、結局これは返り値を参照渡しで受け取るための引数です。今回は返り値の型も `T` というジェネリックな型なので、オペイクポインタで戻すわけですね。

第2引数は 名前が `%1`、型が `%GCs19_AnyIteratorBoxBaseSS_*` です。 `AnyIterator` へのポインタですね。 `BoxBase` とかは `AnyIterator` の実装上の型名とかの名残でしょう。

第3引数は 名前が `%T`、型が `%swift.type*` です。 `%swift.type` へのポインタ、つまり具象化された `T` の型オブジェクトですね。

第4引数は 名前が `%T.Loadable`、型が `i8**` です。要するに何かのポインタなんですが、結論としてはこれは具象化された `T` 
が プロトコル `Loadable` に準拠するための、protocol witness tableへのポインタだと推測できます。ここだけで論理的に結論するのは無理ですが、過去に [ここらへんを調べた記事](http://qiita.com/omochimetaru/items/b41e7699ea25a324aefa) を書いているので気になる人は見てください。

関数本体ですが、witness tableから関数ポインタを取ってきて呼び出しているだけです。詳しく見ていきます。

まず、 `%2` に対して `%T.Loadable` からエントリをロードしています。

```
  %2 = getelementptr inbounds i8*, i8** %T.Loadable, i64 1
```

この末尾の `1` が要素のオフセットですが、SILの方を見ると何が入っているかわかります。

```
sil_witness_table hidden Int: Loadable module Main {
  associated_type Loaded: Int
  method #Loadable.load!1: @_TTWSi4Main8LoadableS_ZFS0_4loadfT8iteratorGVs11AnyIteratorSS__wx6Loaded	// protocol witness for static Loadable.load(iterator : AnyIterator<String>) -> A.Loaded in conformance Int
}
```

例えばこれは `Int` を `Loadable` に適用させるための関数テーブルです。 `Loadble.load` として `_TTWSi4Main8LoadableS_ZFS0_4loadfT8iteratorGVs11AnyIteratorSS__wx6Loaded` が入っているので、このシンボルをIRから探します。すると下記が見つかります。

```
@_TWPSi4Main8LoadableS_ = 
	hidden constant [2 x i8*] [
		i8* bitcast (
			%swift.type* ()* 
			@_TMaSi 
			to i8*
		), 
		i8* bitcast (
			void (%Si*, %GCs19_AnyIteratorBoxBaseSS_*, %swift.type*, %swift.type*, i8**)* @_TTWSi4Main8LoadableS_ZFS0_4loadfT8iteratorGVs11AnyIteratorSS__wx6Loaded 
			to i8*
		)
	], align 8
```

見やすいようにインデントしていますが、さっきのシンボルだった関数ポインタが、 `i8*` にキャストされた上で、 `_TWPSi4Main8LoadableS_`　の第1要素として格納されている事がわかります。

テーブルの第0要素は `@_TMaSi` という関数ですが、これを `swift-demangle` すると `@type metadata accessor for Swift.Int()` であるとわかります。ようするに、このテーブルは `Int` が `Loadable` として振る舞うときのテーブルですが、その `Int` のテーブルであるという事が逆引きできるようになっていますね。そして第1要素が、 `Int` が `Loadable` として振る舞うときのロジックが書かれた関数なわけです。

`Int` にかぎらず他の型であっても同じレイアウトなので、 `Loadble.load` の型ごとの実装を取ってきているとわかります。実際には `getelementptr` はその要素のポインタを返すので、今 `%2` は関数ポインタが書かれたメモリへのポインタなわけです。

次にそれを関数ポインタのポインタにキャストして `%3` に保持します。

```
  %3 = bitcast i8** %2 to void (%swift.opaque*, %GCs19_AnyIteratorBoxBaseSS_*, %swift.type*, %swift.type*, i8**)**
```

これはさっき見た `_TTWSi4Main8LoadableS_ZFS0_4loadfT8iteratorGVs11AnyIteratorSS__wx6Loaded` の型と一致していますね。で、 `load` 命令でポインタをデリファレンスして関数ポインタを `%4` に得ます。

```
  %4 = load void (%swift.opaque*, %GCs19_AnyIteratorBoxBaseSS_*, %swift.type*, %swift.type*, i8**)*, void (%swift.opaque*, %GCs19_AnyIteratorBoxBaseSS_*, %swift.type*, %swift.type*, i8**)** %3, align 8, !invariant.load !29
```

これも長い行ですが、 `load <関数ポインタの型> <関数ポインタの型 *> %3, align 8, !invariant.load !29` と書いてあるだけですね。

次の行でいよいよ関数を呼び出します。

```
	tail call void %4(
		%swift.opaque* noalias nocapture sret %0, 
		%GCs19_AnyIteratorBoxBaseSS_* %1, 
		%swift.type* %T, 
		%swift.type* %T, 
		i8** %T.Loadable)
```

ここもインデントを入れましたが、これまでに出てきた変数を転送しているだけです。 `%0` は返り値用のポインタ、 `%1` は `AnyIterator`、 第3,4 引数は `%T`、第5引数は `%T.Loadable` です。

というわけで、 `T` が汎用的な版の `load` の呼び出しでは、 型 `T` の型のオブジェクトと、その型が `Loadable` 型として振る舞うときのための関数テーブル、つまり動作が渡されている事が確認できました。

よく考えてみると、これは Scala や Kotlin で書いてきたコードと同じ形をしています。`LoadableStatic` のインスタンスを渡していたのは、型によって動作を切り分けるためで、実際、`LoadableStatic` としてアップキャストされた `IntLoadableStatic` などのオブジェクトは、仮想関数テーブルを通して固有の処理を呼び出し側に与えるわけです。つまり、仮想関数テーブルに定義された動作を渡すための媒介としてインスタンスを使っているような状態になっていたわけです。これはSwiftのコンパイル後の状態と全く同じです。Scalaの実装においては、その `LoadableStatic` を自前でScalaコードとして定義して、それを引数に渡す処理は自動でやってくれたわけです。同じようにSwiftは、 `IntLoadableStatic` に相当する `Int` の `Loadable` メソッドテーブルを定義して、それを渡す処理を自動で出力してくれていたわけです。つまり、以下のようになります。

- Kotlinでは、関数テーブルの定義を手動で書いて、テーブルを渡す処理を手動で書いた。
- Scalaでは、関数テーブルの定義を手動で書いて、テーブルを渡す処理を自動で書いた。
- Swiftでは、関数テーブルの定義を自動で書いて、テーブルを渡す処理も自動で書いた。

このように、コンパイラがやってくれる領域が広くなっているのです。結局どの方式も同じしくみでできていて、よりコンパイラが自動で引き受けてくれているのがSwiftでの実装の形だったと言えます。その上でSwiftは、最適化の形として、具象化したバージョンのコードを生成し、内部リンクにおいてはその高速な版を呼び出す、という動作をしています。(Scalaも渡すところでは静的に確定しているので、具象化して展開している可能性はあります)



# 他の方法; suffix方式

別のアプローチとして、 `load` 関数にデコードしたい型名に対応する suffix を付けた版をどんどん作り足していくアプローチが考えられます。例えば、 `loadInt`, `loadString`, `loadCompany` といった関数を、デコードしたい型 `T` に合わせて作り足していく方式です。

この方が一見、 `load(iterator, IntLoadableStatic)` などと書くより、 `loadInt(iterator)` とかけるため、短くて良いと考える人もいるかもしれません。しかし僕はこの方式はあまり良いと思っていません。それは以下の2つの理由によります。

- 名前が衝突する
- 新しい型の追加時に何をすべきかが表明されていない

まず1つ目の理由は名前が衝突するというものです。例えばVRアプリを作ろうとしたとして、画像処理ライブラリとゲームエンジンライブラリを組み合わせて使ったとします。画像処理ライブラリの中に `Vector3` という型があったとします。これに応じて `loadVector3` を実装したとします。しかし、ゲームエンジンライブラリの中にも `Vector3` という型があったとします。この時点で `loadVector3` が2つ必要になってしまって定義できません。そういうとき、ライブラリプレフィックスを付け足して、 `loadImgVector3` と `loadGeVector3` みたいにやったとして、じゃあゲームエンジンの中の `Texture` 型のデコーダーにも `Ge` を付けるのかどうかとか、そもそも `Ge` じゃわかりにくいので `GameEngine` にしなければ、いやいやそれでは呼び出しが長ったらしくなってしまう、と、うまくいきません。

しかし、Swiftでのやり方の場合なら、それぞれの `Vector3` に対する拡張として処理を書くため、名前がぶつかることはありません。そして、片方のライブラリにしかアクセスしていない箇所からは、 `Vector3` という短い名前を使うことができます。両方のライブラリにアクセスしている箇所でのみ、 `Imaging.Vector3` と `GameEngine.Vector3` という長い名前を使う事になります。このように、必要な時は明確な長い名前、不要な時は簡潔な短い名前を使い分けることができるので、型にデコーダ機能を仕込むアプローチの方が、整理されるのです。

これはKotlin等での場合でも同じで、 `LoadableStatic` のサブクラスを定義するパッケージを分けておくことで、不要に長い名前になることを防げます。

2つ目の理由は、新しい方の追加時に何をすべきかが表明されていないという事です。仮に `loadVector3` の実装を忘れていたとしても、これを呼び出したい、つまり、 `Vector3` をデコードしたいというときがくるまで、その実装が漏れている事に気がつけません。 `Vector3` のような簡単な型ならまだしも、もっと複雑な型であったなら、実装が必要になったそのときにはそれを実装する時間が無かったり、他の人が作った型で詳細が不明だったりするかもしれません。

その点、Swiftでのやり方の場合なら、新しい型の追加時には、その型を準拠させるべきプロトコルがある、という事だけ注意すれば、実装が漏れていればコンパイルが通らないのでそのような心配がありません。例えばもし、 `Loadable` とは別に `Savable` といった保存用の機能も必要になったりしたとしても、最初から、 `protocol ModelClass : Loadable` のように定義しておいて、新しい型は `ModelClass` に準拠するように作っておけば、 `protocol ModelClass : Loadable, Savable` と、元々のプロトコルに追加してやるだけで、 `save` が未実装である箇所がコンパイラによって一括で検出できます。

ただ、Kotlin等の場合は、 `LoadableStatic` の実装が漏れるという心配はあります。そこで、機能上の意味はないですが、 `interface ModelClass<T> { fun assertImplementLoadable(): LoadableStatic<T> }` のように、インスタンスから `LoadableStatic` を取得するメソッドを定義しておいて、コンパイラにチェックさせるというテクニックを使うと良いかもしれません。



# 動的言語の場合

これまでの実装方式を、もし型検査の無い言語においても考えたらどうなるでしょうか。基本的には Kotlin の場合と同じ状況になります。動的言語はクラス自体への動的注入ができることが多いので、 TypeScript に近くなるかもしれません。そうすると、 `load(iterator, Company)` にように書くことになります。なので、デコーダを使うときの手間はあまりかわりません。型解決による仕組みは使えないので、SwiftやScalaのような状態にはできません。そして、Kotlinとの違いとしては、この `load` に与えた `LoadableStatic` の型が間違っていた時に、それが検知されないという点にあります。例えば、 `Company` のデコーダでは、 `load(iterator, ListLoadableStatic(Employee))` などと書く必要がありますが、間違って、 `load(iterator, ListLoadableStatic(String))` などと書いていてると、バグってしまいます。これを静的に検出できないというのが動的言語のときに不利な点です。



# 他の方式; マクロやメタプログラミング、リフレクション

マクロやメタプログラミング、リフレクション(以下マクロと総称)などの方式を使えば、Swiftの場合よりもさらに短くできます。というか、クラスにアノテーションを指定するだけ、みたいにできます。この方式自体は良いと思いますし、バンバン使えばいいと思いますが、マクロを使わなかった場合においても簡潔に実装できる状態にしておくことが大事だと思います。なぜなら、この手の方式は一切何も書かないわけですが、なんらかの例外的なケースによって、そこだけ動作を変えたいといった要求が出てきた場合に、マクロにパラメータ設定を追加するとかなってくると、よくわからないDSL状態になっていくからです。具体的な例としては、 JSONでAPIを組んでいたとして、しかしサーバーがアップグレードしてAPIの仕様が変更するから、このキーだけは無かった場合でもエラーとしないで特定のデフォルト値で埋めてデコードしたい、などが考えられます。そういう場合においては、そのクラスだけはマクロの処理対象から外した上で、言語上で手書きする、としたいわけです。その時に、マクロを使わない場合での直接記述が難解だったりすると、そこで実装するのが大変です。なので、出来る限り本体言語上で簡潔に実装できる仕組みを整えてあるうえで、マクロによってその簡潔なコードを自動生成する、というのが理想です。その観点において、マクロが無かった場合の実装方法を改善する議論は有意義です。



# C++での実装

バージョン: `Apple LLVM version 8.0.0 (clang-800.0.42.1)`

最後にC++での実装を示します。なぜこれだけ最後に持ってきたかというと、C++はあまりに異質であるため、分けて考えたほうが良いからです。この手の型に絡んだ言語機能を考える時、テンプレート機能をもつC++はだいたい異質な存在です。Javaより前から存在しているのに、できることだけは非常に先端的だったりします。今回もそうです。

まず `Iterator` とか文字列操作のメソッドを適当に用意します。

```cpp
std::string Format(const char * format, ...);
std::string FormatV(const char * format, va_list ap);
std::vector<std::string> Split(const std::string & string, const std::string & separator);
template <typename T> 
class Iterator {
public:
	Iterator(const std::vector<T> & array): 
		index_(0), array_(array) {}
	int index_;
	std::vector<T> array_;
	T next() {
		auto ret = array_[index_];
		index_ += 1;
		return ret;
	}
};

std::string Format(const char * format, ...) {
    va_list ap;
    va_start(ap, format);
    std::string ret = FormatV(format, ap);
    va_end(ap);
    return ret;
}

std::string FormatV(const char * format, va_list ap) {
    va_list ap2;
    va_copy(ap2, ap);
    int size = vsnprintf(nullptr, 0, format, ap2) + 1;
    va_end(ap2);
    std::vector<char> buf(size);
    vsnprintf(buf.data(), size, format, ap);
    return std::string(buf.data());
}

std::vector<std::string> Split(const std::string & string, const std::string & separator) {
	std::vector<std::string> ret;
	if (string.size() == 0) {
		return ret;
	}
	std::size_t pos = 0;
	while (true) {
		std::size_t found_pos = string.find(separator, pos);
		if (found_pos == std::string::npos) {
			break;
		}
		ret.push_back(string.substr(pos, found_pos - pos));
		pos = found_pos + separator.size();
	}
	ret.push_back(string.substr(pos, string.size() - pos));
	return ret;
}
```

さて、モデルの型を定義します。

```cpp
//	cpp

class Employee {
public:
	Employee(const std::string & name, int age): name_(name), age_(age) {}
	std::string name_;
	int age_;
	std::string ToString() {
		return Format("name=%s, age=%d", name_.c_str(), age_);
	}
};

class Company {
public:
	Company(const std::string & name, const std::vector<Employee> & employees):
	name_(name), employees_(employees) {}
	std::string name_;
	std::vector<Employee> employees_;
	std::string ToString() {
		std::string str;
		str = Format("name=%s, employees=", name_.c_str());
		for (auto & e : employees_) {
			str += Format("%s, ", e.ToString().c_str());
		}
		return str;
	}
};
```

ここまではだいたい今までと一緒です。さて、これまでならプロトコルやインターフェースを定義するところですが、C++ではそのような制約を記述する構文がありません。C++のテンプレートは、型制約と一緒に扱うジェネリクス系の機能とは異なり、とりあえずテンプレートを展開してみてからコンパイルできるかどうかで考える、という感じです。ただ、その際に今回のように型によって動作を制御するためには、そもそもテンプレートを展開するかどうかを型によって切り替える仕掛けを仕込みます。そのために、型 `T` がstaticメソッド `load` をもつかどうかを判定する型をつくります。そして、いったん全ての型が無効であるとしておきます。

```cpp
//	cpp

template <typename T>
struct IsLoadable {
	enum { value = false };
};

template <typename T> 
T load(const std::string & string) {
	auto tokens = Split(string, "/");
	auto iterator = Iterator<std::string>(tokens);
	return load<T>(iterator);
}

template <typename T>
T load(Iterator<std::string> & iterator,
	typename std::enable_if<IsLoadable<T>::value>::type * enabler = nullptr) 
{
	return T::load(iterator);
}
```

3つの要素を定義しています。1つ目の `IsLoadable` という型は、渡された型が `T::load` を呼び出せるかどうかを判定する型です。といっても、型の定義を読み取ってチェックするといったしかけは無く、とりあえず全て `false` となっています。これについては後で使い方を説明します。2つ目の `load` は従来通りの文字列を受け取るものです。そして3つ目の `load` がポイントで、 `std::enable_if` と `IsLoadable` を組み合わせることで、 `IsLoadable` の結果が `true` のときだけ、このメソッドがテンプレート展開されるようにしています。

さて次に `int` と `std::string` のデコーダを作ります。しかし、 `int` は組み込み型のためstaticメソッドを持っていません。そのため、このまま `T::load` のコードに展開させても意味がありません。 `int` と `std::string` については、それぞれ専用の実装が展開されるようにします。

```cpp
//	cpp

template <typename T>
T load(Iterator<std::string> & iterator,
	typename std::enable_if<std::is_same<T, int>::value>::type * enabler = nullptr) 
{
	auto x = iterator.next();
	return std::stoi(x);
}

template <typename T>
T load(Iterator<std::string> & iterator,
	typename std::enable_if<std::is_same<T, std::string>::value>::type * enabler = nullptr) 
{
	auto x = iterator.next();
	return x;
}
```

このように、 `std::enable_if` と `std::is_same` を用いることで、 `T` の型が `int` の時と `std::string` の時だけ展開されるテンプレート関数を定義しています。呼び出しは下記のようになります。

```cpp
//	cpp

	auto i = load<int>("33");
	printf("%d\n", i);

	auto s = load<std::string>("abc");
	printf("%s\n", s.c_str());
```

良い感じです。

次に、要素の型が `std::vector` の場合のときの処理を用意します。今までのように `std::is_same` で判定できれば良いのですが、 `std::vector` はそれ自体型パラメータを持っており、その部分を柔軟に判定するためには工夫が必要です。ここではそのために、型が `std::vector` であるかどうかを判定するための型、 `IsVector` を定義します。

```cpp
//	cpp

template <typename T>
struct IsVector {
    enum { value = false };
};

template <typename T, typename A>
struct IsVector<std::vector<T, A>> {
    enum { value = true };
};
```

このように、 `IsLoadable` と同じように定義しつつ、 `std::vector` については `IsLoadable` を特殊化して `value = true` にします。そして以下のように定義します。

```cpp
//	cpp

template <typename T>
T load(Iterator<std::string> & iterator, 
	typename std::enable_if<IsVector<T>::value>::type * enabler = nullptr) 
{
	auto n = load<int>(iterator);
	auto ret = std::vector<typename T::value_type>();
	for (int i = 0; i < n; i++) {
		ret.push_back(load<typename T::value_type>(iterator));
	}
	return ret;
}
```

`std::enable_if` と `IsVector` を組み合わせることで、 `T` が `std::vector` であるかどうかを判定します。このコードの中においては、 `T` が `std::vector` なので、要素の型は `T::value_type` でアクセスできます。

以下のように呼び出せます。

```cpp
//	cpp

	auto a = load<std::vector<std::string>>("3/apple/banana/cherry");
	for (auto & ax : a) {
		printf("%s\n", ax.c_str());
	}
```

さて、この筋でいけば `Employee` と `Company` についても、 `int` と `std::string` と同じように注入する形で実装できてしまうので、こいつらにstaticメソッドを書いた場合の書き方でやってみます。

```cpp
//	cpp

class Employee {
	...

	static Employee load(Iterator<std::string> & iterator);
};

class Company {
	...

	static Company load(Iterator<std::string> & iterator);
};

template <>
struct IsLoadable<Employee> {
	enum { value = true };
};

template<>
struct IsLoadable<Company> {
	enum { value = true };
};

Employee Employee::load(Iterator<std::string> & iterator) {
	auto name = ::load<std::string>(iterator);
	auto age = ::load<int>(iterator);
	return Employee(name, age);
}

Company Company::load(Iterator<std::string> & iterator) {
	auto name = ::load<std::string>(iterator);
	auto employees = ::load<std::vector<Employee>>(iterator);
	return Company(name, employees);
}
```

staticメソッドを定義するところはごく普通です。デコードするフィールドは、C++は関数引数の評価順序の保証が無かった気がするので、いったんローカル変数に代入しています。また、内部で外側の `load` を指定するために `::load` とトップレベルネームスペースを指定しています。そして事前に定義しておいた `IsLoadable` を特殊化して `Employee` と `Company` の場合に `value = true` とする事です。これによって `T::load` の版が展開されるようになります。`T::load` の版が展開された後、コンパイラはそこで具象化された `T` に対して、本当に `load` が定義されているかをチェックします。ここで `load` が無ければコンパイルエラーになります。

呼び出しは下記のとおりです。

```cpp
//	cpp

	auto c = load<Company>("CatWorld/3/tama/5/mike/6/kuro/7");
	printf("%s\n", c.ToString().c_str());
```

C++の実装はこれまでのどれとも違う感じです。実質的に、C++より一つ上のレイヤーで型に関するメタプログラミングをしているようなもので、Swiftが自動でやるような静的な具象化コードの生成を半分プログラマが制御しながらやっている状態といえるでしょう。一方でC++がSwiftとくらべて特徴的なのは、テンプレート展開は常に具象化されるという点です。Swiftは最適化としての具象化版を生成していましたが、C++の場合は、汎用のジェネリック版はそもそも生成されません。必ず具象化されるのです。そのため、C++のテンプレートクラスは、ライブラリ配布する場合はソースが全部公開される必要があり、これは他の言語には無い特徴です。



# ソース

[全てのソースをgithubにアップしてあります。](https://github.com/omochi/qiita-static-method)



# 改訂履歴

- 2017/03/01: [norio_nomuraさん](http://qiita.com/norio_nomura)のツッコミを受けて改訂。swiftにおいて、loadArrayを定義する例を、loadの返り値型オーバーロードに変更。
- 2017/02/27: [heqateさん](http://qiita.com/heqate)のツッコミを受けて改訂。kotlinにおいて、具象化された型ごとのジェネリッククラスへのextensionメソッド実装による実装方式について追記。
- 2017/02/24: 投稿




[^1]: [heqateさんがコメント](http://qiita.com/omochimetaru/items/621f1ef62b9798ee5ff5#comment-30bb5ab570b0435c3768) で提示しているように、ジェネリッククラスのエクステンションを、具象化された型パラメータごとに実装する事ができます。しかし、 `create` + `load` のように固定の冗長なメソッド呼び出しが必要なインターフェースになってしまっており使いやすくない上、記事で言語をまたいで統一している設計とも合致しないため、ここではその実装方法を取り上げません。仮にここで使われている具象化の機能を使ってより簡潔な形や記事にそった形にしようとしても、残念ながらどうしてもこのような形になります。その理由は、 `inline` 展開に伴う言語機能上の制約が厳しいからです。例えば、ジェネリックな inline 関数の内部で `LoadableStatic` のインスタンスを作って、 `load` の第2引数に渡そうとしたり、第2引数に束縛したクロージャを戻そうとしたりしても、コンパイル時に汎用の `T` としての妥当性が要求されてエラーになります。




