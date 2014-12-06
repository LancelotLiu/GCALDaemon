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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.Observance;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Created;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Priority;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.TzId;
import net.fortuna.ical4j.model.property.TzOffsetTo;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;

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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Event.ExtendedProperties;
import com.google.api.services.calendar.model.Event.Reminders;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;

/**
 * Google Calendar utilities.
 * 
 * <li>loadCalendar <li>updateEvents <li>removeEvents
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class GCalUtilitiesV3 {

	// --- CONSTANTS ---

	public static final String ERROR_MARKER = "gcaldaemon-error";

	private static final long GOOGLE_CONNECTION_TIMEOUT = 1000L * 60 * 5;
	private static final long GOOGLE_RETRY_MILLIS = 1000L;
	private static final int HTTP_CONNECTION_TIMEOUT = 10000;
	private static final int HTTP_WAIT_TIMEOUT = 60000;

	private static final int MAX_POOLED_CONNECTIONS = 100;

	private static final String GOOGLE_HTTPS_URL = "https://www.google.com";
	private static final String GOOGLE_HTTP_URL = "http://www.google.com";
	private static final String USER_AGENT = "Mozilla/5.0 (Windows; U;"
			+ " Windows NT 5.1; hu; rv:1.8.0.8) Gecko/20061025 Thunderbird/1.5.0.8";
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

	private static final Log log = LogFactory.getLog(GCalUtilitiesV3.class);

	// --- GLOBAL PROPERTIES ---

	private static boolean enableExtensions;
	private static boolean enableEmail;
	private static boolean enableSms;
	private static boolean enablePopup;

	// --- HTTP CONNECTION HANDLER ---

	private static final MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
	private static final HttpClient httpClient = new HttpClient(
			connectionManager);

	private static FileDataStoreFactory dataStoreFactory;
	private static HttpTransport httpTransport;
	private static final JsonFactory JSON_FACTORY = JacksonFactory
			.getDefaultInstance();
	private static final java.io.File DATA_STORE_DIR = new java.io.File("store");
	private static final String APPLICATION_NAME = "gcaldaemon_v3";

	static {
		try {
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();

			// initialize the data store factory
			dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	static final void globalInit() {
		try {

			// Set extended sync mode
			String value = System.getProperty("gcaldaemon.extended.sync",
					"false");
			enableExtensions = "true".equals(value);

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

	private GCalUtilitiesV3() {
	}

	// --- GOOGLE ICALENDAR LOADER ---

	private static Credential authorize() throws Exception {
		// load client secrets
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
				JSON_FACTORY,
				new InputStreamReader(ClassLoader.getSystemClassLoader()
						.getResourceAsStream("client_secrets.json")));
		if (clientSecrets.getDetails().getClientId().startsWith("Enter")
				|| clientSecrets.getDetails().getClientSecret()
						.startsWith("Enter ")) {
			System.out
					.println("Enter Client ID and Secret from https://code.google.com/apis/console/?api=calendar "
							+ "into calendar-cmdline-sample/src/main/resources/client_secrets.json");
			System.exit(1);
		}
		// set up authorization code flow
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				httpTransport, JSON_FACTORY, clientSecrets,
				Collections.singleton(CalendarScopes.CALENDAR))
				.setDataStoreFactory(dataStoreFactory).build();
		// authorize
		return new AuthorizationCodeInstalledApp(flow,
				new LocalServerReceiver()).authorize("user");
	}

	/**
	 * Download iCalendar(.ics) file.
	 * 
	 * @param request
	 * @return
	 * @throws Exception
	 */
	static final byte[] loadCalendar(Request request) throws Exception {
		GetMethod get = null;
		String icalURL;

		// Get auth token
		String token = null;
		if (request.url.indexOf("/private-") == -1) {

			// authorization
			Credential credential = authorize();

			// set up global Calendar instance
			new com.google.api.services.calendar.Calendar.Builder(
					httpTransport, JSON_FACTORY, credential)
					.setApplicationName(APPLICATION_NAME).build();
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
		return GCalUtilities.exceptionToCalendar(loadError);
	}

	// --- ICAL CONVERTER ---

	private static final byte[] insertExtensions(Request request,
			String content, byte[] bytes) {
		QuickWriter writer = null;
		try {

			// Get service from pool
			com.google.api.services.calendar.Calendar service = getService(request);

			// Build edit map
			CachedCalendar calendar = new CachedCalendar();
			calendar.url = request.url;
			calendar.username = request.username;
			calendar.password = request.password;
			calendar.previousBody = bytes;
			Map<String, Object> extensions = createEditURLMap(service, calendar);
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
			writer = new QuickWriter(bytes.length * 2);
			String extension, line, id = null;
			int days, hours, mins, i;
			EventReminder reminder;
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
							RecurrenceId recurrenceId = new RecurrenceId(
									line.substring(i + 1));
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
					reminder = (EventReminder) extensions.get(id + "\ta");
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
		} finally {
			if (writer != null) {
				writer.close();
			}
		}
		return bytes;
	}

	// --- AUTOMATIC TIME ZONE MANAGEMENT ---

	private static final Set<String> registeredTimeZones = new HashSet<String>();

	private static final void registerTimeZones(String content, byte[] bytes) {
		try {
			StringTokenizer st = new StringTokenizer(content, "\r\n");
			Set<String> timeZones = new HashSet<String>();
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
			net.fortuna.ical4j.model.Calendar calendar = ICalUtilities
					.parseCalendar(bytes);
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

		// Get service from pool
		com.google.api.services.calendar.Calendar service = getService(calendar);

		// Find remote event
		try {
			return getGoogleEntry(service, calendar, event);
		} catch (Exception invalidEntry) {

			// Remap events
			uidMaps.remove(calendar.url);
			return getGoogleEntry(service, calendar, event);
		}
	}

	// --- EVENT CREATOR ---

	static final void insertEvents(CachedCalendar calendar,
			VTimeZone[] timeZones, VEvent[] events) throws Exception {

		// Get service from pool
		com.google.api.services.calendar.Calendar service = getService(calendar);

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
			insertEvent(calendar, timeZones, events[n], foundRRule, service);
		}

		// Clear cache
		uidMaps.remove(calendar.url);
	}

	private static String getCalendarIdFromURL(String url) {
		String id = "";
		id = url.replace("/calendar/ical/", "");
		id = id.substring(0, id.indexOf("/"));
		id = id.replace("%40", "@");
		return id;
	}

	private static final void insertEvent(CachedCalendar calendar,
			VTimeZone[] timeZones, VEvent event, boolean foundRRule,
			com.google.api.services.calendar.Calendar service) throws Exception {

		// Clear cache
		if (foundRRule && event.getRecurrenceId() != null) {
			foundRRule = false;
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
		Event newEntry = convertVEvent(calendar, timeZones, event);

		// Absolute time = clear reminders mark
		Reminders reminders = newEntry.getReminders();
		if (reminders != null && !reminders.isEmpty()) {
			reminders.clear();
		}

		// Insert new event
		if (log.isDebugEnabled()) {
			log.debug("Inserting event (" + ICalUtilities.getEventTitle(event)
					+ ") into Google Calendar...");
		}
		try {
			service.events()
					.insert(getCalendarIdFromURL(calendar.url), newEntry)
					.execute();
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
				Reminders reminder = newEntry.getReminders();
				log.warn("Too many reminders!");
				if (reminder != null) {
					reminder.clear();
				}
			}

			// Resend request
			Thread.sleep(GOOGLE_RETRY_MILLIS);
			try {
				service.events()
						.insert(getCalendarIdFromURL(calendar.url), newEntry)
						.execute();
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
		if (body == null || body.length() == 0) {
			body = exception.toString();
		}
		return body;
	}

	// --- EVENT UPDATER ---

	static final void updateEvents(CachedCalendar calendar,
			VTimeZone[] timeZones, VEvent[] events) throws Exception {

		// Get service from pool
		com.google.api.services.calendar.Calendar service = getService(calendar);

		// Loop on events
		boolean searchRRule = true;
		boolean foundRRule = false;
		VEvent event;
		for (int n = 0; n < events.length; n++) {
			event = events[n];

			// Find original event
			Event oldEntry = getGoogleEntry(service, calendar, event);

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
				insertEvent(calendar, timeZones, event, foundRRule, service);

				// Clear UID cache
				uidMaps.remove(calendar.url);
			} else {

				// Event found in Google Calendar
				// Link editLink = oldEntry.getEditLink();
				// if (editLink == null) {
				// log.warn("Unable to update read-only event ("
				// + ICalUtilities.getEventTitle(event) + ")!");
				// continue;
				// }
				// String editURL = editLink.getHref();

				// Convert event to Google entry
				Event newEntry = convertVEvent(calendar, timeZones, event);
				String iCalUID = event.getUid().getValue();
				newEntry.setId(iCalUID.substring(0, iCalUID.indexOf("@")));

				// Check recurrence
				boolean recurrenceChanged;
				List<String> recurrence = null;
				if (oldEntry.getRecurrence() == null) {
					recurrence = newEntry.getRecurrence();
					recurrenceChanged = recurrence != null;
				} else {
					recurrence = newEntry.getRecurrence();
					recurrenceChanged = recurrence == null;
				}

				// Copy reminders
				Reminders reminders = newEntry.getReminders();
				if (reminders == null || reminders.isEmpty()) {
					if (!enableExtensions) {
						Reminders oldReminders = oldEntry.getReminders();
						if (oldReminders != null && !oldReminders.isEmpty()) {
							reminders = new Reminders();
							reminders.putAll(oldReminders);
							newEntry.setReminders(reminders);
						}
					}
				} else {
					reminders.clear();
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
						uidMaps.remove(calendar.url);
						service.events()
								.delete(getCalendarIdFromURL(calendar.url),
										newEntry.getId()).execute();
						deleted = true;
						service.events()
								.insert(getCalendarIdFromURL(calendar.url),
										newEntry).execute();
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
							Reminders reminder = newEntry.getReminders();
							log.warn("Too many reminders!");
							if (reminder != null) {
								reminder.clear();
							}
						}

						// Resend request
						Thread.sleep(GOOGLE_RETRY_MILLIS);
						try {
							if (!deleted) {
								service.events()
										.delete(getCalendarIdFromURL(calendar.url),
												newEntry.getId()).execute();
							}
							service.events()
									.insert(getCalendarIdFromURL(calendar.url),
											newEntry).execute();
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
						service.events()
								.update(getCalendarIdFromURL(calendar.url),
										newEntry.getId(), newEntry).execute();
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
										calendar.url);
							} catch (Exception ignored) {
								log.debug("Unable to delete faulty event ("
										+ ICalUtilities.getEventTitle(event)
										+ ")!", ignored);
							}
							continue;
						}

						// Remove reminders
						if (msg.indexOf("many reminder") != -1) {
							Reminders reminder = newEntry.getReminders();
							log.warn("Too many reminders!");
							if (reminder != null) {
								reminder.clear();
							}
						}

						// Resend request
						Thread.sleep(GOOGLE_RETRY_MILLIS);
						try {
							service.events()
									.update(getCalendarIdFromURL(calendar.url),
											newEntry.getId(), newEntry)
									.execute();
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
			com.google.api.services.calendar.Calendar service, VEvent parent,
			String editURL) throws Exception {
		uidMaps.remove(calendar.url);
		VEvent[] events = ICalUtilities.getEvents(ICalUtilities
				.parseCalendar(calendar.previousBody));
		String uid = ICalUtilities.getUid(parent);
		if (uid == null) {
			return;
		}
		Event oldEntry;
		VEvent child;
		String id;
		for (int c = 0; c < events.length; c++) {
			child = events[c];
			id = ICalUtilities.getUid(child);
			if (id == null) {
				continue;
			}
			if (id.startsWith(uid) && !id.equals(uid)) {
				oldEntry = getGoogleEntry(service, calendar, child);
				if (oldEntry != null) {
					Thread.sleep(GOOGLE_RETRY_MILLIS);
					service.events()
							.delete(getCalendarIdFromURL(calendar.url),
									oldEntry.getId()).execute();
				}
			}
		}
		Thread.sleep(GOOGLE_RETRY_MILLIS);
		service.calendars().delete(getCalendarIdFromURL(calendar.url));
		uidMaps.remove(calendar.url);
	}

	// --- EVENT REMOVER ---

	static final void removeEvents(CachedCalendar calendar, VEvent[] events)
			throws Exception {

		// Get service from pool
		com.google.api.services.calendar.Calendar service = getService(calendar);

		// Loop on events
		VEvent event;
		for (int n = 0; n < events.length; n++) {
			event = events[n];

			Event oldEntry = getGoogleEntry(service, calendar, event);

			// Remove event
			if (oldEntry != null) {
				if (log.isDebugEnabled()) {
					log.debug("Removing event ("
							+ ICalUtilities.getEventTitle(event)
							+ ") from Google Calendar...");
				}
				try {
					service.events()
							.delete(getCalendarIdFromURL(calendar.url),
									oldEntry.getId()).execute();
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
						service.calendars().delete(
								getCalendarIdFromURL(calendar.url));
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

	private static final Event convertVEvent(CachedCalendar calendar,
			VTimeZone[] timeZones, VEvent event) throws Exception {
		Event entry = new Event();

		// entry.setCanEdit(true);
		// entry.setDraft(new Boolean(false));
		// entry.setQuickAdd(false);
		entry.setUpdated(new DateTime(System.currentTimeMillis()));
		// entry.setSendEventNotifications(sendInvitations);
		String text;

		// Convert event UID to extended property
		String uid = ICalUtilities.getUid(event);
		if (uid != null) {
			ExtendedProperties extension = new ExtendedProperties();
			extension.put(UID_EXTENSION_NAME, uid);
			entry.setExtendedProperties(extension);
		}

		// Convert priority to extended property
		Priority priority = event.getPriority();
		if (priority != null) {
			text = priority.getValue();
			if (text != null && text.length() != 0) {
				ExtendedProperties extension = new ExtendedProperties();
				extension.put(PRIORITY_EXTENSION_NAME, text);
				entry.setExtendedProperties(extension);
			}
		}

		// Convert URL to extended property
		Url url = event.getUrl();
		if (url != null) {
			text = url.getValue();
			if (text != null && text.length() != 0) {
				ExtendedProperties extension = new ExtendedProperties();
				extension.put(URL_EXTENSION_NAME, text);
				entry.setExtendedProperties(extension);
			}
		}

		// Convert URL to extended property
		Property categories = event.getProperty(Property.CATEGORIES);
		if (categories != null) {
			text = categories.getValue();
			if (text != null && text.length() != 0 && !text.startsWith("http")) {
				ExtendedProperties extension = new ExtendedProperties();
				extension.put(CATEGORIES_EXTENSION_NAME, text);
				entry.setExtendedProperties(extension);
			}
		}

		// Convert created to published
		Created created = event.getCreated();
		if (created != null) {
			DateTime published = toDateTime(created.getDate());
			entry.setCreated(published);
		}

		// Convert summary to title
		Summary summary = event.getSummary();
		if (summary != null) {
			text = summary.getValue();
			if (text != null && text.length() != 0) {
				entry.setSummary(text);
			}
		}

		// Convert description to content
		Description desc = event.getDescription();
		if (desc != null) {
			text = desc.getValue();
			if (text != null && text.length() != 0) {
				entry.setDescription(text);
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
		boolean isAllDay = startDate.toString().indexOf('T') == -1;
		if (isAllDay) {
			entry.setStart(new EventDateTime().setDate(toDateTime(startDate)));
			entry.setEnd(new EventDateTime().setDate(toDateTime(endDate)));
		} else {
			entry.setStart(new EventDateTime()
					.setDateTime(toDateTime(startDate)));
			entry.setEnd(new EventDateTime().setDateTime(toDateTime(endDate)));
		}

		// Convert location to where
		Location location = event.getLocation();
		if (location != null) {
			text = location.getValue();
			if (text != null) {
				entry.setLocation(text);
			}
		}

		// Convert status (tentative, confirmed, canceled)
		Status status = event.getStatus();
		if (status != null) {
			text = status.getValue();
			entry.setStatus(text.toLowerCase());
		}

		// Convert classification to visibility (public / private)
		Clazz clazz = event.getClassification();
		if (clazz != null) {
			text = clazz.getValue();
			entry.setVisibility(text.toLowerCase());
		} else {
			entry.setVisibility("default");
		}

		// Convert transparency (transparent / opaque = free / busy)
		Transp transp = event.getTransparency();
		if (transp == null) {

			// Default is 'Available' (=free or transparent)
			entry.setTransparency("transparent");
		} else {
			if (Transp.TRANSPARENT.getValue().equals(transp.getValue())) {
				entry.setTransparency("transparent");
			} else {
				entry.setTransparency("opaque");
			}
		}

		// Convert attendees
		String[] emails = ICalUtilities.getAttendees(event);
		if (emails != null) {
			List<EventAttendee> attendees = new ArrayList<EventAttendee>();
			for (int i = 0; i < emails.length; i++) {
				EventAttendee ea = new EventAttendee();
				ea.setEmail(emails[i]);
				attendees.add(ea);
			}
			entry.setAttendees(attendees);
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
				List<String> recurrence = new ArrayList<String>();
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
				recurrence.add(writer.toString());
				entry.setRecurrence(recurrence);
				writer.close();
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
					com.google.api.services.calendar.Calendar service = getService(calendar);

					// Get original event
					Event parent = getGoogleEntryByUID(service, calendar, id);
					if (parent != null) {
						entry.setOriginalStartTime(new EventDateTime()
								.setDateTime(toDateTime(rid.getDate())));
					}
				}
			}
		}

		// Convert reminder
		int mins = ICalUtilities.getAlarmMinutes(event);
		if (mins != -1) {
			EventReminder reminder1 = new EventReminder();
			EventReminder reminder2 = new EventReminder();
			EventReminder reminder3 = new EventReminder();
			reminder1.setMethod("popup");
			reminder2.setMethod("email");
			reminder3.setMethod("sms");
			Integer holder;
			if (mins == 0) {
				reminder1.setMinutes(null);
				reminder2.setMinutes(null);
				reminder3.setMinutes(null);
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
						reminder1.setMinutes(holder * 60);
						reminder2.setMinutes(holder * 60);
						reminder3.setMinutes(holder * 60);
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
						reminder1.setMinutes(holder * 24 * 60);
						reminder2.setMinutes(holder * 24 * 60);
						reminder3.setMinutes(holder * 24 * 60);
					}
				}
			}
			// Set "Alert" alarm
			List<EventReminder> reminders = new ArrayList<EventReminder>();
			if (enablePopup) {
				reminders.add(reminder1);
			}

			// Set "E-mail" alarm
			if (enableEmail) {
				reminders.add(reminder2);
			}

			// Set "SMS" alarm
			if (enableSms) {
				reminders.add(reminder3);
			}
			if (!reminders.isEmpty()) {
				Reminders r = new Reminders();
				r.setOverrides(reminders);
				entry.setReminders(r);
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
			dateTime = new DateTime(isAllDay, date.getTime(), 0);
		}
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
		DateTime dateTime = new DateTime(true, calendar.getTime().getTime(), 0);
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

	private static final List<Event> getGoogleEntries(
			com.google.api.services.calendar.Calendar service,
			CachedCalendar calendar) throws Exception {

		// Request feed
		List<Event> feed;
		for (int tries = 0;; tries++) {
			try {
				feed = service.events()
						.list(getCalendarIdFromURL(calendar.url)).execute()
						.getItems();
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
		return feed;
	}

	// --- EVENT FINDER ---

	private static final Map<String, Object> uidMaps = new HashMap<String, Object>();

	private static final Event getGoogleEntry(
			com.google.api.services.calendar.Calendar service,
			CachedCalendar calendar, VEvent event) throws Exception {

		// Get local UID
		String uid = ICalUtilities.getUid(event);
		if (uid == null) {
			return null;
		}

		// Request entry from Google
		return getGoogleEntryByUID(service, calendar, uid);
	}

	/**
	 * 
	 * @param service
	 * @param calendar
	 * @param uid
	 *            It's iCalUID
	 * @return
	 * @throws Exception
	 */
	private final static Event getGoogleEntryByUID(
			com.google.api.services.calendar.Calendar service,
			CachedCalendar calendar, String uid) throws Exception {

		// get event UID from iCalUID
		int at = uid.indexOf("@");
		if (at != -1) {
			uid = uid.substring(0, at);
		}
		// Load event
		for (int tries = 0;; tries++) {
			try {
				return (Event) service.events()
						.get(getCalendarIdFromURL(calendar.url), uid).execute();
			} catch (Exception loadError) {
				if (tries == 5) {
					log.debug("Unable to load event (" + uid + ")!", loadError);
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

	@SuppressWarnings("unchecked")
	static final String getRemoteUID(CachedCalendar calendar, String id) {
		Map<String, String> mappedUIDs = (Map<String, String>) uidMaps
				.get(calendar.url);
		if (mappedUIDs == null) {
			return null;
		}
		return mappedUIDs.get(id);
	}

	private static final Map<String, Object> createEditURLMap(
			com.google.api.services.calendar.Calendar service,
			CachedCalendar calendar) throws Exception {

		// Create alarm registry
		Map<String, Object> extensionMap;
		if (enableExtensions) {
			extensionMap = new HashMap<String, Object>();
		} else {
			extensionMap = null;
		}

		// Create edit URL map
		List<Event> entries = getGoogleEntries(service, calendar);
		Map<String, Object> remoteUIDs = new HashMap<String, Object>();
		uidMaps.put(calendar.url, remoteUIDs);
		net.fortuna.ical4j.model.Calendar oldCalendar = ICalUtilities
				.parseCalendar(calendar.previousBody);
		VEvent[] events = ICalUtilities.getEvents(oldCalendar);

		// Loop on events
		VEvent event;
		Map<String, String> dateCache = new HashMap<String, String>();
		for (int n = 0; n < events.length; n++) {
			event = events[n];

			// Get local UID and RID
			String uid = ICalUtilities.getUid(event);
			if (uid == null) {
				continue;
			}

			// Find original event
			Event oldEntry = findEntry(entries, event, dateCache);
			if (oldEntry == null) {
				continue;
			}

			// Get alarm
			if (enableExtensions) {
				Reminders reminders = oldEntry.getReminders();
				if (reminders != null && !reminders.isEmpty()) {
					// FIXME
					extensionMap.put(uid + "\ta",
							reminders.getOverrides().get(0));
				}
			}

			// Bind local UID to remote UID
			ExtendedProperties p = oldEntry.getExtendedProperties();
			if (p != null) {
				Map<String, String> extensionList = oldEntry
						.getExtendedProperties().getShared();
				for (Map.Entry<String, String> extension : extensionList
						.entrySet()) {
					String name = extension.getKey();
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

	private static final Event findEntry(List<Event> entries, VEvent event,
			Map<String, String> dateCache) throws Exception {

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
				startDate = toUiString(start.getValue());
			}
		}

		// Get end date
		String endDate = null;
		DtEnd dtEnd = event.getEndDate();
		if (dtEnd != null) {
			DateTime end = toDateTime(dtEnd.getDate());
			if (end != null) {
				endDate = toUiString(end.getValue());
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
		Event bestEntry = null;
		Event entry;
		int matchCounter, bestMatch = 0;
		Iterator<Event> entryIterator = entries.iterator();
		while (entryIterator.hasNext()) {
			entry = entryIterator.next();
			matchCounter = 0;

			// Compare extended UID
			ExtendedProperties p = entry.getExtendedProperties();
			if (uid != null && p != null) {
				Map<String, String> extensionList = entry
						.getExtendedProperties().getShared();
				for (Map.Entry<String, String> extension : extensionList
						.entrySet()) {
					if (UID_EXTENSION_NAME.equals(extension.getKey())
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
			DateTime published = entry.getCreated();
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
			String titleText = entry.getSummary();
			if (titleText != null) {
				titleText = ICalUtilities.normalizeLineBreaks(titleText);
				if (titleText.equals(title)) {
					matchCounter++;
				}
			}

			// Compare content
			String contentText = entry.getDescription();
			if (contentText != null) {
				contentText = ICalUtilities.normalizeLineBreaks(contentText);
				if (content != null && content.length() != 0
						&& contentText.length() != 0
						&& contentText.equals(content)) {
					matchCounter++;
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
				DateTime start = entry.getStart().getDateTime();
				if (start != null && startDate != null) {
					boolean entryStartNull = (entryStart == null);
					entryStart = toUiString(start.getValue());
					dateCache.put(startKey, entryStart);
					if (entryStart.equals(startDate) && entryStartNull) {
						matchCounter++;
					}
				}

				DateTime end = entry.getEnd().getDateTime();
				if (end != null && endDate != null) {
					boolean entryEndNull = (entryEnd == null);
					entryEnd = toUiString(end.getValue());
					dateCache.put(endKey, entryEnd);
					if (entryEnd.equals(endDate) && entryEndNull) {
						matchCounter++;
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

	// --- GOOGLE CONNECTION POOL ---

	private static final Map<String, PooledGoogleService> servicePool = new HashMap<String, PooledGoogleService>();

	private static final synchronized com.google.api.services.calendar.Calendar getService(
			Request request) throws Exception {
		long now = System.currentTimeMillis();
		PooledGoogleService service;
		service = servicePool.get(request.url);
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
			for (int tries = 0;; tries++) {
				try {
					Credential credential = authorize();
					service.service = new com.google.api.services.calendar.Calendar.Builder(
							httpTransport, JSON_FACTORY, credential)
							.setApplicationName(APPLICATION_NAME).build();
					break;
				} catch (Exception ioException) {
					if (tries == 5) {
						log.fatal("Connection refused!", ioException);
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

	// --- LIST CALENDARS ---

	private static final Properties calendarNames = new Properties();

	public static final String[] getCalendarURLs(Request request, File workDir)
			throws Exception {

		// Get service from pool
		if (request.url == null) {
			request.url = request.username;
		}
		com.google.api.services.calendar.Calendar service = getService(request);

		// Send the request and receive the response
		CalendarList resultFeed;
		for (int tries = 0;; tries++) {
			try {
				resultFeed = service.calendarList().list().execute();
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
		List<CalendarListEntry> entries = resultFeed.getItems();
		if (entries == null || entries.isEmpty()) {
			return new String[0];
		}
		List<String> urls = new LinkedList<String>();
		Iterator<CalendarListEntry> entryIterator = entries.iterator();
		CalendarListEntry entry;
		String url, text;
		while (entryIterator.hasNext()) {
			entry = entryIterator.next();
			// FIXME don't know how get private iCal URL, temporary return
			// calendar ids
			url = entry.getId();
			urls.add(url);
			text = entry.getSummary();
			if (text != null) {
				text = text.trim();
				if (text.length() != 0) {
					calendarNames.put(url, text);
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
			Iterator<Map.Entry<Object, Object>> names = calendarNames
					.entrySet().iterator();
			Map.Entry<Object, Object> entry;
			while (names.hasNext()) {
				entry = names.next();
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

	private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

	private static String toUiString(long localTime) {

		StringBuilder sb = new StringBuilder();

		java.util.Calendar dateTime = new GregorianCalendar(GMT);

		dateTime.setTimeInMillis(localTime);

		try {

			appendInt(sb, dateTime.get(java.util.Calendar.YEAR), 4);
			sb.append('-');
			appendInt(sb, dateTime.get(java.util.Calendar.MONTH) + 1, 2);
			sb.append('-');
			appendInt(sb, dateTime.get(java.util.Calendar.DAY_OF_MONTH), 2);

		} catch (ArrayIndexOutOfBoundsException e) {
			throw new RuntimeException(e);
		}

		return sb.toString();
	}

	private static void appendInt(StringBuilder sb, int num, int numDigits) {

		if (num < 0) {
			sb.append('-');
			num = -num;
		}

		char[] digits = new char[numDigits];
		for (int digit = numDigits - 1; digit >= 0; --digit) {
			digits[digit] = (char) ('0' + num % 10);
			num /= 10;
		}

		sb.append(digits);
	}
}
