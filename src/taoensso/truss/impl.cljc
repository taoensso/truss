(ns ^:no-doc taoensso.truss.impl
  "Private implementation details."
  (:require
   [clojure.set :as set]
   #?(:clj [clojure.java.io :as io]))
  #?(:cljs (:require-macros [taoensso.truss.impl :refer [catching]])))

(comment (require '[taoensso.encore :as enc]))

;;;; TODO
;; - Namespaced kw registry like clojure.spec, (truss/def <kw> <pred>)?
;; - Ideas for easier sharing of composed preds?

;;;; Manual Encore imports
;; A bit of a nuisance but:
;;   - Allows Encore to depend on Truss (esp. nb for back-compatibility wrappers).
;;   - Allows Truss to be entirely dependency free.

#?(:clj (defmacro if-cljs [then else] (if (:ns &env) then else)))
#?(:clj
   (defmacro catching
     "Cross-platform try/catch/finally."
     ;; Very unfortunate that CLJ-1293 has not yet been addressed
     ([try-expr                     ] `(catching ~try-expr ~'_ nil))
     ([try-expr error-sym catch-expr]
      `(if-cljs
         (try ~try-expr (catch js/Error  ~error-sym ~catch-expr))
         (try ~try-expr (catch Throwable ~error-sym ~catch-expr))))
     ([try-expr error-sym catch-expr finally-expr]
      `(if-cljs
         (try ~try-expr (catch js/Error  ~error-sym ~catch-expr) (finally ~finally-expr))
         (try ~try-expr (catch Throwable ~error-sym ~catch-expr) (finally ~finally-expr))))))

(defn rsome   [pred coll]       (reduce (fn [acc in] (when-let [p (pred in)] (reduced p))) nil coll))
(defn revery? [pred coll]       (reduce (fn [acc in] (if (pred in) true (reduced nil))) true coll))
(defn revery  [pred coll] (when (reduce (fn [acc in] (if (pred in) true (reduced nil))) true coll) coll))

(comment (revery integer? [1 2 3]) (revery integer? nil))

(defn ensure-set [x] (if (set? x) x (set x)))
(let [ensure-set ensure-set]
  (defn #?(:clj ks=      :cljs ^boolean ks=)      [ks m] (=             (set (keys m)) (ensure-set ks)))
  (defn #?(:clj ks<=     :cljs ^boolean ks<=)     [ks m] (set/subset?   (set (keys m)) (ensure-set ks)))
  (defn #?(:clj ks>=     :cljs ^boolean ks>=)     [ks m] (set/superset? (set (keys m)) (ensure-set ks)))
  (defn #?(:clj ks-nnil? :cljs ^boolean ks-nnil?) [ks m] (revery?     #(some? (get m %))           ks)))

#?(:clj
   (defn get-source [form env]
     (let [{:keys [line column file]} (meta form)
           file
           (if-not (:ns env)
             *file* ; Compiling clj
             (or    ; Compiling cljs
               (when-let [url (and file (try (io/resource file) (catch Throwable _ nil)))]
                 (try (.getPath (io/file url)) (catch Throwable _ nil))
                 (do            (str     url)))
               file))]

       {:ns     (str *ns*)
        :line   line
        :column column
        :file
        (when (string? file)
          (when-not (contains? #{"NO_SOURCE_PATH" "NO_SOURCE_FILE" ""} file)
            file))})))

(comment (io/resource "taoensso/truss.cljc"))

#?(:clj
   (let [resolve-clj clojure.core/resolve
         resolve-cljs
         (when-let [ns (find-ns 'cljs.analyzer.api)]
           (when-let [v (ns-resolve ns 'resolve)] @v))]

     (defn resolve-var
       #?(:clj ([sym] (resolve-clj sym)))
       ([env sym]
        (when (symbol? sym)
          (if (:ns env)
            (when resolve-cljs (resolve-cljs env sym))
            (do                (resolve-clj  env sym))))))))

(comment (resolve-var nil 'string?))

#?(:clj
   (defn- var->sym [cljs? v]
     (let [m (if cljs? v (meta v))]
       (symbol (str (:ns m)) (name (:name m))))))

#?(:clj
   (defn resolve-sym
     #?(:clj ([sym] (when-let [v (resolve-var     sym)] (var->sym false     v))))
     ([env sym]     (when-let [v (resolve-var env sym)] (var->sym (:ns env) v)))))

(comment (resolve-sym nil 'string?))

;;;; Truss

(defn default-error-fn [data_]
  (let [data @data_]
    (throw (ex-info @(:msg_ data) (dissoc data :msg_)))))

(def ^:dynamic *data* nil)
(def ^:dynamic *error-fn* default-error-fn)

(def ^:private safe-pred-forms
  (let [names
        (mapv name
          '#{nil? some? string? integer? number? symbol? keyword? float?
             set? vector? coll? list? ifn? fn? associative? sequential? delay?
             sorted? counted? reversible? true? false? identity not boolean})]

    (-> #{}
      (into (mapv #(symbol "clojure.core" %) names))
      (into (mapv #(symbol    "cljs.core" %) names)))))

(defn safe-pred [pred] (fn [x] (catching (pred x))))
#?(:clj
   (defn- safe-pred-form?
     "Returns true for common preds that can't throw."
     [env pred-form]
     (or
       (keyword? pred-form)
       (map?     pred-form)
       (set?     pred-form)
       (when-let [rsym (resolve-sym env pred-form)]
         (contains? safe-pred-forms rsym)))))

(comment (safe-pred-form? nil 'nil?))

#?(:clj
   (defn parse-pred-form
     "Returns {:keys [pred rsym safe?]}"
     [env pred-form]
     (cond
       (= pred-form ::some?) (parse-pred-form env `some?)
       (not (vector? pred-form))
       {:pred-form                  pred-form
        :rsym  (resolve-sym     env pred-form)
        :safe? (safe-pred-form? env pred-form)}

       :else
       (let [[type a1 a2 a3]      pred-form
             num-args (dec (count pred-form))]

         (when (or (< num-args 1) (> num-args 3))
           (throw (ex-info "Truss special predicates should have 1≤n≤3"
                    {:pred-form pred-form})))

         (case type
           :set=             {:pred-form `(fn [~'x] (=             (ensure-set ~'x) (ensure-set ~a1)))}
           :set<=            {:pred-form `(fn [~'x] (set/subset?   (ensure-set ~'x) (ensure-set ~a1)))}
           :set>=            {:pred-form `(fn [~'x] (set/superset? (ensure-set ~'x) (ensure-set ~a1)))}

           :ks=              {:pred-form `(fn [~'x] (ks=      ~a1 ~'x))}
           :ks<=             {:pred-form `(fn [~'x] (ks<=     ~a1 ~'x))}
           :ks>=             {:pred-form `(fn [~'x] (ks>=     ~a1 ~'x))}
           :ks-nnil?         {:pred-form `(fn [~'x] (ks-nnil? ~a1 ~'x))}
           (    :el     :in) {:pred-form `(fn [~'x]      (contains? (ensure-set ~a1) ~'x))}
           (:not-el :not-in) {:pred-form `(fn [~'x] (not (contains? (ensure-set ~a1) ~'x)))}

           :n=               {:pred-form `(fn [~'x] (=  (count ~'x) ~a1))}
           :n>=              {:pred-form `(fn [~'x] (>= (count ~'x) ~a1))}
           :n<=              {:pred-form `(fn [~'x] (<= (count ~'x) ~a1))}

           :instance?        {:pred-form `(fn [~'x] (instance?  ~a1 ~'x))}
           :satisfies?       {:pred-form `(fn [~'x] (satisfies? ~a1 ~'x))}

           (:and :or :not) ; Composition
           (let [;;; Support recursive expansion
                 {a1 :pred-form, sf-a1? :safe?} (when a1 (parse-pred-form env a1))
                 {a2 :pred-form, sf-a2? :safe?} (when a2 (parse-pred-form env a2))
                 {a3 :pred-form, sf-a3? :safe?} (when a3 (parse-pred-form env a3))

                 sf-a1    (when a1 (if sf-a1? a1 `(safe-pred ~a1)))
                 sf-a2    (when a2 (if sf-a2? a2 `(safe-pred ~a2)))
                 sf-a3    (when a3 (if sf-a3? a3 `(safe-pred ~a3)))
                 sf-comp? (cond a3 (and sf-a1? sf-a2? sf-a3?)
                                a2 (and sf-a1? sf-a2?)
                                a1      sf-a1?)]

             (case type
               :and ; all-of
               (cond
                 a3 {:safe? sf-comp?, :pred-form `(fn [~'x] (and (~a1 ~'x) (~a2 ~'x) (~a3 ~'x)))}
                 a2 {:safe? sf-comp?, :pred-form `(fn [~'x] (and (~a1 ~'x) (~a2 ~'x)))}
                 a1 {:safe? sf-a1?,   :pred-form a1})

               :or ; any-of
               (cond
                 a3 {:safe? true,   :pred-form `(fn [~'x] (or (~sf-a1 ~'x) (~sf-a2 ~'x) (~sf-a3 ~'x)))}
                 a2 {:safe? true,   :pred-form `(fn [~'x] (or (~sf-a1 ~'x) (~sf-a2 ~'x)))}
                 a1 {:safe? sf-a1?, :pred-form a1})

               :not ; complement/none-of
               ;; It's unclear if we'd want safe (non-throwing) behaviour here,
               ;; so will nterpret throws as undefined to minimize surprize
               (cond
                 a3 {:safe? sf-comp?, :pred-form `(fn [~'x] (not (or (~a1 ~'x) (~a2 ~'x) (~a3 ~'x))))}
                 a2 {:safe? sf-comp?, :pred-form `(fn [~'x] (not (or (~a1 ~'x) (~a2 ~'x))))}
                 a1 {:safe? sf-a1?,   :pred-form `(fn [~'x] (not     (~a1 ~'x)))})))

           (throw (ex-info "Unexpected Truss special predicate type"
                    {:pred-form pred-form})))))))

