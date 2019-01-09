package io.nuls.ledger.utils;

import io.nuls.ledger.constant.LedgerConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;

/**
 * Created by lanjinsheng on 2019/01/02
 */
public class LedgerUtils {
    static final Logger logger = LoggerFactory.getLogger(LedgerUtils.class);

    /**
     * rockdb key
     *
     * @param address
     * @param assetId
     * @return
     */
    public static String getKeyStr(String address, int assetChainId, int assetId) {
       return  address + "-" + assetChainId + "-" + assetId;

    }
    /**
     * rockdb key
     *
     * @param address
     * @param assetId
     * @return
     */
    public static byte[] getKey(String address, int assetChainId, int assetId) {
        String key = address + "-" + assetChainId + "-" + assetId;
        try {
            return (key.getBytes(LedgerConstant.DEFAULT_ENCODING));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 获取账户缓存快照的key值，key值组成 = 资产key-txHash-区块高度
     * @param assetKey
     * @param txHash
     * @param height
     * @return
     */
    public static byte[] getSnapshotTxKey(String assetKey,String txHash,long height) {
        String key = assetKey+ "-"+txHash+"-"+height;
        try {
            return (key.getBytes(LedgerConstant.DEFAULT_ENCODING));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static String getSnapshotTxKeyStr(String assetKey,String txHash,long height) {
       return assetKey+ "-"+txHash+"-"+height;
    }
    /**
     * 获取账户缓存快照的key值索引，key值组成 = 资产key-区块高度
     * 存储的value值是 List<accountstates>
     * @param assetKey
     * @param height
     * @return
     */
    public static byte[] getSnapshotHeightKey(String assetKey,long height) {
        String key = assetKey+ "-"+height;
        try {
            return (key.getBytes(LedgerConstant.DEFAULT_ENCODING));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static String getSnapshotHeightKeyStr(String assetKey,long height) {
        return assetKey+ "-"+height;
    }

}
