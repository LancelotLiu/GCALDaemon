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
import java.util.LinkedList;
import java.util.List;
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
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Created;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Priority;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.TzId;
import net.fortuna.ical4j.model.property.TzOffsetTo;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.Version;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.logger.QuickWriter;

import com.google.gdata.client.GoogleService.InvalidCredentialsException;
import com.google.gdata.client.calendar.CalendarQuery;
import com.google.gdata.client.calendar.CalendarService;
import com.google.gdata.data.Content;
import com.google.gdata.data.DateTime;
import com.google.gdata.data.Link;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.TextConstruct;
import com.google.gdata.data.TextContent;
import com.google.gdata.data.calendar.CalendarEntry;
import com.google.gdata.data.calendar.CalendarEventEntry;
import com.google.gdata.data.calendar.CalendarEventFeed;
import com.google.gdata.data.calendar.CalendarFeed;
import com.google.gdata.data.calendar.EventWho;
import com.google.gdata.data.extensions.ExtendedProperty;
import com.google.gdata.data.extensions.OriginalEvent;
import com.google.gdata.data.extensions.Recurrence;
import com.google.gdata.data.extensions.Reminder;
import com.google.gdata.data.extensions.When;
import com.google.gdata.data.extensions.Where;
import com.google.gdata.data.extensions.BaseEventEntry.EventStatus;
import com.google.gdata.data.extensions.BaseEventEntry.Transparency;
import com.google.gdata.data.extensions.BaseEventEntry.Visibility;
import com.google.gdata.data.extensions.Who.AttendeeStatus;
import com.google.gdata.util.ResourceNotFoundException;
import com.google.gdata.util.ServiceException;

