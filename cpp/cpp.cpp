//	clang++ -std=c++14 cpp.cpp && ./a.out

#include <cstdio>
#include <vector>
#include <string>
#include <type_traits>

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

class Employee {
public:
	Employee(const std::string & name, int age): name_(name), age_(age) {}
	std::string name_;
	int age_;
	std::string ToString() {
		return Format("name=%s, age=%d", name_.c_str(), age_);
	}

	static Employee load(Iterator<std::string> & iterator);
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

	static Company load(Iterator<std::string> & iterator);
};

template <typename T>
struct IsLoadable {
	enum { value = false };
};

template <>
struct IsLoadable<Employee> {
	enum { value = true };
};

template<>
struct IsLoadable<Company> {
	enum { value = true };
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

template <typename T>
struct IsVector {
    enum { value = false };
};

template <typename T, typename A>
struct IsVector<std::vector<T, A>> {
    enum { value = true };
};

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

int main(int argc, char * argv[]) {
	auto i = load<int>("33");
	printf("%d\n", i);

	auto s = load<std::string>("abc");
	printf("%s\n", s.c_str());

	auto a = load<std::vector<std::string>>("3/apple/banana/cherry");
	for (auto & ax : a) {
		printf("%s\n", ax.c_str());
	}

	auto c = load<Company>("CatWorld/3/tama/5/mike/6/kuro/7");
	printf("%s\n", c.ToString().c_str());
	return 0;
}