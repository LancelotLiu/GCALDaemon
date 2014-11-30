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

import java.applet.Applet;
import java.applet.AudioClip;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.net.URL;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.gcaldaemon.core.Configurator;
import org.gcaldaemon.core.notifier.GmailNotifier;
import org.gcaldaemon.gui.ConfigEditor;
import org.gcaldaemon.gui.Messages;
import org.gcaldaemon.gui.config.MainConfig;

/**
 * Sound property editor for the Gmail notifier.
 * 
 * Created: May 25, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class SoundEditor extends AbstractEditor implements ItemListener,
		ActionListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = 44511292942234178L;

	// --- GUI ---

	private final JPanel panel = new JPanel();
	private final JLabel label = new JLabel();
	private final JComboBox combo = new JComboBox();
	private final JButton browse = new JButton();
	private final JButton play = new JButton();

	// --- AUDIO CLIP ---

	private AudioClip clip;

	// --- CONSTRUCTOR ---

	public SoundEditor(MainConfig config, ConfigEditor editor) throws Exception {
		super(config, editor, Configurator.NOTIFIER_WINDOW_SOUND);
		combo.addItem("beep"); //$NON-NLS-1$
		combo.addItem("sound"); //$NON-NLS-1$
		combo.setEditable(true);
		combo.setSelectedItem(config.getConfigProperty(key, "beep")); //$NON-NLS-1$

		BorderLayout layout = new BorderLayout();
		layout.setVgap(5);
		setLayout(layout);

		BorderLayout borderLayout = new BorderLayout();
		borderLayout.setHgap(5);
		panel.setLayout(borderLayout);

		GridLayout panelLayout = new GridLayout(1, 2);
		panelLayout.setHgap(5);
		JPanel buttons = new JPanel(panelLayout);

		panel.add(label, BorderLayout.WEST);
		panel.add(combo, BorderLayout.CENTER);
		panel.add(buttons, BorderLayout.EAST);

		play.setIcon(editor.getIcon("play")); //$NON-NLS-1$
		browse.setIcon(editor.getIcon("open")); //$NON-NLS-1$
		browse.setToolTipText(Messages.getString("browse")); //$NON-NLS-1$

		buttons.add(play);
		buttons.add(browse);
		play.addActionListener(this);
		browse.addActionListener(this);

		add(panel, BorderLayout.NORTH);

		JLabel info = new JLabel();
		info.setText(Messages.getString("supported.sounds.info")); //$NON-NLS-1$
		add(info, BorderLayout.CENTER);

		combo.addItemListener(this);
		label.setText(Messages.getString(key) + ':');
		label.setPreferredSize(LABEL_SIZE);
	}

	// --- VALUE CHANGED ---

	public final void itemStateChanged(ItemEvent evt) {
		String value = (String) combo.getSelectedItem();
		value = value.replace('\\', '/');
		if (!value.equals(config.getConfigProperty(key, ""))) { //$NON-NLS-1$
			config.setConfigProperty(key, value);
			editor.status(key, value);
		}
	}

	public final void actionPerformed(ActionEvent evt) {
		String oldValue = (String) combo.getSelectedItem();
		if (evt.getSource() == play) {
			if (clip != null) {
				try {
					clip.stop();
				} catch (Exception ignored) {
				}
			}
			try {
				if (oldValue == null || oldValue.length() == 0) {
					return;
				}
				if (oldValue.equals("beep")) { //$NON-NLS-1$
					ConfigEditor.TOOLKIT.beep();
					combo.setForeground(Color.BLACK);
					return;
				}
				URL url;
				if (oldValue.indexOf('.') == -1) {
					url = GmailNotifier.class.getResource(oldValue + ".wav"); //$NON-NLS-1$
				} else {
					oldValue = oldValue.replace(File.separatorChar, '/');
					File file = new File(oldValue);
					if (!file.isFile()) {
						editor.info(key, Messages.getString("no.such.file")); //$NON-NLS-1$
						return;
					}
					url = new URL("file", "", oldValue); //$NON-NLS-1$ //$NON-NLS-2$
				}
				clip = Applet.newAudioClip(url);
				clip.play();
			} catch (Exception soundError) {
				log.warn("Unable to load sound: " + oldValue, soundError); //$NON-NLS-1$
				if (clip != null) {
					try {
						clip.stop();
					} catch (Exception ignored) {
					}
					clip = null;
				}
			}
			return;
		}
		String path = editor.selectFile(Messages.getString(key), oldValue);
		if (path != null) {
			path = path.replace('\\', '/');
			String test = path.toLowerCase();
			if (!test.endsWith(".au") && !test.endsWith(".mid") //$NON-NLS-1$ //$NON-NLS-2$
					&& !test.endsWith(".wav") //$NON-NLS-1$
					&& !test.endsWith(".rmf") //$NON-NLS-1$
					&& !test.endsWith(".aiff")) { //$NON-NLS-1$
				editor.info(Messages.getString(key), Messages
						.getString("unsupported.sound.file")); //$NON-NLS-1$
				return;
			}
			if (!path.equals(oldValue)) {
				config.setConfigProperty(key, path);
				editor.status(key, path);
				combo.setSelectedItem(path);
			}
		}
	}

}
