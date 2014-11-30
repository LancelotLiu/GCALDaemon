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
package org.gcaldaemon.core.notifier;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.FeedUtilities;
import org.gcaldaemon.core.FilterMask;
import org.gcaldaemon.core.GmailContact;
import org.gcaldaemon.logger.QuickWriter;

import com.google.gdata.data.HtmlTextConstruct;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndPerson;

/**
 * Gmail notifier thread.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class GmailNotifier extends Thread {

	// --- CONSTANTS ---

	private static final String FEED_URL = "https://mail.google.com/mail/feed/atom";

	private static final long MINUTE = 1000L * 60;
	private static final long HOUR = 1000L * 60 * 60;

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(GmailNotifier.class);

	// --- GENERAL PARAMETERS ---

	private final Configurator configurator;
	private final GmailNotifierWindow window;

	private final String username;
	private final String password;
	private final FilterMask[] users;
	private final long pollingTimeout;
	private final String mailtermSubject;

	private final SimpleDateFormat dateFormatter;

	// --- CONSTRUCTOR ---

	public GmailNotifier(ThreadGroup mainGroup, Configurator configurator)
			throws Exception {
		super(mainGroup, "Gmail notifier");
		this.configurator = configurator;

		// Get Gmail username
		username = configurator.getConfigProperty(
				Configurator.NOTIFIER_GOOGLE_USERNAME, null);
		if (username == null) {
			throw new NullPointerException("Missing username ("
					+ Configurator.NOTIFIER_GOOGLE_USERNAME + ")!");
		}

		// Acceptable local usernames
		users = configurator
				.getFilterProperty(Configurator.NOTIFIER_LOCAL_USERS);

		// Debug username filters
		if (log.isDebugEnabled()) {
			if (users == null) {
				log.debug("Notifier accepts all local users.");
			} else {
				log.debug("Allowed local users: "
						+ configurator.getConfigProperty(
								Configurator.NOTIFIER_LOCAL_USERS, "*"));
			}
		}

		// Get Gmail password
		password = configurator
				.getPasswordProperty(Configurator.NOTIFIER_GOOGLE_PASSWORD);

		// Get mailbox polling interval
		long timeout = configurator.getConfigProperty(
				Configurator.NOTIFIER_POLLING_MAILBOX, 600000L);
		if (timeout < 60000L) {
			log.warn("The fastest Gmail feed polling period is '1 min'!");
			timeout = 60000L;
		}
		pollingTimeout = timeout;

		// Get date format
		dateFormatter = new SimpleDateFormat(configurator.getConfigProperty(
				Configurator.NOTIFIER_DATE_FORMAT, "yyyy.MM.dd HH:mm:ss"));

		// Get window style
		String style = configurator.getConfigProperty(
				Configurator.NOTIFIER_WINDOW_STYLE, "default");

		// Get sound file / mode
		String sound = configurator.getConfigProperty(
				Configurator.NOTIFIER_WINDOW_SOUND, "beep");

		// Get mailterm subject (or null if disabled)
		boolean enableMailTerm = configurator.getConfigProperty(
				Configurator.MAILTERM_ENABLED, false);
		if (enableMailTerm) {
			mailtermSubject = configurator
					.getPasswordProperty(Configurator.MAILTERM_MAIL_SUBJECT);
		} else {
			mailtermSubject = null;
		}

		// Create window
		window = new GmailNotifierWindow(style, sound);

		// Start thread
		start();
	}

	// --- MAIL CHECKER LOOP ---

	public final void run() {
		log.info("Gmail notifier started successfully.");
		try {
			sleep(7000);
		} catch (Exception ignored) {
			return;
		}

		// Processed (displayed) mails
		HashSet processedMails = new HashSet();

		// Polling mailbox
		int i;
		for (;;) {
			try {

				// Verify local username
				if (users != null) {

					// List active users
					String[] activeUsers = getActiveUsers();
					boolean enabled = false;
					if (activeUsers != null && activeUsers.length != 0) {
						for (i = 0; i < activeUsers.length; i++) {
							enabled = isUserMatch(activeUsers[i]);
							if (enabled) {
								break;
							}
						}
						if (!enabled) {

							// Sleep for a minute
							log.debug("Access denied for active local users.");
							sleep(MINUTE);

							// Restart loop (verify username)
							continue;
						}
					}
				}

				// Get Gmail address book (or null)
				GmailContact[] contacts = configurator.getAddressBook();
				GmailContact contact;

				// Load feed entries
				SyndEntry[] entries = FeedUtilities.getFeedEntries(FEED_URL,
						username, password);
				SyndEntry entry;
				HashSet newMails = new HashSet();
				for (i = 0; i < entries.length; i++) {
					entry = entries[i];
					String date = getDate(entry);
					String from = getFrom(entry);
					if (contacts != null) {
						for (int n = 0; n < contacts.length; n++) {
							contact = contacts[n];
							if (from.equalsIgnoreCase(contact.email)) {
								from = contact.name;
								break;
							}
						}
					}
					String title = getTitle(entry);
					if (mailtermSubject != null) {
						if (title.equals(mailtermSubject)
								|| title.equals("Re:" + mailtermSubject)) {

							// Do not display mailterm commands and responses
							continue;
						}
					}
					String summary = getSummary(entry);
					newMails.add(date + '\t' + from + '\t' + title + '\t'
							+ summary);
				}

				// Remove readed mails
				Iterator iterator = processedMails.iterator();
				Object key;
				while (iterator.hasNext()) {
					key = iterator.next();
					if (!newMails.contains(key)) {
						iterator.remove();
					}
				}

				// Look up unprocessed mails
				LinkedList unprocessedMails = new LinkedList();
				iterator = newMails.iterator();
				while (iterator.hasNext()) {
					key = iterator.next();
					if (processedMails.contains(key)) {
						continue;
					}
					processedMails.add(key);
					unprocessedMails.addLast(key);
				}

				// Display unprocessed mails
				if (!unprocessedMails.isEmpty()) {

					String[] array = new String[unprocessedMails.size()];
					unprocessedMails.toArray(array);
					Arrays.sort(array, String.CASE_INSENSITIVE_ORDER);
					window.show(array);
				}

				// Sleep
				sleep(pollingTimeout);

			} catch (InterruptedException interrupt) {

				// Dispose window
				if (window != null) {
					try {
						window.setVisible(false);
					} catch (Exception ignored) {
					}
				}
				break;
			} catch (Exception loadError) {
				log.error("Unable to load Gmail feed!", loadError);
				try {
					sleep(HOUR);
				} catch (Exception interrupt) {
					return;
				}
			}
		}
	}

	private final boolean isUserMatch(String string) {
		for (int i = 0; i < users.length; i++) {
			if (users[i].match(string)) {
				return true;
			}
		}
		return false;
	}

	private final String getDate(SyndEntry entry) throws Exception {
		Date date = entry.getPublishedDate();
		if (date == null) {
			date = entry.getUpdatedDate();
		}
		if (date == null) {
			date = new Date();
		}
		return dateFormatter.format(date);
	}

	private static final String getFrom(SyndEntry entry) throws Exception {
		List list = entry.getAuthors();
		String from = null;
		if (list != null && !list.isEmpty()) {
			try {
				SyndPerson person = (SyndPerson) list.get(0);
				from = person.getEmail();
			} catch (Exception e) {
				from = null;
			}
		}
		if (from == null) {
			from = entry.getAuthor();
		}
		if (from == null) {
			from = "-";
		}
		return from;
	}

	private static final String getTitle(SyndEntry entry) throws Exception {
		String title = entry.getTitle();
		if (title == null || title.length() == 0) {
			title = "Mail from " + getFrom(entry);
		}
		return title;
	}

	private static final String getSummary(SyndEntry entry) throws Exception {
		SyndContent syndContent = entry.getDescription();
		String summary = null;
		if (syndContent != null) {
			String content = syndContent.getValue();
			if (content != null && content.length() != 0
					&& content.indexOf('<') == -1) {
				summary = content.trim();
			}
		}
		if (summary == null) {
			summary = "-";
		} else {
			try {
				HtmlTextConstruct html = new HtmlTextConstruct(summary);
				summary = html.getPlainText();
				html = new HtmlTextConstruct(summary);
				summary = html.getPlainText();
				summary = summary.replace('\r', ' ').replace('\n', ' ');
			} catch (Exception ignored) {
				summary = "-";
			}
		}
		return summary;
	}

	// --- LIST OF ACTIVE USERS ---

	private static final String[] TASK_COMMAND = { "tasklist", "/V", "/NH",
			"/FO", "CSV" };
	private static boolean commandExecutable = true;

	private static final String[] getActiveUsers() {
		HashSet users = new HashSet();
		try {
			String me = System.getProperty("user.name");
			if (me != null) {
				users.add(me);
			}
		} catch (Exception ignored) {
		}
		try {
			String os = System.getProperty("os.name", "unknown");
			if (commandExecutable && os.toLowerCase().indexOf("windows") != -1) {

				// Execute script
				ProcessBuilder builder = new ProcessBuilder(TASK_COMMAND);
				Process tasklist = builder.start();

				// Read command output
				InputStream in = tasklist.getInputStream();
				QuickWriter buffer = new QuickWriter();
				BufferedInputStream bis = new BufferedInputStream(in);
				InputStreamReader isr = new InputStreamReader(bis);
				char[] chars = new char[1024];
				int len;
				while ((len = isr.read(chars)) != -1) {
					buffer.write(chars, 0, len);
				}

				// Parse output
				String token, out = buffer.toString();
				StringTokenizer lines = new StringTokenizer(out, "\r\n");
				StringTokenizer tokens;
				int i;
				while (lines.hasMoreTokens()) {
					tokens = new StringTokenizer(lines.nextToken(), "\"", false);
					while (tokens.hasMoreTokens()) {
						token = tokens.nextToken();
						i = token.indexOf('\\');
						if (i != -1) {
							token = token.substring(i + 1);
							if (token.length() != 0) {
								users.add(token);
								break;
							}
						}
					}
				}

			}
		} catch (Exception invalidSyntax) {
			commandExecutable = false;
			log.debug(invalidSyntax);
		}
		String[] array = new String[users.size()];
		if (array.length > 0) {
			users.toArray(array);
			if (log.isDebugEnabled()) {
				QuickWriter writer = new QuickWriter(100);
				for (int i = 0; i < array.length; i++) {
					writer.write(array[i]);
					if (i < array.length - 1) {
						writer.write(", ");
					}
				}
				log.debug("Active users: " + writer.toString());
			}
		}
		return array;
	}

	// --- STOP SERVICE ---

	public final void interrupt() {

		// Dispose window
		if (window != null) {
			try {
				window.setVisible(false);
			} catch (Exception ignored) {
			}
		}

		// Interrupt thread
		super.interrupt();
	}

}
