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

;; -------------------------------------------------------------------
;; JMX Attribute stuff
;; -------------------------------------------------------------------

;; changing the value of a reference type
(defmulti set-value (fn [r v] (class r)))
(defmethod set-value clojure.lang.Atom [an-atom v]
  (reset! an-atom v))
(defmethod set-value clojure.lang.Ref [a-ref v]
  (dosync 
   (ref-set a-ref v)))

;; fake getter for JMX - not called but reflected on by JMX
;; Must return a method "String <meth>()".
(def fake-getter
  (-> Class
      (.getMethod "getCanonicalName"
                  (into-array Class []))))

;; fake setter for JMX - not called but reflected on by JMX
;; Must return a method "<type> <meth>(String)".
(def fake-setter
  (-> Class
      (.getMethod "getField"
                  (into-array [String]))))

(defn ref-wrapper-of [a-ref]
  (reify javax.management.DynamicMBean
    (getMBeanInfo [this]
      (javax.management.MBeanInfo.
       "class name ignored"
       "Some description"
       (into-array
        [(javax.management.MBeanAttributeInfo.
          "attribute name"
          "attribute desc"
          fake-getter
          fake-setter)])
       nil ;; no constructors
       nil ;; no operations
       nil)) ;; no notifications
    
    (setAttribute [this attr]
      (let [n (.getName attr)
            v (.getValue attr)]
        (.print System/out (format "setting %s to %s (%s)" a-ref v (.getClass v)))
        (set-value a-ref v)))
    
    (getAttribute [_ attr]
      (.println System/out "getAttribute called!")
      @a-ref)
    
    (getAttributes [_ attrs]
      (.println System/out "getAttributeS called!")
      (let [result (javax.management.AttributeList.)]
        (.add result ^Object @a-ref)
        result))))

;; -------------------------------------------------------------------
;; JMX Operation
;; -------------------------------------------------------------------

;; Proxy/wrapper around a Clojure function - this will be passed
;; to (make-model-mbean-info)
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

;; for JMX operations/Clojure functions
(defn make-mbean-parameter-info []
  (javax.management.MBeanParameterInfo.
   "FORMS:"
   "java.lang.String"
   (str "Enter as many clojure forms as there are parameters"
        "to the clojure function being exposed via JMX.")))

;; for JMX operations/Clojure functions
(defn make-model-mbean-operation-info []
  (javax.management.modelmbean.ModelMBeanOperationInfo.
   "parseObject" ;; method name fits the type/class of fn-wrapper-of
   "some description"
   (into-array [(make-mbean-parameter-info)])
   "java.lang.Object" ;; return type 
   javax.management.MBeanOperationInfo/ACTION_INFO
   nil)) ;; descriptor

;; will only be used for JMX operations/Clojure functions - not JMX attributes
(defn make-model-mbean-info [bean-obj bean-name]
  (.println System/out
            (format
             "+++ making model-mbean-info for bean-obj = %s"
             bean-obj))
  (javax.management.modelmbean.ModelMBeanInfoSupport.
   "classname ignored!"
   "description ignored"
   (into-array javax.management.modelmbean.ModelMBeanAttributeInfo [])
   (into-array javax.management.modelmbean.ModelMBeanConstructorInfo [])
   (into-array [(make-model-mbean-operation-info)])
   (into-array javax.management.modelmbean.ModelMBeanNotificationInfo [])))

;; will be used for bean selection and registration of JMX operations/Clojure functions
(defn mbean-info-assembler [pred]
  (proxy [org.springframework.jmx.export.assembler.AutodetectCapableMBeanInfoAssembler][]
    (includeBean [bean-class bean-name]
      ;; bean-class will be java.lang.Object for factory-generated beans
      (let [incl? (pred bean-name)]
        (.println System/out (format "+++ includeBean class=[%s] id=[%s] RETURNS %s" bean-class bean-name incl?))
        incl?))
    ;; will only be used for JMX operations/Clojure functions - not JMX attributes
    (getMBeanInfo [bean-obj bean-name] ;; returns javax.management.modelmbean.ModelMBeanInfo
      (.println System/out (format "+++ assembling bean=[%s] id=[%s]" bean-obj bean-name))
      (make-model-mbean-info bean-obj bean-name))))
