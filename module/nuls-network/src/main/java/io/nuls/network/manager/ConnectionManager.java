/*
 * MIT License
 *
 * Copyright (c) 2017-2019 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package io.nuls.network.manager;


import io.netty.channel.socket.SocketChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.nuls.core.core.ioc.SpringLiteContext;
import io.nuls.core.log.Log;
import io.nuls.core.rpc.netty.channel.manager.ConnectManager;
import io.nuls.core.thread.ThreadUtils;
import io.nuls.core.thread.commom.NulsThreadFactory;
import io.nuls.network.cfg.NetworkConfig;
import io.nuls.network.constant.ManagerStatusEnum;
import io.nuls.network.constant.NodeConnectStatusEnum;
import io.nuls.network.constant.NodeStatusEnum;
import io.nuls.network.model.Node;
import io.nuls.network.model.NodeGroup;
import io.nuls.network.model.message.VersionMessage;
import io.nuls.network.model.po.GroupNodesPo;
import io.nuls.network.netty.NettyClient;
import io.nuls.network.netty.NettyServer;
import io.nuls.network.netty.container.NodesContainer;
import io.nuls.network.utils.IpUtil;
import io.nuls.network.utils.LoggerUtil;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * ???????????????,???????????????????????????????????????????????????
 * Connection manager, connection start, stop, connection reference cache management
 *
 * @author lan
 * @date 2018/11/01
 */
public class ConnectionManager extends BaseManager {
    NetworkConfig networkConfig = SpringLiteContext.getBean(NetworkConfig.class);
    NettyServer server = null;
    NettyServer serverCross = null;
    private static ConnectionManager instance = new ConnectionManager();
    /**
     * ??????Server ???????????????peer
     * Passer as a server passive connection
     */
    private Map<String, Node> cacheConnectNodeInMap = new ConcurrentHashMap<>();
    /**
     * ??????client ???????????????peer
     * As the client actively connected peer
     */
    private Map<String, Node> cacheConnectNodeOutMap = new ConcurrentHashMap<>();

    public ExecutorService discover = ThreadUtils.createThreadPool(Runtime.getRuntime().availableProcessors(), 20, new NulsThreadFactory("NODE_DISCOVER_MULTI_THREAD"));
    public ExecutorService maintenance = ThreadUtils.createThreadPool(Runtime.getRuntime().availableProcessors(), 20, new NulsThreadFactory("NODE_MAINTENANCE_MULTI_THREAD"));

    public static ConnectionManager getInstance() {
        return instance;
    }

    private ConnectionManager() {

    }

    /**
     * ??????????????????
     *
     * @param node
     */
    public void nodeConnectFail(Node node) {
        node.setStatus(NodeStatusEnum.UNAVAILABLE);
        node.setConnectStatus(NodeConnectStatusEnum.FAIL);
        node.setFailCount(node.getFailCount() + 1);
        node.setLastProbeTime(TimeManager.currentTimeMillis());
    }

    private StorageManager storageManager = StorageManager.getInstance();
    private ManagerStatusEnum status = ManagerStatusEnum.UNINITIALIZED;

    public boolean isRunning() {
        return instance.status == ManagerStatusEnum.RUNNING;
    }

    /**
     * ??????????????????
     * loadSeedsNode
     */
    private void loadSeedsNode() {
        List<String> list = networkConfig.getSeedIpList();
        NodeGroup nodeGroup = NodeGroupManager.getInstance().getNodeGroupByMagic(networkConfig.getPacketMagic());
        for (String seed : list) {
            String[] peer = IpUtil.splitHostPort(seed);
            if (IpUtil.getIps().contains(peer[0])) {
                continue;
            }
            Node node = new Node(nodeGroup.getMagicNumber(), peer[0], Integer.valueOf(peer[1]), 0, Node.OUT, false);
            node.setConnectStatus(NodeConnectStatusEnum.UNCONNECT);
            node.setSeedNode(true);
            node.setStatus(NodeStatusEnum.CONNECTABLE);
            nodeGroup.getLocalNetNodeContainer().getCanConnectNodes().put(node.getId(), node);
            nodeGroup.getLocalNetNodeContainer().getUncheckNodes().remove(node.getId());
            nodeGroup.getLocalNetNodeContainer().getDisconnectNodes().remove(node.getId());
            nodeGroup.getLocalNetNodeContainer().getFailNodes().remove(node.getId());
        }
    }

    public void nodeClientConnectSuccess(Node node) {
        NodeGroup nodeGroup = node.getNodeGroup();
        NodesContainer nodesContainer = null;
        if (node.isCrossConnect()) {
            nodesContainer = nodeGroup.getCrossNodeContainer();
        } else {
            nodesContainer = nodeGroup.getLocalNetNodeContainer();
        }
        nodesContainer.getConnectedNodes().put(node.getId(), node);
        nodesContainer.getCanConnectNodes().remove(node.getId());
        node.setConnectStatus(NodeConnectStatusEnum.CONNECTED);
        LoggerUtil.logger(nodeGroup.getChainId()).debug("client node {} connect success !", node.getId());
        //????????????
        VersionMessage versionMessage = MessageFactory.getInstance().buildVersionMessage(node, nodeGroup.getMagicNumber());
        MessageManager.getInstance().sendHandlerMsg(versionMessage, node, true);
    }

