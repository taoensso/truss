(ns taoensso.truss.impl
  "Private implementation details"
  #+clj  (:require [clojure.set :as set])
  #+cljs (:require [clojure.set :as set])
  #+cljs (:require-macros
          [taoensso.truss.impl :as impl-macros
           :refer (catch-errors* -invariant1)]))

(comment (require '[taoensso.encore :as enc :refer (qb)]))

;;;; Manual Encore imports
;; A bit of a nuisance but:
;;   * Allows Encore to depend on Truss (esp. nb for back-compatibility wrappers)
;;   * Allows Truss to be entirely dependency free

(defmacro if-cljs [then else] (if (:ns &env) then else))
(defmacro catch-errors*
  ;; TODO js/Error instead of :default as temp workaround for http://goo.gl/UW7773
  ([try-form error-sym error-form]
   `(if-cljs
      (try ~try-form (catch js/Error  ~error-sym ~error-form))
      (try ~try-form (catch Throwable ~error-sym ~error-form))))
  ([try-form error-sym error-form finally-form]
   `(if-cljs
      (try ~try-form (catch js/Error  ~error-sym ~error-form) (finally ~finally-form))
      (try ~try-form (catch Throwable ~error-sym ~error-form) (finally ~finally-form)))))

(defn rsome   [pred coll] (reduce (fn [acc in] (when-let [p (pred in)] (reduced p))) nil coll))
(defn revery? [pred coll] (reduce (fn [acc in] (if (pred in) true (reduced nil))) true coll))

(defn set*     [x] (if (set? x) x (set x)))
(defn ks=      [ks m] (=             (set (keys m)) (set* ks)))
(defn ks<=     [ks m] (set/subset?   (set (keys m)) (set* ks)))
(defn ks>=     [ks m] (set/superset? (set (keys m)) (set* ks)))
(defn ks-nnil? [ks m] (revery? #(not (nil? (get m %)))    ks))

(defn now-dt [] #+clj (java.util.Date.) #+cljs (js/Date.))

;;;; Truss

(defn- non-throwing [pred] (fn [x] (catch-errors* (pred x) _ nil)))
(defn -invar-pred
  "Predicate shorthand transformations for convenience"
  ;; TODO (Optimization): simple compile-time transformations when possible
  [pred-form]
  (if-not (vector? pred-form) pred-form
    (let [[type p1 p2 & more] pred-form]
      (case type
        :set=     (fn [x] (=             (set* x) (set* p1)))
        :set<=    (fn [x] (set/subset?   (set* x) (set* p1)))
        :set>=    (fn [x] (set/superset? (set* x) (set* p1)))
        :ks=      (fn [x] (ks=      p1 x))
        :ks<=     (fn [x] (ks<=     p1 x))
        :ks>=     (fn [x] (ks>=     p1 x))
        :ks-nnil? (fn [x] (ks-nnil? p1 x))
        (:el :in)         (fn [x]      (contains? (set* p1) x))
        (:not-el :not-in) (fn [x] (not (contains? (set* p1) x)))

        :not ; complement/none-of
        (fn [x]
          (and (if-not p1 true (not ((-invar-pred p1) x)))
               (if-not p2 true (not ((-invar-pred p2) x)))
               (revery?       #(not ((-invar-pred  %) x)) more)))

        :or ; any-of, (apply some-fn preds)
        (fn [x]
          (or (when p1 ((non-throwing (-invar-pred p1)) x))
              (when p2 ((non-throwing (-invar-pred p2)) x))
              (rsome  #((non-throwing (-invar-pred  %)) x) more)))

        :and ; all-of, (apply every-pred preds)
        (fn [x]
          (and (if-not p1 true ((-invar-pred p1) x))
               (if-not p2 true ((-invar-pred p2) x))
               (revery?       #((-invar-pred  %) x) more)))))))

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
   (let [fmt-msg
         (fn [x1 x2 x3 x4]
           ;; Cider unfortunately doesn't seem to print newlines in errors
           (str "Invariant violation in `" x1 ":" x2 "` [pred-form, val]:"
                "\n [" x3 ", " x4 "]"))

         line-str    (or ?line "?")
         form-str    (str form)
         undefn-val? (= val -invar-undefined-val)
         val-str     (if undefn-val? "<undefined>" (str (or val "<nil>")) #_(pr-str val))
         dummy-err?  (:invariant-violation? (ex-data ?err))
         ?err        (when-not dummy-err? ?err)
         ?err-str    (when-let [e ?err] (str ?err) #_(pr-str ?err))
         msg         (let [msg (fmt-msg ns-str line-str form-str val-str)]
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
         {:dt       (now-dt)
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
        pred*       (if (vector? pred) (list 'taoensso.truss.impl/-invar-pred pred) pred)
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
