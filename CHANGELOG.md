This project uses [**Break Versioning**](https://www.taoensso.com/break-versioning).

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