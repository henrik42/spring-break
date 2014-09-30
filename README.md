This is a Clojure library for Clojure/Spring integration.

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

# Driver 

We'll need a *driver* app to run our code. We have one in Clojure and
one in Java (just to notice that there are 38 braces in the Java code
but only 36 in the Clojure code ;-)

## Clojure

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

## Resolving project dependencies

The first time you run the example above, leiningen will download all
the required JARs to ```./local-m2/``` (see ```:dependencies``` 
in ```project.clj```) so you'll need an internet connection. I
put ```:local-repo "local-m2"``` in there so that I can easily track what
JARs we need for the *integration case*.

You can use

	lein deps

to just resolve dependencies and to update your repository.

## The project's classpath

Run this:

	lein classpath

This will give you the classpath for your project.

## Run without lein

You'll need the classpath if you want to run our code without
lein. Try:

    CP=`lein classpath`
	java -cp ${CP} clojure.main -m spring-break.core spring-config-empty.xml

This will save you the start-up time of lein but you have to update
your ```${CP}``` if you change the project dependencies (which will
not be that often once you project has stablized).

## Working offline

**This needs to be worked over**

When you don't have an internet connection, you want to use

	lein trampoline with-profile production run spring-config-empty.xml

**TODO: introduce a profile *offline* for this**

In this case leiningen will not try to check and download any
dependencies.

## Java

Since I want to show how to integrate Clojure- and Java-based Spring
beans, I need some Java code as well.
In ```./src/main/java/javastuff/Driver.java``` you'll find the *driver*
from above implemented in Java. We won't need it that much.

Compile:

	lein javac -verbose

Run:

    CP=`lein classpath`
	java -cp ${CP} javastuff.Driver spring-config-empty.xml

## Running with an uberjar

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

# Defining Clojure-based Spring beans

Spring has several built-in instantiation strategies for beans. One of
them lets you name a ```class```, an (instance) ```factory-method```
and any number of ```constructor-arg``` which again may be Spring
beans (nested ```bean``` element or referenced by ```ref``` attribute)
or of any of the built-in value types (e.g. ```String```).

## hello world!

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

## Loading Clojure script files

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
namespace but a *plain script*.** (I use dashes here in the directory
name - so this cannot be a namespace!)

Loading a script file will usually not create any instances that you
use as Spring beans. **You load this script for its side-effects**
(i.e. *defines* or just the output to stdout in this simple example).

Try this:

	lein run spring-config-load-script.xml load_script_code

You'll see that the Spring bean ```load_script_code``` has the value ```null``` --- that is the value of the last form that is
evaluated. Try putting this into ```src/main/clojure/no-namespace-scripts/script-code.clj```

	(printf "+++ loading script-code.clj in namespace '%s'\n" *ns*)
	"foobar"

and re-run.

**TODO: do this via classloader instead of plain file IO, since that
would work with uberjar as well**

## Use bean ids

You should get into the habit of using ```id``` attributes to name
your spring beans. If you don't, you may run into situations where you
have more than one Spring **bean** defined.

This happens when using ```classpath*:``` and Spring finds your Spring
bean definition files more than one time (e.g. when your classpath has
more than one entry to a JAR containing the resource). If you
use ```id``` Spring will judge this case as a *bean redefinition* instead
of the definition of two separate beans. This can make a big
difference!

Try this:

* Remove the ```id="load_script_code"```
  from ```resources/spring-config-load-script.xml```

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

## Depending on side-effects

 In ```resources/spring-config-load-script-with-sideeffect.xml``` I
 put a second bean definition that calls the function ```foo/foobar```
 which gets ```def```ined in
 ```src/main/clojure/no-namespace-scripts/script-code-with-function.clj```. Note
 that we have to explicitly name the namespace ```foo``` because
 *this* code is ```eval```uated in namespace ```user```.

	  <bean id="foobar" 
		class="clojure.lang.Compiler" 
		depends-on="load_script_code"
		factory-method="load">
			<constructor-arg>
				<bean class="java.io.StringReader">
			<constructor-arg value='(foo/foobar "bar")' />
		  </bean>
		</constructor-arg>
	  </bean>
	
	  <bean id="load_script_code" 
		class="clojure.lang.Compiler" 
		factory-method="loadFile">
		<constructor-arg 
		value="src/main/clojure/no-namespace-scripts/script-code-with-function.clj" />
	  </bean>

