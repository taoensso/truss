(ns taoensso.truss.examples
  {:author "Peter Taoussanis (@ptaoussanis)"}
  #?(:clj  (:require [taoensso.truss :as truss :refer        (have have! have?)])
     :cljs (:require [taoensso.truss :as truss :refer-macros (have have! have?)])))

;;;; First API example

(comment
(defn square [n]
  (let [n (have integer? n)]
    (* n n)))

;; This returns n if it satisfies (integer? n), otherwise it throws a clear error:
;; (have integer? n)

(square 5)   ; => 25
(square nil) ; =>
;; Invariant violation in `taoensso.truss.examples:11` [pred-form, val]:
;; [(integer? n), <nil>]
;; {:instant 1450937904762,
;;  :ns "taoensso.truss.examples",
;;  :elidable? true,
;;  :val nil,
;;  :val-type nil,
;;  :?err nil,
;;  :*assert* true,
;;  :?data nil,
;;  :?line 11,
;;  :form-str "(integer? n)"}
)

;;;; Inline assertions and bindings

(comment

;; You can add an assertion inline
(println (have string? "foo"))

;; Or you can add an assertion to your bindings
(let [s (have string? "foo")]
  (println s))

;; Anything that fails the predicate will throw an error
(have string? 42) ; =>
;; Invariant violation in `taoensso.truss.examples:44` [pred-form, val]:
;; [(string? 42), 42]
;; {:instant 1450937836680,
;;  :ns "taoensso.truss.examples",
;;  :elidable? true,
;;  :val 42,
;;  :val-type java.lang.Long,
;;  :?err nil,
;;  :*assert* true,
;;  :?data nil,
;;  :?line 44,
;;  :form-str "(string? 42)"}

;; Truss also automatically traps and handles exceptions
(have string? (/ 1 0)) ; =>
;; Invariant violation in `taoensso.truss.examples:59` [pred-form, val]:
;; [(string? (/ 1 0)), <undefined>]
;; `val` error: java.lang.ArithmeticException: Divide by zero
;; {:instant 1450938025898,
;;  :ns "taoensso.truss.examples",
;;  :elidable? true,
;;  :val undefined/threw-error,
;;  :val-type undefined/threw-error,
;;  :?err #error {
;;  :cause "Divide by zero"
;;  :via
;;  [{:type java.lang.ArithmeticException
;;    :message "Divide by zero"
;;    :at [clojure.lang.Numbers divide "Numbers.java" 158]}]
;;  :trace [...]}]
;;  :*assert* true,
;;  :?data nil,
;;  :?line 59,
;;  :form-str "(string? (/ 1 0))"}
)

;;;; Destructured bindings

(comment
;; You can assert against multipe args at once
(let [[x y z] (have string? "foo" "bar" "baz")]
  (str x y z)) ; => "foobarbaz"

;; This won't compromise error message clarity
(let [[x y z] (have string? "foo" 42 "baz")]
  (str x y z)) ; =>
  ;; Invariant violation in `taoensso.truss.examples:91` [pred-form, val]:
;; [(string? 42), 42]
;; {:instant 1450938267043,
;;  :ns "taoensso.truss.examples",
;;  :elidable? true,
;;  :val 42,
;;  :val-type java.lang.Long,
;;  :?err nil,
;;  :*assert* true,
;;  :?data nil,
;;  :?line 91,
;;  :form-str "(string? 42)"}
)

;;;; Attaching debug data

(comment
(defn my-handler [ring-req x y]
  (let [[x y] (have integer? x y :data {:ring-req ring-req})]
    (* x y)))

(my-handler {:foo :bar} 5 nil) ; =>
;; Invariant violation in `taoensso.truss.examples:146` [pred-form, val]:
;; [(integer? y), <nil>]
;; {:instant 1450939196719,
;;  :ns "taoensso.truss.examples",
;;  :elidable? true,
;;  :val nil,
;;  :val-type nil,
;;  :?err nil,
;;  :*assert* true,
;;  :?data {:ring-req {:foo :bar}}, ; <--- This got included
;;  :?line 146,
;;  :form-str "(integer? y)"}
)

