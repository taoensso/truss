<a href="https://www.taoensso.com" title="More stuff by @ptaoussanis at www.taoensso.com">
<img src="https://www.taoensso.com/taoensso-open-source.png" alt="Taoensso open-source" width="400"/></a>

**[CHANGELOG]** | [API] | current [Break Version]:

```clojure
[com.taoensso/truss "1.6.0-RC1"] ; See CHANGELOG for details
[com.taoensso/truss "1.5.0"]     ; Stable
```

> Please consider helping to [support my continued open-source Clojure/Script work]? 
> 
> Even small contributions can add up + make a big difference to help sustain my time writing, maintaining, and supporting Nippy and other Clojure/Script libraries. **Thank you!**
>
> \- Peter Taoussanis

# Truss

### Great Clojure/Script error messages where you need them most

**Or**: A **lightweight** alternative to **static typing**, [clojure.spec], [core.typed], [@plumatic/schema], etc.

**Or**: `(have set? x) => (if (set? x) x (throw-detailed-assertion-error!))`

**Truss** is a **micro library** for Clojure/Script that provides fast, flexible **runtime condition assertions** with **great error messages**. It can be used to get many of the most important benefits of **static/gradual typing** without the usual rigidity or onboarding costs.

![Hero]

> A doubtful friend is worse than a certain enemy. Let a man be one thing or the other, and we then know how to meet him. - **Aesop**

## Features

 * **Tiny** cross-platform codebase with **zero external dependencies**.
 * Trivial to understand and use.
 * Use **just** when+where you need it (**incl. libraries**).
 * Minimal (or zero) runtime performance cost.
 * A practical **80% solution** (focus on **improving error messages**).

## How does it compare to [clojure.spec], [core.typed], [@plumatic/schema]?

This is unfortunately a _really_ tough question to answer briefly. It comes down to trade-offs.

Very superficially, very **subjectively**:

Library      | Ease of setup | Ease of use | Features | Favourite aspect/s
------------ | ------------- | ----------- | -------- | ------------------
clojure.spec | ★★           | ★★         | ★★½     | Official, wide applicability (docs, generative tests, etc.)
core.typed   | ★             | ★          | ★★★     | Compile-time safety, most rigorous
Schema       | ★★½          | ★★½       | ★        | Simplicity/power balance, widely adopted
**Truss**    | ★★★          | ★★★       | ★        | Simplicity/power balance, API

Re: performance - Truss consistently [outperforms](http://muhuk.github.io/validation-benchmark/) similar libs, though I'd suggest that speed is probably one of the least interesting factors to consider when choosing a lib to use.

The best general recommendation I can make is to try actually experiment with the options that seem appealing. Nothing beats hands-on experience for deciding what best fits your particular needs and tastes.

> See also [@marick/structural-typing] for another structural typing option by the creator of [Midje] :-)

## Quickstart

Add the necessary dependency to your project:

```clojure
[com.taoensso/truss "1.5.0"]
```

And setup your namespace imports:

```clojure
(ns my-clj-ns ; Clojure namespace
  (:require [taoensso.truss :as truss :refer (have have! have?)]))

(ns my-cljs-ns ; ClojureScript namespace
  (:require [taoensso.truss :as truss :refer-macros (have have! have?)]))
```

Truss uses a simple `(predicate arg)` pattern that should **immediately feel familiar** to Clojure users:

```clojure
(defn square [n]
  (let [n (have integer? n)] ; <- A Truss assertion [1]
    (* n n)))

;; [1] This basically expands to (if (integer? n) n (throw-detailed-assertion-error!))

(square 5)   ; => 25
(square nil) ; =>
;; Invariant violation in `taoensso.truss.examples:10`.
;; Test form `(integer? n)` with failing input: `<nil>`
;; {:*?data* nil,
;;  :elidable? true,
;;  :dt #inst "2016-10-14T10:01:28.671-00:00",
;;  :val nil,
;;  :ns-str "taoensso.truss.examples",
;;  :val-type nil,
;;  :?err nil,
;;  :*assert* true,
;;  :?data nil,
;;  :?line 10,
;;  :form-str "(integer? n)"}
```

#### And that's it, you know the Truss API.

The `(have <pred> <arg>)` annotation is a standard Clojure form that both **documents the intention of the code** in a way that **cannot go stale**, and provides a **runtime check** that throws a detailed error message on any unexpected violation.

### When to use a Truss assertion

You use Truss to **formalize assumptions** that you have about your data (e.g. **function arguments**, **intermediate values**, or **current application state** at some point in your execution flow).

So any time you find yourself making **implementation choices based on implicit information** (e.g. the state your application should be in if this code is running) - that's when you might want to reach for Truss instead of a comment or Clojure assertion.

