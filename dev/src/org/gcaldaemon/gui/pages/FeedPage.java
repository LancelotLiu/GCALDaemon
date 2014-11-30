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
import org.gcaldaemon.gui.editors.BooleanEditor;
import org.gcaldaemon.gui.editors.TimeEditor;

/**
 * Configurator page for RSS/ATOM feed converter.
 * 
 * Created: May 25, 2007 20:00:00 PM
 * 
 * @author Andras Berkes
 */
public final class FeedPage extends AbstractPage {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 6495075641018954784L;

	// --- CONSTRUCTOR ---

	public FeedPage(MainConfig config, ConfigEditor editor) throws Exception {
		super(config, editor);

		// # Enable RSS/ATOM feed to iCalendar converter
		// feed.enabled=true
		BooleanEditor enable = new BooleanEditor(config, editor,
				Configurator.FEED_ENABLED, true);
		enable.setIcon("plugin"); //$NON-NLS-1$
		enable.markAsServiceEnabler();
		addEditor(enable);

		// # Feed timeout in the local cache (recommended is '1 hour')
		// feed.cache.timeout=1 hour
		addEditor(new TimeEditor(config, editor,
				Configurator.FEED_CACHE_TIMEOUT, "1 min", "1 hour", "4 hour",
				TimeEditor.MIN, "min"));

		// # Length of feed events in calendar (default is '45 min')
		// feed.event.length=45 min
		addEditor(new TimeEditor(config, editor,
				Configurator.FEED_EVENT_LENGTH, "1 min", "45 min", "120 min",
				TimeEditor.MIN, "min"));

		// # Sensitivity of the duplication filter
		// # (50% = very sensitive, 100% = disabled)
		// feed.duplication.filter=70%
		addEditor(new TimeEditor(config, editor,
				Configurator.FEED_DUPLICATION_FILTER, "50", "70", "100", 1, "%"));
	}

}
