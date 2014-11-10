(ns spring-break.jmx
  (require [clojure.java.jmx :as jmx]
           [spring-break.core :as core]))

;; A JMX client
;; For experimenting - not for being called as an API
;; strs-keys because we'll be calling via lein and
;; so args are all strings.
(defn jmx-invoke [mbean arg & {:strs [port host meth]
                               :or {port 9999
                                    host "127.0.0.1"
                                    meth :parseObject}}]
  (jmx/with-connection {:host host :port port}
    (core/log "Calling %s on %s with '%s' returns '%s'"
              meth
              mbean
              arg
              (jmx/invoke mbean meth (str arg)))))

(defn jmx-read [attr-name & {:strs [port host mbean-name]
                             :or {port 9999
                                  host "127.0.0.1"
                                  mbean-name "clojure-beans:name=clj_states"}}]
  (jmx/with-connection {:host host :port port}
    (core/log "Getting attribute value of %s returns '%s'"
              attr-name
              (jmx/read mbean-name attr-name))))

(defn jmx-write [attr-name attr-value
                 & {:strs [port host mbean-name]
                    :or {port 9999
                         host "127.0.0.1"
                         mbean-name "clojure-beans:name=clj_states"}}]
  (jmx/with-connection {:host host :port port}
    (core/log "Setting attribute value of %s to '%s'"
              attr-name attr-value)
    (jmx/write! mbean-name attr-name (str attr-value))))

;; So that we can call lein -m <namespace> (invoke|read|write) [...]
(defn -main [f & args]
  (cond
    (= "invoke" f) (apply jmx-invoke args)
    (= "read" f) (apply jmx-read args)
    (= "write" f) (apply jmx-write args)
    :else (throw (IllegalArgumentException.
                  (format "Neither :read/:invoke : %s" f)))))

