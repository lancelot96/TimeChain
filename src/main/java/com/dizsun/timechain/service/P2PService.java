package com.dizsun.timechain.service;


import com.alibaba.fastjson.JSON;
import com.dizsun.timechain.component.ACK;
import com.dizsun.timechain.component.Message;
import com.dizsun.timechain.constant.Config;
import com.dizsun.timechain.util.RSAUtil;
import com.dizsun.timechain.interfaces.ISubscriber;
import com.dizsun.timechain.constant.R;
import com.dizsun.timechain.constant.ViewState;
import org.apache.log4j.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * 包含了和其他节点之间通信的协议和对消息处理的业务逻辑
 */
public class P2PService implements ISubscriber {
    private BlockService blockService;
    private ExecutorService pool;   //线程池
    private RSAUtil rsaUtil;
    private PeerService peerService;
    private MessageHelper messageHelper;
    private Logger logger = Logger.getLogger(P2PService.class);
    private Config config = Config.getInstance();

//    private long startTime = 0;
//    private long endTime = 0;
    //view number
//    private int VN;
    //节点数3N+0,1,2
    private int N = 1;
    private int stabilityValue = 128;
    private ViewState viewState = ViewState.Running;
    private List<ACK> acks;

    private static class Holder {
        private static P2PService p2pService = new P2PService();
    }

    public static P2PService getInstance() {
        return Holder.p2pService;
    }

    private P2PService() {
    }