Note that I put ```depends-on="load_script_code"``` in the bean
definition. This way we tell Spring that *this* bean needs another
bean to be instanciated before itself can be instanciated. In this
simple example we could have *fixed* the problem by just reverting the
order of the bean definitions. But using ```depends-on``` will work
even in complex bean definition set-ups.

And here's the Clojure code for reference.

	(printf "+++ loading %s in namespace '%s'\n" *file* *ns*)
	(ns foo)
	(defn foobar [& args]
	  (printf "+++ Calling (%s %s)\n" foobar args)
	  (vec args))

Run the example:

	lein run spring-config-load-script-with-sideeffect.xml foobar

## Loading Clojure namespaces

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

Since ```require``` always returns ```nil``` you will see ```null```
regardless of what is in the namespace. But still the last evaluated
form gets returned. And of course you can put it into the XML
defintion as well --- i.e. you can feed more than one form
to ```clojure.lang.Compiler/load``` --- like this:

	<constructor-arg value="(require 'spring-break.the-code) :foobar" />

## Create a Clojure-based Spring bean that creates Spring beans

The nested ```<bean><constructor-arg><bean><constructor-arg>``` XML
definition is a bit too clumsy. I would like to just use one
non-nested structure.

So let's define our first usefull Spring Bean. The bean will be
whatever we put into ```spring-break.factories/clojure-object-factory```
	
	  <bean id="clojure_factory" 
		class="clojure.lang.Compiler" 
		factory-method="load">
		<constructor-arg>
		  <bean class="java.io.StringReader">
			  <constructor-arg 
				  value="(require 'spring-break.factories) 
			      spring-break.factories/clojure-object-factory" />
		  </bean>
		</constructor-arg>
	  </bean>

In ```src/main/clojure/spring_break/factories.clj``` I put

* the factory function

		(defn compiler-load [s]
		  (clojure.lang.Compiler/load
		   (java.io.StringReader. s)))

* a *protocol* so that we have a **named method** that we can tell
  Spring to call

		(defprotocol object-factory
		  (new-instance [this s]))

* and an object of the protocol type which will be our Spring bean

		(def clojure-object-factory
		  (reify object-factory
			(new-instance [this s]
			  (compiler-load s))))

Now we can do this (note that we have to use the *Java-name* of the
method)

	  <bean id="a_clojure_bean" 
		factory-bean="clojure_factory"
		factory-method="new_instance">
		<constructor-arg 
		value=":it-works" />
	  </bean>

There is one more tweak: I wanted to DRY out the XML definition a
little more, so I defined a *abstract parent*. Now all the
Clojure-based beans just have to name their ```id```, ```parent``` and
their *code*.

	  <bean id="clojure_fact" 
		abstract="true"
		factory-bean="clojure_factory"
		factory-method="new_instance">
	  </bean>

	  <bean id="a_clojure_bean" 
		parent="clojure_fact">
		<constructor-arg 
		value=":it-works" />
	  </bean>

I set up an XML file for this. So you may try:

	lein run spring-config-factories.xml a_clojure_bean

## Start Swank

As a *special* side-effect you can start swank:

	  <import resource="spring-config-factories.xml" />
	  <bean id="run_swank" 
		parent="clojure_fact">
		<constructor-arg 
		value='
			   (ns user
				 (:use swank.swank))
			   (println "+++ Starting swank server")
			   (future 
				   (start-server :port 4007))
			   ' />
	  </bean>

Here we re-use the ```clojure_fact``` Spring bean from above and start
the swank server. We use a ```future``` to do this in a separate
thread so that we will not slow down the *boot* of the Spring
application context and to drop any exception that may come out of
that call (e.g. if the port is already in use).

	lein run spring-config-swank.xml

## Start nrepl

Starting an nrepl server is just as easy:

	  <import resource="spring-config-factories.xml" />
	  <bean id="run_nrepl" 
		parent="clojure_fact">
		<constructor-arg 
		value='
			   (ns user
				 (:require [clojure.tools.nrepl.server]))
			   (println "+++ Starting nrepl server ...")
			   (def server (clojure.tools.nrepl.server/start-server :port 7888))
			   (println "+++ nrepl server started.")
			   ' />
	  </bean>

And run like this:

	lein run spring-config-nrepl.xml

To connect to this nrepl server use:

	lein repl :connect 7888

## Beware of comments

