# Лабораторная работа №2

---

**Выполнил:** Данченко Владимир Витальевич, 368087
**Группа:** P3334
**Вариант:** pre-bag(Prefix Tree, Bag)

---

# Цель:

Освоиться с построением пользовательских типов данных, полиморфизмом, рекурсивными алгоритмами и средствами тестирования (unit testing, property-based testing).

---

## Требования к разработанному ПО:

1. Реализовать функции:
    - добавление и удаление элементов;
    - фильтрация;
    - отображение (map);
    - свертки (левая и правая);
    - структура должна быть моноидом.

2. Структуры данных должны быть неизменяемыми.
3. Библиотека должна быть протестирована в рамках unit testing.
4. Библиотека должна быть протестирована в рамках property-based testing (как минимум 3 свойства, включая свойства моноида).
5. Структура должна быть полиморфной.
6. Использовать идиоматичный для технологии стиль программирования. Примечание: некоторые языки позволяют получить большую часть API через реализацию небольшого интерфейса. Так как лабораторная работа про ФП, а не про экосистему языка -- необходимо реализовать их вручную и по возможности -- обеспечить совместимость.

## Реализация

### Объявление протокола структуры данных

```clojure
(defprotocol BagProtocol
   (insert [this element] "Добавляет элемент в мешок по ключу")
   (count-occurrences [this key] "Подсчитать количество вхождений ключа")
   (remove-one-from-bag [this element] "Удаляет элемент из мешка по ключу")
   (entries [this] "Вернуть все пары ключ-счётчик")
   (trie-keys [this] "Вернуть все ключи")
   (merge-bags [this bag2] "Объединяет два мешка")
   (filter-bag [this pred] "Фильтрует элементы в мешке по предикату")
   (map-entries [this f] "Отображает элементы мешка с помощью функции")
   (fold-left-trie [this f init] "Левосторонняя свёртка для мешка")
   (fold-right-trie [this init f] "Правосторонняя свёртка для мешка")
   (compare-bags [this other] "Сравнение двух мешков")
   (entries-with-mapped-keys [this] "Возвращает записи с изменёнными ключами"))
```
### Добавление элементов

```clojure
(insert [_ key]
        (letfn [(insert-seq [node elems]
                   ;; Если все символы обработаны, увеличиваем счетчик для окончания слова
                   (if (empty? elems)
                      (update node :count inc)
                      (let [elem (first elems)
                            ;; Получаем следующий узел, или создаем пустой, если его нет
                            next-node (get-in node [:children elem] (empty-trie))]
                         ;; Рекурсивно добавляем элемент и обновляем дерево
                         (assoc-in node [:children elem] (insert-seq next-node (rest elems))))))]
           ;; Возвращаем новый экземпляр TrieBag с обновленным деревом
           (TrieBag. (insert-seq trie key))))
```

Этот метод добавляет ключ в мультимножество. Ключ представлен последовательностью символов. Внутри функции используется рекурсивная вспомогательная функция insert-seq, которая проходит по дереву, создавая новые узлы для каждого символа ключа, если их ещё нет. Как только последовательность символов заканчивается, счётчик узла увеличивается, обозначая, что этот ключ был добавлен.

### Удаление элементов

```clojure
  (remove-one-from-bag [_ key]
     (letfn [(remove-seq [node elems]
                ;; Если все символы обработаны, уменьшаем счетчик
                (if (empty? elems)
                   (update node :count (fn [cnt] (max 0 (dec cnt))))
                   (let [elem (first elems)
                         next-node (get-in node [:children elem])]
                      ;; Если дочерний узел найден, продолжаем удаление
                      (if next-node
                         (let [updated-node (remove-seq next-node (rest elems))]
                            ;; Если дочерний узел стал пустым, удаляем его
                            (if (and (= (:count updated-node) 0) (empty? (:children updated-node)))
                               (update node :children dissoc elem)
                               (assoc-in node [:children elem] updated-node)))
                         ;; Если узел не найден, возвращаем текущий узел
                         node))))]
        ;; Создаем новый мешок с обновленным префиксным деревом
        (TrieBag. (remove-seq trie key))))
```
Удаляет одно вхождение ключа из мультимножества. Если ключ существует, его счётчик уменьшается на 1. Если после этого счётчик становится равен 0, узел и его поддеревья удаляются, если они больше не содержат других ключей.

### Фильтрация

