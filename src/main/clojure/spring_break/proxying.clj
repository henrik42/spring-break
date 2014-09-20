(ns spring-break.proxying)

(def my-interceptor
  (proxy [org.aopalliance.intercept.MethodInterceptor][]
    (invoke [invctn]
      (let [m (.getMethod invctn)
            t (.getThis invctn)
            a (vec (.getArguments invctn))
            _ (printf "+++ Calling method %s on %s with %s\n" m t a)
            res (try 
                  {:res (.proceed invctn)}
                  (catch Throwable t {:ex t}))]
        (printf "+++ DONE: Calling method %s on %s with %s %s\n"
                m t a
                (if-let [r (:res res)]
                         (format "returns '%s'" r)
                         (format "fails due to %s" (:ex res))))
        (if-let [r (:res res)]
          r
          (throw (:ex res)))))))

