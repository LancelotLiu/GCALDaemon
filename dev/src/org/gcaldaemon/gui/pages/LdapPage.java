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
import org.gcaldaemon.gui.editors.LDAPEditor;
import org.gcaldaemon.gui.editors.BooleanEditor;
import org.gcaldaemon.gui.editors.ComboEditor;
import org.gcaldaemon.gui.editors.FilterEditor;
import org.gcaldaemon.gui.editors.NumberEditor;
import org.gcaldaemon.gui.editors.TimeEditor;

/**
 * Configurator page for LDAP server.
 * 
 * Created: May 25, 2007 20:00:00 PM
 * 
 * @author Andras Berkes
 */
public final class LdapPage extends AbstractPage {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 1834045297654223867L;

	// --- CONSTRUCTOR ---

	public LdapPage(MainConfig config, ConfigEditor editor) throws Exception {
		super(config, editor);

		// # Enable LDAP server
		// ldap.enabled=false
		BooleanEditor enable = new BooleanEditor(config, editor,
				Configurator.LDAP_ENABLED, false);
		enable.setIcon("plugin"); //$NON-NLS-1$
		enable.markAsServiceEnabler();
		addEditor(enable);

		// # Gmail user (your full email address)
		// ldap.google.username=example@gmail.com
		// # Gmail password (use password encoder!)
		// ldap.google.password=5670x5VmXcjV24p
		// 1..n
		addEditor(new LDAPEditor(config, editor));
		editorPanel.add(Box.createRigidArea(BIG_SPACE));

		// # vCard encoding ('quoted', 'native' or 'utf-8', default is 'quoted')
		// ldap.vcard.encoding=quoted
		String[] encodings = { "quoted", "native", "utf-8" };
		addEditor(new ComboEditor(config, editor,
				Configurator.LDAP_VCARD_ENCODING, encodings, encodings[0],
				false));

		// # vCard version ('2.1', '3.0', default is '3.0')
		// ldap.vcard.version=2.1
		String[] versions = { "2.1", "3.0" };
		addEditor(new ComboEditor(config, editor,
				Configurator.LDAP_VCARD_VERSION, versions, versions[1], false));
		editorPanel.add(Box.createRigidArea(BIG_SPACE));

		// # List of allowed hostnames
		// ldap.allowed.hostnames=*
		addEditor(new FilterEditor(config, editor,
				Configurator.LDAP_ALLOWED_HOSTNAMES,
				"*.mydomain.com, localhost, userpc.domain.*"));

		// # List of allowed IP addresses
		// ldap.allowed.addresses=*
		addEditor(new FilterEditor(config, editor,
				Configurator.LDAP_ALLOWED_ADDRESSES,
				"*.23.45.5, 127.0.0.1, 211.32.*"));
		editorPanel.add(Box.createRigidArea(BIG_SPACE));

		// # Port of the LDAP server (default is '9080')
		// ldap.port=9080
		addEditor(new NumberEditor(config, editor, Configurator.LDAP_PORT, 9080));
		editorPanel.add(Box.createRigidArea(BIG_SPACE));

		// # Contact list timeout in the local cache (recommended is '1 hour')
		// ldap.cache.timeout=1 hour
		addEditor(new TimeEditor(config, editor,
				Configurator.LDAP_CACHE_TIMEOUT, "30 min", "1 hour", "4 hour",
				TimeEditor.MIN, "min"));
	}

}
