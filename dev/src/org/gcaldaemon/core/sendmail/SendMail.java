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
package org.gcaldaemon.core.sendmail;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.io.File;
import java.io.LineNumberReader;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.GmailEntry;
import org.gcaldaemon.core.GmailPool;
import org.gcaldaemon.core.ProgressMonitor;
import org.gcaldaemon.core.StringUtils;
import org.gcaldaemon.logger.QuickWriter;
import org.xml.sax.InputSource;

/**
 * Gmail message sender agent.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class SendMail extends Thread {

	// --- CONSTANTS ---

	private static final char[] CRLF = "\r\n".toCharArray();

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(SendMail.class);

	// --- VARIABLES ---

	private final ProgressMonitor monitor;
	private final Configurator configurator;
	private final File directory;
	private final long pollingTimeout;
	private final String username;
	private final String password;

	// --- CONSTRUCTOR ---

	public SendMail(ThreadGroup mainGroup, Configurator configurator)
			throws Exception {
		super(mainGroup, "Sendmail agent");
		this.configurator = configurator;

		// Show animated progress bar while sending
		if (configurator
				.getConfigProperty(Configurator.PROGRESS_ENABLED, false)) {
			if (GraphicsEnvironment.isHeadless()) {
				monitor = null;
				log.warn("Unable to use progress monitor in headless mode!");
			} else {
				monitor = new ProgressMonitor();
				setPriority(NORM_PRIORITY - 1);
			}
		} else {
			monitor = null;
		}

		// Get polling time
		long timeout = configurator.getConfigProperty(
				Configurator.SENDMAIL_POLLING_DIR, 10000L);
		if (timeout < 1000L) {
			log
					.warn("The fastest outgoing directory polling period is '1 sec'!");
			timeout = 1000L;
		}
		pollingTimeout = timeout;

		// Get username
		username = configurator.getConfigProperty(
				Configurator.SENDMAIL_GOOGLE_USERNAME, null);
		if (username == null) {
			throw new NullPointerException("Missing username ("
					+ Configurator.SENDMAIL_GOOGLE_USERNAME + ")!");
		}

		// Get password
		password = configurator
				.getPasswordProperty(Configurator.SENDMAIL_GOOGLE_PASSWORD);

		// Get directory
		String path = configurator.getConfigProperty(
				Configurator.SENDMAIL_DIR_PATH, null);
		if (path == null) {
			throw new NullPointerException("Missing parameter ("
					+ Configurator.SENDMAIL_DIR_PATH + ")!");
		}
		directory = new File(path);
		if (!directory.isDirectory()) {
			directory.mkdirs();
			if (!directory.isDirectory()) {
				throw new Exception("Unable to read the sendmail directory ("
						+ path + ")! Permission denied!");
			}
		}
		log.info("Start listening directory " + path + "...");
		log.info("Sendmail service started successfully.");

		// Start listener
		start();
	}

	// --- DIRECTORY LISTENER LOOP ---

	public final void run() {
		long lastModified = 0;
		for (;;) {
			try {

				// Wait
				sleep(pollingTimeout);

				// Find new files
				long modified = directory.lastModified();
				if (modified == lastModified) {
					continue;
				}
				File[] files = directory.listFiles();
				if (files == null || files.length == 0) {
					lastModified = modified;
					continue;
				}

				// Show monitor
				if (monitor != null) {
					monitor.setVisible(true);
				}

				// Sending mails
				GmailPool pool = configurator.getGmailPool();
				GmailEntry entry = null;
				try {
					entry = pool.borrow(username, password);
					for (int i = 0; i < files.length; i++) {
						try {
							if (log.isDebugEnabled()) {
								log.debug("Sending "
										+ files[i].getAbsolutePath() + "...");
							}
							sendFile(files[i], entry);
							log.debug("Sending finished successfully.");
						} catch (Exception sendError) {
							if (monitor != null) {
								try {
									Toolkit.getDefaultToolkit().beep();
								} catch (Throwable ignored) {
								}
							}
							log.error("Unable to send mail!", sendError);
						}

						// Sleep
						sleep(500);
					}
				} finally {
					pool.recycle(entry);
					if (monitor != null) {
						try {
							monitor.setVisible(false);
						} catch (Throwable ignored) {
						}
					}
				}
				lastModified = modified;
			} catch (InterruptedException interrupt) {
				log.info("Sendmail service stopped.");
				return;
			} catch (Exception poolException) {
				log.warn("Unexpected sendmail error!", poolException);
			}
		}
	}

	private final void sendFile(File file, GmailEntry entry) throws Exception {
		if (!file.exists() || file.length() < 4) {
			return;
		}
		RandomAccessFile raf = null;
		boolean sendingFinished = false;
		try {

			// Detect "To" field by file name
			String name = file.getName();
			String email = null;
			if (name.indexOf('@') != -1) {
				email = name;
				name = name.toLowerCase();
				if (name.endsWith(".txt") || name.endsWith(".xml")
						|| name.endsWith(".htm") || name.endsWith(".log")) {
					email = email.substring(0, email.length() - 4);
				} else {
					if (name.endsWith(".html")) {
						email = email.substring(0, email.length() - 5);
					}
				}
			}

			// Read file
			raf = new RandomAccessFile(file, "r");
			String content = readContent(raf);
			if (content != null) {
				if (content.startsWith("<?xml")) {

					// Parse and send XML file
					sendXML(email, content, entry);
				} else {

					// Parse and send plain text file
					sendPlainText(email, content, entry);
				}
			}
			sendingFinished = true;
		} finally {
			if (raf != null) {
				try {
					raf.close();
				} catch (Exception ignored) {
				}
			}
			if (sendingFinished && file != null) {
				file.delete();
			}
		}
	}

	// --- FILE LOADER ---

	private static final String readContent(RandomAccessFile raf)
			throws Exception {
		String content = null;
		int len = Math.min((int) raf.length(), 500);
		byte[] bytes = new byte[len];
		raf.seek(0);
		raf.readFully(bytes);
		if (bytes[0] == -17 && bytes[1] == -69 && bytes[2] == -65) {

			// UTF-8 header found
			log.debug("File encoding is 'UTF-8'.");
			bytes = new byte[(int) raf.length() - 3];
			raf.seek(3);
			raf.readFully(bytes);
			content = StringUtils.decodeToString(bytes, StringUtils.UTF_8)
					.trim();
		} else {

			// Autodetect encoding
			String header = StringUtils.decodeToString(bytes,
					StringUtils.US_ASCII).toUpperCase().replace(':', '=');
			String encoding = null;
			int pos = header.indexOf("ENCODING=");
			if (pos != -1) {
				int end = endOf(header, pos + 10);
				if (end != -1) {
					encoding = header.substring(pos + 9, end);
					encoding = encoding.replace('\'', ' ');
					encoding = encoding.replace('\"', ' ');
					encoding = encoding.trim();
				}
			}

			// Check XML-encoding
			if (encoding == null) {
				pos = header.indexOf("CHARSET=");
				if (pos != -1) {
					int end = endOf(header, pos + 10);
					if (end != -1) {
						encoding = header.substring(pos + 8, end);
						encoding = encoding.replace('\'', ' ');
						encoding = encoding.replace('\"', ' ');
						encoding = encoding.trim();
					}
				}
			}

			// Convert file encoding to Java encoding
			if (encoding == null) {
				encoding = Charset.defaultCharset().name();
			} else {
				if (encoding.equals("UTF8")) {
					encoding = StringUtils.UTF_8;
				}
			}
			log.debug("File encoding is '" + encoding + "'.");
			bytes = new byte[(int) raf.length()];
			raf.seek(0);
			raf.readFully(bytes);
			content = StringUtils.decodeToString(bytes, encoding).trim();
		}
		return content;
	}

	private static final int endOf(String string, int from) {
		int end0 = string.indexOf('\n', from);
		int end1 = string.indexOf('\"', from);
		int end2 = string.indexOf('\'', from);
		if (end0 == -1) {
			end0 = Integer.MAX_VALUE;
		}
		if (end1 == -1) {
			end1 = Integer.MAX_VALUE;
		}
		if (end2 == -1) {
			end2 = Integer.MAX_VALUE;
		}
		int end = Math.min(end0, Math.min(end1, end2));
		if (end == Integer.MAX_VALUE) {
			end = -1;
		}
		return end;
	}

	// --- PLAIN TEXT SENDER ---

	private final void sendPlainText(String email, String content,
			GmailEntry entry) throws Exception {

		// Mail properties
		HashSet to = new HashSet();
		HashSet cc = new HashSet();
		HashSet bcc = new HashSet();
		String subject = "Mail from " + username;
		QuickWriter body = new QuickWriter(content.length());
		if (email != null) {
			to.add(email);
		}

		// Parse text
		LineNumberReader reader = new LineNumberReader(
				new StringReader(content));
		boolean readingBody = false;
		String line, upper;
		for (;;) {
			line = reader.readLine();
			if (line == null) {
				break;
			}
			if (readingBody) {
				body.write(line);
				body.write(CRLF);
				continue;
			}
			if (line.trim().length() == 0) {
				continue;
			}
			upper = line.toUpperCase();
			if (upper.startsWith("ENCODING")) {
				continue;
			}
			if (upper.startsWith("SUBJECT")) {
				subject = getParameter(line);
				continue;
			}
			if (upper.startsWith("TO")) {
				addParameters(to, line);
				continue;
			}
			if (upper.startsWith("CC")) {
				addParameters(cc, line);
				continue;
			}
			if (upper.startsWith("BCC")) {
				addParameters(bcc, line);
				continue;
			}
			readingBody = true;
			body.write(line);
			body.write(CRLF);
		}

		// Submit mail
		String toList = "";
		String ccList = "";
		String bccList = "";

		Iterator i = to.iterator();
		while (i.hasNext()) {
			toList += (String) i.next() + ",";
		}
		i = cc.iterator();
		while (i.hasNext()) {
			ccList += (String) i.next() + ",";
		}
		i = bcc.iterator();
		while (i.hasNext()) {
			bccList += (String) i.next() + ",";
		}
		if (toList.length() == 0) {
			toList = username;
		}

		String msg = body.toString();
		boolean isHTML = msg.indexOf("/>") != -1 || msg.indexOf("</") != -1;
		if (isHTML) {
			msg = cropBody(msg);
		}
		if (isHTML) {
			log.debug("Sending HTML mail...");
		} else {
			log.debug("Sending plain-text mail...");
		}
		entry.send(toList, ccList, bccList, subject, msg, isHTML);
		log.debug("Mail submission finished.");
	}

	private static final void addParameters(HashSet set, String line) {
		String parameter = getParameter(line);
		StringTokenizer st = new StringTokenizer(parameter, ",");
		String token;
		while (st.hasMoreTokens()) {
			token = st.nextToken().trim();
			if (token != null && token.length() != 0) {
				set.add(token);
			}
		}
	}

	private static final String getParameter(String line) {
		int i = line.indexOf('=');
		if (i == -1) {
			i = line.indexOf(':');
		}
		if (i != -1) {
			return line.substring(i + 1);
		}
		return null;
	}

	private static final String cropBody(String html) {
		String test = html.toLowerCase();
		int i = test.indexOf("<body");
		if (i != -1) {
			i = test.indexOf('>', i + 4);
			if (i != -1) {
				html = html.substring(i + 1);
				test = html.toLowerCase();
			}
		}
		i = test.lastIndexOf("</body");
		if (i != -1) {
			html = html.substring(0, i);
			test = html.toLowerCase();
		}
		i = test.indexOf("<html");
		if (i != -1) {
			i = test.indexOf('>', i + 4);
			if (i != -1) {
				html = html.substring(i + 1);
				test = html.toLowerCase();
			}
		}
		i = test.lastIndexOf("</html");
		if (i != -1) {
			html = html.substring(0, i);
			test = html.toLowerCase();
		}
		return html;
	}

	// --- XML SENDER ---

	private final void sendXML(String email, String content, GmailEntry entry)
			throws Exception {
		log.debug("Parsing XML file...");
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setValidating(false);
		factory.setNamespaceAware(false);
		SAXParser parser = factory.newSAXParser();
		InputSource source = new InputSource(new StringReader(content));
		MailParser mailParser = new MailParser(username);
		parser.parse(source, mailParser);

		// Submit mail
		String toList = "";
		String ccList = "";
		String bccList = "";

		Iterator i = mailParser.getTo().iterator();
		while (i.hasNext()) {
			toList += (String) i.next() + ",";
		}
		i = mailParser.getCc().iterator();
		while (i.hasNext()) {
			ccList += (String) i.next() + ",";
		}
		i = mailParser.getBcc().iterator();
		while (i.hasNext()) {
			bccList += (String) i.next() + ",";
		}
		if (toList.length() == 0) {
			toList = username;
		}

		String msg = mailParser.getBody();
		boolean isHTML = msg.indexOf("/>") != -1 || msg.indexOf("</") != -1;
		if (isHTML) {
			msg = cropBody(msg);
		}
		if (isHTML) {
			log.debug("Sending HTML mail...");
		} else {
			log.debug("Sending plain-text mail...");
		}
		entry.send(toList, ccList, bccList, mailParser.getSubject(), msg,
				isHTML);
		log.debug("Mail submission finished.");
	}

}