(comment
  [(parse-pred-form nil ::some?)
   (parse-pred-form nil 'string?)
   (parse-pred-form nil 'seq)
   (parse-pred-form nil [:or  'string? 'seq])
   (parse-pred-form nil [:and 'string? 'integer?])
   (parse-pred-form nil [:and 'string? 'seq])
   (parse-pred-form nil [:and 'integer? [:and 'number? 'pos? 'int?]])])

;; #?(:clj
;;    (defn- fast-pr-str
;;      "Combination `with-out-str`, `pr`. Ignores *print-dup*."
;;      [x]
;;      (let [w (java.io.StringWriter.)]
;;        (print-method x w)
;;        (.toString      w))))

;; (comment (enc/qb 1e5 (pr-str {:a :A}) (fast-pr-str {:a :A})))

(defn- error-message
  ;; Temporary, to support Clojure 1.9
  ;; Clojure 1.10+ now has `ex-message`
  [x]
  #?(:clj  (when (instance? Throwable x) (.getMessage ^Throwable x))
     :cljs (when (instance? js/Error  x) (.-message              x))))

(deftype WrappedError [val])
(defn -assertion-error [msg] #?(:clj (AssertionError. msg) :cljs (js/Error. msg)))
(def  -dummy-error #?(:clj (Object.) :cljs (js-obj)))
(defn -invar-violation!
  [elidable? ns-sym ?line ?column ?file pred-form pred-rsym arg-form arg ?err ?data-fn]
  (when-let [error-fn *error-fn*]
    (error-fn ; Nb consumer must deref while bindings are still active
     (delay
      (let [instant     #?(:clj (java.util.Date.) :cljs (js/Date.))
            undefn-arg? (instance? WrappedError arg)
            arg-val     (if undefn-arg? 'truss/undefined-arg       arg)
            arg-type    (if undefn-arg? 'truss/undefined-arg (type arg))

            ;; arg-str
            ;; (cond
            ;;   undefn-arg? "<truss/undefined-arg>"
            ;;   (nil? arg)  "<truss/nil>"
            ;;   :else
            ;;   (binding [*print-readably* false
            ;;             *print-length*   3]
            ;;     #?(:clj  (fast-pr-str arg)
            ;;        :cljs (pr-str      arg))))

            ?err
            (cond
              (identical? -dummy-error ?err) nil
              (instance?  WrappedError ?err)
              (.-val     ^WrappedError ?err)
              :else                    ?err)

            msg_
            (delay
              (let [;arg-form (if (nil? arg-form) 'nil arg-form)
                    msg
                    (str "Invariant failed at " ns-sym
                      (when ?line (str "[" ?line (when ?column (str "," ?column)) "]")) ": "
                      (list pred-form arg-form #_arg-val))]

                (if-let [err ?err]
                  (let [err-msg #_(ex-message err) (error-message err)]
                    (if undefn-arg?
                      (str msg "\r\n\r\nError evaluating arg: "  err-msg)
                      (str msg "\r\n\r\nError evaluating pred: " err-msg)))
                  msg)))

            ?data
            (let [dynamic *data*
                  arg
                  (when-let [data-fn ?data-fn]
                    (catching (data-fn) e
                      {:truss/error e}))]

              (when (or   dynamic      arg)
                {:dynamic dynamic :arg arg}))

            loc {:ns ns-sym}
            loc (if-let [v ?line]   (assoc loc :line   v) loc)
            loc (if-let [v ?column] (assoc loc :column v) loc)
            loc (if-let [v ?file]   (assoc loc :file   v) loc)

            output
            {:msg_ msg_
             :dt   instant
             :pred (or pred-rsym pred-form)
             :arg  {:form      arg-form
                    :value     arg-val
                    :type      arg-type}
             :env  {:elidable? elidable?
                    :*assert*  *assert*}
             :loc  loc}

            output (if-let [v ?data] (assoc output :data v) output)
            output (if-let [v ?err]  (assoc output :err  v) output)]

        output)))))

#?(:clj
   (defn const-form? "See issue #12" [x]
     (not (or (list? x) (instance? clojure.lang.Cons x)))))

#?(:clj
   (defmacro -invar
     "Written to maximize performance + minimize post Closure+gzip Cljs code size."
     [elidable? truthy? source pred-form x ?data-fn]
     (let [const-x? (const-form? x) ; Common case

           {:keys [ns line column file]} source
           ns-sym (symbol ns)

           {pred-form* :pred-form,
            pred-safe? :safe?,
            pred-rsym  :rsym} (parse-pred-form &env pred-form)]

       (if const-x? ; Common case
         (if pred-safe? ; Common case
           `(if (~pred-form* ~x)
              ~(if truthy? true x)
              (-invar-violation! ~elidable? '~ns-sym ~line ~column ~file '~pred-form '~pred-rsym '~x ~x nil ~?data-fn))

           `(let [~'e (catching (if (~pred-form* ~x) nil -dummy-error) ~'e ~'e)]
              (if (nil? ~'e)
                ~(if truthy? true x)
                (-invar-violation! ~elidable? '~ns-sym ~line ~column ~file '~pred-form '~pred-rsym '~x ~x ~'e ~?data-fn))))

         (if pred-safe?
           `(let [~'z (catching ~x ~'e (WrappedError. ~'e))
                  ~'e (if (instance? WrappedError ~'z)
                        ~'z
                        (if (~pred-form* ~'z) nil -dummy-error))]

              (if (nil? ~'e)
                ~(if truthy? true 'z)
                (-invar-violation! ~elidable? '~ns-sym ~line ~column ~file '~pred-form '~pred-rsym '~x ~'z ~'e ~?data-fn)))

           `(let [~'z (catching ~x ~'e (WrappedError. ~'e))
                  ~'e (catching
                        (if (instance? WrappedError ~'z)
                          ~'z
                          (if (~pred-form* ~'z) nil -dummy-error)) ~'e ~'e)]

              (if (nil? ~'e)
                ~(if truthy? true 'z)
                (-invar-violation! ~elidable? '~ns-sym ~line ~column ~file '~pred-form '~pred-rsym '~x ~'z ~'e ~?data-fn))))))))

(comment
  (macroexpand '(-invar true false 1      string?    "foo"             nil)) ; Type 0
  (macroexpand '(-invar true false 1 [:or string?]   "foo"             nil)) ; Type 0
  (macroexpand '(-invar true false 1    #(string? %) "foo"             nil)) ; Type 1
  (macroexpand '(-invar true false 1      string?    (str "foo" "bar") nil)) ; Type 2
  (macroexpand '(-invar true false 1    #(string? %) (str "foo" "bar") nil)) ; Type 3
  (enc/qb 1e6
    (string? "foo")                                          ; Baseline
    (-invar true false 1   string?    "foo"             nil) ; Type 0
    (-invar true false 1 #(string? %) "foo"             nil) ; Type 1
    (-invar true false 1   string?    (str "foo" "bar") nil) ; Type 2
    (-invar true false 1 #(string? %) (str "foo" "bar") nil) ; Type 3
    (try
      (string? (try "foo" (catch Throwable _ nil)))
      (catch Throwable _ nil)))
  ;; [41.86 50.43 59.56 171.12 151.2 42.0]

  (-invar false false 1 integer? "foo"   nil) ; Pred failure example
  (-invar false false 1 zero?    "foo"   nil) ; Pred error example
  (-invar false false 1 zero?    (/ 5 0) nil) ; Form error example
  )

#?(:clj
   (defmacro -invariant [elidable? truthy? source args]
     (let [bang?      (= (first args) :!) ; For back compatibility, undocumented
           elidable?  (and elidable? (not bang?))
           elide?     (and elidable? (not *assert*))
           args       (if bang? (next args) args)
           in?        (= (second args) :in) ; (have pred :in xs1 xs2 ...)
           args       (if in? (cons (first args) (nnext args)) args)

           data?      (and (> (count args) 2) ; Distinguish from `:data` pred
                           (= (last (butlast args)) :data))
           ?data-fn   (when data? `(fn [] ~(last args)))
           args       (if data? (butlast (butlast args)) args)

           auto-pred? (= (count args) 1) ; Unique common case: (have ?x)
           pred       (if auto-pred? ::some? (first args))
           [?x1 ?xs]  (if auto-pred?
                        [(first args) nil]
                        (if (nnext args) [nil (next args)] [(second args) nil]))
           single-x?  (nil? ?xs)
           in-fn
           `(fn [~'__in] ; Will (necessarily) lose exact form
              (-invar ~elidable? ~truthy? ~source ~pred ~'__in ~?data-fn))]

       (if elide?
         (if truthy?
           true
           (if single-x? ?x1 (vec ?xs)))

         (if-not in?

           (if single-x?
             ;; (have pred x) -> x
             `(-invar ~elidable? ~truthy? ~source ~pred ~?x1 ~?data-fn)

             ;; (have pred x1 x2 ...) -> [x1 x2 ...]
             (if truthy?
               `(do ~@(mapv (fn [x] `(-invar ~elidable? ~truthy? ~source ~pred ~x ~?data-fn)) ?xs) true)
               (do    (mapv (fn [x] `(-invar ~elidable? ~truthy? ~source ~pred ~x ~?data-fn)) ?xs))))

           (if single-x?

             ;; (have? pred :in xs) -> bool
             ;; (have  pred :in xs) -> xs
             (if truthy?
               `(revery? ~in-fn ~?x1)
               `(revery  ~in-fn ~?x1))

             ;; (have? pred :in xs1 xs2 ...) -> [bool1 ...]
             ;; (have  pred :in xs1 xs2 ...) -> [xs1   ...]
             (if truthy?
               `(do ~@(mapv (fn [xs] `(revery? ~in-fn ~xs)) ?xs) true)
               (do    (mapv (fn [xs] `(revery  ~in-fn ~xs)) ?xs)))))))))
