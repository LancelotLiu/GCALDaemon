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

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.config.MainConfig;
import org.gcaldaemon.gui.editors.AccountEditor;
import org.gcaldaemon.gui.editors.BooleanEditor;
import org.gcaldaemon.gui.editors.FileEditor;
import org.gcaldaemon.gui.editors.TimeEditor;

/**
 * Configurator page for sendmail service.
 * 
 * Created: May 25, 2007 20:00:00 PM
 * 
 * @author Andras Berkes
 */
public final class SendmailPage extends AbstractPage {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = -4599524009835145502L;

	// --- CONSTRUCTOR ---

	public SendmailPage(MainConfig config, ConfigEditor editor)
			throws Exception {
		super(config, editor);

		// # Enable Gmail sender service
		// sendmail.enabled=false
		BooleanEditor enable = new BooleanEditor(config, editor,
				Configurator.SENDMAIL_ENABLED, false);
		enable.setIcon("plugin"); //$NON-NLS-1$
		enable.markAsServiceEnabler();
		addEditor(enable);

		// # Gmail user (your full email address)
		// sendmail.google.username=example@gmail.com
		// # Gmail password (use password encoder!)
		// sendmail.google.password=5670x5VmXcjV24p
		addEditor(new AccountEditor(config, editor,
				Configurator.SENDMAIL_GOOGLE_USERNAME,
				Configurator.SENDMAIL_GOOGLE_PASSWORD));

		// # Full path of the outgoing mail directory
		// sendmail.dir.path=C\:/outbox
		addEditor(new FileEditor(config, editor,
				Configurator.SENDMAIL_DIR_PATH, "/outbox", false));

		// # Outgoing directory polling interval (recommended is '10 sec')
		// sendmail.polling.dir=10 sec
		addEditor(new TimeEditor(config, editor,
				Configurator.SENDMAIL_POLLING_DIR, "1 sec", "10 sec", "3 min",
				TimeEditor.SEC, "sec"));
	}

}
