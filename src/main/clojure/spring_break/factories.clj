(ns spring-break.factories)

(defn compiler-load [s]
  (clojure.lang.Compiler/load
   (java.io.StringReader. s)))

(defprotocol object-factory
  (new-instance [this s]))

(def clojure-object-factory
  (reify object-factory
    (new-instance [this s]
      (compiler-load s))))

