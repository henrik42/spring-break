;; This file should be loaded as a *script file*.
;; It has no namespace declaration and it may
;; not be loaded via (require).
(printf "+++ loading %s in namespace '%s'\n" *file* *ns*)
(in-ns 'foo)
(clojure.core/use 'clojure.core)
(defn foobar [& args]
  (printf "+++ Calling (%s/foobar %s)\n" *ns* args)
  (vec args))
