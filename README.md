**Still working on publishing this**: ETA early Jan 2016

[More by @ptaoussanis] | **[CHANGELOG]** | [API] | current [Break Version] below:

```clojure
[com.taoensso/truss "1.0.0-RC1"] ; Stable, TODO
```

# Truss

### An opinionated assertions API for Clojure/Script

**Or**: Wait, are we sure that's always a string?

**Truss** is a micro library for Clojure/Script consisting of a single macro that provides fast, flexible **runtime condition assertions** with **great error messages**.

![Hero]
	
> A doubtful friend is worse than a certain enemy. Let a man be one thing or the other, and we then know how to meet him. - **Aesop**

## Features

 * Tiny cross-platform codebase with zero external dependencies
 * Trivial to understand and use
 * Easy to adopt just where you need it
 * Minimal-to-zero runtime performance cost

## Motivation

**TODO**: Embed video

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

## What's the API like?

Truss uses a simple `(predicate arg)` pattern that should be immediately familiar to anyone that uses Clojure:

```clojure
(defn int-square [n]
  (let [n (have integer? n)]
    (* n n)))

(int-square 5)   ; => 25
(int-square nil) ; =>
;; Unhandled clojure.lang.ExceptionInfo
;; Invariant violation in `my-ns:18` [pred-form, val]: [(integer? n), <nil>]
;;   {:?form nil,
;;    :instant 1450868195464,
;;    :ns "my-ns",
;;    :elidable? true,
;;    :val nil,
;;    :val-type nil,
;;    :?err nil,
;;    :*assert* true,
;;    :?data nil,
;;    :?line 18,
;;    :form-str "(integer? n)"}
```

The `(have integer? <arg>)` annotation is a standard Clojure form that both **documents the intention of the code** in a way that **cannot go stale**, and provides a **runtime check** that throws a detailed error message on any unexpected violation.

## Getting started
 
Add the necessary dependency to your project:

```clojure
[com.taoensso/truss "1.0.0-RC1"]
```

And setup your namespace imports:

```clojure
(ns my-clj-ns ; Clojure namespace
  (:require
   [taoensso.truss :as truss :refer (have have? have!)]))

(ns my-cljs ; ClojureScript namespace
  (:require-macros
   [taoensso.truss :as truss :refer (have have? have!)]))
```

And you're good to go - see the examples below for usage ideas.

----

### Examples: inline assertions and bindings

```clojure
;; TODO
;; incl. ex. case
```

### Examples: destructured bindings

```clojure
;; TODO
```

### Examples: special predicates

```clojure
;; TODO
```

### Examples: attaching debug data

```clojure
;; TODO
```

### Examples: assertions within data structures

```clojure
;; TODO
```

## FAQ

#### What's the performance cost?

Insignificant. Truss has been **highly tuned** to minimize both code expansion size[1] and runtime costs.

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

An extra macro is provided (`have!`) which ignores `*assert*` and so can never be elided. This can be handy for implementing (and documenting) critical checks like security assertions that you never want disabled.

```clojure
(defn get-restricted-resource [ring-session]

  ;; This is an important security check so we'll use `have!` here instead of
  ;; `have` to make sure the check is never elided (skipped):
  (have! string? (:auth-token ring-session))

  (return-the-resource))
```

> **Tip**: when in doubt just use `have!` instead of `have`

#### How do I disable `clojure.core/*assert*`?

If you're using Leiningen, you can add the following to your project.clj:

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
  3. Tends to be quicker to write than a test
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

To be clear: there's absolutely times when gradual typing provides a wonderful fit. Indeed, I've found the practical overlap between core.typed and Truss to be quite low and the two often even complimentary.

Experiment, weigh your options, choose whatever makes sense for your team and objectives.

#### How does this compare to [@prismatic/schema]?

Confession: I wrote Truss before Schema was published so I've never actually used it or looked at it too closely.

Some superficial observations from looking at the README now:

  1. Goals seem to be a superset of Truss? (coersions, etc.)
  2. Seems to be more declarative (separates structure from validation calls)
  3. Seems to be more concerned with types than general (predicate) conditions?
  4. Codebase and API both seem considerably larger?

Would definitely encourage checking it out if you haven't!

#### Why isn't there something like this for \<other programming language\>?

Short answer: Lisp macros. You could write something _similar_ to Truss in most languages but you'd have a tough time getting the same balance of brevity, flexibility, and performance.

Some things are just _much_ easier in a Lisp. Logging's one of them, this is another.

#### What's your view on static vs dynamic typing?

I've used and enjoyed aspects of pretty much every type system at one point or another. What I reach for on a particular job is entirely dependent on what I expect would be the most productive for that job.

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