(ns spring-break.factories)

(defn compiler-load [s]
  (clojure.lang.Compiler/load
   (java.io.StringReader. s)))

(defprotocol object_factory
  (new_instance [this s]))

(def clojure-object-factory
  (reify object_factory
    (new_instance [this s]
      (compiler-load s))))

;;(deftype clojure-object-factory []
;;  object-factory
;;  (new-instance [s]
;;    (compiler-load s)))
  