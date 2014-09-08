(defn main [conf & bean-names]
  (let [_ (.print System/out (format "+++ Loading ClassPathXmlApplicationContext from '%s'\n" conf))
        sac (org.springframework.context.support.ClassPathXmlApplicationContext. conf)]
    (.print System/out (format "+++ Getting beans %s\n" bean-names))
    (dorun
     (for [bean-id bean-names
           :let [bean (.getBean sac bean-id)]]
       (.print System/out (format "+++ bean '%s' = %s  (%s)\n"
                                  bean-id
                                  bean
                                  (when bean (.getClass bean))))))
    (.println System/out "+++ done")))
