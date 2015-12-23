


(defn square [x]
  (let [x (have integer? x)]
    (* x x)))

(square 5)   ; => 25
(square nil) ; =>
;; Unhandled clojure.lang.ExceptionInfo
;; Invariant violation in `my-app:18` [pred-form, val]: [(integer? x), <nil>]
;;   {:?form nil,
;;    :instant 1450868195464,
;;    :ns "taoensso.encore",
;;    :elidable? true,
;;    :val nil,
;;    :val-type nil,
;;    :?err nil,
;;    :*assert* true,
;;    :?data nil,
;;    :?line 18,
;;    :form-str "(integer? x)"}



(ns my-clj-ns ; A Clojure namespace
  (:require
   [taoensso.truss :as truss :refer (have have? have!)]))

(ns my-cljs ; A ClojureScript namespace
  (:require-macros
   [taoensso.truss :as truss :refer (have have? have!)]))



(defn get-restricted-resource [ring-session]

  ;; This is an important security check so we'll use `have!` here instead of
  ;; `have` to make sure the check is never elided (skipped):
  (have! string? (:auth-token ring-session))

  (return-the-resource))


:global-vars {;; *warn-on-reflection* true
              *assert*                true
              ;; *unchecked-math*     :warn-on-boxed
              }
