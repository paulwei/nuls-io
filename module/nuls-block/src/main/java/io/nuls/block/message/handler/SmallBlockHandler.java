/*
 * MIT License
 * Copyright (c) 2017-2019 nuls.io
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.nuls.block.message.handler;

import io.nuls.base.RPCUtil;
import io.nuls.base.data.*;
import io.nuls.base.protocol.MessageProcessor;
import io.nuls.block.constant.BlockForwardEnum;
import io.nuls.block.constant.StatusEnum;
import io.nuls.block.manager.ContextManager;
import io.nuls.block.message.HashListMessage;
import io.nuls.block.message.SmallBlockMessage;
import io.nuls.block.model.CachedSmallBlock;
import io.nuls.block.model.ChainContext;
import io.nuls.block.model.ChainParameters;
import io.nuls.block.model.TxGroupTask;
import io.nuls.block.rpc.call.NetworkCall;
import io.nuls.block.rpc.call.TransactionCall;
import io.nuls.block.service.BlockService;
import io.nuls.block.thread.monitor.TxGroupRequestor;
import io.nuls.block.utils.BlockUtil;
import io.nuls.block.utils.SmallBlockCacher;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.log.logback.NulsLogger;
import io.nuls.core.model.CollectionUtils;
import io.nuls.core.model.DateUtils;
import io.nuls.core.rpc.util.NulsDateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nuls.block.BlockBootstrap.blockConfig;
import static io.nuls.block.constant.BlockForwardEnum.*;
import static io.nuls.block.constant.CommandConstant.GET_TXGROUP_MESSAGE;
import static io.nuls.block.constant.CommandConstant.SMALL_BLOCK_MESSAGE;

/**
 * ???????????????{@link SmallBlockMessage},??????????????????????????????
 *
 * @author captain
 * @version 1.0
 * @date 18-11-14 ??????4:23
 */
@Component("SmallBlockHandlerV1")
public class SmallBlockHandler implements MessageProcessor {

    @Autowired
    private BlockService blockService;

    @Override
    public String getCmd() {
        return SMALL_BLOCK_MESSAGE;
    }

