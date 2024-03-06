# Setup

Add the [relevant dependency](../#latest-releases) to your project:

```clojure
Leiningen: [com.taoensso/truss               "x-y-z"] ; or
deps.edn:   com.taoensso/truss {:mvn/version "x-y-z"}
```

And setup your namespace imports:

```clojure
(ns my-ns (:require [taoensso.truss :as truss :refer [have have?]]))
```

# Basics

The main way to use Truss is with the [`have`](https://taoensso.github.io/truss/taoensso.truss.html#var-have) macro.

You give it a predicate, and an argument that you believe should satisfy the predicate.

For example:

```clojure
(defn greet
  "Given a string username, prints a greeting message."
  [username]
  (println "hello" (have string? username)))
```

In this case the predicate is `string?` and argument is `username`:

- If `(string? username)` is truthy: the invariant **succeeds** and `(have ...)` returns the given username.
- If `(string? username)` is falsey: the invariant **fails** and a detailed **error is thrown** to help you debug.

That's the basic idea.

These `(have <pred> <arg>)` annotations are standard Clojure forms that both **documents the intention of the code** in a way that **cannot go stale**, and provides a **runtime check** that throws a detailed error message on any unexpected violation.

Everything else documented here is either:

- Advice on how best to use Truss, or
- Details on features for convenience or advanced situations

## When to use Truss assertions

You use Truss to **formalize assumptions** that you have about your data (e.g. **function arguments**, **intermediate values**, or **current application state** at some point in your execution flow).

So any time you find yourself making **implementation choices based on implicit information** (e.g. the state your application should be in if this code is running) - that's when you might want to reach for Truss instead of a comment or Clojure assertion.

Use Truss assertions like **salt in good cooking**; a little can go a long way.

## `have` variants

While most users will only need to use the base `have` macro, a few variations are provided for convenience:

Macro | On success | On failure | Subject to elision? | Comment
:--- | :--- | :--- | :--- | :---
[have](https://taoensso.github.io/truss/taoensso.truss.html#var-have) | Returns given arg/s | Throws | Yes | Most common
[have!](https://taoensso.github.io/truss/taoensso.truss.html#var-have.21) | Returns given arg/s | Throws | No | As above, without elision
[have?](https://taoensso.github.io/truss/taoensso.truss.html#var-have.3F) | Returns true | Throws | Yes | Useful in pre/post conditions
[have!?](https://taoensso.github.io/truss/taoensso.truss.html#var-have.21.3F) | Returns true | Throws | No | As above, without elision

In all cases:

- The basic syntax is identical
- The behaviour on failure is identical

What varies is the return value, and whether elision is possible.

# Examples

> All examples are from [`/examples.cljc`](../blob/master/examples.cljc)

Truss's sweet spot is often in longer, complex code (difficult to show here). So these examples are mostly examples of **syntax**, not **use case**. In particular, they mostly focus on simple **argument type assertions** since those are the easiest to understand.

In practice, you'll often find more value from assertions about your **application state** or **intermediate `let` values** _within_ a larger piece of code.

## Inline assertions and bindings

A Truss `(have <pred> <arg>)` form will either throw or return the given argument. This lets you use these forms within other expressions and within `let` bindings, etc.

```clojure
;; You can add an assertion inline
(println (have string? "foo"))

;; Or you can add an assertion to your bindings
(let [s (have string? "foo")]
  (println s))

;; Anything that fails the predicate will throw an error
(have string? 42) ; =>
;; Invariant failed at truss-examples[41,1]: (string? 42)
;; {:dt #inst "2023-07-31T09:58:07.927-00:00",
;;  :pred clojure.core/string?,
;;  :arg {:form 42, :value 42, :type java.lang.Long},
;;  :env {:elidable? true, :*assert* true},
;;  :loc
;;  {:ns truss-examples,
;;   :line 41,
;;   :column 1,
;;   :file "examples.cljc"}}

;; Truss also automatically traps and handles exceptions
(have string? (/ 1 0)) ; =>
;; Invariant failed at truss-examples[54,1]: (string? (/ 1 0))
;;
;; Error evaluating arg: Divide by zero
;; {:dt #inst "2023-07-31T09:59:06.149-00:00",
;;  :pred clojure.core/string?,
;;  :arg
;;  {:form (/ 1 0),
;;   :value truss/undefined-arg,
;;   :type truss/undefined-arg},
;;  :env {:elidable? true, :*assert* true},
;;  :loc
;;  {:ns truss-examples,
;;   :line 54,
;;   :column 1,
;;   :file "examples.cljc"},
;;  :err
;;  #error
;;  {:cause "Divide by zero"
;;   :via
;;   [{:type java.lang.ArithmeticException
;;     :message "Divide by zero"
;;     :at [clojure.lang.Numbers divide "Numbers.java" 190]}]
;;   :trace
;;   [<...>]}}
```

## Destructured bindings

```clojure
;; You can assert against multipe args at once
(let [[x y z] (have string? "foo" "bar" "baz")]
  (str x y z)) ; => "foobarbaz"

;; This won't compromise error message clarity
(let [[x y z] (have string? "foo" 42 "baz")]
  (str x y z)) ; =>
;; Invariant failed at truss-examples[89,15]: (string? 42)
;; {:dt #inst "2023-07-31T10:01:00.991-00:00",
;;  :pred clojure.core/string?,
;;  :arg {:form 42, :value 42, :type java.lang.Long},
;;  :env {:elidable? true, :*assert* true},
;;  :loc
;;  {:ns truss-examples,
;;   :line 89,
;;   :column 15,
;;   :file "examples.cljc"}}
```

## Attaching debug data

You can attach arbitrary debug data to be displayed on violations:

```clojure
(defn my-handler [ring-req x y]
  (let [[x y] (have integer? x y :data {:ring-req ring-req})]
    (* x y)))

(my-handler {:foo :bar} 5 nil) ; =>
;; Invariant failed at truss-examples[107,15]: (integer? y)
;; {:dt #inst "2023-07-31T10:02:03.415-00:00",
;;  :pred clojure.core/integer?,
;;  :arg {:form y, :value nil, :type nil},
;;  :env {:elidable? true, :*assert* true},
;;  :loc
;;  {:ns truss-examples,
;;   :line 107,
;;   :column 15,
;;   :file "examples.cljc"},
;;  :data {:dynamic nil, :arg {:ring-req {:foo :bar}}}}
```

## Attaching dynamic debug data

And you can attach shared debug data at the `binding` level:

```clojure
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
;; Invariant failed at truss-examples[136,3]: (string? 42)
;; {:dt #inst "2023-07-31T10:02:41.459-00:00",
;;  :pred clojure.core/string?,
;;  :arg {:form 42, :value 42, :type java.lang.Long},
;;  :env {:elidable? true, :*assert* true},
;;  :loc
;;  {:ns truss-examples,
;;   :line 136,
;;   :column 3,
;;   :file "examples.cljc"},
;;  :data {:dynamic {:ring-session {:user-name "Stu"}}, :arg nil}}
```

## Assertions within data structures

```clojure
;;; Compare
(have vector?      [:a :b :c]) ; => [:a :b :c]
(have keyword? :in [:a :b :c]) ; => [:a :b :c]
```

## Assertions within :pre/:post conditions

Just make sure to use the `have?` variant which always returns a truthy val on success:

```clojure
(defn square [n]
  ;; Note the use of `have?` instead of `have`
  {:pre  [(have? #(or (nil? %) (integer? %)) n)]
   :post [(have? integer? %)]}
  (let [n (or n 1)]
    (* n n)))

(square 5)   ; => 25
(square nil) ; => 1
```

## Special predicates

Truss offers some shorthands for your convenience. **These are all optional**: the same effect can always be achieved with an equivalent predicate fn:

```clojure
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

;; Element of (checks for set containment)
(have [:el #{:a :b :c :d nil}] :b)  ; => :b
(have [:el #{:a :b :c :d nil}] nil) ; => nil
(have [:el #{:a :b :c :d nil}] :e)  ; => Error

;; Superset
(have [:set>= #{:a :b}] #{:a :b :c}) ; => #{:a :b :c}

;; Key superset
(have [:ks>= #{:a :b}] {:a "A" :b nil :c "C"}) ; => {:a "A" :b nil :c "C"}

;; Non-nil keys
(have [:ks-nnil? #{:a :b}] {:a "A" :b nil :c "C"}) ; => Error
```

## Complex validators

No need for any special syntax or concepts, just define a function as you'd like:

```clojure
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
```

# Motivation

<a href="https://youtu.be/gMB4Y-EIArA" title="Truss talk (YouTube)"><img src="https://raw.githubusercontent.com/ptaoussanis/truss/master/talk.jpg" width="600"/></a>

Clojure is a beautiful language full of smart trade-offs that tends to produce production code that's short, simple, and easy to understand.

But every language necessarily has trade-offs. In the case of Clojure, **dynamic typing** leads to one of the more common challenges that I've observed in the wild: **debugging or refactoring large codebases**.

Specifically:

 * **Undocumented type assumptions** changing (used to be this thing was never nil; now it can be)
 * Documented **type assumptions going stale** (forgot to update comments)
 * **Unhelpful error messages** when a type assumption is inevitably violated (it crashed in production? why?)

Thankfully, this list is almost exhaustive; in my experience these few causes often account for **80%+ of real-world incidental difficulty**.

So **Truss** targets these issues with a **practical 80% solution** that emphasizes:

 1. **Ease of adoption** (incl. partial/precision/gradual adoption)
 2. **Ease of use** (non-invasive API, trivial composition, etc.)
 3. **Flexibility** (scales well to large, complex systems)
 4. **Speed** (blazing fast => can use in production, in speed-critical code)
 5. **Simplicity** (lean API, zero dependencies, tiny codebase)

The first is particularly important since the need for assertions in a good Clojure codebase is surprisingly _rare_.

Every codebase has trivial parts and complex parts. Parts that suffer a lot of churn, and parts that haven't changed in years. Mission-critical parts (bank transaction backend), and those that aren't so mission-critical (prototype UI for the marketing department).

Having the freedom to reinforce code only **where and when you judge it worthwhile**:

 1. Let's you (/ your developers) easily evaluate the lib
 2. Makes it more likely that you (/ your developers) will actually _use_ the lib
 3. Eliminates upfront buy-in costs
 4. Allows you to retain control over long-term cost/benefit trade-offs
