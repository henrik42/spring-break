(ns spring-break.swank
  (:use swank.swank)
  (:require [spring-break.core :as core]))

(defn swank-starter []
  (let [state (atom {})
        started (promise)]
    (reify
      org.springframework.context.SmartLifecycle
      (getPhase [this] 0)
      (isAutoStartup [this]
        true)
      (isRunning [this]
        (core/log "isRunning swank server : %s"
                  (boolean (:running @state)))
        (boolean (:running @state)))
      (start [this]
        (core/log "starting swank server ... ")
        (swap! state assoc :running true)
        (future 
          (start-server :port 4007)
          (deliver started :started)
          (core/log "starting swank server DONE")))
      (stop [this runnable]
        (core/log "stopping swank server ...")
        @started
        (stop-server)
        (swap! state assoc :running false)
        (core/log "stopping swank server DONE")
        (.run runnable)))))

