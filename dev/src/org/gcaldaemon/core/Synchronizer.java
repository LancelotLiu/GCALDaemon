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

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.logger.QuickWriter;

import com.google.gdata.data.calendar.CalendarEventEntry;

/**
 * Main synchronizer thread.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
final class Synchronizer extends Thread {

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(Synchronizer.class);

	// --- ON DEMAND UID REGISTRY ---

	private final HashMap eventRegistry = new HashMap();

	// --- CALENDAR MODIFICATION QUEUE ---

	private final LinkedList changedCalendars = new LinkedList();

	// --- CONSTRUCTOR ---

	private final ProgressMonitor monitor;
	private final File eventRegistryFile;
	private final boolean deleteEnabled;

	Synchronizer(ThreadGroup mainGroup, Configurator configurator)
			throws Exception {
		super(mainGroup, "Synchronizer");
		setDaemon(true);

		// Show animated progress bar while synching
		if (configurator
				.getConfigProperty(Configurator.PROGRESS_ENABLED, false)) {
			if (GraphicsEnvironment.isHeadless()) {
				monitor = null;
				log.warn("Unable to use progress monitor in headless mode!");
			} else {
				monitor = new ProgressMonitor();
				setPriority(NORM_PRIORITY - 1);
			}
		} else {
			monitor = null;
		}

		// Pointer to the offline history file
		eventRegistryFile = new File(configurator.getWorkDirectory(),
				"event-registry.txt");

		// Enable to remove remote events in gCal (hidden feature)
		deleteEnabled = configurator.getConfigProperty(
				Configurator.REMOTE_DELETE_ENABLED, true);
		if (!deleteEnabled) {
			log.info("Remote event removal disabled.");
		}

		// Init global static variables
		GCalUtilities.globalInit();
		ICalUtilities.globalInit();
		if (configurator.isFeedConverterEnabled()) {
			FeedUtilities.globalInit();
		}

		// Start synchronizer's thread
		start();
	}

	// --- CALENDAR CHANGE EVENT / ONLINE SYNCHRONIZER ---

	final synchronized void calendarChanged(CachedCalendar calendar) {
		changedCalendars.addLast(calendar);
		notifyAll();
	}

	// --- ONLINE SYNCHRONIZER LOOP ---

	public final void run() {
		for (;;) {
			try {

				// Wait for an enqued 'calendar chage event'
				CachedCalendar calendar;
				synchronized (this) {
					while (changedCalendars.isEmpty()) {
						wait();
					}
					calendar = (CachedCalendar) changedCalendars.removeFirst();
				}

				// Start synchronization
				log.debug("Starting Google Calendar synchronizer...");
				if (monitor != null) {
					monitor.setVisible(true);
				}

				// Find new or changed events
				Calendar newCalendar = ICalUtilities
						.parseCalendar(calendar.body);
				Calendar oldCalendar = ICalUtilities
						.parseCalendar(calendar.previousBody);
				VEvent[] changes = ICalUtilities.getNewEvents(oldCalendar,
						newCalendar, true, calendar.url);
				if (changes.length == 0) {

					// Find removed events (reverse method parameters)
					changes = ICalUtilities.getNewEvents(newCalendar,
							oldCalendar, false, null);
					if (changes.length != 0 && deleteEnabled) {

						// Remove deleted events from Google
						GCalUtilities.removeEvents(calendar, changes);
					}
				} else {

					// Get timezones
					VTimeZone[] timeZones = ICalUtilities
							.getTimeZones(newCalendar);

					// Update events in Google Calendar
					GCalUtilities.updateEvents(calendar, timeZones, changes);
				}

				// Synchronization done
				log.debug("Synchronization finished.", null);
			} catch (InterruptedException interrupt) {
				return;
			} catch (Exception syncError) {
				if (monitor != null) {
					try {
						Toolkit.getDefaultToolkit().beep();
					} catch (Throwable ignored) {
					}
				}
				log.error("Unable to synchronize with Google Calendar!",
						syncError);
			} finally {

				// Hide progress monitor
				if (monitor != null) {
					try {
						monitor.setVisible(false);
					} catch (Throwable ignored) {
					}
				}
			}
		}
	}

	// --- ON DEMAND / OFFLINE SYNCHRONIZER ---

	final byte[] syncronizeNow(CachedCalendar calendar) throws Exception {
		log.debug("Starting Google Calendar synchronizer...");

		// Create processing variables
		boolean remoteEventChanged;
		CalendarEventEntry entry;
		String uid, remoteUID;
		long remoteDate;
		Long storedDate;
		VEvent event;
		int i;

		// Load offline history
		loadEventRegistry();

		// Get historical parameters
		HashMap uids = (HashMap) eventRegistry.get(calendar.url);
		if (uids == null) {
			uids = new HashMap();
		}

		// Processed unique IDs
		HashSet processedUids = new HashSet();

		// Parse ics files
		Calendar localCalendar = ICalUtilities.parseCalendar(calendar.body);
		Calendar remoteCalendar = ICalUtilities
				.parseCalendar(calendar.previousBody);

		// Get local and remote changes
		VEvent[] localChanges = ICalUtilities.getNewEvents(remoteCalendar,
				localCalendar, true, calendar.url);
		VEvent[] remoteChanges = ICalUtilities.getNewEvents(localCalendar,
				remoteCalendar, false, null);

		// Updatable and removable events
		LinkedList insertableList = new LinkedList();
		LinkedList updatableList = new LinkedList();
		LinkedList removableList = new LinkedList();

		// Process local changes
		for (i = 0; i < localChanges.length; i++) {
			event = localChanges[i];
			uid = ICalUtilities.getUid(event);
			if (uid == null) {
				log.error("Invalid ical file (missing event ID)!");
				continue;
			}

			// Find remote pair
			entry = GCalUtilities.findEvent(calendar, event);
			if (entry == null) {
				if (uids.containsKey(uid)) {

					// Event removed at Google side -> download & remove
					if (log.isDebugEnabled()) {
						log.debug("Removed event ("
								+ ICalUtilities.getEventTitle(event)
								+ ") found in the Google Calendar.");
					}
				} else {

					// New local event -> insert
					if (log.isDebugEnabled()) {
						log.debug("New event ("
								+ ICalUtilities.getEventTitle(event)
								+ ") found in the local calendar.");
					}
					insertableList.addLast(event);
				}
			} else {

				// Add local and remote ID to processed UIDs
				processedUids.add(entry.getId());

				// Get remote event's modification date
				remoteDate = entry.getUpdated().getValue();
				storedDate = (Long) uids.get(uid);
				remoteEventChanged = true;
				if (storedDate == null) {
					remoteUID = GCalUtilities.getRemoteUID(calendar, uid);
					if (remoteUID != null) {
						storedDate = (Long) uids.get(remoteUID);
					}
				}
				if (storedDate != null) {

					// FIXME If a 'reminder' changes in GCal singly,
					// Google Calendar does NOT update the LAST_MODIFIED
					// timestamp. Otherwise this comparison works.
					remoteEventChanged = storedDate.longValue() != remoteDate;
				}
				if (remoteEventChanged) {

					// Event modified at Google side -> download & update
					if (log.isDebugEnabled()) {
						log.debug("Updated event ("
								+ ICalUtilities.getEventTitle(event)
								+ ") found in the Google Calendar.");
					}
				} else {

					// Local event modified -> update
					if (log.isDebugEnabled()) {
						log.debug("Updated event ("
								+ ICalUtilities.getEventTitle(event)
								+ ") found in the local calendar.");
					}
					updatableList.addLast(event);
				}
			}
		}

		// Process remote changes
		for (i = 0; i < remoteChanges.length; i++) {
			event = remoteChanges[i];

			// Verify remote ID
			entry = GCalUtilities.findEvent(calendar, event);
			if (entry == null || processedUids.contains(entry.getId())) {
				continue;
			}

			// Verify local ID
			uid = ICalUtilities.getUid(event);
			if (uid == null) {
				log.error("Invalid ical file (missing event ID)!");
				continue;
			}

			// Find ID in history
			if (uids.containsKey(uid)) {

				// Local event removed -> remove event
				if (log.isDebugEnabled()) {
					log.debug("Removed event ("
							+ ICalUtilities.getEventTitle(event)
							+ ") found in the local calendar.");
				}
				removableList.addLast(event);
			} else {

				// New remote event -> download & create
				if (log.isDebugEnabled()) {
					log.debug("New event ("
							+ ICalUtilities.getEventTitle(event)
							+ ") found in the Google Calendar.");
				}
			}
		}

		// Check changes
		if (localChanges.length == 0 && remoteChanges.length == 0) {

			// Save offline registry
			saveEventRegistry(calendar.url, calendar.previousBody);

			// Return previous body
			return calendar.previousBody;
		}

		// Show progress monitor
		if (monitor != null) {
			monitor.setVisible(true);
		}
		try {

			// Do modifications
			if (!removableList.isEmpty() && deleteEnabled) {

				// Remove Google entries
				VEvent[] events = new VEvent[removableList.size()];
				removableList.toArray(events);
				GCalUtilities.removeEvents(calendar, events);
			}
			VTimeZone[] timeZones;
			if (!updatableList.isEmpty() || !insertableList.isEmpty()) {

				// Get timezones
				timeZones = ICalUtilities.getTimeZones(localCalendar);
			} else {
				timeZones = new VTimeZone[0];
			}
			if (!updatableList.isEmpty()) {

				// Update Google entries
				VEvent[] events = new VEvent[updatableList.size()];
				updatableList.toArray(events);
				GCalUtilities.updateEvents(calendar, timeZones, events);
			}
			if (!insertableList.isEmpty()) {

				// Insert new Google entries
				VEvent[] events = new VEvent[insertableList.size()];
				insertableList.toArray(events);
				GCalUtilities.insertEvents(calendar, timeZones, events);
			}

			// Load new calendar from Google
			byte[] newBytes = GCalUtilities.loadCalendar(calendar);

			// Save offline registry
			saveEventRegistry(calendar.url, newBytes);

			// Return new ics file
			return newBytes;
		} finally {

			// Hide progress monitor
			if (monitor != null) {
				try {
					monitor.setVisible(false);
				} catch (Throwable ignored) {
				}
			}
		}
	}

	// --- TODO I/O HANDLERS OF ON-DEMAND SYNC ---

	private final void loadEventRegistry() {
		if (!eventRegistry.isEmpty()) {
			return;
		}
		RandomAccessFile file = null;
		try {
			if (eventRegistryFile.isFile()) {

				// Load history file
				file = new RandomAccessFile(eventRegistryFile, "r");
				int len = (int) eventRegistryFile.length();
				byte[] bytes = new byte[len];
				file.readFully(bytes);
				file.close();
				String content = StringUtils.decodeToString(bytes,
						StringUtils.US_ASCII);
				bytes = null;

				// Parse history file
				StringTokenizer st = new StringTokenizer(content, "\r\n");
				HashMap uids = new HashMap();
				String urlHash = new String();
				String line;
				int i;
				while (st.hasMoreTokens()) {
					line = st.nextToken().trim();
					if (line.startsWith("URL\t")) {
						if (!uids.isEmpty()) {
							eventRegistry.put(urlHash, uids);
						}
						i = line.indexOf('\t');
						if (i != -1) {
							urlHash = line.substring(i + 1);
							uids = new HashMap();
						}
						continue;
					}
					i = line.indexOf('\t');
					if (i != -1) {
						uids.put(line.substring(0, i), new Long(line
								.substring(i + 1)));
					}
				}
				if (!uids.isEmpty()) {
					eventRegistry.put(urlHash, uids);
				}
				if (log.isDebugEnabled()) {
					log.debug("Event registry loaded successfully (" + len
							+ " bytes).");
				}
				return;
			}
		} catch (Exception ioError) {
			if (file != null) {
				try {
					file.close();
					eventRegistryFile.delete();
				} catch (Exception ignored) {
				}
			}
			log.warn("Unable to load event registry!", ioError);
		}
	}

	private final void saveEventRegistry(String calendarURL, byte[] newBytes)
			throws Exception {

		// Verify ics file
		char[] chars = new char[Math.min(newBytes.length, 100)];
		int i;
		for (i = 0; i < chars.length; i++) {
			chars[i] = (char) newBytes[i];
		}
		if ((new String(chars)).indexOf(GCalUtilities.ERROR_MARKER) != -1) {
			return;
		}

		// Parse new ics file
		Calendar newCalendar = ICalUtilities.parseCalendar(newBytes);
		VEvent[] newEvents = ICalUtilities.getEvents(newCalendar);
		HashMap uids = new HashMap();
		VEvent event;
		String uid;
		for (i = 0; i < newEvents.length; i++) {
			event = newEvents[i];
			uid = ICalUtilities.getUid(event);
			if (uid != null) {
				uids.put(uid, new Long(event.getLastModified().getDateTime()
						.getTime()));
			}
		}

		// Set historical parameters
		eventRegistry.put(calendarURL, uids);

		// Save file
		FileOutputStream out = null;
		try {

			// Create history
			QuickWriter writer = new QuickWriter();
			writer.write("#GCALDAEMON SYNCHRONIZER REGISTRY\r\n");
			writer.write("#PLEASE  DO NOT MODIFY THIS FILE!\r\n#");
			writer.write((new Date()).toString());
			writer.write("\r\n");

			// Write calendar uids
			Iterator keys = eventRegistry.keySet().iterator();
			Iterator iterator;
			String url;
			while (keys.hasNext()) {
				url = (String) keys.next();
				if (url == null || url.length() == 0) {
					continue;
				}

				// Write path
				writer.write("\r\nURL\t");
				writer.write(url);
				writer.write("\r\n\r\n");

				// Write uids
				uids = (HashMap) eventRegistry.get(url);
				if (uids != null) {
					iterator = uids.keySet().iterator();
					while (iterator.hasNext()) {
						uid = (String) iterator.next();
						writer.write(uid);
						writer.write('\t');
						writer.write(((Long) uids.get(uid)).toString());
						writer.write("\r\n");
					}
				}
			}

			// Save content
			out = new FileOutputStream(eventRegistryFile);
			out.write(writer.getBytes());
			out.flush();
			out.close();
			if (log.isDebugEnabled()) {
				log.debug("Event registry saved successfully ("
						+ writer.length() + " bytes).");
			}
		} catch (Exception ioError) {
			if (out != null) {
				try {
					out.close();
					eventRegistryFile.delete();
				} catch (Exception ignored) {
				}
			}
			log.warn("Unable to save event registry!", ioError);
		}
	}

}
