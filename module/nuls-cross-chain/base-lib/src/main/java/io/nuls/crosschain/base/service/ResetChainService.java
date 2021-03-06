package io.nuls.crosschain.base.service;

import io.nuls.base.data.BlockHeader;
import io.nuls.base.data.Transaction;

import java.util.List;
import java.util.Map;

public interface ResetChainService {
    /**
     * 重置链信息交易初始化批量验证
     * @param chainId       chain ID
     * @param txs           cross chain transaction list
     * @param txMap         Consensus Module All Transaction Classification
     * @param blockHeader   block header
     *
     * @return processor result
     * */
    Map<String, Object> validate(int chainId, List<Transaction> txs, Map<Integer, List<Transaction>> txMap, BlockHeader blockHeader);

    /**
     * 重置链信息交易初始化提交
     * @param chainId       chain ID
     * @param txs           cross chain transaction list
     * @param blockHeader   block header
     *
     * @return processor result
     * */
    boolean commit(int chainId, List<Transaction> txs, BlockHeader blockHeader);

    /**
     * 重置链信息交易初始化回滚
     * @param chainId       chain ID
     * @param txs           cross chain transaction list
     * @param blockHeader   block header
     *
     * @return processor result
     * */
    boolean rollback(int chainId, List<Transaction> txs, BlockHeader blockHeader);
}
