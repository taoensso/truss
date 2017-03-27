> This project uses [Break Versioning](https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md)

## v1.4.0 - 2017 Mar 27

```clojure
[com.taoensso/truss "1.4.0"]
```

* [#8] Show full record values in error messages (@martinklepsch)

## v1.3.7 - 2017 Feb 13

```clojure
[com.taoensso/truss "1.3.7"]
```

* Improved some docstrings

## v1.3.6 - 2016 Sep 7

```clojure
[com.taoensso/truss "1.3.6"]
```

* Tweaked invariant-violation error message to make it clearer for beginners

## v1.3.5 - 2016 Aug 12

```clojure
[com.taoensso/truss "1.3.5"]
```

* **Hotfix**: cljs error wrapping was broken

## v1.3.3 - 2016 Jun 14

```clojure
[com.taoensso/truss "1.3.3"]
```

**BREAKING**: `error-fn` args have changed: `[msg data-map]` -> `[data-map-delay]`

## v1.2.0 - 2016 Mar 21

```clojure
[com.taoensso/truss "1.2.0"]
```

* **New**: added `set-error-fn!` [#3] to allow easier control over how invariant violations are reported

## v1.1.2 - 2016 Feb 26

* **Hotfix**: false vals were printing as "<nil>" in error messages

## v1.1.1 - 2016 Feb 18

* **Hotfix**: remove accidental encore reference [#1]

```clojure
[com.taoensso/truss "1.1.1"]
```

## v1.1.0 - 2016 Feb 17

> This is a non-breaking performance release

* **Perf**: special predicates like `[:or nil? string?]` are now compile-time transformations
* **Perf**: skip throw catching for some common predicates that are known never to throw

```clojure
[com.taoensso/truss "1.1.0"]
```

## v1.0.0 - 2015 Jan 12

> Initial public release

```clojure
[com.taoensso/truss "1.0.0"]
```