```clojure
  (filter-bag [_ pred]
     (letfn [(collect-filtered-keys [node prefix]
                ;; Собираем ключи, удовлетворяющие предикату
                (let [current-key (apply str prefix)
                      current-keys (when (and (> (:count node) 0) (pred current-key))
                                      [current-key])
                      children-keys (mapcat (fn [[elem child]]
                                               (collect-filtered-keys child (conj prefix elem)))
                                            (:children node))]
                   (concat current-keys children-keys)))]
        (collect-filtered-keys trie []))) ;; Начинаем с пустого префикса
```
Этот метод возвращает список ключей, которые соответствуют предикату pred. Для этого используется рекурсивная функция collect-filtered-keys, которая проходит по всем узлам дерева и собирает только те ключи, которые удовлетворяют предикату.

### 4. Отображение (map)

```clojure
  (map-entries [_ f]
      (letfn [(map-seq [node prefix]
        (let [new-prefix (apply str prefix)
              ;; Применяем функцию только если есть вхождения в текущем узле
              updated-entry (when (> (:count node) 0)
                               (f new-prefix (:count node)))
              ;; Рекурсивно обрабатываем всех детей
              updated-children (reduce-kv (fn [acc k child]
                                 (let [updated-child (map-seq child (conj prefix k))]
                                    (if updated-child
                                       (assoc acc k updated-child)
                                        acc))) {}
                                          (:children node))]
           ;; Создаём обновлённый узел на основе полученных данных
           (cond updated-entry ;; Если у узла есть изменённая запись
              {:count (second updated-entry)
               :children updated-children
               :mapped-key (first updated-entry)}
              (empty? updated-children) nil ;; Если узел не имеет детей и нет счётчика
              :else {:count 0 :children updated-children})))] ;; Если есть только дети, возвращаем узел с детьми
         (TrieBag. (map-seq trie [])))) ;; Начинаем обработку с корня дерева
```
Применяет функцию f к каждому ключу и его количеству вхождений. Эта функция рекурсивно преобразует все узлы дерева, не изменяя структуру ключей в промежуточных узлах.

### Свертки (левая и правая)

```clojure
(fold-left-trie [_ f initial]
   (letfn [(fold-seq [node prefix acc]
     ;; Применяем функцию, если у узла есть вхождения
     (let [new-acc (if (> (:count node) 0)
                      (f acc (apply str prefix) (:count node))
                      acc)
           ;; Рекурсивно проходим по дочерним узлам
           children-acc (reduce-kv (fn [acc child-key child]
                                      (fold-seq child (conj prefix child-key) acc))
                                   new-acc
                                   (:children node))]
        children-acc))]
      ;; Начинаем свёртку с корня дерева и пустого префикса
      (fold-seq trie [] initial)))

;; Правосторонняя свёртка
(fold-right-trie [_ f initial]
  (letfn [(fold-seq [node prefix acc]
    ;; Рекурсивно проходим по дочерним узлам начиная справа
    (let [children-acc (reduce-kv (fn [acc child-key child] 
                                     (fold-seq child (conj prefix child-key) acc))
                                  acc
                                  (reverse (seq (:children node))))
          ;; Применяем функцию, если у узла есть вхождения
          new-acc (if (> (:count node) 0)
                     (f children-acc (apply str (conj prefix "")) (:count node))
                     children-acc)]
       new-acc))]
     ;; Начинаем свёртку с корня дерева и пустого префикса
     (fold-seq trie [] initial)))
```
Эти методы реализуют свёртки, которые позволяют рекурсивно обрабатывать все элементы мультимножества, начиная либо с левого края дерева (левосторонняя свёртка), либо с правого (правосторонняя свёртка).

### Создание структуры данных

```clojure
(defn create-prefix-tree [keys]
   (reduce (fn [trie key]
              (.insert trie key))
           (TrieBag. (empty-trie))
           keys))
```
Функция, которая создаёт новое префиксное дерево из списка ключей.

---

## Тестирование

### Unit Testing

[Посмотреть тесты](test/lab2/core_test.clj)

### Property-based тестирование

[Посмотреть тесты](test/lab2/core_prop_test.clj)

Все тесты успешно проходят

## Вывод

Выполняя данную лабораторную работу я разработал мультимножество (bag) на основе префиксного дерева (trie) с основными функциями, я научился работать с неизменяемыми структурами данных в языке Clojure.
Написаны Unit-тесты, которые проверяют корректность основных операций.
Реализованы property-based тесты, которые позволяют проверять свойства данных при различных условиях.


