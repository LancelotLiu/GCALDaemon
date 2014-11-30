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
package org.gcaldaemon.api;

import java.net.URL;

/**
 * Container of a private iCal URL and a Google Calendar name pair.
 * 
 * Created: Jan 22, 2008 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class RemoteCalendar {

	// --- VARIABLES ---

	/**
	 * Name of the calendar (eg. "Work", "Business")
	 */
	private final String name;

	/**
	 * Private iCal URL of the calendar (eg.
	 * "https://www.google.com/calendar/ical/.../basic.ics")
	 */
	private final URL url;

	// --- CONSTRUCTOR ---

	/**
	 * Construct a name/URL pair.
	 * 
	 * @param name
	 *            name
	 * @param url
	 *            private URL
	 */
	RemoteCalendar(String name, URL url) {
		this.name = name;
		this.url = url;
	}

	// --- PROPERTY GETTERS ---

	/**
	 * Returns the calendar's name (eg. "Work", "Business").
	 * 
	 * @return name
	 */
	public final String getName() {
		return name;
	}

	/**
	 * Returns the private iCal URL (eg.
	 * "https://www.google.com/calendar/ical/.../basic.ics").
	 * 
	 * @return private URL
	 */
	public final URL getURL() {
		return url;
	}

	// --- TO STRING ---

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	public final String toString() {
		return "org.gcaldaemon.api.RemoteCalendar[name=" + name + ", URL="
				+ url + ']';
	}

}
