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

import javax.swing.Box;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.config.MainConfig;
import org.gcaldaemon.gui.editors.AccountEditor;
import org.gcaldaemon.gui.editors.BooleanEditor;
import org.gcaldaemon.gui.editors.ComboEditor;
import org.gcaldaemon.gui.editors.FilterEditor;
import org.gcaldaemon.gui.editors.SoundEditor;
import org.gcaldaemon.gui.editors.StyleEditor;
import org.gcaldaemon.gui.editors.TimeEditor;

/**
 * Configurator page for Gmail notifier.
 * 
 * Created: May 25, 2007 20:00:00 PM
 * 
 * @author Andras Berkes
 */
public final class NotifierPage extends AbstractPage {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = -3438270167624411762L;

	// --- CONSTRUCTOR ---

	public NotifierPage(MainConfig config, ConfigEditor editor)
			throws Exception {
		super(config, editor);

		// # Enable Gmail notifier
		// notifier.enabled=false
		BooleanEditor enable = new BooleanEditor(config, editor,
				Configurator.NOTIFIER_ENABLED, false);
		enable.setIcon("plugin"); //$NON-NLS-1$
		enable.markAsServiceEnabler();
		addEditor(enable);

		// # Gmail user (your full email address)
		// notifier.google.username=example@gmail.com
		// # Gmail password (use password encoder!)
		// notifier.google.password=5670x5VmXcjV24p
		addEditor(new AccountEditor(config, editor,
				Configurator.NOTIFIER_GOOGLE_USERNAME,
				Configurator.NOTIFIER_GOOGLE_PASSWORD));

		// # List of allowed local users (eg. 'root,peter*,*admin' or '*')
		// notifier.local.users=*
		addEditor(new FilterEditor(config, editor,
				Configurator.NOTIFIER_LOCAL_USERS, "root, peter*, *admin"));

		// # Style of the notifier's window
		// notifier.window.style=default
		addEditor(new StyleEditor(config, editor));

		// # Notifier's sound effect ('beep', 'sound' or WAV/AU/MID)
		// notifier.window.sound=beep
		addEditor(new SoundEditor(config, editor));
		editorPanel.add(Box.createRigidArea(BIG_SPACE));

		// # Date format in the notifier's window (default is 'yyyy.MM.dd
		// HH:mm:ss')
		// notifier.date.format=yyyy.MM.dd HH\:mm\:ss
		String[] formats = { "yyyy.MM.dd HH:mm:ss", "dd.MM.yyyy HH:mm:ss",
				"EEE, MMM d, HH:mm", "yyyy.MMM.dd 'at' HH:mm:ss",
				"yyyy-MM-dd'T'HH:mm:ss", "EEE, d MMM yyyy HH:mm" };
		addEditor(new ComboEditor(config, editor,
				Configurator.NOTIFIER_DATE_FORMAT, formats, formats[0], true));
		editorPanel.add(Box.createRigidArea(BIG_SPACE));

		// # Mailbox polling interval (recommended is '10 min')
		// notifier.polling.mailbox=10 min
		addEditor(new TimeEditor(config, editor,
				Configurator.NOTIFIER_POLLING_MAILBOX, "2 min", "10 min",
				"1 hour", TimeEditor.MIN, "min"));
	}

}
