/*
 * This file is part of AirReceiver.
 *
 * AirReceiver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * AirReceiver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with AirReceiver.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.phlo.AirReceiver;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

import javax.jmdns.*;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.*;
import org.jboss.netty.channel.socket.nio.*;
import org.jboss.netty.handler.execution.*;

public class AirReceiver {
	/* Load java.util.logging configuration */
	static {
		final InputStream loggingPropertiesStream =
			AirReceiver.class.getClassLoader().getResourceAsStream("logging.properties");
    	try {
			LogManager.getLogManager().readConfiguration(loggingPropertiesStream);
		}
    	catch (final IOException e) {
    		throw new RuntimeException(e.getMessage(), e);
		}
	}

	private static final Logger s_logger = Logger.getLogger(AirReceiver.class.getName());

	/**
	 * The hardware (MAC) address of the emulated Airport Express
	 */
	public static final byte[] HardwareAddressBytes = getHardwareAddress();

	/**
	 * The hardware (MAC) address as a hexadecimal string
	 */
	public static final String HardwareAddressString = toHexString(HardwareAddressBytes);

	/**
	 * The hostname of the emulated Airport Express
	 */
	public static final String HostName = getHostName();

	/**
	 * The AirTunes/RAOP service type
	 */
	public static final String AirtunesServiceType = "_raop._tcp.local.";

	/**
	 * The AirTunes/RAOP RTSP port
	 */
	public static final short AirtunesServiceRTSPPort = 5000;

	/**
	 * The AirTunes/RAOP M-DNS service properties (TXT record)
	 */
	public static final Map<String, String> AirtunesServiceProperties = map(
		"txtvers", "1",
		"tp", "UDP",
		"ch", "2",
		"ss", "16",
		"sr", "44100",
		"pw", "false",
		"sm", "false",
		"sv", "false",
		"ek", "1",
		"et", "0,1",
		"cn", "0,1",
		"vn", "3"
	);

	/**
	 * Global executor service. Used e.g. to initialize the various netty channel factories 
	 */
	public static final ExecutorService ExecutorService = Executors.newCachedThreadPool();

	/**
	 * Channel execution handler. Spreads channel message handling over multiple threads
	 */
	public static final ExecutionHandler ChannelExecutionHandler = new ExecutionHandler(
		new OrderedMemoryAwareThreadPoolExecutor(4, 0, 0)
	);

	/**
	 * Message dispayed in the "About" dialog
	 */
	private static final String AboutMessage =
		"    AirReceiver\n" +
		"Copyright (c) 2011 Florian G. Pflug\n" +
		"\n" +
		"AirReceiver is free software: you can redistribute it and/or modify\n" +
		"it under the terms of the GNU General Public License as published by\n" +
		"the Free Software Foundation, either version 3 of the License, or\n" +
		"(at your option) any later version.\n" +
		"\n" +
		"AirReceiver is distributed in the hope that it will be useful,\n" +
		"but WITHOUT ANY WARRANTY; without even the implied warranty of\n" +
		"MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the\n" +
		"GNU General Public License for more details.\n" +
		"\n" +
		"You should have received a copy of the GNU General Public License\n" +
		"along with AirReceiver.  If not, see <http://www.gnu.org/licenses/>." +
		"\n\n" +
		"    Java ALAC Decoder\n" +
		"Copyright (c) 2011 Peter McQuillan";

	/**
	 * JmDNS instances (one per IP address). Used to unregister the mDNS services
	 * during shutdown.
	 */
	private static final List<JmDNS> s_jmDNSInstances = new java.util.LinkedList<JmDNS>();

	/**
	 * All open RTSP channels. Used to close all open challens during shutdown.
	 */
	private static ChannelGroup s_allChannels = new DefaultChannelGroup();

	/**
	 * Channel handle that registeres the channel to be closed on shutdown
	 */
	public static final ChannelHandler CloseChannelOnShutdownHandler = new SimpleChannelUpstreamHandler() {
		@Override
		public void channelOpen(final ChannelHandlerContext ctx, final ChannelStateEvent e) throws Exception {
			s_allChannels.add(e.getChannel());
			super.channelOpen(ctx, e);
		}
	};
		
	/**
	 * Map factory. Creates a Map from a list of keys and values
	 * 
	 * @param keys_values key1, value1, key2, value2, ...
	 * @return a map mapping key1 to value1, key2 to value2, ...
	 */
	private static Map<String, String> map(final String... keys_values) {
		assert keys_values.length % 2 == 0;
		final Map<String, String> map = new java.util.HashMap<String, String>(keys_values.length / 2);
		for(int i=0; i < keys_values.length; i+=2)
			map.put(keys_values[i], keys_values[i+1]);
		return Collections.unmodifiableMap(map);
	}

	/**
	 * Decides whether or nor a given MAC address is the address of some
	 * virtual interface, like e.g. VMware's host-only interface (server-side).
	 * 
	 * @param addr a MAC address
	 * @return true if the MAC address is unsuitable as the device's hardware address
	 */
	public static boolean isBlockedHardwareAddress(final byte[] addr) {
		if ((addr[0] & 0x02) != 0)
			/* Locally administered */
			return true;
		else if ((addr[0] == 0x00) && (addr[1] == 0x50) && (addr[2] == 0x56))
			/* VMware */
			return true;
		else if ((addr[0] == 0x00) && (addr[1] == 0x1C) && (addr[2] == 0x42))
			/* Parallels */
			return true;
		else if ((addr[0] == 0x00) && (addr[1] == 0x25) && (addr[2] == (byte)0xAE))
			/* Microsoft */
			return true;
		else
			return false;
	}

	/**
	 * Returns a suitable hardware address.
	 * 
	 * @return a MAC address
	 */
	private static byte[] getHardwareAddress() {
		try {
			/* Search network interfaces for an interface with a valid, non-blocked hardware address */
	    	for(final NetworkInterface iface: Collections.list(NetworkInterface.getNetworkInterfaces())) {
	    		if (iface.isLoopback())
	    			continue;
	    		if (iface.isPointToPoint())
	    			continue;

	    		try {
		    		final byte[] ifaceMacAddress = iface.getHardwareAddress();
		    		if ((ifaceMacAddress != null) && (ifaceMacAddress.length == 6) && !isBlockedHardwareAddress(ifaceMacAddress)) {
		    			s_logger.info("Hardware address is " + toHexString(ifaceMacAddress) + " (" + iface.getDisplayName() + ")");
		    	    	return Arrays.copyOfRange(ifaceMacAddress, 0, 6);
		    		}
	    		}
	    		catch (final Throwable e) {
	    			/* Ignore */
	    		}
	    	}
		}
		catch (final Throwable e) {
			/* Ignore */
		}

		/* Fallback to the IP address padded to 6 bytes */
		try {
			final byte[] hostAddress = Arrays.copyOfRange(InetAddress.getLocalHost().getAddress(), 0, 6);
			s_logger.info("Hardware address is " + toHexString(hostAddress) + " (IP address)");
			return hostAddress;
		}
		catch (final Throwable e) {
			/* Ignore */
		}

		/* Fallback to a constant */
		s_logger.info("Hardware address is 00DEADBEEF00 (last resort)");
		return new byte[] {(byte)0x00, (byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF, (byte)0x00};
	}

	/**
	 * Returns the machine's host name
	 * 
	 * @return the host name
	 */
	private static String getHostName() {
		try {
			return InetAddress.getLocalHost().getHostName().split("\\.")[0];
		}
		catch (final Throwable e) {
			return "AirReceiver";
		}
	}

	/**
	 * Converts an array of bytes to a hexadecimal string
	 * 
	 * @param bytes array of bytes
	 * @return hexadecimal representation
	 */
	private static String toHexString(final byte[] bytes) {
		final StringBuilder s = new StringBuilder();
		for(final byte b: bytes) {
			final String h = Integer.toHexString(0x100 | b);
			s.append(h.substring(h.length() - 2, h.length()).toUpperCase());
		}
		return s.toString();
	}

	/**
	 * Shuts the AirReceiver down gracefully
	 */
	public static void onShutdown() {
		/* Close channels */
		final ChannelGroupFuture allChannelsClosed = s_allChannels.close();

		/* Stop all mDNS responders */
		synchronized(s_jmDNSInstances) {
			for(final JmDNS jmDNS: s_jmDNSInstances) {
				try {
					jmDNS.unregisterAllServices();
					s_logger.info("Unregistered all services on " + jmDNS.getInterface());
				}
				catch (final IOException e) {
					s_logger.info("Failed to unregister some services");
				}
			}
		}

		/* Wait for all channels to finish closing */
		allChannelsClosed.awaitUninterruptibly();
		
		/* Stop the ExecutorService */
		ExecutorService.shutdown();

		/* Release the OrderedMemoryAwareThreadPoolExecutor */
		ChannelExecutionHandler.releaseExternalResources();
	}

    public static void main(final String[] args) throws Exception {
    	/* Make sure AirReceiver shuts down gracefully */
    	Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				onShutdown();
			}
    	}));

    	/* Create about dialog */
    	final Dialog aboutDialog = new Dialog((Dialog)null);
    	final GridBagLayout aboutLayout = new GridBagLayout();
    	aboutDialog.setLayout(aboutLayout);
    	aboutDialog.setVisible(false);
    	aboutDialog.setTitle("About AirReceiver");
    	aboutDialog.setResizable(false);
    	{
    		/* Message */
    		final TextArea title = new TextArea(AboutMessage.split("\n").length + 1, 64);
    		title.setText(AboutMessage);
    		title.setEditable(false);
	    	final GridBagConstraints titleConstraints = new GridBagConstraints();
	    	titleConstraints.gridx = 1;
	    	titleConstraints.gridy = 1;
	    	titleConstraints.fill = GridBagConstraints.HORIZONTAL;
	    	titleConstraints.insets = new Insets(0,0,0,0);
	    	aboutLayout.setConstraints(title, titleConstraints);
	    	aboutDialog.add(title);
    	}
    	{
    		/* Done button */
	    	final Button aboutDoneButton = new Button("Done");
	    	aboutDoneButton.addActionListener(new ActionListener() {
				@Override public void actionPerformed(final ActionEvent evt) {
					aboutDialog.setVisible(false);
				}
	    	});
	    	final GridBagConstraints aboutDoneConstraints = new GridBagConstraints();
	    	aboutDoneConstraints.gridx = 1;
	    	aboutDoneConstraints.gridy = 2;
	    	aboutDoneConstraints.anchor = GridBagConstraints.PAGE_END;
	    	aboutDoneConstraints.fill = GridBagConstraints.NONE;
	    	aboutDoneConstraints.insets = new Insets(0,0,0,0);
	    	aboutLayout.setConstraints(aboutDoneButton, aboutDoneConstraints);
	    	aboutDialog.add(aboutDoneButton);
    	}
    	aboutDialog.setVisible(false);
    	aboutDialog.setLocationByPlatform(true);
    	aboutDialog.pack();

    	/* Create tray icon */
		final URL trayIconUrl = AirReceiver.class.getClassLoader().getResource("icon_32.png");
		final TrayIcon trayIcon = new TrayIcon((new ImageIcon(trayIconUrl, "AirReceiver").getImage()));
		trayIcon.setToolTip("AirReceiver");
		trayIcon.setImageAutoSize(true);
		final PopupMenu popupMenu = new PopupMenu();
		final MenuItem aboutMenuItem = new MenuItem("About");
		aboutMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent evt) {
		    	aboutDialog.setLocationByPlatform(true);
		    	aboutDialog.setVisible(true);
			}
		});
		popupMenu.add(aboutMenuItem);
		final MenuItem exitMenuItem = new MenuItem("Quit");
		exitMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent evt) {
				onShutdown();
				System.exit(0);
			}
		});
		popupMenu.add(exitMenuItem);
		trayIcon.setPopupMenu(popupMenu);
		SystemTray.getSystemTray().add(trayIcon);

        /* Create AirTunes RTSP server */
		final ServerBootstrap airTunesRtspBootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(ExecutorService, ExecutorService));
		airTunesRtspBootstrap.setPipelineFactory(new RaopRtspPipelineFactory());
		airTunesRtspBootstrap.setOption("reuseAddress", true);
		airTunesRtspBootstrap.setOption("child.tcpNoDelay", true);
		airTunesRtspBootstrap.setOption("child.keepAlive", true);
		s_allChannels.add(airTunesRtspBootstrap.bind(new InetSocketAddress(Inet4Address.getByName("0.0.0.0"), AirtunesServiceRTSPPort)));
        s_logger.info("Launched RTSP service on port " + AirtunesServiceRTSPPort);

    	/* Create mDNS responders. */
        synchronized(s_jmDNSInstances) {
	    	for(final NetworkInterface iface: Collections.list(NetworkInterface.getNetworkInterfaces())) {
	    		if (iface.isLoopback())
	    			continue;
	    		if (iface.isPointToPoint())
	    			continue;
	    		if (!iface.isUp())
	    			continue;

	    		for(final InetAddress addr: Collections.list(iface.getInetAddresses())) {
	    			if (!(addr instanceof Inet4Address) && !(addr instanceof Inet6Address))
	    				continue;

					try {
						/* Create mDNS responder for address */
				    	final JmDNS jmDNS = JmDNS.create(addr, HostName + "-jmdns");
				    	s_jmDNSInstances.add(jmDNS);

				        /* Publish RAOP service */
				        final ServiceInfo airTunesServiceInfo = ServiceInfo.create(
				    		AirtunesServiceType,
				    		HardwareAddressString + "@" + HostName + " (" + addr.toString() + ")",
				    		AirtunesServiceRTSPPort,
				    		0 /* weight */, 0 /* priority */,
				    		AirtunesServiceProperties
				    	);
				        jmDNS.registerService(airTunesServiceInfo);
						s_logger.info("Registered AirTunes service '" + airTunesServiceInfo.getName() + "' on " + addr);
					}
					catch (final Throwable e) {
						s_logger.log(Level.SEVERE, "Failed to publish service on " + addr, e);
					}
	    		}
	    	}
        }
    }
}
