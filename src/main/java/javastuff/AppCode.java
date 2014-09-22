package javastuff;

public class AppCode {

	public interface SomeBusinessInterface {
		String someMethod(String pOther);
	}

	public static class SomeBusinessImpl implements SomeBusinessInterface {

		private SomeBusinessInterface m_other;

		public void setOther(SomeBusinessInterface pOther) {
			// String msg = "*** Calling setOther(" + pOther + ") on " + this;
			// System.out.println(msg);
			m_other = pOther;
			m_other.someMethod("foo");
		}

		@Override
		public String someMethod(String pArg) {
			String msg = "*** Calling someMethod(" + pArg + ") on " + this;
			// System.out.println(msg);
			return msg;
		}

		@Override
		public String toString() {
			String thisString = "[" + super.toString() + "] m_other = '"
					+ m_other + "'";
			// String msg = "*** Calling toString() on " + thisString;
			// System.out.println(msg);
			return thisString;
		}
	}

}