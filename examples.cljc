(ns truss-examples
  "Truss usage examples"
  (:require
   [taoensso.truss :as truss
    :refer [have have! have?]]))

(comment

;;;; Assertions

;; Throw detailed exception on unexpected argument:
(defn square [n] (let [n (have integer? n)] (* n n)))

(square 5)   ; => 25
(square nil) ; =>
;; Truss assertion failed at truss-examples[15 11]:
;; (clojure.core/integer? n)
;; {:inst   #inst "2025-02-21T14:17:55.143432000-00:00",
;;  :ns     "truss-examples",
;;  :pred   clojure.core/integer?,
;;  :arg    {:form n, :value nil, :type nil},
;;  :coords [15 11]}

;; Truss provides ex-cause info when relevant:
(have string? (/ 1 0)) ; =>
;; Truss assertion failed at truss-examples[29 1]:
;; (clojure.core/string? (/ 1 0))
;; Error evaluating arg: Divide by zero
;; {:inst #inst "2025-02-21T14:19:36.798972000-00:00",
;;  :ns "truss-examples",
;;  :pred clojure.core/string?,
;;  :arg
;;  {:form (/ 1 0), :value :truss/exception, :type :truss/exception},
;;  :coords [29 1]}

;; Assert multipe args at once:
(let [[x y z] (have string? "foo" "bar" "baz")] (str x y z)) ; => "foobarbaz"
(let [[x y z] (have string? "foo" 42    "baz")] (str x y z)) ; =>
;; Truss assertion failed at truss-examples[41 15]:
;; (clojure.core/string? 42)
;; {:inst #inst "2025-02-21T15:14:06.216906000-00:00",
;;  :ns "truss-examples",
;;  :pred clojure.core/string?,
;;  :arg {:form 42, :value 42, :type java.lang.Long},
;;  :coords [41 15]}

;; Attach arb data to exceptions:

(defn my-ring-handler [ring-req x y]
  (let [[x y] (have integer? x y :data {:ring-req ring-req})]
    (* x y)))

(my-ring-handler {:foo :bar} 5 nil) ; =>
;; Truss assertion failed at truss-examples[52 15]:
;; (clojure.core/integer? y)
;; {:inst #inst "2025-02-21T15:15:03.081041000-00:00",
;;  :ns "truss-examples",
;;  :pred clojure.core/integer?,
;;  :arg {:form y, :value nil, :type nil},
;;  :coords [52 15],
;;  :data {:ring-req {:foo :bar}}}

;; Attach arb context to exceptions:

(defn wrap-ring-ctx
  "Wraps given Ring handler so that the Ring req will be
  present in any thrown `truss/ex-info`s."
  [ring-handler-fn]
  (fn [ring-req]
    (truss/with-ctx    ring-req
      (ring-handler-fn ring-req))))

(defn ring-handler [ring-req]
  (have? string? 42) ; Always fail
  {:status 200 :body "Done"})

(def wrapped-ring-handler (wrap-ring-ctx ring-handler))

(wrapped-ring-handler {:method :get :uri "/" :session {:user-id 101}}) ; =>
;; Truss assertion failed at truss-examples[72 3]:
;; (#function[clojure.core/string?--5494] 42)
;; {:inst #inst "2025-02-21T15:19:07.136882000-00:00",
;;  :ns "truss-examples",
;;  :pred #function[clojure.core/string?--5494],
;;  :arg {:form 42, :value 42, :type java.lang.Long},
;;  :coords [72 3],
;;  :truss/ctx {:method :get, :uri "/", :session {:user-id 101}}}

;; Assert within data structures, compare:

(have vector?      [:a :b :c]) ; => [:a :b :c]
(have keyword? :in [:a :b :c]) ; => [:a :b :c]

;; Assert within `:pre`/`:post` conditions:

(defn square [n]
  {:pre  [(have? #(or (nil? %) (integer? %)) n)] ; Note `have?`, not `have`
   :post [(have? integer? %)]}
  (let [n (or n 1)]
    (* n n)))

(square 5)   ; => 25
(square nil) ; => 1
(square "5") ; => Throws

;; Use any unary predicate:
(have #(and (integer? %) (odd? %) (> % 5)) 7) ; => 7

;; Omit predicate as shorthand non-nil:
(have "foo") ; => "foo"
(have nil)   ; => Throws

;; Combine predicates:
(have [:or nil? string?] "foo")     ; => "foo"
(have [:and integer? even? pos?] 6) ; => 6

;; Element of
(have [:el #{:a :b :c :d}] :b) ; => :b
(have [:el #{:a :b :c :d}] :e) ; => Throws

;; Superset
(have [:set>= #{:a :b}] #{:a :b :c}) ; => #{:a :b :c}

;; Key superset
(have [:ks>= #{:a :b}] {:a "A" :b nil :c "C"}) ; => {:a "A" :b nil :c "C"}

;; Non-nil keys
(have [:ks-nnil? #{:a :b}] {:a "A" :b nil :c "C"}) ; => Throws

;; Write custom validators:

(defn pos-int? "Custom predicate" [x] (and (integer? x) (pos? x)))
(defn have-person
  "Returns given arg if it's a valid `person`, otherwise throws an error"
  [person]
  (truss/with-ctx {:person person} ; (Optional) setup some extra debug data
    (do
      (have? map? person)
      (have? [:ks>= #{:age :name}] person)
      (have? [:or nil? pos-int?] (:age person))))
  person ; Return input if nothing's thrown
  )

(have-person {:name "Steve" :age  33})  ; => {:name "Steve", :age 33}
(have-person {:name "Alice" :age "33"}) ; => Throws

;; Assert whithout without elision (ignore `*assert*`):

(defn get-restricted-resource [ring-session]
  ;; This is an important security check so we'll use `have!` instead
  ;; of `have` to make sure the check is never elided (skipped):
  (have! string? (:auth-token ring-session))
  "return-restricted-resource-content")

)
