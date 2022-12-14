package io.github.rfc2616.utilities;

import static io.github.rfc2616.utilities.Utils.nullValue;

import java.net.Inet4Address;
import java.net.UnknownHostException;

public final class AppProperties {

	private AppProperties() { /***/ }

	private static final String DEFAULT_PORT = "8080";

	public static int getPort() {
		final String port = nullValue(
				System.getProperty(Constants.PROPERTY_PORT),
				System.getenv(Constants.ENV_PORT),
				DEFAULT_PORT
			);
		return Integer.parseInt(port);
	}

	private static final String DEFAULT_HOST_NAME;
	
	static {
		String hostname;
		try {
			hostname = Inet4Address.getLocalHost().getHostName();
		} catch(UnknownHostException e) {
			hostname = "localhost";
		}

		DEFAULT_HOST_NAME = hostname;
	}

	public static String getHostName() {
		return nullValue(
				System.getProperty(Constants.PROPERTY_HOSTNAME),
				System.getenv(Constants.ENV_HOSTNAME),
				DEFAULT_HOST_NAME
			);
	}

}
