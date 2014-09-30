(ns spring-break.core)
(defn -main [conf & bean-names]
  (let [_ (printf "+++ Loading ClassPathXmlApplicationContext from '%s'\n" conf)
        sac (org.springframework.context.support.ClassPathXmlApplicationContext. conf)]
    ;;(.registerShutdownHook sac)
    (printf "+++ Getting beans %s\n" bean-names)
    (dorun
     (for [bean-id bean-names
           :let [bean (.getBean sac bean-id)]]
       (printf "+++ bean '%s' = %s  (%s)\n"
               bean-id
               bean
               (when bean (.getClass bean)))))
    (printf "+++ done\n")
    (.close sac)))
