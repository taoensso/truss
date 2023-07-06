(ns taoensso.truss-tests
  (:require
   [clojure.test    :as test :refer [deftest testing is]]
   [clojure.string  :as str]
   [taoensso.encore :as enc   :refer [throws throws?]]
   [taoensso.truss  :as truss :refer [have have? have! have!?]]
   [taoensso.truss.impl :as impl])

  #?(:cljs
     (:require-macros [taoensso.truss-tests :refer
                       [my-macro1 my-macro2 my-macro3]])))

(comment
  (remove-ns      'taoensso.truss-tests)
  (test/run-tests 'taoensso.truss-tests))

;;;; High-level

(deftest _basics
  [(testing "Falsey vals"
     [(is      (throws? (have       nil)))
      (is      (throws? (have?      nil)))

      (is (not (throws? (have       false))))
      (is (not (throws? (have?      false))))

      (is      (false?  (have       false)))
      (is      (true?   (have?      false)))

      (is      (throws? (have  nil? false)))
      (is      (throws? (have? nil? false)))

      (is (not (throws? (have  nil? nil))))
      (is (not (throws? (have? nil? nil))))])

   (testing "Misc basics"
     [(is (= 5     (have integer? 5)))
      (is (throws? (have integer? 5.5)))

      (is (throws? :common {:env {:elidable? true}}  (have    string?  5)))
      (is (throws? :common {:env {:elidable? false}} (have :! string?  5)))

      (is (throws? :common {:arg {:value 6}} (let [x 5 y 6] (have odd?  x x x y x))))
      (is (throws? :common {:arg {:value 1}} (let [x 0 y 1] (have zero? x x x y x))))

      (is (throws? :common {:arg {:value "foo"}}
            ((fn foo [x] {:pre [(have? integer? x)]} (* x x)) "foo")))])

   (testing "Sequential lazy val form evaluation"
     [(let [[a1_ a2_] (repeatedly 2 #(atom nil))
            result
            (have string?
              (do (reset! a1_ true) "foo")
              (do (reset! a2_ true) "bar"))]

        [(is (= ["foo" "bar"] result))
         (is (= [true true]   [@a1_ @a2_]))])

      (let [[a1_ a2_ a3_] (repeatedly 3 #(atom nil))
            result
            (throws? :common {:arg {:value "bar"}}
              (have number?
                (do (reset! a1_ true) 5)
                (do (reset! a2_ true) "bar")
                (do (reset! a3_ true) 10)))]

        [(is (= true            result))
         (is (= [true true nil] [@a1_ @a2_ @a3_]))])])

   (testing "Throwing predicates"
     (let [zero! (fn [n] (if (zero? n) true (throw (ex-info "" {}))))]
       [(is (enc/error? (:err (ex-data (throws :common (have zero! 1))))))
        (is (enc/error? (:err (ex-data (throws :common (have zero! 0 0 1 0))))))
        (is (= [0 0 0 0]                               (have zero! 0 0 0 0)))]))

   (testing "Throwing vals"
     (let [result (throws :common (have string? (throw (ex-info "" {}))))]

       [(is (enc/error?             (-> (ex-data result) :err)))
        (is (= 'truss/undefined-arg (-> (ex-data result) :arg :value)))]))

   (testing ":in"
     [(is (= ["a" "b"] (have string? :in ["a" "b"])))
      (is (= ["a" "b"] (have string? :in (if true ["a" "b"] [1 2]))))

      (is (throws? :common {:arg {:value 1}} (have string? :in (if false ["a" "b"] [1 2]))))
      (is (= ["0" "1" "2"]                   (have string? :in (mapv str (range 3)))))

      (is (throws? :common {:arg {:value 1}} (have string? :in ["a" 1])))
      (is (= [["a" "b"] ["a" "b"]]           (have string? :in ["a" "b"] ["a" "b"])))
      (is (throws? :common {:arg {:value 1}} (have string? :in ["a" "b"] ["a" "b" 1])))])

   (testing "Special preds"
     [(is (= nil     (have [:or nil? string?] nil)))
      (is (= "hello" (have [:or nil? string?] "hello")))
      (is (= "hello" (have [:or pos? string?] "hello")))

      (is (throws? :common {:arg {:value -5}}
            (have [:or pos? string?] -5)))

      (is (= [:a :b :c]    (have [:set>= #{:a :b}] [:a :b :c])))
      (is (throws? :common (have [:set>= #{:a :b}] [:a    :c])))

      (is (= [:a :b]       (have [:set<= [:a :b :c]] [:a :b])))
      (is (= [:a :b]       (have [:n= 2] [:a :b])))
      (is (throws? :common (have [:n= 2] [:a :b :c])))

      (is (= :a    (have [:el #{:a :b :c}] :a)))
      (is (throws? (have [:el #{:a :b :c}] :d)))

      (is (= nil   (have [:or nil? [:and integer? odd?]] nil)))
      (is (= 7     (have [:or nil? [:and integer? odd?]] 7)))
      (is (throws? (have [:or nil? [:and integer? odd?]] 7.5)))
      (is (throws? (have [:or nil? [:and integer? odd?]] 6)))

      #?(:clj (is (= "hello"       (have [:instance? String]  "hello"))))
      #?(:clj (is (throws? :common (have [:instance? Integer] "hello"))))])

   (testing "Error fns"
     [(is (= nil  (truss/with-error-fn nil           (have string? 5))))
      (is (= :foo (truss/with-error-fn (fn [_] :foo) (have string? 5))))])

   (testing "Assertion data"
     [(is (= "5"                                      (have string? "5" :data {:user 101})))
      (is (throws? :common {:data {:arg {:user 101}}} (have string?  5  :data {:user 101})))

      (truss/with-data {:user 101}
        [(is (= "5"                                          (have string? "5")))
         (is (throws? :common {:data {:dynamic {:user 101}}} (have string?  5)))
         (is (throws? :common {:data {:dynamic {:user 101}
                                      :arg     {:name "Stu"}}} (have string? 5 :data {:name "Stu"})))])])])

;;;;

#?(:clj (defmacro my-macro1 "See issue #12" [n x] `(have string? (do (swap! ~n inc) ~x))))

(deftest _side-effects
  (testing "Side effects"

    (let [n (atom 0)]
      [(is (= (have string? (do (swap! n inc) "str")) "str"))
       (is (= @n 1))])

    (let [n (atom 0)]
      [(is (throws? (have string? (do (swap! n inc) :kw))))
       (is (= @n 1))])

    (let [n (atom 0)]
      [(is (= (my-macro1 n "str") "str"))
       (is (= @n 1))])

    (let [n (atom 0)]
      [(is (throws? (my-macro1 n :kw)))
       (is (= @n 1))])))

(defmacro my-macro2 [x]                      `(have string? ~x))
(defmacro my-macro3 [x] (truss/keep-callsite `(have string? ~x)))

(deftest _clj-865
  (testing "Clojure issue #865"
    [(is (=             (my-macro2 "str") "str"))
     (is (=        (->> (my-macro2 :kw) throws ex-data :loc :line) nil))
     (is (integer? (->> (my-macro3 :kw) throws ex-data :loc :line)))]))

;;;;

#?(:cljs (test/run-tests))
