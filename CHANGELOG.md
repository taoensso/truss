> This project uses [Break Versioning](https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md)

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
