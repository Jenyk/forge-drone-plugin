package cz.bcp.forge.arquillian.drone;

import java.util.List;
import javax.inject.Inject;
import cz.bcp.forge.arquillian.drone.DependencyUtil;
import cz.bcp.forge.arquillian.drone.browser.Browser;
import org.jboss.forge.parser.xml.Node;
import org.jboss.forge.parser.xml.XMLParser;
import org.jboss.forge.project.Project;
import org.jboss.forge.project.dependencies.Dependency;
import org.jboss.forge.project.dependencies.DependencyBuilder;
import org.jboss.forge.project.dependencies.ScopeType;
import org.jboss.forge.project.facets.DependencyFacet;
import org.jboss.forge.project.facets.ResourceFacet;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.shell.Shell;
import org.jboss.forge.shell.plugins.Alias;
import org.jboss.forge.shell.plugins.Command;
import org.jboss.forge.shell.plugins.Option;
import org.jboss.forge.shell.plugins.PipeIn;
import org.jboss.forge.shell.plugins.PipeOut;
import org.jboss.forge.shell.plugins.Plugin;
import org.jboss.forge.shell.plugins.SetupCommand;
import org.jboss.forge.shell.project.ProjectScoped;

/**
 *
 */
@Alias("drone")
@ProjectScoped
public class ArquillianDronePlugin implements Plugin {

	public static final String ARQ_CORE_VERSION_PROP_NAME = "version.arquillian_core";
	public static final String ARQ_CORE_VERSION_PROP = "${" + ARQ_CORE_VERSION_PROP_NAME + "}";

	public static final String ARQ_DRONE_VERSION_PROP_NAME = "version.arquillian_drone";
	public static final String ARQ_DRONE_VERSION_PROP = "${" + ARQ_DRONE_VERSION_PROP_NAME + "}";

	public static final String JUNIT_VERSION_PROP_NAME = "version.junit";
	public static final String JUNIT_VERSION_PROP = "${" + JUNIT_VERSION_PROP_NAME + "}";

	public static final String TESTNG_VERSION_PROP_NAME = "version.testng";
	public static final String TESTNG_VERSION_PROP = "${" + TESTNG_VERSION_PROP_NAME + "}";

	public static final String SELENIUM_VERSION_PROP_NAME = "version.selenium";
	public static final String SELENIUM_VERSION_PROP = "${" + SELENIUM_VERSION_PROP_NAME + "}";

	@Inject
	private Shell shell;

	@Inject
	private Project project;

	private DependencyFacet dependencyFacet;

	private String arquillianVersion;

	private String arquillianDroneVersion;
	
	private String seleniumVersion;

	@SetupCommand
	public void doSetup(@Option(name = "testframework", required = false, defaultValue = "junit") String testframework,
			@Option(name = "specify-selenium-version", required = false, defaultValue="false") boolean overrideSelenium) {

		// Install dependencies
		dependencyFacet = project.getFacet(DependencyFacet.class);
		
		if (overrideSelenium) {
			// needs to precede arquillian-drone-bom dependency to override selenium version 
			installSeleniumnBom();
		}		
		installArquillianBom();
		installArquillianDroneDependencies();

		if (testframework.equals("junit")) {
			installJunitDependencies();
		} else {
			installTestNgDependencies();
		}
		// default browser - HTML Unit
	}

	@Command(value = "configure-drone")
	private void config(@Option(name = "browser", required = false, defaultValue = "firefox") Browser browser,
			@Option(name = "remote-reusable", required = false, defaultValue="false") boolean remoteReusable,
			@Option(name = "remote-address", required = false, defaultValue="") String remoteAddress) {
		// Create arquillian.xml config file and set the chosen browser
		ResourceFacet resources = project.getFacet(ResourceFacet.class);
		FileResource<?> resource = (FileResource<?>) resources.getTestResourceFolder().getChild("arquillian.xml");
		Node xml = null;
		if (!resource.exists()) {
			xml = createNewArquillianConfig();
		} else {
			xml = XMLParser.parse(resource.getResourceInputStream());
		}

		setBrowser(xml, browser);
		if (remoteReusable) {
			setRemoteReusable(xml);
		}
		if (!remoteAddress.equals("")) {
			setRemoteAddress(xml, remoteAddress);
		}		

		resource.setContents(XMLParser.toXMLString(xml));
	}