    public void initP2PServer(int port) {
        this.blockService = BlockService.getInstance();
        blockService.init();
        this.pool = Executors.newSingleThreadExecutor();
        this.acks = new ArrayList<>();
//        this.VN = 0;
        this.rsaUtil = RSAUtil.getInstance();
        rsaUtil.init(config.getLocalHost() + "." + config.getP2pPort());
        this.peerService = PeerService.getInstance();
        peerService.init(config.getLocalHost());
        this.messageHelper = MessageHelper.getInstance();
        messageHelper.init();
        final WebSocketServer socket = new WebSocketServer(new InetSocketAddress(port)) {
            public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
                peerService.write(webSocket, messageHelper.queryLatestBlock());
                String host = webSocket.getRemoteSocketAddress().getHostString();
                if (peerService.contains(host)) {
                    peerService.removePeer(host);
                }
                peerService.addPeer(webSocket);
                logger.info("current peers: " + JSON.toJSONString(peerService.getPeerArray()));
            }

            public void onClose(WebSocket webSocket, int i, String s, boolean b) {
                logger.warn("close code: " + i + ", additional info: " + s + ", closed by remote or not: " + b);
                logger.warn("connection failed to peer:" + webSocket.getRemoteSocketAddress());
                peerService.removePeer(webSocket);
                logger.warn("current peers: " + JSON.toJSONString(peerService.getPeerArray()));
            }

            public void onMessage(WebSocket webSocket, String s) {
                // TODO
                // using BlockingQueue to handle message

                pool.execute(new HandleMsgThread(webSocket, s));
            }

            public void onError(WebSocket webSocket, Exception e) {
                logger.error("connection error to peer:" + webSocket.getRemoteSocketAddress());
                peerService.removePeer(webSocket);
            }

            public void onStart() {

            }
        };
        socket.start();
        logger.info("listening websocket p2p port on: " + port);
    }

    /**
     * 相应peer的信息请求
     *
     * @param webSocket
     * @param s
     */
    private void handleMessage(WebSocket webSocket, String s) {
        try {
            Message message = JSON.parseObject(s, Message.class);
            if (message.getSourceIp().equals(config.getLocalHost())) return;
//            if (message.getViewNumber() < R.getViewNumber()) {
//                webSocket.send(messageHelper.responseAllBlocks());
//                logger.info("the message view number is less than us:" + message.getViewNumber() + "<" + R.getViewNumber());
//                logger.info("type:"+message.getType());
//                return;
//            } else if (message.getViewNumber() > R.getViewNumber() && message.getType() != R.RESPONSE_ALL_BLOCKS) {
//                R.setViewNumber(message.getViewNumber());
//                webSocket.send(messageHelper.queryAllBlock());
//                logger.info("the message view number is more than us:" + message.getViewNumber() + ">" + R.getViewNumber());
//                return;
//            }
            switch (message.getType()) {
                case R.QUERY_LATEST_BLOCK:
                    logger.info("a request for newest block");
                    peerService.write(webSocket, messageHelper.responseLatestBlock());
                    break;
                case R.QUERY_ALL_BLOCKS:
                    logger.info("a request for all blocks");
                    peerService.write(webSocket, messageHelper.responseAllBlocks());
                    break;
                case R.QUERY_ALL_PEERS:
                    logger.info("a request for newest peers list");
                    peerService.write(webSocket, messageHelper.responseAllPeers());
                    messageHelper.handlePeersResponse(message.getData());
                    break;
                case R.RESPONSE_ALL_BLOCKS:
                    logger.info("received blocks list");
                    messageHelper.handleBlockChain(webSocket, message.getData());
                    break;
                case R.RESPONSE_ALL_PEERS:
                    logger.info("received peers list");
                    messageHelper.handlePeersResponse(message.getData());
                    break;
                case R.REQUEST_NEGOTIATION:
                    /**
                     * 若对方同意开始公示,则广播ACK
                     */
                    logger.info("received a request for negotiation");
                    N = (peerService.length() + 1) / 3;
                    logger.info("the N is " + N);
                    if (viewState == ViewState.WaitingNegotiation) {
                        R.beginConsensus();
                        logger.info("broad ack");
                        peerService.broadcast(messageHelper.responseACK());
                        viewState = ViewState.WaitingACK;
                    }
                    break;
                case R.RESPONSE_ACK:
                    /**
                     * 收到对方的ACK后进行判断是否满足写区块条件
                     */
                    logger.info("received an ack");
                    ACK tempACK = new ACK(message.getData());
                    logger.info("checking the ack correctness:" + checkACK(tempACK));
                    if (viewState == ViewState.WaitingACK && checkACK(tempACK)) {
                        if (stabilityValue == 1) {
                            peerService.updateSI(webSocket, 1);
                        } else {
                            peerService.updateSI(webSocket, stabilityValue - 2);
                            stabilityValue -= 2;
                        }
                        acks.add(tempACK);
                        logger.info("the number of received ack: " + acks.size());
                        logger.info("Check if the conditions for writing a block are met：" + (acks.size() >= 2 && acks.size() >= 2 * N));
                        if (acks.size() >= 2 && acks.size() >= 2 * N) {
                            R.getBlockWriteLock().lock();
                            writeBlock(config.getLocalHost());
                            peerService.broadcast(messageHelper.responseLatestBlock());
                            viewState = ViewState.Running;
                            R.getBlockWriteLock().unlock();
                        }
                    }
                    break;
                case R.RESPONSE_BLOCK:
                    /**
                     * 收到对方新区块
                     */
                    switch (viewState) {
                        case Running:
                            logger.info(config.getLocalHost() + " is in running state! cannot receive new block!");
                            break;
                        case WritingBlock:
                        case WaitingACK:
                        case Negotiation:
                        case WaitingBlock:
                        case WaitingNegotiation:
                            if (messageHelper.filter(message)) {
                                peerService.broadcast(s);
                            }
                            R.getBlockWriteLock().lock();
                            stopWriteBlock();
                            messageHelper.handleBlock(webSocket, message.getData());
                            viewState = ViewState.Running;
                            R.getBlockWriteLock().unlock();
                            break;

                    }
                    break;

                case R.SYNC_BLOCK:
                    R.getBlockWriteLock().lock();
                    messageHelper.handleBlock(webSocket, message.getData());
                    R.getBlockWriteLock().unlock();
                    break;
            }
        } catch (Exception e) {
            logger.info("An error occurred while processing the message:" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 停止写虚区块,若是已经计算完毕则回滚
     */
    private void stopWriteBlock() {
        if (viewState == ViewState.WritingBlock) {
            viewState = ViewState.WaitingBlock;
        }
        if (blockService.getLatestBlock().getVN() == R.getViewNumber() + 1) {
            blockService.rollback();
        }
    }

    /**
     * 生成新区块
     */
    private void writeBlock(String localHost) {
        if (viewState == ViewState.Running) {
            return;
        }
        viewState = ViewState.WritingBlock;
        blockService.generateNextBlock(blockService.getJSONData(acks), localHost);
        logger.info("new block generated successfully");
    }

    /**
     * 处理消息的方法,开启处理线程
     * @param webSocket
     * @param msg
     */
    public void handleMsgThread(WebSocket webSocket, String msg) {
        Thread thread = new HandleMsgThread(webSocket, msg);
        pool.execute(thread);
    }

    /**
     * 检查ACK是否合法
     * @param ack
     * @return
     */
    private boolean checkACK(ACK ack) {
        if (ack.getVN() != R.getViewNumber()) {
            return false;
        }
        String sign = rsaUtil.getPubPriKey().decrypt(ack.getPublicKey(), ack.getSign());
        if (!sign.equals(ack.getPublicKey() + ack.getVN()))
            return false;
        return true;
    }

    /**
     * 当进入时间点tc时,开始共识
     */
    @Override
    public void doPerHour00() {
        logger.info("enter time 00,the view number is " + R.getViewNumber());
        switch (this.viewState) {
            case WaitingNegotiation:
                R.beginConsensus();
                N = (peerService.length() + 1) / 3;
                this.viewState = ViewState.WaitingACK;
                peerService.broadcast(messageHelper.requestNegotiation());
                break;
            default:
                break;
        }
    }

    /**
     * running期间进行同步节点列表
     */
    @Override
    public void doPerHour45() {
        logger.info("enter time 45,the view number is " + R.getViewNumber());
        peerService.broadcast(messageHelper.queryAllPeers());
        peerService.broadcast(messageHelper.syncBlock());
        logger.info("sync block finished！" + blockService.getLatestBlock().getIndex());
    }

    /**
     * 进入时间点tp时,准备共识
     */
    @Override
    public void doPerHour59() {
        N = (peerService.length() + 1) / 3;
        logger.info("enter time 59,the view number is " + R.getViewNumber() + " and the number of node is " + peerService.length());
        this.viewState = ViewState.WaitingNegotiation;
    }

    /**
     * 进入时间点te时,结束共识
     */
    @Override
    public void doPerHour01() {
        logger.info("enter time 01,the view number is " + R.getViewNumber());
        // 此处加锁是因为handleMessage中的RESPONSE_ACK中需要判定acks，因此和这里会相互影响
        R.getBlockWriteLock().lock();
        this.viewState = ViewState.Running;
        acks.clear();
        R.getBlockWriteLock().unlock();
        R.getAndIncrementViewNumber();
        stabilityValue = 128;
//        peerService.updateDelay();
        peerService.regularizeSI();
    }

    class HandleMsgThread extends Thread {
        private WebSocket ws;
        private String s;

        public HandleMsgThread(WebSocket ws, String s) {
            this.ws = ws;
            this.s = s;
        }

        @Override
        public void run() {
            handleMessage(ws, s);
        }

    }

    public void connect(String ip) {
        this.peerService.connectToPeer(ip);
    }
}
