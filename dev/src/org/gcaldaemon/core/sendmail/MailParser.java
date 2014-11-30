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

import java.util.HashSet;

import org.gcaldaemon.logger.QuickWriter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX-based mail file parser.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
final class MailParser extends DefaultHandler {

	// --- VARIABLES ---

	private final QuickWriter charBuffer = new QuickWriter(100);
	private final HashSet to = new HashSet();
	private final HashSet cc = new HashSet();
	private final HashSet bcc = new HashSet();

	private String subject;
	private String body = "";

	// --- CONSTRUCTOR ---

	MailParser(String username) {
		subject = "Mail from " + username;
	}

	// --- SAX2 DEFAULT HANDLER METHODS ---

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String,
	 *      java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public final void startElement(String uri, String localNames, String qName,
			Attributes attributes) throws SAXException {
		charBuffer.flush();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.ContentHandler#characters(char[], int, int)
	 */
	public final void characters(char[] ch, int start, int length)
			throws SAXException {
		charBuffer.write(ch, start, length);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String,
	 *      java.lang.String, java.lang.String)
	 */
	public final void endElement(String uri, String localName, String qName)
			throws SAXException {
		String value = charBuffer.toString().trim();
		charBuffer.flush();
		if (value.length() == 0) {
			return;
		}
		qName = qName.toUpperCase();
		int i = qName.indexOf(':');
		if (i != -1) {
			qName = qName.substring(i + 1);
		}
		if (qName.equals("TO")) {
			to.add(value);
			return;
		}
		if (qName.equals("CC")) {
			cc.add(value);
			return;
		}
		if (qName.equals("BCC")) {
			bcc.add(value);
			return;
		}
		if (qName.equals("SUBJECT")) {
			subject = value;
			return;
		}
		if (qName.equals("BODY")) {
			body = value;
		}
	}

	// --- PROPERTY GETTERS ---

	/**
	 * @return Returns the bcc.
	 */
	final HashSet getBcc() {
		return bcc;
	}

	/**
	 * @return Returns the body.
	 */
	final String getBody() {
		return body;
	}

	/**
	 * @return Returns the cc.
	 */
	final HashSet getCc() {
		return cc;
	}

	/**
	 * @return Returns the subject.
	 */
	final String getSubject() {
		return subject;
	}

	/**
	 * @return Returns the to.
	 */
	final HashSet getTo() {
		return to;
	}

}
