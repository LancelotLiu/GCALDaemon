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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.ProdId;
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

import com.google.gdata.data.HtmlTextConstruct;
import com.sun.syndication.feed.WireFeed;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.WireFeedOutput;
import com.sun.syndication.io.XmlReader;

/**
 * RSS / ATOM feed utilities.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class FeedUtilities {

	// --- CONSTANTS ---

	private static final String USER_AGENT = "Mozilla/5.0 (Windows; U;"
			+ " Windows NT 5.1; hu; rv:1.8.0.8) Gecko/20061025 Thunderbird/1.5.0.8";

	private static final long FEED_RETRY_MILLIS = 1000L;
	private static final int HTTP_CONNECTION_TIMEOUT = 10000;
	private static final int HTTP_WAIT_TIMEOUT = 60000;

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(FeedUtilities.class);

	// --- HTTP CONNECTION HANDLER ---

	private static final MultiThreadedHttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager();
	private static final HttpClient httpClient = new HttpClient(
			connectionManager);

	static final void globalInit() {
		try {
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
			log.error("Unable to setup proxy!", setupError);
		}
	}

	// --- PRIVATE CONSTRUCTOR ---

	private FeedUtilities() {
	}

	// --- FEED LOADER AND CONVERTER ---

	public static final SyndEntry[] getFeedEntries(String feedURL,
			String username, String password) throws Exception {
		byte[] feedBytes = loadFeed(feedURL, username, password);
		SyndFeed feed = parseFeed(feedBytes);
		return getFeedEntries(feed);
	}

	public static final CachedCalendar getFeedAsCalendar(String feedURL,
			HashMap calendarCache, long eventLength, double duplicationRatio,
			String username, String password) throws Exception {

		// Load RSS/ATOM feed
		byte[] feedBytes = loadFeed(feedURL, username, password);
		SyndFeed feed = parseFeed(feedBytes);

		// Remove duplicated feed entries
		try {
			if (duplicationRatio < 1) {
				byte[] filteredFeed = removeDuplicatedEntries(feed,
						calendarCache, duplicationRatio);
				if (filteredFeed != null) {
					feedBytes = filteredFeed;
					feed = parseFeed(feedBytes);
				}
			}
		} catch (Exception formatError) {
			log.warn("Feed filtering failed!", formatError);
		}

		Calendar calendar = convertFeedToCalendar(feed, eventLength);
		normalizeDates(calendar, eventLength);

		// Create container
		CachedCalendar container = new CachedCalendar();

		// body = ics file
		container.body = getBytes(calendar);

		// previousBody = feed file
		container.previousBody = feedBytes;

		// Return feed and ics together
		return container;
	}

	private static final byte[] removeDuplicatedEntries(SyndFeed feed,
			HashMap feedCache, double duplicationRatio) throws Exception {

		// Remove duplicated feed entries
		if (feedCache.isEmpty()) {
			return null;
		}
		SyndEntry[] entries = getFeedEntries(feed);
		LinkedList duplicatedEntries = new LinkedList();
		HashMap syndEntryCache = new HashMap();
		SyndEntry entry;
		boolean found;
		for (int i = 0; i < entries.length; i++) {
			entry = entries[i];
			found = false;

			// Find entry
			Iterator urls = feedCache.keySet().iterator();
			while (urls.hasNext()) {
				String url = (String) urls.next();
				if (url.endsWith(".ics")) {
					continue;
				}
				SyndEntry[] otherEntries = (SyndEntry[]) syndEntryCache
						.get(url);
				if (otherEntries == null) {
					CachedCalendar container = (CachedCalendar) feedCache
							.get(url);
					SyndFeed otherFeed = parseFeed(container.previousBody);
					otherEntries = getFeedEntries(otherFeed);
					syndEntryCache.put(url, otherEntries);
				}
				found = foundDuplicatedEntry(entry, otherEntries,
						duplicationRatio);
				if (found) {
					break;
				}
			}

			// Store duplicated entry
			if (found) {
				duplicatedEntries.addLast(entry);
				log.debug("Duplicated feed entry: " + entry.getTitle().trim());
			}
		}

		// Convert to byte array
		if (!duplicatedEntries.isEmpty()) {
			List list = feed.getEntries();
			list.removeAll(duplicatedEntries);
			WireFeed wire = feed.createWireFeed();
			WireFeedOutput out = new WireFeedOutput();
			StringWriter writer = new StringWriter();
			out.output(wire, writer);
			byte[] bytes = StringUtils.encodeString(writer.toString(),
					StringUtils.UTF_8);
			return bytes;
		}
		return null;
	}

	private static final boolean foundDuplicatedEntry(SyndEntry search,
			SyndEntry[] entries, double duplicationRatio) throws Exception {

		// Compare titles
		String title = search.getTitle();
		if (title == null || title.length() == 0) {
			return false;
		}
		title = title.toLowerCase();
		SyndEntry entry;
		String test;
		for (int i = 0; i < entries.length; i++) {
			entry = entries[i];
			test = entry.getTitle();
			if (test == null || test.length() == 0) {
				continue;
			}
			test = test.toLowerCase();
			if (isSame(title, test, duplicationRatio)) {
				return true;
			}
		}
		return false;
	}

	private static final boolean isSame(String text1, String text2,
			double duplicationRatio) throws Exception {

		// Compare lengths [max 65%]
		int len1 = text1.length();
		int len2 = text2.length();
		int dif = Math.abs(len1 - len2);
		int average = (len1 + len2) / 2;
		if (dif >= average) {
			return false;
		}

		// Compute ratio
		double matches = 0;
		for (int n = 0; n < len1 - 2; n++) {
			if (text2.indexOf(text1.substring(n, n + 2)) != -1) {
				matches++;
			}
		}

		// Compare ratio [0 -> 1, max 30% dif]
		double currentRatio = matches / len1;
		return currentRatio >= duplicationRatio;
	}

	// --- RSS / ATOM FEED PARSERS ---

	private static final byte[] loadFeed(String feedURL, String username,
			String password) throws Exception {
		HtmlTextConstruct html = new HtmlTextConstruct(feedURL);
		feedURL = html.getPlainText();
		log.debug("Loading feed from " + feedURL + "...");

		// Load feed
		GetMethod get = new GetMethod(feedURL);
		get.addRequestHeader("User-Agent", USER_AGENT);
		get.setFollowRedirects(true);
		if (username != null && password != null) {

			// Set username/password
			byte[] auth = StringUtils.encodeString(username + ':' + password,
					StringUtils.UTF_8);
			get.addRequestHeader("Authorization", "Basic "
					+ StringUtils.encodeBASE64(auth));

		}
		for (int tries = 0;; tries++) {
			try {
				int status = httpClient.executeMethod(get);
				if (status == -1) {
					throw new Exception("Invalid HTTP response status (-1)!");
				}
				byte[] bytes = get.getResponseBody();
				if (log.isDebugEnabled()) {
					log.debug("Feed loaded successfully (" + bytes.length
							+ " bytes).");
				}
				return bytes;
			} catch (Exception loadError) {
				if (tries == 5) {
					throw loadError;
				}
				log.debug("Connection refused, reconnecting...");
				Thread.sleep(FEED_RETRY_MILLIS);
			} finally {
				get.releaseConnection();
			}
		}
	}

	private static final SyndFeed parseFeed(byte[] feedBytes) throws Exception {
		SyndFeedInput input = new SyndFeedInput();
		return input.build(new XmlReader(new ByteArrayInputStream(feedBytes)));
	}

	private static final SyndEntry[] getFeedEntries(SyndFeed feed)
			throws Exception {
		List list = feed.getEntries();
		SyndEntry[] entries = new SyndEntry[list.size()];
		list.toArray(entries);
		return entries;
	}

	// --- FEED TO ICAL CONVERTERS ---

	private static final Calendar convertFeedToCalendar(SyndFeed feed,
			long eventLength) throws Exception {

		// Create new calendar
		Calendar calendar = new Calendar();
		PropertyList props = calendar.getProperties();
		props.add(new ProdId(Configurator.VERSION));
		props.add(Version.VERSION_2_0);
		props.add(CalScale.GREGORIAN);

		// Convert events
		SyndEntry[] entries = getFeedEntries(feed);
		ComponentList events = calendar.getComponents();
		java.util.Date now = new java.util.Date();
		SyndEntry entry;
		for (int i = 0; i < entries.length; i++) {
			entry = entries[i];

			// Convert feed link to iCal URL
			String url = entry.getLink();
			if (url == null || url.length() == 0) {
				continue;
			}

			// Convert feed published date to iCal dates
			java.util.Date date = entry.getPublishedDate();
			if (date == null) {
				date = now;
			}
			DateTime startDate = new DateTime(date);

			// Calculate iCal end date
			DateTime endDate = new DateTime(date.getTime() + eventLength);

			// Convert feed title to iCal summary
			String title = entry.getTitle();
			if (title == null || title.length() == 0) {
				title = url;
			} else {
				title = title.trim();
			}
			VEvent event = new VEvent(startDate, endDate, title);

			// Set event URL
			PropertyList args = event.getProperties();
			URI uri = new URI(url);
			args.add(new Url(uri));

			// Generate location by URL
			Location location = new Location(uri.getHost());
			args.add(location);

			// Generate UID by URL
			Uid uid = new Uid(url);
			args.add(uid);

			// Convert feed description to iCal description
			SyndContent syndContent = entry.getDescription();
			String content = null;
			if (syndContent != null) {
				content = syndContent.getValue();
				if (content != null && content.length() != 0) {
					HtmlTextConstruct html = new HtmlTextConstruct(content);
					content = html.getPlainText();
					html = new HtmlTextConstruct(content);
					content = html.getPlainText();
					content = content.trim() + "\r\n\r\n" + url;
				}
			}
			if (content == null) {
				content = url;
			}
			Description desc = new Description(content);
			args.add(desc);

			// Add converted event
			events.add(event);
		}

		// Return calendar
		return calendar;
	}

	private static final byte[] getBytes(Calendar calendar) throws Exception {
		try {
			ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
			CalendarOutputter outputter = new CalendarOutputter();
			outputter.output(calendar, buffer);
			return buffer.toByteArray();
		} catch (Exception calendarException) {
			String empty = "BEGIN:VCALENDAR\r\n"
					+ "PRODID:"
					+ Configurator.VERSION
					+ "\r\nVERSION:2.0\r\nCALSCALE:GREGORIAN\r\nEND:VCALENDAR\r\n";
			return StringUtils.encodeString(empty, StringUtils.US_ASCII);
		}
	}

	// --- DATE / TIME NORMALIZER ---

	private static final void normalizeDates(Calendar calendar, long eventLength)
			throws Exception {
		VEvent[] events = ICalUtilities.getEvents(calendar);
		VEvent event1, event2;
		boolean foundIntersection;

		// Main shift loop
		for (int n = 0; n < 1000; n++) {
			foundIntersection = false;

			// Check event times
			for (int i = 0; i < events.length; i++) {
				event1 = events[i];

				// Compare with other events
				for (int j = i + 1; j < events.length; j++) {
					event2 = events[j];
					if (isIntersects(event1, event2, eventLength)) {
						foundIntersection = true;
						shiftEarlierEvent(event1, event2, eventLength);
					}
				}
			}

			// Intersection not found -> exit from main loop
			if (!foundIntersection) {
				break;
			}
		}
	}

	private static final boolean isIntersects(VEvent event1, VEvent event2,
			long eventLength) throws Exception {
		long time1 = event1.getStartDate().getDate().getTime();
		long time2 = event2.getStartDate().getDate().getTime();
		if (time1 == time2) {
			return true;
		}
		if (time1 > time2) {
			if (time2 + eventLength > time1) {
				return true;
			}
		} else {
			if (time1 + eventLength > time2) {
				return true;
			}
		}
		return false;
	}

	private static final void shiftEarlierEvent(VEvent event1, VEvent event2,
			long eventLength) throws Exception {
		long time1 = event1.getStartDate().getDate().getTime();
		long time2 = event2.getStartDate().getDate().getTime();
		VEvent earlierEvent, laterEvent;
		if (time1 < time2) {
			earlierEvent = event1;
			laterEvent = event2;
		} else {
			earlierEvent = event2;
			laterEvent = event1;
		}
		long endTime = laterEvent.getStartDate().getDate().getTime();
		long startTime = endTime - eventLength;
		earlierEvent.getStartDate().setDate(new DateTime(startTime));
		earlierEvent.getEndDate().setDate(new DateTime(endTime));
	}

}
