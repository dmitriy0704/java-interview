# Задачи по SQL


## Иннотех

```sql
Иннотех #repeat #sql #innotech

Даны две таблицы:
EMPLOYEES
-EMP_ID        | NUMBER PK
-SURNAME       | VARCHAR
-SALARY        | NUMBER
-DEPARTMENT_ID | NUMBER FK

DEPARTMENTS
-DEPARTMENT_ID   | NUMBER PK
-DEPARTMENT_NAME | VARCHAR

Получить список департаментов и среднюю з/п по департаменту, где средняя з/п по департаменту больше 5000.

Ожидаемый рез-т:
DEPARTMENT_NAME    AVG_SALARY
HR                 8000
IT                 12000Прислать задачу | Подписаться
```

## Ozon

```sql

### Есть две таблицы
- tab1  
  id  
  1  
  2  
  3  
  
- tab2  
  id  
  1  
  1  
  2  
  2  

Скажите количество строк в результирующей таблице при  
1. inner join  
2. left join  
3. cross join 

```
------





## Сбер

```sql

-- create
CREATE TABLE Employees (
  id INTEGER PRIMARY KEY,
  name VARCHAR NOT NULL
);

CREATE TABLE EmployeeUNI (
  id INTEGER PRIMARY KEY,
  unique_id INTEGER
);

-- insert
INSERT INTO Employees VALUES (1, 'Alice');
INSERT INTO Employees VALUES (7, 'Bob');
INSERT INTO Employees VALUES (11, 'Meir');
INSERT INTO Employees VALUES (90, 'Winston');
INSERT INTO Employees VALUES (3, 'Jonathan');

INSERT INTO EmployeeUNI VALUES (3, 1);
INSERT INTO EmployeeUNI VALUES (11, 2);
INSERT INTO EmployeeUNI VALUES (90, 3);

-----------------------------------------------------------------------
-- TODO 


-----------------------------------------------------------------------

-- Написать запрос, который выведет unique_id для каждого пользователя.
-- Если для сотрудника нет записи в таблице EmployeeUNI, то вместо unique_id следует вывести null.#sber

```
------





## Сбер

```sql

Вывести имена клиентов, у которых на активных(status = open) счетах больше 10000 
CREATE TABLE  clients
(
    client_id int primary key,
    name      varchar(100),
    manager   varchar(255)
);

CREATE TABLE accounts
(
    account_id     int primary key,
    client_id      int,
    account_number varchar(255),
    balance        decimal(10, 2),
    created_at      date,
    status         varchar(10),
    foreign key (client_id) references clients (client_id)
);
```
-----





## WB

```sql
--  Создать таблицу автор(id, name, age),  книга(id,  title,  author_id), какие есть ограничения? 

-- + Если нужно, таблица связей для отношения многие‑ко‑многим. 
-- Cделать запросы на получение автора по книге, где возраст автора меньше 40. Решить через JOIN.

```





## Иннотех

```sql
Даны две таблицы:
EMPLOYEES
-EMP_ID        | NUMBER PK
-SURNAME       | VARCHAR
-SALARY        | NUMBER
-DEPARTMENT_ID | NUMBER FK

DEPARTMENTS
-DEPARTMENT_ID   | NUMBER PK
-DEPARTMENT_NAME | VARCHAR

Получить список департаментов и среднюю з/п по департаменту, где средняя з/п по департаменту больше 5000.

Ожидаемый рез-т:
DEPARTMENT_NAME    AVG_SALARY
HR                 8000
IT                 12000Прислать задачу | Подписаться
```
---





## Иннотех, втб

```sql

-- Доменная модель компании ООО "Рога и копыта” представлена таблицами Department и Employee. 
-- Необходимо вывести список сотрудников (id, name), которые получают максимальную ЗП в своем отделе.

Department
===
id
name

Employee
===
id
department_id
name
salary
```





## WB

```sql

таблица employee
|  name   |  lang   |
——————————-—————————-
| Nick    | C#      |
| Nick    | SQL     |
| Eva     | Rust    |
| Vika    | SQL     |
| Ivan    | Java   |
| Ivan    | SQL     |

Надо написать запрос который выберет имена сотрудников, которые знают SQL и ещё хотя бы один любой язык. Считать, что один сотрудник — одно имя.
```
---





## unknown company

```sql

Есть две таблицы:
-таблица department с полями
department_id,
department_name.

- таблица employee с полями
employee_id,
first_name,
last_name,
salary,
department_id
1. Написать запрос, который выводит имя, фамилию сотрудника, название отдела и его зарплату
2. Добавить колонку со средней зарплатой по отделу в котором работает сотрудник
3. Вывести по три наиболее оплачиваемых сотрудников из каждого отдела. 

```
---





## ВТБ

```sql

Таблица orders {id, client_id, amount}, таблица client {id, name} - 
Как найти клиентов с самой большой суммой?
```





## IT ONE

```sql

   Найти всех сотрудников, у которых общий размер начислений превышает 20
-- Persons со списком работников
-- id   | name
-- ------------
-- 1  | Petya
-- 2  | Vasya
-- 3  | Kolya

-- Payments с зарплатными начислениями ежемесячно. 
-- id  |  person_id   | amount 
-- -----------------------------
-- 1  | 1        | 10
-- 2  | 1        | 20
-- 3  | 3        | 15#sql

```
---





## Сбер

