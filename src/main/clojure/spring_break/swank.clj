(ns spring-break.swank
  (:use swank.swank)
  (:require [spring-break.core :as core]))

(defn swank-starter []
  (let [state (atom {})]
    (reify
      org.springframework.context.SmartLifecycle
      (getPhase [this] 0)
      (isAutoStartup [this]
        true)
      (isRunning [this]
        (boolean (:running @state)))
      (start [this]
        (core/log "starting swank server ... ")
        (future 
          (start-server :port 4007)
          (swap! state assoc :running true)
          (core/log "starting swank server DONE")))
      (stop [this runnable]
        (core/log "stopping swank server ...")
        (stop-server)
        (core/log "stopping swank server DONE")
        (.run runnable)))))