/**
 * Google Calendar utilities.
 * 
 * <li>loadCalendar
 * <li>updateEvents
 * <li>removeEvents
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
		GetMethod get = null;
		String icalURL;

		// Get auth token
		String token = null;
		if (request.url.indexOf("/private-") == -1 && request.username != null
				&& request.password != null) {
			CalendarService service = new CalendarService(Configurator.VERSION
					.replace(' ', '-'));
			token = service.getAuthToken(request.username, request.password,
					null, null, CalendarService.CALENDAR_SERVICE,
					Configurator.VERSION);
		}

		// Load calendar
		for (int tries = 0;; tries++) {
			try {

				// Create ical URL
				if (tries < 2) {
					icalURL = GOOGLE_HTTPS_URL + request.url;
				} else {
					icalURL = GOOGLE_HTTP_URL + request.url;
				}
				int i = icalURL.indexOf("basic.ics");
				if (i != -1) {
					icalURL = icalURL.substring(0, i + 9);
				}
				get = new GetMethod(icalURL);
				get.addRequestHeader("User-Agent", USER_AGENT);
				get.setFollowRedirects(true);
				if (token != null) {

					// Set AuthSub token
					get.addRequestHeader("Authorization", "GoogleLogin auth=\""
							+ token + '"');
				}

				// Load iCal file from Google
				log.debug("Loading calendar from " + icalURL + "...");
				int status = httpClient.executeMethod(get);
				if (status == -1) {
					throw new Exception("Invalid HTTP response status (-1)!");
				}
				byte[] bytes = get.getResponseBody();

				// Validate content
				String content;
				if (enableExtensions) {
					content = StringUtils.decodeToString(bytes,
							StringUtils.UTF_8);
				} else {
					content = StringUtils.decodeToString(bytes,
							StringUtils.US_ASCII);
				}
				if (content.indexOf("BEGIN:VCALENDAR") == -1) {
					log.warn("Received file from Google:\r\n" + content);
					throw new Exception("Invalid iCal file: " + icalURL);
				}

				// Register time zones
				registerTimeZones(content, bytes);

				// Insert extended properties
				if (enableExtensions) {
					bytes = insertExtensions(request, content, bytes);
				}

				// Cleanup cache
				editURLMaps.remove(request.url);
				uidMaps.remove(request.url);
				log.debug("Calendar loaded successfully (" + bytes.length
						+ " bytes).");

				// Return ICS calendar file
				return bytes;
			} catch (UnknownHostException networkDown) {
				log.debug("Network down!");
				return exceptionToCalendar(networkDown);
			} catch (Exception loadError) {
				if (tries == 5) {
					log.error("Unable to load calendar!", loadError);
					return exceptionToCalendar(loadError);
				}
				log.debug("Connection refused, reconnecting...");
				Thread.sleep(GOOGLE_RETRY_MILLIS);
			} finally {
				if (get != null) {
					get.releaseConnection();
				}
			}
		}
	}

	private static final byte[] exceptionToCalendar(Exception loadError)
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

	// --- ICAL CONVERTER ---

	private static final byte[] insertExtensions(Request request,
			String content, byte[] bytes) {
		try {

			// Create calendar's private feed URL
			URL feedURL = getFeedURL(request);

			// Get service from pool
			CalendarService service = getService(request);

			// Build edit map
			CachedCalendar calendar = new CachedCalendar();
			calendar.url = request.url;
			calendar.username = request.username;
			calendar.password = request.password;
			calendar.previousBody = bytes;
			HashMap extensions = createEditURLMap(service, calendar, feedURL);
			if (extensions == null || extensions.isEmpty()) {
				return bytes;
			}

			// Last ack
			boolean containsValarm = content.indexOf("BEGIN:VALARM") != -1;
			long lastAck = System.currentTimeMillis() - LAST_ACK_TIMEOUT;
			net.fortuna.ical4j.model.DateTime now = new net.fortuna.ical4j.model.DateTime(
					lastAck);
			now.setUtc(true);
			char[] ack = now.toString().toCharArray();

			// Insert extensions
			StringTokenizer st = new StringTokenizer(content, "\r\n");
			QuickWriter writer = new QuickWriter(bytes.length * 2);
			String extension, line, id = null;
			int days, hours, mins, i;
			Reminder reminder;
			Integer number;
			while (st.hasMoreTokens()) {
				line = st.nextToken();

				// Skip extended ical properties
				if (line.startsWith(Property.CATEGORIES)
						|| line.startsWith(Property.PRIORITY)
						|| line.startsWith(Property.URL)) {
					continue;
				}

				// Get event ID
				if (line.startsWith("UID")) {
					id = line.substring(4);
					writer.write(line);
					writer.write(CR_LF);
					continue;
				}

				// Get recurrence ID
				if (line.startsWith("RECURRENCE-ID") && id != null) {
					i = line.lastIndexOf(':');
					if (i != -1) {
						try {
							RecurrenceId recurrenceId = new RecurrenceId(line
									.substring(i + 1));
							Date date = recurrenceId.getDate();
							if (date != null) {
								id = id + '!' + date.getTime();
							}
						} catch (Exception ignored) {
							log.warn(ignored);
						}
					}
					writer.write(line);
					writer.write(CR_LF);
					continue;
				}

				if (line.startsWith("END:VEVENT") && id != null) {

					// Insert reminder
					reminder = (Reminder) extensions.get(id + "\ta");
					if (reminder != null && !containsValarm) {
						writer.write(ALARM_RAIN_LASTACK);
						writer.write(ack);
						writer.write(ALARM_BEGIN);
						number = reminder.getMinutes();
						if (number != null) {
							mins = number.intValue();
							if (mins <= 45) {

								// Valid minutes: 5, 10, 15, 20, 25, 30, 45
								mins = mins / 5 * 5;
								if (mins == 35 || mins == 40) {
									mins = 45;
								} else {
									if (mins == 0) {
										mins = 5;
									}
								}

								// T1M -> Minutes
								writer.write('T');
								writer.write(Integer.toString(mins));
								writer.write('M');
							} else {

								// Valid hours: 1, 2, 3
								hours = mins / 60;
								if (hours == 0) {
									hours = 1;
								}
								if (hours <= 3) {

									// T1H -> Hours
									writer.write('T');
									writer.write(Integer.toString(hours));
									writer.write('H');
								} else {

									// Valid days: 1, 2, 7
									days = hours / 24;
									if (days == 0) {
										days = 1;
									}
									if ((days > 2 && days < 7) || days > 7) {
										days = 7;
									}

									// 1D -> Days
									writer.write(Integer.toString(days));
									writer.write('D');
								}
							}
						} else {
							writer.write("T1H");
						}
						writer.write(ALARM_MOZ_LASTACK);
						writer.write(ack);
						writer.write(ALARM_END);
					}

					// Insert categories
					extension = (String) extensions.get(id + "\tc");
					if (extension != null && extension.length() != 0) {
						writer.write(Property.CATEGORIES);
						writer.write(':');
						writer.write(extension);
						writer.write(CR_LF);
					}

					// Insert priority
					extension = (String) extensions.get(id + "\tp");
					if (extension != null && extension.length() != 0) {
						writer.write(Property.PRIORITY);
						writer.write(':');
						writer.write(extension);
						writer.write(CR_LF);
					}

					// Insert URL
					extension = (String) extensions.get(id + "\tu");
					if (extension != null && extension.length() != 0) {
						writer.write(Property.URL);
						writer.write(':');
						writer.write(extension);
						writer.write(CR_LF);
					}
					id = null;
				}
				writer.write(line);
				writer.write(CR_LF);
			}

			// Encode extended ics file
			bytes = StringUtils.encodeArray(writer.getChars(),
					StringUtils.UTF_8);
		} catch (Exception ignored) {
			log.debug("Unable to insert extensions!", ignored);
		}
		return bytes;
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

	static final CalendarEventEntry findEvent(CachedCalendar calendar,
			VEvent event) throws Exception {

		// Create calendar's private feed URL
		URL feedURL = getFeedURL(calendar);

		// Get service from pool
		CalendarService service = getService(calendar);

		// Find remote event
		try {
			return getGoogleEntry(service, calendar, feedURL, event);
		} catch (ServiceException invalidEntry) {

			// Remap events
			editURLMaps.remove(calendar.url);
			uidMaps.remove(calendar.url);
			return getGoogleEntry(service, calendar, feedURL, event);
		}
	}

	// --- EVENT CREATOR ---

	static final void insertEvents(CachedCalendar calendar,
			VTimeZone[] timeZones, VEvent[] events) throws Exception {

		// Create calendar's private feed URL
		URL feedURL = getFeedURL(calendar);

		// Get service from pool
		CalendarService service = getService(calendar);

		// Find RRule
		boolean foundRRule = false;
		int n;
		for (n = 0; n < events.length; n++) {
			foundRRule = events[n].getProperty(Property.RRULE) != null;
			if (foundRRule) {
				break;
			}
		}

		// Loop on events
		for (n = 0; n < events.length; n++) {

			// Insert event
			insertEvent(calendar, timeZones, events[n], foundRRule, service,
					feedURL);
		}

		// Clear cache
		editURLMaps.remove(calendar.url);
		uidMaps.remove(calendar.url);
	}

	private static final void insertEvent(CachedCalendar calendar,
			VTimeZone[] timeZones, VEvent event, boolean foundRRule,
			CalendarService service, URL feedURL) throws Exception {

		// Clear cache
		if (foundRRule && event.getRecurrenceId() != null) {
			foundRRule = false;
			editURLMaps.remove(calendar.url);
			uidMaps.remove(calendar.url);
			CachedCalendar swap = new CachedCalendar();
			swap.lastModified = calendar.lastModified;
			swap.url = calendar.url;
			swap.username = calendar.username;
			swap.password = calendar.password;
			swap.filePath = calendar.filePath;
			swap.toDoBlock = calendar.toDoBlock;
			swap.body = calendar.body;
			swap.previousBody = calendar.body;
			calendar = swap;
		}

		// Convert event to Google entry
		CalendarEventEntry newEntry = convertVEvent(calendar, timeZones, event);

		// Absolute time = clear reminders mark
		List reminders = newEntry.getReminder();
		if (reminders != null && !reminders.isEmpty()) {
			Reminder reminder = (Reminder) reminders.get(0);
			DateTime absolute = reminder.getAbsoluteTime();
			if (absolute != null) {
				reminders.clear();
			}
		}

		// Insert new event
		if (log.isDebugEnabled()) {
			log.debug("Inserting event (" + ICalUtilities.getEventTitle(event)
					+ ") into Google Calendar...");
		}
		try {
			service.insert(feedURL, newEntry);
		} catch (Exception exception) {

			// Get remote message
			String msg = getMessageBody(exception);

			// Skip insert
			if (msg.indexOf("no instances") != -1
					|| msg.indexOf("read-only") != -1) {
				log.debug("Unable to insert event ("
						+ ICalUtilities.getEventTitle(event) + ")!\r\n" + msg);
				return;
			}

			// Remove reminders
			if (msg.indexOf("many reminder") != -1) {
				List reminder = newEntry.getReminder();
				log.warn("Too many reminders!");
				if (reminder != null) {
					reminder.clear();
				}
			}

			// Resend request
			Thread.sleep(GOOGLE_RETRY_MILLIS);
			try {
				service.insert(feedURL, newEntry);
			} catch (Exception error) {
				log.warn("Unable to insert event ("
						+ ICalUtilities.getEventTitle(event) + ")!\r\n" + msg);
			}
		}
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

		// Create calendar's private feed URL
		URL feedURL = getFeedURL(calendar);

		// Get service from pool
		CalendarService service = getService(calendar);

		// Loop on events
		boolean searchRRule = true;
		boolean foundRRule = false;
		VEvent event;
		for (int n = 0; n < events.length; n++) {
			event = events[n];

			// Find original event
			CalendarEventEntry oldEntry = getGoogleEntry(service, calendar,
					feedURL, event);

			if (oldEntry == null) {

				// Find RRule
				if (searchRRule) {
					searchRRule = false;
					for (int m = 0; m < events.length; m++) {
						foundRRule = events[m].getProperty(Property.RRULE) != null;
						if (foundRRule) {
							break;
						}
					}
				}

				// Insert event
				insertEvent(calendar, timeZones, event, foundRRule, service,
						feedURL);

				// Clear UID cache
				editURLMaps.remove(calendar.url);
				uidMaps.remove(calendar.url);
			} else {

				// Event found in Google Calendar
				Link editLink = oldEntry.getEditLink();
				if (editLink == null) {
					log.warn("Unable to update read-only event ("
							+ ICalUtilities.getEventTitle(event) + ")!");
					continue;
				}
				String editURL = editLink.getHref();

				// Convert event to Google entry
				CalendarEventEntry newEntry = convertVEvent(calendar,
						timeZones, event);

				// Check recurrence
				boolean recurrenceChanged;
				Recurrence recurrence = null;
				if (oldEntry.getRecurrence() == null) {
					recurrence = newEntry.getRecurrence();
					recurrenceChanged = recurrence != null;
				} else {
					recurrence = newEntry.getRecurrence();
					recurrenceChanged = recurrence == null;
				}

				// Copy reminders
				List reminders = newEntry.getReminder();
				if (reminders.isEmpty()) {
					if (!enableExtensions) {
						List oldReminders = oldEntry.getReminder();
						if (oldReminders != null && !oldReminders.isEmpty()) {
							reminders.addAll(oldReminders);
						}
					}
				} else {
					Reminder reminder = (Reminder) reminders.get(0);
					DateTime absolute = reminder.getAbsoluteTime();
					if (absolute != null) {

						// Absolute time = clear reminders mark
						reminders.clear();
					}
				}

				// Do modifications
				if (recurrenceChanged) {
					if (log.isDebugEnabled()) {
						log.debug("Recreating event ("
								+ ICalUtilities.getEventTitle(event)
								+ ") in Google Calendar...");
					}
					boolean deleted = false;
					try {

						// Remove and recreate entry
						editURLMaps.remove(calendar.url);
						uidMaps.remove(calendar.url);
						service.delete(new URL(editURL));
						deleted = true;
						service.insert(feedURL, newEntry);
					} catch (ResourceNotFoundException notFound) {
						log.warn("Event (" + ICalUtilities.getEventTitle(event)
								+ ") not found in Google Calendar!");
					} catch (Exception exception) {

						// Get remote message
						String msg = getMessageBody(exception);

						// Skip insert
						if (msg.indexOf("no instances") != -1
								|| msg.indexOf("read-only") != -1) {
							log.debug("Unable to recreate event ("
									+ ICalUtilities.getEventTitle(event)
									+ ")!\r\n" + msg);
							continue;
						}

						// Remove reminders
						if (msg.indexOf("many reminder") != -1) {
							List reminder = newEntry.getReminder();
							log.warn("Too many reminders!");
							if (reminder != null) {
								reminder.clear();
							}
						}

						// Resend request
						Thread.sleep(GOOGLE_RETRY_MILLIS);
						try {
							if (!deleted) {
								service.delete(new URL(editURL));
							}
							service.insert(feedURL, newEntry);
						} catch (Exception error) {
							log.warn("Unable to recreate event ("
									+ ICalUtilities.getEventTitle(event)
									+ ")!\r\n" + msg);
						}
					}
				} else {
					if (log.isDebugEnabled()) {
						log.debug("Updating event ("
								+ ICalUtilities.getEventTitle(event)
								+ ") in Google Calendar...");
					}
					try {

						// Simple update
						service.update(new URL(editURL), newEntry);
					} catch (ResourceNotFoundException notFound) {
						log.warn("Event (" + ICalUtilities.getEventTitle(event)
								+ ") not found in Google Calendar!");
					} catch (Exception exception) {

						// Get remote message
						String msg = getMessageBody(exception);

						// Skip insert
						if (msg.indexOf("cannot override") != -1
								|| msg.indexOf("read-only") != -1) {
							log.debug("Unable to update event ("
									+ ICalUtilities.getEventTitle(event)
									+ ")!\r\n" + msg);
							continue;
						}

						// Delete event
						if (msg.indexOf("no instances") != -1) {
							try {
								removeRecurringEvent(calendar, service, event,
										editURL, feedURL);
							} catch (Exception ignored) {
								log.debug("Unable to delete faulty event ("
										+ ICalUtilities.getEventTitle(event)
										+ ")!", ignored);
							}
							continue;
						}

						// Remove reminders
						if (msg.indexOf("many reminder") != -1) {
							List reminder = newEntry.getReminder();
							log.warn("Too many reminders!");
							if (reminder != null) {
								reminder.clear();
							}
						}

						// Resend request
						Thread.sleep(GOOGLE_RETRY_MILLIS);
						try {
							service.update(new URL(editURL), newEntry);
						} catch (Exception error) {
							log.warn("Unable to update event ("
									+ ICalUtilities.getEventTitle(event)
									+ ")!\r\n" + msg);
						}
					}
				}
			}
		}
	}

	private static final void removeRecurringEvent(CachedCalendar calendar,
			CalendarService service, VEvent parent, String editURL, URL feedURL)
			throws Exception {
		editURLMaps.remove(calendar.url);
		uidMaps.remove(calendar.url);
		VEvent[] events = ICalUtilities.getEvents(ICalUtilities
				.parseCalendar(calendar.previousBody));
		String uid = ICalUtilities.getUid(parent);
		if (uid == null) {
			return;
		}
		CalendarEventEntry oldEntry;
		VEvent child;
		String id;
		for (int c = 0; c < events.length; c++) {
			child = events[c];
			id = ICalUtilities.getUid(child);
			if (id == null) {
				continue;
			}
			if (id.startsWith(uid) && !id.equals(uid)) {
				oldEntry = getGoogleEntry(service, calendar, feedURL, child);
				if (oldEntry != null) {
					Thread.sleep(GOOGLE_RETRY_MILLIS);
					service.delete(new URL(oldEntry.getEditLink().getHref()));
				}
			}
		}
		Thread.sleep(GOOGLE_RETRY_MILLIS);
		service.delete(new URL(editURL));
		editURLMaps.remove(calendar.url);
		uidMaps.remove(calendar.url);
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

		// Create calendar's private feed URL
		URL feedURL = getFeedURL(calendar);

		// Get service from pool
		CalendarService service = getService(calendar);

		// Loop on events
		VEvent event;
		for (int n = 0; n < events.length; n++) {
			event = events[n];

			CalendarEventEntry oldEntry = getGoogleEntry(service, calendar,
					feedURL, event);

			// Remove event
			if (oldEntry != null) {
				if (log.isDebugEnabled()) {
					log.debug("Removing event ("
							+ ICalUtilities.getEventTitle(event)
							+ ") from Google Calendar...");
				}
				try {
					Link editLink = oldEntry.getEditLink();
					if (editLink == null) {
						log.warn("Unable to remove read-only event ("
								+ ICalUtilities.getEventTitle(event) + ")!");
						continue;
					}
					service.delete(new URL(editLink.getHref()));
				} catch (ResourceNotFoundException notFound) {
					log.warn("Event (" + ICalUtilities.getEventTitle(event)
							+ ") not found in Google Calendar!");
				} catch (Exception exception) {

					// Get remote message
					String msg = getMessageBody(exception);

					// Skip delete
					if (msg.indexOf("no instances") != -1
							|| msg.indexOf("read-only") != -1) {
						log.debug("Unable to remove event ("
								+ ICalUtilities.getEventTitle(event) + ")!\r\n"
								+ msg);
						continue;
					}

					// Resend request
					Thread.sleep(GOOGLE_RETRY_MILLIS);
					try {
						Link editLink = oldEntry.getEditLink();
						service.delete(new URL(editLink.getHref()));
					} catch (Exception error) {
						log.warn("Unable to remove event ("
								+ ICalUtilities.getEventTitle(event) + ")!\r\n"
								+ msg);
					}
				}
			} else {
				log.warn("Event (" + ICalUtilities.getEventTitle(event)
						+ ") not found in Google Calendar!");
			}
		}
	}

	// --- ICAL EVENT TO GOOGLE EVENT CONVERTER ---

	private static final CalendarEventEntry convertVEvent(
			CachedCalendar calendar, VTimeZone[] timeZones, VEvent event)
			throws Exception {
		CalendarEventEntry entry = new CalendarEventEntry();
		entry.setCanEdit(true);
		entry.setDraft(new Boolean(false));
		entry.setQuickAdd(false);
		entry.setUpdated(new DateTime(new Date(), UTC));
		entry.setSendEventNotifications(sendInvitations);
		String text;

		// Convert event UID to extended property
		String uid = ICalUtilities.getUid(event);
		if (uid != null) {
			ExtendedProperty extension = new ExtendedProperty();
			extension.setName(UID_EXTENSION_NAME);
			extension.setValue(uid);
			entry.addExtendedProperty(extension);
		}

		// Convert priority to extended property
		Priority priority = event.getPriority();
		if (priority != null) {
			text = priority.getValue();
			if (text != null && text.length() != 0) {
				ExtendedProperty extension = new ExtendedProperty();
				extension.setName(PRIORITY_EXTENSION_NAME);
				extension.setValue(text);
				entry.addExtendedProperty(extension);
			}
		}

		// Convert URL to extended property
		Url url = event.getUrl();
		if (url != null) {
			text = url.getValue();
			if (text != null && text.length() != 0) {
				ExtendedProperty extension = new ExtendedProperty();
				extension.setName(URL_EXTENSION_NAME);
				extension.setValue(text);
				entry.addExtendedProperty(extension);
			}
		}

		// Convert URL to extended property
		Property categories = event.getProperty(Property.CATEGORIES);
		if (categories != null) {
			text = categories.getValue();
			if (text != null && text.length() != 0 && !text.startsWith("http")) {
				ExtendedProperty extension = new ExtendedProperty();
				extension.setName(CATEGORIES_EXTENSION_NAME);
				extension.setValue(text);
				entry.addExtendedProperty(extension);
			}
		}

		// Convert created to published
		Created created = event.getCreated();
		if (created != null) {
			DateTime published = toDateTime(created.getDate());
			entry.setPublished(published);
		}

		// Convert summary to title
		Summary summary = event.getSummary();
		if (summary != null) {
			text = summary.getValue();
			if (text != null && text.length() != 0) {
				entry.setTitle(new PlainTextConstruct(text));
			}
		}

		// Convert description to content
		Description desc = event.getDescription();
		if (desc != null) {
			text = desc.getValue();
			if (text != null && text.length() != 0) {
				entry.setContent(new PlainTextConstruct(text));
			}
		}

		// Convert start date
		DtStart start = event.getStartDate();
		if (start == null) {
			Date date = null;
			if (created != null) {
				date = created.getDate();
			}
			if (date == null) {
				date = new Date();
			}
			start = new DtStart(date);
		}
		Date startDate = start.getDate();

		// Convert end date
		DtEnd end = event.getEndDate();
		if (end == null) {
			end = new DtEnd(startDate);
		}
		Date endDate = end.getDate();

		// Check dates
		if (startDate.after(endDate)) {
			Date swap = startDate;
			startDate = endDate;
			endDate = swap;
		}

		// Set when
		When startAndEnd = new When();
		startAndEnd.setStartTime(toDateTime(startDate));
		startAndEnd.setEndTime(toDateTime(endDate));
		entry.addTime(startAndEnd);

		// Convert location to where
		Location location = event.getLocation();
		if (location != null) {
			text = location.getValue();
			if (text != null) {
				Where where = new Where(text, text, text);
				entry.addLocation(where);
			}
		}

		// Convert status (tentative, confirmed, canceled)
		Status status = event.getStatus();
		if (status != null) {
			EventStatus eventStatus;
			text = status.getValue();
			if (Status.VEVENT_CANCELLED.getValue().equals(text)) {
				eventStatus = EventStatus.CANCELED;
			} else {
				if (Status.VEVENT_CONFIRMED.getValue().equals(text)) {
					eventStatus = EventStatus.CONFIRMED;
				} else {
					eventStatus = EventStatus.TENTATIVE;
				}
			}
			entry.setStatus(eventStatus);
		}

		// Convert classification to visibility (public / private)
		Clazz clazz = event.getClassification();
		if (clazz != null) {
			Visibility visible;
			text = clazz.getValue();
			if (Clazz.PUBLIC.getValue().equals(text)) {
				visible = Visibility.PUBLIC;
			} else {
				if (Clazz.PRIVATE.getValue().equals(text)) {
					visible = Visibility.PRIVATE;
				} else {
					visible = Visibility.DEFAULT;
				}
			}
			entry.setVisibility(visible);
		} else {
			entry.setVisibility(Visibility.DEFAULT);
		}

		// Convert transparency (transparent / opaque = free / busy)
		Transp transp = event.getTransparency();
		if (transp == null) {

			// Default is 'Available' (=free or transparent)
			entry.setTransparency(Transparency.TRANSPARENT);
		} else {
			if (Transp.TRANSPARENT.getValue().equals(transp.getValue())) {
				entry.setTransparency(Transparency.TRANSPARENT);
			} else {
				entry.setTransparency(Transparency.OPAQUE);
			}
		}

		// Convert attendees
		String[] emails = ICalUtilities.getAttendees(event);
		if (emails != null) {
			for (int i = 0; i < emails.length; i++) {
				EventWho who = new EventWho();
				who.setEmail(emails[i]);
				if (!sendInvitations) {
					who.setAttendeeStatus(AttendeeStatus.EVENT_TENTATIVE);
				}
				entry.addParticipant(who);
			}
		}

		// Convert recurrence
		if (start != null && end != null) {
			Property rRule = event.getProperty(Property.RRULE);
			if (rRule != null) {
				VTimeZone timeZone = null;

				// Find time zone
				timeZone = getRecurrenceTimeZone(timeZones, event);

				// Get recurrence exceptions
				net.fortuna.ical4j.model.Date[] dates = ICalUtilities
						.getExceptionDates(event);

				// Create recurrence value
				Recurrence recurrence = new Recurrence();
				QuickWriter writer = new QuickWriter(500);
				writer.write(start.toString().trim());
				writer.write(CR_LF);
				writer.write(end.toString().trim());
				writer.write(CR_LF);
				writer.write(rRule.toString().trim());
				if (dates != null) {
					for (int i = 0; i < dates.length; i++) {
						writer.write(CR_LF);
						writer.write(Property.EXDATE);
						writer.write(':');
						if (dates[i] instanceof net.fortuna.ical4j.model.DateTime) {
							net.fortuna.ical4j.model.DateTime dateTime = (net.fortuna.ical4j.model.DateTime) dates[i];
							dateTime.setUtc(true);
						}
						writer.write(dates[i].toString());
					}
				}
				if (timeZone != null) {
					writer.write(CR_LF);
					writer.write(timeZone.toString().trim());
				}
				writer.write(CR_LF);
				recurrence.setValue(writer.toString());
				entry.setRecurrence(recurrence);
			}
		}

		// Convert recurrenceID
		RecurrenceId rid = event.getRecurrenceId();
		if (rid != null) {
			Uid property = event.getUid();
			if (property != null) {
				String id = property.getValue();
				if (id != null) {

					// Get service from pool
					CalendarService service = getService(calendar);

					// Create calendar's private feed URL
					URL feedURL = getFeedURL(calendar);

					// Get original event
					CalendarEventEntry parent = getGoogleEntryByUID(service,
							calendar, feedURL, id);
					if (parent != null) {
						String originalHref = parent.getSelfLink().getHref();
						String originalID = originalHref;
						int i = originalID.lastIndexOf('/');
						if (i != -1) {
							originalID = originalID.substring(i + 1);
						}

						OriginalEvent original = new OriginalEvent();
						original.setOriginalId(originalID);
						original.setHref(originalHref);
						When when = new When();
						when.setStartTime(toDateTime(rid.getDate()));
						original.setOriginalStartTime(when);
						entry.setOriginalEvent(original);
					}
				}
			}
		}

		// Convert reminder
		int mins = ICalUtilities.getAlarmMinutes(event);
		if (mins != -1) {
			Reminder reminder1 = new Reminder();
			Reminder reminder2 = new Reminder();
			Reminder reminder3 = new Reminder();
			reminder1.setMethod(Reminder.Method.ALERT);
			reminder2.setMethod(Reminder.Method.EMAIL);
			reminder3.setMethod(Reminder.Method.SMS);
			Integer holder;
			if (mins == 0) {

				// Absolute time = clear reminders mark
				DateTime dummy = new DateTime(0);
				reminder1.setAbsoluteTime(dummy);
				reminder2.setAbsoluteTime(dummy);
				reminder3.setAbsoluteTime(dummy);
			} else {
				if (mins <= 45) {

					// Valid minutes: 5, 10, 15, 20, 25, 30, 45
					mins = mins / 5 * 5;
					if (mins == 35 || mins == 40) {
						mins = 45;
					} else {
						if (mins == 0) {
							mins = 5;
						}
					}
					holder = new Integer(mins);
					reminder1.setMinutes(holder);
					reminder2.setMinutes(holder);
					reminder3.setMinutes(holder);
				} else {

					// Valid hours: 1, 2, 3
					int hours = mins / 60;
					if (hours == 0) {
						hours = 1;
					}
					if (hours <= 3) {
						holder = new Integer(hours);
						reminder1.setHours(holder);
						reminder2.setHours(holder);
						reminder3.setHours(holder);
					} else {

						// Valid days: 1, 2, 7
						int days = hours / 24;
						if (days == 0) {
							days = 1;
						}
						if ((days > 2 && days < 7) || days > 7) {
							days = 7;
						}
						holder = new Integer(days);
						reminder1.setDays(holder);
						reminder2.setDays(holder);
						reminder3.setDays(holder);
					}
				}
			}
			// Set "Alert" alarm
			if (enablePopup) {
				entry.getReminder().add(reminder1);
			}

			// Set "E-mail" alarm
			if (enableEmail) {
				entry.getReminder().add(reminder2);
			}

			// Set "SMS" alarm
			if (enableSms) {
				entry.getReminder().add(reminder3);
			}
		}

		return entry;
	}

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
		calendar.set(GregorianCalendar.YEAR, Integer.parseInt(text.substring(0,
				4)));
		calendar.set(GregorianCalendar.MONTH, Integer.parseInt(text.substring(
				4, 6)) - 1);
		calendar.set(GregorianCalendar.DAY_OF_MONTH, Integer.parseInt(text
				.substring(6)));
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

	private static final List getGoogleEntries(CalendarService service,
			CachedCalendar calendar, URL feedURL) throws Exception {

		// Request feed
		CalendarEventFeed feed;
		for (int tries = 0;; tries++) {
			try {
				CalendarQuery query = new CalendarQuery(feedURL);
				query.setMaxResults(MAX_FEED_ENTRIES);
				feed = (CalendarEventFeed) service.query(query,
						CalendarEventFeed.class);
				break;
			} catch (Exception loadError) {
				if (tries == 5) {
					throw loadError;
				}
				log.debug("Connection refused, reconnecting...");

				// Rebuild connection
				Thread.sleep(GOOGLE_RETRY_MILLIS);
				servicePool.remove(calendar.url);
				service = getService(calendar);
			}
		}

		// Return list of CalendarEventEntries
		return feed.getEntries();
	}

	// --- EVENT FINDER ---

	private static final HashMap editURLMaps = new HashMap();
	private static final HashMap uidMaps = new HashMap();

	private static final CalendarEventEntry getGoogleEntry(
			CalendarService service, CachedCalendar calendar, URL feedURL,
			VEvent event) throws Exception {

		// Get local UID
		String uid = ICalUtilities.getUid(event);
		if (uid == null) {
			return null;
		}

		// Request entry from Google
		return getGoogleEntryByUID(service, calendar, feedURL, uid);
	}

	private final static CalendarEventEntry getGoogleEntryByUID(
			CalendarService service, CachedCalendar calendar, URL feedURL,
			String uid) throws Exception {

		// Create edit URL map
		if (!editURLMaps.containsKey(calendar.url)) {
			createEditURLMap(service, calendar, feedURL);
		}

		// Get editURL
		HashMap editURLs = (HashMap) editURLMaps.get(calendar.url);
		if (editURLs == null) {
			return null;
		}
		URL editURL = (URL) editURLs.get(uid);
		if (editURL == null) {
			uid = getRemoteUID(calendar, uid);
			if (uid != null) {
				editURL = (URL) editURLs.get(uid);
				if (editURL == null) {
					return null;
				}
			} else {
				return null;
			}
		}

		// Load event
		for (int tries = 0;; tries++) {
			try {
				return (CalendarEventEntry) service.getEntry(editURL,
						CalendarEventEntry.class);
			} catch (Exception loadError) {
				if (tries == 5) {
					log.debug("Unable to load event (" + editURL + ")!",
							loadError);
					return null;
				}
				log.debug("Connection refused, reconnecting...");

				// Rebuild connection
				Thread.sleep(GOOGLE_RETRY_MILLIS);
				servicePool.remove(calendar.url);
				service = getService(calendar);
			}
		}
	}

	static final String getRemoteUID(CachedCalendar calendar, String id) {
		HashMap mappedUIDs = (HashMap) uidMaps.get(calendar.url);
		if (mappedUIDs == null) {
			return null;
		}
		return (String) mappedUIDs.get(id);
	}

	private static final HashMap createEditURLMap(CalendarService service,
			CachedCalendar calendar, URL feedURL) throws Exception {

		// Create alarm registry
		HashMap extensionMap;
		if (enableExtensions) {
			extensionMap = new HashMap();
		} else {
			extensionMap = null;
		}

		// Create edit URL map
		List entries = getGoogleEntries(service, calendar, feedURL);
		HashMap editURLs = new HashMap();
		HashMap remoteUIDs = new HashMap();
		editURLMaps.put(calendar.url, editURLs);
		uidMaps.put(calendar.url, remoteUIDs);
		Calendar oldCalendar = ICalUtilities
				.parseCalendar(calendar.previousBody);
		VEvent[] events = ICalUtilities.getEvents(oldCalendar);

		// Loop on events
		VEvent event;
		HashMap dateCache = new HashMap();
		for (int n = 0; n < events.length; n++) {
			event = events[n];

			// Get local UID and RID
			String uid = ICalUtilities.getUid(event);
			if (uid == null) {
				continue;
			}

			// Find original event
			CalendarEventEntry oldEntry = findEntry(entries, event, dateCache);
			if (oldEntry == null) {
				continue;
			}

			// Get alarm
			if (enableExtensions) {
				List reminders = oldEntry.getReminder();
				if (reminders != null && !reminders.isEmpty()) {
					extensionMap.put(uid + "\ta", reminders.get(0));
				}
			}

			// Bind local UID to remote edit URL
			Link editLink = oldEntry.getEditLink();
			if (editLink == null) {
				continue;
			}
			String editURL = editLink.getHref();
			editURLs.put(uid, new URL(editURL));

			// Bind local UID to remote UID
			List extensionList = oldEntry.getExtendedProperty();
			if (extensionList != null && !extensionList.isEmpty()) {
				Iterator extensions = extensionList.iterator();
				ExtendedProperty extension;
				while (extensions.hasNext()) {
					extension = (ExtendedProperty) extensions.next();
					String name = extension.getName();
					if (UID_EXTENSION_NAME.equals(name)) {
						String localUID = extension.getValue();
						if (!uid.equals(localUID)) {
							remoteUIDs.put(localUID, uid);
						}
						continue;
					}
					if (enableExtensions) {

						// Store extensions
						if (CATEGORIES_EXTENSION_NAME.equals(name)) {
							extensionMap.put(uid + "\tc", extension.getValue());
							continue;
						}
						if (PRIORITY_EXTENSION_NAME.equals(name)) {
							extensionMap.put(uid + "\tp", extension.getValue());
							continue;
						}
						if (URL_EXTENSION_NAME.equals(name)) {
							extensionMap.put(uid + "\tu", extension.getValue());
							continue;
						}
					}
				}
			}
		}

		// Return extensions registry (or null)
		return extensionMap;
	}

	private static final CalendarEventEntry findEntry(List entries,
			VEvent event, HashMap dateCache) throws Exception {

		// Get UID and RID
		String uid = ICalUtilities.getUid(event);

		// Get created
		long created = 0;
		Created createdDate = event.getCreated();
		if (createdDate != null) {
			created = createdDate.getDate().getTime();
		}

		// Get start date
		String startDate = null;
		DtStart dtStart = event.getStartDate();
		if (dtStart != null) {
			DateTime start = toDateTime(dtStart.getDate());
			if (start != null) {
				startDate = start.toUiString();
			}
		}

		// Get end date
		String endDate = null;
		DtEnd dtEnd = event.getEndDate();
		if (dtEnd != null) {
			DateTime end = toDateTime(dtEnd.getDate());
			if (end != null) {
				endDate = end.toUiString();
			}
		}

		// Get title
		String title = null;
		Summary summary = event.getSummary();
		if (summary != null) {
			title = ICalUtilities.normalizeLineBreaks(summary.getValue());
		}

		// Get content
		String content = null;
		Description description = event.getDescription();
		if (description != null) {
			content = ICalUtilities.normalizeLineBreaks(description.getValue());
		}

		// Loop on Google Calendar
		CalendarEventEntry bestEntry = null;
		CalendarEventEntry entry;
		int matchCounter, bestMatch = 0;
		Iterator entryIterator = entries.iterator();
		while (entryIterator.hasNext()) {
			entry = (CalendarEventEntry) entryIterator.next();
			matchCounter = 0;

			// Compare extended UID
			List extensionList = entry.getExtendedProperty();
			if (uid != null && extensionList != null
					&& !extensionList.isEmpty()) {
				Iterator extensions = extensionList.iterator();
				while (extensions.hasNext()) {
					ExtendedProperty extension = (ExtendedProperty) extensions
							.next();
					if (UID_EXTENSION_NAME.equals(extension.getName())
							&& uid.equals(extension.getValue())) {

						// UID found -> 100% match -> stop finding
						if (log.isDebugEnabled()) {
							log.debug("Found event ("
									+ ICalUtilities.getEventTitle(event)
									+ ") in Google Calendar by unique ID.");
						}
						entryIterator.remove();
						return entry;
					}
				}
			}

			// Compare created
			DateTime published = entry.getPublished();
			if (created != 0 && published != null) {
				long remoteCreated = published.getValue();
				if (created == remoteCreated) {
					matchCounter++;
				} else {
					if (remoteCreated != 0 && created > remoteCreated) {
						continue;
					}
				}
			}

			// Compare title
			TextConstruct titleConstruct = entry.getTitle();
			if (titleConstruct != null && title != null) {
				String titleText = titleConstruct.getPlainText();
				if (titleText != null) {
					titleText = ICalUtilities.normalizeLineBreaks(titleText);
					if (titleText.equals(title)) {
						matchCounter++;
					}
				}
			}

			// Compare content
			Content contentConstruct = entry.getContent();
			if (content != null && contentConstruct instanceof TextContent) {
				TextContent textContent = (TextContent) contentConstruct;
				String contentText = textContent.getContent().getPlainText();
				if (contentText != null) {
					contentText = ICalUtilities
							.normalizeLineBreaks(contentText);
					if (content.length() != 0 && contentText.length() != 0
							&& contentText.equals(content)) {
						matchCounter++;
					}
				}
			}

			// Compare dates and times
			String id = entry.getId();
			String entryStart = null;
			String entryEnd = null;
			String startKey = "s\t" + id;
			String endKey = "e\t" + id;
			entryStart = (String) dateCache.get(startKey);
			if (startDate != null && entryStart != null) {
				if (startDate.equals(entryStart)) {
					matchCounter++;
				}
			}
			entryEnd = (String) dateCache.get(endKey);
			if (endDate != null && entryEnd != null) {
				if (endDate.equals(entryEnd)) {
					matchCounter++;
				}
			}
			if (entryStart == null || entryEnd == null) {
				List whenList = entry.getTimes();
				if (whenList.isEmpty()) {
					Recurrence recurrence = entry.getRecurrence();
					if (recurrence != null) {
						VEvent holder = parseRecurrence(recurrence);
						if (holder != null) {
							dtStart = holder.getStartDate();
							if (dtStart != null) {
								DateTime start = toDateTime(dtStart.getDate());
								if (start != null && startDate != null) {
									boolean entryStartNull = (entryStart == null);
									entryStart = start.toUiString();
									dateCache.put(startKey, entryStart);
									if (entryStart.equals(startDate)
											&& entryStartNull) {
										matchCounter++;
									}
								}
							}
							dtEnd = holder.getEndDate();
							if (dtEnd != null) {
								DateTime end = toDateTime(dtEnd.getDate());
								if (end != null && endDate != null) {
									boolean entryEndNull = (entryEnd == null);
									entryEnd = end.toUiString();
									dateCache.put(endKey, entryEnd);
									if (entryEnd.equals(endDate)
											&& entryEndNull) {
										matchCounter++;
									}
								}
							}
						}
					}
				} else {
					When when = (When) whenList.get(0);
					DateTime start = when.getStartTime();
					if (start != null && startDate != null) {
						start.setTzShift(new Integer(0));
						boolean entryStartNull = (entryStart == null);
						entryStart = start.toUiString();
						dateCache.put(startKey, entryStart);
						if (entryStart.equals(startDate) && entryStartNull) {
							matchCounter++;
						}
					}

					DateTime end = when.getEndTime();
					if (end != null && endDate != null) {
						end.setTzShift(new Integer(0));
						boolean entryEndNull = (entryEnd == null);
						entryEnd = end.toUiString();
						dateCache.put(endKey, entryEnd);
						if (entryEnd.equals(endDate) && entryEndNull) {
							matchCounter++;
						}
					}
				}
			}

			if (matchCounter > bestMatch) {
				bestMatch = matchCounter;
				bestEntry = entry;
			}
		}
		if (bestMatch < 2) {
			if (log.isDebugEnabled()) {
				log.debug("Event (" + ICalUtilities.getEventTitle(event)
						+ ") not found in Google Calendar.");
			}
			return null;
		}
		if (log.isDebugEnabled()) {
			log.debug("Found event (" + ICalUtilities.getEventTitle(event)
					+ ") in Google Calendar by " + bestMatch
					+ " concordant property.");
		}
		entries.remove(bestEntry);
		return bestEntry;
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

	private static final synchronized CalendarService getService(Request request)
			throws Exception {
		long now = System.currentTimeMillis();
		PooledGoogleService service;
		service = (PooledGoogleService) servicePool.get(request.url);
		if (service != null) {
			if (now - service.lastUsed > GOOGLE_CONNECTION_TIMEOUT) {

				// Connection timeouted
				servicePool.remove(request.url);
				service = null;
			}
		}
		if (service == null) {

			// Create a new connection
			log.debug("Connecting to Google...");
			service = new PooledGoogleService();
			service.service = new CalendarService(Configurator.VERSION.replace(
					' ', '-'));
			String key = request.url + '\t' + request.username + '\t'
					+ request.password;
			for (int tries = 0;; tries++) {
				try {
					service.service.setUserCredentials(
							normalizeUsername(request.username),
							request.password);
					invalidCredentials.remove(key);
					break;
				} catch (InvalidCredentialsException wrongPassword) {
					log.fatal("Invalid Gmail username or password!");
					invalidCredentials.add(key);
					throw wrongPassword;
				} catch (Exception ioException) {
					if (tries == 5) {
						log.fatal("Connection refused!", ioException);
						invalidCredentials.add(key);
						throw ioException;
					}
					log.debug("Connection refused, reconnecting...");
					Thread.sleep(GOOGLE_RETRY_MILLIS);
				}
			}
			if (servicePool.size() > MAX_POOLED_CONNECTIONS) {
				servicePool.clear();
			}
			servicePool.put(request.url, service);
		}
		service.lastUsed = now;
		return service.service;
	}

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

		// Get service from pool
		if (request.url == null) {
			request.url = request.username;
		}
		CalendarService service = getService(request);

		// Create metafeed URL
		URL feedUrl = new URL(METAFEED_URL);

		// Send the request and receive the response
		CalendarFeed resultFeed;
		for (int tries = 0;; tries++) {
			try {
				resultFeed = (CalendarFeed) service.getFeed(feedUrl,
						CalendarFeed.class);
				break;
			} catch (Exception loadError) {
				if (tries == 3) {
					throw loadError;
				}
				log.debug("Connection refused, reconnecting...");

				// Rebuild connection
				Thread.sleep(GOOGLE_RETRY_MILLIS);
				servicePool.clear();
				service = getService(request);
			}
		}

		// Convert to array
		List entries = resultFeed.getEntries();
		if (entries == null || entries.isEmpty()) {
			return new String[0];
		}
		LinkedList urls = new LinkedList();
		Iterator entryIterator = entries.iterator();
		TextConstruct title;
		CalendarEntry entry;
		String url, text;
		Link link;
		int i;
		while (entryIterator.hasNext()) {
			entry = (CalendarEntry) entryIterator.next();
			link = entry.getSelfLink();
			if (link != null) {
				url = link.getHref();
				if (url != null) {
					i = url.indexOf(FEEDS_DEFAULT_PART);
					if (i != -1) {
						url = CALENDAR_ICAL_PART
								+ url
										.substring(i
												+ FEEDS_DEFAULT_PART.length())
								+ PRIVATE_BASIC_PART;
						urls.addLast(url);
						title = entry.getTitle();
						if (title != null) {
							text = title.getPlainText();
							if (text != null) {
								text = text.trim();
								if (text.length() != 0) {
									calendarNames.put(url, text);
								}
							}
						}
					}
				}
			}
		}
		saveCalendarNamesToCache(workDir);
		String[] array = new String[urls.size()];
		urls.toArray(array);
		return array;
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