	private void setBrowser(Node xml, Browser browser) {
		addPropertyToArquillianConfig(xml, "browserCapabilities", browser.toString());
		if (browser.equals(Browser.chrome)) {
			String chromdriverPath = shell.prompt("Enter path to chromdriver.exe");
			addPropertyToArquillianConfig(xml, "chromeDriverBinary", chromdriverPath);
		} 
	}

	private Node createNewArquillianConfig() {
		return XMLParser.parse("<arquillian " + "\nxmlns=\"http://jboss.org/schema/arquillian\" " + "\nxmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
				+ "\nxsi:schemaLocation=\"http://jboss.org/schema/arquillian " + "http://jboss.org/schema/arquillian/arquillian_1_0.xsd\"></arquillian>");
	}
	
	private void setRemoteReusable(Node xml) {
		addPropertyToArquillianConfig(xml, "remoteReusable", "true");
	}
	
	private void setRemoteAddress(Node xml, String remoteAddress) {
		// e.g. http://localhost:14444/wd/hub
		addPropertyToArquillianConfig(xml, "remoteAddress", remoteAddress);
	}

	private void addPropertyToArquillianConfig(Node xml, String propertyName, String value) {
		xml.getOrCreate("extension@qualifier=webdriver").getOrCreate("property@name=" + propertyName).text(value);
	}

	private void installJunitDependencies() {
		DependencyBuilder junitDependency = createJunitDependency();
		if (!dependencyFacet.hasEffectiveDependency(junitDependency)) {
			List<Dependency> dependencies = dependencyFacet.resolveAvailableVersions(junitDependency);
			Dependency dependency = shell.promptChoiceTyped("Which version of JUnit do you want to install?", dependencies,
					DependencyUtil.getLatestNonSnapshotVersion(dependencies));

			dependencyFacet.setProperty(JUNIT_VERSION_PROP_NAME, dependency.getVersion());
			dependencyFacet.addDirectDependency(DependencyBuilder.create(dependency).setVersion(JUNIT_VERSION_PROP));
		}

		DependencyBuilder junitArquillianDependency = createJunitArquillianDependency();
		if (!dependencyFacet.hasEffectiveDependency(junitArquillianDependency)) {
			dependencyFacet.addDirectDependency(junitArquillianDependency);
		}
	}

	private void installTestNgDependencies() {
		DependencyBuilder testngDependency = createTestNgDependency();
		if (!dependencyFacet.hasEffectiveDependency(testngDependency)) {
			List<Dependency> dependencies = dependencyFacet.resolveAvailableVersions(testngDependency);
			Dependency dependency = shell.promptChoiceTyped("Which version of TestNG do you want to install?", dependencies,
					DependencyUtil.getLatestNonSnapshotVersion(dependencies));

			dependencyFacet.setProperty(TESTNG_VERSION_PROP_NAME, dependency.getVersion());
			dependencyFacet.addDirectDependency(DependencyBuilder.create(dependency).setVersion(TESTNG_VERSION_PROP));
		}

		DependencyBuilder testNgArquillianDependency = createTestNgArquillianDependency();
		if (!dependencyFacet.hasEffectiveDependency(testNgArquillianDependency)) {
			dependencyFacet.addDirectDependency(testNgArquillianDependency);
		}
	}

	private void installArquillianBom() {
		DependencyBuilder arquillianBom = createArquillianBomDependency();

		arquillianVersion = dependencyFacet.getProperty(ARQ_CORE_VERSION_PROP_NAME);
		if (arquillianVersion == null) {
			List<Dependency> dependencies = dependencyFacet.resolveAvailableVersions(arquillianBom);
			Dependency dependency = shell.promptChoiceTyped("Which version of Arquillian do you want to install?", dependencies,
					DependencyUtil.getLatestNonSnapshotVersion(dependencies));
			arquillianVersion = dependency.getVersion();
			dependencyFacet.setProperty(ARQ_CORE_VERSION_PROP_NAME, arquillianVersion);
		}

		if (!dependencyFacet.hasDirectManagedDependency(arquillianBom)) {
			arquillianBom.setVersion(ARQ_CORE_VERSION_PROP);
			dependencyFacet.addDirectManagedDependency(arquillianBom);
		}
	}
	
