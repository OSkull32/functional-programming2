(ns lab2.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [lab2.bag :refer [count-occurrences, entries, create-prefix-tree, remove-one-from-bag, filter-bag, map-entries, entries-with-mapped-keys, compare-bags, trie-keys, merge-bags, empty-trie, insert, fold-left-trie, fold-right-trie]])
  (:import (lab2.bag TrieBag)))

(deftest test-insert
  (let [tree (-> (TrieBag. (empty-trie))
                 (insert "apple")
                 (insert "banana")
                 (insert "apple"))]
    (is (= 2 (count-occurrences tree "apple")))
    (is (= 1 (count-occurrences tree "banana")))
    (is (= 0 (count-occurrences tree "grape")))
    (is (= (set (entries tree)) #{["apple" 2] ["banana" 1]}))))

(deftest test-remove-one-for-bag
  (let [tree (-> (create-prefix-tree ["apple" "apple" "banana"])
                 (remove-one-from-bag "apple")
                 (remove-one-from-bag "banana"))]
    (is (= 1 (count-occurrences tree "apple")))
    (is (= 0 (count-occurrences tree "banana")))
    (is (= 0 (count-occurrences tree "pear")))
    (is (= (set (entries tree)) #{["apple" 1]}))))

(deftest test-filter-bag
  (let [tree (create-prefix-tree ["apple" "banana" "apricot"])
        tree1 (create-prefix-tree ["apple" "banana" "cherry" "date"])]
    ;; Filter to retain elements starting with 'a'
    (is (= (filter-bag tree #(str/starts-with? % "a"))
           ["apple", "apricot"]))
    ;; Filter to retain elements with length > 5
    (is (= (filter-bag tree1 #(> (count %) 5))
           ["banana" "cherry"]))))

(deftest test-entries
  (let [tree (create-prefix-tree ["apple" "apple" "banana"])]
    (is (= (set (entries tree)) #{["apple" 2] ["banana" 1]}))))

(deftest test-map-entries
  (let [tree (create-prefix-tree ["apple" "banana" "apple"])
        mapped-tree (map-entries tree (fn [key cnt] [(str key "-mapped") (* cnt 2)]))
        mapped-entries (entries-with-mapped-keys mapped-tree)]
    (is (= (set mapped-entries) #{["apple-mapped" 4] ["banana-mapped" 2]}))))

(deftest test-compare-bags
  (let [tree1 (create-prefix-tree ["apple" "banana"])
        tree2 (create-prefix-tree ["apple" "banana"])
        tree3 (create-prefix-tree ["apple" "apricot"])]
    (is (compare-bags tree1 tree2))  ;; Ожидается, что деревья одинаковые
    (is (not (compare-bags tree1 tree3)))))  ;; Разные деревья

(deftest test-trie-keys
  (let [tree (create-prefix-tree ["apple" "banana" "apricot"])
        keys (trie-keys tree)]
    (is (= (set keys) #{"apple" "banana" "apricot"}))))

(deftest test-merge-bags
  (let [bag1 (create-prefix-tree ["apple" "banana"])
        bag2 (create-prefix-tree ["apple" "apricot"])
        merged-bag (merge-bags bag1 bag2)]
    ;; Ожидаем, что apple будет иметь count 2, а banana и apricot по 1
    (is (= (set (entries merged-bag)) #{["apple" 2] ["apricot" 1] ["banana" 1]}))))

(deftest fold-trie-tests
  (let [bag (-> (TrieBag. (empty-trie))
                (insert "apple")
                (insert "banana")
                (insert "banana") ;; Вставляем "banana" два раза
                (insert "cherry"))]

      ;; Тест для fold-left-trie
    (let [left-fold-result (fold-left-trie bag
                                           (fn [acc _ count]
                                             (+ acc count)) ;; Суммируем количество вхождений
                                           0)] ;; Начальное значение 0
      (is (= left-fold-result 4) "Total occurrences using fold-left-trie")) ;; Ожидаем 4 (1 + 2 + 1)

      ;; Тест для fold-right-trie
    (let [right-fold-result (fold-right-trie bag
                                             (fn [acc _ count]
                                               (+ acc count)) ;; Суммируем количество вхождений
                                             0)] ;; Начальное значение 0
      (is (= right-fold-result 4) "Total occurrences using fold-right-trie")) ;; Ожидаем 4 (1 + 2 + 1)

      ;; Тест для получения всех ключей с fold-left-trie
    (let [keys-left (fold-left-trie bag
                                    (fn [acc key _]
                                      (conj acc key)) ;; Собираем все ключи
                                    [])] ;; Начальное значение пустой вектор
      (is (= keys-left ["apple" "banana" "cherry"]) "Keys using fold-left-trie"))

      ;; Исправленный тест для получения всех ключей с fold-right-trie
    (let [keys-right (fold-right-trie bag
                                      (fn [acc key _]
                                        (conj acc key)) ;; Собираем все ключи
                                      [])] ;; Начальное значение пустой вектор
      (is (= (vec (reverse keys-right)) ["apple" "banana" "cherry"]) "Keys using fold-right-trie"))))

(run-tests)