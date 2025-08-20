(ns taoensso.truss
  "A micro toolkit for Clojure/Script errors."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:refer-clojure :exclude [ex-info])
  (:require
   #?(:clj  [clojure.core :as core]
      :cljs [cljs.core    :as core])

   #?(:clj  [taoensso.truss.impl :as impl])
   #?(:cljs [taoensso.truss.impl :as impl :refer [FailedAssertionInfo ArgEvalError]]))

  #?(:cljs
     (:require-macros
      [taoensso.truss :refer [typed-val try*]]))

  #?(:clj
     (:import
      [taoensso.truss.impl FailedAssertionInfo ArgEvalError])))

(comment
  (require '[taoensso.encore :as enc])
  (enc/sortv (:api (enc/interns-overview))))

;;;; Callsites

#?(:clj
   (defn callsite-coords
     "Returns [line column] from meta on given macro `&form`.
     See also `keep-callsite`."
     [macro-form]
     (when-let [{:keys [line column]} (meta macro-form)]
       (when line (if column [line column] [line])))))

#?(:clj (defn ^:no-doc merge-callsite [macro-form inner-form] (vary-meta inner-form merge (meta macro-form))))
#?(:clj
   (defmacro keep-callsite
     "CLJ-865 means that it's not possible for an inner macro to access `&form`
     metadata (incl. {:keys [line column]}) of a wrapping outer macro:

       (defmacro inner [] (meta &form))
       (defmacro outer [] `(inner))
       (outer) => nil

     This util offers a workaround for authors of the outer macro, preserving
     the outer `&form` metadata for the inner macro:

       (defmacro inner [] (meta &form))
       (defmacro outer [] (keep-callsite `(inner)))
       (outer) => {:keys [line column ...]}"
     [inner-form] `(merge-callsite ~'&form ~inner-form)))

;;;; Misc

(defn ^:no-doc submap?
  "Returns true iff `sub-map` is a (possibly nested) submap of `super-map`,
  i.e. iff every (nested) value in `sub-map` has the same (nested) value in `super-map`.

  `sub-map` may contain special values:
    `:submap/nx`     - Matches iff `super-map` does not contain key
    `:submap/ex`     - Matches iff `super-map` does     contain key (any     val)
    `:submap/some`   - Matches iff `super-map` does     contain key (non-nil val)
    (fn [super-val]) - Matches iff given unary predicate returns truthy

  Uses stack recursion so supports only limited nesting."
  [super-map sub-map]
  (reduce-kv
    (fn [_ sub-key sub-val]
      (if (map?    sub-val)
        (let [super-val (get super-map sub-key)]
          (if-let [match? (and (map? super-val) (submap? super-val sub-val))]
            true
            (reduced false)))

        (let [super-val (get super-map sub-key ::nx)]
          (if-let [match?
                   (if-let [pred-fn (when (fn? sub-val) sub-val)]
                     (pred-fn super-val)
                     (case sub-val
                       :submap/nx      (impl/identical-kw? super-val ::nx)
                       :submap/ex (not (impl/identical-kw? super-val ::nx))
                       :submap/some                 (some? super-val)
                       (= sub-val super-val)))]
            true
            (reduced false)))))
    true
    sub-map))

;;;; Error context

(def ^:dynamic *ctx*
  "Optional context to add to the data of exceptions created by `truss/ex-info`.

  Value may be any type, but is usually nil or a map. Default (root) value is nil.
  When present, value will be assoc'ed to `:truss/ctx` key of exception data.

  Useful for dynamically providing arbitrary contextual info to exceptions:

  Re/bind dynamic        value using `with-ctx`, `with-ctx+`, or `binding`.
  Modify  root (default) value using `set-ctx!`.

  As with all dynamic Clojure vars, \"binding conveyance\" applies when using
  futures, agents, etc."
  nil)

(defn ex-info
  "Like `core/ex-info` but when dynamic `*ctx*` value is present, it will be
  assoc'ed to `:truss/ctx` key of returned exception's data.

  Useful for dynamically providing arbitrary contextual info to exceptions.
  See `*ctx*` for details."
  ([msg               ] (ex-info msg {}       nil))
  ([msg data-map      ] (ex-info msg data-map nil))
  ([msg data-map cause]
   (if-let [ctx *ctx*]
     (core/ex-info msg (assoc data-map :truss/ctx ctx) cause)
     (core/ex-info msg        data-map                 cause))))

(defn ex-info!
  "Throws a `truss/ex-info`."
  ([msg               ] (throw (ex-info msg {}       nil)))
  ([msg data-map      ] (throw (ex-info msg data-map nil)))
  ([msg data-map cause] (throw (ex-info msg data-map cause))))

(defn set-ctx!
  "Set `*ctx*` var's default (root) value. See `*ctx*` for details."
  [root-ctx-val]
  #?(:clj  (alter-var-root (var *ctx*) (fn [_] root-ctx-val))
     :cljs (set!                *ctx*          root-ctx-val)))

(defmacro with-ctx
  "Evaluates given form with given `*ctx*` value. See `*ctx*` for details."
  [ctx-val form] `(binding [*ctx* ~ctx-val] ~form))

