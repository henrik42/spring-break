package javastuff;

public class AppCode {

	public interface SomeBusinessInterface {
		String someMethod();
	}

	public static class SomeBusinessImpl implements SomeBusinessInterface {

		private SomeBusinessImpl m_other;

		public void setOther(SomeBusinessImpl pOther) {
			m_other = pOther;
		}

		@Override
		public String someMethod() {
			return "My name is : " + super.toString();
		}
	}

}