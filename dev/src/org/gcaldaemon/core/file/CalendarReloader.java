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
package org.gcaldaemon.core.file;

import java.util.LinkedList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.logger.QuickWriter;

/**
 * Calendar reloader thread.
 * 
 * Created: Jul 20, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
final class CalendarReloader extends Thread {

	// --- LOGGER ---

	protected static final Log log = LogFactory.getLog(CalendarReloader.class);

	// --- PARAMETERS ---

	private final String cmd;
	private final String[] args;

	private boolean locked = true;

	// --- CONSTRUCTOR ---

	CalendarReloader(String command) {
		cmd = command;
		args = parseLine(command);
		setPriority(MIN_PRIORITY);
		setDaemon(true);
	}

	// --- RELOAD/RESTART CALENDAR APPLICATION ---

	final synchronized void reload(boolean runOnce) {
		if (runOnce) {
			try {
				executeScript();
			} catch (Exception commandException) {
				log.warn("Unable to execute command (" + cmd + ")!",
						commandException);
			}
		} else {
			locked = false;
			notifyAll();
		}
	}

	// --- SCRIPT RUNNER LOOP ---

	public final void run() {
		for (;;) {
			try {

				// Wait for unlock
				synchronized (this) {
					while (locked) {
						wait(5000);
					}
					locked = true;
				}

				// Execute script
				executeScript();

			} catch (InterruptedException interrupt) {
				return;
			} catch (Exception commandException) {
				log.warn("Unable to execute command (" + cmd + ")!",
						commandException);
			}
		}
	}

	private final void executeScript() throws Exception {

		// Execute script
		log.debug("Executing reloader script (" + cmd + ")...");
		ProcessBuilder builder = new ProcessBuilder(args);
		Process script = builder.start();
		sleep(1000L);

		// Wait for script
		for (int i = 0; i < 15; i++) {
			try {
				script.exitValue();
			} catch (Exception processNotExited) {
				sleep(1000L);
			}
		}

		// Destroy script
		script.destroy();
		script = null;
		log.debug("Reloader script finished successfully.");
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
