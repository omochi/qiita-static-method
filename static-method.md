# Swiftの強力な機能であるstaticメソッド制約の紹介と、Scala, Kotlin, TypeScript, Java, C++との比較

# 導入

Swiftにはstaticメソッド制約という機能があります。「staticメソッド制約」は僕の勝手な呼び方で、公式な呼び方は知らないのですが、protocolにstaticメソッドを定義できる機能の事です。Haskellにおける型クラスのような機能であり新しいものではありませんが、クラスベースのデザインをベースにこれを取り込んだSwiftの仕様はなかなか強力であると僕は考えています。

この記事では、架空の要件を考えた上で、それをstaticメソッド制約を活用して実装する例を示します。そして、同等のコードをその他の言語でも似たような形で実装することで、何が起こっているどういう機能なのかをわかりやすくします。

また、同じ目的のプログラムを複数の言語で構成することで、母語の異なるプログラマの方に、他の言語を紹介する効果もあったら良いなと思います。



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

ここで、 `load` の型パラメータ `T` が、 `load` の書かれている代入文の左辺の変数の型により推論されていることがポイントです。

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

ここで、staticメソッドの `load` と、トップレベルの `load` を区別するため、トップレベルのものを `Main.load` として呼び出しています。そのためにコンパイラにソースコードのモジュール名として `Main` を指定しています。

```
$ swift -module-name Main swift.swift
```

デコード方式としては、はじめに整数をデコードしてこれが要素数となり、その要素数の数だけ要素をデコードしています。要素数のデコードにおいては、先程のテストと同様に左辺値での型指定により `Int` をデコードさせます。要素のデコードにおいては、 `ret.append()` の引数の型が、 `ret` の要素の型 `Element` を受け取るようになっているため、 `Element` をデコードするように推論されます。この `Element` というのは、 `Array` にもともと定義されている 要素の型のパラメータ名です。

さて、残念ながら `Array` を `Loadable` にできなかったので、 `load` からこれを呼び出す事ができません。しかたないので、 `loadArray` を定義します。

```swift
//	swift

func loadArray<T: Loadable>(string: String) -> [T] {
	let tokens = string.components(separatedBy: "/")
	let iterator = AnyIterator<String>(tokens.makeIterator())
	return loadArray(iterator: iterator)
}

func loadArray<T: Loadable>(iterator: AnyIterator<String>) -> [T] {
	return Array<T>.load(iterator: iterator)
}
```

テストしてみます。

```swift
//	swift

	let a: [String] = loadArray(string: "3/apple/banana/cherry")
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
			employees: Main.loadArray(iterator: iterator))
	}
}
```

`Array.load` を実装した時と同じように、 `Main.load` がデコードする型のコンストラクタ引数のところに直接書かれているので、型推論器がコンストラクタ引数の型を参照してくれるおかげで、 `Main.load` についての型指定が不要になっています。 しかし、 `Array` 自体は `Loadable` になっていないため、 `employees:` のところは、 `load` ではなく `loadArray` を指定する必要があります。

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

func loadArray<T: Loadable>(string: String) -> [T] 
	where T.Loaded == T 
{ ... }

func loadArray<T: Loadable>(iterator: AnyIterator<String>) -> [T] 
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

swift版の最終状態にあわせて、 `Employee` をdata class、 `Company` をopen classにしています。

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
...

しかしここで困ることになります。swiftの場合は制約をかけた `T` に対して `T.load` という形でstaticメソッド呼び出しを書くことができました。しかし今 `T` に対してstaticメソッドの制約を書くことはできません。肝心の `load` の実体は、 `LoadableStatic` インターフェースに定義されています。インターフェースのメソッドを呼び出すためには、そのインターフェースを満たすオブジェクトのインスタンスメソッドを呼ぶしかありません。 `T` に対応する `LoadableStatic<T>` のインスタンスがなんらかの方法で呼び出せれば良いですが、そのような機能もありません。そうすると結局のところ、 `LoadableStatic<T>` のインスタンスを引数として受け取るしかありません。よって下記のようになります。

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

`load` の第2引数のところで、 `List<String>` を表現しているのがわかります。なお、この場合は swiftの場合と違って `load` 関数だけでよく、 `loadArray` の実装は不要です。 その代わりに、 `ListLoadableStatic` を書かねばならなくなっているわけです。

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


