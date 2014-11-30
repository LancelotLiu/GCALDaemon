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

import java.awt.BorderLayout;
import java.awt.Color;

import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.gcaldaemon.core.StringUtils;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.MainConfig;

/**
 * Time property editor.
 * 
 * Created: May 25, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class TimeEditor extends AbstractEditor implements ChangeListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 57201651357653142L;

	// --- DIVISORS ---

	public static final long SEC = 1000L;
	public static final long MIN = 60000L;
	public static final long DAY = 86400000L;

	// --- GUI ---

	private final JLabel label = new JLabel();
	private final JSlider slider = new JSlider();

	// --- PROPERTIES ---

	private final String unit;

	// --- CONSTRUCTOR ---

	public TimeEditor(MainConfig config, ConfigEditor editor, String key,
			String minValue, String defaultValue, String maxValue,
			long divisor, String unit) throws Exception {
		super(config, editor, key);
		this.unit = unit;
		setLayout(new BorderLayout());
		add(label, BorderLayout.NORTH);
		add(slider, BorderLayout.CENTER);
		long minLong = StringUtils.stringToLong(minValue);
		long valLong = StringUtils.stringToLong(defaultValue);
		long maxLong = StringUtils.stringToLong(maxValue);
		int min = (int) (minLong / divisor);
		int val = (int) (config.getConfigProperty(key, valLong) / divisor);
		int max = (int) (maxLong / divisor);
		slider.setMinimum(min);
		slider.setMaximum(max);
		slider.setValue(val);
		slider.setPaintLabels(false);
		slider.setPaintTicks(true);
		slider.setPaintTrack(true);
		slider.addChangeListener(this);
		showValue();
	}

	// --- VALUE CHANGED ---

	public final void stateChanged(ChangeEvent evt) {
		String value;
		if (unit.equals("%")) {
			value = slider.getValue() + "%";
		} else {
			value = slider.getValue() + " " + unit;
		}
		config.setConfigProperty(key, value);
		editor.status(key, value);
		showValue();
	}

	private final void showValue() {
		String unitName;
		if (unit.equals("%")) {
			unitName = " %";
		} else {
			unitName = Messages.getString(unit);
		}
		label.setText(Messages.getString(key) + " [" + slider.getValue() + ' '
				+ unitName + "]:");
	}

	// --- ENABLE / DISABLE ---

	public final void setEnabled(boolean enabled) {
		slider.setEnabled(enabled);
		if (enabled) {
			label.setForeground(Color.BLACK);
		} else {
			label.setForeground(Color.GRAY);
		}
	}

	// --- SET LABEL TEXT ---

	public final void setText(String text) {
		label.setText(text);
	}

}
