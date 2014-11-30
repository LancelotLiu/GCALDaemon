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

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.config.MainConfig;
import org.gcaldaemon.gui.editors.AccountEditor;
import org.gcaldaemon.gui.editors.BooleanEditor;
import org.gcaldaemon.gui.editors.ComboEditor;
import org.gcaldaemon.gui.editors.FileEditor;
import org.gcaldaemon.gui.editors.FilterEditor;
import org.gcaldaemon.gui.editors.PasswordEditor;
import org.gcaldaemon.gui.editors.TimeEditor;

/**
 * Configurator page for common mailterm service.
 * 
 * Created: May 25, 2007 20:00:00 PM
 * 
 * @author Andras Berkes
 */
public final class MailtermPage extends AbstractPage {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 4555968638040659135L;

	// --- CONSTRUCTOR ---

	public MailtermPage(MainConfig config, ConfigEditor editor)
			throws Exception {
		super(config, editor);

		// # Enable Gmail terminal
		// mailterm.enabled=false
		BooleanEditor enable = new BooleanEditor(config, editor,
				Configurator.MAILTERM_ENABLED, false);
		enable.setIcon("plugin"); //$NON-NLS-1$
		enable.markAsServiceEnabler();
		addEditor(enable);

		// # Gmail user (your full email address)
		// mailterm.google.username=example@gmail.com
		// # Gmail password (use password encoder!)
		// mailterm.google.password=5670x5VmXcjV24p
		addEditor(new AccountEditor(config, editor,
				Configurator.MAILTERM_GOOGLE_USERNAME,
				Configurator.MAILTERM_GOOGLE_PASSWORD));

		// # List of allowed e-mail addresses (eg. 'admin@home.net')
		// mailterm.allowed.addresses=*
		addEditor(new FilterEditor(config, editor,
				Configurator.MAILTERM_ALLOWED_ADDRESSES,
				"admin@*, myname@gmail.com, *@company.com"));

		// # Console encoding (Cp850, Cp852, etc, default is 'US-ASCII')
		// mailterm.console.encoding=US-ASCII
		SortedMap map = Charset.availableCharsets();
		String[] encodings = null;
		if (map != null) {
			Set set = map.keySet();
			if (set != null) {
				HashSet swap = new HashSet(set.size());
				swap.addAll(set);
				swap.add("US-ASCII");
				set = swap;
				encodings = new String[set.size()];
				set.toArray(encodings);
				Arrays.sort(encodings, String.CASE_INSENSITIVE_ORDER);
			}
		}
		if (encodings == null || encodings.length == 0) {
			encodings = new String[1];
			encodings[0] = "US-ASCII";
		}
		addEditor(new ComboEditor(config, editor,
				Configurator.MAILTERM_CONSOLE_ENCODING, encodings, "US-ASCII",
				false));

		// # Subject of command mails (use password encoder!)
		// mailterm.mail.subject=5670x5VmXcjV24p
		addEditor(new PasswordEditor(config, editor,
				Configurator.MAILTERM_MAIL_SUBJECT));

		// # Full path of the script directory
		// mailterm.dir.path=C\:/scripts
		addEditor(new FileEditor(config, editor,
				Configurator.MAILTERM_DIR_PATH, "/scripts", false));

		// # Gmail inbox polling interval (recommended is '10 min')
		// mailterm.polling.google=10 min
		addEditor(new TimeEditor(config, editor,
				Configurator.MAILTERM_POLLING_GOOGLE, "2 min", "10 min",
				"1 hour", TimeEditor.MIN, "min"));
	}
}
