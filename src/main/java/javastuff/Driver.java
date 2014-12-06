package javastuff;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Driver {

	static void log(String pFmt, Object... pArgs) {
		System.out.printf("*** " + pFmt + "\n", pArgs);
	}

	public static void main(String[] args) {

		List<String> argsList = new LinkedList<String>(Arrays.asList(args));

		String springConfig = argsList.remove(0);
		log("Loading ClassPathXmlApplicationContext from '%s'", springConfig);

		final Queue<Object> closed = new ArrayBlockingQueue<Object>(1);

		ClassPathXmlApplicationContext sac = new ClassPathXmlApplicationContext(
				springConfig) {
			@Override
			protected void doClose() {
				try {
					log("Shutting down Spring application context ...");
					super.doClose();
				} finally {
					log("Shutdown completed with %s.",
							isActive() ? "FAIL/still active" : "OK/inactive");
					closed.add("doesnt-matter");
				}
			};
		};

		sac.registerShutdownHook();
		log("Getting beans: [%s]", argsList);
		for (String beanId : argsList) {
			Object bean = sac.getBean(beanId);
			System.out.printf("bean '%s' = '%s'  (%s)", beanId, bean,
					(bean == null ? null : bean.getClass()));
		}
		if (null != System.getProperty("wait-for-sac-close")) {
			log("Waiting for Spring application context shuttdown ...");
			closed.remove();
		} else
			sac.close();

		System.out.println("done");

		if (null != System.getProperty("do-system-exit-0")) {
			log("Explicit (System/exit 0).");
			System.exit(0);
		}
	}
}
