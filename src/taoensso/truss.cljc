(ns taoensso.truss
  "An opinionated assertions (micro) library for Clojure/Script."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require [taoensso.truss.impl :as impl :refer [-invariant]]))

(comment (require '[taoensso.encore :as enc]))

;;;; CLJ-865

#?(:clj
   (defmacro keep-callsite
     "The long-standing CLJ-865 means that it's not possible for an inner
     macro to access the `&form` metadata of a wrapping outer macro. This
     means that wrapped macros lose calsite info, etc.

     This util offers a workaround for macro authors:
       (defmacro inner [] (meta &form))
       (defmacro outer [] (keep-callsite `(inner)))
       (outer) => {:keys [line column ...]}"

     {:added "v1.8.0 (2022-12-13)"}
     [form] `(with-meta ~form (meta ~'&form))))

#?(:clj
   (defn- clj-865-workaround
     "Experimental undocumented alternative CLJ-865 workaround that
     allows more precise control than `keep-callsite`."
     [macro-form args]
     (let [[a0 & an] args]
       (if-let [macro-form* (and (map? a0) (get a0 :&form))]
         [macro-form* an]
         [macro-form  args]))))

(comment (clj-865-workaround '() [{:&form "a"} "b"]))

;;;; Core API

#?(:clj
   (defmacro have
     "Takes a (fn pred [x]) => truthy, and >=1 vals.
     Tests pred against each val,trapping errors.

     If any pred test fails, throws a detailed `ExceptionInfo`.
     Otherwise returns input val/s for convenient inline-use/binding.

     Respects `*assert*`, so tests can be elided from production if desired
     (meaning zero runtime cost).

     Provides a small, simple, flexible feature subset to alternative tools like
     clojure.spec, core.typed, prismatic/schema, etc.

     Examples:

       (defn my-trim [x] (str/trim (have string? x)))

       ;; Attach arb optional info to violations using `:data`:
       (have string? x
         :data {:my-arbitrary-debug-info \"foo\"})

       ;; Assert inside collections using `:in`:
       (have string? :in [\"foo\" \"bar\"])

     Regarding use within other macros:
       Due to CLJ-865, callsite info like line number of outer macro
       will be lost. See `keep-callsite` for workaround.

     See also `have?`, `have!`."
     {:arglists '([x] [pred (:in) x] [pred (:in) x & more-xs])}
     [& args]
     (let [[&form args] (clj-865-workaround &form args)
           source       (impl/get-source    &form &env)]
       `(-invariant :elidable nil ~source ~args))))

#?(:clj
   (defmacro have?
     "Like `have` but returns `true` on successful tests.
     Handy for `:pre`/`:post` conditions. Compare:
       ((fn my-fn [] {:post [(have  nil? %)]} nil)) ; {:post [nil ]} FAILS
       ((fn my-fn [] {:post [(have? nil? %)]} nil)) ; {:post [true]} passes as intended"
     {:arglists '([x] [pred (:in) x] [pred (:in) x & more-xs])}
     [& args]
     (let [[&form args] (clj-865-workaround &form args)
           source       (impl/get-source    &form &env)]
       `(-invariant :elidable :truthy ~source ~args))))

#?(:clj
   (defmacro have!
     "Like `have` but ignores `*assert*` value (so can never be elided!).
     Useful for important conditions in production (e.g. security checks)."
     {:arglists '([x] [pred (:in) x] [pred (:in) x & more-xs])}
     [& args]
     (let [[&form args] (clj-865-workaround &form args)
           source       (impl/get-source    &form &env)]
       `(-invariant nil nil ~source ~args))))

#?(:clj
   (defmacro have!?
     "Returns `true` on successful tests, and ignores `*assert*` value
     (so can never be elided!).
  
     **WARNING**: Do NOT use in `:pre`/`:post` conditions since those always
     respect `*assert*`, contradicting the intention of the bang (`!`) here."
     {:arglists '([x] [pred (:in) x] [pred (:in) x & more-xs])}
     [& args]
     (let [[&form args] (clj-865-workaround &form args)
           source       (impl/get-source    &form &env)]
       `(-invariant :assertion :truthy ~source ~args))))

(comment :see-tests)
(comment
  (macroexpand '(have a))
  (macroexpand '(have? [:or nil? string?] "hello"))

  (enc/qb 1e6 ; [260.08 294.62]
    (with-error-fn nil                   (have? string? 5))
    (with-error-fn (fn [_] :truss/error) (have? string? 5)))

  (have string? (range 1000)))

(comment
  (enc/qb 1e6 ; [37.97 46.3 145.57 131.99 128.65]
    (string? "a")
    (have?   "a")
    (have            string?  "a" "b" "c")
    (have? [:or nil? string?] "a" "b" "c")
    (have? [:or nil? string?] "a" "b" "c" :data "foo"))

  (enc/qb 1e6 ; [75.73 75.88]
    (have  string? :in ["foo" "bar" "baz"])
    (have? string? :in ["foo" "bar" "baz"]))

  (macroexpand '(have string? 5))
  (macroexpand '(have string? 5 :data "foo"))
  (macroexpand '(have string? 5 :data (enc/get-locals)))
  (let [x :x]   (have string? 5 :data (enc/get-locals)))

  (have string? 5)
  (have string? 5 :data {:a "a"})
  (have string? 5 :data {:a (/ 5 0)})

  ((fn [x]
     (let [a "a" b "b"]
       (have string? x :data {:env (enc/get-locals)}))) 5)

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

(defn ^:no-doc get-dynamic-assertion-data
  {:deprecated "v1.7.0 (2022-11-16)"
   :doc "Prefer `get-data`"}
  [] impl/*data*)

#?(:clj
   (defmacro ^:no-doc with-dynamic-assertion-data
     {:deprecated "v1.7.0 (2022-11-16)"
      :doc "Prefer `with-data`"}
     [data & body] `(binding [impl/*data* ~data] ~@body)))
