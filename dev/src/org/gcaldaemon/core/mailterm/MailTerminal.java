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
package org.gcaldaemon.core.mailterm;

import java.io.File;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Set;
import java.util.SortedMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.FilterMask;
import org.gcaldaemon.core.GmailEntry;
import org.gcaldaemon.core.GmailMessage;
import org.gcaldaemon.core.GmailPool;
import org.gcaldaemon.core.StringUtils;
import org.gcaldaemon.logger.QuickWriter;

import com.google.gdata.data.HtmlTextConstruct;

/**
 * Gmail-based command line interface.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class MailTerminal extends Thread {

	// --- CONSTANTS ---

	private static final String QUIT_COMMAND = "quit";

	private static final byte COMMAND_NOT_FOUND = 0;
	private static final byte COMMAND_EXECUTED = 1;
	private static final byte QUIT_REQUESTED = 2;

	private static final long FAST_POLLING_TIMEOUT = 60000L;
	private static final long SCRIPT_TIMEOUT = 30000L;

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(MailTerminal.class);

	// --- VARIABLES ---

	private final Configurator configurator;
	private final long pollingTimeout;
	private final String username;
	private final String password;
	private final String subject;
	private final String encoding;
	private final File scriptDir;

	private final FilterMask[] addresses;

	// --- CONSTRUCTOR ---

	public MailTerminal(ThreadGroup mainGroup, Configurator configurator)
			throws Exception {
		super(mainGroup, "Mail terminal");
		this.configurator = configurator;

		// Get inbox polling time
		long timeout = configurator.getConfigProperty(
				Configurator.MAILTERM_POLLING_GOOGLE, 10000L);
		if (timeout < 60000L) {
			log.warn("The fastest Gmail inbox polling period is '1 min'!");
			timeout = 60000L;
		}
		pollingTimeout = timeout;

		// Get Gmail user
		username = configurator.getConfigProperty(
				Configurator.MAILTERM_GOOGLE_USERNAME, null);
		if (username == null) {
			throw new NullPointerException("Missing username ("
					+ Configurator.MAILTERM_GOOGLE_USERNAME + ")!");
		}

		// Get Gmail password
		password = configurator
				.getPasswordProperty(Configurator.MAILTERM_GOOGLE_PASSWORD);

		// Get subject of the command mails
		subject = configurator
				.getPasswordProperty(Configurator.MAILTERM_MAIL_SUBJECT);

		// Get script directory
		String path = configurator.getConfigProperty(
				Configurator.MAILTERM_DIR_PATH, "/scripts");
		scriptDir = new File(path);
		if (!scriptDir.isDirectory()) {
			scriptDir.mkdirs();
			if (!scriptDir.isDirectory()) {
				throw new Exception("Unable to read script directory (" + path
						+ ")! Permission denied!");
			}
		}

		// Get native console encoding
		String consoleEncoding = configurator.getConfigProperty(
				Configurator.MAILTERM_CONSOLE_ENCODING, StringUtils.US_ASCII);
		try {
			StringUtils.US_ASCII.getBytes(consoleEncoding);
		} catch (Exception unsupportedEncoding) {

			// Dump supported encodings
			SortedMap map = Charset.availableCharsets();
			if (map != null) {
				Set set = map.keySet();
				if (set != null) {
					String[] array = new String[set.size()];
					set.toArray(array);
					QuickWriter writer = new QuickWriter();
					writer.write("Invalid charset (");
					writer.write(consoleEncoding);
					writer.write(")! Supported console encodings:\r\n");
					for (int i = 0; i < array.length; i++) {
						writer.write(array[i]);
						if (i < array.length - 1) {
							writer.write(", ");
						}
						if (i % 6 == 5) {
							writer.write("\r\n");
						}
					}
					log.warn(writer.toString().trim());
				}
			}
			consoleEncoding = StringUtils.US_ASCII;
		}
		encoding = consoleEncoding;

		// Get acceptable e-mail addresses
		addresses = configurator.getFilterProperty(
				Configurator.MAILTERM_ALLOWED_ADDRESSES, true);

		// Start listener
		log.info("Mailterm service started successfully.");
		start();
	}

	// --- DIRECTORY LISTENER LOOP ---

	public final void run() {
		try {
			sleep(5000L);
		} catch (InterruptedException interrupt) {
			log.info("Mailterm service stopped.");
			return;
		}
		for (;;) {
			try {

				// Borrow pooled Gmail connection
				GmailPool pool = configurator.getGmailPool();
				byte responseType = COMMAND_NOT_FOUND;
				GmailEntry entry = null;
				try {
					entry = pool.borrow(username, password);

					// Receive mails
					responseType = receiveMails(entry);
				} finally {

					// Recycle pooled connection
					pool.recycle(entry);
				}

				// Shutdown mailterm
				if (responseType == QUIT_REQUESTED) {
					throw new InterruptedException();
				}

				// Wait
				if (responseType == COMMAND_NOT_FOUND) {
					sleep(pollingTimeout);
				} else {
					sleep(FAST_POLLING_TIMEOUT);
				}

			} catch (InterruptedException interrupt) {
				log.info("Mailterm service stopped.");
				return;
			} catch (Exception poolException) {
				log.warn("Unexpected mailterm error!", poolException);
				log
						.debug("Please verify your username/password and IMAP settings!");
				try {
					sleep(pollingTimeout);
				} catch (InterruptedException interrupt) {
					log.info("Mailterm service stopped.");
					return;
				}
			}
		}
	}

	// --- MAIL RECEIVER ---

	private final byte receiveMails(GmailEntry client) throws Exception {

		// Find new mails
		log.debug("Searching commands in mailbox...");
		GmailMessage[] unreadMails = client.receive(subject);
		if (unreadMails == null || unreadMails.length == 0) {
			log.debug("Mailbox is empty or subject not found.");
			return COMMAND_NOT_FOUND;
		}

		// Read mails
		byte responseType = COMMAND_NOT_FOUND;
		GmailMessage message;
		for (int i = 0; i < unreadMails.length; i++) {
			message = unreadMails[i];

			// Get reply address
			String replyAddress = message.from;

			// Check access by reply address
			if (addresses != null) {
				if (!isAddressMatch(replyAddress)) {
					log.warn("Request refused, forbidden e-mail address ("
							+ replyAddress + ")!");
					continue;
				}
			}

			// Get command
			String command = message.memo;
			if (command == null) {
				log.debug("Missing command body!");
				continue;
			}
			HtmlTextConstruct html = new HtmlTextConstruct(command);
			command = html.getPlainText();
			command = command.replace('\r', ' ').replace('\n', ' ').trim();
			if (command.length() == 0) {
				log.debug("Missing command!");
				continue;
			}
			log.debug("Executing command from " + replyAddress + " (" + command
					+ ")...");
			String reply;
			if (command.equals(QUIT_COMMAND)) {

				// Shutdown requested
				reply = "Mailterm service terminated. Bye!";
				responseType = QUIT_REQUESTED;
			} else {

				// Parse command
				String[] args = parseLine(command);

				// Execute script
				ScriptRunner runner = new ScriptRunner(scriptDir, encoding,
						args);
				runner.join(SCRIPT_TIMEOUT);
				reply = runner.getScriptOutput();
				if (responseType != QUIT_REQUESTED) {
					responseType = COMMAND_EXECUTED;
				}
			}

			// Send reply
			log.debug("Command output:\r\n" + reply);
			client.send(replyAddress, null, null, "Re:" + subject, "<pre>"
					+ reply.trim() + "</pre>", true);

			// Wait
			Thread.sleep(500);
		}
		return responseType;
	}

	private final boolean isAddressMatch(String string) {
		for (int i = 0; i < addresses.length; i++) {
			if (addresses[i].match(string)) {
				return true;
			}
		}
		return false;
	}

	// --- COMMAND LINE PARSER ---

	/**
	 * Splitting a string into a command-array.
	 * 
	 * <BR>
	 * <BR>
	 * word1 word2 word3 -> "word1", "word2", "word3" <BR>
	 * word1 word2="word3 'abc' def" -> "word1", "word2", "word3 'abc' def" <BR>
	 * 'wo"rd1'="word2" word3 -> "wo\"rd1", "word2", "word3" <BR>
	 * etc.
	 * 
	 * @param cmdLine
	 * @return String[]
	 */
	private static final String[] parseLine(String cmdLine) {
		char delimiter = ' ';
		boolean inToken = false;
		QuickWriter writer = new QuickWriter(100);
		LinkedList tokens = new LinkedList();
		for (int i = 0; i < cmdLine.length(); i++) {
			char c = cmdLine.charAt(i);
			if (inToken) {
				if (c == delimiter || (delimiter == ' ' && c == '=')) {
					tokens.add(writer.toString());
					writer.flush();
					if (c == '-') {
						writer.write(c);
					}
					inToken = false;
					continue;
				}
				writer.write(c);
			} else {
				if (c == '\'') {
					delimiter = '\'';
					inToken = true;
				} else {
					if (c == '"') {
						delimiter = '"';
						inToken = true;
					} else if (c == ' ') {

						// Skip
					} else {
						delimiter = ' ';
						writer.write(c);
						inToken = true;
					}
				}
			}
			if (i == cmdLine.length() - 1 && writer.length() != 0) {
				tokens.add(writer.toString());
			}
		}
		String[] array = new String[tokens.size()];
		tokens.toArray(array);
		return array;
	}

}
