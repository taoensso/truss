**Status**: still need to find some time to finish publishing this. ETA early Jan 2016.

[More by @ptaoussanis] | **[CHANGELOG]** | [API] | current [Break Version] below:

```clojure
[com.taoensso/truss "1.0.0-RC1"] ; Stable
```

# Truss

### An opinionated assertions API for Clojure/Script

**Or**: Wait, are we sure that's always a string?

**Truss** is a micro library for Clojure/Script consisting of a single macro that provides fast, flexible **runtime condition assertions** with **great error messages**.

![Hero]

> A doubtful friend is worse than a certain enemy. Let a man be one thing or the other, and we then know how to meet him. - **Aesop**

## Quickstart

**TODO**: Embed video

Truss uses a simple `(predicate arg)` pattern that should be **immediately familiar** to anyone that uses Clojure. 

This one example covers **most of the Truss API**:

```clojure
(defn square [n]
  (let [n (have integer? n)]
    (* n n)))

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
```

The `(have integer? <arg>)` annotation is a standard Clojure form that both **documents the intention of the code** in a way that **cannot go stale**, and provides a **runtime check** that throws a detailed error message on any unexpected violation.

## Features

 * Tiny cross-platform codebase with zero external dependencies
 * Trivial to understand and use
 * Easy to adopt just where you need it
 * Minimal-to-zero runtime performance cost

## Motivation

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

## Detailed usage

Add the necessary dependency to your project:

```clojure
[com.taoensso/truss "1.0.0-RC1"]
```

And setup your namespace imports:

```clojure
(ns my-clj-ns ; Clojure namespace
  (:require
   [taoensso.truss :as truss :refer (have have! have?)]))

(ns my-cljs ; ClojureScript namespace
  (:require-macros
   [taoensso.truss :as truss :refer (have have! have?)]))
```

And you're good to go - see the examples below (or [here](https://github.com/ptaoussanis/truss/blob/master/src/taoensso/truss/examples.cljc)) for usage ideas.

### Examples: inline assertions and bindings

```clojure
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
```

### Examples: destructured bindings

```clojure
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
```

### Examples: attaching debug data

You can attach arbitrary debug data to be displayed on violations.

```clojure
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
```

### Examples: assertions within data structures

```clojure
;;; Compare
(have vector?      [:a :b :c]) ; => [:a :b :c]
(have keyword? :in [:a :b :c]) ; => [:a :b :c]
```

### Examples: assertions within :pre/:post conditions

As you'd expect, just make sure to use the `have?` variant which always returns a truthy val on success.

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

### Examples: special predicates

Truss offers some shorthands for your convenience. **These are all optional**: the same effect can always be achieved with an equivalent predicate fn.

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