;;;; Attaching dynamic debug data

(comment
(defn wrap-ring-dynamic-assertion-data
  "Returns Ring handler wrapped so that assertion violation errors in handler
  will include `(data-fn <ring-req>)` as debug data."
  [data-fn ring-handler-fn]
  (fn [ring-req]
    (truss/with-dynamic-assertion-data (data-fn ring-req)
      (ring-handler-fn ring-req))))

(defn ring-handler [ring-req]
  (have? string? 42) ; Will always fail
  {:status 200 :body "Done"})

(def wrapped-ring-handler
  (wrap-ring-dynamic-assertion-data
    ;; Include Ring session with all handler's assertion errors:
    (fn data-fn [ring-req] {:ring-session (:session ring-req)})
    ring-handler))

(comment
  (wrapped-ring-handler
    {:method :get :uri "/" :session {:user-name "Stu"}}) ; =>
   ;; Invariant violation in `taoensso.truss.examples:136` [pred-form, val]:
   ;; [(string? 42), 42]
   ;; {:*?data* {:ring-session {:user-name "Stu"}}, ; <--- This got included
   ;;  :elidable? true,
   ;;  :dt #inst "2015-12-28T05:57:49.759-00:00",
   ;;  :val 42,
   ;;  :ns-str "taoensso.truss.examples",
   ;;  :val-type java.lang.Long,
   ;;  :?err nil,
   ;;  :*assert* true,
   ;;  :?data nil,
   ;;  :?line 136,
   ;;  :form-str "(string? 42)"}
  )
)

;;;; Assertions within data structures

(comment

;;; Compare
(have vector?      [:a :b :c]) ; => [:a :b :c]
(have keyword? :in [:a :b :c]) ; => [:a :b :c]
)

;;;; Assertions within :pre/:post conditions

(comment

(defn square [n]
  ;; Note the use of `have?` instead of `have`
  {:pre  [(have? #(or (nil? %) (integer? %)) n)]
   :post [(have? integer? %)]}
  (let [n (or n 1)]
    (* n n)))

(square 5)   ; => 25
(square nil) ; => 1
)

;;;; Special predicates

(comment

;; A predicate can be anything
(have #(and (integer? %) (odd? %) (> % 5)) 7) ; => 7

;; Omit the predicate as a shorthand for #(not (nil? %))
(have "foo") ; => "foo"
(have nil)   ; => Error

;;; There's a number of other optional shorthands

;; Combine predicates (or)
(have [:or nil? string?] "foo") ; => "foo"

;; Combine predicates (and)
(have [:and integer? even? pos?] 6) ; => 6

;; Element of
(have [:el #{:a :b :c :d}] :b) ; => :b
(have [:el #{:a :b :c :d}] :e) ; => Error

;; Superset
(have [:set>= #{:a :b}] #{:a :b :c}) ; => #{:a :b :c}

;; Key superset
(have [:ks>= #{:a :b}] {:a "A" :b nil :c "C"}) ; => {:a "A" :b nil :c "C"}

;; Non-nil keys
(have [:ks-nnil? #{:a :b}] {:a "A" :b nil :c "C"}) ; => Error
)

;;;; Writing custom validators

(comment

;; A custom predicate:
(defn pos-int? [x] (and (integer? x) (pos? x)))

(defn have-person
  "Returns given arg if it's a valid `person`, otherwise throws an error"
  [person]
  (truss/with-dynamic-assertion-data {:person person} ; (Optional) setup some extra debug data
    (have? map? person)
    (have? [:ks>= #{:age :name}] person)
    (have? [:or nil? pos-int?] (:age person)))
  person ; Return input if nothing's thrown
  )

(have-person {:name "Steve" :age 33})   ; => {:name "Steve", :age 33}
(have-person {:name "Alice" :age "33"}) ; => Error
)

;;;; Assertions without elision

(comment
(defn get-restricted-resource [ring-session]
  ;; This is an important security check so we'll use `have!` here instead of
  ;; `have` to make sure the check is never elided (skipped):
  (have! string? (:auth-token ring-session))

  "return-restricted-resource-content")
)
