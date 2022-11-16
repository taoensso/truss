(ns taoensso.truss
  "An opinionated assertions API for Clojure/Script."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require [taoensso.truss.impl :as impl :refer [-invariant]]))

(comment (require '[taoensso.encore :as enc]))

;;;; Core API

(defmacro have
  "Takes a pred and one or more vals. Tests pred against each val,
  trapping errors. If any pred test fails, throws a detailed assertion error.
  Otherwise returns input val/vals for convenient inline-use/binding.

  Respects *assert* value so tests can be elided from production for zero
  runtime costs.

  Provides a small, simple, flexible feature subset to alternative tools like
  clojure.spec, core.typed, prismatic/schema, etc.

    ;; Will throw a detailed error message on invariant violation:
    (fn my-fn [x] (str/trim (have string? x)))

  You may attach arbitrary debug info to assertion violations like:
    `(have string? x :data {:my-arbitrary-debug-info \"foo\"})`

  See also `have?`, `have!`."
  {:arglists '([pred (:in) x] [pred (:in) x & more-xs])}
  [& args] `(-invariant :elidable nil ~(:line (meta &form)) ~@args))

(defmacro have?
  "Like `have` but returns `true` on successful tests. In particular, this
  can be handy for use with :pre/:post conditions. Compare:
    (fn my-fn [x] {:post [(have  nil? %)]} nil) ; {:post [nil]} FAILS
    (fn my-fn [x] {:post [(have? nil? %)]} nil) ; {:post [true]} passes as intended"
  {:arglists '([pred (:in) x] [pred (:in) x & more-xs])}
  [& args] `(-invariant :elidable :truthy ~(:line (meta &form)) ~@args))

(defmacro have!
  "Like `have` but ignores *assert* value (so can never be elided). Useful
  for important conditions in production (e.g. security checks)."
  {:arglists '([pred (:in) x] [pred (:in) x & more-xs])}
  [& args] `(-invariant nil nil ~(:line (meta &form)) ~@args))

(defmacro have!?
  "Specialized cross between `have?` and `have!`. Not used often but can be
  handy for semantic clarification and/or to improve multi-val performance
  when the return vals aren't necessary.

  **WARNING**: Do NOT use in :pre/:post conds since those are ALWAYS subject
  to *assert*, directly contradicting the intention of the bang (`!`) here."
  {:arglists '([pred (:in) x] [pred (:in) x & more-xs])}
  [& args] `(-invariant :assertion :truthy ~(:line (meta &form)) ~@args))

(comment :see-tests)
(comment
  (macroexpand '(have a))
  (macroexpand '(have? [:or nil? string?] "hello"))

  (enc/qb 1e5
    (with-error-fn nil                   (have? string? 5))
    (with-error-fn (fn [_] :truss/error) (have? string? 5)))

  (have string? (range 1000)))

(comment
  ;; HotSpot is great with these:
  (enc/qb 1e4
    (string? "a")
    (have?   "a")
    (have            string?  "a" "b" "c")
    (have? [:or nil? string?] "a" "b" "c")
    (have? [:or nil? string?] "a" "b" "c" :data "foo"))
  ;; [     5.59 26.48 45.82     ] ; 1st gen (macro form)
  ;; [     3.31 13.48 36.22     ] ; 2nd gen (fn form)
  ;; [0.82 1.75  7.57 27.05     ] ; 3rd gen (lean macro form)
  ;; [0.4  0.47  1.3  1.77  1.53] ; 4th gen (macro preds)

  (enc/qb 1e4
    (have  string? :in ["foo" "bar" "baz"])
    (have? string? :in ["foo" "bar" "baz"]))

  (macroexpand '(have string? 5))
  (macroexpand '(have string? 5 :data "foo"))
  (macroexpand '(have string? 5 :data (enc/get-env)))
  (let [x :x]   (have string? 5 :data (enc/get-env)))

  (have string? 5)
  (have string? 5 :data {:a "a"})
  (have string? 5 :data {:a (/ 5 0)})

  ((fn [x]
     (let [a "a" b "b"]
       (have string? x :data {:env (enc/get-env)}))) 5)

  (do
    (set! *assert* false)
    (have? integer? 4.0))

  ;; Combinations: truthy?, single?, in? (8 combinations)
  (do (def i1 1) (def v1 [1 2 3]) (def s1 #{1 2 3}))
  (macroexpand '(have? integer?      1))
  (macroexpand '(have? integer?      1 2 i1))
  (macroexpand '(have? integer? :in [1 2 i1]))
  (macroexpand '(have? integer? :in [1 2] [3 4 i1] v1))
  (macroexpand '(have  integer?      1))
  (macroexpand '(have  integer?      1 2 i1))
  (macroexpand '(have  integer? :in [1 2 i1]))
  (macroexpand '(have  integer? :in [1 2] [3 4 i1] v1))

  (have? integer? :in s1)
  (have  integer? :in s1)
  (have  integer? :in #{1 2 3})
  (have  integer? :in #{1 2 3} [4 5 6] #{7 8 9} s1))

;;;; Utils

(defn get-data
  "Returns current value of dynamic assertion data."
  [] impl/*data*)

(defmacro with-data
  "Executes body with dynamic assertion data bound to given value.
  This data will be included in any violation errors thrown by body."
  [data & body] `(binding [impl/*data* ~data] ~@body))

(comment (with-data "foo" (have string? 5 :data "bar")))

(defn-   -error-fn [f] (if (= f :default) impl/default-error-fn f))
(defn set-error-fn!
  "Sets the root (fn [data-map-delay]) called on invariant violations."
  [f]
  #?(:cljs (set!             impl/*error-fn*        (-error-fn f))
     :clj  (alter-var-root #'impl/*error-fn* (fn [_] (-error-fn f)))))

(defmacro with-error-fn [f & body]
  `(binding [impl/*error-fn* ~(-error-fn f)] ~@body))

;;;; Deprecated

(defn get-dynamic-assertion-data
  ^{:deprecated "v1.7.0 (2022-11-16)"
    :doc "Prefer `get-data`"}
  [] impl/*data*)

(defmacro with-dynamic-assertion-data
  ^{:deprecated "v1.7.0 (2022-11-16)"
    :doc "Prefer `with-data`"}
  [data & body] `(binding [impl/*data* ~data] ~@body))
