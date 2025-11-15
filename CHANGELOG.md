This project uses [**Break Versioning**](https://www.taoensso.com/break-versioning).

---

# `v2.2.1` (2025-11-15)

- **Dependency**: [on Clojars](https://clojars.org/com.taoensso/truss/versions/2.2.1)
- **Versioning**: [Break Versioning](https://www.taoensso.com/break-versioning)

This is a **hotfix release** to address an [issue](https://github.com/taoensso/truss/issues/19) with the `with-ctx+` util. It should be a safe upgrade for users of `v2.2.0`.

---

# `v2.2.0` (2025-08-21)

- **Dependency**: [on Clojars](https://clojars.org/com.taoensso/truss/versions/2.2.0)
- **Versioning**: [Break Versioning](https://www.taoensso.com/break-versioning)

This is a **feature release** focused on quality-of-life improvements. It should be a non-breaking upgrade for the vast majority of typical users, but please note the changes below to be sure.

## Since v2.1.0 (2025-04-29)

- ➤ **\[mod]** Make macros: `ex-info`, `ex-info!`, `unexpected-arg!` \[14e3e86]
- \[new] `with-ctx/+`: allow multi-form bodies \[d9228d8]
- \[doc] Misc doc improvements, added [intro video](https://youtu.be/vGewwWuzk9o)

---

# `v2.1.0` (2025-04-29)

- **Dependency**: [on Clojars](https://clojars.org/com.taoensso/truss/versions/2.1.0)
- **Versioning**: [Break Versioning](https://www.taoensso.com/break-versioning)

This is a **major rewrite of Truss** that expands the library's scope, and modernises its implementation. There are **breaking changes** to the ex-data produced by the 4x assertion macros ([`have`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have), [`have?`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have?), [`have!`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have!), [`have!?`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#have!?)) in Truss and [Encore](https://www.taoensso.com/encore).

This should be a non-breaking update for folks not using assertion ex-data, but please [ping me](http://taoensso.com/truss/slack) if you run into any unexpected trouble. Apologies for the inconvenience! - [Peter Taoussanis](https://www.taoensso.com) 🙏

## CHANGES since v1.x

### Deprecated assertion API

- [`set-error-fn!`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#set-error-fn!) and [`with-error-fn`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#with-error-fn) have been deprecated. Please use [`*failed-assertion-handler*`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#*failed-assertion-handler*) instead (see linked docstring for details).
- [`get-data`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#get-data) and [`with-data`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#with-data) have been deprecated. Please use [`*ctx*`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#*ctx*) instead.

### Changes to assertion ex-data

By default, Truss throws an `ex-info` exception on assertion failures. The included ex-data has changed!

Old ex-data (Truss v1):
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

New ex-data (Truss v2):
```
:pred ------ (Unchanged) Predicate form
:arg ------- (Unchanged) {:keys [form value type]}

:inst ------ `js/Error` or `java.time.Instant` (note type change!)
:ns -------- Namespace string
:coords ---- ?[line column]
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

- \[doc] Document arg given to `*failed-assertion-handler*` \[7de9a82] (v2.1.0)
- \[fix] `set-error-fn!` should convert from v2 to v1 handler arg \[d6bc7c5] (v2.1.0)
- \[fix] Fix broken Clj `set-error-fn!` \[e99fe6b] (v2.0.6)

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
