(ns truss-examples
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require [taoensso.truss :as truss :refer [have have! have?]]))

;;;; First API example

(comment
(defn square [n]
  (let [n (have integer? n)]
    (* n n)))

;; This returns n if it satisfies (integer? n), otherwise it throws a clear error:
;; (have integer? n)

(square 5)   ; => 25
(square nil) ; =>
;; Invariant failed at truss-examples|9: (integer? nil)
;; {:dt #inst "2022-11-16T19:28:18.587-00:00",
;;  :pred integer?,
;;  :arg {:form n, :value nil, :type nil},
;;  :loc {:ns truss-examples, :line 9, :column 1, :file "..."},
;;  :env {:elidable? true, :*assert* true}}
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
;; Invariant failed at truss-examples|37: (string? 42)
;; {:dt #inst "2022-11-16T19:29:49.004-00:00",
;;  :pred string?,
;;  :arg {:form 42, :value 42, :type java.lang.Long},
;;  :loc {:ns truss-examples, :line 37, :column 1, :file "..."},
;;  :env {:elidable? true, :*assert* true}}

;; Truss also automatically traps and handles exceptions
(have string? (/ 1 0)) ; =>
;; Invariant failed at truss-examples|46:
;;   (string? truss/undefined-arg)
;;
;;   Error evaluating arg: Divide by zero
;;   {:dt #inst "2022-11-16T19:30:15.945-00:00",
;;    :pred string?,
;;    :arg {:form (/ 1 0), :value truss/undefined-arg, :type truss/undefined-arg},
;;    :loc {:ns truss-examples, :line 46, :column 1, :file "..."},
;;    :env {:elidable? true, :*assert* true},
;;    :err #error {
;;    :cause "Divide by zero"
;;    :via
;;    [{:type java.lang.ArithmeticException
;;      :message "Divide by zero"
;;      :at [clojure.lang.Numbers divide "Numbers.java" 190]}]
;;    :trace
;;    [...]}
)

;;;; Destructured bindings

(comment
;; You can assert against multipe args at once
(let [[x y z] (have string? "foo" "bar" "baz")]
  (str x y z)) ; => "foobarbaz"

;; This won't compromise error message clarity
(let [[x y z] (have string? "foo" 42 "baz")]
  (str x y z)) ; =>
;; Invariant failed at truss-examples|74: (string? 42)
;; {:dt #inst "2022-11-16T19:32:07.397-00:00",
;;  :pred string?,
;;  :arg {:form 42, :value 42, :type java.lang.Long},
;;  :loc {:ns truss-examples, :line 74, :column 15, :file "..."},
;;  :env {:elidable? true, :*assert* true}}
)

;;;; Attaching debug data

(comment
(defn my-handler [ring-req x y]
  (let [[x y] (have integer? x y :data {:ring-req ring-req})]
    (* x y)))

(my-handler {:foo :bar} 5 nil) ; =>
;; Invariant failed at truss-examples|88: (integer? nil)
;; {:dt #inst "2022-11-16T19:33:39.842-00:00",
;;  :pred integer?,
;;  :arg  {:form y, :value nil, :type nil},
;;  :loc  {:ns truss-examples, :line 88, :column 15, :file "..."},
;;  :env  {:elidable? true, :*assert* true},
;;  :data {:dynamic nil, :arg {:ring-req {:foo :bar}}}}
)

;;;; Attaching dynamic debug data

(comment
(defn wrap-ring-dynamic-assertion-data
  "Returns Ring handler wrapped so that assertion violation errors in handler
  will include `(data-fn <ring-req>)` as debug data."
  [data-fn ring-handler-fn]
  (fn [ring-req]
    (truss/with-data (data-fn ring-req)
      (ring-handler-fn ring-req))))

(defn ring-handler [ring-req]
  (have? string? 42) ; Will always fail
  {:status 200 :body "Done"})

(def wrapped-ring-handler
  (wrap-ring-dynamic-assertion-data
    ;; Include Ring session with all handler's assertion errors:
    (fn data-fn [ring-req] {:ring-session (:session ring-req)})
    ring-handler))

(wrapped-ring-handler
  {:method :get :uri "/" :session {:user-name "Stu"}}) ; =>
;; Invariant failed at truss-examples|113: (string? 42)
;; {:dt #inst "2022-11-16T19:34:14.006-00:00",
;;  :pred string?,
;;  :arg  {:form 42, :value 42, :type java.lang.Long},
;;  :loc  {:ns truss-examples, :line 113, :column 3, :file "..."},
;;  :env  {:elidable? true, :*assert* true},
;;  :data {:dynamic {:ring-session {:user-name "Stu"}}, :arg nil}}
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
  (truss/with-data {:person person} ; (Optional) setup some extra debug data
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
