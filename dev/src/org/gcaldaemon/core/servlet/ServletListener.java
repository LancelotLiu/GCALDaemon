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
package org.gcaldaemon.core.servlet;

import java.io.EOFException;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.Request;
import org.gcaldaemon.core.Response;
import org.gcaldaemon.core.StringUtils;
import org.gcaldaemon.core.http.HTTPListener;

/**
 * Servlet-based request processor.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class ServletListener extends HTTPListener {

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(ServletListener.class);

	// --- CONSTRUCTOR ---

	public ServletListener(ThreadGroup mainGroup, Configurator configurator)
			throws Exception {
		super(mainGroup, configurator);

		// Log start (serlet mode)
		log.info("GCALDaemon started successfully.");
	}

	// --- GET CALENDAR ---

	public final void doGet(HttpServletRequest req, HttpServletResponse rsp)
			throws ServletException, IOException {
		processRequest(req, rsp, true);
	}

	// --- PUT CALENDAR ---

	public final void doPut(HttpServletRequest req, HttpServletResponse rsp)
			throws ServletException, IOException {
		processRequest(req, rsp, false);
	}

	// --- HANDLE REQUEST ---

	private final void processRequest(HttpServletRequest req,
			HttpServletResponse rsp, boolean getMethod)
			throws ServletException, IOException {
		try {

			// Transform HttpServletRequest to Request
			Request request = new Request();
			request.method = getMethod ? GET_METHOD : PUT_METHOD;

			// Transform URL
			request.url = req.getPathInfo();
			int i = request.url.indexOf('@');
			if (i != -1) {
				request.url = request.url.substring(0, i) + "%40"
						+ request.url.substring(i + 1);
			}

			// Get the properties of the request
			HashMap properties = new HashMap();
			Enumeration names = req.getHeaderNames();
			String header;
			while (names.hasMoreElements()) {
				header = (String) names.nextElement();
				properties.put(formatString(header.toCharArray()), req
						.getHeader(header));
			}

			// Transform username and password
			header = (String) properties.get(AUTHORIZATION);
			if (header != null && header.startsWith(BASIC)) {
				header = StringUtils.decodeBASE64(header.substring(6));
				int n = header.indexOf(COLON);
				if (n != 0) {
					request.username = header.substring(0, n);
				}
				if (n != header.length() - 1) {
					request.password = header.substring(n + 1);
				}
			}

			// Get Content-Length header
			int contentLength = 0;
			header = (String) properties.get(CONTENT_LENGTH);
			if (header != null && header.length() != 0) {
				contentLength = Integer.parseInt(header);
			}

			// Read body
			if (contentLength != 0) {
				int packet, readed = 0;
				request.body = new byte[contentLength];
				ServletInputStream in = req.getInputStream();
				while (readed != contentLength) {
					packet = in.read(request.body, readed, contentLength
							- readed);
					if (packet == -1) {
						throw new EOFException();
					}
					readed += packet;
				}
			}

			// Redirect request to superclass
			Response response;
			if (getMethod) {
				response = doGet(request);
			} else {
				response = doPut(request);
			}

			// Set response status
			rsp.setStatus(response.status);

			// Add unauthorized header and realm
			if (response.status == STATUS_UNAUTHORIZED) {
				String realm = null;
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
				if (realm == null || realm.length() == 0) {
					realm = "Google Account";
				}
				rsp.addHeader("WWW-Authenticate", "Basic realm=\"" + realm
						+ '\"');
			}

			// Write body
			ServletOutputStream out = null;
			try {
				out = rsp.getOutputStream();
				out.write(response.body);
			} finally {
				if (out != null) {
					try {
						out.close();
					} catch (Exception ignored) {
					}
				}
			}

		} catch (Exception processingError) {
			throw new ServletException("Unable to process " + req.getMethod()
					+ " request!", processingError);
		}
	}

	// --- PRIVATE UTILITIES ---

	/**
	 * Reads & formats a string value from an array. Content-length ->
	 * ContentLength cache-control -> CacheControl
	 * 
	 * @param input
	 * @return String
	 */
	private static final String formatString(char[] input) {
		boolean upperCase = true;
		char[] chars = new char[input.length];
		int data;
		int pos = 0;
		for (int i = 0; i < input.length; i++) {
			data = input[i];
			if (data == '-') {
				upperCase = true;
				continue;
			}
			if (upperCase) {
				if ((data >= LOWER_A) && (data <= LOWER_Z)) {
					chars[pos] = (char) (data + CASE_OFFSET);
				} else {
					chars[pos] = (char) data;
				}
				upperCase = false;
			} else {
				chars[pos] = (char) data;
			}
			pos++;
		}
		return new String(chars, 0, pos);
	}

}