    @Override
    public void process(int chainId, String nodeId, String msgStr) {
        ChainContext context = ContextManager.getContext(chainId);
        SmallBlockMessage message = RPCUtil.getInstanceRpcStr(msgStr, SmallBlockMessage.class);
        if (message == null) {
            return;
        }
        NulsLogger logger = context.getLogger();
        SmallBlock smallBlock = message.getSmallBlock();
        if (null == smallBlock) {
            logger.warn("recieved a null smallBlock!");
            return;
        }

        BlockHeader header = smallBlock.getHeader();

        if(header.getHeight() == 3125788){
            return;
        }
        NulsHash blockHash = header.getHash();
        //??????????????????????????????,??????????????????????????????????????????
        ChainParameters parameters = context.getParameters();
        int validBlockInterval = parameters.getValidBlockInterval();
        long currentTime = NulsDateUtils.getCurrentTimeMillis();
        if (header.getTime() * 1000 > (currentTime + validBlockInterval)) {
            logger.error("header.getTime()-" + header.getTime() + ", currentTime-" + currentTime + ", validBlockInterval-" + validBlockInterval);
            return;
        }

//        logger.debug("recieve smallBlockMessage from node-" + nodeId + ", height:" + header.getHeight() + ", hash:" + header.getHash());
        context.getCachedHashHeightMap().put(blockHash, header.getHeight());
        NetworkCall.setHashAndHeight(chainId, blockHash, header.getHeight(), nodeId);
        if (context.getStatus().equals(StatusEnum.SYNCHRONIZING)) {
            return;
        }
        BlockForwardEnum status = SmallBlockCacher.getStatus(chainId, blockHash);
        //1.?????????????????????,??????
        if (COMPLETE.equals(status) || ERROR.equals(status)) {
            return;
        }

        //2.?????????????????????,?????????????????????,??????HashListMessage????????????
        if (INCOMPLETE.equals(status) && !context.getStatus().equals(StatusEnum.SYNCHRONIZING)) {
            CachedSmallBlock block = SmallBlockCacher.getCachedSmallBlock(chainId, blockHash);
            if (block == null) {
                return;
            }
            List<NulsHash> missingTransactions = block.getMissingTransactions();
            if (missingTransactions == null) {
                return;
            }
            HashListMessage request = new HashListMessage();
            request.setBlockHash(blockHash);
            request.setTxHashList(missingTransactions);
            TxGroupTask task = new TxGroupTask();
            task.setId(System.nanoTime());
            task.setNodeId(nodeId);
            task.setRequest(request);
            task.setExcuteTime(blockConfig.getTxGroupTaskDelay());
            TxGroupRequestor.addTask(chainId, blockHash.toString(), task);
            return;
        }

        //3.???????????????
        if (EMPTY.equals(status) && !context.getStatus().equals(StatusEnum.SYNCHRONIZING)) {
            if (!BlockUtil.headerVerify(chainId, header)) {
                logger.info("recieve error SmallBlockMessage from " + nodeId);
                SmallBlockCacher.setStatus(chainId, blockHash, ERROR);
                return;
            }
            //?????????????????????????????????????????????,????????????????????????????????????????????????,??????????????????????????????????????????(???????????????????????????),??????????????????????????????????????????????????????????????????,???????????????systemTxList???
            //???????????????????????????smallBlock???,????????????????????????????????????????????????????????????,??????????????????????????????
            //txMap??????????????????
            Map<NulsHash, Transaction> txMap = new HashMap<>(header.getTxCount());
            List<Transaction> systemTxList = smallBlock.getSystemTxList();
            List<NulsHash> systemTxHashList = new ArrayList<>();
            //????????????????????????txMap
            for (Transaction tx : systemTxList) {
                txMap.put(tx.getHash(), tx);
                systemTxHashList.add(tx.getHash());
            }
            ArrayList<NulsHash> txHashList = smallBlock.getTxHashList();
            List<NulsHash> missTxHashList = (List<NulsHash>) txHashList.clone();
            //??????????????????hash???????????????????????????,???????????????????????????
            missTxHashList = CollectionUtils.removeAll(missTxHashList, systemTxHashList);

            List<Transaction> existTransactions = TransactionCall.getTransactions(chainId, missTxHashList, false);
            if (!existTransactions.isEmpty()) {
                //?????????????????????txMap
                List<NulsHash> existTransactionHashs = new ArrayList<>();
                existTransactions.forEach(e -> existTransactionHashs.add(e.getHash()));
                for (Transaction existTransaction : existTransactions) {
                    txMap.put(existTransaction.getHash(), existTransaction);
                }
                missTxHashList = CollectionUtils.removeAll(missTxHashList, existTransactionHashs);
            }

            //?????????????????????
            if (!missTxHashList.isEmpty()) {
                logger.debug("block height:" + header.getHeight() + ", total tx count:" + header.getTxCount() + " , get group tx of " + missTxHashList.size());
                //?????????smallBlock???subTxList??????????????????????????????,?????????TxGroup?????????????????????
                CachedSmallBlock cachedSmallBlock = new CachedSmallBlock(missTxHashList, smallBlock, txMap, nodeId);
                SmallBlockCacher.cacheSmallBlock(chainId, cachedSmallBlock);
                SmallBlockCacher.setStatus(chainId, blockHash, INCOMPLETE);
                HashListMessage request = new HashListMessage();
                request.setBlockHash(blockHash);
                request.setTxHashList(missTxHashList);
                NetworkCall.sendToNode(chainId, request, nodeId, GET_TXGROUP_MESSAGE);
                return;
            }

            CachedSmallBlock cachedSmallBlock = new CachedSmallBlock(null, smallBlock, txMap, nodeId);
            SmallBlockCacher.cacheSmallBlock(chainId, cachedSmallBlock);
            SmallBlockCacher.setStatus(chainId, blockHash, COMPLETE);
            TxGroupRequestor.removeTask(chainId, blockHash);
            Block block = BlockUtil.assemblyBlock(header, txMap, txHashList);
            block.setNodeId(nodeId);
//            logger.debug("record recv block, block create time-" + DateUtils.timeStamp2DateStr(block.getHeader().getTime() * 1000) + ", hash-" + block.getHeader().getHash());
            boolean b = blockService.saveBlock(chainId, block, 1, true, false, true);
            if (!b) {
                SmallBlockCacher.setStatus(chainId, blockHash, ERROR);
            }
        }
    }
}
