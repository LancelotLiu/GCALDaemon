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
package org.gcaldaemon.gui.pages;

import java.io.File;
import java.util.LinkedList;

import javax.swing.Box;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.config.MainConfig;
import org.gcaldaemon.gui.editors.AlarmEditor;
import org.gcaldaemon.gui.editors.BooleanEditor;
import org.gcaldaemon.gui.editors.ComboEditor;
import org.gcaldaemon.gui.editors.FileEditor;
import org.gcaldaemon.gui.editors.NumberEditor;
import org.gcaldaemon.gui.editors.PasswordEditor;
import org.gcaldaemon.gui.editors.StringEditor;
import org.gcaldaemon.gui.editors.TimeEditor;

/**
 * Configurator page for common settings.
 * 
 * Created: May 25, 2007 20:00:00 PM
 * 
 * @author Andras Berkes
 */
public final class CommonPage extends AbstractPage {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 3027091319209539803L;

	// --- CONSTRUCTOR ---

	public CommonPage(MainConfig config, ConfigEditor editor) throws Exception {
		super(config, editor);

		// # Show animated progress bar while synching
		// progress.enabled=false
		BooleanEditor progress = new BooleanEditor(config, editor,
				Configurator.PROGRESS_ENABLED, false);
		progress.setIcon("progress"); //$NON-NLS-1$
		addEditor(progress);

		// # Google Calendar send an email to the attendees to invite them to
		// # attend
		// send.invitations=false
		BooleanEditor invitations = new BooleanEditor(config, editor,
				Configurator.SEND_INVITATIONS, false);
		invitations.setIcon("mail"); //$NON-NLS-1$
		addEditor(invitations);

		// # Enable to sync alarms, categories, urls, priorities
		// # (reduces the performance!)
		// extended.sync.enabled=false
		BooleanEditor extended = new BooleanEditor(config, editor,
				Configurator.EXTENDED_SYNC_ENABLED, false);
		extended.setIcon("alarm"); //$NON-NLS-1$
		addEditor(extended);
		editorPanel.add(Box.createRigidArea(BIG_SPACE));

		// # Name of the Log4J configuration file (file name or full path)
		// log.config=logger-config.cfg
		addEditor(new FileEditor(config, editor, Configurator.LOG_CONFIG,
				"logger-config.cfg", true));

		// # Full path of the working directory (or empty)
		// work.dir=
		String path = config.getConfigProperty(Configurator.WORK_DIR, null);
		String programDir = System.getProperty("gcaldaemon.program.dir",
				"/Progra~1/GCALDaemon");
		if (path != null) {
			path = path.replace('\\', '/');
		} else {
			File dir = new File(programDir, "work");
			if (dir.isDirectory()) {
				path = dir.getCanonicalPath().replace('\\', '/');
			} else {
				path = "";
			}
		}
		addEditor(new FileEditor(config, editor, Configurator.WORK_DIR, path,
				false));

		// # Full path of the calendar reloader script (or empty)
		// file.reloader.script=
		String commonPart = programDir.replace('\\', '/')
				+ "/bin/reload-calendar.";
		LinkedList list = new LinkedList();
		String os = System.getProperty("os.name", "unknown").toLowerCase();
		list.addLast("");
		if (os.indexOf("windows") == -1) {
			list.addLast("'" + commonPart + "sh'");
			if (os.indexOf("mac") != -1) {
				list.addLast("'/usr/bin/osascript' '" + commonPart + "scpt'");
			}
		} else {
			list.addLast("'" + commonPart + "bat'");
		}
		String[] scripts = new String[list.size()];
		list.toArray(scripts);
		addEditor(new ComboEditor(config, editor,
				Configurator.FILE_RELOADER_SCRIPT, scripts, "", true));

		// # Alarm type(s) in Google Calendar (defaults are 'email,sms,popup')
		// remote.alarm.types=email,sms,popup
		addEditor(new AlarmEditor(config, editor));
		editorPanel.add(Box.createRigidArea(BIG_SPACE));

		// # HTTP proxy host (eg. 'firewall.mycompany.com' or empty)
		// proxy.host=
		addEditor(new StringEditor(config, editor, Configurator.PROXY_HOST, ""));

		// # HTTP proxy port (eg. '8080' or empty)
		// proxy.port=
		addEditor(new NumberEditor(config, editor, Configurator.PROXY_PORT,
				8080));

		// # Username for HTTP proxy authentication (username or empty)
		// proxy.username=
		addEditor(new StringEditor(config, editor, Configurator.PROXY_USERNAME,
				""));

		// # Password for HTTP proxy authentication (use password encoder!)
		// proxy.password=
		addEditor(new PasswordEditor(config, editor,
				Configurator.PROXY_PASSWORD));
		editorPanel.add(Box.createRigidArea(BIG_SPACE));

		// # Calendar timeout in the local cache (recommended is '3 min')
		// cache.timeout=3 min
		addEditor(new TimeEditor(config, editor, Configurator.CACHE_TIMEOUT,
				"1 min", "3 min", "10 min", TimeEditor.MIN, "min"));

		// # Backup file timeout (0 = don't create backups, default is '7 day')
		// ical.backup.timeout=7 day
		addEditor(new TimeEditor(config, editor,
				Configurator.ICAL_BACKUP_TIMEOUT, "0 day", "7 day", "30 day",
				TimeEditor.DAY, "day"));
	}

}
