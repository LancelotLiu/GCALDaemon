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

import org.gcaldaemon.core.CachedCalendar;
import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.GCalUtilities;
import org.gcaldaemon.core.Request;

/**
 * 'On Demand' file synchronizer. Supports offline working.
 * 
 * Created: Mar 24, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class OfflineFileListener extends OnlineFileListener {

	// --- CONSTRUCTOR ---

	public OfflineFileListener(ThreadGroup mainGroup, Configurator configurator)
			throws Exception {
		super(mainGroup, configurator);
		offlineMode = true;
		log.info("Offline file synchronization enabled.");
	}

	// --- SYNCRONIZER LOOP ---

	public final void run() {

		// Main loop
		for (;;) {

			// Loop on files
			int files = iCalFiles.length;
			boolean fileChanged = false;
			for (int fileIndex = 0; fileIndex < files; fileIndex++) {
				try {

					// Create request
					Request request = new Request();
					request.url = urls[fileIndex];
					request.username = usernames[fileIndex];
					request.password = passwords[fileIndex];
					request.filePath = iCalFiles[fileIndex].getAbsolutePath();

					// Synchronize
					if (iCalFiles[fileIndex].exists()) {
						request.body = loadFile(fileIndex);
						configurator.synchronizeNow(request);
					}

					// Download the new ics file
					CachedCalendar calendar = configurator.getCalendar(request);
					byte[] bytes = calendar.toByteArray();

					// Verify loaded ics file
					char[] chars = new char[Math.min(bytes.length, 100)];
					for (int i = 0; i < chars.length; i++) {
						chars[i] = (char) bytes[i];
					}
					if ((new String(chars)).indexOf(GCalUtilities.ERROR_MARKER) != -1) {
						continue;
					}

					// Save modified ics
					if (!isEquals(request.body, bytes)) {
						saveFile(bytes, fileIndex);
						fileChanged = true;
					}

					// Sleep in service mode
					if (configurator.getRunMode() != Configurator.MODE_RUNONCE) {
						sleep(100);
					}

				} catch (InterruptedException interrupt) {

					// Service stopped
					log.info("File listener stopped.");
					return;

				} catch (Exception fatalError) {

					// Fatal error
					log.fatal("Fatal service error!", fatalError);
				}
			}

			// Reload calendars
			if (fileChanged) {
				reloadCalendar();
			}

			// Quit in 'on demand' mode
			if (configurator.getRunMode() == Configurator.MODE_RUNONCE) {
				log.info("Synchronization finished.");
				System.exit(0);
			}
			log.debug("Synchronization finished.");

			// Sleep
			try {
				if (log.isDebugEnabled()) {
					log.debug("Process is suspended for " + googlePollingTime
							+ " milliseconds.");
				}
				sleep(googlePollingTime);
			} catch (InterruptedException interrupt) {

				// Service stopped
				log.info("File listener stopped.");
				return;
			}

		}
	}

	// --- OVERRIDE WAKEUP ---

	public final void wakeUp() throws Exception {

		// Do nothing
	}

}
