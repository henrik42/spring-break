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
;; Methods/functions called by JMX may throw exceptions which
;; run up the call stack that is controlled by JMX.
;; So there is not exception handler controlled by our code.
;; The macro with-exception-mapping
;; (a) prints exception stack traces to STDERR
;; (b) Maps exception chains to RuntimeException so that
;;     it is deserializable on remote clients that do not have
;;     access to Clojure classes.
;; -------------------------------------------------------------------
(defn copy-exception-chain [x]
  (let [cause (.getCause x)
        cause-copy (if (or (= nil cause) (identical? cause x)) nil
                       (copy-exception-chain cause))
        copy (RuntimeException. (str x) cause-copy)]
    copy))

;; fmt and args will become part of the mapped
;; exception. Use them to add context to the
;; exception message
(defmacro with-exception-mapping [fmt args & body]
  `(try
    ~@body
    (catch Throwable t#
      (let [msg# (apply format (str ~fmt " FAILED: %s") ~@args [t#])
            x# (RuntimeException. msg# t#)]
        (.printStackTrace x# System/err)
        (throw (copy-exception-chain x#))))))

;; -------------------------------------------------------------------
;; JMX Attribute
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

(defn name-of [state]
  (.println System/out (format "state %s meta %s" state (meta state)))
  (:name (meta state)))

(defn description-of [state]
  (:description (meta state)))

;; called from Spring not JMX
(defn make-mbean-attribute-info [state]
  (javax.management.MBeanAttributeInfo.
   (name-of state)
   (description-of state)
   fake-getter
   fake-setter))

;; called from Spring not JMX
(defn make-mbean-info [mbean ^String mbean-description states]
  (javax.management.MBeanInfo.
   (str (class mbean)) ;; doesn't matter
   mbean-description
   (into-array
    (map #(make-mbean-attribute-info %) states))
   nil ;; no constructors
   nil ;; no operations
   nil)) ;; no notifications

(defn set-attribute [mbean attr attrs]
  (let [a-str (.getValue attr)
        state (or (attrs (.getName attr))
                  (let [msg (format "+++ State %s not found. Known states are %s." attr attrs)]
                    (.println System/out msg)
                    (throw (RuntimeException. msg))))
        fmt "+++ Setting JMX attribute '%s' (%s meta=%s) \nto [%s] (%s)"
        arg (try
              (read-string a-str)
              (catch Exception x
                (let [msg (format (str fmt "\nread-string FAILS [%s]")
                                  (.getName attr)
                                  state (meta state)
                                  a-str (class a-str)
                                  x)]
                  (.println System/out msg)
                  (throw (doto (RuntimeException. msg)
                           (.setStackTrace (.getStackTrace x)))))))]
    (.println System/out (format fmt 
                                 (.getName attr)
                                 state (meta state)
                                 arg (class arg)))
    (try
      ;; may fail due to validation!
      (set-value state arg)
      (catch Exception x
        (let [msg (format (str fmt "\nFAILS [%s]")
                          (.getName attr)
                          state (meta state)
                          arg (class arg)
                          x)]
          (.println System/out msg)
          (throw (doto (RuntimeException. msg)
                   (.setStackTrace (.getStackTrace x)))))))))

(defn get-attribute [attr-name attrs]
  (try
    (do 
      (.println System/out
                (format "+++ (getAttribute %s)" attr-name))
      (let [state (or @(attrs attr-name)
                      (let [msg (format "%s %s" attr-name attrs)]
                        (throw
                         (RuntimeException. msg))))
            res (pr-str state)]
        (.println System/out
                  (format "+++ (getAttribute %s) -> %s"
                          attr-name
                          res))
        res))
    (catch Exception x
      (do
        (.println System/out (str "ops" x))
        (throw x)))))

(defn get-attributes [attr-names attrs]
  (.println System/out (format "getAttributes %s" (vec attr-names)))
  (let [result (javax.management.AttributeList.)]
    (dorun
     (for [attr-name attr-names
           :let [state (attrs attr-name)
                 v (pr-str @state)]]
       (do 
         (.println System/out (format "state = %s value = %s" state v))
         (.add result ^Object (pr-str @state)))))
    result))

;; Creates a DynamicMBean
;; Exceptions are logged and mapped
(defn make-mbean [mbean-description & states]
  (let [attrs (zipmap (map name-of states) states)]
    (.println System/out (format "attrs = %s" attrs))
    (reify javax.management.DynamicMBean
      (getMBeanInfo [this]
        (make-mbean-info this (str mbean-description) states))

      (setAttribute [this attr]
        (with-exception-mapping "(setAttribute %s)" [attr]
          (set-attribute this attr attrs)))
      
      (getAttribute [this attr-name]
        (with-exception-mapping "(getAttribute %s)" [attr-name]
          (get-attribute attr-name attrs)))
      
      (getAttributes [this attr-names]
        (with-exception-mapping "(getAttributes %s)" [attr-names]
          (get-attributes attr-names attrs))))))

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
      (.println System/out (format "+++ assembling JMX operation bean=[%s] id=[%s]" bean-obj bean-name))
      (make-model-mbean-info bean-obj bean-name))))
