(ns ^:no-doc taoensso.truss.impl
  "Private implementation details."
  (:require
   [clojure.set :as set]
   #?(:clj [clojure.java.io :as jio]))
  #?(:cljs
     (:require-macros
      [taoensso.truss.impl :refer
       [catching now-inst* and? or?]])))

(comment (require '[taoensso.encore :as enc]))

;;;;

#?(:clj (defmacro legacy-assertion-ex-data? [] (= (System/getProperty "taoensso.truss.legacy-assertion-ex-data") "true")))

;;;; Encore imports

#?(:clj (defn list-form? [x] (or (list? x) (instance? clojure.lang.Cons x))))

#?(:clj  (defn          re-pattern? [x] (instance? java.util.regex.Pattern x))
   :cljs (defn ^boolean re-pattern? [x] (instance? js/RegExp               x)))

#?(:clj (defmacro now-dt*   [] (if (:ns &env) `(js/Date.) `(java.util.Date.))))
#?(:clj (defmacro now-inst* [] (if (:ns &env) `(js/Date.) `(java.time.Instant/now))))
#?(:clj
   (defmacro identical-kw? [x y]
     (if (:ns &env)
       `(cljs.core/keyword-identical? ~x ~y)
       `(identical?                   ~x ~y))))

(defn str-contains?
  #?(:cljs {:tag 'boolean})
  [s substr]
  #?(:clj  (.contains ^String s ^String substr)
     :cljs (not= -1 (.indexOf s substr))))

(defn revery? #?(:cljs {:tag 'boolean}) [pred coll] (reduce (fn [_ in] (if (pred in) true (reduced false))) true coll))
(defn revery                            [pred coll] (reduce (fn [_ in] (if (pred in) coll (reduced   nil))) coll coll))
(defn rsome                             [pred coll] (reduce (fn [_ in] (when-let [p (pred in)] (reduced p))) nil coll))
(defn assoc-some
  ([m k v      ] (if-not (nil? v) (assoc m k v) m))
  ([m     m-kvs] (reduce-kv  assoc-some m m-kvs)))

(defn ensure-set [x] (if (set? x) x (set x)))
(defn ks-nnil? #?(:cljs {:tag 'boolean}) [ks m] (revery? #(some? (get m %)) ks))
(defn ks=      #?(:cljs {:tag 'boolean}) [ks m] (and (== (count m) (count ks)) (revery? #(contains? m %) ks)))
(defn ks>=     #?(:cljs {:tag 'boolean}) [ks m] (and (>= (count m) (count ks)) (revery? #(contains? m %) ks)))
(defn ks<=     #?(:cljs {:tag 'boolean}) [ks m]
  (let [counted-ks (if (counted? ks) ks (set ks))]
    (and
      (<= (count m)     (count counted-ks))
      (let [ks-set (ensure-set counted-ks)]
        (reduce-kv (fn [_ k v] (if (contains? ks-set k) true (reduced false))) true m)))))

#?(:clj
   (defmacro catching
     ([try-expr                     ] `(catching ~try-expr ~'_ nil))
     ([try-expr error-sym catch-expr]
      (if (:ns &env)
        `(try ~try-expr (catch :default  ~error-sym ~catch-expr))
        `(try ~try-expr (catch Throwable ~error-sym ~catch-expr))))))

#?(:clj
   (defn- var-info [macro-env sym]
     (when (symbol? sym)
       (if (:ns macro-env)
         (let [ns (find-ns 'cljs.analyzer.api)
               v  (ns-resolve ns 'resolve)] ; Don't cache!
           (when-let [{:as m, var-ns :ns, var-name :name} ; ?{:keys [meta ns name ...]}
                      (@v macro-env sym)]
             (when var-ns ; Skip locals
               (assoc m :sym (symbol (str var-ns) (name var-name))))))

         (when-let [v (resolve macro-env sym)]
           (let [{:as m, var-ns :ns, var-name :name} (meta v)]
             {:var  v
              :sym  (symbol (str var-ns) (name var-name))
              :ns   var-ns
              :name var-name
              :meta
              (if-let [x (get m :arglists)]
                (assoc m :arglists `'~x) ; Quote
                (do    m))}))))))

