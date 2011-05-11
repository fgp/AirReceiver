package org.phlo.AirReceiver;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
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

public class AirReceiver {
	private static final Logger s_logger = Logger.getLogger(RtspUnsupportedResponseHandler.class.getName());

	private static final String AirtunesServiceNamePrefix = "00DEADBEEF00@";
	private static final String AirtunesServiceType = "_raop._tcp.local.";
	private static final short AirtunesServiceRTSPPort = 5000;
	private static final Map<String, String> AirtunesServiceProperties = map(
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
	
	public static final ExecutorService ExecutorService = Executors.newCachedThreadPool();
	
	public static final ChannelHandler CloseOnShutdownHandler = new SimpleChannelUpstreamHandler() {
	    @Override
	    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
	    	s_allChannels.add(e.getChannel());
	    	super.channelOpen(ctx, e);
	    }
	};
	
	private static final List<JmDNS> s_jmDNSInstances = new java.util.LinkedList<JmDNS>();
	private static ChannelGroup s_allChannels = new DefaultChannelGroup();
		
	private static Map<String, String> map(String... keys_values) {
		assert keys_values.length % 2 == 0;
		Map<String, String> map = new java.util.HashMap<String, String>(keys_values.length / 2);
		for(int i=0; i < keys_values.length; i+=2)
			map.put(keys_values[i], keys_values[i+1]);
		return Collections.unmodifiableMap(map);
	}
	
	public static void onShutdown() {
		ChannelGroupFuture allChannelsClosed = s_allChannels.close();
		
		for(JmDNS jmDNS: s_jmDNSInstances) {
			try {
				jmDNS.unregisterAllServices();
				s_logger.info("Unregistered all services on " + jmDNS.getInterface());
			}
			catch (IOException e) {
				s_logger.info("Failed to unregister some services");
			}
		}
		
		allChannelsClosed.awaitUninterruptibly();
	}
	
    public static void main(String[] args) throws Exception {
    	Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				onShutdown();
			}
    	}));
    	
		final InputStream loggingPropertiesStream =
			AirReceiver.class.getClassLoader().getResourceAsStream("logging.properties");
    	LogManager.getLogManager().readConfiguration(loggingPropertiesStream);
    	
    	/* Register BouncyCaster security provider */
    	java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    	
    	/* Create tray icon */
    	final URL trayIconUrl = AirReceiver.class.getClassLoader().getResource("icon_32.png");
        final TrayIcon trayIcon = new TrayIcon((new ImageIcon(trayIconUrl, "AirReceiver").getImage()));
        trayIcon.setToolTip("AirReceiver");
        trayIcon.setImageAutoSize(true);
        PopupMenu popupMenu = new PopupMenu();
        MenuItem exitMenuItem = new MenuItem("Quit");
        exitMenuItem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent evt) {
				onShutdown();
				System.exit(0);
			}
        });
        popupMenu.add(exitMenuItem);
        trayIcon.setPopupMenu(popupMenu);
        SystemTray.getSystemTray().add(trayIcon);
    	
        /* Get host name */
        final String hostName = InetAddress.getLocalHost().getHostName().split("\\.")[0];
        
        /* Create AirTunes RTSP pipeline factory.
         * NOTE: We immediatly create a test channel. This isn't necessary,
         * but uncoveres failures earlier
         */
        ChannelPipelineFactory airTunesRtspPipelineFactory = new RaopRtspPipelineFactory();
        airTunesRtspPipelineFactory.getPipeline();
        
        /* Create AirTunes RTSP server */
		final ServerBootstrap airTunesRtspBootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(ExecutorService, ExecutorService));
		airTunesRtspBootstrap.setPipelineFactory(airTunesRtspPipelineFactory);
		airTunesRtspBootstrap.setOption("reuseAddress", true);
		airTunesRtspBootstrap.setOption("child.tcpNoDelay", true);
		airTunesRtspBootstrap.setOption("child.keepAlive", false);
		s_allChannels.add(airTunesRtspBootstrap.bind(new InetSocketAddress(Inet4Address.getByName("0.0.0.0"), AirtunesServiceRTSPPort)));
        s_logger.info("Launched RTSP service on port " + AirtunesServiceRTSPPort);
        
    	/* Create mDNS responders. Also arrange for all services
    	 * to be unregistered on VM shutdown
    	 */
    	for(NetworkInterface iface: Collections.list(NetworkInterface.getNetworkInterfaces())) {
    		if (iface.isLoopback())
    			continue;
    		if (iface.isPointToPoint())
    			continue;
    		if (!iface.isUp())
    			continue;

    		for(final InetAddress addr: Collections.list(iface.getInetAddresses())) {
    			if (!(addr instanceof Inet4Address))
    				continue;
    			
    			(new Thread(new Runnable() {
					@Override public void run() {
						try {
							/* Create mDNS responder for address */
					    	final JmDNS jmDNS = JmDNS.create(addr, hostName);
					    	s_jmDNSInstances.add(jmDNS);
	
					        /* Publish RAOP service */
					        final ServiceInfo airTunesServiceInfo = ServiceInfo.create(
					    		AirtunesServiceType,
					    		AirtunesServiceNamePrefix + hostName,
					    		AirtunesServiceRTSPPort,
					    		0 /* weight */, 0 /* priority */,
					    		AirtunesServiceProperties
					    	);
					        jmDNS.registerService(airTunesServiceInfo);
							s_logger.info("Registered AirTunes service '" + airTunesServiceInfo.getName() + "' on " + addr);
						}
						catch (Throwable e) {
							s_logger.log(Level.SEVERE, "Failed to publish service on " + addr, e);
						}
					}
				})).start();
    		}
    	}
    }
}
