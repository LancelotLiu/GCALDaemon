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
package org.gcaldaemon.servlet;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.servlet.ServletListener;

/**
 * GCALDAEMON STARTER (J2EE embedded mode)
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class Main extends HttpServlet {

	// --- STATIC GCALDAEMON SINGLETON ---

	private static final long serialVersionUID = -5231949161765732634L;

	private static Configurator configurator;

	// --- SERVLET INSTANCE COUNTER ---

	private static volatile int servletCounter;

	// --- SERVLET LIFECYCLE METHODS ---

	private ServletConfig servletConfig;

	public final void init(ServletConfig config) throws ServletException {
		try {
			this.servletConfig = config;
			synchronized (Main.class) {

				// Check inited state
				if (configurator == null) {

					// Create configurator
					configurator = new Configurator(null, null, false,
							Configurator.MODE_DAEMON);
				}

				// Increase servlet counter
				servletCounter++;
			}
		} catch (Throwable t) {
			throw new ServletException("Unable to init GCALDaemon!", t);
		}
	}

	public final void destroy() {
		synchronized (Main.class) {

			// Decrease servlet counter
			servletCounter--;

			// Shutdown synchronizer engine
			if (servletCounter == 0 && configurator != null) {
				configurator.interrupt();
				configurator = null;
			}
		}
	}

	// --- GET CALENDAR ---

	protected final void doGet(HttpServletRequest req, HttpServletResponse rsp)
			throws ServletException, IOException {

		// Redirect request
		((ServletListener) configurator.getServletListener()).doGet(req, rsp);
	}

	// --- PUT CALENDAR ---

	protected void doPut(HttpServletRequest req, HttpServletResponse rsp)
			throws ServletException, IOException {

		// Redirect request
		((ServletListener) configurator.getServletListener()).doPut(req, rsp);
	}

	// --- BASIC SERVLET INFO METHODS ---

	public final ServletConfig getServletConfig() {
		return servletConfig;
	}

	public final String getServletInfo() {
		return Configurator.VERSION;
	}

}