Note that this won't work:

	  <import resource="spring-config-factories.xml" />
	  <bean id="run_swank" 
		parent="clojure_fact">
		<constructor-arg 
		value='
			   (ns user
				 (:use swank.swank))
			   (println "+++ Starting swank server")
			   ;; Start swank
			   (future 
				   (start-server :port 4007))
			   ' />
	  </bean>

The XML text content will be given to the ```Compiler/load``` function
as a **one line String with no line-breaks**. So the comment will
comment out everything after the ```;; Start swank```. Use ```#_```
and ```(comment)``` instead.

# Wrapping Spring beans

One of the use-cases of the Clojure/Spring integration is to wrap
Java-based Spring beans with your Clojure code (*proxying*). Since
this use-case is not limited to Clojure but is omni-present in Spring
AOP, there exist some Spring classes to support this.

For the following examples I will use some Java classes as a
placeholder for the *Java-based application* and then show how to wire
the Clojure code into that.

Assume that you have a Java-based Spring bean ```some_bean``` which is
used by (and injected into) a second Java-based bean ```some_bean_user```
(both being of class ```javastuff.AppCode$SomeBusinessImpl```).  This is **the** standard
case for Java-based applications since that is what most people use
Spring for: *wiring Java instances*.

	  <bean id="some_bean" class="javastuff.AppCode$SomeBusinessImpl" />
	  <bean id="some_bean_user" class="javastuff.AppCode$SomeBusinessImpl">
		<property name="other" ref="some_bean" />
	  </bean>

And you have some Clojure code that you want to *wrap around* ```some_bean```.
We put that into bean ```my_interceptor```.

	  <bean id="my_interceptor" 
		parent="clojure_fact">
		<constructor-arg value="(require 'spring-break.proxying) 
								spring-break.proxying/my-interceptor" />
	  </bean>

Here's the code for reference:

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

The easiest way to do this is with a ```BeanNameAutoProxyCreator```:

	  <bean id="creator" 
		class="org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator">
		<property name="beanNames" value="some_bean"/>
		<property name="interceptorNames">
		  <list>
			  <value>my_interceptor</value>
		  </list>
		</property>
	  </bean>

And run:

	lein run spring-config-with-BeanNameAutoProxyCreator.xml some_bean

In this example we implemented an *around advice* --- i.e. we supplied
code that runs before the proxied code and after the proxied
code. This is the most general case of *wrapping*. We could have
decided to not delegate to the proxied code at all, effectively
replacing the code/bean. Or we could have done something with the
parameters/aguments (i.e. changed/replaced) before passing them to the
proxied code or we could have changed the returned value or mapped the
exception in case one was thrown or whatever.

Spring has a lot more ways of specifying how to apply some code/beans
to some other code/beans (e.g. by type, annotation, regular
expressions, etc) --- see the Spring AOP documentation for details.

# Using CGLIB

**TODO: apply proxying without using an interface.**

# Replacing Spring beans

We can effectively replace a Java-based Spring bean via *proxying*
(see above) but there is a much simpler way: just **redefine** the
bean. *Redefining* a bean just means: defining a bean with the id of
the bean you want to redefine (target bean) **after** the target bean
has been defined.

One way to control this *order* is to use an ```import```. 

	  <bean id="some_bean_user" class="javastuff.AppCode$SomeBusinessImpl">
		<property name="other" ref="some_bean" />
	  </bean>
	  <bean id="some_bean" class="javastuff.AppCode$SomeBusinessImpl" />
	  <import resource="spring-config-redefine-some-bean.xml" />

Here we ```import``` the redefinition of bean ```some_bean```:

	  <bean id="some_bean" parent="clojure_fact">
		<constructor-arg value='(proxy [javastuff.AppCode$SomeBusinessInterface][]
					(someMethod [p]
					(printf "+++ Calling someMethod(%s)\n" p)
					"foo"))
					' />
	  </bean>

Try running

	lein run spring-config-with-redefine.xml some_bean

