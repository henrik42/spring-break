# spring-break

A Clojure library for Clojure/Spring integration.

It is not really a library because there is not that much code. I'll
show how to define Spring beans in Clojure in a way that enables you
to use Clojure in a Java-based Spring application without touching
neither the Java code nor the Spring bean definitions.

For the examples below I assume that the Java-based Spring application
uses XML bean definition files to define the Spring application
context. If I find the time I will supply examples for other
definition strategies as well.

For the usage examples below I'll use leiningen to run the code. You
should be able to adopt the code and the examples to cases where you
don't use leiningen (as it is the standard case for Java-based
applications). Using leiningen makes some tasks a lot easier (like
setting up the classpath, running tests, etc.) but it does hide
details that you have to know about when you're not using it. So I'll
try give *plain Java examples* as well. Finally I'll show how to
*deploy* the code into the Java-based Spring application (the
*integration case*).

## Driver 

There is a simple driver app (```spring-break.core/-main```) which you
can run via lein (see ```:main spring-break.core``` in
```project.clj```) that will build a *Spring application context* from
XML definitions files -- i.e. *resources* -- (first arg) and then
retrieves Spring beans by their ```id``` (remaining args) from that
and print those to stdout.

Try this:

    lein run spring-config-1.xml bean1

The first time you run this leiningen will probably download all the
required JARs to ```./local-m2/``` (see ```:dependencies``` in
```project.clj```). I put ```:local-repo "local-m2"``` in there too so
that I can track what JARs we need for the *integration case*.

The XML definitions files will be loaded via classloader/classpath
which is ```./resources/spring-config-1.xml``` in this case.

When you don't have an internet connection you want to use

	lein trampoline with-profile production run spring-config-1.xml bean1

In this case leiningen will not try to check and download any
dependnecies.

## More to come

OK, this is it for today. 
