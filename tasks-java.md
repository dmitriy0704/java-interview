# Задачи по Java:

## Озон

```java

// Дана строка s. нужно найти первый не повторяющийся 
// символ в строке и вернуть его индекс. Если такого символа нет, вернуть -1

// Пример:
//input: abcbaad
//output: 2

```



## itone

```java
itone #repeat
/**
 * Посчитать сумму элементов списка (более 1 млн. элементов),
 * используя для этого 10 потоков
 */

    public static long sum(List<Long> numbers) {
        final int threads = 10;
        List<List<Long>> subLists = split(numbers, threads);

        // TODO: make it work!

        return ???;
    }

    private static List<List<Long>> split(List<Long> numbers, int parts) {
        int size = numbers.size();
        int subListSize = (int) Math.ceil((double) size / parts);

        List<List<Long>> result = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            int fromIndex = i * subListSize;
            if (fromIndex >= size) {
                result.add(Collections.emptyList());
            } else {
                int toIndex = Math.min((i + 1) * subListSize, size);
                List<Long> sublist = numbers.subList(fromIndex, toIndex);
                result.add(sublist);
            }
        }
        return result;
    }#itone 
Прислать задачу | Подписаться
```





## Тбанк

```java
// Дан массив натуральных чисел, числа могут повторяться. Необходимо выбрать из них k чисел так, чтобы разность максимального и минимального из выбранных была минимальной. Вернуть эту разность.

// [10,100,300,200,1000,20,30] k=3 -> 20

```



## WB

```java

// Необходимо перенести все элементы, равные нулю, в конец массива, сохранив порядок остальных элементов. 

in:  [3, 0, 4, 0, 1]
out: [3, 4, 1, 0, 0]
```




## Альфа банк

```java

// 1) написать, что будет выведено в консоль

public class MainJava {
    
        public static void main(String[] args) {
            List.of("d2", "a2", "b1", "b3", "c")
                    .stream()
                    .map(s -> {
                        System.out.println("map: " + s);
                        return s.toUpperCase();
                    })
                    .anyMatch(s -> {
                        System.out.println("anyMatch: " + s);
                        return s.startsWith("A");
                    });
        }
}
```




## it one

```java

// 1. классика с массивом 
// 2. Как избежать дедлока?

private void move(Account a1, Account a2, int summa) {
    synchronized (a1) {
        synchronized (a2) {
            // Проверки
            a1.money = a1.money + summa;
        }
        a2.money = a2.money + summa;
    }
}
```





## IT ONE

```java

// Что будет выведено на экран?

class Program {
    public static void main(String[] args) {
        try {
            try {
                throw new Exception("a");
            } finally {
                if (true) {
                    throw new IOException("b");
                }
                System.out.println("c");
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (Exception e) {
            System.out.println("d");
            System.out.println(e.getMessage());
        }
    }
}
```




## IT ONE

```java
class Program {
    public static void main(String args[]) {
        // todo: вернуть первый неповторящийся элемент и вывести ответ
        int[] arr = {9, 4, 9, 6, 7, 4, 5};
        System.out.println(firstUnique(arr));
    }
}
```





## Альфабанк

```java

/* Когда будет вызван метод? */
@Service
@Scope("prototype")
public class MyService {

    @PreDestroy
    public void preDestroy(){
        System.out.println("Service was destroyed.");
    }
}
```





## РСХБ

```java

// Что будет выведено на экран?

String s1 = "RSHB intech is cool";
String s2 = new String("RSHB intech is cool");
String s3 = "RSHB intech is cool";
System.out.println(s1 == s2);
System.out.println(s1 == s3);

```





## Диасфот

```java

// Дан массив чисел в котором все числа кроме одного имеют пару(встречаются дважды)
// Найти число, которое встречается только один раз

// <= [1, 0, 3, -2, 9, 9, 1, -2, 0]
// => 3

int find(int[] arr) {

}
```






## Яндекс/ на стажера

