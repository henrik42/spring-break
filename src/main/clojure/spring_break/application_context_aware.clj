(ns spring-break.application-context-aware)

(defn consume-args [sac args]
  (reduce-kv #(assoc %1 %2 (.getBean sac (name %3)))
             {}
             (apply hash-map args)))

(defn make-some-bean [& args]
  (printf "+++ make-some-bean args = %s\n" args)
  (let [state (atom {})]
    (reify
      org.springframework.context.ApplicationContextAware
      org.springframework.beans.factory.InitializingBean
      org.springframework.beans.factory.DisposableBean
      org.springframework.context.SmartLifecycle
      
      (toString [this] (str @state))

      ;; org.springframework.context.ApplicationContextAware
      (setApplicationContext [this v] 
        (swap! state assoc :sac v)
        (printf "+++ after setApplicationContext : %s\n" this))
      
      ;; org.springframework.beans.factory.InitializingBean
      (afterPropertiesSet [this]
        ;;(reset! state (consume-args (:sac @state) args))
        (swap! state merge (consume-args (:sac @state) args))
        (printf "+++ afterPropertiesSet : %s\n" this))

      ;; org.springframework.beans.factory.DisposableBean
      (destroy [this] (printf "+++ destroy : %s\n" this)
        #_ @(promise))

      ;; org.springframework.context.SmartLifecycle
      (getPhase [this] 0)
      (isAutoStartup [this] (printf "+++ isAutoStartup : %s\n" this) true)
      (isRunning [this] (printf "+++ isRunning : %s\n" this) true)
      (start [this] (printf "+++ start : %s\n" this))
      (stop [this runnable] (printf "+++ stop : %s\n" this) (.run runnable))
      )))