;; Element of
(have [:el #{:a :b :c :d}] :b) ; => :b
(have [:el #{:a :b :c :d}] :e) ; => Error

;; Superset
(have [:set>= #{:a :b}] #{:a :b :c}) ; => #{:a :b :c}

;; Key superset
(have [:ks>= #{:a :b}] {:a "A" :b nil :c "C"}) ; => {:a "A" :b nil :c "C"}

;; Non-nil keys
(have [:ks-nnil? #{:a :b}] {:a "A" :b nil :c "C"}) ; => Error
```

## FAQ

#### What's the performance cost?

Usually insignificant. Truss has been tuned to minimize both code expansion size[1] and runtime costs.

```clojure
(quick-bench 100000 ; 100k iterations
       (string? "foo")
  (have string? "foo"))
;; => [4.19 4.78] ; 4.19ms vs 4.78ms
```

> [1] This can be important for ClojureScript codebases

So you're seeing ~15% overhead here against a simple predicate test. In practice this means that predicate costs dominate.

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

#### Wouldn't you rather just have static typing?

Every conceivable type system necessarily provides trade-offs. Sometimes you'll want a static type system, sometimes a dynamic, and sometimes a gradual.

Truss is a tool to help folks who've decided that a dynamic or gradual type system makes sense for their current team and objectives.

#### Wouldn't you rather just have unit tests?

Unit tests are one common way of dealing with some of the [challenges] that large Clojure codebases face.

In my experience, Truss can cover a lot of (but not all) the same ground. In cases of overlap, choose whichever you feel would be more productive for your team and objectives.

Personally, I tend to favour Truss when possible because an assertion:

  1. Is present _precisely_ where it's relevant
  2. Acts as a form of documentation
  3. Tends to be quicker to write and keep up-to-date than a test
  4. Offers runtime protection

#### How does this compare to gradual typing / [core.typed]?

Typed Clojure is _awesome_ and something I'd definitely recommend considering. As with all type systems though, it necessarily provides trade-offs.

Some of the challenges I've noticed with gradual typing:

  1. Buy-in cost can sometimes be high (often all-or-nothing adoption)
  2. Difficulty evaluating benefits (because of the buy-in cost)
  3. Difficulty with inclusion in libraries (forces downstream requirements)
  4. Difficulty educating developers (new syntax, type theory, etc.)
  5. Added complexity (lots of code, extra tools, possible bugs, library incompatibilities, etc.)
  6. Sometimes clumsy/difficult to define nuanced types (e.g. transducers)
  7. Low control over long-term cost/benefit trade-offs (can be difficult to apply with precision where it'd most help)

To be clear: there's absolutely times when gradual typing provides a wonderful fit. Indeed, I've found the practical overlap between core.typed and Truss small and the two often complimentary.

Experiment, weigh your options, choose whatever makes sense for your team and objectives.

#### How does this compare to [@prismatic/schema]?

Confession: I wrote Truss before Schema was published so I've never actually used it or looked at it too closely.

Some superficial observations from looking at the README now:

  1. Goals seem to be a superset of Truss? (incl. coersions, etc.)
  2. Seems to be more declarative (separates structure from validation calls)
  3. Seems to be more concerned with types than general (predicate) conditions?
  4. Codebase and API both seem significantly more extensive?

Would definitely encourage checking it out if you haven't!

#### Why isn't there something like this for \<other programming language\>?

Short answer: Lisp macros. You could write something _similar_ to Truss in most languages but you'd have a tough time getting the same balance of brevity, flexibility, and performance.

Some things are just _much_ easier in a Lisp. Logging's one of them, this is another.

#### What's your view on static vs dynamic typing?

I've used and enjoyed aspects of a number of type systems at one point or another. What I reach for on a particular job is entirely dependent on what I expect would be the most productive for that job.

Likewise, the tools _you'll_ find most productive will necessarily depend _on you_: your objectives and your preferred style of working. Trying to argue that one kind of programming is strictly better than another is like trying to argue that a chisel is strictly better than a wrench.

I'd advocate using whatever tools help you meet your objectives, and regularly trying new stuff to get a sense of your options. Use what you like, skip the rest.

## Contacting me / contributions

Please use the project's [GitHub issues page] for all questions, ideas, etc. **Pull requests welcome**. See the project's [GitHub contributors page] for a list of contributors.

Otherwise, you can reach me at [Taoensso.com]. Happy hacking!

\- [Peter Taoussanis]

## License

Distributed under the [EPL v1.0] \(same as Clojure).  
Copyright &copy; 2015 [Peter Taoussanis].

<!--- Standard links -->
[Taoensso.com]: https://www.taoensso.com
[Peter Taoussanis]: https://www.taoensso.com
[@ptaoussanis]: https://www.taoensso.com
[More by @ptaoussanis]: https://www.taoensso.com
[Break Version]: https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md

<!--- Standard links (repo specific) -->
[CHANGELOG]: https://github.com/ptaoussanis/truss/releases
[API]: http://ptaoussanis.github.io/truss/
[GitHub issues page]: https://github.com/ptaoussanis/truss/issues
[GitHub contributors page]: https://github.com/ptaoussanis/truss/graphs/contributors
[EPL v1.0]: https://raw.githubusercontent.com/ptaoussanis/truss/master/LICENSE
[Hero]: https://raw.githubusercontent.com/ptaoussanis/truss/master/hero.png "Egyptian ship with rope truss, the oldest known use of trusses (about 1250 BC)"

<!--- Unique links -->
[core.typed]: https://github.com/clojure/core.typed
[@prismatic/schema]: https://github.com/Prismatic/schema
[challenges]: #challenges