```java

/*
* Есть сообщения из соцсети, например:
* "Я работаю в Гугле :-)))"
* 
* Хочется удялить смайлики из сообщений, подпадающие под регулярку ":-\)+|:-\(+" за линейное время. 
* То есть, сделать так:
* "Я работаю в Гугле :-)))" -> "Я работаю в Гугле "
* "везет :-) а я туда собеседование завалил:-((" -> "везет  а я туда
собеседование завалил"
* "лол:)" - >"лол:)"
* "Ааааа!!!!! :-))(())" -> "Ааааа!!!!! (())""
*/

```





## ИнформЗащита

```java 

/*

Даны два массива, содержащие числа от 0 до 9. Эти массивы представляют собой целые неотрицательные числа, разбитые в массив по десятичным разрядам. 

Например:

[1, 5, 2] (число 152)

[4, 2, 6] (число 426)

Нужно написать функцию, которая примет на вход два таких массива, вычислит сумму чисел, представленных массивами, и вернет результат в виде такого же массива:

[5, 7, 8] (число 578)

Числа, которые представлены массивами, могут быть любыми, в том числе очень большими (тысячи разрядов = элементов массива).

Импортировать другие классы нельзя.
*/

```




## Инсайрес

```java

Требования:
Создать консольное приложение со следующей функциональностью:
- предлагает ввести размер массива сообщением "введите размер массива:"
- инициализирует целочисленный массив с заданным размером случайными значениями
- выводит массив в консоль с заголовком "инициализированный массив:"
- сортирует массив от меньшего к большему методом пузырька
- выводит массив в консоль с заголовком "отсортированный массив:"
Требование к коду:
- код должен соответствовать принципу единой ответственности

Задание: 
- уточнить требования если необходимо
- оценить время выполнения задания
- написать и продемонстрировать код
```





## Сбер

```java

// Какая сложность для операций?

public class HashmapTimeComplexity {

public static void main(String[] args) {
    Map<KeyMap, String> msg = new HashMap<>();
    KeyMap key = new KeyMap(200, "foo");


    //в map добавляются 1...N разных элементов
    //какая сложность алгоритма добавления ключа ниже
    map.put(key, "123");
    map.remove(key);
    map.get(key);

    }
}
record KeyMap(int first, String second) { 

    @Override
    public int hashCode() {
        return 42;
    }
}#sber | Подписаться
```





## It one

```java


//Даны два бина. При выполнении третьей итерации в методе m1 возникло исключение. Сколько записей будет в БД?
 
public class BeanA() {
    
    private BeanB b;
 
    @Transactional
    public void m1(List<Integer> list) {
        for (Integer i : list) {
            b.m2(i);
        }
    }
}
 
 
public class BeanB() {
    @Transactional
    public void m2(Integer i) {
        // Добавляем запись в таблицу
    }
}
```




## Сбер

```java

Написать счетчик обратного отсчета от заданного числа до нуля, который выводит каждое число с паузой в 1 секунду
#sber| Прислать задачу | Подписаться
```





## Иннотех

```java
 
// Найти самое часто повторяющееся число в массиве.
         
Например, [16, 9, 2, 2, 5, 2, 1]. Ответ - 2

```





## Vadarod (беларусь)

```java

/*
Посчитать кол-во повторяющихся чисел в массиве (1, 2, 3, 1, 5, 6, 1, 3 )
Результат в виде (1, 3) (2, 1) (3, 2) (5, 1) (6, 1)
*/
public class TaskTwo {

    public static void main(String[] args) {

    }
}
```




## Сбер

```java

// Написать метод удаляющий дубликаты букв

public static String deduplicate(String s) {
	....
	return "Здравствуй, Сбер!";
}

public static void main(String[] args) {
	String input = "ЗЗддррааввссттввууй, Сссббееерр!!";
	String expected = "Здравствуй, Сбер!";
	String deduplicated = deduplicate(input);
	System.out.println(deduplicated);
	System.out.println(expected.equals(deduplicated));
}

```