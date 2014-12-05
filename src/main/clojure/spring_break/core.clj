(ns spring-break.core
  (:gen-class))

(defn log [fmt & args]
  (.println System/out (apply format (str "+++ " fmt) args)))

(defn -main [conf & bean-names]
  (let [_ (log "Loading ClassPathXmlApplicationContext from '%s'" conf)
        closed (promise)
        sac (proxy [org.springframework.context.support.ClassPathXmlApplicationContext][conf]
              (doClose []
                (try
                  (log "Shutting down Spring application context ...")
                  (proxy-super doClose)
                  (finally
                    (log "Shutdown completed with %s."
                         (if (proxy-super isActive)
                           "FAIL/still active"
                           "OK/inactive"))
                    (deliver closed :doesnt-matter)))))]
    (.registerShutdownHook sac)
    (log "Getting beans: [%s]" bean-names)
    (dorun
     (for [bean-id bean-names
           :let [bean (.getBean sac bean-id)]]
       (log "bean '%s' = '%s'  (%s)"
            bean-id
            bean
            (when bean (.getClass bean)))))
    (if (System/getProperty "wait-for-sac-close")
      (do
        (log "Waiting for Spring application context shuttdown ...")
        @closed)
      (.close sac))
    (log "done.")
    (when (System/getProperty "do-system-exit-0")
      (log "Explicit (System/exit 0).")
      (System/exit 0))))
