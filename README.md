# spring-break

A Clojure library for Clojure/Spring integration.

It is not really a library because there is not much code. I'll show
how to define Spring beans in Clojure in a way that enables you to use
Clojure in a Java-based Spring application without touching either the
Java code nor the Spring bean definitions (but there a some
constaints; see below).

## Driver 

There is a simple driver app (```spring-break.core/-main```) that will
build a *Spring application context* from XML definitions files --
i.e. *resources* -- (first arg) and then retrieve Springs beans from
that and print those to stdout.

    lein run spring-config-1.xml bean1

## More to come

OK, this is it for now. 