	private void installSeleniumnBom() {
		DependencyBuilder seleniumBom = createSeleniumBomDependency();

		seleniumVersion = dependencyFacet.getProperty(SELENIUM_VERSION_PROP_NAME);
		if (seleniumVersion == null) {
			List<Dependency> dependencies = dependencyFacet.resolveAvailableVersions(seleniumBom);
			Dependency dependency = shell.promptChoiceTyped("Which version of Selenium do you want to install?", dependencies,
					DependencyUtil.getLatestNonSnapshotVersion(dependencies));
			seleniumVersion = dependency.getVersion();
			dependencyFacet.setProperty(SELENIUM_VERSION_PROP_NAME, seleniumVersion);
		}

		if (!dependencyFacet.hasDirectManagedDependency(seleniumBom)) {
			seleniumBom.setVersion(SELENIUM_VERSION_PROP);
			dependencyFacet.addDirectManagedDependency(seleniumBom);
		}
	}

	private void installArquillianDroneDependencies() {
		DependencyBuilder arquillianDroneBom = createArquillianDroneBomDependency();
		DependencyBuilder arquillianDroneWebdriverDependency = createArquillianDroneWebdriverDependency();

		arquillianDroneVersion = dependencyFacet.getProperty(ARQ_DRONE_VERSION_PROP_NAME);
		if (arquillianDroneVersion == null) {
			List<Dependency> dependencies = dependencyFacet.resolveAvailableVersions(arquillianDroneBom);
			Dependency dependency = shell.promptChoiceTyped("Which version of Arquillian Drone do you want to install?", dependencies,
					DependencyUtil.getLatestNonSnapshotVersion(dependencies));
			arquillianDroneVersion = dependency.getVersion();
			dependencyFacet.setProperty(ARQ_DRONE_VERSION_PROP_NAME, arquillianDroneVersion);
		}

		if (!dependencyFacet.hasDirectManagedDependency(arquillianDroneBom)) {
			arquillianDroneBom.setVersion(ARQ_DRONE_VERSION_PROP);
			dependencyFacet.addDirectManagedDependency(arquillianDroneBom);
		}

		if (!dependencyFacet.hasDirectManagedDependency(arquillianDroneWebdriverDependency)) {
			arquillianDroneWebdriverDependency.setVersion(ARQ_DRONE_VERSION_PROP);
			dependencyFacet.addDirectDependency(arquillianDroneWebdriverDependency);
		}

	}

	private DependencyBuilder createJunitDependency() {
		return DependencyBuilder.create().setGroupId("junit").setArtifactId("junit").setScopeType(ScopeType.TEST);
	}

	private DependencyBuilder createJunitArquillianDependency() {
		return DependencyBuilder.create().setGroupId("org.jboss.arquillian.junit").setArtifactId("arquillian-junit-container").setScopeType(ScopeType.TEST);
	}

	private DependencyBuilder createTestNgDependency() {
		return DependencyBuilder.create().setGroupId("org.testng").setArtifactId("testng").setScopeType(ScopeType.TEST);
	}

	private DependencyBuilder createTestNgArquillianDependency() {
		return DependencyBuilder.create().setGroupId("org.jboss.arquillian.testng").setArtifactId("arquillian-testng-container").setScopeType(ScopeType.TEST);
	}

	private DependencyBuilder createArquillianBomDependency() {
		return DependencyBuilder.create().setGroupId("org.jboss.arquillian").setArtifactId("arquillian-bom").setPackagingType("pom")
				.setScopeType(ScopeType.IMPORT);
	}

	private DependencyBuilder createArquillianDroneBomDependency() {
		return DependencyBuilder.create().setGroupId("org.jboss.arquillian.extension").setArtifactId("arquillian-drone-bom").setPackagingType("pom")
				.setScopeType(ScopeType.IMPORT);
	}

	private DependencyBuilder createArquillianDroneWebdriverDependency() {
		return DependencyBuilder.create().setGroupId("org.jboss.arquillian.extension").setArtifactId("arquillian-drone-webdriver-depchain").setPackagingType("pom")
				.setScopeType(ScopeType.TEST);
	}
	
	private DependencyBuilder createSeleniumBomDependency() {
		return DependencyBuilder.create().setGroupId("org.jboss.arquillian.selenium").setArtifactId("selenium-bom").setPackagingType("pom")
				.setScopeType(ScopeType.IMPORT);
	}

}
