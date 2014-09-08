(ns spring-break.core)
(defn -main [conf & bean-names]
  (let [_ (println (format "+++ Loading ClassPathXmlApplicationContext from '%s'" conf))
        sac (org.springframework.context.support.ClassPathXmlApplicationContext. conf)]
    (println (format "+++ Getting beans %s" bean-names))
    (dorun
     (for [bean-id bean-names
           :let [bean (.getBean sac bean-id)]]
       (println (format "+++ bean '%s' = %s  (%s)"
                        bean-id
                        bean
                        (when bean (.getClass bean))))))
    (println "+++ done")))
