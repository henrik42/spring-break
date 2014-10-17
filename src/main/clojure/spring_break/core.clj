(ns spring-break.core)
(defn -main [conf & bean-names]
  (let [_ (printf "+++ Loading ClassPathXmlApplicationContext from '%s'\n" conf)
        closed (promise)
        sac (proxy [org.springframework.context.support.ClassPathXmlApplicationContext][conf]
              (close []
                (try
                  (print "+++ Closing down SAC\n")
                  (proxy-super close)
                  (finally
                    (printf "+++ SAC close() returned (active = '%s' --- should be 'false').\n"
                            (proxy-super isActive))
                    (deliver closed :doesnt-matter)))))]
    ;;(.registerShutdownHook sac)
    (printf "+++ Getting beans %s\n" bean-names)
    (dorun
     (for [bean-id bean-names
           :let [bean (.getBean sac bean-id)]]
       (printf "+++ bean '%s' = %s  (%s)\n"
               bean-id
               bean
               (when bean (.getClass bean)))))
    (if (System/getProperty "wait-for-sac-close")
      (do
        (print "+++ waiting for Spring application context being closed ...\n")
        @(promise))
      (.close sac))
    (printf "+++ done\n")))