    private void cacheNode(Node node, SocketChannel channel) {

        String name = "node-" + node.getId();
        boolean exists = AttributeKey.exists(name);
        AttributeKey attributeKey;
        if (exists) {
            attributeKey = AttributeKey.valueOf(name);
        } else {
            attributeKey = AttributeKey.newInstance(name);
        }
        Attribute<Node> attribute = channel.attr(attributeKey);

        attribute.set(node);
    }

    public boolean nodeConnectIn(String ip, int port, SocketChannel channel) {
        boolean isCross = false;
        //client ?????? server?????????????????????????
        if (channel.localAddress().getPort() == networkConfig.getCrossPort()) {
            isCross = true;
        }
        if (!isRunning()) {
            LoggerUtil.COMMON_LOG.debug("ConnectionManager is stop,refuse peer = {}:{} connectIn isCross={}", ip, port, isCross);
            return false;
        }
        LoggerUtil.COMMON_LOG.debug("peer = {}:{} connectIn isCross={}", ip, port, isCross);
        //???????????????????????????????????????id???????????????????????????group,?????????version???????????????????????????
        Node node = new Node(0L, ip, port, 0, Node.IN, isCross);
        node.setConnectStatus(NodeConnectStatusEnum.CONNECTED);
        node.setChannel(channel);
        cacheNode(node, channel);
        return true;
    }

    public void nodeConnectDisconnect(Node node) {
        if (node.getChannel() != null) {
            node.setChannel(null);
        }
        NodeGroup nodeGroup = node.getNodeGroup();
        NodesContainer nodesContainer = null;
        if (node.isCrossConnect()) {
            nodesContainer = nodeGroup.getCrossNodeContainer();
        } else {
            nodesContainer = nodeGroup.getLocalNetNodeContainer();
        }
        //???????????????,????????????????????????????????????????????????????????????

        if (node.getConnectStatus() == NodeConnectStatusEnum.AVAILABLE) {
            //??????????????????
            node.setFailCount(0);
            node.setHadShare(false);
            node.setConnectStatus(NodeConnectStatusEnum.DISCONNECT);
            nodesContainer.getDisconnectNodes().put(node.getId(), node);
            nodesContainer.getConnectedNodes().remove(node.getId());
//            Log.info("node {} disconnect !", node.getId());
        } else {
            // ???????????????????????????????????????????????????????????????+1?????????????????????????????????????????????????????????
            if (node.getConnectStatus() == NodeConnectStatusEnum.CONNECTED) {
                //socket??????????????????????????????????????????????????????????????????
                nodesContainer.getConnectedNodes().remove(node.getId());
            }
            nodeConnectFail(node);
            nodesContainer.getCanConnectNodes().remove(node.getId());
            nodesContainer.getFailNodes().put(node.getId(), node);
        }
    }


    /**
     * netty boot
     */
    private void nettyBoot() {
        serverStart();
        Log.info("==========================NettyServerBoot");
    }

    /**
     * server start
     */
    private void serverStart() {
        server = new NettyServer(networkConfig.getPort());
        serverCross = new NettyServer(networkConfig.getCrossPort());
        server.init();
        serverCross.init();
        ThreadUtils.createAndRunThread("node server start", () -> {
            try {
                server.start();
            } catch (InterruptedException e) {
                Log.error(e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        }, false);
        ThreadUtils.createAndRunThread("node crossServer start", () -> {
            try {
                serverCross.start();
            } catch (InterruptedException e) {
                Log.error(e);
                Thread.currentThread().interrupt();
            }
        }, false);

    }

    public boolean connection(Node node) {
        try {
            NettyClient client = new NettyClient(node);
            return client.start();
        } catch (Exception e) {
            Log.error("connect to node {} error : {}", node.getId(), e.getMessage());
            return false;
        }
    }

    @Override
    public void init() throws Exception {
        status = ManagerStatusEnum.INITIALIZED;
        Collection<NodeGroup> nodeGroups = NodeGroupManager.getInstance().getNodeGroupCollection();
        for (NodeGroup nodeGroup : nodeGroups) {
            if (!nodeGroup.isMoonCrossGroup()) {
                //??????????????????????????????????????????????????????????????????????????????????????????
                loadSeedsNode();
            }
            //???????????????node
            GroupNodesPo groupNodesPo = storageManager.getNodesByChainId(nodeGroup.getChainId());
            nodeGroup.loadNodes(groupNodesPo);
        }
    }

    @Override
    public void start() throws Exception {
        while (!ConnectManager.isReady()) {
            Log.debug("wait depend modules ready");
            Thread.sleep(2000L);
        }
        nettyBoot();
        status = ManagerStatusEnum.RUNNING;
    }

    @Override
    public void change(ManagerStatusEnum toStatus) throws Exception {
        status = toStatus;
        if (toStatus == ManagerStatusEnum.STOPED) {
            //???????????????netty

        } else if (toStatus == ManagerStatusEnum.RUNNING) {
            //?????????
        }

    }
}
