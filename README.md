# spring-break

A Clojure library for Clojure/Spring integration.

It is not really a library because there is not that much code. I'll
show how to define Spring beans in Clojure in a way that enables you
to use Clojure in a Java-based Spring application without touching
either the Java code nor the Spring bean definitions.

For the examples below I assume that the Java-based Spring application
uses XML bean definition files to define the Spring application
context. If I find the time I will supply examples for other
definition strategies (like via annotations) as well.

For the usage examples below I'll use leiningen to run the code. You
should be able to adopt the code and the examples to cases where you
don't use leiningen (as it is the standard case for Java-based
applications). Using leiningen makes some tasks a lot easier (like
setting up the classpath, running tests, etc.) but it does hide
details that you have to know about when you're not using it. So I'll
try to give *plain Java examples* as well. Finally I'll show how to
*deploy* the code into a Java-based Spring application (the
*integration case*).

## Driver 

We'll need a *driver* app to run our code. We have one in Clojure and
one in Java (just to notice that there are 38 parentheses in the Java
code but only 36 in the Clojure code ;-)

### Clojure

You can run the driver app (```spring-break.core/-main```) via lein
 (see ```:main spring-break.core``` in ```project.clj```). It will
 build a *Spring application context* from XML definitions files --
 i.e. *resources* -- (first arg) and then retrieve Spring beans by
 their ```id``` (remaining args) from the application context and
 print those to stdout.

Try this:

    lein run spring-config-empty.xml

The XML definitions files will be loaded via classloader/classpath
which is ```./resources/spring-config-empty.xml``` in this case.

This run doesn't do much. It is just there to ensure that everything
is set up right. We'll load Clojure-based Spring beans in just a
minute.

### Resolving project dependencies

The first time you run the example above, leiningen will download all
the required JARs to ```./local-m2/``` (see ```:dependencies``` in
```project.clj```) so you'll need an internet connection. I put
```:local-repo "local-m2"``` in there so that I can easily track what
JARs we need for the *integration case*.

You can use

	lein deps

to just resolve dependencies and to update your repository.

### The project's classpath

Run this:

	lein classpath

This will give you the classpath for your project.

### Run without lein

You'll need the classpath if you want to run our code without
lein. Try:

    CP=`lein classpath`
	java -cp ${CP} clojure.main -m spring-break.core spring-config-empty.xml

This will save you the start-up time of lein but you have to update
your ```${CP}``` if you change the project dependencies (which will
not be that often once you project has stablized).

### Working offline

**This needs to be worked over**

When you don't have an internet connection, you want to use

	lein trampoline with-profile production run spring-config-empty.xml

**TODO: introduce a profile *offline* for this**

In this case leiningen will not try to check and download any
dependencies.

### Java

Since I want to show how to integrate Clojure- and Java-based Spring
beans, I need some Java code as well.  In
```./src/main/java/javastuff/Driver.java``` you'll find the *driver*
from above implemented in Java. We won't need it that much.

Compile:

	lein javac -verbose

Run:

    CP=`lein classpath`
	java -cp ${CP} javastuff.Driver spring-config-empty.xml

### Running with an uberjar

You can use lein to create an *uberjar* (aka *shaded jar*). Usually
you do this when delivering your application to end users:

	lein uberjar
	java -cp target/spring-break-0.1.0-SNAPSHOT-standalone.jar javastuff.Driver spring-config-empty.xml
	java -cp target/spring-break-0.1.0-SNAPSHOT-standalone.jar clojure.main -m spring-break.core spring-config-empty.xml

This builds an *uberjar* (which contains all JARs and resources in you
project dependencies -- i.e. *classpath*) and then runs our Java-based
driver and the Clojure-based driver.

**TODO: show how to use AOT and run the compiled Clojure code via
  -jar**

## Defining Clojure-based Spring beans

Spring has several built-in instantiation strategies for beans. One of
them lets you name a ```class```, an (instance) ```factory-method```
and any number of ```constructor-arg``` which again may be Spring
beans (nested ```bean``` element or referenced by ```ref``` attribute)
or of any of the built-in value types (e.g. ```String```).

### hello world!

So we may do this:

	  <bean id="hello_world" 
		class="clojure.lang.Compiler" 
		factory-method="load">
		<constructor-arg>
		  <bean class="java.io.StringReader">
		    <constructor-arg value='"Hello world!"' />
		  </bean>
		</constructor-arg>
	  </bean>

Run:

	lein run spring-config-hello-world.xml hello_world

### Loading Clojure script files

You can load Clojure script files via ```clojure.lang.Compiler/loadFile```:

	  <bean id="load_script_code" 
		class="clojure.lang.Compiler" 
		factory-method="loadFile">
		<constructor-arg value="src/main/clojure/no-namespace-scripts/script-code.clj" />
	  </bean>

Now run this:

	lein run spring-config-load-script.xml

You'll see the output of ```script-code.clj```. Notice that
evalutation starts in namespace ```user```. We are **not loading a
namespace but a *plain script*.** (I use dashes here - so this cannot
be namespaces!)

Loading a script file will usually not create any instances that you
use as Spring beans. **You load this script for it's side effects**
(i.e. *defines* or just the output in this simple example).

Try this:

	lein run spring-config-load-script.xml load_script_code

You'll see that the Spring bean ```load_script_code``` has the value ```null```.

**TODO: do this via classloader instead of plain file IO**

### Use bean ids

You should get into the habbit of using ```id``` attributes to name
your spring beans. If you don't, you may run into situations where you
have more than one Spring **bean** defined.

This happens when using ```classpath*:``` and Spring finds your Spring
bean definition files more than one time (e.g. when your classpath has
more than one entry to a JAR containing the resource). If you use
```id``` Spring will judge this as a *bean redefinition* instead of
the definition of two separate beans. This can make a big difference!

Try this:

* Remove the ```id="load_script_code"``` from
  ```resources/spring-config-load-script.xml```

* build the *uberjar*:

		lein uberjar 

* Run the driver app with
  ```classpath\*:spring-config-load-script.xml``` (this tells Spring
  to load **all occurences** of the named resource) and an *extended
  classpath* (```:./resources/```). This way we have the **resource
  ```spring-config-load-script.xml```** **twice** in our classpath:

		java -cp target/spring-break-0.1.0-SNAPSHOT-standalone.jar:./resources/ \
		clojure.main -m spring-break.core classpath\*:spring-config-load-script.xml 

You should see the output of ```script-code.clj``` twice. Depending on
what the code (i.e. the loaded Spring bean) does, it may make a
difference. Now insert ```id="load_script_code"``` back in and re-run
(don't forget ```lein uberjar```!). See?

### Loading Clojure namespaces

To load a namespace we can just ```require``` it:

	  <bean id="load_namespace" 
		class="clojure.lang.Compiler" 
		factory-method="load">
		<constructor-arg>
		  <bean class="java.io.StringReader">
	        <constructor-arg value="(require 'spring-break.the-code)" />
		  </bean>
		</constructor-arg>
	  </bean>

Run:

	lein run spring-config-load-namespace.xml load_namespace

Try the *multiple load example* from above. There **will be** two bean
definition, but Clojure will *load* the namespace only once!

## More to come

OK, this is it for today. 
