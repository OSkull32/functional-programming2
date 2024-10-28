(ns lab2.core-prop-test
  (:require [clojure.test :refer [deftest run-tests]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lab2.bag :refer [count-occurrences, remove-one-from-bag, filter-bag, compare-bags, merge-bags, empty-trie, insert]])
  (:import (lab2.bag TrieBag)))

;; Генератор строк
(def string-gen
  (gen/fmap #(apply str %) (gen/vector gen/char-alpha 1 20)))

;; Генератор чисел
(def number-gen
  (gen/choose 0 300))

;; Генератор символов
(def symbol-gen
  (gen/fmap #(symbol (str %)) (gen/elements (map char (range 53 173)))))

;; Генератор смешанных типов
(def mixed-gen
  (gen/one-of [string-gen number-gen symbol-gen]))

(deftest test-insert-and-count-occurrences
  (tc/quick-check 100
                  (prop/for-all [key mixed-gen]
                                (let [bag (insert (TrieBag. (empty-trie)) key)]
                                  (= 1 (count-occurrences bag key))))))

(deftest test-insert-and-remove
  (tc/quick-check 100
                  (prop/for-all [key mixed-gen]
                                (let [bag (insert (TrieBag. (empty-trie)) key)
                                      updated-bag (remove-one-from-bag bag key)]
                                  (and (= 0 (count-occurrences updated-bag key))
                                       (= 1 (count-occurrences bag key)))))))

(deftest test-merge-bags
  (tc/quick-check 100
                  (prop/for-all [keys1 (gen/vector mixed-gen 1 5)
                                 keys2 (gen/vector mixed-gen 1 5)]
                                (let [bag1 (reduce insert (TrieBag. (empty-trie)) keys1)
                                      bag2 (reduce insert (TrieBag. (empty-trie)) keys2)
                                      merged-bag (merge-bags bag1 bag2)]
                                  (and (every? #(= (count-occurrences merged-bag %) (+ (count-occurrences bag1 %) (count-occurrences bag2 %))) keys1)
                                       (every? #(= (count-occurrences merged-bag %) (+ (count-occurrences bag1 %) (count-occurrences bag2 %))) keys2))))))

;; Исправленный тест для filter-bag с правильным генератором предикатов
(deftest test-filter-bag
  (tc/quick-check 100
                  (prop/for-all [keys (gen/vector mixed-gen 1 5)
                                 pred (gen/elements [(fn [s] (seq (s)))  ;; Предикаты для фильтрации
                                                     (constantly false)])]
                                (let [bag (reduce insert (TrieBag. (empty-trie)) keys)
                                      filtered-bag (filter-bag bag pred)]
                                  ;; Проверяем, что результат фильтрации соответствует предикату
                                  (every? (fn [k]
                                            (if (pred k)
                                              ;; Если предикат истинный, количество в отфильтрованном мешке должно быть тем же
                                              (= (count-occurrences filtered-bag k) (count-occurrences bag k))
                                              ;; Иначе количество в отфильтрованном мешке должно быть 0
                                              (= (count-occurrences filtered-bag k) 0)))
                                          keys)))))

(deftest test-compare-bags
  (tc/quick-check 100
                  (prop/for-all [keys1 (gen/vector mixed-gen 1 5)
                                 keys2 (gen/vector mixed-gen 1 5)]
                                (let [bag1 (reduce insert (TrieBag. (empty-trie)) keys1)
                                      bag2 (reduce insert (TrieBag. (empty-trie)) keys1) ; bag2 будет равен bag1
                                      bag3 (reduce insert (TrieBag. (empty-trie)) keys2)] ; bag3 будет отличаться от bag1
                                  (and (compare-bags bag1 bag2)
                                       (not (compare-bags bag1 bag3)))))))

(deftest commutative-property
  (tc/quick-check 100
                  (prop/for-all [keys1 (gen/vector mixed-gen 1 5)
                                 keys2 (gen/vector mixed-gen 1 5)]
                                (let [tree1 (reduce insert (TrieBag. (empty-trie)) keys1)
                                      tree2 (reduce insert (TrieBag. (empty-trie)) keys2)]
                                  (compare-bags (merge-bags tree1 tree2) (merge-bags tree2 tree1))))))

(deftest associative-property
  (tc/quick-check 100
                  (prop/for-all [keys1 (gen/vector string-gen 1 5)
                                 keys2 (gen/vector string-gen 1 5)
                                 keys3 (gen/vector string-gen 1 5)]
                                (let [tree1 (reduce insert (TrieBag. (empty-trie)) keys1)
                                      tree2 (reduce insert (TrieBag. (empty-trie)) keys2)
                                      tree3 (reduce insert (TrieBag. (empty-trie)) keys3)]
                                  (compare-bags (merge-bags tree1 (merge-bags tree2 tree3))
                                                (merge-bags (merge-bags tree1 tree2) tree3))))))

(deftest neutral-element-property
  (tc/quick-check 100
                  (prop/for-all [keys (gen/vector string-gen 1 5)]
                                (let [tree (reduce insert (TrieBag. (empty-trie)) keys)
                                      zero-tree (TrieBag. (empty-trie))] ;; Нейтральный элемент
                                  (compare-bags (merge-bags tree zero-tree) tree)))))

(run-tests)



