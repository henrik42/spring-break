;; Try this:
;; CP=$(JAVA_CMD=`which java` lein classpath)
;; java -cp ${CP} clojure.main src/main/clojure/no-namespace-scripts/shutdown-hook.clj
;;
;; And in another shell:
;; kill -HUP $(ps aux | grep java | grep shutdown-hook | awk '{print $2}')
;; Try -KILL instead of -HUP. Go to the java shell and type ctrl-c.
(let [state (atom nil)]
  (..
   (Runtime/getRuntime)
   (addShutdownHook
    (proxy [Thread][]
      (run []
        (.println System/out "hook")
        ;; make main thread complete
        ;;(swap! state (constantly 1))
        (dorun
         (for [i (range 10)]
           (do 
             (Thread/sleep 1000)
             (.print System/out "+"))))
        (.println System/out "hook ends")))))
  (loop []
    (Thread/sleep 1000)
    (.print System/out ".")
    (when-not @state
      (recur))))
