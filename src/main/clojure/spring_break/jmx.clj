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

;; JMX Attribute stuff
(defmulti set-value (fn [r v] (class r)))
(defmethod set-value clojure.lang.Atom [an-atom v]
  (reset! an-atom v))
(defmethod set-value clojure.lang.Ref [a-ref v]
  (dosync 
   (ref-set a-ref v)))

(defn build-getter [s]
  (let [getter (-> s
                   (.getClass)
                   (.getMethod "toString"
                               (into-array Class [])))]
    (.println System/out (format "!!! getter for %s = %s" s getter))
    getter))

(defn build-setter [s]
  (let [setter (-> java.lang.Double
                   (.getMethod "valueOf"
                               (into-array Class [String])))]
    (.println System/out (format "!!! setter for %s = %s" s setter))
    setter))

(defn ref-wrapper-of [a-ref]
  (reify javax.management.DynamicMBean
    (getMBeanInfo [this]
      (javax.management.MBeanInfo.
       (.. this getClass getName)                             ; class name
       "Clojure Dynamic MBean"                             ; description
       ;;(map->attribute-infos @state-ref)                   ; attributes
       (into-array
        [(javax.management.MBeanAttributeInfo.
          "name"
          "desc"
          (build-getter this)
          (build-setter this)
          #_ setter)])
       nil                                                 ; constructors
       nil                                                 ; operations
       nil)) ;; Descriptor
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


#_ (defn ref-wrapper-of [a-ref]
  (reify clojure.lang.IFn
    (toString [this] (format "(ref-wrapper-of %s meta=%s)" a-ref (meta a-ref)))
    (invoke [this] ;; getter
      (.println System/out (str "***** returning " @a-ref))
      @a-ref)
    (invoke [this v] ;; setter
      (.println System/out (str "***** setting " v " on " a-ref))
      (set-value a-ref v))))

;; JMX Operation wrapper
(defn fn-wrapper-of [a-fn]
  (proxy [java.text.Format Runnable][] ;; Runnable is marker for JMX operation (vs. attribute)
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

#_ (defn make-getter [ref-wrapper]
  (let [getter (-> ref-wrapper
                   (.getClass)
                   (.getMethod "invoke"
                               (into-array Class [])))]
    (.println System/out (format "!!! getter = %s" getter))
    getter))

(defn make-getter [ref-wrapper]
  (let [getter (-> ref-wrapper
                   (.getClass)
                   (.getMethod "toString"
                               (into-array Class [])))]
    (.println System/out (format "!!! getter = %s" getter))
    getter))

(defn __make-getter [a-ref]
  (let [getter-proxy
        (proxy [clojure.lang.IFn][]
          (invoke []
            (.println System/out (format "getter called: %s" @a-ref))
            @a-ref))
        getter (-> getter-proxy
                   (.getClass)
                   (.getMethod "invoke"
                               (into-array Class [])))]
    getter))
    
(defn make-setter [ref-wrapper]
  (let [setter (-> ref-wrapper
                   (.getClass)
                   (.getMethod "invoke"
                               (into-array Class [Object])))]
    (.println System/out (format "!!! setter = %s" setter))
    setter))

(defn ___make-setter [a-ref]
  (let [setter-proxy nil
        setter nil]
    setter))

#_ (defn make-model-mbean-attribute-info [bean-obj]
  (let [getter (make-getter bean-obj)
        setter nil #_ (make-setter bean-obj)
        descriptor nil #_
        (javax.management.modelmbean.DescriptorSupport.
         (into-array
          String
          [#_ "openType=javax.management.openmbean.SimpleType(name=java.lang.String)"
           "originalType=java.lang.Object"]))]
    (.println System/out (format "!!! getter returns %s"
                                 (.invoke getter bean-obj (into-array Object []))))
    (.invoke setter bean-obj (into-array Object ["foo"]))
    (.println System/out (format "!!! getter returns %s"
                                 (.invoke getter bean-obj (into-array Object []))))
    (javax.management.modelmbean.ModelMBeanAttributeInfo.
     "a name"
     "a descrption"
     getter
     nil #_ setter
     descriptor)))

(defn make-model-mbean-attribute-info [bean-obj]
  (let [getter (make-getter bean-obj)
        setter nil #_ (make-setter bean-obj)
        info (javax.management.modelmbean.ModelMBeanAttributeInfo.
              "Foo"
              "a descrption"
              getter
              setter)
        desc (.getDescriptor info)]
    (.println System/out (format "!!! getter returns %s"
                                 (.invoke getter bean-obj (into-array Object []))))
    ;;(.invoke setter bean-obj (into-array Object ["foo"]))
    (.println System/out (format "!!! getter returns %s"
                                 (.invoke getter bean-obj (into-array Object []))))
    (.setField desc "getMethod" (.getName getter))
    ;;(.setField desc "currencyTimeLimit" (str Integer/MAX_VALUE))
    #_ (.setField desc "setMethod" (.getName setter))
    (.setDescriptor info desc)
    info))

(defn make-model-mbean-info [bean-obj bean-name]
  (let [is-fn (instance? java.text.Format bean-obj)
        _ (.println System/out
                    (format
                     "+++ making model-mbean-info for bean-obj = %s  is-fn = %s"
                     bean-obj is-fn))]
    (javax.management.modelmbean.ModelMBeanInfoSupport.
     ;;"classname ignored!"
     (.getName (.getClass bean-obj))
     "description ignored"
     (into-array javax.management.modelmbean.ModelMBeanAttributeInfo
                 (if-not is-fn [(make-model-mbean-attribute-info bean-obj)] []))
     (into-array javax.management.modelmbean.ModelMBeanConstructorInfo [])
     (into-array javax.management.modelmbean.ModelMBeanOperationInfo
                 (if is-fn [(make-model-mbean-operation-info)] []))
     (into-array javax.management.modelmbean.ModelMBeanNotificationInfo []))))

(defn mbean-info-assembler [pred]
  (proxy [org.springframework.jmx.export.assembler.AutodetectCapableMBeanInfoAssembler][]
    (includeBean [bean-class bean-name]
      ;; bean-class will be java.lang.Object for factory-generated beans
      (let [incl? (pred bean-name)]
        (.println System/out (format "+++ includeBean class=[%s] id=[%s] RETURNS %s" bean-class bean-name incl?))
        incl?))
    (getMBeanInfo [bean-obj bean-name] ;; returns javax.management.modelmbean.ModelMBeanInfo
      (.println System/out (format "+++ assembling bean=[%s] id=[%s]" bean-obj bean-name))
      (make-model-mbean-info bean-obj bean-name))))
