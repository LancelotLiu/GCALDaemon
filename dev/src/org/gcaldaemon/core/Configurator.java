//
// GCALDaemon is an OS-independent Java program that offers two-way
// synchronization between Google Calendar and various iCalalendar (RFC 2445)
// compatible calendar applications (Sunbird, Rainlendar, iCal, Lightning, etc).
//
// Apache License
// Version 2.0, January 2004
// http://www.apache.org/licenses/
// 
// Project home:
// http://gcaldaemon.sourceforge.net
//
package org.gcaldaemon.core;

import java.awt.GraphicsEnvironment;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.TimeZone;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.util.CompatibilityHints;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.SimpleLayout;
import org.gcaldaemon.core.ldap.ContactLoader;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.logger.QuickWriter;

/**
 * Config loader, property setter, and listener starter object.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class Configurator {

	// --- COMMON CONSTANTS ---

	public static final String VERSION = "GCALDaemon V1.0 alpha 18, 2015/03/17";

	public static final byte MODE_DAEMON = 0;
	public static final byte MODE_RUNONCE = 1;
	public static final byte MODE_CONFIGEDITOR = 2;
	public static final byte MODE_EMBEDDED = 3;

	private static final int MAX_CACHE_SIZE = 100;
	private static final SimpleDateFormat BACKUP_FORMAT = new SimpleDateFormat(
			"yyyy-MM-dd");

	// --- SIMPLE CONFIG CONSTANTS ---

	public static final String REMOTE_DELETE_ENABLED = "remote.delete.enabled";
	public static final String FILE_POLLING_FILE = "file.polling.file";
	public static final String FILE_RELOADER_SCRIPT = "file.reloader.script";
	public static final String LDAP_VCARD_ENCODING = "ldap.vcard.encoding";
	public static final String MAILTERM_DIR_PATH = "mailterm.dir.path";
	public static final String FEED_CACHE_TIMEOUT = "feed.cache.timeout";
	public static final String FILE_ENABLED = "file.enabled";
	public static final String NOTIFIER_LOCAL_USERS = "notifier.local.users";
	public static final String HTTP_ALLOWED_ADDRESSES = "http.allowed.addresses";
	public static final String PROXY_PASSWORD = "proxy.password";
	public static final String PROXY_USERNAME = "proxy.username";
	public static final String HTTP_ENABLED = "http.enabled";
	public static final String SENDMAIL_ENABLED = "sendmail.enabled";
	public static final String LDAP_GOOGLE_PASSWORD = "ldap.google.password";
	public static final String LDAP_GOOGLE_USERNAME = "ldap.google.username";
	public static final String NOTIFIER_WINDOW_SOUND = "notifier.window.sound";
	public static final String ICAL_BACKUP_TIMEOUT = "ical.backup.timeout";
	public static final String SEND_INVITATIONS = "send.invitations";
	public static final String MAILTERM_GOOGLE_PASSWORD = "mailterm.google.password";
	public static final String MAILTERM_GOOGLE_USERNAME = "mailterm.google.username";
	public static final String LDAP_CACHE_TIMEOUT = "ldap.cache.timeout";
	public static final String MAILTERM_ALLOWED_ADDRESSES = "mailterm.allowed.addresses";
	public static final String PROXY_PORT = "proxy.port";
	public static final String FEED_DUPLICATION_FILTER = "feed.duplication.filter";
	public static final String FEED_ENABLED = "feed.enabled";
	public static final String LDAP_ALLOWED_HOSTNAMES = "ldap.allowed.hostnames";
	public static final String SENDMAIL_GOOGLE_PASSWORD = "sendmail.google.password";
	public static final String SENDMAIL_GOOGLE_USERNAME = "sendmail.google.username";
	public static final String LDAP_ENABLED = "ldap.enabled";
	public static final String MAILTERM_CONSOLE_ENCODING = "mailterm.console.encoding";
	public static final String NOTIFIER_WINDOW_STYLE = "notifier.window.style";
	public static final String LDAP_VCARD_VERSION = "ldap.vcard.version";
	public static final String SENDMAIL_DIR_PATH = "sendmail.dir.path";
	public static final String LOG_CONFIG = "log.config";
	public static final String HTTP_PORT = "http.port";
	public static final String MAILTERM_POLLING_GOOGLE = "mailterm.polling.google";
	public static final String LDAP_ALLOWED_ADDRESSES = "ldap.allowed.addresses";
	public static final String PROGRESS_ENABLED = "progress.enabled";
	public static final String MAILTERM_MAIL_SUBJECT = "mailterm.mail.subject";
	public static final String SENDMAIL_POLLING_DIR = "sendmail.polling.dir";
	public static final String FEED_EVENT_LENGTH = "feed.event.length";
	public static final String PROXY_HOST = "proxy.host";
	public static final String NOTIFIER_DATE_FORMAT = "notifier.date.format";
	public static final String EXTENDED_SYNC_ENABLED = "extended.sync.enabled";
	public static final String HTTP_ALLOWED_HOSTNAMES = "http.allowed.hostnames";
	public static final String FILE_OFFLINE_ENABLED = "file.offline.enabled";
	public static final String MAILTERM_ENABLED = "mailterm.enabled";
	public static final String NOTIFIER_GOOGLE_PASSWORD = "notifier.google.password";
	public static final String NOTIFIER_POLLING_MAILBOX = "notifier.polling.mailbox";
	public static final String NOTIFIER_GOOGLE_USERNAME = "notifier.google.username";
	public static final String NOTIFIER_ENABLED = "notifier.enabled";
	public static final String CACHE_TIMEOUT = "cache.timeout";
	public static final String FILE_POLLING_GOOGLE = "file.polling.google";
	public static final String LDAP_PORT = "ldap.port";
	public static final String EDITOR_LANGUAGE = "editor.language";
	public static final String EDITOR_LOOK_AND_FEEL = "editor.look.and.feel";
	public static final String WORK_DIR = "work.dir";
	public static final String REMOTE_ALARM_TYPES = "remote.alarm.types";

	// --- FILE CONFIG CONSTANTS ---

	public static final String FILE_PRIVATE_ICAL_URL = "file.private.ical.url";
	public static final String FILE_ICAL_PATH = "file.ical.path";
	public static final String FILE_GOOGLE_USERNAME = "file.google.username";
	public static final String FILE_GOOGLE_PASSWORD = "file.google.password";

	// --- UTILS ---

	private Properties config = new Properties();

	private final HashMap toDoCache = new HashMap();
	private final HashSet backupFiles = new HashSet();
	private final File workDirectory;
	private final long calendarCacheTimeout;
	private final boolean standaloneMode;
	private final byte mode;
	private final long backupTimeout;

	private long backupLastVerified;
	private File configFile;

	// --- SERVICES AND LISTENERS ---

	private Thread synchronizer;
	private Thread gmailPool;

	private Thread servletListener;
	private Thread httpListener;
	private Thread fileListener;
	private Thread contactLoader;
	private Thread mailNotifier;
	private Thread sendMail;
	private Thread mailTerm;

	// --- FEED CONVERTER'S PARAMETERS ---

	protected final boolean feedEnabled;
	protected final long feedCacheTimeout;
	protected final long feedEventLength;
	protected final double duplicationRatio;

	// --- CONSTRUCTOR ---

	public Configurator(String configPath, Properties properties,
			boolean userHome, byte mode) throws Exception {
		this.mode = mode;
		int i;
		File programRootDir = null;
		if (mode == MODE_EMBEDDED) {

			// Embedded mode
			standaloneMode = false;
			config = properties;
			String workPath = getConfigProperty(WORK_DIR, null);
			workDirectory = new File(workPath);
		} else {

			// Load config
			if (configPath != null) {
				configFile = new File(configPath);
			}
			InputStream in = null;
			boolean configInClassPath = false;
			if (configFile == null || !configFile.isFile()) {
				try {
					in = Configurator.class
							.getResourceAsStream("/gcal-daemon.cfg");
					configInClassPath = in != null;
				} catch (Exception ignored) {
					in = null;
				}
				if (in == null) {
					System.out
							.println("INFO  | Searching main configuration file...");
					String path = (new File("x")).getAbsolutePath().replace(
							'\\', '/');
					i = path.lastIndexOf('/');
					if (i > 1) {
						i = path.lastIndexOf('/', i - 1);
						if (i > 1) {
							configFile = new File(path.substring(0, i),
									"conf/gcal-daemon.cfg");
						}
					}
					if (configFile == null || !configFile.isFile()) {
						configFile = new File(
								"/usr/local/sbin/GCALDaemon/conf/gcal-daemon.cfg");
					}
					if (configFile == null || !configFile.isFile()) {
						configFile = new File(
								"/GCALDaemon/conf/gcal-daemon.cfg");
					}					
					if (configFile == null || !configFile.isFile()) {
						File root = new File("/");
						String[] dirs = root.list();
						if (dirs != null) {
							for (i = 0; i < dirs.length; i++) {
								configFile = new File('/' + dirs[i]
										+ "/GCALDaemon/conf/gcal-daemon.cfg");
								if (configFile.isFile()) {
									break;
								}
							}
						}
					}
					if (configFile == null || !configFile.isFile()) {
						throw new FileNotFoundException(
								"Missing main configuration file: "
										+ configPath);
					}
					if (!userHome) {

						// Open global config file
						in = new FileInputStream(configFile);
					}
				}
			} else {
				if (!userHome) {

					// Open global config file
					in = new FileInputStream(configFile);
				}
			}
			standaloneMode = !configInClassPath;
			if (in != null) {

				// Load global config file
				config.load(new BufferedInputStream(in));
				in.close();
			}

			// Loading config from classpath
			if (configFile == null) {
				try {
					URL url = Configurator.class
							.getResource("/gcal-daemon.cfg");
					configFile = new File(url.getFile());
				} catch (Exception ignored) {
				}
			}
			programRootDir = configFile.getParentFile().getParentFile();
			System.setProperty("gcaldaemon.program.dir", programRootDir
					.getAbsolutePath());
			String workPath = getConfigProperty(WORK_DIR, null);
			File directory;
			if (workPath == null) {
				directory = new File(programRootDir, "work");
			} else {
				directory = new File(workPath);
			}
			if (!directory.isDirectory()) {
				if (!directory.mkdirs()) {
					directory = new File("work");
					directory.mkdirs();
				}
			}
			workDirectory = directory;

			// User-specific config file handler
			if (userHome) {
				boolean useGlobal = true;
				try {
					String home = System.getProperty("user.home", null);
					if (home != null) {
						File userConfig = new File(home,
								".gcaldaemon/gcal-daemon.cfg");
						if (!userConfig.isFile()) {

							// Create new user-specific config
							File userDir = new File(home, ".gcaldaemon");
							userDir.mkdirs();
							copyFile(configFile, userConfig);
							if (!userConfig.isFile()) {
								userConfig.delete();
								userDir.delete();
							}
						}
						if (userConfig.isFile()) {

							// Load user-specific config
							configFile = userConfig;
							in = new FileInputStream(configFile);
							config.load(new BufferedInputStream(in));
							in.close();
							useGlobal = false;
						}
					}
				} catch (Exception ignored) {
				}
				if (useGlobal) {

					// Load global config file
					config.load(new BufferedInputStream(in));
					in.close();
				}
			}
		}

		// Init logger
		ProgressMonitor monitor = null;
		if (standaloneMode && mode != MODE_CONFIGEDITOR) {

			// Compute log config path
			String logConfig = getConfigProperty(LOG_CONFIG,
					"logger-config.cfg");
			logConfig = logConfig.replace('\\', '/');
			File logConfigFile;
			if (logConfig.indexOf('/') == -1) {
				logConfigFile = new File(programRootDir, "conf/" + logConfig);
			} else {
				logConfigFile = new File(logConfig);
			}
			if (logConfigFile.isFile()) {
				String logConfigPath = logConfigFile.getAbsolutePath();
				System.setProperty("org.apache.commons.logging.Log",
						"org.apache.commons.logging.impl.Log4JLogger");
				System.setProperty("log4j.defaultInitOverride", "false");
				System.setProperty("log4j.configuration", logConfigPath);
				try {
					PropertyConfigurator.configure(logConfigPath);
				} catch (Throwable ignored) {
				}
			}
		}
		if (mode == MODE_CONFIGEDITOR) {

			// Show monitor
			try {
				monitor = new ProgressMonitor();
				monitor.setVisible(true);
				Thread.sleep(400);
			} catch (Exception ignored) {
			}

			// Init simple logger
			try {
				System.setProperty("log4j.defaultInitOverride", "false");
				Logger root = Logger.getRootLogger();
				root.removeAllAppenders();
				root.addAppender(new ConsoleAppender(new SimpleLayout()));
				root.setLevel(Level.INFO);
			} catch (Throwable ingored) {
			}
		}

		// Disable unnecessary INFO messages of the GData API
		try {
			java.util.logging.Logger logger = java.util.logging.Logger
					.getLogger("com.google");
			logger.setLevel(java.util.logging.Level.WARNING);
		} catch (Throwable ingored) {
		}

		Log log = LogFactory.getLog(Configurator.class);
		log.info(VERSION + " starting...");
		if (configFile != null && log.isDebugEnabled()) {
			log.debug("Config loaded successfully (" + configFile + ").");
		}
		
		// Check Java version
		double jvmVersion = 1.5;
		try {
			jvmVersion = Float.valueOf(
					System.getProperty("java.version", "1.5").substring(0, 3))
					.floatValue();
		} catch (Exception ignored) {
		}
		if (jvmVersion < 1.5) {
			log
					.fatal("GCALDaemon requires at least Java 1.5! Current version: "
							+ System.getProperty("java.version"));
			throw new Exception("Invalid JVM version!");
		}

		// Check permission
		if (workDirectory.isDirectory() && !workDirectory.canWrite()) {
			if (System.getProperty("os.name", "unknown").toLowerCase().indexOf(
					"windows") == -1) {
				String path = workDirectory.getCanonicalPath();
				if (programRootDir != null) {
					path = programRootDir.getCanonicalPath();
				}
				log.warn("Please check the file permissions on the '"
						+ workDirectory.getCanonicalPath() + "' folder!\r\n"
						+ "Hint: [sudo] chmod -R 777 " + path);
			}
		}

		// Disable all ICS file syntax validators
		CompatibilityHints.setHintEnabled(
				CompatibilityHints.KEY_RELAXED_PARSING, true);
		CompatibilityHints.setHintEnabled(
				CompatibilityHints.KEY_RELAXED_VALIDATION, true);
		CompatibilityHints.setHintEnabled(
				CompatibilityHints.KEY_RELAXED_UNFOLDING, true);
		CompatibilityHints.setHintEnabled(
				CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true);
		CompatibilityHints.setHintEnabled(
				CompatibilityHints.KEY_NOTES_COMPATIBILITY, true);

		// Disable SSL validation
		try {

			// Create a trust manager that does not validate certificate chains
			javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] { new javax.net.ssl.X509TrustManager() {

				public final java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				public final void checkClientTrusted(
						java.security.cert.X509Certificate[] certs,
						String authType) {
				}

				public final void checkServerTrusted(
						java.security.cert.X509Certificate[] certs,
						String authType) {
				}
			} };

			// Install the all-trusting trust manager
			javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext
					.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc
					.getSocketFactory());
		} catch (Throwable ignored) {
		}

		// Replace hostname verifier
		try {
			javax.net.ssl.HostnameVerifier hv[] = new javax.net.ssl.HostnameVerifier[] { new javax.net.ssl.HostnameVerifier() {

				public final boolean verify(String hostName,
						javax.net.ssl.SSLSession session) {
					return true;
				}
			} };
			javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(hv[0]);
		} catch (Throwable ignored) {
		}

		// Setup proxy
		String proxyHost = getConfigProperty(PROXY_HOST, null);
		if (proxyHost != null) {
			String proxyPort = getConfigProperty(PROXY_PORT, null);
			if (proxyPort == null) {
				log.warn("Missing 'proxy.port' configuration property!");
			} else {

				// HTTP proxy server properties
				System.setProperty("http.proxyHost", proxyHost);
				System.setProperty("http.proxyPort", proxyPort);
				System.setProperty("http.proxySet", "true");

				// HTTPS proxy server properties
				System.setProperty("https.proxyHost", proxyHost);
				System.setProperty("https.proxyPort", proxyPort);
				System.setProperty("https.proxySet", "true");

				// Setup proxy credentials
				String username = getConfigProperty(PROXY_USERNAME, null);
				String encodedPassword = getConfigProperty(PROXY_PASSWORD, null);
				if (username != null) {
					if (encodedPassword == null) {
						log
								.warn("Missing 'proxy.password' configuration property!");
					} else {
						String password = StringUtils
								.decodePassword(encodedPassword);

						// HTTP auth credentials
						System.setProperty("http.proxyUser", username);
						System.setProperty("http.proxyUserName", username);
						System.setProperty("http.proxyPassword", password);

						// HTTPS auth credentials
						System.setProperty("https.proxyUser", username);
						System.setProperty("https.proxyUserName", username);
						System.setProperty("https.proxyPassword", password);
					}
				}
			}
		}

		// Get iCal cache timeout
		long timeout = getConfigProperty(CACHE_TIMEOUT, 180000L);
		if (timeout < 60000L) {
			log.warn("The enabled minimal cache timeout is '1 min'!");
			timeout = 60000L;
		}
		calendarCacheTimeout = timeout;

		// Get backup file timeout
		timeout = getConfigProperty(ICAL_BACKUP_TIMEOUT, 604800000L);
		if (timeout < 86400000L && timeout != 0) {
			log.warn("The enabled minimal backup timeout is '1 day'!");
			timeout = 86400000L;
		}
		backupTimeout = timeout;

		// Get extended syncronization mode (alarms, url, category, etc)
		boolean enable = getConfigProperty(EXTENDED_SYNC_ENABLED, false);
		System
				.setProperty("gcaldaemon.extended.sync", Boolean
						.toString(enable));
		if (enable) {
			log.info("Extended synchronization enabled.");
		}

		// Google send an email to the attendees to invite them to attend
		enable = getConfigProperty(SEND_INVITATIONS, false);
		System.setProperty("gcaldaemon.send.invitations", Boolean
				.toString(enable));

		// Enabled alarm types in the Google Calendar (e.g. 'sms,popup,email')
		System.setProperty("gcaldaemon.remote.alarms", getConfigProperty(
				REMOTE_ALARM_TYPES, "email,sms,popup"));

		// Get parameters of the feed to iCal converter
		feedEnabled = getConfigProperty(FEED_ENABLED, true);
		feedEventLength = getConfigProperty(FEED_EVENT_LENGTH, 2700000L);
		timeout = getConfigProperty(FEED_CACHE_TIMEOUT, 3600000L);
		if (timeout < 60000L) {
			log.warn("The enabled minimal feed timeout is '1 min'!");
			timeout = 60000L;
		}
		feedCacheTimeout = timeout;
		if (feedEnabled) {
			log.info("RSS/ATOM feed converter enabled.");
		} else {
			log.info("RSS/ATOM feed converter disabled.");
		}

		// Get feed event duplication ratio
		String percent = getConfigProperty(FEED_DUPLICATION_FILTER, "70")
				.trim();
		if (percent.endsWith("%")) {
			percent = percent.substring(0, percent.length() - 1).trim();
		}
		double ratio = Double.parseDouble(percent) / 100;
		if (ratio < 0.4) {
			ratio = 0.4;
			log.warn("The smallest enabled filter percent is '40%'!");
		} else {
			if (ratio > 1) {
				log.warn("The largest filter percent is '100%'!");
				ratio = 1;
			}
		}
		duplicationRatio = ratio;
		if (feedEnabled) {
			if (duplicationRatio == 1) {
				log.debug("Duplication filter disabled.");
			} else {
				log.debug("Sensibility of the duplication filter is " + percent
						+ "%.");
			}
		}

		// Delete backup files
		if (backupTimeout == 0) {
			File backupDirectory = new File(workDirectory, "backup");
			if (backupDirectory.isDirectory()) {
				File[] backups = backupDirectory.listFiles();
				if (backups != null && backups.length != 0) {
					for (i = 0; i < backups.length; i++) {
						backups[i].delete();
					}
				}
			}
		}

		// Displays time zone
		log.info("Local time zone is " + TimeZone.getDefault().getDisplayName()
				+ ".");

		// Get main thread group
		ThreadGroup mainGroup = Thread.currentThread().getThreadGroup();
		while (mainGroup.getParent() != null) {
			mainGroup = mainGroup.getParent();
		}

		// Configurator mode - launch ConfigTool's window
		if (mode == MODE_CONFIGEDITOR) {
			synchronizer = new Synchronizer(mainGroup, this);
			gmailPool = startService(log, mainGroup,
					"org.gcaldaemon.core.GmailPool");
			new ConfigEditor(this, monitor);
			return;
		}

		// Init synchronizer
		boolean enableHTTP = getConfigProperty(HTTP_ENABLED, true);
		boolean enableFile = getConfigProperty(FILE_ENABLED, false);
		if (enableHTTP || enableFile || !standaloneMode) {
			synchronizer = new Synchronizer(mainGroup, this);
			if (mode == MODE_EMBEDDED) {
				return;
			}
		}

		// On demand mode - run once then quit
		if (mode == MODE_RUNONCE) {
			fileListener = startService(log, mainGroup,
					"org.gcaldaemon.core.file.OfflineFileListener");
			return;
		}

		// Init Gmail pool
		boolean enableLDAP = getConfigProperty(LDAP_ENABLED, false);
		boolean enableSendMail = getConfigProperty(SENDMAIL_ENABLED, false);
		boolean enableMailTerm = getConfigProperty(MAILTERM_ENABLED, false);
		if (enableLDAP || enableSendMail || enableMailTerm) {
			gmailPool = startService(log, mainGroup,
					"org.gcaldaemon.core.GmailPool");
		}

		if (standaloneMode) {

			// Init HTTP listener
			if (enableHTTP) {
				httpListener = startService(log, mainGroup,
						"org.gcaldaemon.core.http.HTTPListener");
			} else {
				log.info("HTTP server disabled.");
			}
		} else {

			// Init J2EE servlet listener
			servletListener = startService(log, mainGroup,
					"org.gcaldaemon.core.servlet.ServletListener");
		}

		// Init file listener
		if (enableFile) {
			if (getConfigProperty(FILE_OFFLINE_ENABLED, true)) {
				fileListener = startService(log, mainGroup,
						"org.gcaldaemon.core.file.OfflineFileListener");
			} else {
				fileListener = startService(log, mainGroup,
						"org.gcaldaemon.core.file.OnlineFileListener");
			}
		} else {
			if (standaloneMode) {
				log.info("File listener disabled.");
			}
		}

		// Init LDAP listener
		if (enableLDAP) {
			contactLoader = startService(log, mainGroup,
					"org.gcaldaemon.core.ldap.ContactLoader");
		} else {
			if (standaloneMode) {
				log.info("LDAP server disabled.");
			}
		}

		// Init Gmail notifier
		if (getConfigProperty(NOTIFIER_ENABLED, false)) {
			if (GraphicsEnvironment.isHeadless()) {
				log.warn("Unable to use Gmail notifier in headless mode!");
			} else {
				mailNotifier = startService(log, mainGroup,
						"org.gcaldaemon.core.notifier.GmailNotifier");
			}
		} else {
			if (standaloneMode) {
				log.info("Gmail notifier disabled.");
			}
		}

		// Init sendmail service
		if (enableSendMail) {
			sendMail = startService(log, mainGroup,
					"org.gcaldaemon.core.sendmail.SendMail");
		} else {
			if (standaloneMode) {
				log.info("Sendmail service disabled.");
			}
		}

		// Init mailterm service
		if (enableMailTerm) {
			mailTerm = startService(log, mainGroup,
					"org.gcaldaemon.core.mailterm.MailTerminal");
		} else {
			if (standaloneMode) {
				log.info("Mail terminal disabled.");
			}
		}

		// Clear configuration holder
		config.clear();
	}

	private final Thread startService(Log log, ThreadGroup group, String name)
			throws Exception {
		try {
			Class serviceClass = Class.forName(name);
			Class[] types = new Class[2];
			types[0] = ThreadGroup.class;
			types[1] = Configurator.class;
			Constructor constructor = serviceClass.getConstructor(types);
			Object[] values = new Object[2];
			values[0] = group;
			values[1] = this;
			return (Thread) constructor.newInstance(values);
		} catch (Exception configError) {
			String message = configError.getMessage();
			Throwable cause = configError.getCause();
			while (cause != null) {
				if (cause.getMessage() != null) {
					message = cause.getMessage();
				}
				cause = cause.getCause();
			}
			log.fatal(message.toUpperCase(), configError);
			throw configError;
		}
	}

	public final byte getRunMode() {
		return mode;
	}

	public final File getConfigFile() {
		return configFile;
	}

	public static final void copyFile(File from, File to) throws Exception {
		if (from == null || to == null || !from.exists()) {
			return;
		}
		RandomAccessFile fromFile = null;
		RandomAccessFile toFile = null;
		try {
			fromFile = new RandomAccessFile(from, "r");
			toFile = new RandomAccessFile(to, "rw");
			FileChannel fromChannel = fromFile.getChannel();
			FileChannel toChannel = toFile.getChannel();
			long length = fromFile.length();
			long start = 0;
			while (start < length) {
				start += fromChannel.transferTo(start, length - start,
						toChannel);
			}
			fromChannel.close();
			toChannel.close();
		} finally {
			if (fromFile != null) {
				fromFile.close();
			}
			if (toFile != null) {
				toFile.close();
			}
		}
	}

	// --- COMMON CONFIGURATION PROPERTY GETTERS ---

	public final String getConfigProperty(String name, String defaultValue) {
		String value = config.getProperty(name, defaultValue);
		if (value == null) {
			return defaultValue;
		} else {
			value = value.trim();
			if (value.length() == 0) {
				return defaultValue;
			}
		}
		return value;
	}

	public final boolean getConfigProperty(String name, boolean defaultValue) {
		String bool = config.getProperty(name, Boolean.toString(defaultValue))
				.toLowerCase();
		return "true".equals(bool) || "on".equals(bool) || "1".equals(bool);
	}

	public final long getConfigProperty(String name, long defaultValue)
			throws Exception {
		String number = config.getProperty(name, Long.toString(defaultValue));
		try {
			return StringUtils.stringToLong(number);
		} catch (Exception malformed) {
			throw new IllegalArgumentException("Malformed numeric parameter ("
					+ name + ")!");
		}
	}

	public final FilterMask[] getFilterProperty(String name) throws Exception {
		return getFilterProperty(name, false);
	}

	public final FilterMask[] getFilterProperty(String name, boolean ignoreCase)
			throws Exception {
		String list = config.getProperty(name, null);
		try {
			return StringUtils.splitMaskList(list, ignoreCase);
		} catch (Exception malformed) {
			throw new IllegalArgumentException("Malformed mask list (" + name
					+ ")!");
		}
	}

	public final String getPasswordProperty(String name) throws Exception {
		String encodedPassword = config.getProperty(name, null);
		if (encodedPassword == null) {
			throw new IllegalArgumentException("Missing password (" + name
					+ ")!");
		}
		try {
			return StringUtils.decodePassword(encodedPassword);
		} catch (Exception malformed) {
			throw new IllegalArgumentException("Malformed password (" + name
					+ ")!");
		}
	}

	// --- GLOBAL CALENDAR CACHE ---

	private final HashMap calendarCache = new HashMap();

	public final synchronized void calendarChanged(Request request)
			throws Exception {

		// Find error marker
		String content = StringUtils.decodeToString(request.body,
				StringUtils.UTF_8);
		if (content.indexOf(GCalUtilities.ERROR_MARKER) != -1) {
			return;
		}

		// Save to-do block
		String toDoBlock = saveToDoBlock(request, content);

		// Store previous ics file
		boolean isSyncJob = request.url.endsWith(".ics");
		CachedCalendar newCalendar = new CachedCalendar();
		newCalendar.body = request.body;
		newCalendar.lastModified = System.currentTimeMillis();
		if (isSyncJob || !feedEnabled) {
			CachedCalendar oldCalendar = (CachedCalendar) calendarCache
					.get(request.url);

			// Set ical bytes
			if (oldCalendar != null
					&& newCalendar.lastModified - oldCalendar.lastModified < calendarCacheTimeout) {

				// Use cached ics file
				newCalendar.previousBody = oldCalendar.body;
			} else {

				// Load original ics file
				newCalendar.previousBody = GCalUtilities.loadCalendar(request);
			}

			// Verify ics file
			char[] chars = new char[Math.min(newCalendar.previousBody.length,
					100)];
			for (int i = 0; i < chars.length; i++) {
				chars[i] = (char) newCalendar.previousBody[i];
			}
			if ((new String(chars)).indexOf(GCalUtilities.ERROR_MARKER) != -1) {
				return;
			}
		}

		// Store other properties
		newCalendar.method = request.method;
		newCalendar.url = request.url;
		newCalendar.username = request.username;
		newCalendar.password = request.password;
		newCalendar.toDoBlock = toDoBlock;
		if (calendarCache.size() >= MAX_CACHE_SIZE) {
			calendarCache.clear();
		}
		calendarCache.put(request.url, newCalendar);

		// Start synchronization
		if (isSyncJob) {
			((Synchronizer) synchronizer).calendarChanged(newCalendar);
		}

		// Notify file listener (save new calendar file)
		if (request.method != null && fileListener != null) {
			Method wakeUp = fileListener.getClass().getMethod("wakeUp",
					new Class[0]);
			wakeUp.invoke(fileListener, new Object[0]);
		}
	}

	public final synchronized CachedCalendar getCalendar(Request request)
			throws Exception {
		CachedCalendar calendar = (CachedCalendar) calendarCache
				.get(request.url);
		boolean isSyncJob = request.url.endsWith(".ics");
		long now = System.currentTimeMillis();
		if (calendar != null) {
			long timeOut = feedCacheTimeout;
			if (isSyncJob) {
				timeOut = calendarCacheTimeout;
			}
			if (now - calendar.lastModified >= timeOut) {
				calendarCache.remove(request.url);
			} else {

				// Return calendar from cache
				return calendar;
			}
		}
		calendar = new CachedCalendar();

		if (isSyncJob || !feedEnabled) {

			// Load calendar from Google
			calendar.body = GCalUtilities.loadCalendar(request);
		} else {

			// Load feed
			String feedURL = request.url;
			if (!feedURL.startsWith("http")) {
				feedURL = "http:/" + feedURL;
			}
			calendar = FeedUtilities.getFeedAsCalendar(feedURL, calendarCache,
					feedEventLength, duplicationRatio, request.username,
					request.password);
			calendar.lastModified = now;
		}
		if (calendarCache.size() >= MAX_CACHE_SIZE) {
			calendarCache.clear();
		}

		// Load todo block
		calendar.toDoBlock = loadToDoBlock(request);
		calendar.filePath = request.filePath;
		calendar.lastModified = now;
		calendarCache.put(request.url, calendar);
		if (backupTimeout != 0 && isSyncJob) {

			// Do the daily backup
			calendar.url = request.url;
			if (now - backupLastVerified > 3600000L) {
				backupLastVerified = now;
				backupFiles.clear();
			}
			if (!backupFiles.contains(request.url)) {
				backupFiles.add(request.url);
				manageBackups(calendar, now);
			}
		}
		return calendar;
	}

	// --- ON-DEMAND SYNCHRONIZER ---

	public final synchronized void synchronizeNow(Request request)
			throws Exception {

		// Find error marker
		String content = StringUtils.decodeToString(request.body,
				StringUtils.UTF_8);
		if (content.indexOf(GCalUtilities.ERROR_MARKER) != -1) {
			return;
		}

		// Save to-do block
		String toDoBlock = saveToDoBlock(request, content);

		// Create calendar container
		long now = System.currentTimeMillis();
		CachedCalendar calendar = new CachedCalendar();
		calendar.body = request.body;
		calendar.lastModified = now;

		boolean isSyncJob = request.url.endsWith(".ics");
		if (isSyncJob) {

			// Load calendar from Google
			calendar.previousBody = GCalUtilities.loadCalendar(request);
		} else {

			// Find feed in cache
			if (feedEnabled) {
				calendar = (CachedCalendar) calendarCache.get(request.url);
				if (calendar != null) {
					if (now - calendar.lastModified >= feedCacheTimeout) {
						calendarCache.remove(request.url);
						calendar = null;
					}
				}
				if (calendar == null) {

					// Load feed
					String feedURL = request.url;
					if (!feedURL.startsWith("http")) {
						feedURL = "http:/" + feedURL;
					}
					calendar = FeedUtilities.getFeedAsCalendar(feedURL,
							calendarCache, feedEventLength, duplicationRatio,
							request.username, request.password);
					calendar.lastModified = now;
				}
			} else {
				throw new Exception("Invalid private ical URL (" + request.url
						+ ")!");
			}
		}

		// Verify loaded ics file
		char[] chars = new char[Math.min(calendar.previousBody.length, 100)];
		for (int i = 0; i < chars.length; i++) {
			chars[i] = (char) calendar.previousBody[i];
		}
		if ((new String(chars)).indexOf(GCalUtilities.ERROR_MARKER) != -1) {
			return;
		}

		// Store other properties
		calendar.username = request.username;
		calendar.password = request.password;
		calendar.filePath = request.filePath;
		calendar.method = request.method;
		calendar.url = request.url;
		calendar.toDoBlock = toDoBlock;
		if (calendarCache.size() >= MAX_CACHE_SIZE) {
			calendarCache.clear();
		}
		calendarCache.put(request.url, calendar);

		// Do synchronization
		if (isSyncJob) {
			calendar.body = ((Synchronizer) synchronizer)
					.syncronizeNow(calendar);

			// Load todo block
			calendar.toDoBlock = loadToDoBlock(request);
		}

		// Do the daily backup
		if (backupTimeout != 0 && isSyncJob) {
			calendar.url = request.url;
			if (now - backupLastVerified > 3600000L) {
				backupLastVerified = now;
				backupFiles.clear();
			}
			if (!backupFiles.contains(request.url)) {
				backupFiles.add(request.url);
				manageBackups(calendar, now);
			}
		}

		// Notify file listener (save new calendar file)
		if (request.method != null && fileListener != null) {
			Method wakeUp = fileListener.getClass().getMethod("wakeUp",
					new Class[0]);
			wakeUp.invoke(fileListener, new Object[0]);
		}
	}

	// --- BACKUP HANDLER ---

	private final void manageBackups(CachedCalendar calendar, long now)
			throws Exception {

		// Get backup dir
		File backupDirectory = new File(workDirectory, "backup");
		if (!backupDirectory.isDirectory()) {
			backupDirectory.mkdirs();
		}

		// Cleanup backup directory
		if (backupFiles.size() == 1) {
			String[] files = backupDirectory.list();
			File backup;
			for (int i = 0; i < files.length; i++) {
				backup = new File(backupDirectory, files[i]);
				if (now - backup.lastModified() > backupTimeout) {
					backup.delete();
				}
			}
		}

		// Generate backup file names (2007-05-12-ical-3947856328.bak)
		String hashCode = Long.toString(Math.abs(calendar.url.hashCode()));
		String date = BACKUP_FORMAT.format(new Date(now));
		String icalFileName = date + "-ical-" + hashCode + ".ics";
		String gcalFileName = date + "-gcal-" + hashCode + ".ics";
		File icalBackupFile = new File(backupDirectory, icalFileName);
		File gcalBackupFile = new File(backupDirectory, gcalFileName);

		// Save Google backup
		byte[] bytes = calendar.toByteArray();
		saveBackup(gcalBackupFile, bytes);

		// Save local backup
		if (calendar.filePath == null) {
			return;
		}
		File localFile = new File(calendar.filePath);
		if (!localFile.isFile()) {
			return;
		}
		if (icalBackupFile.exists()) {
			return;
		}
		RandomAccessFile in = null;
		try {
			in = new RandomAccessFile(localFile, "r");
			bytes = new byte[(int) localFile.length()];
			in.readFully(bytes);
			in.close();
			saveBackup(icalBackupFile, bytes);
		} catch (Exception ioException) {
			if (in != null) {
				in.close();
			}
		}
	}

	private static final void saveBackup(File backup, byte[] bytes) {
		if (!backup.exists()) {
			FileOutputStream out = null;
			try {
				if (bytes == null) {
					return;
				}
				char[] header = new char[Math.min(bytes.length, 1024)];
				for (int i = 0; i < header.length; i++) {
					header[i] = (char) bytes[i];
				}
				String test = new String(header);
				if (test.indexOf(GCalUtilities.ERROR_MARKER) != -1) {
					return;
				}
				out = new FileOutputStream(backup);
				out.write(bytes);
				out.flush();
				out.close();
			} catch (Exception ioException) {
				if (out != null) {
					try {
						out.close();
					} catch (Exception ignored) {
					}
				}
			}
		}
	}

	// --- TO-DO HANDLERS ---

	private final String saveToDoBlock(Request request, String content)
			throws Exception {
		int s = content.indexOf(Component.VTODO);
		int e = content.lastIndexOf(Component.VTODO);
		if (s == -1 || e == -1) {
			getToDoFile(request).delete();
			toDoCache.remove(request.url);
			return null;
		}
		content = content.substring(s, e);

		// Crop todo block from ical file
		String toDoBlock;
		if (content.indexOf(Component.VEVENT) == -1) {

			// Fast solution
			toDoBlock = "BEGIN:" + content + "VTODO\r\n";
		} else {

			// Slow and safe solution
			Calendar calendar = ICalUtilities.parseCalendar(request.body);
			VToDo[] toDoArray = ICalUtilities.getToDos(calendar);
			QuickWriter writer = new QuickWriter();
			for (int i = 0; i < toDoArray.length; i++) {
				writer.write(toDoArray[i].toString());
			}
			toDoBlock = writer.toString();
		}

		// Compare with cached instance
		if (toDoBlock.equals(toDoCache.get(request.url))) {
			return toDoBlock;
		}

		// Save block
		toDoCache.put(request.url, toDoBlock);
		byte[] toDoBytes = StringUtils.encodeString(toDoBlock,
				StringUtils.UTF_8);
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(getToDoFile(request));
			fos.write(toDoBytes);
		} finally {
			if (fos != null) {
				fos.close();
			}
		}
		return toDoBlock;
	}

	private final String loadToDoBlock(Request request) throws Exception {
		String toDoBlock = (String) toDoCache.get(request.url);
		if (toDoBlock != null) {
			return toDoBlock;
		}
		File file = getToDoFile(request);
		if (file.exists()) {
			RandomAccessFile raf = null;
			try {
				raf = new RandomAccessFile(file, "r");
				byte[] bytes = new byte[(int) raf.length()];
				raf.readFully(bytes);
				raf.close();
				toDoBlock = StringUtils
						.decodeToString(bytes, StringUtils.UTF_8);
				if (toDoCache.size() > MAX_CACHE_SIZE) {
					toDoCache.clear();
				}
				toDoCache.put(request.url, toDoBlock);
				return toDoBlock;
			} catch (Exception ioError) {
				try {
					raf.close();
				} catch (Exception ignored) {
				}
				if (file != null) {
					file.delete();
				}
			}
		}
		return null;
	}

	private final File getToDoFile(Request request) throws Exception {
		String hash = Integer.toHexString(request.url.hashCode());
		String prefix;
		if (request.url.endsWith(".ics")) {
			prefix = "gcal";
			int e = request.url.indexOf('%');
			if (e != -1) {
				int s = request.url.lastIndexOf('/', e);
				if (s != -1) {
					prefix = request.url.substring(s, e).replace('.', '-')
							.replace('_', '-');
				}
			}
		} else {
			prefix = request.url.replace('/', ' ').replace(':', ' ').replace(
					'.', ' ');
			if (prefix.startsWith("http")) {
				prefix = prefix.substring(4);
			}
			if (prefix.startsWith("s ")) {
				prefix = prefix.substring(2);
			}
			prefix = prefix.trim();
			if (prefix.startsWith("www ")) {
				prefix = prefix.substring(4);
			}
			prefix = prefix.trim();
			int e = prefix.indexOf(' ');
			if (e != -1) {
				prefix = prefix.substring(0, e);
			}
		}
		File todoDirectory = new File(workDirectory, "todo");
		if (!todoDirectory.isDirectory()) {
			todoDirectory.mkdirs();
		}
		return new File(todoDirectory, prefix + '-' + hash + ".ics");
	}

	public final File getWorkDirectory() {
		return workDirectory;
	}

	// --- GMAIL ADDRESS BOOK ---

	private volatile boolean started = false;

	public final GmailContact[] getAddressBook() throws Exception {
		GmailContact[] contacts = null;
		if (contactLoader != null) {
			ContactLoader loader = (ContactLoader) contactLoader;
			try {
				contacts = loader.getContacts();
				if (!started) {
					started = true;
					if (contacts == null) {
						for (int i = 0; i < 5; i++) {
							contacts = loader.getContacts();
							if (contacts != null) {
								break;
							}
							Thread.sleep(2000);
						}
					}
				}
			} catch (InterruptedException interrupt) {
				throw interrupt;
			} catch (Exception ignored) {
			}
		}
		return contacts;
	}

	// --- COMMON GMAIL POOL ---

	public final GmailPool getGmailPool() {
		return (GmailPool) gmailPool;
	}

	// --- FEED SUPPORT ---

	public final boolean isFeedConverterEnabled() {
		return feedEnabled;
	}

	// --- SERVLET REQUEST PROCESSOR ---

	public final Thread getServletListener() {
		return servletListener;
	}

	// --- STANDALONE APPLICATION MARKER ---

	public final boolean isStandalone() {
		return standaloneMode;
	}

	// --- STOP LISTENERS ---

	public final void interrupt() {

		// Stop services
		stopService(httpListener);
		stopService(fileListener);
		stopService(contactLoader);
		stopService(mailNotifier);
		stopService(sendMail);
		stopService(mailTerm);
		stopService(synchronizer);
		stopService(gmailPool);
	}

	private static final void stopService(Thread service) {
		if (service != null) {
			try {
				service.interrupt();
			} catch (Exception ignored) {
			}
		}
	}

}
