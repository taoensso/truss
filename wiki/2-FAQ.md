# How to report/log violations?

By default, Truss just throws an **exception** on any invariant violations.

You can adjust that behaviour with the [`set-error-fn!`](https://taoensso.github.io/truss/taoensso.truss.html#var-set-error-fn.21) and [`with-error-fn`](https://taoensso.github.io/truss/taoensso.truss.html#var-with-error-fn) utils.

Some common usage ideas:

- Use `with-error-fn` to capture violations during unit testing
- Use `set-error-fn!` to _log_ violations with something like [Timbre](https://www.taoensso.com/timbre)

# Should I annotate my whole API?

**Please don't**! I'd encourage you to think of Truss assertions like **salt in good cooking**; a little can go a long way, and the need for too much salt can be a sign that something's gone wrong in the cooking.

Another useful analogy would be the Clojure STM. Good Clojure code tends to use the STM very rarely. When you want the STM, you _really_ want it - but many new Clojure developers end up surprised at just how rarely they end up wanting it in an idiomatic Clojure codebase.

Do the interns keep getting that argument wrong despite attempts at making the code as clear as possible? By all means, add an assertion.

More than anything, I tend to use Truss assertions as a form of documentation in long/hairy or critical bits of code to remind myself of any unusual input/output contracts/expectations. E.g. for performance reasons, we _need_ this to be a vector; throw if a list comes in since it means that some consumer has a bug.

# What's the performance cost?

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

In rare cases where the cost does matter (e.g. for an unusually expensive predicate), Truss supports complete elision in production code.

# How to elide Truss checks?

Disable `clojure.core/*assert*` before macro expansion, and Truss forms will noop. They'll pass their arguments through with **zero performance overhead**.

If you use Leiningen, an easy way to do this is to add the following to your `project.clj`:

```clojure
:global-vars {*assert* false}
```

# How to prevent elision?

An extra macro is provided (`have!`) which ignores `*assert*` and so can never be elided. This is handy for implementing (and documenting) critical checks like security assertions that you never want disabled.

```clojure
(defn get-restricted-resource [ring-session]
  ;; This is an important security check so we'll use `have!` here instead of
  ;; `have` to make sure the check is never elided (skipped):
  (have! string? (:auth-token ring-session))

  "return-restricted-resource-content")
```

# How does Truss compare to alternatives?

There are several good choices when it comes to providing type and/or structural information to Clojure/Script code, including. [clojure.spec](https://clojure.org/about/spec), [core.typed](https://github.com/clojure/core.typed), [@plumatic/schema](https://github.com/plumatic/schema), [@marick/structural-typing](https://github.com/marick/structural-typing), etc.

How these compare is a tough question to answer briefly since these projects may have different objectives, and sometimes offer very different trade-offs.

Some of the variables to consider might include:

- **Cost of getting started** - e.g. is it cheap/easy to cover an initial/small subset of code?
- **Ease of learning** - e.g. how complex is the syntax/API for newcomers?
- **Flexibility at scale** - e.g. likelihood of encountering frustrating limitations?
- **Performance** - e.g. impact on testing/development/production runtimes?

To make a useful comparison, ultimately one might want some kind of `relevant-power ÷ relevant-cost`, relative to some specific context and objectives.

For my part, I'm really pleased with the balance of particular trade-offs that Truss offers.

As of 2023, Truss continues to be my preferred/default choice for a wide variety of common cases in projects large and small.

The best general recommendation I can make is to try actually experiment with the options that seem appealing to you. Nothing beats hands-on experience for deciding what best fits your particular needs and tastes.

See [here](../wiki#motivation) for some of the specific objectives I had with Truss.