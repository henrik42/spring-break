(printf "+++ loading %s in namespace '%s'\n" *file* *ns*)
(ns foo)
(defn foobar [& args]
  (printf "+++ Calling (%s %s)\n" foobar args)
  (vec args))
