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

/**
 * Gmail contact container.
 * 
 * Created: Jan 03, 2008 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class GmailContact {

	// --- BASIC CONTACT VARIABLES ---

	public String name = "";
	public String email = "";
	public String notes = "";

	// --- EXTENDED CONTACT VARIABLES ---

	public String description = "";
	public String mail = "";
	public String im = "";
	public String phone = "";
	public String mobile = "";
	public String pager = "";
	public String fax = "";
	public String company = "";
	public String title = "";
	public String other = "";
	public String address = "";

}
