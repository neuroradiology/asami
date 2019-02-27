(ns asami.test-planner
  "Tests internals of the query portion of the memory storage"
  (:require [asami.planner :refer [first-group min-join-path plan-path merge-filters Bindings]]
            [asami.graph :refer [Graph resolve-triple]]
            [naga.storage.store-util :refer [matching-vars]]
            [schema.core :as s]
            #?(:clj  [clojure.test :refer [is use-fixtures testing]]
               :cljs [clojure.test :refer-macros [is run-tests use-fixtures testing]])
            #?(:clj  [schema.test :as st :refer [deftest]]
               :cljs [schema.test :as st :refer-macros [deftest]]))
  #?(:clj (:import [clojure.lang ExceptionInfo])))

(use-fixtures :once st/validate-schemas)

(deftest test-query-path
  (let [simple-p '[[?a :a :b] [?b :c :d]]
        simple-cm '{[?a :a :b] 1, [?b :c :d] 1}
        [g] (first-group simple-p)
        p (min-join-path simple-p simple-cm)
        simple-p2 '[[?a :a :b] [?b :c :d] [?c :e ?b] [?a :c :d]]
        simple-cm2 '{[?a :a :b] 1, [?b :c :d] 2, [?c :e ?b] 1, [?a :c :d] 1}
        [g2] (first-group simple-p2)
        p2 (min-join-path simple-p2 simple-cm2)
        patterns '[[?a :a :b]
                   [?b :c ?d]
                   [?d :d ?e]
                   [?d :e ?f]
                   [?f :f ?a]
                   [?f :g ?g]
                   [?g :v1 ?v1]
                   [?g :v2 ?v2]
                   [?h :v1 ?v1]
                   [?h :v2 ?v2]
                   [?i :i ?h]
                   [?other :id "id"]]
        count-map '{[?a :a :b] 1
                    [?b :c ?d] 2
                    [?d :d ?e] 3
                    [?d :e ?f] 3
                    [?f :f ?a] 3
                    [?f :g ?g] 5
                    [?g :v1 ?v1] 3
                    [?g :v2 ?v2] 4
                    [?h :v1 ?v1] 5
                    [?h :v2 ?v2] 6
                    [?i :i ?h] 7
                    [?other :id "id"] 1}
        [group] (first-group patterns)
        path (min-join-path patterns count-map)]

    (is (= '[[?a :a :b]] g))
    (is (= '[[?a :a :b] [?b :c :d]] p))

    (is (= '[[?a :a :b] [?a :c :d]] g2))
    (is (= '[[?a :a :b] [?a :c :d] [?c :e ?b] [?b :c :d]] p2))

    (is (= '[[?a :a :b]
             [?f :f ?a]
             [?f :g ?g]
             [?g :v1 ?v1]
             [?g :v2 ?v2]
             [?h :v1 ?v1]
             [?h :v2 ?v2]
             [?i :i ?h]
             [?d :e ?f]
             [?b :c ?d]
             [?d :d ?e]] group))
    (is (= '[[?a :a :b]
             [?f :f ?a]
             [?d :e ?f]
             [?b :c ?d]
             [?d :d ?e]
             [?f :g ?g]
             [?g :v1 ?v1]
             [?g :v2 ?v2]
             [?h :v1 ?v1]
             [?h :v2 ?v2]
             [?i :i ?h]
             [?other :id "id"]] path))))


(defn mapto [s1 s2]
  (into {} (filter second (map vector s1 s2))))

(deftest test-query-paths
  (let [short-patterns '[[?a :b ?c]
                         [?d :e :f]
                         [?c ?d ?e]]
        path1 (min-join-path short-patterns
                             (mapto short-patterns [1 2 3]))
        path2 (min-join-path short-patterns
                             (mapto short-patterns [2 1 3]))
        path3 (min-join-path short-patterns
                             (mapto short-patterns [2 3 1]))
        path4 (min-join-path short-patterns
                             (mapto short-patterns [3 2 1]))]
    (is (= '[[?a :b ?c]
             [?c ?d ?e]
             [?d :e :f]]
           path1))
    (is (= '[[?d :e :f]
             [?c ?d ?e]
             [?a :b ?c]]
           path2))
    (is (= '[[?c ?d ?e]
             [?a :b ?c]
             [?d :e :f]]
           path3))
    (is (= '[[?c ?d ?e]
             [?d :e :f]
             [?a :b ?c]]
           path4))))


(defrecord StubResolver [counts]
  Graph
  (graph-add [this _ _ _] this)
  (graph-delete [this _ _ _] this)
  (resolve-triple [store s p o] (repeat (get counts [s p o])
                                        [:s :p :o]))
  (count-triple [store s p o] (count (resolve-triple store s p o))))

(defn resolver-for [patterns counts]
  (let [m (mapto patterns counts)]
    (->StubResolver m)))

(deftest test-filtered-query-paths
  (let [short-patterns [(with-meta '[(= ?d 5)] {:vars '#{?d}})
                        '[?a :b ?c]
                        '[?d :e :f]
                        (with-meta '[(not= ?e ?a)] {:vars '#{?e ?a}})
                        '[?c ?d ?e]]
        path1 (plan-path (resolver-for short-patterns [nil 1 2 nil 3])
                          short-patterns [])
        path2 (plan-path (resolver-for short-patterns [nil 2 1 nil 3])
                            short-patterns [])
        path3 (plan-path (resolver-for short-patterns [nil 2 3 nil 1])
                          short-patterns [])
        path4 (plan-path (resolver-for short-patterns [nil 3 2 nil 1])
                          short-patterns [])]
    (is (= '[[?a :b ?c]
             [?c ?d ?e]
             [(= ?d 5)]
             [(not= ?e ?a)]
             [?d :e :f]]
           path1))
    (is (= '[[?d :e :f]
             [(= ?d 5)]
             [?c ?d ?e]
             [?a :b ?c]
             [(not= ?e ?a)]]
           path2))
    (is (= '[[?c ?d ?e]
             [(= ?d 5)]
             [?a :b ?c]
             [(not= ?e ?a)]
             [?d :e :f]]
           path3))
    (is (= '[[?c ?d ?e]
             [(= ?d 5)]
             [?d :e :f]
             [?a :b ?c]
             [(not= ?e ?a)]]
           path4))))

(deftest var-mapping
  (let [m1 (matching-vars `[?a :rel ?c] `[?a ?b ?c] )
        m2 (matching-vars `[?b :rel ?f] `[?a ?b ?c ?d ?e ?f])
        m3 (matching-vars `[?b :rel ?f ?b :r2 ?e] `[?a ?b ?c ?d ?e ?f])
        m4 (matching-vars `[?x :rel ?f ?x :r2 ?e] `[?a ?b ?c ?d ?e ?f])]
    (is (= m1 {0 0, 2 2}))
    (is (= m2 {0 1, 2 5}))
    (is (= m3 {0 1, 2 5, 3 1, 5 4}))
    (is (= m4 {2 5, 5 4}))))

(deftest test-merge-filters
  (is (= '[[:a ?a ?b] [(= ?b :z)]] (merge-filters '[[:a ?a ?b]] '[[(= ?b :z)]])))
  (is (= '[[:x ?c ?a] [:a ?a ?b] [(= ?b :z)]] (merge-filters '[[:x ?c ?a] [:a ?a ?b]] '[[(= ?b :z)]])))
  (is (= '[[:x ?c ?a] [(= ?a :z)] [:a ?a ?b]] (merge-filters '[[:x ?c ?a] [:a ?a ?b]] '[[(= ?a :z)]]))))

#?(:cljs (run-tests))