(ns spring-break.shutdown-agents
  (:require [spring-break.core :as core]))

(defn make-shutdown-agent-bean []
  (let [state (atom {:running true})]
    (reify
      org.springframework.context.SmartLifecycle
      (getPhase [this] 0)
      (isAutoStartup [this]
        true)
      (isRunning [this]
        (core/log "isRunning agents : %s"
                  (boolean (:running @state)))
        (boolean (:running @state)))
      (start [this])
      (stop [this runnable]
        (core/log "stopping agents ...")
        (shutdown-agents)
        (swap! state assoc :running false)
        (core/log "stopping agents DONE")
        (.run runnable)))))
