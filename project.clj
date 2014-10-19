(def springframework-version "3.2.11.RELEASE")
(defproject spring-break "0.1.0-SNAPSHOT"
  :description "A Clojure library for Clojure/Spring integration."
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [swank-clojure "1.4.3"]
                 [org.clojure/tools.nrepl "0.2.5"]
                 [org.clojure/java.jmx "0.3.0"]
                 [org.springframework/spring-core ~springframework-version]
                 [org.springframework/spring-context ~springframework-version]
                 [org.springframework/spring-beans ~springframework-version]]
  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :test-paths ["src/test/clojure"]
  :main spring-break.core
  ;;:java-cmd "/opt/jdk1.8.0/bin/java"
  :local-repo "local-m2"
  :profiles {:server-app {:jvm-opts ["-Dwait-for-sac-close"]}
             :jmx-server-app [:server-app
                              {:jvm-opts
                               ["-Dcom.sun.management.jmxremote"
                                "-Dcom.sun.management.jmxremote.port=9999"
                                "-Dcom.sun.management.jmxremote.authenticate=false"
                                "-Dcom.sun.management.jmxremote.ssl=false"]}]
             })


