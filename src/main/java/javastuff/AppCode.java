package javastuff;

public class AppCode {

	public interface SomeBusinessInterface {
		String someMethod();
	}

	public static class SomeBusinessImpl implements SomeBusinessInterface {

		@Override
		public String someMethod() {
			return "My name is : " + super.toString();
		}
	}

}