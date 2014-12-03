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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.Observance;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.TzId;
import net.fortuna.ical4j.model.property.TzOffsetTo;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.logger.QuickWriter;

import com.google.api.services.calendar.model.Event;
import com.google.gdata.data.DateTime;
import com.google.gdata.data.extensions.Recurrence;
import com.google.gdata.util.ServiceException;

/**
 * Google Calendar utilities.
 * 
 * <li>loadCalendar <li>updateEvents <li>removeEvents
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class GCalUtilities {

	// --- CONSTANTS ---

	public static final String ERROR_MARKER = "gcaldaemon-error";

	private static final long GOOGLE_CONNECTION_TIMEOUT = 1000L * 60 * 5;
	private static final long GOOGLE_RETRY_MILLIS = 1000L;
	private static final int HTTP_CONNECTION_TIMEOUT = 10000;
	private static final int HTTP_WAIT_TIMEOUT = 60000;

	private static final int MAX_POOLED_CONNECTIONS = 100;
	private static final int MAX_FEED_ENTRIES = 10000;

	private static final String GOOGLE_HTTPS_URL = "https://www.google.com";
	private static final String GOOGLE_HTTP_URL = "http://www.google.com";
	private static final String CALENDAR_FEED_POSTFIX = "/private/full";
	private static final String USER_AGENT = "Mozilla/5.0 (Windows; U;"
			+ " Windows NT 5.1; hu; rv:1.8.0.8) Gecko/20061025 Thunderbird/1.5.0.8";
	private static final String CALENDAR_FEED_PREFIX = GOOGLE_HTTPS_URL
			+ "/calendar/feeds/";
	private static final String METAFEED_URL = CALENDAR_FEED_PREFIX + "default";
	private static final String FEEDS_DEFAULT_PART = "/feeds/default/";
	private static final String CALENDAR_ICAL_PART = "/calendar/ical/";
	private static final String PRIVATE_BASIC_PART = "/private/basic.ics";

	private static final String UID_EXTENSION_NAME = "gcaldaemon-uid";
	private static final String CATEGORIES_EXTENSION_NAME = "gcaldaemon-categories";
	private static final String PRIORITY_EXTENSION_NAME = "gcaldaemon-priority";
	private static final String URL_EXTENSION_NAME = "gcaldaemon-url";

	private static final char[] CR_LF = "\r\n".toCharArray();
	private static final char[] ALARM_BEGIN = "\r\nBEGIN:VALARM\r\nTRIGGER;VALUE=DURATION:-P"
			.toCharArray();
	private static final char[] ALARM_END = "\r\nACTION:AUDIO\r\nEND:VALARM\r\n"
			.toCharArray();
	private static final char[] ALARM_MOZ_LASTACK = "\r\nX-MOZ-LASTACK:"
			.toCharArray();
	private static final char[] ALARM_RAIN_LASTACK = "X-RAINLENDAR-LASTALARMACK:"
			.toCharArray();

	private static final long LAST_ACK_TIMEOUT = 86400000L;

	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(GCalUtilities.class);

	// --- GLOBAL PROPERTIES ---

	private static boolean enableExtensions;
	private static boolean sendInvitations;
	private static boolean enableEmail;
	private static boolean enableSms;
	private static boolean enablePopup;

	// --- HTTP CONNECTION HANDLER ---

	private static final MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
	private static final HttpClient httpClient = new HttpClient(
			connectionManager);

	static final void globalInit() {
		try {

			// Set extended sync mode
			String value = System.getProperty("gcaldaemon.extended.sync",
					"false");
			enableExtensions = "true".equals(value);

			// Set send invitations
			value = System.getProperty("gcaldaemon.send.invitations", "false");
			sendInvitations = "true".equals(value);

			// Set enabled alarm types in the Google Calendar
			value = System.getProperty("gcaldaemon.remote.alarms",
					"email,sms,popup");
			boolean email = value.indexOf("mail") != -1;
			boolean sms = value.indexOf("sms") != -1;
			boolean popup = value.indexOf("pop") != -1;
			if (!email && !sms && !popup) {
				enableEmail = true;
				enableSms = true;
				enablePopup = true;
			} else {
				enableEmail = email;
				enableSms = sms;
				enablePopup = popup;
			}

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

	// --- PRIVATE CONSTRUCTOR ---

	private GCalUtilities() {
	}

	// --- GOOGLE ICALENDAR LOADER ---

	static final byte[] loadCalendar(Request request) throws Exception {
		return GCalUtilitiesV3.loadCalendar(request);
	}

	public static final byte[] exceptionToCalendar(Exception loadError)
			throws Exception {

		// Create new calendar
		Calendar calendar = new Calendar();
		PropertyList props = calendar.getProperties();
		props.add(new ProdId(ERROR_MARKER));
		props.add(Version.VERSION_2_0);
		props.add(CalScale.GREGORIAN);

		// Convert exception to event
		String title, content;
		if (loadError != null && loadError instanceof UnknownHostException) {
			title = "NETWORK DOWN";
			content = "Service temporarily unavailable!\r\n"
					+ "Please do not modify this calendar! "
					+ "Try clicking on the Reload or Refresh button. "
					+ "If this doesn't work, try again later.";
		} else {
			title = "UNAVAILABLE";
			content = "Service unavailable!\r\n"
					+ "Please do not modify this calendar!";
		}
		long eventStart = System.currentTimeMillis();
		long eventEnd = eventStart + 2700000L;
		VEvent event = new VEvent(new net.fortuna.ical4j.model.DateTime(
				eventStart), new net.fortuna.ical4j.model.DateTime(eventEnd),
				title);

		// Generate UID by start millis
		PropertyList args = event.getProperties();
		Uid uid = new Uid(ERROR_MARKER);
		args.add(uid);

		// Create description
		if (loadError != null) {
			String message = loadError.getMessage();
			if (message != null && message.length() != 0) {
				content = content + "\r\n[cause: " + message + ']';
			}
		}
		Description desc = new Description(content);
		args.add(desc);

		// Add marker event to calendar
		ComponentList events = calendar.getComponents();
		events.add(event);

		// Get calendar bytes
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
		CalendarOutputter outputter = new CalendarOutputter();
		outputter.output(calendar, buffer);
		return buffer.toByteArray();
	}

	// --- AUTOMATIC TIME ZONE MANAGEMENT ---

	private static final HashSet registeredTimeZones = new HashSet();

	private static final void registerTimeZones(String content, byte[] bytes) {
		try {
			StringTokenizer st = new StringTokenizer(content, "\r\n");
			HashSet timeZones = new HashSet();
			String line, timeZone;
			while (st.hasMoreTokens()) {
				line = st.nextToken();
				if (!line.startsWith("TZID:")) {
					continue;
				}
				timeZone = line.substring(5);
				if (timeZone.length() == 0) {
					continue;
				}
				if (registeredTimeZones.contains(timeZone)) {
					continue;
				}
				timeZones.add(timeZone);
			}
			if (timeZones.isEmpty()) {
				return;
			}
			Calendar calendar = ICalUtilities.parseCalendar(bytes);
			VTimeZone[] zones = ICalUtilities.getTimeZones(calendar);
			if (zones.length == 0) {
				return;
			}
			Component seasonalTime;
			TzOffsetTo offsetTo;
			String id, offset;
			VTimeZone zone;
			for (int i = 0; i < zones.length; i++) {
				zone = zones[i];
				seasonalTime = zone.getObservances().getComponent(
						Observance.STANDARD);
				if (seasonalTime == null) {
					seasonalTime = zone.getObservances().getComponent(
							Observance.DAYLIGHT);
				}
				id = zone.getTimeZoneId().getValue();
				if (registeredTimeZones.contains(id)) {
					continue;
				}
				if (seasonalTime == null) {
					continue;
				}
				offsetTo = (TzOffsetTo) seasonalTime
						.getProperty(Property.TZOFFSETTO);
				if (offsetTo == null) {
					continue;
				}
				registeredTimeZones.add(id);
				offset = offsetTo.getValue();
				log.debug("Set the offset of " + id + " to GMT" + offset + ".");
				if (!ICalUtilities.setTimeZone(id, offset)) {
					log.warn("Unknown time zone (" + id + ")!");
				}
			}
		} catch (Exception ignored) {
			log.debug(ignored);
		}
	}

	// --- EVENT FINDER ---

	static final Event findEvent(CachedCalendar calendar, VEvent event)
			throws Exception {
		return GCalUtilitiesV3.findEvent(calendar, event);
	}

	// --- EVENT CREATOR ---

	static final void insertEvents(CachedCalendar calendar,
			VTimeZone[] timeZones, VEvent[] events) throws Exception {
		GCalUtilitiesV3.insertEvents(calendar, timeZones, events);
	}

	private static final String getMessageBody(Exception exception) {
		if (exception == null) {
			return "";
		}
		String body = null;
		if (exception instanceof ServiceException) {
			body = ((ServiceException) exception).getResponseBody();
		}
		if (body == null || body.length() == 0) {
			body = exception.toString();
		}
		return body;
	}

	// --- EVENT UPDATER ---

	static final void updateEvents(CachedCalendar calendar,
			VTimeZone[] timeZones, VEvent[] events) throws Exception {
		GCalUtilitiesV3.updateEvents(calendar, timeZones, events);
	}

	private static final URL getFeedURL(Request request) throws Exception {
		String target = request.url;
		int i = target.indexOf("/ical/");
		if (i == -1) {
			throw new Exception("Malformed iCal URL, '/ical/' part not found: "
					+ request.url);
		}
		target = target.substring(i + 6);
		i = target.indexOf('/');
		if (i == -1) {
			throw new Exception(
					"Malformed iCal URL, 4th '/' character not found: "
							+ request.url);
		}
		target = target.substring(0, i);
		return new URL(CALENDAR_FEED_PREFIX + target + CALENDAR_FEED_POSTFIX);
	}

	// --- EVENT REMOVER ---

	static final void removeEvents(CachedCalendar calendar, VEvent[] events)
			throws Exception {
		GCalUtilitiesV3.removeEvents(calendar, events);
	}

	// --- ICAL EVENT TO GOOGLE EVENT CONVERTER ---

	static final DateTime toDateTime(Date date) throws Exception {
		if (date == null) {
			return null;
		}
		boolean isAllDay = date.toString().indexOf('T') == -1;
		DateTime dateTime;
		if (isAllDay) {
			dateTime = toOneDayEventDateTime(date);
		} else {
			dateTime = new DateTime(date, UTC);
		}
		dateTime.setDateOnly(isAllDay);
		return dateTime;
	}

	private static final DateTime toOneDayEventDateTime(Date date)
			throws Exception {

		// Convert one day event's date to UTC date
		String text = date.toString();
		GregorianCalendar calendar = new GregorianCalendar(UTC);
		calendar.set(GregorianCalendar.YEAR,
				Integer.parseInt(text.substring(0, 4)));
		calendar.set(GregorianCalendar.MONTH,
				Integer.parseInt(text.substring(4, 6)) - 1);
		calendar.set(GregorianCalendar.DAY_OF_MONTH,
				Integer.parseInt(text.substring(6)));
		calendar.set(GregorianCalendar.HOUR_OF_DAY, 0);
		calendar.set(GregorianCalendar.MINUTE, 0);
		calendar.set(GregorianCalendar.SECOND, 0);
		calendar.set(GregorianCalendar.MILLISECOND, 0);
		DateTime dateTime = new DateTime(calendar.getTime(), UTC);
		return dateTime;
	}

	private static final VTimeZone getRecurrenceTimeZone(VTimeZone[] timeZones,
			VEvent event) throws Exception {
		if (timeZones == null || timeZones.length == 0) {
			return null;
		}
		String tzid = getTimeZoneID(event);
		if (tzid != null) {
			VTimeZone timeZone;
			for (int i = 0; i < timeZones.length; i++) {
				timeZone = timeZones[i];
				TzId id = timeZone.getTimeZoneId();
				if (tzid.toLowerCase().equals(
						id.getValue().toString().toLowerCase())) {
					return timeZone;
				}
			}
		}
		return null;
	}

	private static final String getTimeZoneID(VEvent event) throws Exception {
		Property start = event.getProperty(Property.DTSTART);
		if (start != null) {
			String tzid = start.toString();
			if (tzid != null) {
				int s = tzid.indexOf(Property.TZID);
				if (s != -1) {
					int e = tzid.indexOf(':', s);
					if (e != -1) {
						return tzid.substring(s + 5, e);
					} else {
						return null;
					}
				} else {
					return null;
				}
			}
		}
		return null;
	}

	// --- GOOGLE EVENT FEED ---

	// --- EVENT FINDER ---

	private static final HashMap editURLMaps = new HashMap();
	private static final HashMap uidMaps = new HashMap();

	static final String getRemoteUID(CachedCalendar calendar, String id) {
		HashMap mappedUIDs = (HashMap) uidMaps.get(calendar.url);
		if (mappedUIDs == null) {
			return null;
		}
		return (String) mappedUIDs.get(id);
	}

	private static final VEvent parseRecurrence(Recurrence recurrence) {
		if (recurrence == null) {
			return null;
		}
		VEvent event = null;
		try {
			QuickWriter writer = new QuickWriter(300);
			writer.write("BEGIN:VCALENDAR\r\n");
			writer.write("VERSION:2.0\r\n");
			writer.write("PRODID:DUMMY\r\n");
			writer.write("CALSCALE:GREGORIAN\r\n");
			writer.write("BEGIN:VEVENT\r\n");
			writer.write("UID:DUMMY\r\n");
			writer.write("SUMMARY:DUMMY\r\n");
			writer.write(recurrence.getValue());
			writer.write("\r\nEND:VEVENT\r\n");
			writer.write("END:VCALENDAR\r\n");
			Calendar calendar = ICalUtilities.parseCalendar(writer.getBytes());
			return ICalUtilities.getEvents(calendar)[0];
		} catch (Exception ignored) {
			log.debug(ignored);
		}
		return event;
	}

	// --- GOOGLE CONNECTION POOL ---

	private static final HashMap servicePool = new HashMap();
	private static final HashSet invalidCredentials = new HashSet();

	public static final boolean hasInvalidCredentials(Request request) {
		return invalidCredentials.remove(request.url + '\t' + request.username
				+ '\t' + request.password);
	}

	public static final String normalizeUsername(String username) {
		if (username != null && username.length() > 0) {
			if (username.endsWith("@googlemail.com")
					|| username.endsWith("@gmail")
					|| username.endsWith("@googlemail")) {
				return username.substring(0, username.indexOf('@'))
						+ "@gmail.com";
			}
			if (username.indexOf('@') == -1) {
				log.warn("Malformed username (" + username + "@where)!");
			}
		}
		return username;
	}

	// --- LIST CALENDARS ---

	private static final Properties calendarNames = new Properties();

	public static final String[] getCalendarURLs(Request request, File workDir)
			throws Exception {
		return GCalUtilitiesV3.getCalendarURLs(request, workDir);
	}

	public static final String getCalendarName(String url, File workDir) {
		if (url == null || url.length() == 0) {
			return null;
		}
		if (calendarNames.isEmpty()) {
			loadCalendarNamesFromCache(workDir);
		}
		if (url.startsWith(GOOGLE_HTTP_URL)) {
			url = url.substring(GOOGLE_HTTP_URL.length());
		} else {
			if (url.startsWith(GOOGLE_HTTPS_URL)) {
				url = url.substring(GOOGLE_HTTPS_URL.length());
			}
		}
		String name = (String) calendarNames.get(url);
		if (name != null) {
			return name;
		}
		int i = url.indexOf("/private");
		if (i != -1) {
			url = url.substring(0, i);
			Iterator names = calendarNames.entrySet().iterator();
			Map.Entry entry;
			while (names.hasNext()) {
				entry = (Map.Entry) names.next();
				if (((String) entry.getKey()).startsWith(url)) {
					return (String) entry.getValue();
				}
			}
		}
		return null;
	}

	private static final void loadCalendarNamesFromCache(File workDir) {
		try {
			File file = new File(workDir, "gcal-names.txt");
			if (!file.isFile()) {
				return;
			}
			BufferedInputStream in = new BufferedInputStream(
					new FileInputStream(file));
			calendarNames.load(in);
			in.close();
		} catch (Exception ioException) {
			log.warn("Unable to load 'gcal-names.txt'!", ioException);
		}
	}

	private static final void saveCalendarNamesToCache(File workDir) {
		try {
			File file = new File(workDir, "gcal-names.txt");
			BufferedOutputStream out = new BufferedOutputStream(
					new FileOutputStream(file));
			calendarNames.store(out, "CALENDAR NAME CACHE");
			out.flush();
			out.close();
		} catch (Exception ioException) {
			log.warn("Unable to save 'gcal-names.txt'!", ioException);
		}
	}

}