and then comment out the ```import``` and re-run. Note that the output
of the Java-based Spring bean is out-of-order compared to the output
of the Clojure-based one (don't know why yet).

Of course the Clojure-based bean must implement any *client view*
(i.e. all the classes and interfaces) through which the replaced bean
may be accessed by its *users*. Sometimes this may be impossible
(e.g. in case of ```final``` classes).

# Autowiring

Until now we have wired the Spring beans via nested ```property```
elements within the ```bean``` definition elemnts. But some
applications may use *autowiring*. In this case there will be
annotations in the Java code which mark the
injection/wiring-points. Spring will then search the application
context to find *matching* beans and autowire them. You may supply
explicit injection via ```property``` to override but that is
optional.

The Java code may like this:

		@org.springframework.beans.factory.annotation.Autowired
		private SomeCode m_autoWired;

And the Spring bean definition (```spring-config-autowiring.xml```)

	  <context:annotation-config/>
	  <import resource="spring-config-factories.xml" />
	  <bean id="an_auto_wire_candidate" 
		parent="clojure_fact">
		<constructor-arg value='(proxy [javastuff.AppCode$SomeCode][])' />
	  </bean>
	  <bean id="some_bean" scope="singleton" class="javastuff.AppCode$SomeBusinessImpl" />

And run:

	lein run spring-config-autowiring.xml some_bean

## Autowiring gotcha --- beware of ordering

Try changing the order of the bean definitions
(```spring-config-autowiring-gotcha.xml```)

	  <bean id="some_bean" class="javastuff.AppCode$SomeBusinessImpl" />
	  <bean id="an_auto_wire_candidate" 
		parent="clojure_fact">
		<constructor-arg value='(proxy [javastuff.AppCode$SomeCode][])' />
	  </bean>

And run:

	lein run spring-config-autowiring-gotcha.xml some_bean

This will fail. When Spring tries to instanciate and autowire ```some_bean``` it
has no class/type information for ```an_auto_wire_candidate``` because that
bean is created by a factory
and since it is a singleton and has not been created yet, Spring does
not know about its type. Adding ```class="javastuff.AppCode.SomeCode"```
to ```an_auto_wire_candidate``` won't help --- Spring will not interpret
this as being the type of the bean.

You can *fix* this by letting ```some_bean```
*depend on* ```an_auto_wire_candidate``` (this is the most *natural* but also the
most *invasive* way of controlling bean instanciation order):

	<bean id="some_bean" depends-on="an_auto_wire_candidate" class="javastuff.AppCode$SomeBusinessImpl" />

But there are other (more implicit) ways:

Make ```some_bean``` a ```prototype```:

	<bean id="some_bean" scope="prototype" class="javastuff.AppCode$SomeBusinessImpl" />

Or you make it ```lazy-init```:

	<bean id="some_bean" lazy-init="true" class="javastuff.AppCode$SomeBusinessImpl" />

All of these fixes introduce an ordering so that ```some_bean``` is
created after ```an_auto_wire_candidate```.

If you want to autowire your Clojure beans into an existing Java-based
Spring application it is usually no option to change the (existing)
Spring bean definitions.

Sometime you may be able to insert this into one of the bean
definition files that get loaded early enough so that the beans that
need those autowiring candidates come after that:

	<import resource="classpath*:spring-config-clojure.xml" />

In this case Spring will try to load **all occurances**
of ```spring-config-clojure.xml``` from the classpath but won't fail when
it can't find any (```classpath*:```). This introduces the option of
placing such a file where the classloader can pick it up just when you
need it.

But still this *solution* has the drawback of depending on an ordering
that may be hard to control.

The most elegant solution is this: we just use a *dummy* bean that is
a ```org.springframework.beans.factory.config.BeanFactoryPostProcessor```
and let that depend on our Clojure-based beans. We cannot supply such
a bean via Clojure since Spring needs the class information for this
(and again that will be available *too late*). I just use one of the
Spring utility classes as a *no-op* just to have a hook that will
pull-in the Clojure-based beans.

	  <bean class="org.springframework.beans.factory.config.PropertyPlaceholderConfigurer" 
		id="pull_in_clojure_beans"
		depends-on="an_auto_wire_candidate">
		<property name="placeholderPrefix" value="$$$do-not-use$$$" />
	  </bean>

Usually the beans that use autowiring will be *normal business beans*
that won't hook into the Spring application context lifecycle. So in
most cases we do not have to worry about if
our ```BeanFactoryPostProcessor``` comes before any
other ```BeanFactoryPostProcessor``` that may carry ```@Autowired``` members
that should receive our Clojure-based beans.

If you run into such a situation please contact me and tell me how you
got out of that :-)

# Bean lifycycle callbacks

Spring beans have a *lifecycle*:

* construction (optionally with parameters)

* property setting

* initialization

* shutdown

The implementation of these are not Spring-dependent --- i.e. there
are no Spring-specific code bindings (classes, interfaces,
annotations) if you're not using Spring annotations for autowiring.