```sql

-- Какие индексы нужны для этих запросов?

CREATE TABLE products (
    id SERIAL NOT NULL,
    name TEXT(255) NOT NULL,
    price FLOAT NOT NULL,
    creation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN
);

SELECT * FROM products WHERE price > 100 AND creation_time >= CURDATED - IN
SELECT * FROM products WHERE price > 1000;
SELECT * FROM products WHERE creation_time >= CURDATED - INTERVAL 1 WEEK;#sber

```
---





## Сбер

```sql

Вывести всех студентов которые хоть раз сдали экзамен

Students
-id pk
-name varchar
-phone varchar

exams
-id pk
-ref_students fk
-discipline varchar
-mark int not null#sber

```
---





## Unknown company (анонимно, мед. компания) 

```sql

=================
Appointment
=================
id
patient_id
doctor_id
date
=================

=================
Doctor
=================
id
full_name
=================

Нужно найти врачей, которые 2-10-2025 осуществили больше 10 приемов.

Вывести id доктора, его имя, количество приемов.

```
---





## Райффайзенбанк
```sql

-- SQL задача: вывести список сотрудников,
-- получающих заработную плату больше чем у непосредственного руководителя

<EMPLOYEE>
ID NUMBER [PK]
DEPARTMENT_ID NUMBER [FK1] 
CHIEF_ID NUMBER [FK2]
NAME VARCHAR2(198)
SALARY NUMBER

```
---






## Сбер

```sql

CREATE TABLE country (
    id          SERIAL
    name        VARCHAR(100)
    continent   VARCHAR(50)  
    population  INT       
);

CREATE TABLE gdp (
    id          
    country_id  INT
    year        INT 
    value       INT
);
1. Получить континенты и суммарное число жителей на каждом
2. в которых жителей больше миллиарда
3. Получить наименование страны + ввп на душу населения
4. Какие ошибки возникают (нулл и 0)
5. В таблице стран 200 строк в gdp 180, сколько будет строк в запросе (нужно было пояснить за теорию множеств))))
6. Получить континент наименование страны и число жителе в стране. Выводим только самые большие по населению страны на континенте 
7. Тоже самое только топ 3 страны

```
---





## Мойсклад
```sql

Написать  SQL запрос, который выбрал бы папки с файлами*.avi или пустые папки.

CREATE TABLE folder (
    id uuid PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE file (
    id uuid PRIMARY KEY,
    name text NOT NULL,
    folder_id uuid NOT NULL,
    CONSTRAINT fk_file_folder_id FOREIGN KEY (folder_id) REFERENCES folder (id)
);
```
---





## Сбер

```sql

-- Две таблицы
-- Company: id, title, sector
-- Vacancy: id, id_company, name, salary

-- Написать запрос, который выведет список компаний из отрасли IT,
-- со средним доходом по вакансиям, названия которых содержат слово Java, более $1000
```
---





## Инсайрес
```sql
Требования: 
есть 2 связанные таблицы 
1) таблица User; поля Id, Name; 
2) таблица UserTask; поля Id, UserId, Name
Задание:
написать SQL запрос который вернет в обратном алфавитном порядке имена всех пользователей у которых более 5 задач
Условия:
без вложенных селектов#sql
```
---





## Точка банк (стажировка)

```sql

Вывести названия всех книг и фамилии их авторов (title, surname)
-- Создание таблицы авторов
CREATE TABLE authors ( 
	id INT PRIMARY KEY AUTO_INCREMENT,
	surname VARCHAR(255) NOT NULL, 
	name VARCHAR(255) NOT NULL, 
	patronymic VARCHAR(255) NOT NULL,
	birth_date DATE,
	country VARCHAR(100),
	biography TEXT
);

-- Создание таблицы книг
CREATE TABLE books ( 
	id INT PRIMARY KEY AUTO_INCREMENT,
	title VARCHAR(255) NOT NULL, 
	author_id INT NOT NULL, 
	publish_date DATE, 
	isbn VARCHAR(20) UNIQUE,
	pages INT,
	FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE CASCADE
);

-- Далее доп задание - убирают NOT NULL у author_id и FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE CASCADE и спрашивают как теперь вывести все книги с авторами (даже если у книги нет автора)

```
---





## Гринатом

```sql

Дана таблица employee (last_name, first_name, middle_name)
Вывести сотрудников, у которых есть однофамильцы
```
---





## Сбер

```sql
-- Вывести список: название отдела и количество сотрудников в нем. Если в отделе нет сотрудников, то должно быть отображено название отдела с null или 0

dep  
-id  
-name  

emp  
-id  
-id_dep  
-fio
```


## X5

```sql

Вывести список отделов, количество сотрудников в которых не превышает 3 человек
-- Отдел:
CREATE TABLE department (
  id   INTEGER      NOT NULL, -- идентификатор отдела
  name VARCHAR(128) NOT NULL, -- название отдела
  PRIMARY KEY (id)
);
 
-- Сотрудник:
CREATE TABLE employee (
  id            INTEGER      NOT NULL, -- идентификатор сотрудника
  department_id INTEGER      NOT NULL, -- идентификатор отдела
  manager_id    INTEGER,               -- идентификатор начальника
  name          VARCHAR(128) NOT NULL, -- имя сотрудника
  salary        DECIMAL      NOT NULL, -- оклад сотрудника
  PRIMARY KEY (id),
  FOREIGN KEY (department_id) REFERENCES department(id),
  FOREIGN KEY (manager_id) REFERENCES employee(id)
);
  
   #x5 | Прислать задачу | Подписаться
```