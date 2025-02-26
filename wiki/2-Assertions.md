# Basics

The main way to use Truss's assertions API is with the [`have`](https://taoensso.github.io/truss/taoensso.truss.html#var-have) macro. You give it a predicate, and an argument that you believe should satisfy the predicate.

Example:

```clojure
(defn greet
  "Given a string username, prints a greeting message."
  [username]
  (println "hello" (have string? username)))
```

In this case the predicate is `string?` and argument is `username`:

- If `(string? username)` is truthy: the assertion **succeeds** and `(have ...)` returns the given username.
- If `(string? username)` is falsey: the assertion **fails** and a detailed **error is thrown** to help you debug.

That's the basic idea.

These `(have <pred> <arg>)` annotations are standard Clojure forms that both **documents the intention of the code** in a way that **cannot go stale**, and provides a **runtime check** that throws a detailed error message on any unexpected violation.

## When to use Truss assertions

You use Truss assertions to **formalize assumptions** that you have about your data (e.g. **function arguments**, **intermediate values**, or **current application state** at some point in your execution flow).

So any time you find yourself making **implementation choices based on implicit information** (e.g. the state your application should be in if this code is running) - that's when you might want to reach for Truss instead of a comment or Clojure assertion.

Truss assertions are like **salt in good cooking**; a little can go a long way.

## `have` variants

While most users will only need to use the base `have` macro, a few variations are provided for convenience:

| Macro                                                                         | On success          | Subject to elision by `*assert*`? |
| :---------------------------------------------------------------------------- | :------------------ | :-------------------------------- |
| [have](https://taoensso.github.io/truss/taoensso.truss.html#var-have)         | Returns given arg/s | Yes                               |
| [have!](https://taoensso.github.io/truss/taoensso.truss.html#var-have.21)     | Returns given arg/s | No                                |
| [have?](https://taoensso.github.io/truss/taoensso.truss.html#var-have.3F)     | Returns true        | Yes                               |
| [have!?](https://taoensso.github.io/truss/taoensso.truss.html#var-have.21.3F) | Returns true        | No                                |

In all cases:

- The basic syntax is identical.
- The behaviour on failure is identical (throws a detailed exception).

What varies is the return value, and whether elision is possible.

# Examples

See [`examples.cljc`](../blob/master/examples.cljc).

# Motivation

<p><a href="https://www.youtube.com/watch?v=gMB4Y-EIArA" target="_blank">
 <img src="https://img.youtube.com/vi/gMB4Y-EIArA/maxresdefault.jpg" alt="Truss demo video" width="480" border="0" /></a></p>

Clojure is a beautiful language full of smart trade-offs that tends to produce production code that's short, simple, and easy to understand.

But every language necessarily has trade-offs. In the case of Clojure, being a **dynamically typed** and **hosted** language leads to one of the more common challenges that I've observed in the wild: **debugging or refactoring large codebases**.

Specifically:

 * **Undocumented type assumptions** changing (used to be this thing was never nil; now it can be).
 * Documented **type assumptions going stale** (forgot to update comments).
 * **Unhelpful error messages** when an assumption is inevitably violated (it crashed in production? why?).

Thankfully, this list is almost exhaustive; in my experience these few causes often account for **80%+ of real-world incidental difficulty**.

So **Truss** assertions target these issues with a **practical 80% solution** that emphasizes:

 1. **Ease of adoption** (incl. partial/precision/gradual adoption).
 2. **Ease of use** (non-invasive API, trivial composition, etc.).
 3. **Flexibility** (scales well to large, complex systems).
 4. **Speed** (blazing fast => can use in production, in speed-critical code).
 5. **Simplicity** (lean API, zero dependencies, tiny codebase).

The first is particularly important since the need for assertions in a good Clojure codebase is surprisingly _rare_.

Every codebase has trivial parts and complex parts. Parts that suffer a lot of churn, and parts that haven't changed in years. Mission-critical parts (bank transaction backend), and those that aren't so mission-critical (prototype UI for the marketing department).

Having the freedom to reinforce code only **where and when you judge it worthwhile**:

 1. Let's you (/ your developers) easily evaluate the lib.
 2. Makes it more likely that you (/ your developers) will actually _use_ the lib.
 3. Eliminates upfront buy-in costs.
 4. Allows you to retain control over long-term cost/benefit trade-offs.

# Dealing with failed assertions

By default, Truss just throws an **exception** on any failed assertions. You can adjust that behaviour with the [`*failed-assertion-handler*`](https://cljdoc.org/d/com.taoensso/truss/CURRENT/api/taoensso.truss#*failed-assertion-handler*) dynamic var, e.g. to:

- Use a dynamic binding to capture failures during unit testing.
- Set the root value to emit failures as [Telemere](https://www.taoensso.com/telemere) signals before throwing.

# What to annotate

**Please don't** annotate your whole codebase! I'd encourage you to think of Truss assertions like **salt in good cooking**; a little can go a long way, and the need for too much salt can be a sign that something's gone wrong in the cooking.

More than anything, I tend to use Truss assertions as a form of documentation in long/hairy or critical bits of code to remind myself of any unusual input/output contracts/expectations. E.g. for performance reasons, we _need_ this to be a vector; throw if a list comes in since it means that some consumer has a bug.

# Performance

Truss assertions have been **highly tuned** to minimize both runtime costs and code expansion size (important for Cljs codebases).

In many common cases, a Truss assertion expands to no more than `(if (pred arg) arg (throw-detailed-exception!))`.

For simple predicates (including `instance?` checks), modern JITs work great; the runtime performance impact is almost always completely insignificant even in tight loops.

In rare cases where the cost does matter (e.g. for an unusually expensive predicate), Truss assertions may be elided in production by disabling `core/*assert*`.

# Eliding assertions

Disable `core/*assert*` before macro expansion, and Truss assertions will be elided, passing their given arguments through with no predicate checks and **no performance overhead**.

If you use Leiningen, an easy way to do this is to add the following to your `project.clj`:

```clojure
:global-vars {*assert* false}
```

# Preventing elision

An extra macro is provided (`have!`) which ignores `*assert*` and so can never be elided. This is handy for implementing (and documenting) critical checks like security assertions that should never be disabled:

```clojure
(defn get-restricted-resource [ring-session]
  (let [auth (have! string? (:auth-token ring-session))] ; Never elide!
    ;; Do stuff...
    "Return restricted resource content"))
```

# Alternative tools

There are several good choices when it comes to providing type and/or structural information to Clojure/Script code, including. [clojure.spec](https://clojure.org/about/spec), [core.typed](https://github.com/clojure/core.typed), [@plumatic/schema](https://github.com/plumatic/schema), [@marick/structural-typing](https://github.com/marick/structural-typing), etc.

How these compare is a tough question to answer briefly since these projects may have different objectives, and sometimes offer very different trade-offs.

Some of the variables to consider might include:

- **Cost of getting started** - e.g. is it cheap/easy to cover an initial/small subset of code?
- **Ease of learning** - e.g. how complex is the syntax/API for newcomers?
- **Flexibility at scale** - e.g. likelihood of encountering frustrating limitations?
- **Performance** - e.g. impact on testing/development/production runtimes?

To make a useful comparison, ultimately one might want some kind of `relevant-power ÷ relevant-cost`, relative to some specific context and objectives.

For my part, I'm really pleased with the balance of particular trade-offs that Truss assertions offers. As of 2025, Truss assertions continue to be my preferred/default choice for a wide variety of common cases in projects large and small.

The best general recommendation I can make is to try actually experiment with the options that seem appealing to you. Nothing beats hands-on experience for deciding what best fits your particular needs and tastes.

See [here](../wiki#motivation) for some of the specific objectives I had for Truss assertions.