Use Truss assertions like **salt in good cooking**; a little can go a long way.

## Motivation

> Feel free to skim/skip this section :-)

<a href="https://youtu.be/gMB4Y-EIArA" title="Truss talk (YouTube)">
<img src="https://raw.githubusercontent.com/ptaoussanis/truss/master/talk.jpg" width="600"/>
</a>

Clojure is a beautiful language full of smart trade-offs that tends to produce production code that's short, simple, and easy to understand.

<a id="challenges"></a>

But every language necessarily has trade-offs. In the case of Clojure, **dynamic typing** leads to one of the more common challenges that I've observed in the wild: **debugging or refactoring large codebases**.

Specifically:

 * **Undocumented type assumptions** changing (used to be this thing was never nil; now it can be)
 * Documented **type assumptions going stale** (forgot to update comments)
 * **Unhelpful error messages** when a type assumption is inevitably violated (it crashed in production? why?)

Thankfully, this list is almost exhaustive; in my experience these few causes often account for **80%+ of real-world incidental difficulty**.

So **Truss** targets these issues with a **practical 80% solution** that emphasizes:

 1. Ease of adoption (incl. partial/precision adoption)
 2. Ease of use
 3. Flexibility

The first is particularly important since the need for assertions in a good Clojure codebase is surprisingly _rare_.

Every codebase has trivial parts and complex parts. Parts that suffer a lot of churn, and parts that haven't changed in years. Mission-critical parts (bank transaction backend), and those that aren't so mission-critical (prototype UI for the marketing department).

Having the freedom to reinforce code only **where and when you judge it worthwhile**:

 1. Let's you (/ your developers) easily evaluate the lib
 2. Makes it more likely that you (/ your developers) will actually _use_ the lib
 3. Eliminates upfront buy-in costs
 4. Allows you to retain control over long-term cost/benefit trade-offs

<a id="detailed-usage"/>

## Examples!

