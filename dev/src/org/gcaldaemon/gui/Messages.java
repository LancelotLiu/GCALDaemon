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
package org.gcaldaemon.gui;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Message bundle handler.
 * 
 * Created: Apr 16, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public class Messages {

	// --- BUILT-IN RESOURCE BUNDLE ---

	private static final String BUNDLE_NAME = "org.gcaldaemon.gui.messages"; //$NON-NLS-1$
	private static final ResourceBundle DEFAULT_RESOURCE_BUNDLE = ResourceBundle
			.getBundle(BUNDLE_NAME);

	// --- SELECTED RESOURCE BUNDLE ---

	private static ResourceBundle userResourceBundle;
	private static Locale userLocale = Locale.ENGLISH;

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(Messages.class);

	// --- PRIVATE CONSTRUCTOR ---

	private Messages() {
	}

	// --- GLOBAL MESSAGE HANDLER ---

	public static final String getString(String key) {
		if (userResourceBundle != null) {
			try {
				return userResourceBundle.getString(key);
			} catch (MissingResourceException ignored) {
			}
		}
		try {
			return DEFAULT_RESOURCE_BUNDLE.getString(key);
		} catch (MissingResourceException undefined) {
			return '!' + key + '!';
		}
	}

	public static final boolean setUserLocale(Locale locale) {
		try {
			userResourceBundle = null;
			String programDir = System.getProperty("gcaldaemon.program.dir",
					"/Progra~1/GCALDaemon");
			File langDir = new File(programDir, "lang");
			if (!langDir.isDirectory()) {
				langDir.mkdir();
				return false;
			}
			File msgFile = new File(langDir, "messages-"
					+ locale.getLanguage().toLowerCase() + ".txt");
			if (!msgFile.isFile()) {
				return false;
			}
			InputStream in = new BufferedInputStream(new FileInputStream(
					msgFile));
			userResourceBundle = new PropertyResourceBundle(in);
			in.close();
			userLocale = locale;
			Locale.setDefault(locale);
			return true;
		} catch (Exception ignored) {
			log.error("Unable to load messages!", ignored);
		}
		return false;
	}

	public static final Locale getUserLocale() {
		return userLocale;
	}

	public static final Locale[] getAvailableLocales() {
		LinkedList list = new LinkedList();
		list.addLast(Locale.ENGLISH);
		try {
			Locale[] availables = Locale.getAvailableLocales();
			String programDir = System.getProperty("gcaldaemon.program.dir",
					"/Progra~1/GCALDaemon");
			File langDir = new File(programDir, "lang");
			if (langDir.isDirectory()) {
				String[] names = langDir.list();
				String name;
				int s, e;
				for (int i = 0; i < names.length; i++) {
					name = names[i];
					if (name.equals("messages-en.txt")) {
						continue;
					}
					s = name.indexOf('-');
					e = name.indexOf('.', s);
					if (s != -1 && e != -1) {
						name = name.substring(s + 1, e);
						Locale locale = null;
						for (s = 0; s < availables.length; s++) {
							if (name.equals(availables[s].getCountry()
									.toLowerCase())) {
								locale = availables[s];
								break;
							}
						}
						if (locale == null) {
							locale = new Locale(name);
						}
						list.add(locale);
					}
				}
			}
		} catch (Exception ignored) {
			log.warn(ignored);
		}
		Locale[] array = new Locale[list.size()];
		list.toArray(array);
		return array;
	}

	public static final String[][] getTranslatorTable(Locale locale) {
		Enumeration keyEnumerator = DEFAULT_RESOURCE_BUNDLE.getKeys();
		LinkedList list = new LinkedList();
		while (keyEnumerator.hasMoreElements()) {
			list.addLast(keyEnumerator.nextElement());
		}
		String[] keys = new String[list.size()];
		list.toArray(keys);
		Arrays.sort(keys, String.CASE_INSENSITIVE_ORDER);
		String[][] data = new String[keys.length][3];
		PropertyResourceBundle localeBundle = null;
		try {
			String programDir = System.getProperty("gcaldaemon.program.dir",
					"/Progra~1/GCALDaemon");
			File langDir = new File(programDir, "lang");
			if (!langDir.isDirectory()) {
				langDir.mkdir();
			} else {
				File msgFile = new File(langDir, "messages-"
						+ locale.getLanguage().toLowerCase() + ".txt");
				if (msgFile.isFile()) {
					InputStream in = new BufferedInputStream(
							new FileInputStream(msgFile));
					localeBundle = new PropertyResourceBundle(in);
					in.close();
				}
			}
		} catch (Exception ignored) {
			log.warn("Unable to load messages!", ignored);
		}
		for (int i = 0; i < keys.length; i++) {
			data[i][0] = keys[i];
			data[i][1] = DEFAULT_RESOURCE_BUNDLE.getString(keys[i]);
			if (localeBundle != null) {
				try {
					data[i][2] = localeBundle.getString(keys[i]);
				} catch (Exception ignored) {
				}
			}
			if (data[i][2] == null) {
				data[i][2] = data[i][1];
			}
		}
		return data;
	}

}
