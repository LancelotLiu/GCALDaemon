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
package org.gcaldaemon.core.file;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.core.CachedCalendar;
import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.ICalUtilities;
import org.gcaldaemon.core.Request;
import org.gcaldaemon.core.StringUtils;
import org.gcaldaemon.logger.QuickWriter;

/**
 * iCalendar (ICS) file synchronizer thread. Does not supports offline working
 * (requires permanent internet connection), but mutch more faster than the
 * offline synchronizer.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public class OnlineFileListener extends Thread {

	// --- CONSTANTS ---

	private static final long FAST_POLLING_TIME = 1000L;
	private static final int FAST_POLLING_LOOPS = 60;
	private static final int MAX_INDEX_GAP = 100;

	private static final String CRLF = "\r\n";

	// --- LOGGER ---

	protected static final Log log = LogFactory
			.getLog(OnlineFileListener.class);

	// --- VARIABLES ---

	protected final Configurator configurator;

	private final long filePollingTime;
	private final String reloaderScript;

	protected final long googlePollingTime;

	protected final File[] iCalFiles;
	protected final String[] usernames;
	protected final String[] passwords;
	protected final String[] urls;

	// --- OFFLINE FLAG ---

	protected boolean offlineMode = false;

	// --- CONSTRUCTOR ---

	public OnlineFileListener(ThreadGroup mainGroup, Configurator configurator)
			throws Exception {
		super(mainGroup, "File listener");
		this.configurator = configurator;

		// Get polling times
		long timeout = configurator.getConfigProperty(
				Configurator.FILE_POLLING_FILE, 10000L);
		if (timeout < 1000L) {
			log.warn("The fastest file polling period is '1 sec'!");
			timeout = 1000L;
		}
		filePollingTime = timeout;
		timeout = configurator.getConfigProperty(
				Configurator.FILE_POLLING_GOOGLE, 600000L);
		if (timeout < 180000L) {
			log.warn("The fastest Google Calendar polling period is '3 min'!");
			timeout = 180000L;
		}
		googlePollingTime = timeout;

		// Get reloader script's path
		reloaderScript = configurator.getConfigProperty(
				Configurator.FILE_RELOADER_SCRIPT, null);

		// Get parameters
		LinkedList iCalFileList = new LinkedList();
		LinkedList usernameList = new LinkedList();
		LinkedList passwordList = new LinkedList();
		LinkedList urlList = new LinkedList();
		String parameterPostfix;
		int gapCounter = 0;
		for (int j, i = 1;; i++) {

			// Create parameter postfix [..n]
			if (i == 1) {
				parameterPostfix = "";
			} else {
				parameterPostfix = Integer.toString(i);
			}
			if (configurator.getConfigProperty(Configurator.FILE_ICAL_PATH
					+ parameterPostfix, null) == null) {
				if (gapCounter < MAX_INDEX_GAP) {
					gapCounter++;
					continue;
				}
				break;
			}
			gapCounter = 0;

			// Get local file path
			String filePath = configurator.getConfigProperty(
					Configurator.FILE_ICAL_PATH + parameterPostfix, "/google"
							+ i + ".ics");
			if (filePath.startsWith("~")) {
				filePath = filePath.substring(1);
			}
			if (filePath.endsWith("/*.ics")) {
				filePath = filePath.substring(0, filePath.length() - 6);
			}
			log.info("Start listening file " + filePath + "...");
			File iCalFile = new File(filePath);

			// Get username
			String username = configurator.getConfigProperty(
					Configurator.FILE_GOOGLE_USERNAME + parameterPostfix, null);

			// Get password
			String password = null;
			if (configurator.getConfigProperty(
					Configurator.FILE_GOOGLE_PASSWORD + parameterPostfix, null) != null) {
				password = configurator
						.getPasswordProperty(Configurator.FILE_GOOGLE_PASSWORD
								+ parameterPostfix);
			}

			// Get calendar URL
			String url = configurator
					.getConfigProperty(Configurator.FILE_PRIVATE_ICAL_URL
							+ parameterPostfix, null);

			// Verify parameters
			if (url == null) {
				throw new NullPointerException("Missing private ICAL URL ("
						+ Configurator.FILE_PRIVATE_ICAL_URL + parameterPostfix
						+ ")!");
			}
			if (!configurator.isFeedConverterEnabled()) {
				if (username == null) {
					throw new NullPointerException("Missing username ("
							+ Configurator.FILE_GOOGLE_USERNAME
							+ parameterPostfix + ")!");
				}
				if (password == null) {
					throw new NullPointerException("Missing password ("
							+ Configurator.FILE_GOOGLE_PASSWORD
							+ parameterPostfix + ")!");
				}
				j = url.indexOf("/calendar");
				if (j > 0) {
					url = url.substring(j);
				}
				if (url.charAt(0) != '/') {
					throw new NullPointerException("Invalid private ICAL URL ("
							+ Configurator.FILE_PRIVATE_ICAL_URL
							+ parameterPostfix + ")!");
				}
				j = url.indexOf('@');
				if (j != -1) {
					url = url.substring(0, j) + "%40" + url.substring(j + 1);
				}
				j = url.indexOf("googlemail.com");
				if (j != -1) {
					url = url.substring(0, j) + "gmail.com"
							+ url.substring(j + 14);
				}
			} else {
				if (url.startsWith("calendar/")) {
					url = '/' + url;
				}
			}

			// Add parameters to lists
			iCalFileList.addLast(iCalFile);
			usernameList.addLast(username);
			passwordList.addLast(password);
			urlList.addLast(url);
		}

		// Create object arrays
		iCalFiles = new File[iCalFileList.size()];
		usernames = new String[usernameList.size()];
		passwords = new String[passwordList.size()];
		urls = new String[urlList.size()];
		iCalFileList.toArray(iCalFiles);
		usernameList.toArray(usernames);
		passwordList.toArray(passwords);
		urlList.toArray(urls);
		log.info("File listener started successfully.");

		// Start listener
		start();
	}

	// --- FILE LISTENER LOOP ---

	private byte[][] lastCalendarBytes;
	private long[] calendarLastModified;
	private long[] calendarLastChecked;
	private long[] fileLastModified;

	public void run() {

		// Create processing arrays
		int fastPollingCounter = FAST_POLLING_LOOPS;
		int files = iCalFiles.length;
		lastCalendarBytes = new byte[files][];
		calendarLastModified = new long[files];
		calendarLastChecked = new long[files];
		fileLastModified = new long[files];

		try {

			// Main loop
			for (;;) {

				// Sleeping
				synchronized (this) {
					if (fastPollingCounter < FAST_POLLING_LOOPS) {
						wait(FAST_POLLING_TIME);
						fastPollingCounter++;
					} else {
						wait(filePollingTime);
					}
				}

				// Loop on files
				long now = System.currentTimeMillis();
				boolean fileChanged = false;
				for (int fileIndex = 0; fileIndex < files; fileIndex++) {

					// Create request
					Request request = new Request();
					request.url = urls[fileIndex];
					request.username = usernames[fileIndex];
					request.password = passwords[fileIndex];

					// Verify file's timestamp
					if (iCalFiles[fileIndex].exists()) {
						long lastModified = lastModified(iCalFiles[fileIndex]);
						if (lastModified != fileLastModified[fileIndex]
								&& fileLastModified[fileIndex] != 0) {
							request.body = loadFile(fileIndex);
							fileLastModified[fileIndex] = lastModified;
							fastPollingCounter = 0;
							if (request.body != null) {
								if (!isEquals(lastCalendarBytes[fileIndex],
										request.body)) {
									lastCalendarBytes[fileIndex] = request.body;
									configurator.calendarChanged(request);
								}
								if (now - calendarLastChecked[fileIndex] < googlePollingTime) {
									continue;
								}
							}
						}
					}

					// Download iCal file (or get from cache)
					if (now - calendarLastChecked[fileIndex] >= googlePollingTime) {
						calendarLastChecked[fileIndex] = now;
						CachedCalendar calendar = configurator
								.getCalendar(request);

						// Verify calendar's timestamp
						if (calendar.lastModified != calendarLastModified[fileIndex]) {
							calendarLastModified[fileIndex] = calendar.lastModified;
							byte[] bytes = calendar.toByteArray();

							if (!isEquals(lastCalendarBytes[fileIndex], bytes)) {
								fileLastModified[fileIndex] = saveFile(bytes,
										fileIndex);
								lastCalendarBytes[fileIndex] = new byte[bytes.length];
								System.arraycopy(bytes, 0,
										lastCalendarBytes[fileIndex], 0,
										bytes.length);
								fileChanged = true;
							}
						}
					}

					// Wait
					sleep(100);
				}

				// Reload calendars
				if (fileChanged) {
					reloadCalendar();
				}

			}
		} catch (InterruptedException interrupt) {

			// Service stopped
			log.info("File listener stopped.");
		} catch (Exception fatalError) {

			// Fatal error
			log.fatal("Fatal service error!", fatalError);
		}
	}

	protected static final boolean isEquals(byte[] iCalBytes1, byte[] iCalBytes2)
			throws Exception {
		if (iCalBytes1 == null || iCalBytes2 == null) {
			return false;
		}
		String iCal1 = removeTimestamps(iCalBytes1);
		String iCal2 = removeTimestamps(iCalBytes2);
		return iCal1.equals(iCal2);
	}

	private static final String removeTimestamps(byte[] iCalBytes)
			throws Exception {
		String iCal = StringUtils.decodeToString(iCalBytes, StringUtils.UTF_8);
		StringTokenizer st = new StringTokenizer(iCal, CRLF);
		QuickWriter writer = new QuickWriter(iCal.length());
		boolean started = false;
		String line;
		while (st.hasMoreTokens()) {
			line = st.nextToken();
			if (!started) {
				if (line.equals("BEGIN:VEVENT") || line.equals("BEGIN:VTODO")) {
					started = true;
				}
				continue;
			}
			if (line.indexOf("STAMP") != -1) {
				continue;
			}
			if (line.startsWith("UID") || line.startsWith("PRODID")
					|| line.startsWith("X-")) {
				continue;
			}
			if (line.startsWith(" ")) {
				line = line.substring(1);
				writer.setLength(writer.length() - 2);
			}
			writer.write(line);
			writer.write(CRLF);
		}
		return writer.toString();
	}

	// --- I/O HANDLERS ---

	protected final long lastModified(File file) throws Exception {
		long modified = file.lastModified();
		if (file.isDirectory()) {
			File[] subFiles = file.listFiles();
			long test;
			for (int i = 0; i < subFiles.length; i++) {
				test = subFiles[i].lastModified();
				if (test > modified) {
					modified = test;
				}
			}
		}
		return modified;
	}

	protected final byte[] loadFile(int fileIndex) throws Exception {
		if (iCalFiles[fileIndex].isDirectory()) {

			// MacOSX Leopard (one calendar = multiple iCal files)
			return loadDir(fileIndex);
		}

		// Standard iCal file (one calendar = one iCal file)
		RandomAccessFile raf = null;
		for (int tries = 0;; tries++) {
			try {
				if (log.isDebugEnabled()) {
					log.debug("Loading file "
							+ iCalFiles[fileIndex].getCanonicalPath().replace(
									'\\', '/') + "...");
				}
				raf = new RandomAccessFile(iCalFiles[fileIndex], "r");
				int len = (int) iCalFiles[fileIndex].length();
				byte[] bytes = new byte[len];
				raf.readFully(bytes);
				raf.close();
				if (log.isDebugEnabled()) {
					log.debug("File loaded successfully (" + len + " bytes).");
				}
				return bytes;
			} catch (Exception loadError) {
				if (raf != null) {
					try {
						raf.close();
					} catch (Exception ignored) {
					}
				}
				if (tries == 5) {
					throw loadError;
				}
				Thread.sleep(500);
			}
		}
	}

	private final byte[] loadDir(int fileIndex) throws Exception {

		// MacOSX Leopard (one calendar = multiple iCal files)
		File dir = iCalFiles[fileIndex];
		File[] files = dir.listFiles();

		QuickWriter writer = new QuickWriter();
		writer.write("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:");
		writer.write(Configurator.VERSION);
		writer.write("\r\n");
		ComponentList list;
		Calendar event;
		String content;
		File file;

		// Load and concatenate events and todos
		for (int i = 0; i < files.length; i++) {
			file = files[i];
			if (!file.getName().endsWith(".ics")) {
				continue;
			}
			event = loadEvent(file);
			list = event.getComponents();
			if (list != null && !list.isEmpty()) {
				content = list.toString();
				if (content != null && content.indexOf("BEGIN") != -1) {
					writer.write(content);
				}
			}
		}
		writer.write("END:VCALENDAR\r\n");
		return StringUtils.encodeString(writer.toString(), StringUtils.UTF_8);
	}

	private final Calendar loadEvent(File file) throws Exception {

		// MacOSX Leopard (one calendar = multiple iCal files)
		RandomAccessFile raf = null;
		for (int tries = 0;; tries++) {
			try {
				if (log.isDebugEnabled()) {
					log.debug("Loading file "
							+ file.getCanonicalPath().replace('\\', '/')
							+ "...");
				}
				raf = new RandomAccessFile(file, "r");
				int len = (int) file.length();
				byte[] bytes = new byte[len];
				raf.readFully(bytes);
				raf.close();
				if (log.isDebugEnabled()) {
					log.debug("File loaded successfully (" + len + " bytes).");
				}
				return ICalUtilities.parseCalendar(bytes);
			} catch (Exception loadError) {
				if (raf != null) {
					try {
						raf.close();
					} catch (Exception ignored) {
					}
				}
				if (tries == 5) {
					throw loadError;
				}
				Thread.sleep(500);
			}
		}
	}

	protected final long saveFile(byte[] iCalBytes, int fileIndex)
			throws Exception {
		if (iCalFiles[fileIndex].isDirectory()) {

			// MacOSX Leopard (one calendar = multiple iCal files)
			return saveDir(iCalBytes, fileIndex);
		}

		// Standard iCal file (one calendar = one iCal file)
		FileOutputStream fos = null;
		for (int tries = 0;; tries++) {
			try {
				if (log.isDebugEnabled()) {
					log.debug("Saving file "
							+ iCalFiles[fileIndex].getCanonicalPath().replace(
									'\\', '/') + "...");
				}
				fos = new FileOutputStream(iCalFiles[fileIndex]);
				fos.write(iCalBytes);
				fos.flush();
				fos.close();
				if (log.isDebugEnabled()) {
					log.debug("File saved successfully ("
							+ iCalFiles[fileIndex].length() + " bytes).");
				}
				return iCalFiles[fileIndex].lastModified();
			} catch (Exception saveError) {
				if (fos != null) {
					try {
						fos.close();
					} catch (Exception ignored) {
					}
				}
				if (tries == 5) {
					throw saveError;
				}
				Thread.sleep(500);
			}
		}
	}

	private final long saveDir(byte[] iCalBytes, int fileIndex)
			throws Exception {

		// Mac OS X Leopard (one calendar = multiple iCal files)
		// Get original/old iCalendar files
		File dir = iCalFiles[fileIndex];
		HashSet oldFiles = new HashSet();
		oldFiles.addAll(Arrays.asList(dir.list()));

		// Get component array
		Calendar container = ICalUtilities.parseCalendar(iCalBytes);
		ComponentList list = container.getComponents();
		Component[] array = new Component[list.size()];
		list.toArray(array);

		// Save component
		String fileName;
		for (int i = 0; i < array.length; i++) {
			fileName = saveComponent(dir, array[i], fileIndex);
			oldFiles.remove(fileName);
		}

		// Remove deleted events
		Iterator removedNames = oldFiles.iterator();
		File file;
		while (removedNames.hasNext()) {
			fileName = (String) removedNames.next();
			if (!fileName.endsWith(".ics")) {
				continue;
			}
			try {
				file = new File(dir, fileName);
				file.delete();
				if (log.isDebugEnabled()) {
					log
							.debug("File deleted ("
									+ file.getCanonicalPath()
											.replace('\\', '/') + ").");
				}
			} catch (Exception ignored) {
			}
		}

		// Return the last modified file's timestamp
		return lastModified(dir);
	}

	private final String saveComponent(File dir, Component component,
			int fileIndex) throws Exception {

		// Mac OS X Leopard (one calendar = multiple iCal files)
		String name = component.getName();
		if (name == null
				|| (!name.equals(Component.VEVENT) && !name
						.equals(Component.VTODO))) {
			return "";
		}
		Property uidProperty = component.getProperty(Property.UID);
		if (uidProperty == null) {
			return "";
		}
		String uid = uidProperty.getValue();
		if (uid == null) {
			return "";
		}

		// Create event file
		QuickWriter writer = new QuickWriter();
		writer.write("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:");
		writer.write(Configurator.VERSION);
		writer.write("\r\n");
		writer.write(component.toString());
		writer.write("END:VCALENDAR\r\n");
		byte[] iCalBytes = StringUtils.encodeString(writer.toString(),
				StringUtils.UTF_8);

		// Save event
		name = uid.replace('@', '-') + ".ics";
		FileOutputStream fos = null;
		for (int tries = 0;; tries++) {
			try {
				File file = new File(dir, name);
				if (log.isDebugEnabled()) {
					log.debug("Saving file "
							+ file.getCanonicalPath().replace('\\', '/')
							+ "...");
				}
				fos = new FileOutputStream(file);
				fos.write(iCalBytes);
				fos.flush();
				fos.close();
				if (log.isDebugEnabled()) {
					log.debug("File saved successfully (" + file.length()
							+ " bytes).");
				}
				break;
			} catch (Exception saveError) {
				if (fos != null) {
					try {
						fos.close();
					} catch (Exception ignored) {
					}
				}
				if (tries == 5) {
					throw saveError;
				}
				Thread.sleep(500);
			}
		}

		// Return file name
		return name;
	}

	// --- REFRESH ICAL FILE FROM CACHE ---

	public void wakeUp() throws Exception {
		synchronized (this) {
			for (int fileIndex = 0; fileIndex < calendarLastChecked.length; fileIndex++) {
				calendarLastChecked[fileIndex] = 0;
			}
			this.notifyAll();
		}
	}

	// --- RELOAD CALENDAR APPLICATION ---

	private CalendarReloader reloader;

	protected final void reloadCalendar() {

		// Remove file cache (MacOSX Leopard)
		clearFileCache();

		if (reloaderScript == null) {

			// Missing reloader script
			return;
		}

		// Execute custom reloader script
		boolean runOnce = configurator.getRunMode() == Configurator.MODE_RUNONCE;
		if (reloader == null) {
			reloader = new CalendarReloader(reloaderScript);
			if (!runOnce) {
				reloader.start();
			}
		}
		reloader.reload(runOnce);
		if (runOnce) {
			reloader.interrupt();
			reloader = null;
		}
	}

	// --- CLEAR FILE CACHES ---

	private final void clearFileCache() {
		try {

			// Compute cache path of user (MacOSX Leopard)
			String userHome = System.getProperty("user.home");
			if (userHome == null || userHome.length() == 0) {
				return;
			}
			File cacheFile = new File(userHome,
					"Library/Calendars/Calendar Cache");

			// Delete SQLITE3 database file (if exists)
			cacheFile.delete();
		} catch (Exception ioError) {
			log.debug("Unable to remove cache file!", ioError);
		}
	}

	// --- STOP SERVICE ---

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Thread#interrupt()
	 */
	public void interrupt() {
		super.interrupt();
		if (reloader != null) {
			reloader.interrupt();
			reloader = null;
		}
		if (offlineMode) {
			return;
		}

		// Save messages
		try {
			long now = System.currentTimeMillis();
			String start = new DateTime(now).toString();
			String end = new DateTime(now + 2700000L).toString();
			QuickWriter writer = new QuickWriter(600);
			writer.write("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:");
			writer.write(Configurator.VERSION);
			writer.write("\r\n");
			writer.write("BEGIN:VEVENT\r\nCREATED:");
			writer.write(start);
			writer.write("\r\nLAST-MODIFIED:");
			writer.write(start);
			writer.write("\r\nDTSTAMP:");
			writer.write(start);
			writer.write("\r\nUID:gcaldaemon-error\r\nSUMMARY:UNAVAILABLE\r\n");
			writer.write("DTSTART:");
			writer.write(start);
			writer.write("\r\nDTEND:");
			writer.write(end);
			writer.write("\r\nDESCRIPTION:Service stopped!\\n");
			writer.write("Please do not modify this \r\n calendar!\r\n");
			writer.write("END:VEVENT\r\nEND:VCALENDAR\r\n");
			byte[] bytes = writer.getBytes();
			for (int fileIndex = 0; fileIndex < iCalFiles.length; fileIndex++) {
				saveFile(bytes, fileIndex);
			}
		} catch (Exception ignored) {
		}
	}

}
