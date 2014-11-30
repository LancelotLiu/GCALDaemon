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
package org.gcaldaemon.gui.editors;

import java.awt.Dimension;

import javax.swing.JPanel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.config.MainConfig;

/**
 * Abstract property editor.
 * 
 * Created: May 25, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public abstract class AbstractEditor extends JPanel {

	// --- COMMON CONSTANTS ---

	protected static final Dimension LABEL_SIZE = new Dimension(200, 20);

	// --- LOGGER ---

	protected static final Log log = LogFactory.getLog(AbstractEditor.class);

	// --- CONFIGURATOR ---

	protected final MainConfig config;
	protected final ConfigEditor editor;
	protected final String key;

	// --- CONSTRUCTOR ---

	public AbstractEditor(MainConfig config, ConfigEditor editor, String key)
			throws Exception {
		this.config = config;
		this.editor = editor;
		this.key = key;
	}

}
