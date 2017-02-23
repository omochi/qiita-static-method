//	javac Main.java && java Main

import java.util.*;

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

class IntLoadableStatic implements LoadableStatic<Integer> {
	@Override
	public Integer load(Iterator<String> iterator) {
		String x = iterator.next();
		return Integer.parseInt(x);
	}
}

class StringLoadableStatic implements LoadableStatic<String> {
	@Override
	public String load(Iterator<String> iterator) {
		String x = iterator.next();
		return x;
	}
}

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

public class Main {
	public static void main(String[] args) {
		int i = Load.load("33", new IntLoadableStatic());
		println(Integer.toString(i));

		List<String> a = Load.load("3/apple/banana/cherry", new ListLoadableStatic<>(new StringLoadableStatic()));
		println(a.toString());

		Company c = Load.load("CatWorld/3/tama/5/mike/6/kuro/7", new CompanyLoadableStatic());
		println(c.toString());
	}

	public static void println(String s) {
		System.out.println(s);
	}
}