> All examples are from [`src/taoensso/truss/examples.cljc`](https://github.com/ptaoussanis/truss/blob/master/src/taoensso/truss/examples.cljc)

Truss's sweet spot is often in longer, complex code (difficult to show here). So these examples are mostly examples of **syntax**, not **use case**. In particular, they mostly focus on simple **argument type assertions** since those are the easiest to understand.

In practice, you'll often find more value from assertions about your **application state** or **intermediate `let` values** _within_ a larger piece of code.

### Inline assertions and bindings

A Truss `(have <pred> <arg>)` form will either throw or return the given argument. This lets you use these forms within other expressions and within `let` bindings, etc.

```clojure
;; You can add an assertion inline
(println (have string? "foo"))

;; Or you can add an assertion to your bindings
(let [s (have string? "foo")]
  (println s))

;; Anything that fails the predicate will throw an error
(have string? 42) ; =>
;; Invariant violation in `taoensso.truss.examples:44`.
;; Test form `(string? 42)` with failing input: `42`
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
;; Invariant violation in `taoensso.truss.examples:59`.
;; Test form `(string? (/ 1 0))` with failing input: `<undefined>`
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
```

### Destructured bindings

```clojure
;; You can assert against multipe args at once
(let [[x y z] (have string? "foo" "bar" "baz")]
  (str x y z)) ; => "foobarbaz"

;; This won't compromise error message clarity
(let [[x y z] (have string? "foo" 42 "baz")]
  (str x y z)) ; =>
;; Invariant violation in `taoensso.truss.examples:91`.
;; Test form `(string? 42)` with failing input: `42`
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
```

### Attaching debug data

You can attach arbitrary debug data to be displayed on violations:

```clojure
(defn my-handler [ring-req x y]
  (let [[x y] (have integer? x y :data {:ring-req ring-req})]
    (* x y)))

(my-handler {:foo :bar} 5 nil) ; =>
;; Invariant violation in `taoensso.truss.examples:146`.
;; Test-form `(integer? y)` with failing input: `<nil>`
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
```

### Attaching dynamic debug data

And you can attach shared debug data at the `binding` level:

```clojure
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
   ;; Invariant violation in `taoensso.truss.examples:136`
   ;; Test form `(string? 42)` with failing input: `42
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
```

### Assertions within data structures

```clojure
;;; Compare
(have vector?      [:a :b :c]) ; => [:a :b :c]
(have keyword? :in [:a :b :c]) ; => [:a :b :c]
```

### Assertions within :pre/:post conditions

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

### Special predicates

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

### Writing custom validators

No need for any special syntax or concepts, just define a function as you'd like:

```clojure
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
```

## FAQ

#### How can I report/log violations?

By default, Truss just throws an **exception** on any invariant violations. You can adjust that behaviour with the `set-error-fn!` and `with-error-fn` utils.

Some common usage ideas:

 * Use `with-error-fn` to capture violations during unit testing
 * Use `set-error-fn!` to _log_ violations with something like [Timbre]

#### Should I annotate my whole API?

**Please don't**! I'd encourage you to think of Truss assertions like **salt in good cooking**; a little can go a long way, and the need for too much salt can be a sign that something's gone wrong in the cooking.

Another useful analogy would be the Clojure STM. Good Clojure code tends to use the STM very rarely. When you want the STM, you _really_ want it - but many new Clojure developers end up surprised at just how rarely they end up wanting it in an idiomatic Clojure codebase.

Do the interns keep getting that argument wrong despite attempts at making the code as clear as possible? By all means, add an assertion.

More than anything, I tend to use Truss assertions as a form of documentation in long/hairy or critical bits of code to remind myself of any unusual input/output contracts/expectations. E.g. for performance reasons, we _need_ this to be a vector; throw if a list comes in since it means that some consumer has a bug.

I very rarely use Truss for library code, though I wouldn't hesitate to in cases that might inherently be confusing or to guard against common error cases that'd otherwise be hard to debug.

#### What's the performance cost?

Usually insignificant. Truss has been **highly tuned** to minimize both code expansion size[1] and runtime costs.

In many common cases, a Truss expression expands to no more than `(if (pred arg) arg (throw-detailed-assertion-error!))`.

```clojure
(quick-bench 1e5
  (if (string? "foo") "foo" (throw (Exception. "Assertion failure")))
  (have string? "foo"))
;; => [4.19 4.17] ; ~4.2ms / 100k iterations
```

> [1] This can be important for ClojureScript codebases

So we're seeing zero overhead against a simple predicate test in this example. In practice this means that predicate costs dominate.

For simple predicates (including `instance?` checks), modern JITs work great; the runtime performance impact is almost always completely insignificant even in tight loops.

In rare cases where the cost does matter (e.g. for an unusually expensive predicate), Truss supports complete elision in production code. Disable `clojure.core/*assert*` and Truss forms will noop, passing their arguments through with **zero performance overhead**.

An extra macro is provided (`have!`) which ignores `*assert*` and so can never be elided. This is handy for implementing (and documenting) critical checks like security assertions that you never want disabled.

```clojure
(defn get-restricted-resource [ring-session]
  ;; This is an important security check so we'll use `have!` here instead of
  ;; `have` to make sure the check is never elided (skipped):
  (have! string? (:auth-token ring-session))

  "return-restricted-resource-content")
```

> **Tip**: when in doubt just use `have!` instead of `have`

#### How do I disable `clojure.core/*assert*`?

If you're using Leiningen, you can add the following to your `project.clj`:

```clojure
:global-vars {;; *warn-on-reflection* true
              *assert*                true
              ;; *unchecked-math*     :warn-on-boxed
              }
```

## Contacting me / contributions

Please use the project's [GitHub issues page] for all questions, ideas, etc. **Pull requests welcome**. See the project's [GitHub contributors page] for a list of contributors.

Otherwise, you can reach me at [Taoensso.com]. Happy hacking!

\- [Peter Taoussanis]

## License

Distributed under the [EPL v1.0] \(same as Clojure).  
Copyright &copy; 2015-2016 [Peter Taoussanis].

<!--- Standard links -->
[Taoensso.com]: https://www.taoensso.com
[Peter Taoussanis]: https://www.taoensso.com
[@ptaoussanis]: https://www.taoensso.com
[More by @ptaoussanis]: https://www.taoensso.com
[Break Version]: https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md
[support my continued open-source Clojure/Script work]: http://taoensso.com/clojure/backers

<!--- Standard links (repo specific) -->
[CHANGELOG]: https://github.com/ptaoussanis/truss/releases
[API]: http://ptaoussanis.github.io/truss/
[GitHub issues page]: https://github.com/ptaoussanis/truss/issues
[GitHub contributors page]: https://github.com/ptaoussanis/truss/graphs/contributors
[EPL v1.0]: https://raw.githubusercontent.com/ptaoussanis/truss/master/LICENSE
[Hero]: https://raw.githubusercontent.com/ptaoussanis/truss/master/hero.png "Egyptian ship with rope truss, the oldest known use of trusses (about 1250 BC)"

<!--- Unique links -->
[core.typed]: https://github.com/clojure/core.typed
[clojure.spec]: http://clojure.org/about/spec
[@plumatic/schema]: https://github.com/plumatic/schema
[@marick/structural-typing]: https://github.com/marick/structural-typing/
[Midje]: https://github.com/marick/Midje
[challenges]: #challenges
[Timbre]: https://github.com/ptaoussanis/timbre
