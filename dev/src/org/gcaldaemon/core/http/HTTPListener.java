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
package org.gcaldaemon.core.http;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.core.CachedCalendar;
import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.FilterMask;
import org.gcaldaemon.core.GCalUtilities;
import org.gcaldaemon.core.Request;
import org.gcaldaemon.core.Response;
import org.gcaldaemon.core.StringUtils;
import org.gcaldaemon.logger.QuickWriter;

/**
 * Simple HTTP server.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public class HTTPListener extends Thread {

	// --- CONSTANTS ---

	protected static final int BUFFER_SIZE = 2048;

	protected static final String GET_METHOD = "GET";
	protected static final String PUT_METHOD = "PUT";
	protected static final String AUTHORIZATION = "Authorization";
	protected static final String BASIC = "Basic";
	protected static final String CONTENT_LENGTH = "ContentLength";

	private static final char CARRIAGE_RETURN = '\r';
	private static final char LINE_FEED = '\n';
	private static final char SPACE = ' ';
	private static final char UPPER_A = 'A';
	private static final char UPPER_Z = 'Z';
	private static final char TABULATOR = '\t';
	private static final char MINUS = '-';

	protected static final char COLON = ':';
	protected static final char LOWER_A = 'a';
	protected static final char LOWER_Z = 'z';
	protected static final int CASE_OFFSET = UPPER_A - LOWER_A;

	protected static final int STATUS_OK = 200;
	protected static final int STATUS_CREATED = 201;
	protected static final int STATUS_NOT_FOUND = 404;
	protected static final int STATUS_UNAUTHORIZED = 401;

	private static final String CACHED_FEED_POSTFIX = "-cached-feed";

	private static final byte UPPER_CASE = (byte) 1;
	private static final byte BOTH_CASE = (byte) 0;

	// --- MAX ENABLED REQUEST SIZE ---

	private static final long MAX_CONTENT_LENGTH = Runtime.getRuntime()
			.maxMemory() / 5;

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(HTTPListener.class);

	// --- GENERAL PARAMETERS ---

	protected final Configurator configurator;
	private final ServerSocket serverSocket;

	private final FilterMask[] hosts;
	private final FilterMask[] addresses;

	// --- CONSTRUCTOR ---

	public HTTPListener(ThreadGroup mainGroup, Configurator configurator)
			throws Exception {
		super(mainGroup, "HTTP listener");
		this.configurator = configurator;

		// Verify standalone flag (false = servlet mode)
		if (!configurator.isStandalone()) {
			serverSocket = null;
			addresses = null;
			hosts = null;
			return;
		}

		// Acceptable hostnames
		hosts = configurator
				.getFilterProperty(Configurator.HTTP_ALLOWED_HOSTNAMES);

		// Acceptable TCP/IP addresses
		addresses = configurator
				.getFilterProperty(Configurator.HTTP_ALLOWED_ADDRESSES);

		// Init server
		int port = (int) configurator.getConfigProperty(Configurator.HTTP_PORT,
				9090);
		log.info("HTTP server starting on port " + port + "...");
		serverSocket = new ServerSocket(port);

		if (hosts == null && addresses == null) {

			// Security warning
			log.warn("Set the '" + Configurator.HTTP_ALLOWED_HOSTNAMES
					+ "' parameter to limit remote access.");
		} else {

			// Debug filters
			if (log.isDebugEnabled()) {
				log.debug("Allowed HTTP hosts: "
						+ configurator.getConfigProperty(
								Configurator.HTTP_ALLOWED_HOSTNAMES, "*"));
				log.debug("Allowed HTTP addresses: "
						+ configurator.getConfigProperty(
								Configurator.HTTP_ALLOWED_ADDRESSES, "*"));
			}
		}

		// Start listener
		log.info("HTTP server started successfully.");
		start();
	}

	// --- HTTP SERVER LOOP ---

	public final void run() {

		// Variables
		Socket socket = null;
		Request request = null;
		Response response = null;

		try {

			// Loop
			for (;;) {

				// Accept connection
				socket = serverSocket.accept();
				socket.setSoTimeout(5000);

				// Access control
				try {
					checkAccess(socket);
				} catch (Exception securityError) {
					log.debug("Connection refused!", securityError);
					try {
						socket.close();
					} catch (Exception igonre) {
					}
					socket = null;
				}

				// Parse request
				request = null;
				if (socket != null) {
					try {
						request = readRequest(socket);
						log
								.debug("Processing " + request.method
										+ " method...");
					} catch (Exception readError) {
						log.warn("Unable to read request!", readError);
						request = null;
					}
				}

				// Create response
				response = null;
				if (request != null) {
					if (request.url != null && request.url.endsWith(".ics")) {
						int i = request.url.indexOf('@');
						if (i != -1) {
							request.url = request.url.substring(0, i) + "%40"
									+ request.url.substring(i + 1);
						}
						i = request.url.indexOf("googlemail.com");
						if (i != -1) {
							request.url = request.url.substring(0, i)
									+ "gmail.com"
									+ request.url.substring(i + 14);
						}
					}
					try {
						if (GET_METHOD.equals(request.method)) {
							response = doGet(request);
						} else {
							if (PUT_METHOD.equals(request.method)) {
								response = doPut(request);
							} else {
								response = doUnsupportedMethod(request);
							}
						}
					} catch (Exception processingError) {
						log.warn("Unable to process request!", processingError);
						response = null;
					}
				}

				// Write response
				if (response != null) {
					log.trace("Response processed with status code "
							+ response.status + ".");
					try {
						writeResponse(socket, request, response);
					} catch (Exception writeError) {
						log.warn("Unable to write response!", writeError);
						response = null;
					}
				}
				request = null;

				// Close socket
				if (socket != null) {
					try {
						socket.close();
					} catch (Exception closeError) {
						log.warn("Unable to close socket!", closeError);
						socket = null;
					}
				}
			}
		} catch (SocketException socketException) {

			// Check message
			String msg = socketException.getMessage();
			if (msg == null || msg.indexOf("close") == -1) {
				log.fatal("Fatal HTTP server error!", socketException);
			}
		} catch (Exception fatalError) {

			// Service stopped
			log.fatal("Fatal service error!", fatalError);
		} finally {

			// Close resources
			if (serverSocket != null && !serverSocket.isClosed()) {
				try {
					serverSocket.close();
				} catch (Exception ignore) {
				}
			}
			if (socket != null) {
				try {
					socket.close();
				} catch (Exception igonre) {
				}
				socket = null;
			}
		}
		log.info("HTTP server stopped.");
	}

	// --- STOP SERVICE ---

	public final void interrupt() {
		if (serverSocket != null && !serverSocket.isClosed()) {
			try {
				serverSocket.close();
			} catch (Exception ignore) {
			}
		}
		super.interrupt();
	}

	// --- TCP/IP ACCESS CONTROL ---

	private final void checkAccess(Socket socket) throws IOException, Exception {
		if (hosts != null || addresses != null) {
			InetAddress inetAddress = socket.getInetAddress();
			if (hosts != null) {
				String host = inetAddress.getHostName();
				if (host == null || host.length() == 0
						|| host.equals("127.0.0.1")) {
					host = "localhost";
				} else {
					host = host.toLowerCase();
					if (host.equals("localhost.localdomain")) {
						host = "localhost";
					}
				}
				if (!isHostMatch(host)) {
					log.warn("Connection refused (" + host
							+ " is a forbidden hostname)!");
					throw new Exception("forbidden hostname (" + host + ')');
				}
			}
			if (addresses != null) {
				String address = inetAddress.getHostAddress();
				if (address == null || address.length() == 0) {
					address = "127.0.0.1";
				}
				if (!isAddressMatch(address)) {
					log.warn("Connection refused (" + address
							+ " is a forbidden address)!");
					throw new Exception("forbidden IP-address (" + address
							+ ')');
				}
			}
		}
	}

	private final boolean isAddressMatch(String string) {
		for (int i = 0; i < addresses.length; i++) {
			if (addresses[i].match(string)) {
				return true;
			}
		}
		return false;
	}

	private final boolean isHostMatch(String string) {
		for (int i = 0; i < hosts.length; i++) {
			if (hosts[i].match(string)) {
				return true;
			}
		}
		return false;
	}

	// --- HTTP REQUEST READER ---

	private final Request readRequest(Socket socket) throws Exception {

		// Open stream
		InputStream is = socket.getInputStream();
		BufferedInputStream bis = new BufferedInputStream(is, BUFFER_SIZE);
		Request request = new Request();

		// Start processing - skip whitespaces
		char[] chars = new char[BUFFER_SIZE];
		char c;
		int n = 0;
		for (;;) {
			c = (char) bis.read();
			if (c != CARRIAGE_RETURN && c != LINE_FEED) {
				chars[n++] = c;
				break;
			}
		}

		// Read HTTP-method (GET, POST, PUT, etc)
		for (;;) {
			c = (char) bis.read();
			if (c == SPACE) {
				request.method = new String(chars, 0, n);
				break;
			}
			chars[n++] = c;
		}

		// Read URL and optional query-string
		n = 0;
		boolean flag = false;
		for (;;) {
			c = (char) bis.read();
			if (c == CARRIAGE_RETURN || c == LINE_FEED || c == SPACE) {

				// End of URL
				flag = c == SPACE;
				break;
			}
			chars[n++] = c;
		}
		int contentLength = 0;

		// URL always encoded in US-ASCII
		request.url = new String(chars, 0, n);

		// Read optional protocol
		n = 0;
		if (flag) {
			for (;;) {
				c = (char) bis.read();
				if (c == CARRIAGE_RETURN || c == LINE_FEED) {

					// End of protocol
					bis.read();
					break;
				}
				chars[n++] = c;
			}

			// Read headers
			String headerName;
			byte caseMode;
			for (;;) {
				for (;;) {
					c = (char) bis.read();
					if ((c == CARRIAGE_RETURN)) {

						// Do nothing
					} else {
						if (c == LINE_FEED) {

							// End of headers
							flag = false;
						}
						break;
					}
				}
				if (!flag) {
					break;
				}

				// Read header name
				n = 0;
				headerName = null;
				caseMode = UPPER_CASE;
				for (;;) {
					if (c == COLON) {
						headerName = new String(chars, 0, n);
						break;
					}
					if (c == MINUS) {
						caseMode = 1;
						c = (char) bis.read();
						continue;
					}
					if (caseMode != BOTH_CASE) {
						if (caseMode == UPPER_CASE) {
							if ((c >= LOWER_A) && (c <= LOWER_Z)) {
								chars[n++] = (char) (c + CASE_OFFSET);
							} else {
								chars[n++] = c;
							}
							caseMode = BOTH_CASE;
						} else {
							if ((c >= UPPER_A) && (c <= UPPER_Z)) {
								chars[n++] = (char) (c - CASE_OFFSET);
							} else {
								chars[n++] = c;
							}
						}
					} else {
						chars[n++] = c;
					}
					c = (char) bis.read();
				}

				// Read header's value
				n = 0;
				c = (char) bis.read();
				while (c == SPACE || c == TABULATOR) {
					c = (char) bis.read();
				}
				for (;;) {
					if (c == CARRIAGE_RETURN) {
					} else {
						if (c == LINE_FEED) {
							break;
						}
						chars[n++] = c;
					}
					c = (char) bis.read();
				}
				if (headerName.equals(AUTHORIZATION)) {
					headerName = new String(chars, 0, n);
					if (headerName.startsWith(BASIC)) {
						headerName = StringUtils.decodeBASE64(headerName
								.substring(6));
						n = headerName.indexOf(COLON);
						if (n != 0) {
							request.username = headerName.substring(0, n);
						}
						if (n != headerName.length() - 1) {
							request.password = headerName.substring(n + 1);
						}
					}
				} else {
					if (headerName.equals(CONTENT_LENGTH)) {
						contentLength = Integer
								.parseInt(new String(chars, 0, n));
					}
				}
			}
		}

		// Read body
		if (contentLength != 0) {
			if (contentLength > MAX_CONTENT_LENGTH) {
				throw new IllegalArgumentException("Too large message body ("
						+ contentLength + ">" + MAX_CONTENT_LENGTH + ")!");
			}
			int packet, readed = 0;
			request.body = new byte[contentLength];
			while (readed != contentLength) {
				packet = bis.read(request.body, readed, contentLength - readed);
				if (packet == -1) {
					throw new EOFException();
				}
				readed += packet;
			}
		}

		// Return request
		return request;
	}

	// --- HTTP RESPONSE WRITER ---

	private final void writeResponse(Socket socket, Request request,
			Response response) throws Exception {

		// Write headers
		OutputStream os = socket.getOutputStream();
		QuickWriter headers = new QuickWriter(BUFFER_SIZE);
		headers.write("HTTP/1.1 ");
		switch (response.status) {
		case STATUS_OK:
			headers.write("200 OK\r\nContent-Type: ");
			headers.write(response.contentType);
			headers.write("\r\n");
			break;
		case STATUS_CREATED:
			headers.write("201 Created\r\n");
			break;
		case STATUS_UNAUTHORIZED:
			headers.write("401 Unauthorized\r\n");
			String realm = null;
			if (request.url != null) {
				int s, e = request.url.indexOf("%40");
				if (e != -1) {
					s = request.url.lastIndexOf('/', e);
					if (s != -1) {
						realm = request.url.substring(s + 1, e).replace('.',
								' ');
					}
				}
				if (realm == null) {
					s = request.url.indexOf("private");
					if (s != -1) {
						e = request.url.indexOf('/', s + 7);
						if (e != -1) {
							realm = request.url.substring(s + 8, e);
						}
					}
				}
			}
			if (realm == null || realm.length() == 0) {
				realm = "Google Account";
			}
			headers.write("WWW-Authenticate: Basic realm=\"");
			headers.write(realm);
			headers.write("\"\r\n");
			headers.write("Content-Type: text/plain; charset=utf-8\r\n");
			break;
		default:
			headers.write("404 Not Found\r\n");
			headers.write("Content-Type: text/plain; charset=utf-8\r\n");
		}
		headers.write("Connection: close\r\n");
		headers
				.append("Cache-Control: no-cache, no-store, must-revalidate\r\n");
		if (response.body == null) {
			headers.write("Content-Length: 0\r\n\r\n");
		} else {
			headers.write("Content-Length: ");
			headers.write(Integer.toString(response.body.length));
			headers.write("\r\n\r\n");
		}
		os.write(headers.getBytes());

		// Write body
		if (response.body != null) {
			os.flush();
			os.write(response.body);
		}
		os.flush();
	}

	// --- PROCESS UNSUPPORTED METHODS ---

	private final Response doUnsupportedMethod(Request request)
			throws Exception {
		Response response = new Response();
		response.status = STATUS_NOT_FOUND;
		response.body = StringUtils.encodeString(
				"ERROR 404\r\n\r\nResource not found: " + request.url,
				StringUtils.US_ASCII);
		return response;
	}

	// --- PROCESS GET METHOD ---

	protected final Response doGet(Request request) throws Exception {

		// Validate URL
		Response response = new Response();
		boolean calendarRequested = request.url.endsWith(".ics");

		// Validate user
		if (calendarRequested
				&& (request.username == null || request.password == null || GCalUtilities
						.hasInvalidCredentials(request))) {
			log.debug("Password required!");
			response.status = STATUS_UNAUTHORIZED;
			response.body = StringUtils.encodeString(
					"ERROR 401\r\n\r\nPassword required: " + request.url,
					StringUtils.US_ASCII);
			return response;
		}

		// RSS / ATOM feed cache
		response.status = STATUS_OK;
		CachedCalendar calendar;
		boolean feedRequested = false;
		if (configurator.isFeedConverterEnabled()) {
			feedRequested = request.url.endsWith(CACHED_FEED_POSTFIX);
			if (feedRequested) {
				request.url = request.url.substring(0, request.url.length()
						- CACHED_FEED_POSTFIX.length());

				// Verify feedEnabled flag
				if (!configurator.isFeedConverterEnabled()) {
					response.status = STATUS_NOT_FOUND;
					response.body = StringUtils.encodeString(
							"Requested resource isn't an iCal file: "
									+ request.url, StringUtils.US_ASCII);
					log.warn("Requested resource isn't an iCal file ("
							+ request.url + ")!");
					return response;
				}
			}
		}

		// Get Google Calendar (ics file) from common cache
		if (feedRequested) {
			log.debug("Feed requested from " + request.url + "...");
		} else {
			log.debug("Calendar requested from " + request.url + "...");
		}
		calendar = configurator.getCalendar(request);
		if (feedRequested) {
			response.body = calendar.previousBody;
			if (response.body == null) {
				response.status = STATUS_NOT_FOUND;
				response.body = StringUtils.encodeString(
						"Requested resource isn't a valid RSS/ATOM feed: "
								+ request.url, StringUtils.US_ASCII);
				log.warn("Unable to load RSS/ATOM feed (" + request.url + ")!");
			} else {
				response.contentType = "text/xml";
			}
		} else {
			response.body = calendar.toByteArray();
		}

		// Return response container
		return response;
	}

	// --- PROCESS PUT METHOD ---

	protected final Response doPut(Request request) throws Exception {
		Response response = new Response();

		// Validate URL
		boolean needPassword = request.url.endsWith(".ics");

		// Validate user
		if (needPassword
				&& (request.username == null || request.password == null || GCalUtilities
						.hasInvalidCredentials(request))) {
			log.debug("Password required!");
			response.status = STATUS_UNAUTHORIZED;
			response.body = StringUtils.encodeString(
					"ERROR 401\r\n\r\nPassword required: " + request.url,
					StringUtils.US_ASCII);
			return response;
		}

		// Start synchronization
		log.debug("Calendar changed at " + request.url + '.');
		configurator.calendarChanged(request);

		// Return response
		response.status = STATUS_CREATED;
		return response;
	}

}