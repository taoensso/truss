(ns taoensso.truss
  {:author "Peter Taoussanis (@ptaoussanis)"}
  #+clj
  (:require
   [clojure.set     :as set]
   [taoensso.encore :as enc :refer (if-cljs catch-errors* qb)])

  #+cljs
  (:require
   [clojure.set     :as set]
   [taoensso.encore :as enc :refer-macros (if-cljs catch-errors* qb)]))

(defn- non-throwing [pred] (fn [x] (catch-errors* (pred x) _ nil)))
(defn -invar-pred
  "Predicate shorthand transformations for convenience"
  ;; TODO (Optimization): simple compile-time transformations when possible
  [pred-form]
  (if-not (vector? pred-form) pred-form
    (let [[type p1 p2 & more] pred-form]
      (case type
        :set=     (fn [x] (=             (enc/set* x) (enc/set* p1)))
        :set<=    (fn [x] (set/subset?   (enc/set* x) (enc/set* p1)))
        :set>=    (fn [x] (set/superset? (enc/set* x) (enc/set* p1)))
        :ks=      (fn [x] (enc/ks=      p1 x))
        :ks<=     (fn [x] (enc/ks<=     p1 x))
        :ks>=     (fn [x] (enc/ks>=     p1 x))
        :ks-nnil? (fn [x] (enc/ks-nnil? p1 x))
        (:el :in)         (fn [x]      (contains? (enc/set* p1) x))
        (:not-el :not-in) (fn [x] (not (contains? (enc/set* p1) x)))

        :not ; complement/none-of
        (fn [x]
          (and (if-not p1 true (not ((-invar-pred p1) x)))
               (if-not p2 true (not ((-invar-pred p2) x)))
               (enc/revery?   #(not ((-invar-pred  %) x)) more)))

        :or ; any-of, (apply some-fn preds)
        (fn [x]
          (or  (when p1 ((non-throwing (-invar-pred p1)) x))
               (when p2 ((non-throwing (-invar-pred p2)) x))
            (enc/rsome #((non-throwing (-invar-pred  %)) x) more)))

        :and ; all-of, (apply every-pred preds)
        (fn [x]
          (and (if-not p1 true ((-invar-pred p1) x))
               (if-not p2 true ((-invar-pred p2) x))
               (enc/revery?   #((-invar-pred  %) x) more)))))))

(comment
  ((-invar-pred [:or nil? string?]) "foo")
  ((-invar-pred [:or [:and integer? neg?] string?]) 5)
  ((-invar-pred [:or zero? nil?]) nil) ; (zero? nil) throws
  )

(defn -assertion-error [msg] #+clj (AssertionError. msg) #+cljs (js/Error. msg))
(def  -invar-undefined-val :invariant/undefined-val)
(defn -invar-violation!
  ;; * http://dev.clojure.org/jira/browse/CLJ-865 would be handy for line numbers
  ;; * Clojure 1.7+'s `pr-str` dumps a ton of error info that we don't want here
  ([] (throw (ex-info "Invariant violation" {:invariant-violation? true})))
  ([assertion? ns-str ?line form val ?err ?data-fn]
   (let [;; Cider unfortunately doesn't seem to print newlines in errors...
         pattern     "Invariant violation in `%s:%s` [pred-form, val]:\n [%s, %s]"
         line-str    (or ?line "?")
         form-str    (str form)
         undefn-val? (= val -invar-undefined-val)
         val-str     (if undefn-val? "<undefined>" (str (or val "<nil>")) #_(pr-str val))
         dummy-err?  (:invariant-violation? (ex-data ?err))
         ?err        (when-not dummy-err? ?err)
         ?err-str    (when-let [e ?err] (str ?err) #_(pr-str ?err))
         msg         (let [msg (format pattern ns-str line-str form-str val-str)]
                       (cond
                         (not ?err)       msg
                         undefn-val? (str msg       "\n`val` error: " ?err-str)
                         :else       (str msg "\n`pred-form` error: " ?err-str)))
         ?data       (when-let [data-fn ?data-fn]
                       (catch-errors* (data-fn) e {:data-error e}))]

     (throw
       ;; Vestigial, we now prefer to just always throw `ex-info`s:
       ;; (if assertion? (-assertion-error msg) (ex-info msg {}))
       (ex-info msg
         {:instant  (enc/now-udt)
          :ns       ns-str
          :?line    ?line
          :?form    (when-not (string? form) form)
          :form-str form-str
          :val      (if undefn-val? 'undefined/threw-error val)
          :val-type (if undefn-val? 'undefined/threw-error (type val))
          :?data    ?data ; Arbitrary user data, handy for debugging
          :?err     ?err
          :*assert* *assert*
          :elidable? assertion?})))))

(defmacro -invariant1
  "Written to maximize performance + minimize post Closure+gzip Cljs code size"
  [assertion? truthy? line pred x ?data-fn]
  (let [;; form     (list pred x)
        form        (str (list pred x)) ; Better expansion gzipping
        pred*       (if (vector? pred) (list 'taoensso.encore/-invar-pred pred) pred)
        pass-result (if truthy? true '__x)]

    (if (list? x) ; x is a form; could throw on evaluation
      `(let [~'__x
             (catch-errors* ~x ~'__t
               (-invar-violation! ~assertion? ~(str *ns*) ~line '~form
                 -invar-undefined-val ~'__t ~?data-fn))]

         (catch-errors*
           (if (~pred* ~'__x) ~pass-result (-invar-violation!))
           ~'__t (-invar-violation! ~assertion? ~(str *ns*) ~line '~form
                   ~'__x ~'__t ~?data-fn)))

      ;; x is pre-evaluated (common case); no need to wrap for possible throws
      `(let [~'__x ~x]
         (catch-errors*
           (if (~pred* ~'__x) ~pass-result (-invar-violation!))
           ~'__t (-invar-violation! ~assertion? ~(str *ns*) ~line '~form
                   ~'__x ~'__t ~?data-fn))))))

(comment
  (macroexpand '(-invariant1 true false 1    #(string? %) "foo" nil))
  (macroexpand '(-invariant1 true false 1      string?    "foo" nil))
  (macroexpand '(-invariant1 true false 1 [:or string?]   "foo" nil))
  (qb 100000
    (string? "foo")
    (-invariant1 true false 1 string? "foo" nil) ; ~1.2x cheapest possible pred cost
    (-invariant1 true false 1 string? (str "foo" "bar") nil) ; ~3.5x ''
    )

  (-invariant1 false false 1 integer? "foo"   nil) ; Pred failure example
  (-invariant1 false false 1 zero?    "foo"   nil) ; Pred error example
  (-invariant1 false false 1 zero?    (/ 5 0) nil) ; Form error example
  )

(defmacro -invariant [assertion? truthy? line & sigs]
  (let [bang?      (= (first sigs) :!) ; For back compatibility, undocumented
        assertion? (and assertion? (not bang?))
        elide?     (and assertion? (not *assert*))
        sigs       (if bang? (next sigs) sigs)
        in?        (= (second sigs) :in) ; (have pred :in xs1 xs2 ...)
        sigs       (if in? (cons (first sigs) (nnext sigs)) sigs)

        data?      (and (> (count sigs) 2) ; Distinguish from `:data` pred
                        (= (last (butlast sigs)) :data))
        ?data-fn   (when data? `(fn [] ~(last sigs)))
        sigs       (if data? (butlast (butlast sigs)) sigs)

        auto-pred? (= (count sigs) 1) ; Unique common case: (have ?x)
        pred       (if auto-pred? 'taoensso.encore/nnil? (first sigs))
        [?x1 ?xs]  (if auto-pred?
                     [(first sigs) nil]
                     (if (nnext sigs) [nil (next sigs)] [(second sigs) nil]))
        single-x?  (nil? ?xs)
        map-fn     (if truthy? 'taoensso.encore/revery? 'clojure.core/mapv)]

    (if elide?
      (if single-x? ?x1 (vec ?xs))

      (if-not in?

        (if single-x?
          ;; (have pred x) -> x
          `(-invariant1 ~assertion? ~truthy? ~line ~pred ~?x1 ~?data-fn)

          ;; (have pred x1 x2 ...) -> [x1 x2 ...]
          (mapv (fn [x] `(-invariant1 ~assertion? ~truthy? ~line ~pred ~x ~?data-fn)) ?xs))

        (if single-x?
          ;; (have  pred :in xs) -> xs
          ;; (have? pred :in xs) -> bool
          `(~map-fn
             (fn [~'__in] ; Will (necessarily) lose exact form
               (-invariant1 ~assertion? ~truthy? ~line ~pred ~'__in ~?data-fn)) ~?x1)

          ;; (have  pred :in xs1 xs2 ...) -> [xs1   ...]
          ;; (have? pred :in xs1 xs2 ...) -> [bool1 ...]
          (mapv
            (fn [xs]
              `(~map-fn
                 (fn [~'__in] (-invariant1 ~assertion? ~truthy? ~line ~pred ~'__in ~?data-fn))
                 ~xs))
            ?xs))))))

(comment
  (qb 10000
    (have  string? :in ["foo" "bar" "baz"])
    (have? string? :in ["foo" "bar" "baz"]))

  (macroexpand '(have string? 5))
  (macroexpand '(have string? 5 :data "foo"))
  (macroexpand '(have string? 5 :data (get-env)))
  (let [x :x]   (have string? 5 :data (get-env)))

  (have string? 5)
  (have string? 5 :data {:a "a"})
  (have string? 5 :data {:a (/ 5 0)})

  (qb 100000
    (have           string?  "foo")
    (have [:or nil? string?] "foo"))

  (qb 100000
    (string? "foo")
    (have string? "foo")
    (have string? "foo" :data "bar")) ; [4.19 4.78 4.69]

  ((fn [x] (let [a "a" b "b"] (have string? x :data {:env (get-env)}))) 5))

(defmacro have
  "Invariant/assertion utility.

  Takes a pred and one or more vals. Tests pred against each val,
  trapping errors. If any pred test fails, throws a detailed assertion error.
  Otherwise returns input val/vals for convenient inline-use/binding.

  Respects *assert* value so tests can be elided from production for zero
  runtime costs.

  Provides a small, simple, flexible alternative to tools like core.typed,
  prismatic/schema, etc.

    ;; Will throw a detailed error message on invariant violation:
    (fn my-fn [x] (str/trim (have string? x)))

  May attach arbitrary debug info to assertion violations like:
    `(have string? x :data {:my-debug-info \"foo\" :env (get-env)})`

  See also `have?`, `have!`."
  {:arglists '([pred (:in) x] [pred (:in) x & more-xs])}
  [& sigs] `(-invariant :assertion nil ~(:line (meta &form)) ~@sigs))

(defmacro have?
  "Like `have` but returns `true` on successful tests. This can be handy for use
  with :pre/:post conditions. Compare:
    (fn my-fn [x] {:post [(have  nil? %)]} nil) ; {:post [nil]} FAILS
    (fn my-fn [x] {:post [(have? nil? %)]} nil) ; {:post [true]} passes as intended"
  {:arglists '([pred (:in) x] [pred (:in) x & more-xs])}
  [& sigs] `(-invariant :assertion :truthy ~(:line (meta &form)) ~@sigs))

(defmacro have!
  "Like `have` but ignores *assert* value (so can never be elided). Useful for
  important conditions in production (e.g. security checks)."
  {:arglists '([pred (:in) x] [pred (:in) x & more-xs])}
  [& sigs] `(-invariant nil nil ~(:line (meta &form)) ~@sigs))

(defmacro have!?
  "A cross between `have?` and `have!`. Not used often but can be handy for
  semantic clarification and/or to improve multi-val performance when the return
  vals aren't necessary.

  **WARNING**: Resist the temptation to use these in :pre/:post conds since
  they're always subject to *assert* and will interfere with the intent of the
  bang (`!`) here."
  {:arglists '([pred (:in) x] [pred (:in) x & more-xs])}
  [& sigs] `(-invariant :assertion :truthy ~(:line (meta &form)) ~@sigs))

(comment
  (qb 10000
    (have!  string? :in ["a" "b" "c"])
    (have!? string? :in ["a" "b" "c"])))

(comment
  (let [x 5]      (have    integer? x))
  (let [x 5]      (have    string?  x))
  (let [x 5]      (have :! string?  x))
  (let [x 5 y  6] (have odd?  x x x y x))
  (let [x 0 y :a] (have zero? x x x y x))
  (have string? (do (println "eval1") "foo")
                (do (println "eval2") "bar"))
  (have number? (do (println "eval1") 5)
                (do (println "eval2") "bar")
                (do (println "eval3") 10))
  (have nil? false)
  (have nil)
  (have false)
  (have string? :in ["a" "b"])
  (have string? :in (if true  ["a" "b"] [1 2]))
  (have string? :in (if false ["a" "b"] [1 2]))
  (have string? :in (mapv str (range 10)))
  (have string? :in ["a" 1])
  (have string? :in ["a" "b"] ["a" "b"])
  (have string? :in ["a" "b"] ["a" "b" 1])
  ((fn foo [x] {:pre [(have? integer? x)]} (* x x)) "foo")
  (macroexpand '(have a))
  (have? [:or nil? string?] "hello")
  (macroexpand '(have? [:or nil? string?] "hello"))
  (have? [:set>= #{:a :b}]    [:a :b :c])
  (have? [:set<= [:a :b :c]] #{:a :b})

  ;; HotSpot is great with these:
  (qb 10000
    (string? "a")
    (have? "a")
    (have string? "a" "b" "c")
    (have? [:or nil? string?] "a" "b" "c")
    (have? [:or nil? string?] "a" "b" "c" :data "foo"))
  ;; [     5.59 26.48 45.82] ; 1st gen (macro form)
  ;; [     3.31 13.48 36.22] ; 2nd gen (fn form)
  ;; [0.82 1.75  7.57 27.05] ; 3rd gen (lean macro form)
  )
