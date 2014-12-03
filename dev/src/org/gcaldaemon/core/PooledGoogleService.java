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

import com.google.api.services.calendar.Calendar;

/**
 * Pooled Google connection (used in GoogleUtilities object).
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
final class PooledGoogleService {

	/**
	 * Timestamp of last usage
	 */
	long lastUsed;

	/**
	 * Cached Google connection
	 */
	Calendar service;

}
