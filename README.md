<a href="https://www.taoensso.com/clojure" title="More stuff by @ptaoussanis at www.taoensso.com"><img src="https://www.taoensso.com/open-source.png" alt="Taoensso open source" width="340"/></a>  
[**API**][cljdoc] | [**Wiki**][GitHub wiki] | [Latest releases](#latest-releases) | [Slack channel][]

# Truss

### An opinionated micro toolkit for Clojure/Script errors

**Truss** is a lightweight, dependency-free library for Clojure and ClojureScript that offers a small set of high-value utils and patterns that I've honed over the years to help tame Clojure's famously impenetrable error messages.

It works great with [Telemere](https://www.taoensso.com/telemere) and [Tufte](https://www.taoensso.com/tufte), and includes practical tools for [inline assertions](#inline-assertions), [contextual exceptions](#contextual-exceptions), [cross-platform exceptions](cross-platform-exceptions), [testing exceptions](#testing-exceptions), and [more](#misc-utils).

<img width="640" src="../../blob/master/hero.png" alt="Egyptian ship with rope truss, the oldest known use of trusses (about 1250 BC)."/>

> A doubtful friend is worse than a certain enemy. Let a man be one thing or the other, and we then know how to meet him. - Aesop

## Latest release/s

- `2025-02-27` `v2.0.0`: [release info](../../releases/tag/v2.0.0)

> (v2 expands Truss's scope from just inline assertions to a general toolkit for Clojure/Script errors).

[![Main tests][Main tests SVG]][Main tests URL]
[![Graal tests][Graal tests SVG]][Graal tests URL]

See [here][GitHub releases] for earlier releases.

## Inline assertions

In my experience a large proportion of production Clojure errors are caused by a small set of recurring patterns like:

1. A **data type has changed**, breaking an obscure downstream consumer.
2. An **abstract data type is provided** (e.g. seq) where a concrete type (e.g. vector) is needed for specific behaviour (e.g. right-side peek).
3. An undocumented or easy-to-miss **state invariant is assumed** (e.g. if an item is in map `A`, its id will also be in set `B`) but broken during refactoring.

The resulting Clojure/Script error messages encountered in production are often unhelpful.

To help with these kinds of cases, Truss offers a set of small macros that provide super efficient and flexible **inline runtime assertions** with **terrific error messages** when something goes wrong: [`have`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have), [`have?`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have?), [`have!`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have!), [`have!?`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have!?).

`have` is the most common and is basically sugar for:

```clojure
(have string? my-arg) ; -> expands to:
(if  (string? my-arg)
  my-arg ; Returns given arg on success
  (throw-detailed-exception!))
```

Use `have` inline, or in `let` bindings. A few well-placed assertions can go a surprisingly long way to making unexpected errors in production easier to identify and understand.

When a Truss assertion fails, it'll throw a [`truss/ex-info`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#ex-info) with ex-data that includes a timestamp, the failed predicate, the tested argument, the source location, and the current Truss context ([`*ctx*`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#*ctx*)) in which you can store the relevant Ring request, etc.

I can't overstate how much difference having even this basic info can make.

Examples:

```clojure
(have string? "foo") ; => "foo"
(have string? 5)     ; => Throws detailed exception

;; Multiple args can be given:
(have string? "foo" "bar") ; => ["foo" "bar"]
(have string? "foo" 5)     ; => Throws detailed exception

;; Omit predicate to default to `some?` (non-nil):
(have "foo") ; => "foo"
(have false) ; => false
(have nil)   ; => Throws detailed exception

;; Add arb optional info to thrown ex-data using `:data`:
(have string? "foo" :data {:user-id 101}) => "foo"

;; Assert inside collections using `:in`:
(have string? :in #{"foo" "bar"}) ; => #{"foo" "bar"}

;; Several special predicates are supported:
(have [:or nil? string?] "foo")  ; => "foo"
(have [:ks<= #{:a :b}]  my-map)  ; => Map,  or throws
(have [:el #{:a :b :c}] my-arg)  ; => Arg,  or throws
(have [:n<= 10]         my-coll) ; => Coll, or throws

;; An example exception:
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
```

See [examples.cljc](../../blob/master/examples.cljc) or [YouTube demo](https://www.youtube.com/watch?v=gMB4Y-EIArA) for more.

## Contextual exceptions

Exceptions unexpectedly thrown in production can be fiendishly difficult to understand/debug. What argument triggered the exception? What was its type and value? What was the execution context when it was encountered?

Truss's [inline assertions](#inline-assertions) (above) provide one solution to the problem of vague exceptions.

Truss's **contextual exception** API provides another. Any time a [`truss/ex-info`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#ex-info) is thrown, it'll include the dynamic [`*ctx*`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#*ctx*) value.

You can use [`set-ctx!`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#set-ctx!), [`with-ctx`, `with-ctx+`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#with-ctx+) to easily establish relevant information about what your code is doing. Then if something unexpectedly throws, this context info will be included in relevant exceptions.

Example:

```clojure
(defn wrap-ring-ctx
  "Wraps given Ring handler so that the Ring req will be
  included in any thrown `truss/ex-info`s."
  [ring-handler-fn]
  (fn [ring-req]
    (truss/with-ctx+ {:ring-req ring-req} ; Merge into `truss/*ctx*`
      (ring-handler-fn ring-req))))
```

See also [`truss/ex-info!`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#ex-info!) to directly throw a [`truss/ex-info`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#ex-info).

> I'll be updating all my [open source libraries](https://www.taoensso.com/clojure) over time to use [`truss/ex-info`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#ex-info) everywhere exceptions are thrown.

## Cross-platform exceptions

Catching exceptions in cross-platform Clojure/Script code can be needlessly tedious. Truss provides a couple utils to make this easier:

- [`catching`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#catching) just swallow exceptions: `(catching (my-code))`.
- [`try*`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#try*) is like `core/try` but can catch special classes: `:ex-info`, `:common`, `:all`, `:default`. See docstring for details.

A cross-platform [`error?`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#error?) predicate is also provided.

## Testing exceptions

Writing unit tests that need to check for specific exception types, messages, and/or ex-data? Truss provides some relevant utils: [`throws?`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#throws?), [`throws`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#throws), [`matching-error`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#matching-error).

Example:

```clojure
(is (throws? :any "Divide by zero" (/ 1 0))) => true
(is (throws? :ex-info {:user-name :stu, :user-id pos-int?} ...))
```

  When an error with (nested) causes doesn't match, a match will be attempted
  against its (nested) causes.

## Misc utils

- Clojure's transducers are awesome, but can be an absolute pita to debug. See [`catching-xform`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#catching-xform) for a util this _far_ easier. [`catching-rf`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#catching-rf) is likewise available for regular reducing fns.
- [`unexpected-arg!`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#unexpected-arg!) provides an easy (if somewhat verbose) way to reject an argument with a clear error message. I'm increasingly using this in my own [open source libraries](https://www.taoensso.com/clojure) to make common user errors easier to debug.

## Documentation

- [Wiki][GitHub wiki] (getting started, usage, etc.)
- API reference via [cljdoc][cljdoc]
- Support via [Slack channel][] or [GitHub issues][]

## Funding

You can [help support][sponsor] continued work on this project, thank you!! 🙏

## License

Copyright &copy; 2014-2025 [Peter Taoussanis][].  
Licensed under [EPL 1.0](LICENSE.txt) (same as Clojure).

<!-- Common -->

[GitHub releases]: ../../releases
[GitHub issues]:   ../../issues
[GitHub wiki]:     ../../wiki
[Slack channel]: https://www.taoensso.com/truss/slack

[Peter Taoussanis]: https://www.taoensso.com
[sponsor]:          https://www.taoensso.com/sponsor

<!-- Project -->

[cljdoc]: https://cljdoc.org/d/com.taoensso/truss/

[Clojars SVG]: https://img.shields.io/clojars/v/com.taoensso/truss.svg
[Clojars URL]: https://clojars.org/com.taoensso/truss

[Main tests SVG]:  https://github.com/taoensso/truss/actions/workflows/main-tests.yml/badge.svg
[Main tests URL]:  https://github.com/taoensso/truss/actions/workflows/main-tests.yml
[Graal tests SVG]: https://github.com/taoensso/truss/actions/workflows/graal-tests.yml/badge.svg
[Graal tests URL]: https://github.com/taoensso/truss/actions/workflows/graal-tests.yml
