package javastuff;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Driver {
    
    public static void main(String[] args) {

	List<String> argsList = Arrays.asList(args);
	
	String springConfig = argsList.get(0);
	System.out.printf(
			  "*** Loading ClassPathXmlApplicationContext from '%s'\n",
			  springConfig);
	
	ApplicationContext sac = new ClassPathXmlApplicationContext(
								    springConfig);
	
	List<String> beanNames = argsList.subList(1, argsList.size());
	System.out.printf("*** Getting beans %s\n", beanNames);
	for (String beanId : beanNames) {
	    Object bean = sac.getBean(beanId);
	    System.out.printf("*** bean '%s' = '%s'  (%s)\n", beanId, bean,
			      (bean == null ? null : bean.getClass()));
	}
	System.out.println("*** done");
    }
}
