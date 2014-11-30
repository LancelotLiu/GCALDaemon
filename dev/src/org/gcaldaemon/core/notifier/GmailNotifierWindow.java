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
package org.gcaldaemon.core.notifier;

import java.applet.Applet;
import java.applet.AudioClip;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Gmail notifier pop-up window.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
final class GmailNotifierWindow extends Window implements Runnable,
		MouseListener, MouseMotionListener {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = -4183774034603853544L;

	// --- LOGGER ---

	private static final Log log = LogFactory.getLog(GmailNotifierWindow.class);

	// --- LOCKS ---

	private static final Object WAIT_LOCK = new Object();
	private static final Object RENDER_LOCK = new Object();

	// --- DEFAULTS ---

	private static final Font BOLD = new Font("Dialog", Font.BOLD, 12);
	private static final Font PLAIN = new Font("Dialog", Font.PLAIN, 12);

	private final FontMetrics metrics;

	// --- VARIABLES ---

	private Color fromColor = Color.BLACK;
	private Color titleColor = Color.BLACK;
	private Color dateColor = Color.GRAY;

	private int lastX;
	private int offset;
	private String current;
	private boolean dragged;

	private String from;
	private String date;
	private String title;
	private String summary;
	private String count;

	private Image background;
	private Image buffer;
	private Graphics offscreen;
	private AudioClip clip;

	// --- CONSTRUCTOR ---

	GmailNotifierWindow(String style, String sound) throws Exception {
		super(new Frame());

		// Load background
		int w = 300;
		int h = 110;
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		InputStream in;
		try {
			if (style.indexOf('.') == -1) {
				in = GmailNotifierWindow.class.getResourceAsStream(style
						+ ".gif");
			} else {
				in = new FileInputStream(style);
			}
		} catch (Exception loadError) {
			log.error(loadError.getMessage(), loadError);
			in = GmailNotifierWindow.class.getResourceAsStream("default.gif");
		}
		ImageIO.setUseCache(false);
		background = ImageIO.read(in);
		in.close();
		Dimension size = toolkit.getScreenSize();
		setBounds((size.width - w) / 2, (size.height - h) / 2, w, h);
		metrics = getFontMetrics(PLAIN);

		// Set colors
		for (;;) {
			if (style.equals("metal")) {
				fromColor = Color.WHITE;
				break;
			}
			if (style.equals("green")) {
				titleColor = Color.GREEN;
				dateColor = new Color(0, 145, 0);
				break;
			}
			if (style.equals("blue")) {
				fromColor = Color.WHITE;
				titleColor = new Color(212, 235, 255);
				dateColor = titleColor;
				break;
			}
			if (style.equals("mail")) {
				titleColor = new Color(139, 128, 118);
				dateColor = titleColor;
				break;
			}
			break;
		}

		// Create offscreen buffer
		buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		offscreen = buffer.getGraphics();

		// Set notification sound
		if (sound == null || sound.equals("beep")) {
			clip = null;
		} else {
			try {
				URL url;
				if (sound.indexOf('.') == -1) {
					url = GmailNotifierWindow.class.getResource(sound + ".wav");
				} else {
					sound = sound.replace(File.separatorChar, '/');
					url = new URL("file", "", sound);
				}
				clip = Applet.newAudioClip(url);
			} catch (Exception soundError) {
				log.warn("Unable to load sound: " + sound, soundError);
				clip = null;
			}
		}

		// Init window
		setAlwaysOnTop(true);
		addMouseListener(this);
		addMouseMotionListener(this);
		validate();
	}

	// --- SHOW WINDOW ---

	private Thread repainter;
	private String[] mails;
	private int pointer;

	final void show(String[] newMails) {
		if (newMails != null && newMails.length != 0) {
			if (clip != null) {
				try {
					clip.play();
				} catch (Exception soundError) {
					log.error("Unable to play sound!", soundError);
				}
			} else {
				try {
					Toolkit.getDefaultToolkit().beep();
				} catch (Exception ignored) {
				}
			}
			synchronized (RENDER_LOCK) {
				pointer = -1;
				offset = 0;
				dragged = false;
				if (isVisible()) {
					String[] swap = new String[mails.length + newMails.length];
					System.arraycopy(mails, 0, swap, 0, mails.length);
					System.arraycopy(newMails, 0, swap, mails.length,
							newMails.length);
					Arrays.sort(swap, String.CASE_INSENSITIVE_ORDER);
					mails = swap;
					current = mails[0];
					render();
				} else {
					mails = newMails;
					current = mails[0];
					render();
					super.setVisible(true);
					if (repainter != null && repainter.isAlive()) {
						repainter.interrupt();
					}
					repainter = new Thread(this);
					repainter.start();
				}
			}
		}
	}

	public final void setVisible(boolean visible) {
		if (!visible) {
			if (repainter != null && repainter.isAlive()) {
				repainter.interrupt();
				mails = null;
			}
			if (clip != null) {
				try {
					clip.stop();
				} catch (Exception ignored) {
				}
			}
		}
		super.setVisible(visible);
	}

	public final void run() {
		try {
			for (;;) {

				synchronized (RENDER_LOCK) {

					// Increase pointer
					if (mails != null && mails.length != 0) {
						pointer++;
						offset = 0;
						if (pointer >= mails.length) {
							pointer = 0;
						}

						// Create image
						current = mails[pointer];
						render();
					}
				}

				// Force repaint
				repaint(100);

				// Sleep
				synchronized (WAIT_LOCK) {
					WAIT_LOCK.wait(4000);
					while (dragged) {
						dragged = false;
						WAIT_LOCK.wait(4000);
					}
				}
			}
		} catch (InterruptedException interrupt) {
			return;
		} catch (Exception repaintError) {
			log.warn(repaintError.getMessage(), repaintError);
		}
	}

	private final void render() {
		if (current != null) {

			// Parse fields
			int s = current.indexOf('\t');
			date = current.substring(0, s);
			int e = current.indexOf('\t', s + 1);
			from = current.substring(s + 1, e);
			s = current.indexOf('\t', e + 1);
			title = current.substring(e + 1, s);
			summary = current.substring(s + 1);
			count = Integer.toString(mails.length) + '/' + (pointer + 1);
			int countLenght = metrics.stringWidth(count);

			// Draw image
			if (background != null) {

				// Draw background
				offscreen.drawImage(background, 0, 0, null);
			}
			if (from != null && date != null && title != null) {

				// Draw from field
				offscreen.setFont(BOLD);
				offscreen.setColor(fromColor);
				offscreen.drawString(from, 47, 18);

				// Draw title field
				offscreen.setFont(PLAIN);
				offscreen.setColor(titleColor);
				if (metrics.stringWidth(title) > 270) {
					String test;
					int len = title.length();
					for (int i = 0; i < len; i++) {
						test = title.substring(0, i) + "...";
						if (metrics.stringWidth(test) > 275) {
							title = test;
							break;
						}
					}
				}
				offscreen.drawString(title, 8, 55);

				// Draw summary
				int len = summary.length();
				if (offset > len - 1) {
					offset = len - 1;
				} else {
					if (offset < 0) {
						offset = 0;
					}
				}
				summary = summary.substring(offset);
				if (metrics.stringWidth(summary) > 270) {
					len = summary.length();
					String test;
					for (int i = 0; i < len; i++) {
						test = summary.substring(0, i) + "...";
						if (metrics.stringWidth(test) > 275) {
							summary = test;
							break;
						}
					}
				}
				offscreen.drawString(summary, 8, 77);

				// Draw date field
				offscreen.setColor(dateColor);
				offscreen.drawString(date, 8, 101);

				// Draw count field
				offscreen.drawString(count, 292 - countLenght, 101);
			}
		}
	}

	// --- PAINTING ---

	public final void update(Graphics g) {
		paint(g);
	}

	public final void paint(Graphics g) {
		if (offscreen != null) {
			synchronized (RENDER_LOCK) {
				g.drawImage(buffer, 0, 0, null);
			}
		}
	}

	// --- CLICK AND ESCAPE LISTENER ---

	public final void mouseClicked(MouseEvent evt) {
		if (evt.getY() < 30 && evt.getX() > 270) {
			setVisible(false);
			offset = 0;
			dragged = false;
		} else {
			synchronized (WAIT_LOCK) {
				WAIT_LOCK.notifyAll();
			}
		}
	}

	public final void mousePressed(MouseEvent evt) {
		lastX = evt.getX();
	}

	public final void mouseDragged(MouseEvent evt) {
		synchronized (RENDER_LOCK) {
			if (current != null) {
				int x = evt.getX();
				if (Math.abs(lastX - x) > 50) {
					lastX = x;
					return;
				}
				dragged = true;
				int d = 0;
				if (lastX > x) {
					d = 1;
				} else {
					if (lastX < x) {
						d = -1;
					}
				}
				if (Math.abs(lastX - x) > 5) {
					lastX = x;
					int newOffset = offset + d;
					if (newOffset < 0) {
						newOffset = 0;
					}
					if (offset != newOffset) {
						offset = newOffset;
						render();
						repaint(100);
					}
				}
			}
		}
	}

	public final void mouseReleased(MouseEvent evt) {
	}

	public final void mouseEntered(MouseEvent evt) {
	}

	public final void mouseExited(MouseEvent evt) {
	}

	public void mouseMoved(MouseEvent evt) {
	}

}
