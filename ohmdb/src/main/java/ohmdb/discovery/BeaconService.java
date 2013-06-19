/*
 * Copyright (C) 2013  Ohm Data
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ohmdb.discovery;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import ohmdb.OhmServer;
import ohmdb.OhmService;
import ohmdb.codec.UdpProtobufDecoder;
import ohmdb.codec.UdpProtobufEncoder;
import ohmdb.util.FiberOnly;
import org.jetlang.channels.MemoryChannel;
import org.jetlang.channels.MemoryRequestChannel;
import org.jetlang.channels.Request;
import org.jetlang.channels.RequestChannel;
import org.jetlang.core.Callback;
import org.jetlang.fibers.Fiber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static ohmdb.discovery.Beacon.Availability;

public class BeaconService extends AbstractService implements OhmService {
    private static final Logger LOG = LoggerFactory.getLogger(BeaconService.class);

    @Override
    public String getServiceName() {
        return "BeaconService";
    }

    @Override
    public boolean hasPort() {
        return true;
    }

    @Override
    public int port() {
        return discoveryPort;
    }

    public static class NodeInfoRequest {
        public final long nodeId;
        public final String serviceName;

        public NodeInfoRequest(long nodeId, String serviceName) {
            this.nodeId = nodeId;
            this.serviceName = serviceName;
        }

        @Override
        public String toString() {
            return "NodeInfoRequest{" +
                    "nodeId=" + nodeId +
                    ", serviceName='" + serviceName + '\'' +
                    '}';
        }
    }
    public static class NodeInfoReply {
        /**
         * Was the node/service information found?
         */
        public final boolean found;
        public final List<String> addresses;
        public final int port;

        public NodeInfoReply(boolean found, List<String> addresses, int port) {
            this.found = found;
            this.addresses = addresses;
            this.port = port;
        }

        @Override
        public String toString() {
            return "NodeInfoReply{" +
                    "found=" + found +
                    ", addresses=" + addresses +
                    ", port=" + port +
                    '}';
        }
        public final static NodeInfoReply NO_REPLY = new NodeInfoReply(false, null, 0);
    }
    private final RequestChannel<NodeInfoRequest, NodeInfoReply> nodeInfoRequests = new MemoryRequestChannel<>();
    public RequestChannel<NodeInfoRequest, NodeInfoReply> getNodeInfo() {
        return nodeInfoRequests;
    }
    @FiberOnly
    private void handleNodeInfoRequest(Request<NodeInfoRequest, NodeInfoReply> message) {
        NodeInfoRequest req = message.getRequest();
        NodeInfo peer = peers.get(req.nodeId);
        if (peer == null) {
            message.reply(NodeInfoReply.NO_REPLY);
            return;
        }

        Integer servicePort = peer.services.get(req.serviceName);
        if (servicePort == null) {
            message.reply(NodeInfoReply.NO_REPLY);
            return;
        }


        List<String> peerAddrs = peer.availability.getAddressesList();
        // does this service run on that peer?
        message.reply(new NodeInfoReply(true, peerAddrs, servicePort));
    }

    /**
     * Information about a node.
     */
    public static class NodeInfo {
        public final Availability availability;
        public final long lastContactTime;
        public final ImmutableMap<String, Integer> services;

        public NodeInfo(Availability availability, long lastContactTime) {
            this.availability = availability;
            this.lastContactTime = lastContactTime;
            ImmutableMap.Builder b = ImmutableMap.builder();
            for (Beacon.ServiceDescriptor serviceDescriptor : availability.getServicesList()) {
                b.put(serviceDescriptor.getServiceName(), serviceDescriptor.getServicePort());
            }
            services = b.build();
        }

        public NodeInfo(Availability availability) {
            this(availability, System.currentTimeMillis());
        }

        @Override
        public String toString() {
            return availability + " last contact: " + lastContactTime;
        }
    }

    @Override
    public String toString() {
        return "BeaconService{" +
                "discoveryPort=" + discoveryPort +
                ", nodeId=" + nodeId +
                '}';
    }

    // For main system services/pubsub stuff.
    private final OhmServer ohmServer;
    private final long nodeId;
    private final int discoveryPort;
    private final NioEventLoopGroup eventLoop;
    private final Map<String, Integer> serviceInfo = new HashMap<>();
    private final Map<Long, NodeInfo> peers = new HashMap<>();
    private final org.jetlang.channels.Channel<Availability> incomingMessages = new MemoryChannel<>();
    private final Fiber fiber;

    // These should be final, but they are initialized in doStart().
    private Channel broadcastChannel = null;
    private InetSocketAddress sendAddress = null;
    private Bootstrap bootstrap = null;
    private List<String> localIPs;

    public class BeaconMessageHandler extends SimpleChannelInboundHandler<Availability> {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            LOG.warn("Exception, ignoring datagram", cause);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Availability msg) throws Exception {
            incomingMessages.publish(msg);
        }
    }

    /**
     *
     * @param nodeId the id of this node.
     * @param discoveryPort the port to send discovery beacons on and to listen to
     * @throws InterruptedException
     * @throws SocketException
     */
    public BeaconService(long nodeId, int discoveryPort,
                         final Fiber fiber,
                         NioEventLoopGroup eventLoop,
                         Map<String, Integer> services,
                         OhmServer theOhmServer
                         ) throws InterruptedException, SocketException {
        this.discoveryPort = discoveryPort;
        this.nodeId = nodeId;
        this.fiber = fiber;
        serviceInfo.putAll(services);
        this.ohmServer = theOhmServer;
        this.eventLoop = eventLoop;
    }

    public ListenableFuture<ImmutableMap<Long, NodeInfo>> getState() {
        final SettableFuture<ImmutableMap<Long,NodeInfo>> future = SettableFuture.create();

        fiber.execute(new Runnable() {
            @Override
            public void run() {
                future.set(getCopyOfState());
            }
        });

        return future;
    }

    private ImmutableMap<Long, NodeInfo> getCopyOfState() {
        return ImmutableMap.copyOf(peers);
    }

    @FiberOnly
    private void sendBeacon() {
        if (broadcastChannel == null) {
            LOG.debug("Channel not available yet, deferring beacon send");
            return;
        }
        LOG.debug("Sending beacon broadcast message to {}", sendAddress);
        // Build beacon message:
        Availability.Builder beaconMessage = Availability.newBuilder()
                .addAllAddresses(localIPs)
                .setNodeId(nodeId);

        List<Beacon.ServiceDescriptor> msgServices = new ArrayList<>(serviceInfo.size());
        for (String svcName : serviceInfo.keySet()) {
            msgServices.add(Beacon.ServiceDescriptor.newBuilder()
            .setServiceName(svcName)
            .setServicePort(serviceInfo.get(svcName))
            .build());
        }

        beaconMessage.addAllServices(msgServices);

        broadcastChannel.writeAndFlush(new UdpProtobufEncoder.UdpProtobufMessage(sendAddress, beaconMessage));
    }

    @FiberOnly
    private void processWireMessage(Availability message) {
        LOG.debug("Got incoming message {}", message);
        if (!message.hasNodeId()) {
            LOG.error("Incoming availability message does not have node id, ignoring!");
            return;
        }
        // Always just overwrite what was already there for now.
        // TODO consider a more sophisticated merge strategy?
        peers.put(message.getNodeId(), new NodeInfo(message));
    }

    @FiberOnly
    private void serviceChange(OhmServer.ServiceStateChange message) {
        if (message.state == State.RUNNING) {
            LOG.debug("BeaconService adding running service {} on port {}", message.service.getServiceName(), message.service.port());
            serviceInfo.put(message.service.getServiceName(), message.service.port());
        } else if (message.state == State.STOPPING || message.state == State.FAILED || message.state == State.TERMINATED) {
            LOG.debug("BeaconService removed service {} on port {} with state {}", message.service.getServiceName(), message.service.port(),
                    message.state);
            serviceInfo.remove(message.service.getServiceName());
        } else {
            LOG.debug("BeaconService got unknown service change {}", message);
        }
    }

    @Override
    protected void doStart() {
        eventLoop.next().execute(new Runnable() {
            @Override
            public void run() {
                bootstrap = new Bootstrap();
                try {
                    bootstrap.group(eventLoop)
                            .channel(NioDatagramChannel.class)
                            .option(ChannelOption.SO_BROADCAST, true)
                            .option(ChannelOption.SO_REUSEADDR, true)
                            .handler(new ChannelInitializer<DatagramChannel>() {
                                @Override
                                protected void initChannel(DatagramChannel ch) throws Exception {
                                    ChannelPipeline p = ch.pipeline();

                                    p.addLast("protobufDecoder", new UdpProtobufDecoder(Availability.getDefaultInstance()));

                                    p.addLast("protobufEncoder", new UdpProtobufEncoder());

                                    p.addLast("beaconMessageHandler", new BeaconMessageHandler());
                                }
                            });
                    // Wait, this is why we are in a new executor...
                    bootstrap.bind(discoveryPort).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            broadcastChannel = future.channel();
                        }
                    });
                    sendAddress = new InetSocketAddress("255.255.255.255", discoveryPort);
                    //Availability.Builder msgBuilder = Availability.newBuilder(nodeInfoFragment);
                    localIPs = getLocalIPs();
                    //msgBuilder.addAllAddresses(getLocalIPs());
                    //beaconMessage = msgBuilder.build();

                    // Schedule fiber tasks and subscriptions.
                    incomingMessages.subscribe(fiber, new Callback<Availability>() {
                        @Override
                        public void onMessage(Availability message) {
                            processWireMessage(message);
                        }
                    });
                    nodeInfoRequests.subscribe(fiber, new Callback<Request<NodeInfoRequest, NodeInfoReply>>() {
                        @Override
                        public void onMessage(Request<NodeInfoRequest, NodeInfoReply> message) {
                            handleNodeInfoRequest(message);
                        }
                    });

                    fiber.scheduleAtFixedRate(new Runnable() {
                        @Override
                        public void run() {
                            sendBeacon();
                        }
                    }, 2, 10, TimeUnit.SECONDS);

                    ohmServer.getServiceRegisteredChannel().subscribe(fiber, new Callback<OhmServer.ServiceStateChange>() {
                        @Override
                        public void onMessage(OhmServer.ServiceStateChange message) {
                            serviceChange(message);
                        }
                    });

                    fiber.start();

                    notifyStarted();
                } catch (Throwable t) {
                    if (fiber != null)
                        fiber.dispose();

                    notifyFailed(t);
                }
            }
        });
    }

    @Override
    protected void doStop() {
        eventLoop.next().execute(new Runnable() {
            @Override
            public void run() {
                fiber.dispose();

                notifyStopped();
            }
        });
    }

    private List<String> getLocalIPs() throws SocketException {
        List<String> ips = new LinkedList<>();
        for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements(); ) {
            NetworkInterface iface = interfaces.nextElement();
            for (Enumeration<InetAddress> addrs = iface.getInetAddresses(); addrs.hasMoreElements(); ) {
                InetAddress addr = addrs.nextElement();
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                    continue;
                }
                ips.add(addr.getHostAddress());
            }
        }
        return ips;
    }
}