(ns lab2.bag)

;; Протокол для мешка (Bag)
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

;; Создаёт пустой узел для дерева
(defn empty-trie []
  {:count 0 :children {}})

;; Реализация мультимножества на основе Trie
(deftype TrieBag [trie]
  BagProtocol

  ;; Вставка элементов в мультимножество
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

  ;; Подсчёт количества вхождений ключа
  (count-occurrences [_ key]
    (letfn [(lookup-seq [node elems]
              ;; Если все символы обработаны, возвращаем счетчик
              (if (empty? elems)
                (:count node)
                (let [next-node (get-in node [:children (first elems)])]
                  ;; Если следующий узел найден, продолжаем поиск
                  (if next-node
                    (lookup-seq next-node (rest elems))
                    0))))] ;; Если узел не найден, возвращаем 0
      (lookup-seq trie key)))

  ;; Удаление одного вхождения ключа
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

  ;; Возвращает все ключи мультимножества
  (trie-keys [_]
    (letfn [(collect-keys [node prefix]
              (when node ;; Используем when для проверки наличия узла
                (let [current-key (when (> (:count node) 0) (apply str prefix))
                      children-keys (mapcat (fn [[elem child]]
                                              (collect-keys child (conj prefix elem)))
                                            (:children node))]
                  ;; Объединяем текущий ключ и ключи дочерних узлов
                  (concat (when current-key [current-key]) children-keys))))]
      (collect-keys trie []))) ;; Начинаем с пустого префикса)

  ;; Возвращает все записи (ключ и его количество)
  (entries [_]
    (letfn [(collect-entries [node prefix]
              ;; Проверка наличия узла
              (when node
                (let [current-entry (when (> (:count node) 0)
                                      [[(apply str prefix) (:count node)]])
                      children-entries (mapcat (fn [[elem child]]
                                                 (collect-entries child (conj prefix elem)))
                                               (:children node))]
                  ;; Объединяем текущую запись и записи дочерних узлов
                  (concat (or current-entry []) children-entries))))]
      (collect-entries trie []))) ;; Начинаем с пустого префикса)

  ;; Применение функции к каждому элементу
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
                                                      acc)))
                                                {}
                                                (:children node))]
                ;; Создаём обновлённый узел на основе полученных данных
                (cond
                  ;; Если у узла есть изменённая запись
                  updated-entry
                  {:count (second updated-entry)
                   :children updated-children
                   :mapped-key (first updated-entry)}
                  ;; Если узел не имеет детей и нет счётчика
                  (empty? updated-children) nil
                  ;; Если есть только дети, возвращаем узел с детьми
                  :else {:count 0 :children updated-children})))]
      ;; Начинаем обработку с корня дерева
      (TrieBag. (map-seq trie []))))

;; Возвращает записи с изменёнными ключами
  (entries-with-mapped-keys [_]
    (letfn [(collect-entries [node]
              ;; Собираем текущую запись, если счетчик больше 0
              (let [current-entry (when (> (:count node) 0)
                                    [[(:mapped-key node) (:count node)]])
                    ;; Рекурсивно обрабатываем дочерние узлы
                    children-entries (mapcat (fn [[_ child]]
                                               (collect-entries child))
                                             (:children node))]
                ;; Объединяем текущую запись и детей
                (concat current-entry children-entries)))]
      (collect-entries trie)))

;Фильтрация элементов собирает ключи, удовлетворяющие предикату.
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

  ;; Левосторонняя свёртка
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

  ;; Сравнение двух мешков
  (compare-bags [this other]
    ;; Сначала проверим совпадение ключей
    (let [keys1 (trie-keys this)
          keys2 (trie-keys other)]
      (and (= (set keys1) (set keys2))  ;; Проверяем, что множества ключей равны
           ;; Проверяем количество вхождений для каждого ключа
           (every? (fn [key]
                     (= (count-occurrences this key)
                        (count-occurrences other key)))
                   keys1))))

;; Объединение двух мультимножеств
  (merge-bags [this other]
    (let [other-keys (trie-keys other)]  ;Извлекаем все ключи из второго дерева.
      (reduce (fn [trie key] ;Каждый ключ второго мешка добавляется в первый мешок
                (insert trie key))
              this
              other-keys))))

;; Создание мультимножества из списка ключей
(defn create-prefix-tree [keys]
  (reduce (fn [trie key]
            (.insert trie key))
          (TrieBag. (empty-trie))
          keys))
