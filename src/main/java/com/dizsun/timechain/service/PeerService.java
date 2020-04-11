package com.dizsun.timechain.service;

import com.alibaba.fastjson.JSON;
import com.dizsun.timechain.component.Peer;
import com.dizsun.timechain.constant.Config;
import com.dizsun.timechain.interfaces.ICheckDelay;
import com.dizsun.timechain.util.LogUtil;
import org.apache.log4j.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 管理peer节点的连接和移除及通信
 * 包装了节点间传输消息的具体方法和对节点的操作
 */
public class PeerService implements ICheckDelay {
    private String localHost;
    private ConcurrentHashMap<String, Peer> peersMap;
    private ArrayList<Peer> peers;
    private MessageHelper messageHelper;
    private Logger logger = Logger.getLogger(PeerService.class);

    private PeerService() {
    }

    private static class Holder {
        private final static PeerService peerService = new PeerService();
    }

    public static PeerService getInstance() {
        return Holder.peerService;
    }

    public void init(String localHost) {
        peersMap = new ConcurrentHashMap<>();
        messageHelper = MessageHelper.getInstance();
        peers = new ArrayList<>();
        this.localHost = localHost;
    }

    /**
     * 添加节点
     * @param webSocket
     * @return
     */
    public boolean addPeer(WebSocket webSocket) {
        String host = webSocket.getRemoteSocketAddress().getHostString();
//        localHost = webSocket.getLocalSocketAddress().getHostString();

        logger.info("host: " + host + ", localHost: " + localHost);

        if (contains(host) || host.equals(localHost)) {
            return false;
        }

        Peer p = new Peer();
        p.setWebSocket(webSocket);
        p.setIp(host);
        peersMap.put(host, p);
        peers.add(p);
        return true;
    }

    /**
     * 移除节点
     * @param webSocket
     */
    public void removePeer(WebSocket webSocket) {
        if (webSocket != null && webSocket.getRemoteSocketAddress() != null) {
            String hostString = webSocket.getRemoteSocketAddress().getHostString();
            Peer peer = peersMap.get(hostString);
            if (peer != null) {
                peersMap.remove(hostString);
                peers.remove(peer);
            }
        }
    }

    public void removePeer(String host) {
        Peer peer = peersMap.get(host);
        if (peer != null) {
            peersMap.remove(host);
            peers.remove(peer);
        }
    }

    /**
     * 向节点发送消息
     * @param webSocket 节点连接
     * @param msg 发送的消息
     */
    public void write(WebSocket webSocket, String msg) {
        if (webSocket != null && webSocket.isOpen())
            webSocket.send(msg);
    }

    public void write(String host, String msg) {
        Peer peer = peersMap.get(host);
        if (peer == null) return;
        WebSocket webSocket = peer.getWebSocket();
        if (webSocket == null || !webSocket.isOpen()) return;
        webSocket.send(msg);
    }

    /**
     * 向所有节点广播消息
     * @param msg
     */
    public void broadcast(String msg) {
        peers.forEach(v -> write(v.getWebSocket(), msg));
        logger.info(" broadcast complete!!!");
    }

    /**
     * 判断节点列表是否包含该节点
     * @param host
     * @return
     */
    public boolean contains(String host) {
        return peersMap.containsKey(host);
    }

    public boolean contains(WebSocket webSocket) {
        return contains(webSocket.getRemoteSocketAddress().getHostString());
    }