#?(:clj (defmacro and? ([] true)  ([x] x) ([x & next] `(if ~x (and? ~@next) false))))
#?(:clj (defmacro or?  ([] false) ([x] x) ([x & next] `(if ~x true (or? ~@next)))))

;;;; Assertion predicates

#?(:clj (defn ensure-set-form [form] (if (set? form) form `(ensure-set ~form))))
#?(:clj (def ^:private safe-pred-forms
          (let [names
                (mapv name
                  '#{nil? some? string? integer? number? symbol? keyword? float?
                     set? vector? coll? list? ifn? fn? associative? sequential? delay?
                     sorted? counted? reversible? true? false? identity not boolean})]
            (-> #{}
              (into (mapv #(symbol "clojure.core" %) names))
              (into (mapv #(symbol    "cljs.core" %) names))))))

#?(:clj
   (defn parse-pred-form
     "Returns [safe? form show special?]."
     ([macro-env pred-form              ] (parse-pred-form macro-env pred-form false nil))
     ([macro-env pred-form in-comp? gsym]
      (if-not (vector? pred-form)
        ;; Standard predicate
        (let [{:keys [meta sym]} (var-info macro-env pred-form)
              safe?
              (or
                (get meta :truss/safe)
                (keyword? pred-form)
                (map?     pred-form)
                (set?     pred-form)
                (contains? safe-pred-forms sym))]

          [safe? (or sym pred-form) (or sym pred-form)])

        ;; Special predicate
        (let [[kind a1 a2 a3]      pred-form
              num-args (dec (count pred-form))
              _
              (when (or (< num-args 1) (> num-args 3))
                (throw
                  (ex-info "Truss special predicates should have 1≤n≤3 elements"
                    {:pred-form pred-form})))

              gsym (or gsym (gensym "arg"))

              [safe? body]
              (case kind
                :set=             [false `(=             (ensure-set ~gsym) ~(ensure-set-form a1))]
                :set<=            [false `(set/subset?   (ensure-set ~gsym) ~(ensure-set-form a1))]
                :set>=            [false `(set/superset? (ensure-set ~gsym) ~(ensure-set-form a1))]

                :ks=              [false `(ks=           ~(ensure-set-form a1) ~gsym)]
                :ks<=             [false `(ks<=          ~(ensure-set-form a1) ~gsym)]
                :ks>=             [false `(ks>=          ~(ensure-set-form a1) ~gsym)]
                :ks-nnil?         [false `(ks-nnil?      ~(ensure-set-form a1) ~gsym)]
                (    :el     :in) [false     `(contains? ~(ensure-set-form a1) ~gsym)]
                (:not-el :not-in) [false `(if (contains? ~(ensure-set-form a1) ~gsym) false true)]

                :n=               [false `(=  (count ~gsym) ~a1)]
                :n>=              [false `(>= (count ~gsym) ~a1)]
                :n<=              [false `(<= (count ~gsym) ~a1)]

                :instance?        [false `(instance?  ~a1 ~gsym)]
                :satisfies?       [false `(satisfies? ~a1 ~gsym)]

                (:and :or :not) ; Composition
                (let [;; Support recursive expansion
                      [sf3? a3 _ sp3?] (when a3 (parse-pred-form macro-env a3 :in-comp gsym))
                      [sf2? a2 _ sp2?] (when a2 (parse-pred-form macro-env a2 :in-comp gsym))
                      [sf1? a1 _ sp1?] (when a1 (parse-pred-form macro-env a1 :in-comp gsym))

                      a3-test (when a3 (if sp3? a3 `(~a3 ~gsym)))
                      a2-test (when a2 (if sp2? a2 `(~a2 ~gsym)))
                      a1-test (when a1 (if sp1? a1 `(~a1 ~gsym)))

                      ;; [:and ...] comp is safe if all preds are safe
                      ;; [:or  ...] comp ensures that all but last pred are safe

                      in-or?         (= kind :or)
                      [sf2? a2-test] (if (and in-or? a3 (not sf2?)) [true `(catching ~a2-test)] [sf2? a2-test])
                      [sf1? a1-test] (if (and in-or? a2 (not sf1?)) [true `(catching ~a1-test)] [sf1? a1-test])

                      sf-all? (boolean (and (if a1 sf1? true) (if a2 sf2? true) (if a3 sf3? true)))
                      body
                      (case kind
                        :or ; any-of
                        (cond
                          a3 `(or? ~a1-test ~a2-test ~a3-test)
                          a2 `(or? ~a1-test ~a2-test)
                          a1        a1-test)

                        :and ; all-of
                        (cond
                          a3 `(and? ~a1-test ~a2-test ~a3-test)
                          a2 `(and? ~a1-test ~a2-test)
                          a1         a1-test)

                        :not ; none-of, same as [:and (not a1) (not a2) ...]
                        (cond
                          a3 `(if (or? ~a1-test ~a2-test ~a3-test) false true)
                          a2 `(if (or? ~a1-test ~a2-test)          false true)
                          a1 `(if      ~a1-test                    false true)))]

                  [sf-all? body])

                (throw
                  (ex-info "Unexpected Truss special predicate kind"
                    {:pred-form pred-form})))]

          (let [safe? (boolean safe?)
                form  (if in-comp? body `(fn [~gsym] ~body))
                show  pred-form]

            [safe? form show :special]))))))

;;;; Assertions

;; User-facing record provided to `*failed-assertion-handler*`
(defrecord FailedAssertionInfo [ns coords, pred arg-form arg-val, data error])
(deftype ArgEvalError [ex]) ; Private wrapper type to identiy exceptions evaluating args or executing (pred arg) checks
(def   FalsePredError #?(:clj (Object.) :cljs (js-obj))) ; Private object to identify falsey (pred arg) checks

#?(:clj
   (defmacro assert1
     [bool? coords [psafe? pform pshow] arg-form data-fn-form]
     (let [cljs?         (:ns &env)
           [line column] coords
           eval-arg?     (list-form? arg-form)
           ns            (str *ns*)

           gs-error   (gensym "error")
           gs-arg-val (gensym "arg-val")]

       (case [(if eval-arg? :eval-arg  :local-arg)
              (if psafe?    :safe-pred :unsafe-pred)]

         [:local-arg :safe-pred]
         `(if (~pform ~arg-form)
            ~(if bool? true arg-form)
            (taoensso.truss/failed-assertion! ~ns ~line ~column ~pshow '~arg-form ~arg-form ~data-fn-form nil))

         [:local-arg :unsafe-pred]
         `(let [~gs-error (catching (if (~pform ~arg-form) nil FalsePredError) ~'e ~'e)]
            (if ~gs-error
              (taoensso.truss/failed-assertion! ~ns ~line ~column ~pshow '~arg-form ~arg-form ~data-fn-form ~gs-error)
              ~(if bool? true arg-form)))

         [:eval-arg :safe-pred]
         `(let [~gs-arg-val (catching ~arg-form ~'e (ArgEvalError. ~'e))
                ~gs-error
                (if (instance? ArgEvalError ~gs-arg-val)
                  ~gs-arg-val
                  (if (~pform ~gs-arg-val) nil FalsePredError))]

            (if ~gs-error
              (taoensso.truss/failed-assertion! ~ns ~line ~column ~pshow '~arg-form ~gs-arg-val ~data-fn-form ~gs-error)
              ~(if bool? true gs-arg-val)))

         [:eval-arg :unsafe-pred]
         `(let [~gs-arg-val (catching ~arg-form ~'e (ArgEvalError. ~'e))
                ~gs-error
                (if (instance? ArgEvalError ~gs-arg-val)
                  ~gs-arg-val
                  (catching
                    (if (~pform ~gs-arg-val) nil FalsePredError)
                    ~'e ~'e))]

            (if ~gs-error
              (taoensso.truss/failed-assertion! ~ns ~line ~column ~pshow '~arg-form ~gs-arg-val ~data-fn-form ~gs-error)
              ~(if bool? true gs-arg-val)))))))

(comment (macroexpand '(assert1 true [0 0] [true string? string?] "foo" (fn [] (+ 3 2)))))

#?(:clj
   (defmacro assert-args
     "Low-level `assert` wrapper for `have`-style API."
     [elidable? bool? coords arg-forms]
     (let [bang?      (= (first arg-forms) :!) ; For back compatibility, undocumented
           elidable?  (and elidable? (not bang?))
           elide?     (and elidable? (not *assert*))
           arg-forms  (if bang? (next arg-forms) arg-forms)
           in?        (= (second arg-forms) :in) ; (have pred :in xs1 xs2 ...)
           arg-forms  (if in? (cons (first arg-forms) (nnext arg-forms)) arg-forms)
           data-fn-form
           (when (and
                   (> (count arg-forms) 2) ; Distinguish from `:data` pred
                   (= (last (butlast arg-forms)) :data))
             `(fn [] ~(last arg-forms)))

           arg-forms  (if data-fn-form (butlast (butlast arg-forms)) arg-forms)
           auto-pred? (= (count arg-forms) 1) ; Unique common case: (have ?x)
           pred-form  (if auto-pred? `some? (first arg-forms))
           [?x1 ?xs]
           (cond
             auto-pred?        [(first arg-forms)  nil]
             (nnext arg-forms) [nil   (next arg-forms)]
             :else             [(second arg-forms) nil])

           single-x? (nil? ?xs)

           [psafe? pform pshow] (parse-pred-form &env pred-form)
           gs-ps (gensym "ps")
           gs-pf (gensym "pf")
           gs-df (gensym "df")
           gs-in (gensym "in")]

       (if elide?
         (if bool? true (if single-x? ?x1 (vec ?xs)))

         (case [(if in?       :in       :not-in)
                (if single-x? :single-x :multi-x)]

           [:not-in :single-x] ; (have* pred x) -> x or bool
           `(assert1 ~bool? ~coords [~psafe? ~pform '~pshow] ~?x1 ~data-fn-form)

           [:not-in :multi-x] ; (have* pred x1 x2 ...) -> [x1 x2 ...] or bool
           (let [body (mapv (fn [x] `(assert1 ~bool? ~coords [~psafe? ~gs-pf ~gs-ps] ~x ~gs-df)) ?xs)
                 body (if bool? `(do ~@body true) body)]
             `(let [~gs-ps '~pshow
                    ~gs-pf  ~pform
                    ~gs-df  ~data-fn-form]
                ~body))

           [:in :single-x] ; (have* pred :in xs) -> xs or bool
           (let [rfn (if bool? `revery? `revery)]
             `(let [~gs-ps '~pshow
                    ~gs-pf  ~pform
                    ~gs-df  ~data-fn-form]
                (~rfn (fn [~gs-in] (assert1 ~bool? ~coords [~psafe? ~gs-pf ~gs-ps] ~gs-in ~gs-df)) ~?x1)))

           [:in :multi-x] ; (have* pred :in xs1 xs2 ...) -> [xs1   ...] or bool
           (let [rfn  (if bool? `revery? `revery)
                 body (mapv (fn [xs] `(~rfn (fn [~gs-in] (assert1 ~bool? ~coords [~psafe? ~gs-pf ~gs-ps] ~gs-in ~gs-df)) ~xs)) ?xs)
                 body (if bool? `(do ~@body true) body)]
             `(let [~gs-ps '~pshow
                    ~gs-pf  ~pform
                    ~gs-df  ~data-fn-form]
                ~body)))))))
