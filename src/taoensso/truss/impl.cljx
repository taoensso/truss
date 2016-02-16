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

(def ^:dynamic *-?data* nil)

(defn  non-throwing [pred] (fn [x] (catch-errors* (pred x) _ nil)))
(defn- non-throwing?
  "Returns true for some common preds that are naturally non-throwing"
  [p]
  #+cljs false ; Would need `resolve`; other ideas?
  #+clj
  (or
    (keyword? p)
    (boolean
      (#{nil? #_some? string? integer? number? keyword? float?
         set? vector? coll? list? ifn? fn? associative? sequential?
         sorted? counted? reversible?}
        (if (symbol? p) (resolve p) p)))))

(defn -xpred
  "Expands any special predicate forms and returns [<expanded-pred> <non-throwing?>]"
  [pred]
  (if-not (vector? pred)
    [pred (non-throwing? pred)]
    (let [[type a1 a2 a3] pred]
      (assert a1 "Special predicate [<special-type> <arg>] form w/o <arg>")
      (case type
        :set=             [`(fn [~'__x] (=             (set* ~'__x) (set* ~a1))) false]
        :set<=            [`(fn [~'__x] (set/subset?   (set* ~'__x) (set* ~a1))) false]
        :set>=            [`(fn [~'__x] (set/superset? (set* ~'__x) (set* ~a1))) false]
        :ks=              [`(fn [~'__x] (ks=      ~a1 ~'__x)) false]
        :ks<=             [`(fn [~'__x] (ks<=     ~a1 ~'__x)) false]
        :ks>=             [`(fn [~'__x] (ks>=     ~a1 ~'__x)) false]
        :ks-nnil?         [`(fn [~'__x] (ks-nnil? ~a1 ~'__x)) false]
        (    :el     :in) [`(fn [~'__x]      (contains? (set* ~a1) ~'__x))  false]
        (:not-el :not-in) [`(fn [~'__x] (not (contains? (set* ~a1) ~'__x))) false]

        ;; Pred composition
        (let [self (fn [?pred] (when ?pred (-xpred ?pred)))

              ;; Support recursive expansion:
              [[a1 nt-a1?] [a2 nt-a2?] [a3 nt-a3?]] [(self a1) (self a2) (self a3)]

              nt-a1    (when a1 (if nt-a1? a1 `(non-throwing ~a1)))
              nt-a2    (when a2 (if nt-a2? a2 `(non-throwing ~a2)))
              nt-a3    (when a3 (if nt-a3? a3 `(non-throwing ~a3)))
              nt-comp? (cond a3 (and nt-a1? nt-a2? nt-a3?)
                             a2 (and nt-a1? nt-a2?)
                             a1 nt-a1?)]

          (case type
            :and ; all-of
            (cond
              a3 [`(fn [~'__x] (and (~a1 ~'__x) (~a2 ~'__x) (~a3 ~'__x))) nt-comp?]
              a2 [`(fn [~'__x] (and (~a1 ~'__x) (~a2 ~'__x))) nt-comp?]
              a1 [a1 nt-a1?])

            :or  ; any-of
            (cond
              a3 [`(fn [~'__x] (or (~nt-a1 ~'__x) (~nt-a2 ~'__x) (~nt-a3 ~'__x))) true]
              a2 [`(fn [~'__x] (or (~nt-a1 ~'__x) (~nt-a2 ~'__x))) true]
              a1 [a1 nt-a1?])

            :not ; complement/none-of
            ;; Note that it's a little ambiguous whether we'd want non-throwing
            ;; behaviour here or not so choosing to interpret throws as
            ;; undefined to minimize surprise
            (cond
              a3 [`(fn [~'__x] (not (or (~a1 ~'__x) (~a2 ~'__x) (~a3 ~'__x)))) nt-comp?]
              a2 [`(fn [~'__x] (not (or (~a1 ~'__x) (~a2 ~'__x)))) nt-comp?]
              a1 [`(fn [~'__x] (not     (~a1 ~'__x))) nt-a1?])))))))

(comment
  (-xpred string?)
  (-xpred [:or string? integer? :foo]) ; t
  (-xpred [:or string? integer? seq])  ; f
  (-xpred [:or string? integer? [:and number? integer?]]) ; t
  (-xpred [:or string? integer? [:and number? pos?]])     ; f
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
          :ns-str   ns-str
          :?line    ?line
          ;; :?form (when-not (string? form) form)
          :form-str form-str
          :val      (if undefn-val? 'undefined/threw-error val)
          :val-type (if undefn-val? 'undefined/threw-error (type val))
          :?data      ?data  ; Arbitrary user data, handy for debugging
          :*?data*  *-?data* ; ''
          :?err     ?err
          :*assert* *assert*
          :elidable? assertion?})))))

(defmacro -invariant1
  "Written to maximize performance + minimize post Closure+gzip Cljs code size"
  [assertion? truthy? line pred x ?data-fn]
  (let [;; form (list pred x)
        form    (str (list pred x)) ; Better expansion gzipping
        [pred* non-throwing-pred?] (-xpred pred)
        pass-result (if truthy? true '__x)
        x-val-form
        (if-not (list? x)
          x ; x is pre-evaluated (common case); no need to wrap for throws
          `(catch-errors* ~x ~'__t
             (-invar-violation! ~assertion? ~(str *ns*) ~line '~form
               -invar-undefined-val ~'__t ~?data-fn)))]

    (if non-throwing-pred?
      `(let [~'__x ~x-val-form]
         (if (~pred* ~'__x)
           ~pass-result
           (-invar-violation! ~assertion? ~(str *ns*) ~line '~form
             ~'__x nil ~?data-fn)))

      `(let [~'__x ~x-val-form]
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