    /**
     * 连接节点peer
     *
     * @param host 输入的host格式示例: 192.168.1.1 或者http://192.168.1.1:6001
     */
    public void connectToPeer(String host) {
        if (isIP(host)) {
            if (contains(host) || host.equals(localHost))
                return;
            host = "http://" + host + ":6001";
        }
        try {
            final WebSocketClient socket = new WebSocketClient(new URI(host)) {
                @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    if (!addPeer(this)) {
                        this.close();
                    }
                    logger.info(localHost + " connects to " + getRemoteSocketAddress());
                    logger.info("currenct peers: " + JSON.toJSONString(getPeerArray()));
                    write(this, messageHelper.queryLatestBlock());
                    write(this, messageHelper.queryAllPeers());
                }

                @Override
                public void onMessage(String s) {
                    P2PService.getInstance().handleMsgThread(this, s);
                }

                @Override
                public void onClose(int i, String s, boolean b) {
                    logger.warn(localHost + " connects to " + getRemoteSocketAddress() + " closed");
                    removePeer(this);
                    logger.warn("remove " + getRemoteSocketAddress());
                    logger.warn("currenct peers: " + JSON.toJSONString(getPeerArray()));
                }

                @Override
                public void onError(Exception e) {
                    logger.error(localHost + " connection to " + getRemoteSocketAddress() + " error");
                    removePeer(this);
                }
            };
            socket.connect();
        } catch (URISyntaxException e) {
            logger.warn("p2p connect is error:" + e.getMessage());
        }

    }

    /**
     * 目前连接的节点数
     *
     * @return
     */
    public int length() {
        return peersMap.size();
    }

    /**
     * 获取节点IP列表
     *
     * @return
     */
    public Object[] getPeerArray() {
        String[] ps = new String[peers.size()];
        for (int i = 0; i < peers.size(); i++) {
            ps[i] = peers.get(i).getIp();
        }
        return ps;
    }

    public Object getCoPeerArray() {
        List<String> cps = new ArrayList<>();
        for (String host : peersMap.keySet()) {
            cps.add(host);
        }
        return cps;
    }

    /**
     * 判断是否是ip值
     *
     * @param addr
     * @return
     */
    public boolean isIP(String addr) {
        if (addr == null || addr.isEmpty() || addr.length() < 7 || addr.length() > 15) {
            return false;
        }
        /**
         * 判断IP格式和范围
         */
        String rexp = "([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}";
        Pattern pat = Pattern.compile(rexp);
        Matcher mat = pat.matcher(addr);
        return mat.find();
    }

    /**
     * 更新稳定指数
     *
     * @param webSocket 目标节点
     * @param stability 要增加的指数值
     */
    public void updateSI(WebSocket webSocket, int stability) {
        String hostString = webSocket.getRemoteSocketAddress().getHostString();
        Peer peer = peersMap.get(hostString);
        if (peer == null) return;
        peer.addStability(stability);
    }

    /**
     * 对SI表进行规整化，即所有SI值减去最小值并排序
     */
    public void regularizeSI() {
        if (peers.size() == 0) return;
        peers.sort((o1, o2) -> o2.getStability() - o1.getStability());
        if (peers.get(0).getStability() >= (Integer.MAX_VALUE >>> 2)) {
            for (Peer peer : peers) {
                peer.setStability(peer.getStability() / 2);
            }
        }
    }

    /**
     * 更新与各节点之间的延迟
     */
//    public void updateDelay() {
//        peersMap.forEach((k, v) -> {
//            new DelayHandler(this, v).start();
//        });
//    }


    @Override
    public void checkDelay(Peer peer, double delay) {
        Peer p1 = peersMap.get(peer.getIp());
        if (p1 == null) return;
        p1.setDelay(delay);
    }

    /**
     * 延时测试机，向对方发送一个消息并等待回应，计算延迟
     */
//    private class DelayHandler extends Thread {
//        private ICheckDelay context;
//        private Peer peer;
//        private long t1;
//        private long t2;
//        private Double delay;
//
//        public DelayHandler(ICheckDelay context, Peer peer) {
//            this.context = context;
//            this.peer = peer;
//        }
//
//        @Override
//        public void run() {
//            try {
//                Socket client = new Socket(InetAddress.getByName(peer.getIp()), Config.getInstance().getNtpListenPort());
//                DataOutputStream dos = new DataOutputStream(client.getOutputStream());
//                DataInputStream dis = new DataInputStream(client.getInputStream());
//                dos.writeBoolean(true);
//                t1 = System.nanoTime();
//                dos.flush();
//                if (dis.readBoolean()) {
//                    t2 = System.nanoTime();
//                }
//                delay = (t2 - t1) / 2.0;
//                context.checkDelay(peer, delay);
//                LogUtil.writeLog(peer.getIp() + ":" + delay.intValue(), LogUtil.NTP);
//                dis.close();
//                dos.close();
//                client.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//        }
//    }
}

