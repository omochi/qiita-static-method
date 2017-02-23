// npm run build && npm start

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

interface NumberConstructor {
	load: (iterator: Iterator<string>) => number;
}

Number.load = (iterator: Iterator<string>): number => {
	const x: string = iterator.next()!!;
	return parseFloat(x);	
};

interface StringConstructor {
	load: (iterator: Iterator<string>) => string;
}

String.load = (iterator: Iterator<string>): string => {
	const x: string = iterator.next()!!;
	return x;
};

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

namespace Employee {
	export let load: (iterator: Iterator<string>) => Employee;
}

Employee.load = (iterator: Iterator<string>): Employee => {
	return new Employee(
		load(iterator, String), 
		load(iterator, Number));
}

namespace Company {
	export let load: (iterator: Iterator<string>) => Company;
}

Company.load = (iterator: Iterator<string>): Company => {
	return new Company(
		load(iterator, String),
		load(iterator, new ArrayLoadableStatic(Employee)));
}

function main() {
	const i = load("33", Number);
	console.log(i);

	const s = load("abc", String);
	console.log(s);

	const a = load("3/apple/banana/cherry", new ArrayLoadableStatic(String));
	console.log(a)

	const e = load("taro/3", Employee);
	console.log(`${e}`);

	const c = load("CatWorld/3/tama/5/mike/6/kuro/7", Company)
	console.log(`${c}`);
}

main();
