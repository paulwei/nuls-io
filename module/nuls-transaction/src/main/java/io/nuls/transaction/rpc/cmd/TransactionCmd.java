package io.nuls.transaction.rpc.cmd;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.nuls.base.RPCUtil;
import io.nuls.base.basic.AddressTool;
import io.nuls.base.data.BlockHeader;
import io.nuls.base.data.NulsHash;
import io.nuls.base.data.Transaction;
import io.nuls.base.protocol.ProtocolGroupManager;
import io.nuls.base.protocol.TxRegisterDetail;
import io.nuls.base.signture.MultiSignTxSignature;
import io.nuls.base.signture.P2PHKSignature;
import io.nuls.base.signture.TransactionSignature;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.crypto.ECKey;
import io.nuls.core.exception.NulsException;
import io.nuls.core.model.ObjectUtils;
import io.nuls.core.parse.JSONUtils;
import io.nuls.core.rpc.cmd.BaseCmd;
import io.nuls.core.rpc.model.*;
import io.nuls.core.rpc.model.message.Response;
import io.nuls.transaction.cache.PackablePool;
import io.nuls.transaction.constant.TxCmd;
import io.nuls.transaction.constant.TxConstant;
import io.nuls.transaction.constant.TxContext;
import io.nuls.transaction.constant.TxErrorCode;
import io.nuls.transaction.manager.ChainManager;
import io.nuls.transaction.manager.TxManager;
import io.nuls.transaction.model.bo.Chain;
import io.nuls.transaction.model.bo.TxPackage;
import io.nuls.transaction.model.bo.VerifyLedgerResult;
import io.nuls.transaction.model.dto.ModuleTxRegisterDTO;
import io.nuls.transaction.model.po.TransactionConfirmedPO;
import io.nuls.transaction.rpc.call.NetworkCall;
import io.nuls.transaction.service.ConfirmedTxService;
import io.nuls.transaction.service.TxService;
import io.nuls.transaction.utils.TxUtil;

import java.util.*;

import static io.nuls.transaction.utils.LoggerUtil.LOG;

/**
 * @author: Charlie
 * @date: 2018/11/12
 */
@Component
public class TransactionCmd extends BaseCmd {

    @Autowired
    private TxService txService;
    @Autowired
    private ConfirmedTxService confirmedTxService;
    @Autowired
    private ChainManager chainManager;
    @Autowired
    private PackablePool packablePool;