(declare unexpected-arg!)

(defn ^:no-doc update-ctx
  "Returns `new-ctx` given `old-ctx` and an update map or fn."
  [old-ctx update-map-or-fn]
  (cond
    (nil? update-map-or-fn)           old-ctx
    (map? update-map-or-fn) (conj (or old-ctx) update-map-or-fn) ; Before ifn
    (ifn? update-map-or-fn) (update-map-or-fn old-ctx)
    :else
    (unexpected-arg! update-map-or-fn
      {:param       'update-map-or-fn
       :context  `update-ctx
       :expected '#{nil map fn}})))

(defmacro with-ctx+
  "Evaluates given form with updated `*ctx*` value.

  `update-map-or-fn` may be:
    - A map to merge with    current `*ctx*` value, or
    - A unary fn to apply to current `*ctx*` value

  See `*ctx*` for details."
  [update-map-or-fn form]
  `(binding [*ctx* (update-ctx *ctx* ~update-map-or-fn)]
     ~form))

;;;; Error utils

#?(:clj (defmacro ^:no-doc typed-val [x] `{:value ~x, :type (type ~x)}))

(defn error?
  "Returns true iff given platform error (`Throwable` or `js/Error`)."
  #?(:cljs {:tag 'boolean})
  [x]
  #?(:clj  (instance? Throwable x)
     :cljs (instance? js/Error  x)))

(defn ^:no-doc ex-root
  "Private, don't use.
  Returns root cause of given platform error."
  [x]
  (when (error? x)
    (loop [error x]
      (if-let [cause (ex-cause error)]
        (recur cause)
        error))))

(comment (ex-root (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))))

(defn ^:no-doc ex-type
  "Private, don't use.
  Returns class symbol of given platform error."
  [x]
  #?(:clj (symbol (.getName (class x)))
     :cljs
     (cond
       (instance? ExceptionInfo x) `ExceptionInfo ; Note namespaced
       (instance? js/Error      x) (symbol "js" (.-name x)))))

(defn ^:no-doc ex-map*
  "Private, don't use.
  Returns ?{:keys [type msg data]} for given platform error."
  [x]
  (when-let [msg             (ex-message x)]
    (if-let [data (not-empty (ex-data    x))]
      {:type (ex-type x), :msg msg, :data data}
      {:type (ex-type x), :msg msg})))

(comment (ex-map* (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))))

(defn ^:no-doc ex-chain
  "Private, don't use.
  Returns vector cause chain of given platform error."
  ([         x] (ex-chain false x))
  ([as-maps? x]
   (when (error? x)
     (let [xf (if as-maps? ex-map* identity)]
       (loop [acc [(xf x)], error x]
         (if-let [cause (ex-cause error)]
           (recur (conj acc (xf cause)) cause)
           (do          acc)))))))

(comment (ex-chain :as-maps (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))))

#?(:clj
   (defn- st-element->map [^StackTraceElement ste]
     {:class  (symbol (.getClassName  ste))
      :method (symbol (.getMethodName ste))
      :file           (.getFileName   ste)
      :line           (.getLineNumber ste)}))

;; #?(:clj
;;    (defn- st-element->str ^String [^StackTraceElement ste]
;;      (str
;;        "`" (.getClassName ste) "/" (.getMethodName ste) "`"
;;        " at " (.getFileName ste) ":" (.getLineNumber ste))))

(defn ^:no-doc ex-map
  "Private, don't use.
  Returns ?{:keys [type msg data chain trace]} for given platform error."
  [x]
  (when-let [chain (ex-chain x)]
    (let [maps     (mapv ex-map* chain)
          root     (peek chain)
          root-map (peek maps)]

      (impl/assoc-some root-map
        {:chain maps
         :trace
         #?(:cljs (when-let [st (.-stack root)] (when-not (= st "") st))
            :clj
            (when-let [st (not-empty (.getStackTrace ^Throwable root))] ; Don't delay
              (delay (mapv st-element->map st))))}))))

(comment
  (ex-map  (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"})))
  (ex-map  (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"})))
  (let [ex (ex-info "Ex2" {:k2 "v2"} (ex-info "Ex1" {:k1 "v1"}))]
    (enc/qb 1e5 ; [21.22 114.51]
      (ex-map         ex)
      (Throwable->map ex))))

#?(:clj (defn ^:no-doc critical-error?     [x] (and (instance? Error     x) (not (instance? AssertionError x)))))
#?(:clj (defn-     non-critical-throwable? [x] (and (instance? Throwable x) (not (critical-error? x)))))
#?(:clj
   (defn ^:no-doc throw-critical
     "Private, don't use.
     If given any `Error` besides `AssertionError`, (re)throw it.
     Useful as a hack to allow easily catching both `Exception` and `AssertionError`:
       (try <body> (catch Throwable t (throw-critical t) <body>)), etc."
     [x] (when (critical-error? x) (throw x))))

#?(:clj
   (defmacro try*
     "Like `try`, but `catch` clause class may be:
       `:ex-info` -- Catches only `ExceptionInfo`
       `:common` --- Catches `js/Error` (Cljs), `Exception` (Clj)
       `:all` ------ Catches `:default` (Cljs), `Throwable` (Clj)
       `:default` -- Catches `:default` (Cljs), `Exception`, and `AssertionError` (Clj)
                     but NOT other (usually critical) `Error`s

     Addresses CLJ-1293 and the fact that `AssertionError`s are typically NON-critical
     (so desirable to catch, in contrast to other `Error` classes)."
     {:arglists '([expr* catch-clauses* ?finally-clause])}
     [& forms]
     (let [cljs? (some? (:ns &env))
           forms
           (mapv
             (fn [in]
               (if-not (impl/list-form? in)
                 in
                 (let [[s1 s2 s3 & more] in]
                   (cond
                     (not= s1 'catch)    in
                     (not (keyword? s2)) in
                     :else
                     (let [[rethrow-critical? s2]
                           (case s2
                             (:all     :any)              (if cljs? [false  :default] [false `Throwable])
                             (:default :all-but-critical) (if cljs? [false  :default] [true  `Throwable])
                             (:common)                    (if cljs? [false 'js/Error] [false `Exception])
                             (:ex-info)
                             (if cljs?
                               [false    'cljs.core.ExceptionInfo]
                               [false 'clojure.lang.ExceptionInfo])

                             (throw
                               (ex-info "Unexpected Truss `try*` catch clause keyword"
                                 {:given    {:value s2, :type (type s2)}
                                  :expected '#{:ex-info :common :all :default}})))]

                       (if rethrow-critical?
                         `(catch ~s2 ~s3 (throw-critical ~s3) ~@more)
                         `(catch ~s2 ~s3                      ~@more)))))))
             forms)]

       `(try ~@forms))))

(comment
  (macroexpand '(try*         (catch :all     t t 1 2 3) (finally 1 2 3)))
  (macroexpand '(try* (/ 1 0) (catch :all     t t 1 2 3) (finally 1 2 3)))
  (macroexpand '(try* (/ 1 0) (catch :default t t 1 2 3) (finally 1 2 3))))

#?(:clj
   (defmacro catching
     "Terse cross-platform util to swallow exceptions in `expr`.
     Like (try* expr (catch :default _ nil)). See also `try*`."
     ([            expr] `(try* ~expr (catch :default     ~'_)))
     ([catch-class expr] `(try* ~expr (catch ~catch-class ~'_)))))

(comment (catching (zero? "9")))

(defn unexpected-arg!
  "Throws `truss/ex-info` to indicate an unexpected argument.
  Takes optional kvs for merging into exception's data map.

    (let [mode :unexpected]
      (case mode
        :read  (do <...>)
        :write (do <...>)
        (unexpected-arg! mode
          {:param       'mode
           :context  `my-function
           :expected #{:read :write}}))) =>

    Unexpected argument: :unexpected
    {:param 'mode,
     :arg {:value :unexpected, :type clojure.lang.Keyword},
     :context 'taoensso.encore/my-function,
     :expected #{:read :write}}"

  {:arglists
   '([arg]
     [arg   {:keys [msg context param expected ...]}]
     [arg & {:keys [msg context param expected ...]}])}

  ([arg     ] (unexpected-arg! arg nil))
  ([arg opts]
   (throw
     (ex-info (or (get opts :msg) (str "Unexpected argument: " (if (nil? arg) "<nil>" arg)))
       (conj {:arg (typed-val arg)} (dissoc opts :msg)))))

  ([arg k1 v1                  ] (unexpected-arg! arg {k1 v1}))
  ([arg k1 v1 k2 v2            ] (unexpected-arg! arg {k1 v1, k2 v2}))
  ([arg k1 v1 k2 v2 k3 v3      ] (unexpected-arg! arg {k1 v1, k2 v2, k3 v3}))
  ([arg k1 v1 k2 v2 k3 v3 k4 v4] (unexpected-arg! arg {k1 v1, k2 v2, k3 v3, k4 v4})))

(comment (unexpected-arg! :arg :expected '#{string?}))

(defn matching-error
  "Given a platform error and criteria for matching, returns the error if it
  matches all criteria. Otherwise returns nil.

  `kind` may be:
    - A class (`ArithmeticException`, `AssertionError`, etc.)
    - A special keyword as given to `try*` (`:default`, `:common`, `:ex-info`, `:all`)
    - A set of `kind`s  as above, at least one of which must match
    - A predicate function, (fn match? [x]) -> bool

  `pattern` may be:
    - A string or Regex against which `ex-message` must match
    - A map             against which `ex-data`    must match using `submap?`
    - A set of `pattern`s as above, at least one of which must match

  When an error with (nested) causes doesn't match, a match will be attempted
  against its (nested) causes.

  This is a low-level util, see also `throws`, `throws?`."
  ([     error] error)
  ([kind error]
   (when-let [match?
              (cond
                (keyword? kind)
                (case     kind
                  (:default :all-but-critical) #?(:clj (non-critical-throwable?               error) :cljs (some?                   error))
                  (:common)                    #?(:clj (instance? Exception                   error) :cljs (instance? js/Error      error))
                  (:ex-info)                   #?(:clj (instance? clojure.lang.IExceptionInfo error) :cljs (instance? ExceptionInfo error))
                  (:all :any)                  #?(:clj (instance? Throwable                   error) :cljs (some?                   error))
                  (throw
                    (ex-info "Unexpected Truss `matching-error` `kind` keyword"
                      {:given    (typed-val kind)
                       :expected '#{:default :common :ex-info :all}})))

                (error? kind) (= kind error) ; Exact match
                (fn?    kind) (kind error)   ; Pred
                (set?   kind) (impl/rsome #(matching-error % error) kind)
                :else (instance? kind error))]
     error))

  ([kind pattern error]
   (if-let [match?
            (and
              (matching-error kind error)
              (cond
                (nil?             pattern) true
                (set?             pattern) (impl/rsome #(matching-error kind % error) pattern)
                (string?          pattern) (impl/str-contains?     (ex-message error) pattern)
                (impl/re-pattern? pattern) (re-find pattern        (ex-message error))
                (map?             pattern) (when-let [data         (ex-data    error)]
                                             (submap? data pattern))
                :else
                (unexpected-arg! pattern
                  {:param       'pattern
                   :context  `matching-error
                   :expected '#{nil set string re-pattern map}})))]
     error
     ;; Try match cause
     (when-let [cause (ex-cause error)]
       (matching-error kind pattern cause)))))

#?(:clj
   (defmacro throws
     "Evals `form` and if it throws an error that matches given criteria using
     `matching-error`, returns the matching error. Otherwise returns nil.

     Useful for unit tests, e.g.:
       (is (throws :default {:a :b}  (throw (ex-info \"MyEx\" {:a :b, :c :d})))) ; => ExceptionInfo
       (is (throws :default \"MyEx\" (throw (ex-info \"MyEx\" {:a :b, :c :d})))) ; => ExceptionInfo

     See also `throws?`, `matching-error`."
     ([             form] `                               (try* ~form nil (catch :all ~'t ~'t)))
     ([kind         form] `(matching-error ~kind          (try* ~form nil (catch :all ~'t ~'t))))
     ([kind pattern form] `(matching-error ~kind ~pattern (try* ~form nil (catch :all ~'t ~'t))))))

#?(:clj
   (defmacro throws?
     "Evals `form` and if it throws an error that matches given criteria using
     `matching-error`, returns true. Otherwise returns false.

     Useful for unit tests, e.g.:
       (is (throws? :default {:a :b}  (throw (ex-info \"MyEx\" {:a :b, :c :d})))) ; => true
       (is (throws? :default \"MyEx\" (throw (ex-info \"MyEx\" {:a :b, :c :d})))) ; => true

     See also `throws`, `matching-error`."
     ([             form] `(boolean (throws                ~form)))
     ([kind         form] `(boolean (throws ~kind          ~form)))
     ([kind pattern form] `(boolean (throws ~kind ~pattern ~form)))))

(let [get-default-error-fn
      (fn [base-data]
        (let [msg       (get    base-data :error/msg "Error thrown during reduction")
              base-data (dissoc base-data :error/msg)]

          (fn default-error-fn [data cause] ; == (partial ex-info <msg>)
            (throw (ex-info msg (conj base-data data) cause)))))]

  (defn catching-rf
    "Returns wrapper around given reducing function `rf` so that if `rf`
    throws, (error-fn <thrown-error> <contextual-data>) will be called.

    The default `error-fn` will rethrow the original error, wrapped in
    extra contextual information to aid debugging.

    Helps make reducing fns easier to debug!
    See also `catching-xform`."
    ([         rf] (catching-rf (get-default-error-fn {:rf rf}) rf))
    ([error-fn rf]
     (let [error-fn
           (if (map? error-fn) ; Undocumented convenience
             (get-default-error-fn error-fn)
             (do                   error-fn))]

       (fn catching-rf
         ([       ] (try* (rf)        (catch :all t (error-fn {:rf rf :call '(rf)} t))))
         ([acc    ] (try* (rf acc)    (catch :all t (error-fn {:rf rf :call '(rf acc)    :args {:acc (typed-val acc)}} t))))
         ([acc in ] (try* (rf acc in) (catch :all t (error-fn {:rf rf :call '(rf acc in) :args {:acc (typed-val acc)
                                                                                                :in  (typed-val in)}} t))))
         ([acc k v]
          (try* (rf acc k v)
            (catch :all t
              (error-fn
                {:rf     rf
                 :call '(rf acc k v)
                 :args
                 {:acc (typed-val acc)
                  :k   (typed-val k)
                  :v   (typed-val v)}}
                t)))))))))

(defn catching-xform
  "Like `catching-rf`, but applies to a transducer (`xform`).

  Helps make transductions much easier to debug by greatly improving
  the info provided in any errors thrown by `xform` or the reducing fn:

    (transduce
      (catching-xform (comp (filter even?) (map inc))) ; Modified xform
      <reducing-fn>
      <...>)"

  ([error-fn xform] (comp (fn [rf] (catching-rf error-fn rf)) xform))
  ([         xform] (comp           catching-rf               xform)))

;;;; Assertions

(def ^:private sys-newline #?(:cljs "\n" :clj (System/getProperty "line.separator")))

(let [legacy-ex-data? (impl/legacy-assertion-ex-data?)]

  (defn failed-assertion-ex-info
    "Returns an appropriate `truss/ex-info` for given failed assertion info map."
    ([                failed-assertion-info] (failed-assertion-ex-info legacy-ex-data? failed-assertion-info))
    ([legacy-ex-data? failed-assertion-info]
     (let [{:keys [inst ns coords, pred arg-form arg-val, data error]} failed-assertion-info
           undefined-arg? (impl/identical-kw? arg-val :truss/exception)

           coords-str ; Faster (str coords)
           (when-let [[line column] coords]
             (if column
               (str "[" line "," column "]")
               (str "[" line "]")))

           msg (str "Truss assertion failed at " ns coords-str ": " (list pred arg-form))
           msg
           (if error
             (let [error-msg (ex-message error)]
               (if undefined-arg?
                 (str msg sys-newline "Error evaluating arg: "  error-msg)
                 (str msg sys-newline "Error evaluating pred: " error-msg)))
             msg)]

       (ex-info msg

         (if legacy-ex-data?
           {:dt     (impl/now-dt*)
            :loc    (let [[line column] coords] {:ns ns, :line line, :column column})
            :msg    msg
            :pred   pred
            :data   {:arg data, :dynamic *ctx*}
            :env    {:*assert* *assert*}
            :error  error
            :arg
            {:form  arg-form
             :value arg-val
             :type  (if undefined-arg? :truss/exception (type arg-val))}}

           (impl/assoc-some
             {:inst   (impl/now-inst*)
              :ns     ns
              :pred   pred
              :arg
              {:form  arg-form
               :value arg-val
               :type  (if undefined-arg? :truss/exception (type arg-val))}}
             {:coords coords
              :data   data}))

         error)))))

(def ^:dynamic *failed-assertion-handler*
  "Unary handler fn to call with failed assertion info map when a Truss
  assertion (`have`, `have?`, `have!`, `have!?`) fails.

  Will by default throw an appropriate `truss/ex-info`.
  This is a decent place to inject logging for assertion failures, etc.

  Arg given to handler is a map with keys:

  `:ns` ----------- ?str namespace of assertion callsite
  `:coords` ------- ?[line column] of assertion callsite

  `:pred` --------- Assertion predicate form  (e.g. `clojure.core/string?` sym)
  `:arg-form` ----- Assertion argument  form given  to predicate (e.g. `x` sym)
  `:arg-val` ------ Runtime value of argument given to predicate

  `:data` --------- Optional arbitrary data map provided to assertion macro
  `:error` -------- `Throwable` or `js/Error` thrown evaluating predicate"

  (fn  [failed-assertion-info]
    (-> failed-assertion-info failed-assertion-ex-info throw)))

(comment
  (let [foo (fn [x] (have true? x))]
    (binding [*failed-assertion-handler* identity] (foo false))))

(defn ^:no-doc failed-assertion!
  "Private, don't use."
  [ns line column, pred arg-form arg-val, data-fn error]
  (if-let [;; Not accessible from impl ns in Cljs
           handler *failed-assertion-handler*]
    (handler
      (let [undefined-arg? (instance? ArgEvalError arg-val)]
        (FailedAssertionInfo. ns
          (when line (if column [line column] [line]))
          pred arg-form
          (if undefined-arg? :truss/exception arg-val)
          (when-let [df data-fn] (impl/catching (df) _ :truss/exception))
          (cond
            (identical? error impl/FalsePredError) nil
            undefined-arg? (.-ex ^ArgEvalError error)
            :else                              error))))
    arg-val))

#?(:clj
   (defmacro have
     "Main Truss assertion util.
     Takes a (fn pred [x]) => truthy, and >=1 vals.
     Tests pred against each val,trapping errors.

     If any pred test fails, throws a detailed `truss/ex-info`.
     Otherwise returns input val/s for convenient inline-use/binding.

     Respects `*assert*`, so tests can be elided from production if desired
     (meaning zero runtime cost).

     Examples:
       (defn my-trim [x] (str/trim (have string? x)))

       ;; Add arb optional info to thrown ex-data using `:data`:
       (have string? \"foo\" :data {:user-id 101}) => \"foo\"

       ;; Assert inside collections using `:in`:
       (have string? :in #{\"foo\" \"bar\"}) => #{\"foo\" \"bar\"}

     Regarding use within other macros:
       Due to CLJ-865, callsite info like line number of outer macro
       will be lost. See `keep-callsite` for workaround.

     See also `have?`, `have!`."
     {:arglists '([x] [pred (:in) x] [pred (:in) x & more-xs])}
     [& args] `(impl/assert-args true false ~(callsite-coords &form) ~args)))

#?(:clj
   (defmacro have?
     "Truss assertion util.
     Like `have` but returns `true` (rather than given arg value) on success.
     Handy for `:pre`/`:post` conditions. Compare:
       ((fn my-fn [] {:post [(have  nil? %)]} nil)) ; {:post [nil ]} FAILS
       ((fn my-fn [] {:post [(have? nil? %)]} nil)) ; {:post [true]} passes as intended"
     {:arglists '([x] [pred (:in) x] [pred (:in) x & more-xs])}
     [& args] `(impl/assert-args true true ~(callsite-coords &form) ~args)))

#?(:clj
   (defmacro have!
     "Truss assertion util.
     Like `have` but ignores `*assert*` value (so will never be elided).
     Useful for important conditions in production (e.g. security checks)."
     {:arglists '([x] [pred (:in) x] [pred (:in) x & more-xs])}
     [& args] `(impl/assert-args false false ~(callsite-coords &form) ~args)))

#?(:clj
   (defmacro have!?
     "Truss assertion util.
     Returns `true` (rather than given arg value) on success, and ignores
     `*assert*` value (so will never be elided).
  
     **WARNING**: do NOT use in `:pre`/`:post` conditions since those ALWAYS
     respect `*assert*`, contradicting the intention of the bang (`!`) here."
     {:arglists '([x] [pred (:in) x] [pred (:in) x & more-xs])}
     [& args] `(impl/assert-args false true ~(callsite-coords &form) ~args)))

;;;; Deprecated

(defn ^:no-doc legacy-error-fn
  "Private, don't use. Wraps given Truss v1 `error-fn` to convert
  Truss v2 `*failed-assertion-handler*` arg."
  [f]
  (when f
    (fn [failed-assertion-info]
      (f
        (delay
          (let [{:keys [ns coords, pred arg-form arg-val, data error]} failed-assertion-info
                [line column] coords
                msg_
                (delay
                  (let [msg
                        (str "Invariant failed at " ns
                          (when line (str "[" line (when column (str "," column)) "]")) ": "
                          (list pred arg-form))]

                    (if error
                      (let [error-msg (ex-message error)]
                        (if (impl/identical-kw? arg-val :truss/exception)
                          (str msg sys-newline sys-newline "Error evaluating arg: "  error-msg)
                          (str msg sys-newline sys-newline "Error evaluating pred: " error-msg)))
                      msg)))]

            (impl/assoc-some
              {:msg_ msg_
               :dt   #?(:clj (java.util.Date.) :cljs (js/Date.))
               :pred pred
               :arg  {:form        arg-form
                      :value       arg-val
                      :type  (type arg-val)}
               :env  {:*assert* *assert*}
               :loc  {:ns ns, :line line, :column column}}

              {:data (impl/assoc-some nil {:dynamic *ctx* :arg data})
               :err  error})))))))

(defn ^:no-doc ^:deprecated get-dynamic-assertion-data "Prefer `*ctx*`" [] *ctx*)
(defn ^:no-doc ^:deprecated get-data                   "Prefer `*ctx*`" [] *ctx*)
(defn ^:no-doc ^:deprecated set-error-fn!
  "Prefer `*failed-assertion-handler*` (note breaking changes to argument)."
  [f]
  #?(:cljs (set!             *failed-assertion-handler*         (legacy-error-fn f))
     :clj  (alter-var-root #'*failed-assertion-handler* (fn [_] (legacy-error-fn f)))))

#?(:clj (defmacro ^:no-doc ^:deprecated with-dynamic-assertion-data "Prefer `*ctx*`" [data & body] `(binding [*ctx* ~data] ~@body)))
#?(:clj (defmacro ^:no-doc ^:deprecated with-data                   "Prefer `*ctx*`" [data & body] `(binding [*ctx* ~data] ~@body)))
#?(:clj
   (defmacro ^:no-doc ^:deprecated with-error-fn
     "Prefer `*failed-assertion-handler*` (note breaking changes to argument)."
     [f & body]
     `(binding [*failed-assertion-handler* (legacy-error-fn ~f)]
        ~@body)))

(comment (force (:msg_ (with-data {:a :A} (with-error-fn force (have true? false))))))
