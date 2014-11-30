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
package org.gcaldaemon.gui.config;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.FilterMask;
import org.gcaldaemon.core.GCalUtilities;
import org.gcaldaemon.core.PasswordEncoder;
import org.gcaldaemon.core.Request;
import org.gcaldaemon.core.StringUtils;
import org.gcaldaemon.logger.QuickWriter;

/**
 * Container for the GCALDaemon's configuration.
 * 
 * Created: Apr 16, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class MainConfig {

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(MainConfig.class);

	// --- VARIABLES ---

	private File configFile;
	private Properties config = new Properties();

	// --- CONSTRUCTOR ---

	public MainConfig(Configurator configurator) throws Exception {

		// Load config file
		configFile = configurator.getConfigFile();
		FileInputStream in = new FileInputStream(configFile);
		config.load(new BufferedInputStream(in));
		in.close();

		// Verify alarm mappings
		String alarmTypes = getConfigProperty(Configurator.REMOTE_ALARM_TYPES,
				null);
		if (alarmTypes == null) {
			config.setProperty(Configurator.REMOTE_ALARM_TYPES,
					"email,sms,popup");
		}

		// Parse LDAP accounts
		String parameterPostfix;
		for (int i = 1;; i++) {
			if (i == 1) {
				parameterPostfix = "";
			} else {
				parameterPostfix = Integer.toString(i);
				if (getConfigProperty(Configurator.LDAP_GOOGLE_USERNAME
						+ parameterPostfix, null) == null) {
					break;
				}
			}

			// Get username
			String username = getConfigProperty(
					Configurator.LDAP_GOOGLE_USERNAME + parameterPostfix, null);
			if (username == null) {
				continue;
			}

			// Get password
			String password = null;
			if (getConfigProperty(Configurator.LDAP_GOOGLE_PASSWORD
					+ parameterPostfix, null) != null) {
				password = getPasswordProperty(Configurator.LDAP_GOOGLE_PASSWORD
						+ parameterPostfix);
				if (password == null || password.length() == 0) {
					password = "password";
				}
			}

			// Add account
			AccountInfo info = new AccountInfo();
			info.username = username;
			info.password = password;
			ldapAccountList.addLast(info);
		}

		// Parse file configs
		for (int j, i = 1;; i++) {
			if (i == 1) {
				parameterPostfix = "";
			} else {
				parameterPostfix = Integer.toString(i);
				if (getConfigProperty(Configurator.FILE_ICAL_PATH
						+ parameterPostfix, null) == null) {
					break;
				}
			}

			// Get local file path
			String filePath = getConfigProperty(Configurator.FILE_ICAL_PATH
					+ parameterPostfix, "/google" + i + ".ics");
			if (filePath.charAt(0) == '~') {
				filePath = filePath.substring(1);
			}

			// Get username
			String username = getConfigProperty(
					Configurator.FILE_GOOGLE_USERNAME + parameterPostfix, null);

			// Get password
			String password = null;
			if (getConfigProperty(Configurator.FILE_GOOGLE_PASSWORD
					+ parameterPostfix, null) != null) {
				password = getPasswordProperty(Configurator.FILE_GOOGLE_PASSWORD
						+ parameterPostfix);
				if (password == null || password.length() == 0) {
					password = "password";
				}
			}

			// Get calendar URL
			String url = getConfigProperty(Configurator.FILE_PRIVATE_ICAL_URL
					+ parameterPostfix, null);

			// Verify parameters
			if (url == null) {
				log.warn("Missing private ICAL URL ("
						+ Configurator.FILE_PRIVATE_ICAL_URL + parameterPostfix
						+ ")!");
			} else {
				j = url.indexOf("/calendar");
				if (j > 0) {
					url = url.substring(j);
				}
				j = url.indexOf('@');
				if (j != -1) {
					url = url.substring(0, j) + "%40" + url.substring(j + 1);
				}
				j = url.indexOf("googlemail.com");
				if (j != -1) {
					url = url.substring(0, j) + "gmail.com"
							+ url.substring(j + 14);
				}
			}

			// Add parameters to lists
			FileSync fileSync = new FileSync();
			fileSync.icalPath = filePath;
			fileSync.privateIcalUrl = url;
			fileSync.username = username;
			fileSync.password = password;
			fileSyncList.addLast(fileSync);
		}
	}

	// --- COMMON CONFIGURATION PROPERTY GETTERS ---

	private boolean configChanged;

	public final boolean isConfigChanged() {
		return configChanged;
	}

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
			if (number.length() == 0) {
				return defaultValue;
			}
			number = number.replace('%', ' ').trim();
			return StringUtils.stringToLong(number);
		} catch (Exception malformed) {
			log.warn("Malformed numeric parameter (" + name + ")!");
		}
		return defaultValue;
	}

	public final FilterMask[] getFilterPropertyd(String name) {
		String list = config.getProperty(name, null);
		try {
			return StringUtils.splitMaskList(list, false);
		} catch (Exception malformed) {
			log.warn("Malformed mask list (" + name + ")!");
		}
		return new FilterMask[0];
	}

	public final String getPasswordProperty(String name) {
		String encodedPassword = config.getProperty(name, null);
		if (encodedPassword == null || encodedPassword.length() == 0) {
			return "";
		}
		try {
			return StringUtils.decodePassword(encodedPassword);
		} catch (Exception malformed) {
			log.warn("Malformed password (" + name + ")!");
		}
		return "";
	}

	// --- PROPERTY SETTERS ---

	public final void markAsChanged() {
		configChanged = true;
	}

	public final void setConfigProperty(String name, String value) {
		String old = getConfigProperty(name, null);
		if (value == null) {
			if (old != null) {
				configChanged = true;
			}
			config.remove(name);
		} else {
			if (old == null || !value.equals(old)) {
				configChanged = true;
			}
			config.setProperty(name, value);
		}
	}

	public final void setConfigProperty(String name, boolean value) {
		setConfigProperty(name, Boolean.toString(value));
	}

	public final void setPasswordProperty(String name, String password) {
		if (password == null) {
			setConfigProperty(name, null);
			return;
		}
		password = password.trim();
		if (password.length() == 0) {
			setConfigProperty(name, null);
			return;
		}
		try {
			setConfigProperty(name, PasswordEncoder.encodePassword(password));
		} catch (Exception anyException) {
			log.warn("Invalid password!", anyException);
			setConfigProperty(name, null);
		}
	}

	// --- GETTER / SETTER FOR LDAP ACCOUNTS ---

	private final LinkedList ldapAccountList = new LinkedList();

	public final AccountInfo[] getLDAPAccounts() {
		AccountInfo[] array = new AccountInfo[ldapAccountList.size()];
		ldapAccountList.toArray(array);
		return array;
	}

	public final void removeLDAPAccount(AccountInfo info) {
		if (info.username == null || info.username.length() == 0) {
			return;
		}
		if (info.password == null || info.password.length() == 0) {
			return;
		}
		Iterator configs = ldapAccountList.iterator();
		while (configs.hasNext()) {
			AccountInfo test = (AccountInfo) configs.next();
			if (test.username != null && test.username.equals(info.username)) {
				configs.remove();
				continue;
			}
		}
	}

	public final void setLDAPAccount(AccountInfo info) {
		if (info.username == null || info.username.length() == 0) {
			return;
		}
		if (info.password == null || info.password.length() == 0) {
			return;
		}
		removeLDAPAccount(info);
		ldapAccountList.addLast(info);
	}

	// --- GETTER / SETTER FOR FILE SYNC ---

	private final LinkedList fileSyncList = new LinkedList();

	public final FileSync[] getFileSyncConfigs() {
		FileSync[] array = new FileSync[fileSyncList.size()];
		fileSyncList.toArray(array);
		return array;
	}

	public final void removeFileSyncConfig(FileSync fileSync) {
		Iterator configs = fileSyncList.iterator();
		while (configs.hasNext()) {
			FileSync test = (FileSync) configs.next();
			if (test.icalPath != null
					&& test.icalPath.equals(fileSync.icalPath)) {
				configs.remove();
				continue;
			}
			if (test.privateIcalUrl != null
					&& test.privateIcalUrl.equals(fileSync.privateIcalUrl)) {
				configs.remove();
				continue;
			}
		}
	}

	public final void setFileSyncConfig(FileSync fileSync) {
		if (fileSync.privateIcalUrl == null
				|| fileSync.privateIcalUrl.length() == 0) {
			return;
		}
		if (fileSync.icalPath == null || fileSync.icalPath.length() == 0) {
			return;
		}
		removeFileSyncConfig(fileSync);
		fileSyncList.addLast(fileSync);
	}

	// --- SAVE CONFIG ---

	public final void saveConfig() {
		configChanged = false;
		QuickWriter out = new QuickWriter(10240);
		out.write("################################################\r\n");
		out.write("#        COMMON GCALDAEMON CONFIGURATION       #\r\n");
		out.write("################################################\r\n\r\n");

		writeParam(out, Configurator.LOG_CONFIG,
				"Name of the Log4J configuration file (file name or full path)");
		writeParam(out, Configurator.WORK_DIR,
				"Full path of the working directory (or empty)");
		writeParam(out, Configurator.CACHE_TIMEOUT,
				"Calendar timeout in the local cache (recommended is '3 min')");
		writeParam(out, Configurator.PROGRESS_ENABLED,
				"Show animated progress bar while synching");
		writeParam(out, Configurator.SEND_INVITATIONS,
				"Google Calendar send an email to the attendees to invite them to attend");
		writeParam(out, Configurator.ICAL_BACKUP_TIMEOUT,
				"Backup file timeout (0 = don't create backups, default is '7 day')");
		writeParam(
				out,
				Configurator.EXTENDED_SYNC_ENABLED,
				"Enable to sync alarms, categories, urls, priorities (reduces the performance!)");
		writeParam(out, Configurator.REMOTE_ALARM_TYPES,
				"Enabled remote alarm types (defaults are 'email,sms,popup')");
		writeParam(out, Configurator.PROXY_HOST,
				"HTTP proxy host (eg. 'firewall.mycompany.com' or empty)");
		writeParam(out, Configurator.PROXY_PORT,
				"HTTP proxy port (eg. '8080' or empty)");
		writeParam(out, Configurator.PROXY_USERNAME,
				"Username for HTTP proxy authentication (username or empty)");
		writeParam(out, Configurator.PROXY_PASSWORD,
				"Password for HTTP proxy authentication (use password encoder!)");

		out.write("################################################\r\n");
		out.write("# CONFIGURATION OF THE HTTP-BASED SYNCHRONIZER #\r\n");
		out.write("################################################\r\n\r\n");

		writeParam(out, Configurator.HTTP_ENABLED,
				"Enable built-in HTTP server/synchronizer");
		writeParam(out, Configurator.HTTP_PORT,
				"Port of the HTTP server (default is '9090')");
		writeParam(
				out,
				Configurator.HTTP_ALLOWED_HOSTNAMES,
				"List of allowed hostnames (eg. '*.mydomain.com,localhost,userpc.domain.*' or '*')");
		writeParam(out, Configurator.HTTP_ALLOWED_ADDRESSES,
				"List of allowed IP addresses (eg. '*.23.45.5,127.0.0.1,211.32.*' or '*')");

		out.write("################################################\r\n");
		out.write("# CONFIGURATION OF THE FILE-BASED SYNCHRONIZER #\r\n");
		out.write("################################################\r\n\r\n");

		writeParam(out, Configurator.FILE_ENABLED,
				"Enable built-in HTTP server/synchronizer");

		FileSync[] configs = getFileSyncConfigs();
		FileSync cfg;
		String idx;
		for (int i = 0; i < configs.length; i++) {
			cfg = configs[i];
			if (i == 0) {
				idx = "";
			} else {
				idx = Integer.toString(i + 1);
			}
			writeParam(out, Configurator.FILE_ICAL_PATH + idx, cfg.icalPath,
					"Full path of the local iCalendar file (don't include backslash characters!)");
			writeParam(out, Configurator.FILE_PRIVATE_ICAL_URL + idx,
					cfg.privateIcalUrl,
					"URL (without hostname) of the Google Calendar's private ical file");
			if (cfg.username != null && cfg.password != null) {
				writeParam(out, Configurator.FILE_GOOGLE_USERNAME + idx,
						cfg.username, "Gmail user (your full email address)");
				try {
					writeParam(out, Configurator.FILE_GOOGLE_PASSWORD + idx,
							PasswordEncoder.encodePassword(cfg.password),
							"Gmail password (use password encoder!)");
				} catch (Exception passwordException) {
					writeParam(out, Configurator.FILE_GOOGLE_PASSWORD + idx,
							"", "Gmail password (use password encoder!)");
					log.warn("Unable to encode password!", passwordException);
				}
			}
		}

		writeParam(out, Configurator.FILE_POLLING_FILE,
				"Local iCalendar file polling interval (recommended is '10 sec')");
		writeParam(out, Configurator.FILE_POLLING_GOOGLE,
				"Google Calendar polling interval (recommended is '10 min')");
		writeParam(out, Configurator.FILE_OFFLINE_ENABLED,
				"Turn it on when you use dial-up connection (default is 'false')");
		writeParam(out, Configurator.FILE_RELOADER_SCRIPT,
				"Full path of the calendar reloader script (or empty)");

		out.write("################################################\r\n");
		out.write("# CONFIGURATION OF THE FEED TO ICAL CONVERTER  #\r\n");
		out.write("################################################\r\n\r\n");

		writeParam(out, Configurator.FEED_ENABLED,
				"Enable RSS/ATOM feed to iCalendar converter");
		writeParam(out, Configurator.FEED_CACHE_TIMEOUT,
				"Feed timeout in the local cache (recommended is '1 hour')");
		writeParam(out, Configurator.FEED_EVENT_LENGTH,
				"Length of feed events in calendar (default is '45 min')");
		writeParam(out, Configurator.FEED_DUPLICATION_FILTER,
				"Sensitivity of the duplication filter (50% = very sensitive, 100% = disabled)");

		out.write("################################################\r\n");
		out.write("# CONFIGURATION OF THE GMAIL CONTACT CONVERTER #\r\n");
		out.write("################################################\r\n\r\n");

		writeParam(out, Configurator.LDAP_ENABLED, "Enable LDAP server");
		writeParam(out, Configurator.LDAP_PORT,
				"Port of the LDAP server (default is '9080')");

		AccountInfo[] accounts = getLDAPAccounts();
		AccountInfo account;
		for (int i = 0; i < accounts.length; i++) {
			account = accounts[i];
			if (i == 0) {
				idx = "";
			} else {
				idx = Integer.toString(i + 1);
			}
			writeParam(out, Configurator.LDAP_GOOGLE_USERNAME + idx,
					account.username, "Gmail user (your full email address)");
			try {
				writeParam(out, Configurator.LDAP_GOOGLE_PASSWORD + idx,
						PasswordEncoder.encodePassword(account.password),
						"Gmail password (use password encoder!)");
			} catch (Exception passwordException) {
				writeParam(out, Configurator.LDAP_GOOGLE_PASSWORD + idx, "",
						"Gmail password (use password encoder!)");
				log.warn("Unable to encode password!", passwordException);
			}
		}

		writeParam(out, Configurator.LDAP_CACHE_TIMEOUT,
				"Contact list timeout in the local cache (recommended is '1 hour')");
		writeParam(out, Configurator.LDAP_VCARD_ENCODING,
				"vCard encoding ('quoted', 'native' or 'utf-8', default is 'quoted')");
		writeParam(out, Configurator.LDAP_VCARD_VERSION,
				"vCard version ('2.1', '3.0', default is '3.0')");
		writeParam(
				out,
				Configurator.LDAP_ALLOWED_HOSTNAMES,
				"List of allowed hostnames (eg. '*.mydomain.com,localhost,userpc.domain.*' or '*')");
		writeParam(out, Configurator.LDAP_ALLOWED_ADDRESSES,
				"List of allowed IP addresses (eg. '*.23.45.5,127.0.0.1,211.32.*' or '*')");

		out.write("################################################\r\n");
		out.write("#     CONFIGURATION OF THE GMAIL NOTIFIER      #\r\n");
		out.write("################################################\r\n\r\n");

		writeParam(out, Configurator.NOTIFIER_ENABLED, "Enable Gmail notifier");
		writeParam(out, Configurator.NOTIFIER_GOOGLE_USERNAME,
				"Gmail user (your full email address)");
		writeParam(out, Configurator.NOTIFIER_GOOGLE_PASSWORD,
				"Gmail password (use password encoder!)");
		writeParam(out, Configurator.NOTIFIER_POLLING_MAILBOX,
				"Mailbox polling interval (recommended is '10 min')");
		writeParam(
				out,
				Configurator.NOTIFIER_WINDOW_STYLE,
				"Style of the notifier's window (style name or GIF/JPG/PNG file path without backslash)");
		writeParam(
				out,
				Configurator.NOTIFIER_WINDOW_SOUND,
				"Notifier's sound effect ('beep', 'sound' or WAV/AU/MID file path without backslash)");
		writeParam(out, Configurator.NOTIFIER_DATE_FORMAT,
				"Date format in the notifier's window (default is 'yyyy.MM.dd HH\\:mm\\:ss')");
		writeParam(out, Configurator.NOTIFIER_LOCAL_USERS,
				"List of allowed local users (eg. 'root,peter*,*admin' or '*')");

		out.write("################################################\r\n");
		out.write("#  CONFIGURATION OF THE MAIL SENDER SERVICE    #\r\n");
		out.write("################################################\r\n\r\n");

		writeParam(out, Configurator.SENDMAIL_ENABLED,
				"Enable Gmail sender service");
		writeParam(out, Configurator.SENDMAIL_GOOGLE_USERNAME,
				"Gmail user (your full email address)");
		writeParam(out, Configurator.SENDMAIL_GOOGLE_PASSWORD,
				"Gmail password (use password encoder!)");
		writeParam(
				out,
				Configurator.SENDMAIL_DIR_PATH,
				"Full path of the outgoing mail directory (don't include backslash characters!)");
		writeParam(out, Configurator.SENDMAIL_POLLING_DIR,
				"Outgoing directory polling interval (recommended is '10 sec')");

		out.write("################################################\r\n");
		out.write("#      CONFIGURATION OF THE MAIL TERMINAL      #\r\n");
		out.write("################################################\r\n\r\n");

		writeParam(out, Configurator.MAILTERM_ENABLED, "Enable Gmail terminal");
		writeParam(out, Configurator.MAILTERM_GOOGLE_USERNAME,
				"Gmail user (your full email address)");
		writeParam(out, Configurator.MAILTERM_GOOGLE_PASSWORD,
				"Gmail password (use password encoder!)");
		writeParam(out, Configurator.MAILTERM_MAIL_SUBJECT,
				"Subject of command mails (use password encoder!)");
		writeParam(out, Configurator.MAILTERM_ALLOWED_ADDRESSES,
				"List of allowed e-mail addresses (eg. 'admin@home.net,*company.com' or '*')");
		writeParam(out, Configurator.MAILTERM_POLLING_GOOGLE,
				"Gmail inbox polling interval (recommended is '10 min')");
		writeParam(out, Configurator.MAILTERM_DIR_PATH,
				"Full path of the script directory (don't include backslash characters!)");
		writeParam(out, Configurator.MAILTERM_CONSOLE_ENCODING,
				"Console encoding (IBM850, IBM852, etc, default is 'US-ASCII')");

		out.write("################################################\r\n");
		out.write("#      CONFIGURATION OF THE CONFIG EDITOR      #\r\n");
		out.write("################################################\r\n\r\n");

		writeParam(out, Configurator.EDITOR_LANGUAGE,
				"Language code (default is 'en')");
		writeParam(out, Configurator.EDITOR_LOOK_AND_FEEL,
				"Class name of the Swing Look and Feel");

		try {

			// Write config
			FileOutputStream file = new FileOutputStream(configFile);
			file.write(out.getBytes());
			file.close();

			// Remove registry file
			File registryFile = new File(getWorkDirectory(),
					"event-registry.txt");
			if (registryFile.isFile() && registryFile.canWrite()) {
				registryFile.delete();
			}
		} catch (Exception ioException) {
			log.fatal("Unable to save config!", ioException);
			if (System.getProperty("os.name", "unknown").toLowerCase().indexOf(
					"windows") == -1) {
				log.info("Please check the file permissions on the '"
						+ configFile.getParent() + "' folder!\r\n"
						+ "Hint: [sudo] chmod -R 777 <"
						+ configFile.getParent() + ">");
			}
		}
	}

	public final File getWorkDirectory() {
		String programRootDir = System.getProperty("gcaldaemon.program.dir");
		String workPath = getConfigProperty(Configurator.WORK_DIR, null);
		File directory;
		if (workPath == null) {
			directory = new File(programRootDir, "work");
		} else {
			directory = new File(workPath);
		}
		return directory;
	}

	private final void writeParam(QuickWriter out, String name,
			String description) {
		String value = getConfigProperty(name, "");
		writeParam(out, name, value, description);
	}

	private static final void writeParam(QuickWriter out, String name,
			String value, String description) {
		if (value == null) {
			value = "";
		}
		if (description != null && description.length() != 0) {
			out.write("# ");
			out.write(description);
			out.write("\r\n");
		}
		out.write(name);
		out.write('=');
		boolean head = true;
		int size = value.length();
		for (int i = 0; i < size; i++) {
			char c = value.charAt(i);
			switch (c) {
			case '\n':
				out.write("\\n");
				break;
			case '\r':
				out.write("\\r");
				break;
			case '\t':
				out.write("\\t");
				break;
			case ' ':
				out.write(head ? "\\ " : " ");
				break;
			case '\\':
			case '!':
			case '#':
			case '=':
			case ':':
				out.write('\\');
				out.write(c);
				break;
			default:
				if (c < ' ' || c > '~') {
					String hex = Integer.toHexString(c);
					out.write("\\u0000".substring(0, 6 - hex.length()));
					out.write(hex);
				} else {
					out.write(c);
				}
			}
			if (c != ' ') {
				head = false;
			}
		}
		out.write("\r\n\r\n");
	}

	// --- ACCOUNT UTILS ---

	private final HashMap accountMap = new HashMap();

	public final AccountInfo[] getAccounts() {
		FileSync[] configs = getFileSyncConfigs();
		FileSync cfg;
		int i = 0;

		// Add file sync accounts
		for (; i < configs.length; i++) {
			cfg = configs[i];
			if (cfg.username != null && cfg.password != null) {
				AccountInfo info = new AccountInfo();
				info.username = cfg.username;
				info.password = cfg.password;
				accountMap.put(info.username, info);
			}
		}

		// Add account of the LDAP service
		AccountInfo[] array = getLDAPAccounts();
		AccountInfo info;
		for (i = 0; i < array.length; i++) {
			info = array[i];
			if (info.username != null && info.password != null) {
				AccountInfo copy = new AccountInfo();
				copy.username = info.username;
				copy.password = info.password;
				accountMap.put(copy.username, copy);
			}
		}

		// Add account of the Gmail notifier service
		info = new AccountInfo();
		info.username = getConfigProperty(
				Configurator.NOTIFIER_GOOGLE_USERNAME, null);
		if (info.username != null) {
			info.password = getPasswordProperty(Configurator.NOTIFIER_GOOGLE_PASSWORD);
			accountMap.put(info.username, info);
		}

		// Add account of the sendmail service
		info = new AccountInfo();
		info.username = getConfigProperty(
				Configurator.SENDMAIL_GOOGLE_USERNAME, null);
		if (info.username != null) {
			info.password = getPasswordProperty(Configurator.SENDMAIL_GOOGLE_PASSWORD);
			accountMap.put(info.username, info);
		}

		// Add account of the mailterm service
		info = new AccountInfo();
		info.username = getConfigProperty(
				Configurator.MAILTERM_GOOGLE_USERNAME, null);
		if (info.username != null) {
			info.password = getPasswordProperty(Configurator.MAILTERM_GOOGLE_PASSWORD);
			accountMap.put(info.username, info);
		}

		array = new AccountInfo[accountMap.size()];
		if (array.length == 0) {
			return array;
		}
		Iterator mapEntries = accountMap.entrySet().iterator();
		Map.Entry entry;
		i = 0;
		while (mapEntries.hasNext()) {
			entry = (Map.Entry) mapEntries.next();
			array[i] = (AccountInfo) entry.getValue();
			i++;
		}
		return array;
	}

	public final void removeAccount(AccountInfo account) {
		AccountInfo[] accounts = getAccounts();
		if (account == null || accounts.length < 2 || account.username == null) {
			return;
		}
		accountMap.remove(account.username);

		// Remove account from file sync config
		Iterator fileIterator = fileSyncList.iterator();
		FileSync fileSync;
		while (fileIterator.hasNext()) {
			fileSync = (FileSync) fileIterator.next();
			if (account.username.equals(fileSync.username)) {
				fileIterator.remove();
			}
		}

		// Find another account
		AccountInfo another = null;
		AccountInfo info;
		for (int i = 0; i < accounts.length; i++) {
			info = accounts[i];
			if (info.username != null
					&& !info.username.equals(account.username)) {
				another = info;
				break;
			}
		}
		if (another == null) {
			another = new AccountInfo();
			another.username = "example@gmail.com";
			another.password = "example";
		}
		info = new AccountInfo();

		// Remove account from LDAP config
		removeLDAPAccount(account);
		if (ldapAccountList.isEmpty()) {
			ldapAccountList.addLast(another);
		}

		// Remove account from notifier's config
		info.username = getConfigProperty(
				Configurator.NOTIFIER_GOOGLE_USERNAME, null);
		if (info.username == null || info.username.equals(account.username)) {
			setConfigProperty(Configurator.NOTIFIER_GOOGLE_USERNAME,
					another.username);
			setPasswordProperty(Configurator.NOTIFIER_GOOGLE_PASSWORD,
					another.password);
		}

		// Remove account from sendmail's config
		info.username = getConfigProperty(
				Configurator.SENDMAIL_GOOGLE_USERNAME, null);
		if (info.username == null || info.username.equals(account.username)) {
			setConfigProperty(Configurator.SENDMAIL_GOOGLE_USERNAME,
					another.username);
			setPasswordProperty(Configurator.SENDMAIL_GOOGLE_PASSWORD,
					another.password);
		}

		// Remove account from mailterm's config
		info.username = getConfigProperty(
				Configurator.MAILTERM_GOOGLE_USERNAME, null);
		if (info.username == null || info.username.equals(account.username)) {
			setConfigProperty(Configurator.MAILTERM_GOOGLE_USERNAME,
					another.username);
			setPasswordProperty(Configurator.MAILTERM_GOOGLE_PASSWORD,
					another.password);
		}
	}

	public final void setAccount(AccountInfo account, String oldUsername) {
		getAccounts();
		if (oldUsername == null || account.username == null) {
			return;
		}
		accountMap.put(account.username, account);

		// Set account in file sync config
		Iterator fileIterator = fileSyncList.iterator();
		FileSync fileSync;
		while (fileIterator.hasNext()) {
			fileSync = (FileSync) fileIterator.next();
			if (oldUsername.equals(fileSync.username)) {
				fileSync.username = account.username;
				fileSync.password = account.password;
			}
		}

		// Set account in LDAP config
		String username = getConfigProperty(Configurator.LDAP_GOOGLE_USERNAME,
				null);
		if (username == null || username.equals(oldUsername)) {
			setConfigProperty(Configurator.LDAP_GOOGLE_USERNAME,
					account.username);
			setPasswordProperty(Configurator.LDAP_GOOGLE_PASSWORD,
					account.password);
		}

		// Set account in notifier's config
		username = getConfigProperty(Configurator.NOTIFIER_GOOGLE_USERNAME,
				null);
		if (username == null || username.equals(oldUsername)) {
			setConfigProperty(Configurator.NOTIFIER_GOOGLE_USERNAME,
					account.username);
			setPasswordProperty(Configurator.NOTIFIER_GOOGLE_PASSWORD,
					account.password);
		}

		// Set account in sendmail's config
		username = getConfigProperty(Configurator.SENDMAIL_GOOGLE_USERNAME,
				null);
		if (username == null || username.equals(oldUsername)) {
			setConfigProperty(Configurator.SENDMAIL_GOOGLE_USERNAME,
					account.username);
			setPasswordProperty(Configurator.SENDMAIL_GOOGLE_PASSWORD,
					account.password);
		}

		// Set account in mailterm's config
		username = getConfigProperty(Configurator.MAILTERM_GOOGLE_USERNAME,
				null);
		if (username == null || username.equals(oldUsername)) {
			setConfigProperty(Configurator.MAILTERM_GOOGLE_USERNAME,
					account.username);
			setPasswordProperty(Configurator.MAILTERM_GOOGLE_PASSWORD,
					account.password);
		}
	}

	// --- CALENDAR UTILS ---

	private final HashMap urlMap = new HashMap();

	public final String[] getCalendarURLs(AccountInfo account,
			boolean loadFromGoogle) throws Exception {
		if (account == null || account.username == null) {
			return new String[0];
		}
		HashSet set = new HashSet();
		String[] urls = null;
		int i;
		if (loadFromGoogle) {
			Request request = new Request();
			request.username = account.username;
			request.password = account.password;
			urls = GCalUtilities.getCalendarURLs(request, getWorkDirectory());
			urlMap.put(account.username, urls);
		} else {
			urls = (String[]) urlMap.get(account.username);
		}
		if (urls != null) {
			for (i = 0; i < urls.length; i++) {
				set.add(urls[i]);
			}
		}
		FileSync[] configs = getFileSyncConfigs();
		FileSync config;
		for (i = 0; i < configs.length; i++) {
			config = configs[i];
			if (account.username.equals(config.username)) {
				if (config.privateIcalUrl != null
						&& config.privateIcalUrl.endsWith(".ics")
						&& !containsURL(set, config.privateIcalUrl)) {
					set.add(config.privateIcalUrl);
				}
			}
		}
		String[] array = new String[set.size()];
		set.toArray(array);
		Arrays.sort(array, String.CASE_INSENSITIVE_ORDER);
		return array;
	}

	private static final boolean containsURL(HashSet set, String url) {
		int i = url.indexOf("/private");
		if (i != -1) {
			url = url.substring(0, i);
			Iterator urls = set.iterator();
			String test;
			while (urls.hasNext()) {
				test = (String) urls.next();
				if (test.startsWith(url)) {
					return true;
				}
			}
		}
		return false;
	}

	public static final boolean selectItem(JComboBox combo, String value) {
		if (value == null || combo == null || value.length() == 0) {
			return false;
		}
		ComboBoxModel model = combo.getModel();
		if (model != null) {
			int n, size = model.getSize();
			n = value.indexOf(" - ");
			if (n != -1) {
				value = value.substring(n + 3);
			}
			String test;
			for (int i = 0; i < size; i++) {
				test = (String) model.getElementAt(i);
				if (test != null) {
					n = test.indexOf(" - ");
					if (n != -1) {
						test = test.substring(n + 3);
					}
					if (test.equals(value)) {
						combo.setSelectedIndex(i);
						combo.setToolTipText((String) model.getElementAt(i));
						return true;
					}
				}
			}
		}
		if (combo.isEditable()) {
			combo.setSelectedItem(value);
			combo.setToolTipText(value);
			return true;
		}
		return false;
	}

	// --- LOCAL ICAL FILE UTILS ---

	private final HashMap icalNames = new HashMap();

	public final String[] getCalendarPaths() {
		try {
			HashSet set = new HashSet();
			FileSync[] configs = getFileSyncConfigs();

			// Scan directories
			HashSet dirs = new HashSet();
			int i;
			FileSync config;
			File file, dir;
			for (i = 0; i < configs.length; i++) {
				config = configs[i];
				if (config.icalPath != null && config.icalPath.endsWith(".ics")) {
					if (config.icalPath.indexOf("*.ics") != -1) {
						continue;
					}
					if (config.icalPath.indexOf("Application Support") != -1) {
						continue;
					}
					file = new File(config.icalPath);
					dir = file.getParentFile();
					if (dir.isDirectory() && dir.canRead()
							&& !dirs.contains(dir)) {
						dirs.add(dir);
						getCalendarPaths(set, dir, true);
					}
				}
			}

			// Scan folders of iCal3
			getICalendar3Paths(set, dirs);

			// Scan folders of iCal4
			getICalendar4Paths(set, dirs);

			// Scan folders of Evolution
			getEvolutionPaths(set, dirs);

			// Add configured paths
			for (i = 0; i < configs.length; i++) {
				config = configs[i];
				if (config.icalPath != null && config.icalPath.endsWith(".ics")
						&& !containsPath(set, config.icalPath)) {
					set.add(config.icalPath);
				}
			}
			String[] array = new String[set.size()];
			set.toArray(array);
			Arrays.sort(array, String.CASE_INSENSITIVE_ORDER);
			return array;
		} catch (Exception anyException) {
		}
		return null;
	}

	private static final boolean containsPath(HashSet set, String path) {
		int i = path.indexOf(':');
		if (i != -1) {
			path = path.substring(i + 1);
		}
		Iterator urls = set.iterator();
		String test;
		while (urls.hasNext()) {
			test = (String) urls.next();
			if (test.indexOf(path) != -1) {
				return true;
			}
		}
		return false;
	}

	private static final void getICalendar3Paths(HashSet set, HashSet dirs) {
		try {
			String userHome = System.getProperty("user.home");
			if (userHome == null || userHome.length() == 0) {
				return;
			}
			File sources = new File(userHome,
					"Library/Application Support/iCal/Sources");
			if (!sources.isDirectory()) {
				return;
			}
			File[] calendarDirs = sources.listFiles();
			if (calendarDirs == null || calendarDirs.length == 0) {
				return;
			}
			File calendarDir, calendar;
			String dirName;
			for (int i = 0; i < calendarDirs.length; i++) {
				calendarDir = calendarDirs[i];
				if (calendarDir.isDirectory() && !dirs.contains(calendarDir)) {
					dirs.add(calendarDir);
					dirName = calendarDir.getName();
					if (dirName.endsWith(".group")) {
						continue;
					}
					calendar = new File(calendarDir, "corestorage.ics");
					if (calendar.isFile()) {
						set.add(calendar.getCanonicalPath().replace('\\', '/'));
					}
				}
			}
		} catch (Exception ignored) {
		}
	}

	private static final void getICalendar4Paths(HashSet set, HashSet dirs) {
		try {
			String userHome = System.getProperty("user.home");
			if (userHome == null || userHome.length() == 0) {
				return;
			}
			File sources = new File(userHome, "Library/Calendars");
			if (!sources.isDirectory()) {
				return;
			}
			File[] calendarDirs = sources.listFiles();
			if (calendarDirs == null || calendarDirs.length == 0) {
				return;
			}
			File calendarDir, eventsDir;
			String dirName;
			for (int i = 0; i < calendarDirs.length; i++) {
				calendarDir = calendarDirs[i];
				if (calendarDir.isDirectory() && !dirs.contains(calendarDir)) {
					dirs.add(calendarDir);
					dirName = calendarDir.getName();
					if (dirName.endsWith(".group")) {
						continue;
					}
					if (dirName.endsWith(".calendar")) {
						eventsDir = new File(calendarDir, "Events");
						if (eventsDir.isDirectory()) {
							dirName = eventsDir.getCanonicalPath().replace(
									'\\', '/');
							if (!dirName.endsWith("/")) {
								dirName += "/";
							}
							set.add(dirName + "*.ics");
						}
					}
				}
			}
		} catch (Exception ignored) {
		}
	}

	private static final void getEvolutionPaths(HashSet set, HashSet dirs) {
		try {
			String userHome = System.getProperty("user.home");
			if (userHome == null || userHome.length() == 0) {
				return;
			}
			File sources = new File(userHome, ".evolution/calendar/local");
			if (!sources.isDirectory() || !sources.canRead()) {
				return;
			}
			File[] calendarDirs = sources.listFiles();
			if (calendarDirs == null || calendarDirs.length == 0) {
				return;
			}
			File calendarDir, calendar;
			for (int i = 0; i < calendarDirs.length; i++) {
				calendarDir = calendarDirs[i];
				if (calendarDir.isDirectory() && calendarDir.canRead()
						&& !dirs.contains(calendarDir)) {
					dirs.add(calendarDir);
					calendar = new File(calendarDir, "calendar.ics");
					if (calendar.isFile()) {
						set.add(calendar.getCanonicalPath().replace('\\', '/'));
					}
				}
			}
		} catch (Exception ignored) {
		}
	}

	private static final void getCalendarPaths(HashSet set, File dir,
			boolean recursive) {
		try {
			File[] files = dir.listFiles();
			if (files == null || files.length == 0) {
				return;
			}
			String name;
			File file;
			for (int i = 0; i < files.length; i++) {
				file = files[i];
				if (file.isFile()) {
					name = file.getName();
					if (name.endsWith(".ics")) {
						name = file.getCanonicalPath().replace('\\', '/');
						set.add(name);
					}
					continue;
				}
				if (recursive && file.isDirectory() && file.canRead()) {
					getCalendarPaths(set, file, false);
				}
			}
		} catch (Exception ignored) {
		}
	}

	public final String getCalendarName(String path) {
		if (path == null || path.length() == 0 || !path.endsWith(".ics")) {
			return null;
		}
		try {
			int n = path.indexOf(" - ");
			if (n != -1) {
				path = path.substring(n + 3);
			}
			path = path.replace('\\', '/').trim();
			if (path.endsWith("/*.ics")) {
				path = path.substring(0, path.length() - 6);
			}
			String name = (String) icalNames.get(path);
			if (name != null) {
				return name;
			}
			File file = new File(path);
			File dir = file.getParentFile();
			if (dir != null && dir.isDirectory() && dir.canRead()) {
				File plist = new File(dir, "Info.plist");
				if (plist.isFile() && plist.canRead()) {
					name = readNameFromPList(plist);
					if (name != null) {
						icalNames.put(path, name);
						return name;
					}
				}
				name = readNameFromCalendar(file);
				if (name != null) {
					icalNames.put(path, name);
					return name;
				}
			}
			name = file.getName();
			int i = name.lastIndexOf('.');
			name = name.substring(0, i);
			icalNames.put(path, name);
			return name;
		} catch (Exception anyException) {
		}
		return null;
	}

	private static final String readNameFromPList(File plist) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(plist), "UTF8"));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.indexOf("Title") != -1) {
					if ((line = reader.readLine()) != null) {
						reader.close();
						int index = line.indexOf(">");
						line = line.substring(index + 1);
						index = line.indexOf("<");
						line = line.substring(0, index).trim();
						if (line.length() == 0) {
							return null;
						}
						return line;
					}
				}
			}
			reader.close();
		} catch (Exception anyException) {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static final String readNameFromCalendar(File ical) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(ical), "UTF8"));
			String line;
			int counter = 0;
			while ((line = reader.readLine()) != null) {
				if (counter == 30) {
					break;
				}
				if (line.indexOf("X-WR-CALNAME") != -1) {
					reader.close();
					int index = line.indexOf(':');
					line = line.substring(index + 1).trim();
					if (line.length() == 0) {
						return null;
					}
					return line;
				}
				counter++;
			}
			reader.close();
		} catch (Exception anyException) {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	public final String getConfigPath() {
		try {
			return configFile.getCanonicalPath();
		} catch (Exception ignored) {
			return configFile.getAbsolutePath();
		}
	}

}
