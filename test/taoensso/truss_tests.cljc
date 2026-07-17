(ns taoensso.truss-tests
  (:require
   #?(:clj  [clojure.core :as core]
      :cljs [cljs.core    :as core])
   [clojure.test   :as test :refer [deftest testing is]]
   [clojure.string :as str]
   [taoensso.truss :as truss :refer [have have? have! have!? throws throws? submap?]]
   [taoensso.truss.impl :as impl])

  #?(:cljs
     (:require-macros
      [taoensso.truss-tests :refer
       [macro1 macro2 macro3 macro4 macro5
        parse-pred-form have-macro]])))

(comment
  (remove-ns      'taoensso.truss-tests)
  (test/run-tests 'taoensso.truss-tests))

;;;; Error utils

(deftest _submap?
  [(is      (submap? {}       {:a :submap/nx}))
   (is (not (submap? {:a nil} {:a :submap/nx})))
   (is      (submap? {:a nil} {:a :submap/ex}))
   (is (not (submap? {}       {:a :submap/ex})))
   (is      (submap? {:a :A}  {:a :submap/some}))
   (is (not (submap? {:a nil} {:a :submap/some})))
   (is (not (submap? {}       {:a :submap/some})))
   (is (not (submap? {}       {:a some?})))
   (is (not (submap? {}       {:a :taoensso.truss/nx})))
   (is      (submap? {:a :taoensso.truss/nx} {:a :taoensso.truss/nx}))
   (is      (submap? {:a :taoensso.truss/nx} {:a :submap/ex}))])

(def  ex1 (ex-info "Ex1" {}))
(defn ex1!   [   ] (throw ex1))
(defn throw! [arg] (throw (ex-info "TestEx" {:arg {:value arg :type (type arg)}})))

(deftest _error-basics
  (let [ex1 (ex-info "Ex1" {:k1 "v1"})
        ex2 (ex-info "Ex2" {:k2 "v2"} ex1)
        ex-type
        #?(:clj  'clojure.lang.ExceptionInfo
           :cljs    'cljs.core/ExceptionInfo)

        ex1-map {:type ex-type :msg "Ex1" :data {:k1 "v1"}}
        ex2-map {:type ex-type :msg "Ex2" :data {:k2 "v2"}}]

    [(is (=       (truss/ex-root          ex2)          ex1))
     (is (=       (truss/ex-chain         ex2) [ex2     ex1]))
     (is (=       (truss/ex-chain :as-map ex2) [ex2-map ex1-map]))
     (is (submap? (truss/ex-map ex2)
           (assoc ex1-map
             :chain [ex2-map ex1-map]
             :trace #?(:clj #(vector? (force %))
                       :cljs string?))))]))

(deftest _throws?
  (let [throw-common   (fn [] (throw (ex-info "Shenanigans" {:a :a1 :b :b1})))
        throw-uncommon (fn [] (throw #?(:clj (Error.) :cljs "Error")))]

    [(is      (throws?                            (throw-common)))
     (is      (throws? :common                    (throw-common)))
     (is      (throws? :all                       (throw-common)))
     (is (not (throws? :common                    (throw-uncommon))))
     (is      (throws? :all                       (throw-uncommon)))
     (is      (throws? #{:common :all}            (throw-uncommon)))

     (is      (throws? :default #"Shenanigans"    (throw-common)))
     (is (not (throws? :default #"Brouhaha"       (throw-common))))

     (is      (throws? :default {:a :a1}          (throw-common)))
     (is (not (throws? :default {:a :a1 :b :b2}   (throw-common))))

     (is      (throws? :default {:a :a1} (throw (ex-info "Test" {:a :a1 :b :b1}))))
     (is (not (throws? :default {:a :a1} (throw (ex-info "Test" {:a :a2 :b :b1})))))

     (is (= (ex-data (throws :ex-info     {:a :a3}
                       (throw
                         (ex-info       "ex1" {:a :a1}
                           (ex-info     "ex2" {:a :a2}
                             (ex-info   "ex3" {:a :a3} ; <- Match this
                               (ex-info "ex4" {:a :a4})))))))
           {:a :a3})
       "Check nested causes for match")

     ;; Form must throw error, not return it
    #?(:clj
       [(is      (throws? Exception (throw (Exception.))))
        (is (not (throws? Exception        (Exception.))))]

       :cljs
       [(is      (throws? js/Error (throw (js/Error.))))
        (is (not (throws? js/Error        (js/Error.))))])]))

(deftest _try*
  [(is (= (truss/try*)         nil) "No body or catch")
   (is (= (truss/try* (+ 0 1)) 1)   "No catch")
   (is (= (let [a (atom false)] (truss/try* (finally (reset! a true))) @a) true) "Lone finally")
   (is (= (truss/try* (ex1!) (catch :all     _ :caught)) :caught))
   (is (= (truss/try* (ex1!) (catch :all     _ :caught)) :caught))
   (is (= (truss/try* (ex1!) (catch :common  _ :caught)) :caught))
   (is (= (truss/try* (ex1!) (catch :ex-info _ :caught)) :caught))
   (is (throws?
         (let [err #?(:clj (Exception. "Ex1") :cljs (js/Error. "Err1"))]
           (truss/try* (throw err) (catch :ex-info _ :caught)))))

   #?(:clj
      [(is       (= (truss/try* (throw (Exception.))      (catch :all              _ :caught)) :caught))
       (is       (= (truss/try* (throw (Throwable.))      (catch :all              _ :caught)) :caught))
       (is       (= (truss/try* (throw (Error.))          (catch :all              _ :caught)) :caught))

       (is       (= (truss/try* (throw (Exception.))      (catch :all-but-critical _ :caught)) :caught))
       (is       (= (truss/try* (throw (Throwable.))      (catch :all-but-critical _ :caught)) :caught))
       (is       (= (truss/try* (throw (AssertionError.)) (catch :all-but-critical _ :caught)) :caught))
       (is (throws? (truss/try* (throw (Error.))          (catch :all-but-critical _ :caught))))])])

(deftest _matching-error
  [(is (truss/error? (truss/matching-error                     (truss/try* ("") (catch :all t t)))))
   (is (truss/error? (truss/matching-error            :common  (truss/try* ("") (catch :all t t)))))
   (is (nil?         (truss/matching-error   :ex-info          (truss/try* ("") (catch :all t t)))))
   (is (truss/error? (truss/matching-error #{:ex-info :common} (truss/try* ("") (catch :all t t)))))

   (is (truss/error? (truss/matching-error :common  "Foo"   (truss/try* (throw (ex-info "Foo"   {})) (catch :all t t)))))
   (is (nil?         (truss/matching-error :common  "Foo"   (truss/try* (throw (ex-info "Bar"   {})) (catch :all t t)))))
   (is (truss/error? (truss/matching-error :common  {:a :b}                    (ex-info "Test"  {:a :b :c :d}))))
   (is (truss/error? (truss/matching-error :ex-info {:a :b}                    (ex-info "Dummy" {} (ex-info "Test" {:a :b})))))

   (let [error #?(:clj  (Exception.)
                  :cljs (let [error (js/Error.)] (set! (.-message error) nil) error))
         outer (ex-info "Outer" {} error)]
     [(is (nil? (truss/matching-error :common  "message" error)))
      (is (nil? (truss/matching-error :common #"message" error)))
      (is (nil? (truss/matching-error :common  "message" outer)))
      (is (nil? (truss/matching-error :common #"message" outer)))])

   (let [error (ex-info "Exact" {})]
     [(is (identical? error (truss/matching-error error error)))
      (is (nil? (truss/matching-error (ex-info "Other" {}) error)))])

   #?(:clj
      (let [kind  (proxy [Exception] ["Kind"]  (equals [_] true))
            error (proxy [Exception] ["Error"] (equals [_] true))]
        [(is (= kind error))
         (is (nil? (truss/matching-error kind error)))]))

   (let [inner #?(:clj  (java.net.SocketException. "Inner")
                  :cljs (js/TypeError.             "Inner"))
         kind  #?(:clj  java.net.SocketException
                  :cljs #(instance? js/TypeError %))
         outer (ex-info "Outer" {} inner)]

     [(is (identical? (truss/matching-error   kind  outer) inner))
      (is (identical? (truss/matching-error #{kind} outer) inner))
      (is (identical? (throws kind (throw outer)) inner))
      (is (identical? (truss/matching-error :common #{"Inner"} outer) inner))])

   (let [inner (ex-info "Inner info" {:id :inner})
         outer (ex-info "Outer info" {:id :outer} inner)]
     [(is (identical? (truss/matching-error :ex-info #{"Outer info"} outer) outer))
      (is (identical? (truss/matching-error :ex-info #{"Inner info"} outer) inner))
      (is (identical? (truss/matching-error :ex-info #{{:id :inner}} outer) inner))
      (is (identical? (throws :ex-info #{{:id :inner}} (throw outer)) inner))])

   (is (truss/error? (truss/matching-error #{:ex-info :common} #{"foobar" "not a function" "cannot be cast"}
                     (truss/try* ("") (catch :all t t)))))])

(deftest _catching-rf
  [(is (=   (reduce (truss/catching-rf            (fn [acc in] (conj acc         in)))  [] [:a :b]) [:a :b]))
   (is (=   (reduce (truss/catching-rf {:id :foo} (fn [acc in] (conj acc         in)))  [] [:a :b]) [:a :b]))
   (is (->> (reduce (truss/catching-rf {:id :foo} (fn [acc in] (conj acc (throw! in)))) [] [:a :b])
         (throws? :common {:id :foo :call '(rf acc in) :args {:in {:value :a}}})))

   (is (=   (reduce-kv (truss/catching-rf (fn [acc k v] (assoc acc k         v)))  {} {:a :A}) {:a :A}))
   (is (->> (reduce-kv (truss/catching-rf (fn [acc k v] (assoc acc k (throw! v)))) {} {:a :A})
         (throws? :common {:call '(rf acc k v) :args {:k {:value :a} :v {:value :A}}})))])

(deftest _catching-xform
  [(is (=   (transduce (truss/catching-xform (map identity)) (completing (fn [acc in] (conj acc in))) [] [:a :b]) [:a :b]))
   (is (->> (transduce (truss/catching-xform (map ex1!))     (completing (fn [acc in] (conj acc in))) [] [:a :b])
         (throws? :common {:call '(rf acc in) :args {:in {:value :a}}}))
     "Error in xform")

   (is (=   (transduce (truss/catching-xform (map identity)) (completing (fn [acc in] (conj acc         in)))  [] [:a :b]) [:a :b]))
   (is (->> (transduce (truss/catching-xform (map identity)) (completing (fn [acc in] (conj acc (throw! in)))) [] [:a :b])
         (throws? :common {:call '(rf acc in) :args {:in {:value :a}}}))
     "Error in rf")])

;;;; Truss exceptions

(deftest _ex-info
  [(is (submap? (ex-data (truss/ex-info "msg")) {:ns string?, :coords vector?}))
   (is (= :given (-> (truss/ex-info "msg" {:truss/ctx :given}) ex-data :truss/ctx)))
   (is (submap?
         (ex-data
           (truss/with-ctx {:actual :ctx}
             (truss/ex-info "msg" {:ns :given, :coords :given, :truss/ctx :given})))
         {:ns string?, :coords vector?, :truss/ctx {:actual :ctx}}))
   (is (submap?
         (ex-data
           (truss/with-ctx {:actual :ctx}
             (throws
               (truss/ex-info! "msg" {:ns :given, :coords :given, :truss/ctx :given}))))
         {:ns string?, :coords vector?, :truss/ctx {:actual :ctx}}))
   (is (submap? (ex-data (truss/with-ctx {:c :c1} (truss/ex-info "msg")))          {:truss/ctx {:c :c1}}))
   (is (submap? (ex-data (truss/with-ctx {:c :c1} (truss/ex-info "msg" {:d :d1}))) {:truss/ctx {:c :c1}, :d :d1}))
   (is (submap? (ex-data
                  (truss/with-ctx {:c1 :c1a, :c2 :c2a}
                    (truss/with-ctx+ {:c2 :c2b}
                      (truss/with-ctx+ #(assoc % :c3 :c3a)
                        (truss/ex-info "msg" {:d :d1})))))

         {:truss/ctx {:c1 :c1a, :c2 :c2b, :c3 :c3a}, :d :d1
          :ns     string?
          :coords vector?}))])

;;;; Callsites

#?(:clj
   (do
     (defmacro macro1 "Non-preserving" [& body]                             `(macro2 ~@body))
     (defmacro macro2 "Preserving"     [& body] (truss/keep-callsite        `(macro3 ~@body)))
     (defmacro macro3 "Preserving"     [& body] (truss/merge-callsite &form `(macro4 ~@body)))
     (defmacro macro4 "Preserving"     [& body] `(do ~(truss/keep-callsite  `(macro5 ~@body))))
     (defmacro macro5 "Consumer"       [& body] `(do ~(truss/callsite-coords &form)))))

(deftest _callsites
  [(is (nil?    (macro1)) "&form meta lost at macro1->2")
   (is (vector? (macro2)) "&form meta preserved from macro2 through macro5")])

;;;; Assertions

(defn-              pred-fn1 [pass?] (boolean pass?))
(defn- ^:truss/safe pred-fn2 [pass?] (boolean pass?))
#?(:clj (defmacro pred-macro [x]   `(string? ~x)))
#?(:clj (defmacro have-macro [n x] `(have string? (do (swap! ~n inc) ~x)))) ; Ref. issue #12

#?(:clj
   (defmacro parse-pred-form [pred-form]
     (let [[safe? form] (impl/parse-pred-form &env pred-form false 'x)]
       [safe?
        (clojure.walk/postwalk
          (fn [x]
            (cond
              (and (qualified-symbol? x) (contains? #{"clojure.core" "cljs.core"} (namespace x))) (symbol "core" (name x))
              (and (qualified-symbol? x) (= "taoensso.truss.impl"                 (namespace x))) (symbol "impl" (name x))
              :else x))
          `'~form)])))

(comment (parse-pred-form [:or nil? string?]))

(deftest _predicates
  [(is (= (parse-pred-form seq)                                        '[false core/seq]))
   (is (= (parse-pred-form string?)                                    '[true  core/string?]))
   (is (= (parse-pred-form (fn [x] false))                             '[false      (fn [x] false)]))
   (is (= (parse-pred-form [:or nil? string?])                         '[true  (core/fn [x] (impl/or? (core/nil? x) (core/string?  x)))]))
   (is (= (parse-pred-form [:or nil? seq])                             '[false (core/fn [x] (impl/or? (core/nil? x) (core/seq      x)))]))
   (is (= (parse-pred-form [:and string? integer?])                    '[true  (core/fn [x] (impl/and? (core/string? x) (core/integer? x)))]))
   (is (= (parse-pred-form [:not string?])                             '[true  (core/fn [x] (if (core/string? x) false true))]))
   (is (= (parse-pred-form [:or nil? (fn [x] false)])                  '[false (core/fn [x] (impl/or? (core/nil? x) ((fn [x] false) x)))]))
   (is (= (parse-pred-form [:or nil? [:and number? integer? pos?]])    '[false (core/fn [x] (impl/or? (core/nil? x) (impl/and? (core/number? x) (core/integer? x) (core/pos? x))))]))
   (is (= (parse-pred-form [:or nil? [:el #{1 2 3}]])                  '[false (core/fn [x] (impl/or? (core/nil? x) (core/contains? #{1 3 2} x)))]))
   (is (= (parse-pred-form [:or #{1 2 3} {:a :A} :kw])                 '[true  (core/fn [x] (impl/or? (#{1 3 2} x) ({:a :A} x) (:kw x)))]))
   (is (= (parse-pred-form [:ks<= #{:a :b :c}])                        '[false (core/fn [x] (impl/ks<= #{:c :b :a} x))]))
   (is (= (parse-pred-form [:ks<= my-ks])                              '[false (core/fn [x] (impl/ks<= (impl/ensure-set my-ks) x))]))
   (is (= (parse-pred-form [:or nil? [:or seq]])                       '[false (core/fn [x] (impl/or? (core/nil? x) (core/seq x)))]))
   (is (= (parse-pred-form [:or nil? [:or seq] integer?])              '[true  (core/fn [x] (impl/or? (core/nil? x) (impl/catching (core/seq x)) (core/integer? x)))]))
   (is (= (parse-pred-form pred-fn1)                                   '[false taoensso.truss-tests/pred-fn1]))
   (is (= (parse-pred-form pred-fn2)                                   '[true  taoensso.truss-tests/pred-fn2]))
   (is (= (parse-pred-form pred-macro)                                 '[false taoensso.truss-tests/pred-macro]))
   (is (= (parse-pred-form  (fn [x] x))                                '[false (fn [x] x)]))

   #?(:clj
      [(is (throws? :common #"exactly 1 argument"
             (macroexpand '(taoensso.truss/have [:n= 2 3] [1 2]))))
       (is (throws? :common #"exactly 1 argument"
             (macroexpand '(taoensso.truss/have [:n=] [1 2]))))
       (is (throws? :common #"1≤n≤3 arguments"
             (macroexpand '(taoensso.truss/have [:and string? seqable? coll? vector?] []))))])])

;;;; Assertions

(deftest _assertions
  [#?(:clj
      (testing "Malformed forms"
        [(is (throws? :common #"at least one argument"    (macroexpand '(taoensso.truss/have))))
         (is (throws? :common #"at least one collection"  (macroexpand '(taoensso.truss/have string? :in))))
         (is (throws? :common #"at least one collection"
               (macroexpand '(taoensso.truss/have string? :in :data {:id :test}))))]))

   (testing "Falsey args"
     [(is (= :data (have :data)))
      (is (= :data (have keyword? :data)))
      (is (= [:foo :data]      (have keyword? :foo :data)))
      (is (= [:foo :bar :data] (have keyword? :foo :bar :data)))
      (is (= {:data "value"} (have :data {:data "value"})))
      (is      (throws? (have       nil)))
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
      (is (throws? :common {:arg {:value 6}} (let [x 5 y 6] (have odd?  x x x y x))))
      (is (throws? :common {:arg {:value 1}} (let [x 0 y 1] (have zero? x x x y x))))
      (is (throws? :common {:arg {:value "foo"}}
            ((fn foo [x] {:pre [(have? integer? x)]} (* x x)) "foo")))])

   (testing "Sequential lazy arg evaluation"
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

   (testing "Side effects"
     [(let [n (atom 0)] [(is (= (have string? (do (swap! n inc) "str")) "str")) (is (= @n 1))])
      (let [n (atom 0)] [(is (throws? (have string? (do (swap! n inc) :kw))))   (is (= @n 1))])
      (let [n (atom 0)] [(is (= (have-macro n "str") "str"))                    (is (= @n 1))])
      (let [n (atom 0)] [(is (throws? (have-macro n :kw)))                      (is (= @n 1))])

      (let [n (atom 0)] [(is (= (have vector? [(swap! n inc)]) [1]))            (is (= @n 1))])
      (let [n (atom 0)] [(is (= (have map?    {:n (swap! n inc)}) {:n 1}))      (is (= @n 1))])
      (let [n (atom 0)] [(is (= (have set?    #{(swap! n inc)}) #{1}))          (is (= @n 1))])
      (let [n (atom 0)] [(is (= (have #(map? %) {:n (swap! n inc)}) {:n 1}))    (is (= @n 1))])
      (let [n (atom 0)] [(is (throws? :common {:arg {:value [1]}}
                                  (have string? [(swap! n inc)])))               (is (= @n 1))])
      (let [n (atom 0)] [(is (throws? (have (fn [_] false) #{(swap! n inc)})))  (is (= @n 1))])])

   (testing "Throwing predicates"
     (let [zero! (fn [n] (if (zero? n) true (throw (ex-info "" {}))))]
       [(is (truss/error? (ex-cause (throws :common (have zero! 1)))))
        (is (truss/error? (ex-cause (throws :common (have zero! 0 0 1 0)))))
        (is (= [0 0 0 0]                            (have zero! 0 0 0 0)))])

     (let [pred  (fn [_] (throw (ex-info "Pred failed" {})))
           info  (binding [truss/*failed-assertion-handler* identity]
                   (have pred :truss/exception))
           error (truss/failed-assertion-ex-info info)
           legacy-data
           (binding [truss/*failed-assertion-handler* (truss/legacy-error-fn force)]
             (have pred :truss/exception))]
       [(is (str/includes? (ex-message error) "Error evaluating pred: Pred failed"))
        (is (= (get-in (ex-data error) [:arg :value]) :truss/exception))
        (is (str/includes? (force (:msg_ legacy-data)) "Error evaluating pred: Pred failed"))])

     (let [info (binding [truss/*failed-assertion-handler* identity]
                  (have string? :truss/exception))]
       [(is (nil? (:error info)))
        (is (= (get-in (ex-data (truss/failed-assertion-ex-info info)) [:arg :value])
              :truss/exception))]))

   (testing "Throwing args"
     (let [result (throws :common (have string? (throw (ex-info "" {}))))]
       [(is (truss/error?           (ex-cause result)))
        (is (str/includes?          (ex-message result) "Error evaluating arg:"))
        (is (= :truss/exception (-> (ex-data  result) :arg :value)))]))

   (testing "Throwing data"
     [(is (throws? :ex-info {:data :truss/exception}
            (have string? 5 :data (throw (ex-info "" {})))))])

   (testing ":in"
     [(is (= ["a" "b"] (have string? :in ["a" "b"])))
      (is (= ["a" "b"] (have string? :in (if true ["a" "b"] [1 2]))))
      (is (= [nil]     (have nil?   :in [nil])))
      (is (= [false]   (have false? :in [false])))
      (is (= [[nil] [false]] (have [:or nil? false?] :in [nil] [false])))
      (is (= [:a :b]
            (binding [truss/*failed-assertion-handler* nil]
              (have string? :in [:a :b]))))

      (is (throws? :common {:arg {:value 1}} (have string? :in (if false ["a" "b"] [1 2]))))
      (is (= ["0" "1" "2"]                   (have string? :in (mapv str (range 3)))))

      (is (throws? :common {:arg {:value 1}} (have string? :in ["a" 1])))
      (is (= [["a" "b"] ["a" "b"]]           (have string? :in ["a" "b"] ["a" "b"])))
      (is (throws? :common {:arg {:value 1}} (have string? :in ["a" "b"] ["a" "b" 1])))

      (is (throws? :common {:arg {:form [:in 'xs]}}  (let [xs  [1 2 :3]] (have integer? :in xs)))      "Sensible :arg/form (:in one)")
      (is (throws? :common {:arg {:form [:in 'xs2]}} (let [xs1 [1 2  3]
                                                           xs2 [1 2 :3]] (have integer? :in xs1 xs2))) "Sensible :arg/form (:in many)")])

   (testing "Special preds"
     [(is (= nil       (have [:or nil? string?] nil)))
      (is (= "hello"   (have [:or nil? string?] "hello")))
      (is (= "hello"   (have [:or pos? string?] "hello")))
      (is (= ["a" "b"] (have [:or pos? string?] "a" "b")))

      (is (throws? :common {:arg {:value -5}} (have [:or pos? string?] -5)))

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

   (testing "Data and context"
     [(is (= "5"                               (have string? "5" :data {:user 101})))
      (is (throws? :common {:data {:user 101}} (have string?  5  :data {:user 101})))

      (truss/with-ctx {:user 101}
        [(is (= "5"                                    (have string? "5")))
         (is (throws? :common {:truss/ctx {:user 101}} (have string?  5)))
         (is (throws? :common {:truss/ctx {:user 101}
                               :data      {:name "Stu"}}
               (have string? 5 :data      {:name "Stu"})))])])

   (testing "Resolved preds"
     [(is (throws? :common {:pred 'taoensso.truss-tests/pred-fn1} (have pred-fn1 false)))
      (is (throws? :common {:pred `core/some?}                    (have nil)))
      (is (throws? :common {:pred `core/string?}  (have      string?  nil)))
      (is (throws? :common {:pred '[:or string?]} (have [:or string?] nil)))])

   (testing "Failed assertion handler"
     [(is (= :my-kw (binding [truss/*failed-assertion-handler* nil]      (have string? :my-kw))))
      (let [error (ex-info "Arg eval failed" {})]
        [(is (identical? error
               (throws (binding [truss/*failed-assertion-handler* nil]
                         (have string? (throw error))))))
         (is (identical? error
               (throws (binding [truss/*failed-assertion-handler* nil]
                         (have (fn [x] (string? x)) (throw error))))))

         (let [next-evaluated?_ (atom false)]
           [(is (identical? error
                  (throws (binding [truss/*failed-assertion-handler* nil]
                            (have string?
                              (throw error)
                              (do (reset! next-evaluated?_ true) "next"))))))
            (is (false? @next-evaluated?_))])])

      (let [pred-error (ex-info "Pred failed" {})]
        (is (= :arg
              (binding [truss/*failed-assertion-handler* nil]
                (have (fn [_] (throw pred-error)) :arg)))))

      (testing "Non-throwing handler mirrors elision for bool assertions"
        [(is (true? (binding [truss/*failed-assertion-handler* nil] (have?  string?        5))))
         (is (true? (binding [truss/*failed-assertion-handler* nil] (have!? string?        5))))
         (is (true? (binding [truss/*failed-assertion-handler* nil] (have?  (fn [_] false) 5)))       "Unsafe pred branch")
         (is (true? (binding [truss/*failed-assertion-handler* nil] (have?  string?        (+ 2 3)))) "Eval'd arg branch")
         (is (true? (binding [truss/*failed-assertion-handler* nil] (have?  (fn [_] false) (+ 2 3)))) "Eval'd arg, unsafe pred branch")
         (is (true? (binding [truss/*failed-assertion-handler* nil] (have?  string? 5 6))))
         (is (true? (binding [truss/*failed-assertion-handler* nil] (have?  string? :in [5 6]))))
         (is (nil?
               (binding [truss/*failed-assertion-handler* nil]
                 ((fn [x] {:pre [(have? string? x)]} x) nil)))
           "Disabled handler can't trip `:pre`/`:post` conditions, even on falsey args")

         (is (true? (binding [truss/*failed-assertion-handler* (fn [_] :handled)] (have? string? 5)))
           "`have?` keeps boolean contract with non-throwing custom handler")

         (is (= :fallback (binding [truss/*failed-assertion-handler* (fn [_] :fallback)] (have string? 5)))
           "`have` returns non-throwing custom handler's result")

         (is (= [5 6] (binding [truss/*failed-assertion-handler* (fn [_] :fallback)] (have string? :in [5 6])))
           "`:in` always returns original collection, discarding handler results")

         (let [error (ex-info "Arg eval failed" {})]
           [(is (identical? error
                  (throws (binding [truss/*failed-assertion-handler* nil]
                            (have? string? (throw error)))))
              "Disabled handler always rethrows arg eval errors")

            (is (true? (binding [truss/*failed-assertion-handler* (fn [_] :handled)]
                         (have? string? (throw error))))
              "Custom handler receives arg eval errors, may swallow by returning")])])
      (is (submap?  (binding [truss/*failed-assertion-handler* identity] (have string? :my-kw :data {:a :A, :b :B}))
            {;; :inst #?(:clj #(instance? java.time.Instant %), :cljs #(instance? js/Date %))
             :ns      "taoensso.truss-tests"
             :coords  vector?
             :pred    #(= % `core/string?)
             :arg-val :my-kw
             :data    {:a :A, :b :B}
             :error   nil}))])])

;;;;

#?(:cljs
   (defmethod test/report [:cljs.test/default :end-run-tests] [m]
     (when-not (test/successful? m)
       ;; Trigger non-zero `lein test-cljs` exit code for CI
       (throw (ex-info "ClojureScript tests failed" {})))))

#?(:cljs (test/run-tests))
