(ns spring-break.jmx
  (require [clojure.java.jmx :as jmx]))

;; A JMX client
(defn -main [mbean a-str & {:keys [port host meth]
                            :or {port 9999 host "127.0.0.1" meth :parseObject}}]
  (jmx/with-connection {:host host :port port}
    (.println System/out
              (format "+++ Calling %s on %s with '%s' returns '%s'"
                      meth
                      mbean
                      a-str
                      (jmx/invoke mbean meth a-str)))))

(defn fn-wrapper-of [a-fn]
  (proxy [java.text.Format][]
    (toString [] (format "(fn-wrapper-of %s meta=%s)" a-fn (meta a-fn)))
    (parseObject [a-str]
      (let [fmt "+++ Calling (%s [meta=%s] %s)"
            arg (read-string (format "[%s]" a-str))
            _ (.println System/out (format fmt 
                                           a-fn (meta a-fn) a-str))
            res (try
                  (apply a-fn arg)
                  (catch Exception x
                    ;; convert into a type/class that will be
                    ;; deserializable at remote JMX client sites
                    ;; (e.g. Clojure ArityExceptions wouldn't usually be)
                    ;; TODO: recure down the cause chain and convert those too
                    (.println System/out (format (str fmt " FAILS [%s]")
                                                 a-fn (meta a-fn) a-str x))
                    (throw (doto (RuntimeException. (str x))
                             (.setStackTrace (.getStackTrace x))))))
            res-str (pr-str res)]
        (.println System/out (format (str fmt " RETURNS [%s]")
                                     a-fn (meta a-fn) a-str res-str))
        res-str))))

(defn make-mbean-parameter-info []
  (javax.management.MBeanParameterInfo.
   "FORMS:"
   "java.lang.String"
   (str "Enter as many clojure forms as there are parameters"
        "to the clojure function being exposed via JMX.")))

(defn make-model-mbean-operation-info []
  (javax.management.modelmbean.ModelMBeanOperationInfo.
   "parseObject" ;; method name fits the type/class of fn-wrapper-of
   "some description"
   (into-array [(make-mbean-parameter-info)])
   "java.lang.Object" ;; return type 
   javax.management.MBeanOperationInfo/ACTION_INFO
   nil)) ;; descriptor

(defn make-model-mbean-info [bean-obj bean-name]
  (javax.management.modelmbean.ModelMBeanInfoSupport.
   "classname ignored!"
   "description ignored"
   (into-array javax.management.modelmbean.ModelMBeanAttributeInfo [])
   (into-array javax.management.modelmbean.ModelMBeanConstructorInfo [])
   (into-array [(make-model-mbean-operation-info)])
   (into-array javax.management.modelmbean.ModelMBeanNotificationInfo [])))

(defn mbean-info-assembler [pred]
  (proxy [org.springframework.jmx.export.assembler.AutodetectCapableMBeanInfoAssembler][]
    (includeBean [bean-class bean-name]
      ;; bean-class will be java.lang.Object for factory-generated beans
      (let [incl? (pred bean-name)]
        (.println System/out (format "+++ includeBean class=[%s] id=[%s] RETURNS %s" bean-class bean-name incl?))
        incl?))
    (getMBeanInfo [bean-obj bean-name]
      (.println System/out (format "+++ assembling bean=[%s] id=[%s]" bean-obj bean-name))
      (make-model-mbean-info bean-obj bean-name))))
