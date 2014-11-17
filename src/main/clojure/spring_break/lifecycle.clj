(ns spring-break.lifecycle
    (:require [spring-break.core :as core]))

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
        (core/log "after setFoo : %s" this))
      (setBar [this v]
        (swap! state assoc :bar v)
        (core/log "after setBar : %s" this))
      
      ;; org.springframework.beans.factory.InitializingBean
      (afterPropertiesSet [this]
        (core/log "afterPropertiesSet : %s" this))

      ;; org.springframework.beans.factory.DisposableBean
      (destroy [this]
        (core/log "destroy : %s" this))

      ;; org.springframework.context.SmartLifecycle
      (getPhase [this] 0)
      (isAutoStartup [this]
        (core/log "isAutoStartup : %s" this)
        true)
      (isRunning [this]
        (core/log "isRunning : %s" this)
        (boolean (:running @state)))
      (start [this]
        (core/log "start : %s" this)
        (swap! state assoc :running true))
      (stop [this runnable]
        (core/log "stop : %s" this)
        (.run runnable)))))

