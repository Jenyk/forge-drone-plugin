package cz.bcp.forge.arquillian.drone.browser;

public enum Browser {

	firefox, chrome, internetExplorer, opera, iphone, android, htmlUnit;
	

	public static boolean exists(String name) {
		for (Browser browser : Browser.values()) {
			if (browser.name().equals(name)) {
				return true;
			}
		}
		return false;
	}

}
