This project uses [**Break Versioning**](https://www.taoensso.com/break-versioning).

---

# `v2.0.6` (2025-04-29)

- **Dependency**: [on Clojars](https://clojars.org/com.taoensso/truss/versions/2.0.6)
- **Versioning**: [Break Versioning](https://www.taoensso.com/break-versioning)

This is a **major rewrite of Truss** that expands the library's scope, and modernises its implementation. There are **breaking changes** to the ex-data produced by the 4x assertion macros ([`have`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have), [`have?`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have?), [`have!`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have!), [`have!?`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have!?)) in Truss and [Encore](https://www.taoensso.com/encore).

This is a non-breaking update for folks not using assertion ex-data.

Apologies for any inconvenience! - [Peter Taoussanis](https://www.taoensso.com) 🙏

## CHANGES since v1.x

### Deprecated assertion API

- [`set-error-fn!`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#set-error-fn!) and [`with-error-fn`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#with-error-fn) have been deprecated. Please use [`*failed-assertion-handler*`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#*failed-assertion-handler*) instead.
- [`get-data`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#get-data) and [`with-data`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#with-data) have been deprecated. Please use [`*ctx*`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#*ctx*) instead.

### Changes to assertion ex-data

The ex-data included in assertion failures has changed!

Old ex-data:
```
:pred ------ (Unchanged) Predicate form
:arg ------- (Unchanged) {:keys [form value type]}

:dt -------- `js/Error` or `java.util.Date`
:loc ------- {:keys [ns line column]}
:data ------ {:keys [arg dynamic]}

:env ------- {:keys [elidable? *assert*]}
:msg ------- String
:err ------- Error thrown during pred check
```

New ex-data:
```
:pred ------ (Unchanged) Predicate form
:arg ------- (Unchanged) {:keys [form value type]}

:inst ------ `js/Error` or `java.time.Instant`
:ns -------- Namespace string
:coords ---- [line column]
:data ------ Optional `:data` value (replaces :data/arg)
:truss/ctx - `truss/*ctx*` value (replaces :data/dynamic)

:msg ------- REMOVED (use `ex-message` instead)
:error ----- REMOVED (use `ex-cause`   instead)
```

- You can customise ex-data by modifying [`*failed-assertion-handler*`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#*failed-assertion-handler*).
- You can keep the old ex-data by setting the `taoensso.truss.legacy-assertion-ex-data` JVM property to `true`.

## New since v1.x

- Significant performance and expansion size improvements to the assertions API ([`have`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have), [`have?`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have?), [`have!`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have!), [`have!?`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have!?)). Esp. useful for ClojureScript codebases that use many assertions.
  
- Added new contextual exceptions API: [`ex-info`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#ex-info), [`ex-info!`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#ex-info!), [`*ctx*`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#*ctx*), [`set-ctx!`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#set-ctx!), [`with-ctx`, `with-ctx+`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#with-ctx+).
  
- Added new Error utils imported (moved) from Encore: [`error?`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#error?), [`try*`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#try*), [`catching`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#catching), [`matching-error`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#matching-error), [`throws`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#throws), [`throws?`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#throws?), [`catching-rf`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#catching-rf), [`catching-xform`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#catching-xform), [`unexpected-arg!`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#unexpected-arg!).

## Since v2.0.0

- Fix broken Clj `set-error-fn!` (v2.0.6)

---

# `v1.12.0` (2024-09-07)

📦 [Available on Clojars](https://clojars.org/com.taoensso/truss/versions/1.12.0), uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is a non-breaking **minor maintenance release** that **improves some docstrings** and updates some internal code. Thank you!

\- [Peter Taoussanis](https://www.taoensso.com)


---

# `v1.11.0` (2023-07-31)

> 📦 [Available on Clojars](https://clojars.org/com.taoensso/truss/versions/1.11.0)

This is a **maintenance + feature release**, and should be a non-breaking upgrade.

## New since `v1.10.1` (2023-07-15)

* f42b81b [mod] Improve invariant violation output
* 02c027e [new] Add `cljdoc.edn` config (improve cljdoc output)

## Other improvements since `v1.10.1` (2023-07-15)

* 9ff9d55 [nop] More reliable predicate parsing


# `v1.10.1` (2023-07-15)

> 📦 [Available on Clojars](https://clojars.org/com.taoensso/truss/versions/1.10.1)

This is a **hotfix release**, please upgrade if you're using `v1.10.0`.

## Fixes since `v1.10.0` (2023-07-07)

* 46b2f69 [fix] Prevent `get-source` from throwing for JAR resources

---

# `v1.10.0` (2023-07-07)

> 📦 [Available on Clojars](https://clojars.org/com.taoensso/truss/versions/1.10.0)

This is a minor **feature release**, and should be a non-breaking upgrade.

## New since `v1.9.0` (2023-03-15)

* 9855aa9 [new] Add `:column` and `:file` to `:loc` data for invariant violations
* 042eb78 [nop] Add tests for GraalVM compatibility

---

# `v1.9.0` (2023-03-15)

```clojure
[com.taoensso/truss "1.9.0"]
```

> This is a **feature release**. Should be non-breaking.
> See [here](https://github.com/taoensso/encore#recommended-steps-after-any-significant-dependency-update) for a tip re: general recommended steps when updating any Clojure/Script dependencies.

## Since `v1.8.0` (2022-12-13)

- 4bbab6b [new] Add unevaluated arg `:form` info to invariant violations
- 9a572b1 [new] Add `:instance?`, `:satisfies?` special predicate forms
- e644631 [new] Experimental alternative workaround for CLJ-865

---

# Earlier releases

See [here](https://github.com/taoensso/TODO/releases) for earlier releases.
