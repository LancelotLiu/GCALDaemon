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

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.Flags.Flag;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Pooled Gmail connection.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class GmailEntry {

	// --- LDAP CONSTANTS ---

	private static final int HTTP_CONNECTION_TIMEOUT = 10000;
	private static final int HTTP_WAIT_TIMEOUT = 60000;
	private static final String USER_AGENT = "Mozilla/5.0 (Windows; U;"
			+ " Windows NT 5.1; hu; rv:1.8.0.8) Gecko/20061025 Thunderbird/1.5.0.8";

	// --- LDAP VARIABLES ---

	private String oldContactURL = "http://mail.google.com/mail/?ui=1&view=fec";
	private String newContactURL = "http://mail.google.com/mail/contacts/data/export?"
			+ "exportType=ALL&out=GMAIL_CSV";
	private String gmailURL = "https://mail.google.com/mail";
	private String logoutURL = "https://mail.google.com/mail?logout";
	private String refererURL = "https://www.google.com/accounts/ServiceLogin?"
			+ "service=mail&passive=true&rm=false&"
			+ "continue=https%3A%2F%2Fmail.google.com%2Fmail%3Fui%3Dhtml%26zy%3Dl";

	private String gmailAt = null;

	// --- PACKAGE-PRIVATE VARIABLES ---

	String username;
	long lastUsage;

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(GmailEntry.class);

	// --- HTTP CONNECTION HANDLER OF THE LDAP SERVICE ---

	private static final MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
	private static final HttpClient httpClient = new HttpClient(
			connectionManager);

	static final void globalInit() {
		try {

			// Set proxy
			HttpConnectionManagerParams params = connectionManager.getParams();
			params.setConnectionTimeout(HTTP_CONNECTION_TIMEOUT);
			params.setSoTimeout(HTTP_WAIT_TIMEOUT);
			String proxyHost = System.getProperty("http.proxyHost");
			String proxyPort = System.getProperty("http.proxyPort");
			if (proxyHost != null && proxyPort != null) {
				httpClient.getHostConfiguration().setProxy(proxyHost,
						Integer.parseInt(proxyPort));
				String username = System.getProperty("http.proxyUserName");
				String password = System.getProperty("http.proxyPassword");
				if (username != null && password != null) {
					Credentials credentials = new UsernamePasswordCredentials(
							username, password);
					httpClient.getState().setProxyCredentials(AuthScope.ANY,
							credentials);
				}
			}
		} catch (Exception setupError) {
			log.warn("Unable to init proxy!", setupError);
		}
	}

	// --- SMTP CONNECTION VARIABLES ---

	private Session smtpSession;

	// --- IMAP CONNECTION VARIABLES ---

	private Store mailbox;

	// --- CONSTRUCTOR ---

	private final boolean ldap;
	private final boolean smtp;
	private final boolean imap;

	GmailEntry(boolean ldap, boolean smtp, boolean imap) {
		this.ldap = ldap;
		this.smtp = smtp;
		this.imap = imap;
	}

	// --- CONNECT ALL SERVICES ---

	private boolean connected;

	final void connect(String username, String password) throws Exception {

		// Login to LDAP service
		if (ldap) {
			connectLDAP(username, password);
		}

		// Login to SMTP service
		if (smtp) {
			connectSMTP(username, password);
		}

		// Login to IMAP service
		if (imap) {
			connectIMAP(username, password);
		}
		connected = true;
	}

	public final boolean isConnected() {
		return connected;
	}

	// --- LDAP CONNECT ---

	private final void connectLDAP(String username, String password)
			throws Exception {

		// Google Apps For Your Domain support
		String domain = null;
		int i = username.indexOf('@');
		String loginUsername = username;
		String loginURL = "https://www.google.com/accounts/ServiceLoginAuth";
		if (i != -1) {
			if (username.indexOf("gmail.com") == -1) {
				domain = username.substring(i + 1);
				loginUsername = username.substring(0, i);

				// Use Google Apps URLs instead of 'gmail.com' URLs
				gmailURL = "http://mail.google.com/a/" + domain + '/';
				loginURL = "https://www.google.com/a/" + domain
						+ "/ServiceLogin";
				logoutURL = "http://mail.google.com/a/" + domain + "/?logout";
				refererURL = "https://www.google.com/a/"
						+ domain
						+ "/ServiceLogin?service=mail&continue=https%3A%2F%2Fwww.google.com%3A443%2Fa%2F"
						+ domain + "%2FDashboard&passive=true";
				oldContactURL = "http://mail.google.com/a/" + domain
						+ "/?ui=1&view=fec";
				newContactURL = "http://mail.google.com/a/" + domain
						+ "/mail/contacts/data/export?"
						+ "exportType=ALL&out=GMAIL_CSV";
			}
		}

		// Default 'gmail.com' login fields
		PostMethod post = new PostMethod(loginURL);
		String usernameField = "Email";
		String passwordField = "Passwd";
		if (domain != null) {

			// Google Apps For Your Domain login fields
			usernameField = "userName";
			passwordField = "password";
		}

		// Create login request
		NameValuePair[] data = { new NameValuePair("service", "mail"),
				new NameValuePair(usernameField, loginUsername),
				new NameValuePair(passwordField, password),
				new NameValuePair("null", "Sign in"),
				new NameValuePair("continue", gmailURL) };
		post.addRequestHeader("User-Agent", USER_AGENT);
		post.addRequestHeader("referer", refererURL);
		post.addRequestHeader("Content-Type",
				"application/x-www-form-urlencoded");
		post.setRequestBody(data);

		// Send login form to Gmail
		int httpStatus = httpClient.executeMethod(post);
		if (httpStatus == -1) {
			throw new Exception("Login failed!\r\n"
					+ "Invalid HTTP status code (check username/password)!");
		}
		String response = post.getResponseBodyAsString();
		post.releaseConnection();
		if (response.toLowerCase().indexOf("errormsg") != -1) {
			log.warn("Response from Google:\r\n" + response);
			throw new Exception("Login failed (check username/password)!");
		}

		// Check redirection #1
		GetMethod get = null;
		String newLocationURL;
		i = response.toLowerCase().indexOf("moved temporarily");
		if (i != -1) {
			i = response.toLowerCase().indexOf("href");
			if (i != -1) {
				int n = response.indexOf('"', i + 7);

				// Get new URL #1
				newLocationURL = response.substring(i + 6, n);
				get = new GetMethod(newLocationURL);
				get.addRequestHeader("User-Agent", USER_AGENT);
				try {
					httpStatus = httpClient.executeMethod(get);

					// Get 'GMAIL_AT' cookie's value
					gmailAt = getGmailAt(get.getRequestHeaders());
					response = get.getResponseBodyAsString();
					get.releaseConnection();
				} catch (IOException e) {
					log.error("Failed to redirect URL!", e);
				}
			}
		}

		// Finish login (cookie handshake)
		get = new GetMethod(gmailURL);
		get.addRequestHeader("User-Agent", USER_AGENT);
		get.addRequestHeader("referer", refererURL);
		get.addRequestHeader("Content-Type", "text/html");
		try {
			httpStatus = httpClient.executeMethod(get);

			// Get 'GMAIL_AT' cookie's value
			gmailAt = getGmailAt(get.getRequestHeaders());

			// Check redirection #2
			if (httpStatus == HttpStatus.SC_MOVED_PERMANENTLY
					|| httpStatus == HttpStatus.SC_MOVED_TEMPORARILY
					|| httpStatus == HttpStatus.SC_SEE_OTHER
					|| httpStatus == HttpStatus.SC_TEMPORARY_REDIRECT) {

				// Get new URL #2
				Header newLocationHeader = get.getResponseHeader("location");
				if (newLocationHeader != null) {
					newLocationURL = newLocationHeader.getValue();
					get.releaseConnection();
					get = new GetMethod(newLocationURL);
					try {
						httpStatus = httpClient.executeMethod(get);

						// Check redirection #3
						if (httpStatus == HttpStatus.SC_MOVED_PERMANENTLY
								|| httpStatus == HttpStatus.SC_MOVED_TEMPORARILY
								|| httpStatus == HttpStatus.SC_SEE_OTHER
								|| httpStatus == HttpStatus.SC_TEMPORARY_REDIRECT) {

							// Get new URL #3
							newLocationHeader = get
									.getResponseHeader("location");
							if (newLocationHeader != null) {
								newLocationURL = newLocationHeader.getValue();
								get.releaseConnection();
								get = new GetMethod(newLocationURL);
								try {
									httpStatus = httpClient.executeMethod(get);

									// Get 'GMAIL_AT' cookie's value
									gmailAt = getGmailAt(get
											.getRequestHeaders());
									get.releaseConnection();
								} catch (IOException e) {
									log.warn("Failed to redirect URL!", e);
								}
							} else {
								log.warn("Missing location URL!");
							}
						}
					} catch (IOException e) {
						log.warn("Failed to redirect URL!", e);
					}
				} else {
					log.warn("Missing location header!");
				}
			}
		} catch (IOException e) {
			log.error("Failed to open URL", e);
		}
	}

	private static final String getGmailAt(Header[] headers) {
		if (headers != null) {
			String value;
			int i, n, k;

			// Loop on headers
			for (i = 0; i < headers.length; i++) {
				value = headers[i].getValue();
				n = value.indexOf("GMAIL_AT");
				if (n != -1) {

					// Found 'GMAIL_AT' cookie
					k = value.indexOf(';', n);
					if (k == -1) {
						return value.substring(n + 9);
					} else {
						return value.substring(n + 9, k);
					}
				}
			}
		}
		return null;
	}

	// --- SMTP CONNECT ---

	private final void connectSMTP(String username, String password)
			throws Exception {

		// Create SMTP session
		Properties props = (Properties) System.getProperties().clone();
		props.setProperty("mail.transport.protocol", "smtp");
		props.put("mail.smtp.host", "smtp.gmail.com");
		props.put("mail.smtp.port", "465");
		props.put("mail.smtp.user", username);
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.socketFactory.port", "465");
		props.put("mail.smtp.socketFactory.class",
				"javax.net.ssl.SSLSocketFactory");
		props.put("mail.smtp.socketFactory.fallback", "false");
		props.setProperty("mail.smtp.quitwait", "false");

		// Connect to Gmail via SMTP+TLS
		GmailAuthenticator login = new GmailAuthenticator(username, password);
		smtpSession = Session.getDefaultInstance(props, login);
		Transport smtpTransport = smtpSession.getTransport("smtp");
		smtpTransport.connect();

		log.debug("Gmail's SMTP service has been connected successfully.");
	}

	private final class GmailAuthenticator extends Authenticator {

		private final String username;
		private final String password;

		private GmailAuthenticator(String username, String password) {
			this.username = username;
			this.password = password;
		}

		protected PasswordAuthentication getPasswordAuthentication() {
			return new PasswordAuthentication(username, password);
		}

	}

	// --- IMAP CONNECT ---

	private final void connectIMAP(String username, String password)
			throws Exception {

		// Create IMAP session
		Properties props = (Properties) System.getProperties().clone();
		props.put("mail.imap.host", "imap.gmail.com");
		props.put("mail.imap.port", "993");
		props.put("mail.imap.user", username);
		props.put("mail.imap.auth", "true");
		props.put("mail.imap.socketFactory.port", "993");
		props.put("mail.imap.socketFactory.class",
				"javax.net.ssl.SSLSocketFactory");
		props.put("mail.imap.socketFactory.fallback", "false");

		// Connect to Gmail via IMAP+SSL
		GmailAuthenticator login = new GmailAuthenticator(username, password);
		Session session = Session.getDefaultInstance(props, login);
		mailbox = session.getStore("imaps");
		mailbox.connect("imap.gmail.com", 993, username, password);

		log.debug("Gmail's IMAP service has been connected successfully.");
	}

	// --- DISCONNECT ALL SERVICES ---

	final void disconnect() {

		// Disconnect LDAP service
		if (ldap) {
			try {
				GetMethod get = new GetMethod(logoutURL);
				get.addRequestHeader("referer", refererURL);
				get.addRequestHeader("Content-Type", "text/html");
				httpClient.getState().addCookie(
						new Cookie("gmail.google.com", "GMAIL_LOGIN", "T"
								+ Calendar.getInstance().getTimeInMillis()
								+ "/"
								+ Calendar.getInstance().getTimeInMillis()
								+ "/"
								+ Calendar.getInstance().getTimeInMillis(),
								"/", 999, true));
				httpClient.executeMethod(get);
				get.releaseConnection();
			} catch (Exception ioException) {
				log.debug("Unable to disconnect LDAP service!", ioException);
			}
		}

		// Disconnect SMTP service
		if (smtp) {
			try {
				smtpSession.getTransport("smtp").close();
			} catch (Exception ioException) {
				log.debug("Unable to disconnect SMTP service!", ioException);
			}
		}

		// Disconnect IMAP service
		if (imap) {
			try {
				mailbox.close();
			} catch (Exception ioException) {
				log.debug("Unable to disconnect IMAP service!", ioException);
			}
		}
	}

	// --- CONTACT LOADER [LDAP SERVICE] ---

	public final String downloadCSV() throws Exception {

		// Use the new download URL
		GetMethod get = null;
		try {
			get = new GetMethod(newContactURL);
			get.addRequestHeader("User-Agent", USER_AGENT);
			get.addRequestHeader("referer", gmailURL);
			get.addRequestHeader("Content-Type", "text/html");
			int status = httpClient.executeMethod(get);
			String csv = get.getResponseBodyAsString();
			if (status == 200 && csv.toLowerCase().indexOf("<html") == -1) {
				return csv;
			}
		} finally {
			if (get != null) {
				try {
					get.releaseConnection();
				} catch (Exception ignored) {
				}
			}
		}

		// Use the deprecated download URL
		PostMethod post = null;
		try {
			post = new PostMethod(oldContactURL);
			post.addRequestHeader("User-Agent", USER_AGENT);
			post.addRequestHeader("referer", gmailURL);
			post.addRequestHeader("Content-Type",
					"application/x-www-form-urlencoded");
			NameValuePair[] data2 = { new NameValuePair("at", gmailAt),
					new NameValuePair("ecf", "g"),
					new NameValuePair("ac", "Export Contacts") };
			post.setRequestBody(data2);
			int status = httpClient.executeMethod(post);
			String csv = post.getResponseBodyAsString();
			if (status == 200 && csv.toLowerCase().indexOf("<html") == -1) {
				return csv;
			}
		} finally {
			if (post != null) {
				try {
					post.releaseConnection();
				} catch (Exception ignored) {
				}
			}
		}

		log.warn("Incompatible Gmail interface -"
				+ " unable to download contacts!");
		return null;
	}

	// --- SEND MAIL [SMTP] ---

	public final void send(String to, String cc, String bcc, String subject,
			String memo, boolean html) throws Exception {
		MimeMessage mimeMessage = new MimeMessage(smtpSession);

		// Add 'to' fields
		StringTokenizer st = new StringTokenizer(to, ", ");
		while (st.hasMoreTokens()) {
			mimeMessage.addRecipients(Message.RecipientType.TO, st.nextToken());
		}

		// Add 'cc' fields
		if (cc != null && cc.length() != 0) {
			st = new StringTokenizer(cc, ", ");
			while (st.hasMoreTokens()) {
				mimeMessage.addRecipients(Message.RecipientType.CC, st
						.nextToken());
			}
		}

		// Add 'bcc' fields
		if (bcc != null && bcc.length() != 0) {
			st = new StringTokenizer(bcc, ", ");
			while (st.hasMoreTokens()) {
				mimeMessage.addRecipients(Message.RecipientType.BCC, st
						.nextToken());
			}
		}

		// Set message
		if (html) {
			mimeMessage.setHeader("Content-Type", "text/html");
			mimeMessage.setContent(memo.trim(), "text/html; charset=UTF-8");
		} else {
			mimeMessage.setHeader("Content-Type", "text/plain");
			mimeMessage.setContent(memo.trim(), "text/plain; charset=UTF-8");
		}

		// Set 'from', 'subject' and date fields
		mimeMessage.setFrom(new InternetAddress(username));
		mimeMessage.setSubject(subject.trim(), "UTF-8");
		mimeMessage.setSentDate(new Date());
		Transport.send(mimeMessage);
	}

	// --- RECEIVE UNREAD MAILS [IMAP] ---

	public final GmailMessage[] receive(String title) throws Exception {

		// Open 'INBOX' folder
		Folder inbox = mailbox.getFolder("INBOX");
		inbox.open(Folder.READ_WRITE);
		Message[] messages = inbox.getMessages();
		if (messages == null || messages.length == 0) {
			return new GmailMessage[0];
		}

		// Loop on messages
		LinkedList list = new LinkedList();
		for (int i = 0; i < messages.length; i++) {
			Message msg = messages[i];
			if (!msg.isSet(Flag.SEEN)) {
				String subject = msg.getSubject();
				if (title == null || title.length() == 0
						|| title.equals(subject)) {
					GmailMessage gm = new GmailMessage();
					Address[] from = msg.getFrom();
					msg.setFlag(Flag.SEEN, true);
					if (from == null || from.length == 0) {
						continue;
					}
					gm.subject = subject;
					gm.from = from[0].toString();
					gm.memo = String.valueOf(msg.getContent());
					list.addLast(gm);
				}
			}
		}
		inbox.close(true);

		// Return the array of the messages
		GmailMessage[] array = new GmailMessage[list.size()];
		list.toArray(array);
		return array;
	}

}
