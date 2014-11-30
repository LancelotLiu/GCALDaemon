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
package org.gcaldaemon.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JTabbedPane;

/**
 * Decorated JTabbedPane.
 * 
 * Created: Feb 7, 2008 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public class DecoratedFolder extends JTabbedPane {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 3425120038460093100L;

	// --- PAINT RASTER ---

	protected final void paintChildren(Graphics g) {
		g.setColor(Color.LIGHT_GRAY);
		Dimension size = getSize();
		for (int y = 1; y < size.height; y += 2) {
			g.drawLine(0, y, size.width, y);
		}
		super.paintChildren(g);
	}

}
