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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.config.MainConfig;
import org.gcaldaemon.gui.editors.AccountEditor;
import org.gcaldaemon.gui.editors.LDAPEditor;

/**
 * Abstract page for config editor.
 * 
 * Created: Apr 16, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public abstract class AbstractPage extends JPanel {

	// --- CONSTANTS ---

	protected static final Dimension SMALL_SPACE = new Dimension(5, 5);
	protected static final Dimension BIG_SPACE = new Dimension(10, 10);

	// --- GUI ---

	protected final JPanel editorPanel;
	protected final BoxLayout layout;

	// --- POINTERS ---

	protected final MainConfig config;
	protected final ConfigEditor editor;

	// --- CONSTRUCTOR ---

	public AbstractPage(MainConfig config, ConfigEditor editor)
			throws Exception {
		this.config = config;
		this.editor = editor;
		setLayout(new BorderLayout());
		editorPanel = new JPanel();
		layout = new BoxLayout(editorPanel, BoxLayout.Y_AXIS);
		editorPanel.setLayout(layout);
		add(editorPanel, BorderLayout.NORTH);
		setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));
	}

	protected final void addEditor(JComponent component) {
		editorPanel.add(component);
		editorPanel.add(Box.createRigidArea(SMALL_SPACE));
	}

	// --- ACCOUNT LIST CHANGED ---

	public void updateAccountEditors() {
		int pageCount = editorPanel.getComponentCount();
		Component component;
		for (int i = 0; i < pageCount; i++) {
			component = editorPanel.getComponent(i);
			if (component != null) {
				if (component instanceof AccountEditor) {
					((AccountEditor) component).updateAccount();
					continue;
				}
				if (component instanceof LDAPEditor) {
					((LDAPEditor) component).updateAccount();
					continue;
				}
			}
		}
	}

	// --- CHECKBOX CHANGED ---

	public void setChecked(boolean checked) {
	}

}