;; Just a helper that delivers a var with meta
;; How do you do this with out a namespace entry?
(def ^{:attr-name "a_var" :attr-description "a var description"} a-var)
(defn get-a-var []
  #'a-var)

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
(defn with-exception-mapping* [fmt args t]
  (let [msg (apply format
                   (str fmt " failed due to: %s")
                   (concat (map pr-str args) [t]))
        x (RuntimeException. msg t)]
    (.printStackTrace x System/err)
    (throw (copy-exception-chain x))))

(defmacro with-exception-mapping [fmt args & body]
  `(try
    ~@body
    (catch Throwable t#
      (with-exception-mapping* ~fmt ~args t#))))

;; parses the input and returns the value that
;; will be further processed. This value will be
;; passed to functions (JMX operation) and
;; or be the value for state changes (JMX attributes)
;; Not-using eval allows you to process plain forms without
;; evaluation. If you want the evaluated <form>, use input
;; #=(eval <form>)
(defn make-value-from-input [input]
  ;;(eval (read-string input)))
  (read-string input))

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
(defmethod set-value clojure.lang.Var [a-var v]
  (alter-var-root a-var (constantly v)))

;; fake getter for JMX - not called but reflected upon by JMX
;; Must return a method "String <meth>()".
(def fake-getter
  (-> Class
      (.getMethod "getCanonicalName"
                  (into-array Class []))))

;; fake setter for JMX - not called but reflected upon by JMX
;; Must return a method "<type> <meth>(String)".
(def fake-setter
  (-> Class
      (.getMethod "getField"
                  (into-array [String]))))

(defn name-of [state]
  (or (:attr-name (meta state))
      (throw (RuntimeException.
              (format "oops: %s %s" (pr-str state) (meta state))))))

(defn description-of [state]
  (:attr-description (meta state)))

;; called from Spring -- not JMX
(defn make-mbean-attribute-info [state]
  (javax.management.MBeanAttributeInfo.
   (name-of state)
   (description-of state)
   fake-getter
   fake-setter))

;; called from Spring -- not JMX
(defn make-mbean-info [mbean ^String mbean-description states]
  (javax.management.MBeanInfo.
   (str (class mbean)) ;; doesn't matter
   mbean-description
   (into-array
    (map #(make-mbean-attribute-info %) states))
   nil ;; no constructors
   nil ;; no operations
   nil)) ;; no notifications

(defn set-attribute [attr attrs]
  (core/log "(setAttribute %s %s) called"
            (pr-str attr)
            attrs)
  (let [attr-name (.getName attr)
        state (or (attrs attr-name)
                  (throw (IllegalArgumentException.
                          (format "Unknown attribute '%s'"
                                  attr-name))))
        new-value (make-value-from-input (.getValue attr))]
    (set-value state new-value)))

(defn get-attribute [attr-name attrs]
  (core/log "(getAttribute %s %s) called"
            (pr-str attr-name)
            attrs)
  (let [state (or (attrs attr-name)
                  (throw (IllegalArgumentException.
                          (format "Unknown attribute '%s'"
                                  attr-name))))
        res (pr-str @state)]
    (core/log "(getAttribute %s %s) returns %s"
              (pr-str attr-name)
              attrs
              (pr-str res))
    res))

(defn get-attributes [attr-names attrs]
  (core/log "(getAttributes %s %s) called"
            (vec attr-names)
            attrs)
  (let [result (javax.management.AttributeList.)]
    (dorun
     (for [attr-name attr-names
           :let [state (or (attrs attr-name)
                           (throw (IllegalArgumentException.
                                   (format "Unknown attribute '%s'"
                                           attr-name))))
                 attr-value (pr-str @state)]]
       (.add result ^Object attr-value)))
    (core/log "(getAttributes %s %s) returns %s"
              (vec attr-names)
              attrs
              (pr-str result))
    result))

;; Creates a DynamicMBean
;; Exceptions are logged and mapped
(defn make-mbean [mbean-description & states]
  (let [attrs (zipmap (map name-of states) states)]
    (reify javax.management.DynamicMBean
      ;; called via Spring
      (getMBeanInfo [this]
        (make-mbean-info this (str mbean-description) states))

      ;; called via JMX
      (setAttribute [this attr]
        (with-exception-mapping "(setAttribute %s %s)" [attr attrs]
          (set-attribute attr attrs)))
      
      ;; called via JMX
      (getAttribute [this attr-name]
        (with-exception-mapping "(getAttribute %s %s)" [attr-name attrs]
          (get-attribute attr-name attrs)))
      
      ;; called via JMX
      (getAttributes [this attr-names]
        (with-exception-mapping "(getAttributes %s %s)" [(vec attr-names) attrs]
          (get-attributes attr-names attrs))))))

;; -------------------------------------------------------------------
;; JMX Operation
;; -------------------------------------------------------------------

;; Proxy/wrapper around a Clojure function - this will be passed
;; to (make-model-mbean-info)
(defn fn-wrapper-of [a-fn]
  (proxy [java.text.Format] [] 
    (toString [] (format "(fn-wrapper-of %s meta=%s)" a-fn (meta a-fn)))
    (parseObject [a-str]
      (with-exception-mapping "Calling %s with [%s]" [a-fn a-str]
        (let [arg (make-value-from-input (format "[%s]" a-str))
              _ (core/log "Calling %s with %s" a-fn arg)
              res (pr-str (apply a-fn arg))]
          (core/log "Calling %s with %s returns %s" a-fn arg res)
          res)))))

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
  (core/log "making model-mbean-info for bean-obj = %s"
            bean-obj)
  (javax.management.modelmbean.ModelMBeanInfoSupport.
   "classname ignored!"
   "description ignored"
   (into-array javax.management.modelmbean.ModelMBeanAttributeInfo [])
   (into-array javax.management.modelmbean.ModelMBeanConstructorInfo [])
   (into-array [(make-model-mbean-operation-info)])
   (into-array javax.management.modelmbean.ModelMBeanNotificationInfo [])))

;; -------------------------------------------------------------------
;; Spring integration
;; -------------------------------------------------------------------

;; will be used for bean selection and registration of JMX operations/Clojure functions
(defn mbean-info-assembler [pred]
  (proxy [org.springframework.jmx.export.assembler.AutodetectCapableMBeanInfoAssembler] []
    (includeBean [bean-class bean-name]
      ;; bean-class will be java.lang.Object for factory-generated beans
      (let [incl? (pred bean-name)]
        (core/log "includeBean class=[%s] id=[%s] RETURNS %s" bean-class bean-name incl?)
        incl?))
    ;; will only be used for JMX operations/Clojure functions - not JMX attributes
    (getMBeanInfo [bean-obj bean-name] ;; returns javax.management.modelmbean.ModelMBeanInfo
      (core/log "assembling JMX operation bean=[%s] id=[%s]" bean-obj bean-name)
      (make-model-mbean-info bean-obj bean-name))))