    @CmdAnnotation(cmd = TxCmd.TX_REGISTER, version = 1.0, description = "??????????????????/Register module transactions")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "moduleCode", parameterType = "String", parameterDes = "?????????????????????code"),
            @Parameter(parameterName = "list", requestType = @TypeDescriptor(value = List.class, collectionElement = TxRegisterDetail.class), parameterDes = "????????????????????????"),
            @Parameter(parameterName = "delList", requestType = @TypeDescriptor(value = List.class, collectionElement = Integer.class), parameterDes = "??????????????????????????????", canNull = true)
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = boolean.class, description = "??????????????????")
    }))
    public Response register(Map params) {
        Map<String, Boolean> map = new HashMap<>(TxConstant.INIT_CAPACITY_2);
        boolean result = false;
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("moduleCode"), TxErrorCode.PARAMETER_ERROR.getMsg());

            JSONUtils.getInstance().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            ModuleTxRegisterDTO moduleTxRegisterDto = JSONUtils.map2pojo(params, ModuleTxRegisterDTO.class);

            chain = chainManager.getChain(moduleTxRegisterDto.getChainId());
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            List<TxRegisterDetail> txRegisterList = moduleTxRegisterDto.getList();
            if (moduleTxRegisterDto == null || txRegisterList == null) {
                throw new NulsException(TxErrorCode.TX_NOT_EXIST);
            }
            result = txService.register(chain, moduleTxRegisterDto);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }

        map.put("value", result);
        return success(map);
    }

    @CmdAnnotation(cmd = TxCmd.TX_BROADCAST, version = 1.0, description = "?????????????????????/broadcast a new transaction")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "tx", parameterType = "String", parameterDes = "??????????????????????????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = boolean.class, description = "????????????"),
            @Key(name = "hash", description = "??????hash")
    }))
    public Response broadcastTx(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("tx"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            String txStr = (String) params.get("tx");
            //???txStr?????????Transaction??????
            Transaction transaction = TxUtil.getInstanceRpcStr(txStr, Transaction.class);
            //?????????????????????????????????????????????
            boolean rs = NetworkCall.broadcastTx(chain, transaction);
            Map<String, Object> map = new HashMap<>(TxConstant.INIT_CAPACITY_4);
            map.put("value", rs);
            map.put("hash", transaction.getHash().toHex());
            return success(map);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }

    @CmdAnnotation(cmd = TxCmd.TX_NEWTX, version = 1.0, description = "?????????????????????/receive a new transaction")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "tx", parameterType = "String", parameterDes = "??????????????????????????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = boolean.class, description = "????????????"),
            @Key(name = "hash", description = "??????hash")
    }))
    public Response newTx(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("tx"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            String txStr = (String) params.get("tx");
            //???txStr?????????Transaction??????
            Transaction transaction = TxUtil.getInstanceRpcStr(txStr, Transaction.class);
            //?????????????????????????????????????????????
            txService.newTx(chain, transaction);
            Map<String, Object> map = new HashMap<>(TxConstant.INIT_CAPACITY_4);
            map.put("value", true);
            map.put("hash", transaction.getHash().toHex());
            return success(map);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }

    @CmdAnnotation(cmd = TxCmd.TX_PACKABLETXS, version = 1.0, description = "???????????????????????????/returns a list of packaged transactions")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "endTimestamp", requestType = @TypeDescriptor(value = long.class), parameterDes = "????????????"),
            @Parameter(parameterName = "maxTxDataSize", requestType = @TypeDescriptor(value = int.class), parameterDes = "?????????????????????"),
            @Parameter(parameterName = "blockTime", requestType = @TypeDescriptor(value = long.class), parameterDes = "????????????????????????"),
            @Parameter(parameterName = "packingAddress", parameterType = "String", parameterDes = "??????????????????"),
            @Parameter(parameterName = "preStateRoot", parameterType = "String", parameterDes = "???????????????????????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map???????????????key", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "list", valueType = List.class, valueElement = String.class, description = "??????????????????"),
            @Key(name = "stateRoot", description = "????????????????????????"),
            @Key(name = "packageHeight", valueType = long.class, description = "???????????????????????????")
    }))
    public Response packableTxs(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("endTimestamp"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("maxTxDataSize"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("blockTime"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("packingAddress"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("preStateRoot"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            //?????????????????????
            long endTimestamp = Long.parseLong(params.get("endTimestamp").toString());
            //???????????????????????????
            int maxTxDataSize = (int) params.get("maxTxDataSize");

            long blockTime = Long.parseLong(params.get("blockTime").toString());
            String packingAddress = (String) params.get("packingAddress");
            String preStateRoot = (String) params.get("preStateRoot");

            TxPackage txPackage;
            if(ProtocolGroupManager.getCurrentVersion(chain.getChainId()) >= TxContext.UPDATE_VERSION_CONTRACT_ASSET ) {
                txPackage = txService.getPackableTxsV8(chain, endTimestamp, maxTxDataSize, blockTime, packingAddress, preStateRoot);
            } else {
                txPackage = txService.getPackableTxs(chain, endTimestamp, maxTxDataSize, blockTime, packingAddress, preStateRoot);
            }

            Map<String, Object> map = new HashMap<>(TxConstant.INIT_CAPACITY_4);
            map.put("list", txPackage.getList());
            map.put("stateRoot", txPackage.getStateRoot());
            map.put("packageHeight", txPackage.getPackageHeight());
            return success(map);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }

    @CmdAnnotation(cmd = TxCmd.TX_BACKPACKABLETXS, version = 1.0, description = "???????????????????????????????????????????????????????????????????????????/back packaged transactions")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "txList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "????????????????????????????????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = boolean.class, description = "????????????")
    }))
    public Response backPackableTxs(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("txList"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            List<String> txStrList = (List<String>) params.get("txList");
            int count = txStrList.size() - 1;
            for (int i = count; i >= 0; i--) {
                Transaction tx = TxUtil.getInstanceRpcStr(txStrList.get(i), Transaction.class);
                packablePool.offerFirstOnlyHash(chain, tx);
            }
            Map<String, Object> map = new HashMap<>(TxConstant.INIT_CAPACITY_2);
            map.put("value", true);
            return success(map);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }

    /**
     * Save the transaction in the new block that was verified to the database
     * ????????????????????????
     *
     * @param params Map
     * @return Response
     */
    @CmdAnnotation(cmd = TxCmd.TX_SAVE, priority = CmdPriority.HIGH, version = 1.0, description = "????????????????????????/Save the confirmed transaction")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "txList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "????????????????????????"),
            @Parameter(parameterName = "contractList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "??????????????????"),
            @Parameter(parameterName = "blockHeader", parameterType = "String", parameterDes = "?????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = boolean.class, description = "????????????")
    }))
    public Response txSave(Map params) {
        Map<String, Boolean> map = new HashMap<>(TxConstant.INIT_CAPACITY_16);
        boolean result = false;
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("txList"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("blockHeader"), TxErrorCode.PARAMETER_ERROR.getMsg());

            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            List<String> txStrList = (List<String>) params.get("txList");
            if (null == txStrList) {
                throw new NulsException(TxErrorCode.PARAMETER_ERROR);
            }
            List<String> contractList = (List<String>) params.get("contractList");
            result = confirmedTxService.saveTxList(chain, txStrList, contractList, (String) params.get("blockHeader"));
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
        Map<String, Boolean> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_2);
        resultMap.put("value", result);
        return success(resultMap);
    }

    @CmdAnnotation(cmd = TxCmd.TX_GENGSIS_SAVE, version = 1.0, description = "????????????????????????/Save the transactions of the Genesis block ")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "txList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "????????????????????????"),
            @Parameter(parameterName = "blockHeader", parameterType = "String", parameterDes = "?????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = boolean.class, description = "????????????")
    }))
    public Response txGengsisSave(Map params) {
        Map<String, Boolean> map = new HashMap<>(TxConstant.INIT_CAPACITY_16);
        boolean result = false;
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("txList"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("blockHeader"), TxErrorCode.PARAMETER_ERROR.getMsg());

            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            List<String> txStrList = (List<String>) params.get("txList");
            result = confirmedTxService.saveGengsisTxList(chain, txStrList, (String) params.get("blockHeader"));
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
        Map<String, Boolean> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_2);
        resultMap.put("value", result);
        return success(resultMap);
    }

    @CmdAnnotation(cmd = TxCmd.TX_ROLLBACK, priority = CmdPriority.HIGH, version = 1.0, description = "?????????????????????/transaction rollback")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "txHashList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "?????????????????????"),
            @Parameter(parameterName = "blockHeader", parameterType = "String", parameterDes = "?????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = boolean.class, description = "????????????")
    }))
    public Response txRollback(Map params) {
        boolean result;
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("txHashList"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("blockHeader"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            List<String> txHashStrList = (List<String>) params.get("txHashList");
            List<NulsHash> txHashList = new ArrayList<>();
            //?????????hashHex???????????????hash????????????
            for (String hashStr : txHashStrList) {
                txHashList.add(NulsHash.fromHex(hashStr));
            }
            //???????????????????????????
            result = confirmedTxService.rollbackTxList(chain, txHashList, (String) params.get("blockHeader"));
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
        Map<String, Boolean> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_2);
        resultMap.put("value", result);
        return success(resultMap);
    }

    @CmdAnnotation(cmd = TxCmd.TX_GET_SYSTEM_TYPES, version = 1.0, description = "??????????????????????????????/Get system transaction types")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id")
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "list", valueType = List.class, valueElement = Integer.class, description = "????????????????????????")
    }))
    public Response getSystemTypes(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            List<Integer> list = TxManager.getSysTypes(chain);
            Map<String, Object> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_2);
            resultMap.put("list", list);
            return success(resultMap);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }

    @CmdAnnotation(cmd = TxCmd.TX_GETTX, version = 1.0, description = "??????hash????????????, ???????????????, ????????????????????????/Get transaction by tx hash")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "txHash", parameterType = "String", parameterDes = "???????????????hash")
    })
    @ResponseData(name = "?????????", description = "????????????Map??????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "tx", description = "????????????????????????????????????????????????")
    }))
    public Response getTx(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("txHash"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            String txHash = (String) params.get("txHash");
            if (!NulsHash.validHash(txHash)) {
                throw new NulsException(TxErrorCode.HASH_ERROR);
            }
            TransactionConfirmedPO tx = txService.getTransaction(chain, NulsHash.fromHex(txHash));
            Map<String, String> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_2);
            if (tx == null) {
//                LOG.debug("getTx - from all, fail! tx is null, txHash:{}", txHash);
                resultMap.put("tx", null);
            } else {
//                LOG.debug("getTx - from all, success txHash : " + tx.getTx().getHash().toHex());
                resultMap.put("tx", RPCUtil.encode(tx.getTx().serialize()));
            }
            return success(resultMap);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }

    @CmdAnnotation(cmd = TxCmd.TX_GET_CONFIRMED_TX, version = 1.0, description = "??????hash?????????????????????(???????????????)/Get confirmed transaction by tx hash")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "txHash", parameterType = "String", parameterDes = "???????????????hash")
    })
    @ResponseData(name = "?????????", description = "????????????Map??????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "tx", description = "????????????????????????????????????????????????")
    }))
    public Response getConfirmedTx(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("txHash"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            String txHash = (String) params.get("txHash");
            if (!NulsHash.validHash(txHash)) {
                throw new NulsException(TxErrorCode.HASH_ERROR);
            }
            TransactionConfirmedPO tx = confirmedTxService.getConfirmedTransaction(chain, NulsHash.fromHex(txHash));
            Map<String, String> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_2);
            if (tx == null) {
//                LOG.debug("getConfirmedTransaction fail, tx is null. txHash:{}", txHash);
                resultMap.put("tx", null);
            } else {
//                LOG.debug("getConfirmedTransaction success. txHash:{}", txHash);
                resultMap.put("tx", RPCUtil.encode(tx.getTx().serialize()));
            }
            return success(resultMap);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }


    @CmdAnnotation(cmd = TxCmd.TX_ISCONFIRMED, version = 1.0, description = "??????hash???????????????????????????(???????????????)/Check tx is confirmed by tx hash")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "txHash", parameterType = "String", parameterDes = "???????????????hash")
    })
    @ResponseData(name = "?????????", description = "????????????Map??????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", description = "true: confirmed; false:unconfirmed")
    }))
    public Response isConfirmed(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("txHash"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            String txHash = (String) params.get("txHash");
            if (!NulsHash.validHash(txHash)) {
                throw new NulsException(TxErrorCode.HASH_ERROR);
            }
            TransactionConfirmedPO txPO = confirmedTxService.getConfirmedTransaction(chain, NulsHash.fromHex(txHash));
            Map<String, Boolean> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_2);
            resultMap.put("value", false);
            if (txPO != null) {
                Transaction tx = txPO.getTx();
                if (null != tx && txHash.equals(tx.getHash().toHex())) {
                    resultMap.put("value", true);
                }
            }
            return success(resultMap);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }

    @CmdAnnotation(cmd = TxCmd.TX_GET_BLOCK_TXS, version = 1.0,
            description = "??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????/Get block transactions")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "txHashList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "???????????????hash??????")
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "txList", valueType = List.class, valueElement = String.class, description = "??????????????????????????????????????????")
    }))
    public Response getBlockTxs(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("txHashList"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            List<String> txHashList = (List<String>) params.get("txHashList");
            List<String> txList = confirmedTxService.getTxList(chain, txHashList);
            Map<String, List<String>> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_2);
            resultMap.put("txList", txList);
            return success(resultMap);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }

    @CmdAnnotation(cmd = TxCmd.TX_GET_BLOCK_TXS_EXTEND, version = 1.0, description = "??????hash?????????????????????????????????????????????????????????/Get transactions by hashs")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "txHashList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "???????????????hash??????"),
            @Parameter(parameterName = "allHits", requestType = @TypeDescriptor(value = boolean.class), parameterDes = "true??????????????????????????????????????????????????????list??? false???????????????????????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "txList", valueType = List.class, valueElement = String.class, description = "??????????????????????????????????????????")
    }))
    public Response getBlockTxsExtend(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("txHashList"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("allHits"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            List<String> txHashList = (List<String>) params.get("txHashList");
            boolean allHits = (boolean) params.get("allHits");
            List<String> txList = confirmedTxService.getTxListExtend(chain, txHashList, allHits);
            Map<String, List<String>> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_2);
            resultMap.put("txList", txList);
            return success(resultMap);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }


    @CmdAnnotation(cmd = TxCmd.TX_GET_NONEXISTENT_UNCONFIRMED_HASHS, version = 1.0, description = "?????????????????????hash???,??????????????????????????????hash/Get nonexistent unconfirmed transaction hashs")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "txHashList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "???????????????hash??????")
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "txList", valueType = List.class, valueElement = String.class, description = "??????????????????????????????????????????")
    }))
    public Response getNonexistentUnconfirmedHashs(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("txHashList"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            List<String> txHashList = (List<String>) params.get("txHashList");
            List<String> hashList = confirmedTxService.getNonexistentUnconfirmedHashList(chain, txHashList);
            Map<String, List<String>> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_2);
            resultMap.put("txHashList", hashList);
            return success(resultMap);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }


    @CmdAnnotation(cmd = TxCmd.TX_BATCHVERIFY, priority = CmdPriority.HIGH, version = 1.0, description = "????????????????????????/Verify all transactions in the block")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "txList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "?????????????????????????????????????????????"),
            @Parameter(parameterName = "blockHeader", parameterType = "String", parameterDes = "??????????????????"),
            @Parameter(parameterName = "preStateRoot", parameterType = "String", parameterDes = "????????????????????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map???????????????key", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = boolean.class, description = "??????????????????"),
            @Key(name = "contractList", valueType = List.class, valueElement = String.class, description = "??????????????????????????????")
    }))
    public Response batchVerify(Map params) {
        VerifyLedgerResult verifyLedgerResult = null;
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("txList"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("blockHeader"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("preStateRoot"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            List<String> txList = (List<String>) params.get("txList");

            String blockHeaderStr = (String) params.get("blockHeader");
            BlockHeader blockHeader = TxUtil.getInstanceRpcStr(blockHeaderStr, BlockHeader.class);

            String preStateRoot = (String) params.get("preStateRoot");

            Map<String, Object> resultMap;
            if(ProtocolGroupManager.getCurrentVersion(chain.getChainId()) >= TxContext.UPDATE_VERSION_CONTRACT_ASSET ) {
                resultMap = txService.batchVerifyV8(chain, txList, blockHeader, blockHeaderStr, preStateRoot);
            } else {
                resultMap = txService.batchVerify(chain, txList, blockHeader, blockHeaderStr, preStateRoot);
            }
            return success(resultMap);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }

    }

    @CmdAnnotation(cmd = TxCmd.TX_SETCONTRACTGENERATETXTYPES, priority = CmdPriority.HIGH, version = 1.0, description = "??????????????????????????????????????????????????????????????????????????????????????????gas???????????????")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "txTypeList", requestType = @TypeDescriptor(value = List.class, collectionElement = Integer.class), parameterDes = "????????????????????????????????????????????????????????????????????????????????????gas???????????????"),
    })
    @ResponseData(description = "????????????????????????????????????????????????")
    public Response setContractGenerateTxTypes(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("txTypeList"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            List<Integer> txTypeList = (List<Integer>) params.get("txTypeList");
            if (txTypeList == null) {
                txTypeList = new ArrayList<>();
            }
            chain.setContractGenerateTxTypes(new HashSet<>(txTypeList));
            chain.getLogger().info("????????????????????????????????????: {}", Arrays.toString(txTypeList.toArray()));
            return success();
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }

    }

    @CmdAnnotation(cmd = TxCmd.TX_CS_STATE, version = 1.0, description = "????????????????????????(?????????????????????)/Set the node packaging state")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "packaging", requestType = @TypeDescriptor(value = boolean.class), parameterDes = "??????????????????")
    })
    @ResponseData(description = "????????????????????????????????????????????????")
    public Response packaging(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            Boolean packaging = null == params.get("packaging") ? null : (Boolean) params.get("packaging");
            if (null == packaging) {
                throw new NulsException(TxErrorCode.PARAMETER_ERROR);
            }
            chain.getPackaging().set(packaging);
            chain.getLogger().debug("Task-Packaging ???????????????????????????,???????????????: {}", chain.getPackaging().get());
            return success();
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }


    @CmdAnnotation(cmd = TxCmd.TX_BL_STATE, version = 1.0, description = "??????????????????????????????(?????????????????????)/Set the node block state")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "status", requestType = @TypeDescriptor(value = int.class), parameterDes = "??????????????????, ???????????????")
    })
    @ResponseData(description = "????????????????????????????????????????????????")
    public Response blockNotice(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            Integer status = (Integer) params.get("status");
            if (null == status) {
                throw new NulsException(TxErrorCode.PARAMETER_ERROR);
            }
            if (1 == status) {
                chain.getProcessTxStatus().set(true);
                chain.getLogger().info("?????????????????????????????????: true");
            } else {
                chain.getProcessTxStatus().set(false);
                chain.getLogger().info("?????????????????????????????????: false");
            }
            return success();
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }

    /**
     * ??????????????????
     *
     * @param params
     * @return
     */
    @CmdAnnotation(cmd = TxCmd.TX_BLOCK_HEIGHT, priority = CmdPriority.HIGH, version = 1.0, description = "????????????????????????/Receive the latest block height")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "height", requestType = @TypeDescriptor(value = long.class), parameterDes = "????????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = boolean.class, description = "????????????")
    }))
    public Response height(Map params) {
        Chain chain = null;
        try {
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            Long height = Long.parseLong(params.get("height").toString());
            chain.setBestBlockHeight(height);
            chain.getLogger().debug("????????????????????????????????????: [{}]" + TxUtil.nextLine() + TxUtil.nextLine(), height);
            Map<String, Object> resultMap = new HashMap<>(TxConstant.INIT_CAPACITY_2);
            resultMap.put("value", true);
            return success(resultMap);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }


    @CmdAnnotation(cmd = "tx_getTxSigners", version = 1.0, description = "??????????????????????????????????????????/Gets the list of signers of the transaction's legal signature")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "txHex", parameterType = "String", parameterDes = "???????????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = List.class, valueElement = String.class, description = "???????????????????????????"),

    }))
    public Object getTxSigners(Map params){
        Chain chain = null;
        try {
            // check parameters
            ObjectUtils.canNotEmpty(params.get("chainId"), TxErrorCode.PARAMETER_ERROR.getMsg());
            ObjectUtils.canNotEmpty(params.get("txHex"), TxErrorCode.PARAMETER_ERROR.getMsg());
            chain = chainManager.getChain((Integer) params.get("chainId"));
            if (null == chain) {
                throw new NulsException(TxErrorCode.CHAIN_NOT_FOUND);
            }
            String txHex = (String)params.get("txHex");
            Transaction tx = TxUtil.getInstance(txHex, Transaction.class);
            TransactionSignature transactionSignature = null;
            if (tx.isMultiSignTx()) {
                transactionSignature = TxUtil.getInstance(tx.getTransactionSignature(), MultiSignTxSignature.class);
            } else {
                transactionSignature = TxUtil.getInstance(tx.getTransactionSignature(), TransactionSignature.class);
            }
            List<P2PHKSignature> p2PHKSignatureList = transactionSignature.getP2PHKSignatures();
            Set<String> signers = new HashSet<>();
            if(null != p2PHKSignatureList && !p2PHKSignatureList.isEmpty()){
                for (P2PHKSignature signature : p2PHKSignatureList) {
                    if (!ECKey.verify(tx.getHash().getBytes(), signature.getSignData().getSignBytes(), signature.getPublicKey())) {
                        throw new NulsException(new Exception("Transaction signature error !"));
                    }else{
                        signers.add(AddressTool.getStringAddressByBytes(AddressTool.getAddress(signature.getPublicKey(), chain.getChainId())));
                    }
                }
            }
            Map<String, Object> map = new HashMap<>(TxConstant.INIT_CAPACITY_2);
            map.put("list", signers);
            return success(map);
        } catch (NulsException e) {
            errorLogProcess(chain, e);
            return failed(e.getErrorCode());
        } catch (Exception e) {
            errorLogProcess(chain, e);
            return failed(TxErrorCode.SYS_UNKOWN_EXCEPTION);
        }
    }


    private void errorLogProcess(Chain chain, Exception e) {
        if (chain == null) {
            LOG.error(e);
        } else {
            chain.getLogger().error(e);
        }
    }

}