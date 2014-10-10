(ns spring-break.jmx)

;; Ein Wrapper um eine IFn. Dieser Wrapper besitzt eine benannte Methode, die
;; EIN String Parameter hat.
;; Achtung:man muss noch try/catch verwenden, damit die Exceptions auch ser/deser sind.
;; Evaluation kann man via #=() erreichen.
(defn fn-wrapper-of [a-fn]
  (proxy [java.text.Format][]
    (parseObject [a-str]
      (let [arg (read-string (format "[%s]" a-str))
            _ (.println System/out (format "+++ jmx-wrapper: Calling (%s %s)\n" a-fn a-str))
            res (apply a-fn arg)
            res-str (str res)]
        res-str))))

;; Dies ist eine "normale" Clojure Funktion
(defn bar-fn [a b]
  [b a])

;; Dies ist eine gewrappte Clojure Funktion.
(def bar-bean
  (fn-wrapper-of bar-fn))

(defn make-mbean-parameter-info []
  (javax.management.MBeanParameterInfo.
   "FORMS:"
   "java.lang.String"
   "Enter as many clojure forms as there are parameters to the clojure function being exposed via JMX."))

(defn make-model-mbean-operation-info [& args]
  (javax.management.modelmbean.ModelMBeanOperationInfo.
   ;; wird als Name der Method interpretiert, die "über" diese Operation aufgerufen wird.
   ;; Das Object, auf diesem diese Methode aufgerufen wird, ist die Spring-Bean.
   ;; Der Aufruf erfolgt über die Spring-Proxy-Klasse.
   ;;"toString"
   "parseObject" ;; Method matches make-jmx-wrapper
   "some description"
   (into-array [(make-mbean-parameter-info)])
   "java.lang.Object" ;; return type 
   javax.management.MBeanOperationInfo/ACTION_INFO ;; impact  INFO, ACTION, ACTION_INFO, UNKNOWN
   nil ;; Descriptor
   ))

;; Spring nimmt sich den Rückgabewert und konstruiet damit einen Proxy/Wrapper
;; auf die eigentliche Sping-Bean. Spring erzeugt eine
;; org.springframework.jmx.export.SpringModelMBean, deren invoke
;; Methode (definiert in javax.management.DynamicMBean) aufgerufen wird, um
;; generisch die Methoden der Spring-Bean aufzurufen.
;; Frage: kann man dem MBeanExporter eine Factory für die Erzeugung des Proxy
;; injizieren?
;; Hier darf man keine dynamisch erzeugte Klasse liefern, weil diese Instanz
;; ser/deser wird und die Jconsole hat die Klassen nicht!
(defn make-model-mbean-info [bean-obj bean-name]
  (javax.management.modelmbean.ModelMBeanInfoSupport.
   "some classname" ;; braucht nicht wirklich zu existieren
   "some desc"
   (into-array javax.management.modelmbean.ModelMBeanAttributeInfo [])
   (into-array javax.management.modelmbean.ModelMBeanConstructorInfo [])
   ;;(into-array javax.management.modelmbean.ModelMBeanOperationInfo [])
   (into-array [(make-model-mbean-operation-info)])
   (into-array javax.management.modelmbean.ModelMBeanNotificationInfo [])))

(defn mbean-info-assembler [& args]
  (proxy [org.springframework.jmx.export.assembler.AutodetectCapableMBeanInfoAssembler][]
    ;; Selektor der entscheidet, welche Beans über diesen Assembler publiziert werden
    ;; Der Selektor könnte natürlich auch die closure über args zur Spezialisierung
    ;; verwenden
    ;; Achtung: derzeit muss aber noch in dem "mapping" der Naming Strategy "KeyNamingStrategy"
    ;; der Name dieser Beans hinterlegt werden. Idee: alles über EINE Bean abhandeln und die Namen
    ;; dynamisch erzeugen
    (includeBean [bean-class bean-name]
      (.println System/out (format "INCLUDE? --> [%s] bean-name = [%s]" bean-class bean-name))
      (= bean-name "bar"))
    ;; Erzeugt die Interface Beschreibung der Spring-Bean bean-obj.
    ;; Hier muss man eigentlich einen Dispatch einbauen, damit man steuern kann,
    ;; wie das Interface für die verschiedenen Typen aussieht.
    ;; Steuern kann man das am Besten über den Typ und/oder über clojure-metas.
    (getMBeanInfo [bean-obj bean-name]
      (.println System/out (format "--> %s %s" bean-obj bean-name))
      (make-model-mbean-info bean-obj bean-name))))
