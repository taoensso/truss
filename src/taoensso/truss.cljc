(ns taoensso.truss
  "An opinionated assertions (micro) library for Clojure/Script."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require [taoensso.truss.impl :as impl :refer [-invariant]]))

(comment (require '[taoensso.encore :as enc]))

;;;;

#?(:clj
   (defn- clj-865-workaround
     "Experimental undocumented alternative CLJ-865 workaround that
     allows more precise control than `keep-callsite`."
     [macro-amp-form args]
     (let [[a0 & an] args]
       (if-let [given-amp-form (and (map? a0) (get a0 :&form))]
         [given-amp-form an]
         [macro-amp-form args]))))

;;;; Core API

#?(:clj
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

     Re: use of Truss assertions within other macro bodies:
       Due to CLJ-865, call site information (e.g. line number) of
       outer macro will unfortunately be lost.

       See `keep-callsite` util for a workaround.

     See also `have?`, `have!`."
     {:arglists '([x] [pred (:in) x] [pred (:in) x & more-xs])}
     [& args]
     (let [[&form args] (clj-865-workaround &form args)
           source       (impl/get-source    &form &env)]
       `(-invariant :elidable nil ~source ~args))))

#?(:clj
   (defmacro have?
     "Like `have` but returns `true` on successful tests. In particular, this
     can be handy for use with :pre/:post conditions. Compare:
       (fn my-fn [x] {:post [(have  nil? %)]} nil) ; {:post [nil]} FAILS
       (fn my-fn [x] {:post [(have? nil? %)]} nil) ; {:post [true]} passes as intended"
     {:arglists '([x] [pred (:in) x] [pred (:in) x & more-xs])}
     [& args]
     (let [[&form args] (clj-865-workaround &form args)
           source       (impl/get-source    &form &env)]
       `(-invariant :elidable :truthy ~source ~args))))

#?(:clj
   (defmacro have!
     "Like `have` but ignores *assert* value (so can never be elided). Useful
     for important conditions in production (e.g. security checks)."
     {:arglists '([x] [pred (:in) x] [pred (:in) x & more-xs])}
     [& args]
     (let [[&form args] (clj-865-workaround &form args)
           source       (impl/get-source    &form &env)]
       `(-invariant nil nil ~source ~args))))

#?(:clj
   (defmacro have!?
     "Specialized cross between `have?` and `have!`. Not used often but can be
     handy for semantic clarification and/or to improve multi-val performance
     when the return vals aren't necessary.

     **WARNING**: Do NOT use in :pre/:post conds since those are ALWAYS subject
     to *assert*, directly contradicting the intention of the bang (`!`) here."
     {:arglists '([x] [pred (:in) x] [pred (:in) x & more-xs])}
     [& args]
     (let [[&form args] (clj-865-workaround &form args)
           source       (impl/get-source    &form &env)]
       `(-invariant :assertion :truthy ~source ~args))))

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

#?(:clj
   (defmacro keep-callsite
     "CLJ-865 unfortunately means that it's currently not possible
     for an inner macro to access the &form metadata of an outer macro.

     This means that inner macros lose call site information like the
     line number of the outer macro.

     This util offers a workaround for macro authors:

       (defmacro my-macro1 [x]                `(truss/have ~x))  ; W/o  call site info
       (defmacro my-macro2 [x] (keep-callsite `(truss/have ~x))) ; With call site info"

     {:added "v1.8.0 (2022-12-13)"}
     [& body] `(with-meta (do ~@body) (meta ~'&form))))

(comment
  (defmacro my-macro1 [x]                `(have ~x))
  (defmacro my-macro2 [x] (keep-callsite `(have ~x)))

  (my-macro1 nil)
  (my-macro2 nil))

(defn get-data
  "Returns current value of dynamic assertion data."
  [] impl/*data*)

#?(:clj
   (defmacro with-data
     "Executes body with dynamic assertion data bound to given value.
     This data will be included in any violation errors thrown by body."
     [data & body] `(binding [impl/*data* ~data] ~@body)))

(comment (with-data "foo" (have string? 5 :data "bar")))

(defn-   -error-fn [f] (if (= f :default) impl/default-error-fn f))
(defn set-error-fn!
  "Sets the root (fn [data-map-delay]) called on invariant violations."
  [f]
  #?(:cljs (set!             impl/*error-fn*         (-error-fn f))
     :clj  (alter-var-root #'impl/*error-fn* (fn [_] (-error-fn f)))))

#?(:clj
   (defmacro with-error-fn [f & body]
     `(binding [impl/*error-fn* ~(-error-fn f)] ~@body)))

;;;; Deprecated

(defn get-dynamic-assertion-data
  {:deprecated "v1.7.0 (2022-11-16)"
   :doc "Prefer `get-data`"}
  [] impl/*data*)

#?(:clj
   (defmacro with-dynamic-assertion-data
     {:deprecated "v1.7.0 (2022-11-16)"
      :doc "Prefer `with-data`"}
     [data & body] `(binding [impl/*data* ~data] ~@body)))
