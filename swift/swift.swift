
// swift -module-name Main swift.swift

import Foundation

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

class Company: CustomStringConvertible {
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

protocol Loadable {
	associatedtype Loaded
	static func load(iterator: AnyIterator<String>) -> Loaded
}

//{}

func load<T: Loadable>(string: String) -> T 
	where T.Loaded == T
{
	let tokens = string.components(separatedBy: "/")
	let iterator = AnyIterator<String>(tokens.makeIterator())
	return load(iterator: iterator)
}

func load<T: Loadable>(iterator: AnyIterator<String>) -> T
	where T.Loaded == T 
{
	return T.load(iterator: iterator)
}

func load<T: Loadable>(string: String) -> [T] 
	where T.Loaded == T 
{
	let tokens = string.components(separatedBy: "/")
	let iterator = AnyIterator<String>(tokens.makeIterator())
	return load(iterator: iterator)
}

func load<T: Loadable>(iterator: AnyIterator<String>) -> [T] 
	where T.Loaded == T 
{
	return Array<T>.load(iterator: iterator)
}

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

extension Array where Element: Loadable,
	Element.Loaded == Element
{
	static func load(iterator: AnyIterator<String>) -> [Element] {
		let n: Int = Main.load(iterator: iterator)
		var ret: [Element] = []
		for _ in 0..<n {
			ret.append(Main.load(iterator: iterator))
		}
		return ret
	}
}

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

class PaperCompany : Company {
}

func main() {
	let i: Int = load(string: "33")
	print(i)
	let s: String = load(string: "abc")
	print(s)

	let a: [String] = load(string: "3/apple/banana/cherry")
	print(a)

	let c: Company = load(string: "CatWorld/3/tama/5/mike/6/kuro/7")
	print(c)
}

main()