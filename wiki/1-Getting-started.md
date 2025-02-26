# Setup

Add the [relevant dependency](../#latest-releases) to your project:

```clojure
Leiningen: [com.taoensso/truss               "x-y-z"] ; or
deps.edn:   com.taoensso/truss {:mvn/version "x-y-z"}
```

And setup your namespace imports:

```clojure
(ns my-ns (:require [taoensso.truss :as truss :refer [have have?]]))
```