The following sections will show how Clojure-based Spring beans can
participate in this lifecycle.

## Construction

Using parameters with construction via ```(.new-instance
spring-break.factories/clojure-object-factory)``` is tricky and
clumsy. I haven't come up with a good solution for that.

Even doing this with Java-based beans is tricky, since in some cases
you have to resolve arity ambiguities via ```index``` values in
the ```constructor-arg``` elements.

So we just won't use this and stick to *property settings*.

You find the complete code 
in ```src/main/clojure/spring_break/lifecycle.clj```:

	lein spring-config-lifecycle.xml some_bean
	
## Property setting

Spring uses *Java beans setter methods* to set bean properties. Before
Spring 3.1 these methods had to be ```void``` (see [1] [2]).

These examples will only work with Spring 3.1 and above, because I 
use ```defprotocol``` to define the setter methods. I you want to use
older Spring versions you would have to use AOT ([3]).

	(defprotocol some-bean-java-bean
	  (setFoo [this v])
	  (setBar [this v]))

Now we implement that:

	(def some-bean
	  (let [state (atom {})]
		(reify
		  some-bean-java-bean
		  (toString [this] (str @state))
		  (setFoo [this v]
			(swap! state assoc :foo v)
			(printf "+++ after setFoo : %s\n" this))
		  (setBar [this v]
			(swap! state assoc :bar v)
			(printf "+++ after setBar : %s\n" this)))))

**TODO**: For the boiler-plate code you should use a macro.

[1] http://docs.spring.io/spring/docs/3.2.x/spring-framework-reference/html/new-in-3.1.html

[2] https://jira.spring.io/browse/SPR-8079

[3] http://stackoverflow.com/questions/10634189/spring-bean-initialization-clojure

## Initialization/afterPropertiesSet

Spring lets you define your own *init* methods but you may use the
marker interface ```org.springframework.beans.factory.InitializingBean``` as well:

	(def some-bean
	  (let [state (atom {})]
		(reify
		  org.springframework.beans.factory.InitializingBean
		  (toString [this] (str @state))
		  (afterPropertiesSet [this] (printf "+++ afterPropertiesSet : %s\n" this)))))

Note that in this case ```afterPropertiesSet``` **will be called** ---
**you cannot turn it off in the XML bean definition file** 
(using ```init-method="<method>"``` instead gives you that option but then you
have to define a ```protocol``` for that).

## Cleanup/destroy

The cleanup is similar:

	(def some-bean
	  (let [state (atom {})]
		(reify
		  org.springframework.beans.factory.DisposableBean
		  (toString [this] (str @state))
		  (destroy [this] (printf "+++ destroy : %s\n" this)))))

## Startup/shutdown

Spring offers call-back methods for asynchronuous startup and
shutdown. See the Spring documentation for details. I put in the code
just for completeness:

	(def some-bean
	  (let [state (atom {})]
		(reify
		  org.springframework.context.SmartLifecycle
		  (toString [this] (str @state))
		  (getPhase [this] 0)
		  (isAutoStartup [this] (printf "+++ isAutoStartup : %s\n" this) true)
		  (isRunning [this] (printf "+++ isRunning : %s\n" this) true)
		  (start [this] (printf "+++ start : %s\n" this))
		  (stop [this runnable] (printf "+++ stop : %s\n" this) (.run runnable)))))

# Implementing Spring callback interfaces

The Spring IoC container lets you define your beans and wire them
(like we have done above). But Spring also lets you *inject* your own
code into the Spring infrastructure. This way your code can
participate in the lifycycle of the Spring application context.

One way to inject your code is via Spring beans (of course!). Spring
will inspect all beans (i.e. bean definitions) of the application
context and will *detect* those beans that can participate in the
lifecycle (like the
```org.springframework.beans.factory.config.PropertyPlaceholderConfigurer```
above). Once those beans are identified they will be instanciated and
their *call-back* methods will be called by Spring during **the
appropriate lifecycle phase**.

## Container awareness

**TODO**

As such, they are recommended for infrastructure beans that require
programmatic access to the container.

Spring dependency

* ApplicationContextAware

Other methods of the
ApplicationContext provide access to file resources, publishing application events, and accessing a
MessageSource.

* BeanNameAware

* ApplicationEventPublisherAware

* NotificationPublisherAware

## Application context lifecycle

**TODO**

* BeanPostProcessor


# JMX/MBeans

**TODO**

# More to come

OK, this is it for today. 
