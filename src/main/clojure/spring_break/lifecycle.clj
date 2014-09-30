(ns spring-break.lifecycle)

(defprotocol some-bean-java-bean
  (setFoo [this v])
  (setBar [this v]))

(def some-bean
  (let [state (atom {})]
    (reify
      some-bean-java-bean
      org.springframework.beans.factory.InitializingBean
      org.springframework.beans.factory.DisposableBean
      org.springframework.context.SmartLifecycle
      
      (toString [this] (str @state))
      
      (setFoo [this v]
        (swap! state assoc :foo v)
        (printf "+++ after setFoo : %s\n" this))
      (setBar [this v]
        (swap! state assoc :bar v)
        (printf "+++ after setBar : %s\n" this))
      
      ;; org.springframework.beans.factory.InitializingBean
      (afterPropertiesSet [this] (printf "+++ afterPropertiesSet : %s\n" this))

      ;; org.springframework.beans.factory.DisposableBean
      (destroy [this] (printf "+++ destroy : %s\n" this))

      ;; org.springframework.context.SmartLifecycle
      (getPhase [this] 0)
      (isAutoStartup [this] (printf "+++ isAutoStartup : %s\n" this) true)
      (isRunning [this] (printf "+++ isRunning : %s\n" this) true)
      (start [this] (printf "+++ start : %s\n" this))
      (stop [this runnable] (printf "+++ stop : %s\n" this) (.run runnable))
      )))

