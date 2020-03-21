package com.dizsun.timechain.service;


import com.alibaba.fastjson.JSON;

import com.dizsun.timechain.constant.Config;
import com.dizsun.timechain.constant.R;
import com.dizsun.timechain.util.DateUtil;
import com.dizsun.timechain.util.LogUtil;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by dizsun on 2017/7/6.
 * 提供http接口,包含jetty服务器
 */
public class HTTPService {
    private Logger logger = Logger.getLogger(HTTPService.class);
    private BlockService blockService;
    private PeerService peerService;

    private Config config = Config.getInstance();
    private DateUtil dateUtil = DateUtil.getInstance();

    public HTTPService() {

    }

    public void initHTTPServer(int port) {
        this.blockService = BlockService.getInstance();
        this.peerService = PeerService.getInstance();
        try {
            Server server = new Server(port);
            logger.info("listening http port on: " + port);
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");
            server.setHandler(context);
            context.addServlet(new ServletHolder(new BlocksServlet()), "/blocks");
//            context.addServlet(new ServletHolder(new MineBlockServlet()), "/mineBlock");
            context.addServlet(new ServletHolder(new PeersServlet()), "/peers");
            context.addServlet(new ServletHolder(new AddPeerServlet()), "/addPeer");
            //context.addServlet(new ServletHolder(new TimeCenterServlet()), "/setTC");

            context.addServlet(new ServletHolder(new RepGetServlet()), "/proxy_ip");
            context.addServlet(new ServletHolder(new NewBlkGetServlet()), "/latest_blk");
            context.addServlet(new ServletHolder(new LastConsensusTime()), "/consensus_time");
            context.addServlet(new ServletHolder(new GetLatestTime()), "/sync_time");

            server.start();
            server.join();
        } catch (Exception e) {
            logger.error("init http server is error:" + e.getMessage());
        }
    }

    // 时间同步，非代表节点通过代表节点向授时中心获取最新时间
    private class GetLatestTime extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().println(JSON.toJSONString(dateUtil.getTimeFromRC()));
        }
    }

    // 上一次共识时间
    private class LastConsensusTime extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setCharacterEncoding("UTF-8");
//            resp.getWriter().println(JSON.toJSONString(LogUtil.readLastLog(LogUtil.CONSENSUS)));
            resp.getWriter().println(JSON.toJSONString(R.getDuration()));
        }
    }

    // 最新块内容获取
    private class NewBlkGetServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().println(JSON.toJSONString(blockService.getLatestBlock()));
        }
    }

    private class RepGetServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().println(JSON.toJSONString(blockService.getLatestBlock().getCreater()));
        }
    }

    private class BlocksServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().println(JSON.toJSONString(blockService.getBlockChain()));
        }
    }

    /**
     * 格式peer=http://192.168.1.1:6001
     */
    private class AddPeerServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            this.doPost(req, resp);
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setCharacterEncoding("UTF-8");
            String peer = req.getParameter("peer");
            peerService.connectToPeer(peer);
            resp.getWriter().print("ok");
        }
    }


    private class PeersServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().println(JSON.toJSONString(peerService.getPeerArray()));
        }
    }

//    private class TimeCenterServlet extends HttpServlet{
//        @Override
//        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//            this.doPost(req,resp);
//        }
//
//        @Override
//        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//            resp.setCharacterEncoding("UTF-8");
//            String host = req.getParameter("host");
//            DateUtil dateUtil=DateUtil.newDataUtil();
//            dateUtil.setHost(host);
//        }
//    }
}

