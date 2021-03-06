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

package io.nuls.account.service.impl;

import io.nuls.account.config.NulsConfig;
import io.nuls.account.constant.AccountConstant;
import io.nuls.account.constant.AccountErrorCode;
import io.nuls.account.model.NonceBalance;
import io.nuls.account.model.bo.Account;
import io.nuls.account.model.bo.Chain;
import io.nuls.account.model.bo.tx.AliasTransaction;
import io.nuls.account.model.bo.tx.txdata.Alias;
import io.nuls.account.model.dto.CoinDTO;
import io.nuls.account.model.dto.MultiSignTransactionResultDTO;
import io.nuls.account.model.dto.MultiSignTransferDTO;
import io.nuls.account.model.dto.TransferDTO;
import io.nuls.account.model.po.AliasPO;
import io.nuls.account.rpc.call.TransactionCall;
import io.nuls.account.service.AccountService;
import io.nuls.account.service.AliasService;
import io.nuls.account.service.MultiSignAccountService;
import io.nuls.account.service.TransactionService;
import io.nuls.account.storage.AliasStorageService;
import io.nuls.account.util.LoggerUtil;
import io.nuls.account.util.Preconditions;
import io.nuls.account.util.TxUtil;
import io.nuls.account.util.manager.ChainManager;
import io.nuls.account.util.validator.TxValidator;
import io.nuls.base.RPCUtil;
import io.nuls.base.basic.AddressTool;
import io.nuls.base.basic.NulsByteBuffer;
import io.nuls.base.basic.TransactionFeeCalculator;
import io.nuls.base.data.*;
import io.nuls.base.signture.MultiSignTxSignature;
import io.nuls.base.signture.P2PHKSignature;
import io.nuls.base.signture.SignatureUtil;
import io.nuls.base.signture.TransactionSignature;
import io.nuls.core.basic.Result;
import io.nuls.core.constant.BaseConstant;
import io.nuls.core.constant.TxType;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.crypto.ECKey;
import io.nuls.core.crypto.HexUtil;
import io.nuls.core.exception.NulsException;
import io.nuls.core.exception.NulsRuntimeException;
import io.nuls.core.model.BigIntegerUtils;
import io.nuls.core.model.StringUtils;
import io.nuls.core.parse.SerializeUtils;
import io.nuls.core.rpc.util.NulsDateUtils;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author: qinyifeng
 */
@Component
public class TransactionServiceImpl implements TransactionService {

    @Autowired
    private AccountService accountService;
    @Autowired
    private ChainManager chainManager;
    @Autowired
    private AliasService aliasService;
    @Autowired
    private TxValidator txValidator;
    @Autowired
    private MultiSignAccountService multiSignAccountService;
    @Autowired
    private AliasStorageService aliasStorageService;

    @Override
    public Result transferTxValidate(Chain chain, Transaction tx) throws NulsException {
        return txValidator.validate(chain, tx);
    }

    @Override
    public Transaction transfer(Chain chain, TransferDTO transferDTO) throws NulsException {
        int chainId = chain.getChainId();
        List<CoinDTO> fromList = transferDTO.getInputs();
        List<CoinDTO> toList = transferDTO.getOutputs();
        aliasTransferProcess(chainId, fromList, toList);
        for (CoinDTO from : fromList) {
            //from????????????????????????
            if (AddressTool.isMultiSignAddress(from.getAddress())) {
                throw new NulsException(AccountErrorCode.IS_MULTI_SIGNATURE_ADDRESS);
            }
        }
        String remark = transferDTO.getRemark();
//        if (!TxUtil.validTxRemark(remark)) {
//            throw new NulsException(AccountErrorCode.PARAMETER_ERROR);
//        }
        //????????????????????????
        Transaction tx = this.createNormalTransferTx(chain, fromList, toList, remark);
        //?????????????????????
        TransactionCall.newTx(chain, tx);
        return tx;
    }

    @Override
    public MultiSignTransactionResultDTO multiSignTransfer(Chain chain, MultiSignTransferDTO multiSignTransferDTO) throws NulsException {
        int chainId = chain.getChainId();
        List<CoinDTO> fromList = multiSignTransferDTO.inputsConvert();
        List<CoinDTO> toList = multiSignTransferDTO.outputsConvert();
        aliasTransferProcess(chainId, fromList, toList);
        String address = null;
        for (CoinDTO from : fromList) {
            String addr = from.getAddress();
            //from???????????????????????????????????????
            if (!AddressTool.isMultiSignAddress(addr)) {
                throw new NulsException(AccountErrorCode.IS_NOT_MULTI_SIGNATURE_ADDRESS);
            }
            if (address == null) {
                address = addr;
            } else if (!address.equals(addr)) {
                throw new NulsException(AccountErrorCode.ONLY_ONE_MULTI_SIGN_ADDRESS);
            }
        }
        String remark = multiSignTransferDTO.getRemark();
//        if (!TxUtil.validTxRemark(remark)) {
//            throw new NulsException(AccountErrorCode.PARAMETER_ERROR);
//        }
        //??????????????????????????????????????????????????????????????????????????????????????????????????????
        MultiSigAccount multiSigAccount = multiSignAccountService.getMultiSigAccountByAddress(address);
        Preconditions.checkNotNull(multiSigAccount, AccountErrorCode.MULTISIGN_ACCOUNT_NOT_EXIST);
        //?????????????????????
        Transaction tx = assemblyUnsignedTransaction(chain, fromList, toList, remark);
        MultiSignTxSignature transactionSignature = new MultiSignTxSignature();
        transactionSignature.setM(multiSigAccount.getM());
        transactionSignature.setPubKeyList(multiSigAccount.getPubKeyList());
        try {
            tx.setTransactionSignature(transactionSignature.serialize());
        } catch (IOException e) {
            throw new NulsException(AccountErrorCode.SERIALIZE_ERROR);
        }
        boolean isBroadcasted = false;
        //?????????????????????????????????
        if (null != multiSignTransferDTO.getSignAddress() && null != multiSignTransferDTO.getSignPassword()) {
            //??????????????????????????????????????????????????????
            Account account = accountService.getAccount(chainId, multiSignTransferDTO.getSignAddress());
            Preconditions.checkNotNull(account, AccountErrorCode.ACCOUNT_NOT_EXIST);
            //?????????????????????????????????????????????????????????,???????????????????????????????????????????????????
            if (!AddressTool.validSignAddress(multiSigAccount.getPubKeyList(), account.getPubKey())) {
                throw new NulsRuntimeException(AccountErrorCode.SIGN_ADDRESS_NOT_MATCH);
            }
            TransactionSignature txSignature = buildMultiSignTransactionSignature(tx, multiSigAccount, account, multiSignTransferDTO.getSignPassword());
            isBroadcasted = txMutilProcessing(chain, multiSigAccount.getM(), tx, txSignature);
        }
        MultiSignTransactionResultDTO multiSignTransactionResultDto = new MultiSignTransactionResultDTO();
        multiSignTransactionResultDto.setBroadcasted(isBroadcasted);
        multiSignTransactionResultDto.setTransaction(tx);
        return multiSignTransactionResultDto;

    }

    /**
     * ?????????????????????????????????
     *
     * @param chainId
     * @param fromList
     * @param toList
     */
    private void aliasTransferProcess(int chainId, List<CoinDTO> fromList, List<CoinDTO> toList) {
        if (fromList == null || toList == null) {
            throw new NulsRuntimeException(AccountErrorCode.NULL_PARAMETER);
        }
        Function<CoinDTO, CoinDTO> checkAddress = cd -> {
            //??????address ??????????????????????????????
            if (!AddressTool.validAddress(chainId, cd.getAddress())) {
                AliasPO aliasPo = aliasStorageService.getAlias(chainId, cd.getAddress());
                Preconditions.checkNotNull(aliasPo, AccountErrorCode.ALIAS_NOT_EXIST);
                cd.setAddress(AddressTool.getStringAddressByBytes(aliasPo.getAddress()));
            }
            return cd;
        };
        fromList = fromList.stream().map(checkAddress).collect(Collectors.toList());
        toList = toList.stream().map(checkAddress).collect(Collectors.toList());
    }


    @Override
    public MultiSignTransactionResultDTO signMultiSignTransaction(Chain chain, Account account, String password, String txStr)
            throws NulsException {
        //create transaction
        Transaction transaction = new Transaction();
        transaction.parse(new NulsByteBuffer(RPCUtil.decode(txStr)));

        CoinData coinData = new CoinData();
        coinData.parse(new NulsByteBuffer(transaction.getCoinData()));
        List<CoinFrom> list = coinData.getFrom();
        if (list == null) {
            throw new NulsRuntimeException(AccountErrorCode.TX_NOT_EFFECTIVE);
        }
        byte[] address = list.get(0).getAddress();
        MultiSigAccount multiSigAccount = null;
        byte[] txSignatureByte = transaction.getTransactionSignature();

        List<byte[]> accountPubKeyList = null;
        byte accountM = 0;
        if (null == txSignatureByte || txSignatureByte.length == 0) {
            //???????????????????????????????????????????????????????????????
            multiSigAccount = multiSignAccountService.getMultiSigAccountByAddress(AddressTool.getStringAddressByBytes(address));
            if (multiSigAccount == null) {
                throw new NulsRuntimeException(AccountErrorCode.MULTISIGN_ACCOUNT_NOT_EXIST);
            }
            accountM = multiSigAccount.getM();
            accountPubKeyList = multiSigAccount.getPubKeyList();
        } else {
            /**
             * ???????????????????????????, ?????????????????????????????????????????????????????????????????????
             * ????????????????????????????????????????????????????????????????????????from??????????????????
             */
            MultiSignTxSignature transactionSignature = new MultiSignTxSignature();
            transactionSignature.parse(new NulsByteBuffer(transaction.getTransactionSignature()));
            List<String> pubKeys = new ArrayList<>();
            for (byte[] pubkey : transactionSignature.getPubKeyList()) {
                pubKeys.add(HexUtil.encode(pubkey));
            }
            try {
                byte[] hash160 = SerializeUtils.sha256hash160(AddressTool.createMultiSigAccountOriginBytes(chain.getChainId(), transactionSignature.getM(), pubKeys));
                Address multiSignAddress = new Address(chain.getChainId(), BaseConstant.P2SH_ADDRESS_TYPE, hash160);
                if (!Arrays.equals(address, multiSignAddress.getAddressBytes())) {
                    chain.getLogger().error("????????????????????????????????????????????????From??????????????????, The multi-signature address generated by the existing signature data does not match the multi-signature address in the From");
                    throw new NulsException(AccountErrorCode.SIGN_ADDRESS_NOT_MATCH);
                }
            } catch (Exception e) {
                chain.getLogger().error(e);
                throw new NulsException(AccountErrorCode.SIGNATURE_ERROR);
            }
            accountM = transactionSignature.getM();
            accountPubKeyList = transactionSignature.getPubKeyList();
        }

        //????????????????????????????????????????????????
        if (!AddressTool.validSignAddress(accountPubKeyList, account.getPubKey())) {
            throw new NulsRuntimeException(AccountErrorCode.SIGN_ADDRESS_NOT_MATCH);
        }
        TransactionSignature transactionSignature = buildMultiSignTransactionSignature(transaction, multiSigAccount, account, password);
        //process transaction
        boolean isBroadcasted = txMutilProcessing(chain, accountM, transaction, transactionSignature);
        MultiSignTransactionResultDTO multiSignTransactionResultDto = new MultiSignTransactionResultDTO();
        multiSignTransactionResultDto.setBroadcasted(isBroadcasted);
        multiSignTransactionResultDto.setTransaction(transaction);
        return multiSignTransactionResultDto;
    }


    private TransactionSignature buildMultiSignTransactionSignature(Transaction transaction, MultiSigAccount multiSigAccount, Account account, String password) throws NulsException {
        MultiSignTxSignature transactionSignature = new MultiSignTxSignature();
        List<P2PHKSignature> p2PHKSignatures;
        if (transaction.getTransactionSignature() != null) {
            transactionSignature.parse(new NulsByteBuffer(transaction.getTransactionSignature()));
            p2PHKSignatures = transactionSignature.getP2PHKSignatures();
            for (P2PHKSignature p2PHKSignature : p2PHKSignatures) {
                if (Arrays.equals(p2PHKSignature.getPublicKey(), account.getPubKey())) {
                    //??????????????????
                    throw new NulsRuntimeException(AccountErrorCode.ADDRESS_ALREADY_SIGNED);
                }
            }
        } else {
            p2PHKSignatures = new ArrayList<>();
            if (multiSigAccount == null) {
                throw new NulsRuntimeException(AccountErrorCode.MULTISIGN_ACCOUNT_NOT_EXIST);
            }
            transactionSignature.setM(multiSigAccount.getM());
            transactionSignature.setPubKeyList(multiSigAccount.getPubKeyList());
        }
        ECKey eckey = account.getEcKey(password);
        P2PHKSignature p2PHKSignature = SignatureUtil.createSignatureByEckey(transaction, eckey);
        p2PHKSignatures.add(p2PHKSignature);
        transactionSignature.setP2PHKSignatures(p2PHKSignatures);
        try {
            transaction.setTransactionSignature(transactionSignature.serialize());
        } catch (IOException e) {
            throw new NulsException(AccountErrorCode.SERIALIZE_ERROR);
        }
        return transactionSignature;
    }

    /**
     * ????????????????????????
     *
     * @param chain
     * @param address
     * @param aliasName
     * @param signAddr
     * @param password
     * @return
     * @throws NulsException
     */
    @Override
    public MultiSignTransactionResultDTO setMultiSignAccountAlias(Chain chain, String address, String aliasName, String signAddr, String password) throws NulsException {
        MultiSigAccount multiSigAccount = multiSignAccountService.getMultiSigAccountByAddress(address);
        Preconditions.checkNotNull(multiSigAccount, AccountErrorCode.MULTISIGN_ACCOUNT_NOT_EXIST);
        //??????????????????????????????
        Transaction tx = createSetAliasTxWithoutSign(chain, multiSigAccount.getAddress(), aliasName, multiSigAccount.getM());
        MultiSignTxSignature transactionSignature = new MultiSignTxSignature();
        transactionSignature.setM(multiSigAccount.getM());
        transactionSignature.setPubKeyList(multiSigAccount.getPubKeyList());
        try {
            tx.setTransactionSignature(transactionSignature.serialize());
        } catch (IOException e) {
            throw new NulsException(AccountErrorCode.SERIALIZE_ERROR);
        }
        boolean isBroadcasted = false;
        if (null != signAddr && password != null) {
            //???????????????????????????????????????????????????
            Account account = accountService.getAccount(chain.getChainId(), signAddr);
            if (null == account) {
                throw new NulsRuntimeException(AccountErrorCode.ACCOUNT_NOT_EXIST);
            }
            if (account.isEncrypted() && account.isLocked()) {
                if (!account.validatePassword(password)) {
                    throw new NulsRuntimeException(AccountErrorCode.PASSWORD_IS_WRONG);
                }
            }
            TransactionSignature txSignature = buildMultiSignTransactionSignature(tx, multiSigAccount, account, password);
            isBroadcasted = txMutilProcessing(chain, multiSigAccount.getM(), tx, txSignature);
        }
        MultiSignTransactionResultDTO multiSignTxResultDto = new MultiSignTransactionResultDTO();
        multiSignTxResultDto.setBroadcasted(isBroadcasted);
        multiSignTxResultDto.setTransaction(tx);
        return multiSignTxResultDto;
    }

    /**
     * ???????????????????????????????????????????????????(?????????????????????)
     *
     * @param chain
     * @param address
     * @param aliasName
     * @return
     * @throws NulsException
     */
    @Override
    public Transaction createSetAliasTxWithoutSign(Chain chain, Address address, String aliasName) throws NulsException {
        return createSetAliasTxWithoutSign(chain, address, aliasName, 1);
    }

    /**
     * ???????????????????????????????????????????????????(?????????????????????)
     *
     * @param chain
     * @param address
     * @param aliasName
     * @param msign     ???????????????????????????
     * @return
     * @throws NulsException
     */
    @Override
    public Transaction createSetAliasTxWithoutSign(Chain chain, Address address, String aliasName, int msign) throws NulsException {
        byte[] addressByte = address.getAddressBytes();
        AliasTransaction tx = new AliasTransaction();
        tx.setTime(NulsDateUtils.getCurrentTimeSeconds());
        //tx.setRemark(StringUtils.bytes(null));//??????????????????
        Alias alias = new Alias(addressByte, aliasName);
        try {
            tx.setTxData(alias.serialize());
        } catch (IOException e) {
            throw new NulsException(AccountErrorCode.SERIALIZE_ERROR);
        }
        int assetChainId = chain.getChainId();
        int assetId = chain.getConfig().getAssetId();
        //??????????????????nonce???
        NonceBalance nonceBalance = TxUtil.getBalanceNonce(chain, assetChainId, assetId, addressByte);
        byte[] nonce = nonceBalance.getNonce();
        CoinFrom coinFrom = new CoinFrom(addressByte, assetChainId, assetId, AccountConstant.ALIAS_FEE, nonce, AccountConstant.NORMAL_TX_LOCKED);
        //????????????
        byte[] blackHoleAddress = AddressTool.getAddress(NulsConfig.BLACK_HOLE_PUB_KEY, assetChainId);
        CoinTo coinTo = new CoinTo(blackHoleAddress, assetChainId, assetId, AccountConstant.ALIAS_FEE);
        int txSize = tx.size() + coinFrom.size() + coinTo.size() + msign * P2PHKSignature.SERIALIZE_LENGTH;
        //???????????????
        BigInteger fee = TransactionFeeCalculator.getNormalTxFee(txSize);
        //????????????
        BigInteger totalAmount = AccountConstant.ALIAS_FEE.add(fee);
        coinFrom.setAmount(totalAmount);
        //????????????????????????
        BigInteger mainAsset = nonceBalance.getAvailable();
        //????????????
        if (BigIntegerUtils.isLessThan(mainAsset, totalAmount)) {
            throw new NulsRuntimeException(AccountErrorCode.INSUFFICIENT_FEE);
        }
        CoinData coinData = new CoinData();
        coinData.setFrom(Arrays.asList(coinFrom));
        coinData.setTo(Arrays.asList(coinTo));
        try {
            tx.setCoinData(coinData.serialize());
            tx.setHash(NulsHash.calcHash(tx.serializeForHash()));
        } catch (IOException e) {
            throw new NulsException(AccountErrorCode.SERIALIZE_ERROR);
        }
        return tx;
    }

    /**
     * ?????????????????????????????????, ??????????????????
     *
     * @param chain
     * @param fromList
     * @param toList
     * @param remark
     * @return
     * @throws NulsException
     */
    private Transaction createNormalTransferTx(Chain chain, List<CoinDTO> fromList, List<CoinDTO> toList, String remark) throws NulsException {
        Transaction tx = assemblyUnsignedTransaction(chain, fromList, toList, remark);
        //??????ECKey????????????
        List<ECKey> signEcKeys = new ArrayList<>();
        Set<String> addrs = new HashSet<>();
        for (CoinDTO from : fromList) {
            if (!addrs.add(from.getAddress())) {
                //????????????????????????????????????
                break;
            }
            //????????????????????????
            Account account = accountService.getAccount(chain.getChainId(), from.getAddress());
            if (null == account) {
                throw new NulsRuntimeException(AccountErrorCode.ACCOUNT_NOT_EXIST);
            }
            ECKey ecKey = account.getEcKey(from.getPassword());
            signEcKeys.add(ecKey);
        }
        try {
            //????????????
            SignatureUtil.createTransactionSignture(tx, signEcKeys);
        } catch (IOException e) {
            LoggerUtil.LOG.error("assemblyTransaction io exception.", e);
            throw new NulsException(AccountErrorCode.SERIALIZE_ERROR);
        }
        return tx;
    }


    /**
     * ??????????????????????????????
     *
     * @param chain
     * @param fromList
     * @param toList
     * @param remark
     * @return
     * @throws NulsException
     */
    private Transaction assemblyUnsignedTransaction(Chain chain, List<CoinDTO> fromList, List<CoinDTO> toList, String remark) throws NulsException {
        Transaction tx = new Transaction(TxType.TRANSFER);
        tx.setTime(NulsDateUtils.getCurrentTimeSeconds());
        tx.setRemark(StringUtils.bytes(remark));
        //??????CoinData??????coinFrom???coinTo??????
        assemblyCoinData(tx, chain, fromList, toList);
        //??????????????????????????????
        try {
            tx.setHash(NulsHash.calcHash(tx.serializeForHash()));
        } catch (IOException e) {
            throw new NulsException(AccountErrorCode.SERIALIZE_ERROR);
        }
        return tx;
    }

    /**
     * ??????CoinData??????
     * assembly coinFrom data
     *
     * @param tx
     * @param chain
     * @param fromList
     * @param toList
     * @return
     */
    private Transaction assemblyCoinData(Transaction tx, Chain chain, List<CoinDTO> fromList, List<CoinDTO> toList) throws NulsException {
        //??????coinFrom???coinTo??????
        List<CoinFrom> coinFromList = assemblyCoinFrom(chain, fromList);
        List<CoinTo> coinToList = assemblyCoinTo(chain, toList);
        //?????????????????????????????????
        if (coinFromList.size() == 0 || coinToList.size() == 0) {
            LoggerUtil.LOG.warn("assemblyCoinData coinData params error");
            throw new NulsRuntimeException(AccountErrorCode.COINDATA_IS_INCOMPLETE);
        }
        //???????????????=??????????????????+??????????????????
        int txSize = tx.size() + getSignatureSize(coinFromList);
        //??????coinData??????
        CoinData coinData = getCoinData(chain, coinFromList, coinToList, txSize);
        try {
            tx.setCoinData(coinData.serialize());
        } catch (IOException e) {
            throw new NulsException(AccountErrorCode.SERIALIZE_ERROR);
        }
        return tx;
    }

    /**
     * ??????coinFrom??????
     * assembly coinFrom data
     *
     * @param listFrom Initiator set coinFrom
     * @return List<CoinFrom>
     * @throws NulsException
     */
    private List<CoinFrom> assemblyCoinFrom(Chain chain, List<CoinDTO> listFrom) throws NulsException {
        int chainId = chain.getChainId();
        List<CoinFrom> coinFroms = new ArrayList<>();
        for (CoinDTO coinDto : listFrom) {
            String address = coinDto.getAddress();
            byte[] addressByte = AddressTool.getAddress(address);
            //?????????????????????????????????????????????
            if (!AddressTool.validAddress(chainId, address)) {
                chain.getLogger().error("assemblyCoinFrom address error");
                throw new NulsException(AccountErrorCode.IS_NOT_CURRENT_CHAIN_ADDRESS);
            }
            //??????????????????????????????????????????
            if (TxUtil.isLegalContractAddress(addressByte, chain)) {
                chain.getLogger().error("Tx from cannot have contract address ");
                throw new NulsException(AccountErrorCode.COINDATA_CANNOT_HAS_CONTRACT_ADDRESS);
            }
            int assetChainId = coinDto.getAssetsChainId();
            int assetId = coinDto.getAssetsId();
            //????????????????????????????????????
            BigInteger amount = coinDto.getAmount();
            if (BigIntegerUtils.isLessThan(amount, BigInteger.ZERO)) {
                chain.getLogger().error("assemblyCoinFrom amount too small");
                throw new NulsException(AccountErrorCode.AMOUNT_TOO_SMALL);
            }
            NonceBalance nonceBalance = TxUtil.getBalanceNonce(chain, assetChainId, assetId, addressByte);
            BigInteger balance = nonceBalance.getAvailable();
            if (BigIntegerUtils.isLessThan(balance, amount)) {
                chain.getLogger().error("assemblyCoinFrom insufficient amount");
                throw new NulsException(AccountErrorCode.INSUFFICIENT_BALANCE);
            }
            //??????????????????nonce???
            byte[] nonce = nonceBalance.getNonce();
            CoinFrom coinFrom = new CoinFrom(addressByte, assetChainId, assetId, amount, nonce, AccountConstant.NORMAL_TX_LOCKED);
            coinFroms.add(coinFrom);
        }
        return coinFroms;
    }

    /**
     * ??????coinTo??????
     * assembly coinTo data
     * ?????????to?????????????????????????????????????????????
     *
     * @param listTo Initiator set coinTo
     * @return List<CoinTo>
     * @throws NulsException
     */
    private List<CoinTo> assemblyCoinTo(Chain chain, List<CoinDTO> listTo) throws NulsException {
        int chainId = chain.getChainId();
        List<CoinTo> coinTos = new ArrayList<>();
        for (CoinDTO coinDto : listTo) {
            String address = coinDto.getAddress();
            byte[] addressByte = AddressTool.getAddress(address);
            //?????????????????????????????????????????????
            if (!AddressTool.validAddress(chainId, address)) {
                chain.getLogger().error("assemblyCoinFrom address error");
                throw new NulsException(AccountErrorCode.IS_NOT_CURRENT_CHAIN_ADDRESS);
            }
            //??????????????????????????????????????????
            if (TxUtil.isLegalContractAddress(addressByte, chain)) {
                chain.getLogger().error("Tx to cannot have contract address ");
                throw new NulsException(AccountErrorCode.COINDATA_CANNOT_HAS_CONTRACT_ADDRESS);
            }
            int assetsChainId = coinDto.getAssetsChainId();
            int assetId = coinDto.getAssetsId();
            //????????????????????????0
            BigInteger amount = coinDto.getAmount();
            if (BigIntegerUtils.isLessThan(amount, BigInteger.ZERO)) {
                chain.getLogger().error("assemblyCoinTo amount too small");
                throw new NulsException(AccountErrorCode.AMOUNT_TOO_SMALL);
            }
            CoinTo coinTo = new CoinTo();
            coinTo.setAddress(addressByte);
            coinTo.setAssetsChainId(assetsChainId);
            coinTo.setAssetsId(assetId);
            coinTo.setAmount(coinDto.getAmount());
            coinTo.setLockTime(coinDto.getLockTime());
            coinTos.add(coinTo);
        }
        return coinTos;
    }


    /**
     * ??????coinData??????
     * assembly coinData
     *
     * @param listFrom
     * @param listTo
     * @param txSize
     * @return
     * @throws NulsException
     */
    private CoinData getCoinData(Chain chain, List<CoinFrom> listFrom, List<CoinTo> listTo, int txSize) throws NulsException {
        //???????????????
        BigInteger feeTotalFrom = BigInteger.ZERO;
        for (CoinFrom coinFrom : listFrom) {
            txSize += coinFrom.size();
            if (TxUtil.isMainAsset(chain, coinFrom.getAssetsChainId(), coinFrom.getAssetsId())) {
                feeTotalFrom = feeTotalFrom.add(coinFrom.getAmount());
            }
        }
        //???????????????
        BigInteger feeTotalTo = BigInteger.ZERO;
        for (CoinTo coinTo : listTo) {
            txSize += coinTo.size();
            if (TxUtil.isMainAsset(chain, coinTo.getAssetsChainId(), coinTo.getAssetsId())) {
                feeTotalTo = feeTotalTo.add(coinTo.getAmount());
            }
        }
        //?????????????????????????????????
        BigInteger targetFee = TransactionFeeCalculator.getNormalTxFee(txSize);
        //????????????????????????, ??????????????????????????????
        BigInteger actualFee = feeTotalFrom.subtract(feeTotalTo);
        if (BigIntegerUtils.isLessThan(actualFee, BigInteger.ZERO)) {
            chain.getLogger().error("insufficient fee");
            //??????from????????????????????????????????????????????????to?????????????????????????????????
            throw new NulsException(AccountErrorCode.INSUFFICIENT_FEE);
        } else if (BigIntegerUtils.isLessThan(actualFee, targetFee)) {
            //????????????????????????????????????coinfrom??????????????????
            actualFee = getFeeDirect(chain, listFrom, targetFee, actualFee);
            if (BigIntegerUtils.isLessThan(actualFee, targetFee)) {
                //??????????????????????????????????????????CoinFrom????????????????????????????????????coin?????????????????????????????????????????????????????????coinfrom??????????????????
                if (!getFeeIndirect(chain, listFrom, txSize, targetFee, actualFee)) {
                    chain.getLogger().error("insufficient fee");
                    //??????from??????????????????????????????????????????????????????????????????
                    throw new NulsException(AccountErrorCode.INSUFFICIENT_FEE);
                }
            }
        }
        CoinData coinData = new CoinData();
        coinData.setFrom(listFrom);
        coinData.setTo(listTo);
        return coinData;
    }

    /**
     * Only the fee is charged from the coin in CoinFrom for the current chain main asset, and the actual amount is returned.
     * ??????CoinFrom?????????????????????????????????coin????????????????????????????????????????????????
     *
     * @param listFrom  All coins transferred out ???????????????coin
     * @param targetFee The amount of the fee that needs to be charged ??????????????????????????????
     * @param actualFee Actual amount charged ?????????????????????
     * @return BigInteger The amount of the fee actually charged ??????????????????????????????
     * @throws NulsException
     */
    private BigInteger getFeeDirect(Chain chain, List<CoinFrom> listFrom, BigInteger targetFee, BigInteger actualFee) throws NulsException {
        for (CoinFrom coinFrom : listFrom) {
            //???????????????????????????
            if (TxUtil.isChainAssetExist(chain, coinFrom)) {
                NonceBalance nonceBalance = TxUtil.getBalanceNonce(chain, coinFrom.getAssetsChainId(), coinFrom.getAssetsId(), coinFrom.getAddress());
                BigInteger mainAsset = nonceBalance.getAvailable();
                //????????????=??????????????????????????????
                mainAsset = mainAsset.subtract(coinFrom.getAmount());
                //????????????????????????
                BigInteger current = targetFee.subtract(actualFee);
                //????????????????????????????????????????????????????????????????????????
                if (BigIntegerUtils.isEqualOrGreaterThan(mainAsset, current)) {
                    coinFrom.setAmount(coinFrom.getAmount().add(current));
                    actualFee = actualFee.add(current);
                    break;
                } else if (BigIntegerUtils.isGreaterThan(mainAsset, BigInteger.ZERO)) {
                    coinFrom.setAmount(coinFrom.getAmount().add(mainAsset));
                    actualFee = actualFee.add(mainAsset);
                    continue;
                }
            }
        }
        return actualFee;
    }

    /**
     * ???CoinFrom????????????????????????????????????coin???????????????????????????????????????????????????????????????
     * From the coin in CoinFrom, the current chain main asset handling fee is not collected in the coin of the current chain main asset, and the return is collected.
     *
     * @param listFrom  All coins transferred out ???????????????coin
     * @param txSize    Current transaction size
     * @param targetFee Estimated fee
     * @param actualFee actual Fee
     * @return boolean
     * @throws NulsException
     */
    private boolean getFeeIndirect(Chain chain, List<CoinFrom> listFrom, int txSize, BigInteger targetFee, BigInteger actualFee) throws NulsException {
        ListIterator<CoinFrom> iterator = listFrom.listIterator();
        out:
        while (iterator.hasNext()) {
            CoinFrom coinFrom = iterator.next();
            //??????????????????????????????
            if (!TxUtil.isChainAssetExist(chain, coinFrom)) {
                //??????FROM????????????????????????????????????coin, ??????????????????????????????????????????????????????.
                for (CoinFrom coin : listFrom) {
                    if (Arrays.equals(coin.getAddress(), coinFrom.getAddress())
                            && TxUtil.isChainAssetExist(chain, coin)) {
                        continue out;
                    }
                }
                int assetsChainId = chain.getConfig().getChainId();
                int assetsId = chain.getConfig().getAssetId();
                //?????????????????????????????????????????????
                NonceBalance nonceBalance = TxUtil.getBalanceNonce(chain, assetsChainId, assetsId, coinFrom.getAddress());
                BigInteger mainAsset = nonceBalance.getAvailable();
                if (BigIntegerUtils.isEqualOrLessThan(mainAsset, BigInteger.ZERO)) {
                    continue;
                }
                //?????????????????????CoinFrom
                CoinFrom feeCoinFrom = new CoinFrom();
                byte[] address = coinFrom.getAddress();
                feeCoinFrom.setAddress(address);
                feeCoinFrom.setNonce(nonceBalance.getNonce());
                txSize += feeCoinFrom.size();
                //????????????CoinFrom??????????????????????????????????????????????????????
                targetFee = TransactionFeeCalculator.getNormalTxFee(txSize);
                //????????????????????????
                BigInteger current = targetFee.subtract(actualFee);
                //?????????????????????????????????
                BigInteger fee = BigIntegerUtils.isEqualOrGreaterThan(mainAsset, current) ? current : mainAsset;
                feeCoinFrom.setLocked(AccountConstant.NORMAL_TX_LOCKED);
                feeCoinFrom.setAssetsChainId(assetsChainId);
                feeCoinFrom.setAssetsId(assetsId);
                feeCoinFrom.setAmount(fee);

                iterator.add(feeCoinFrom);
                actualFee = actualFee.add(fee);
                if (BigIntegerUtils.isEqualOrGreaterThan(actualFee, targetFee)) {
                    break;
                }
            }
        }
        //?????????????????????????????????????????????????????????????????????????????????CoinData
        if (BigIntegerUtils.isEqualOrGreaterThan(actualFee, targetFee)) {
            return true;
        }
        return false;
    }

    /**
     * ??????coinfrom?????????????????????size
     * ??????coinfrom?????????????????????????????????????????????????????????????????????m????????????size
     *
     * @param coinFroms
     * @return
     */
    private int getSignatureSize(List<CoinFrom> coinFroms) {
        int size = 0;
        Set<String> commonAddress = new HashSet<>();
        for (CoinFrom coinFrom : coinFroms) {
            String address = AddressTool.getStringAddressByBytes(coinFrom.getAddress());
            if (AddressTool.isMultiSignAddress(coinFrom.getAddress())) {
                //????????????,????????????from, ????????????from????????????????????????????????????,??????????????????????????????
                MultiSigAccount multiSigAccount = multiSignAccountService.getMultiSigAccountByAddress(address);
                size += multiSigAccount.getPubKeyList().size() * P2PHKSignature.SERIALIZE_LENGTH;
                return size;
            } else {
                commonAddress.add(address);
            }
        }
        size += commonAddress.size() * P2PHKSignature.SERIALIZE_LENGTH;
        return size;
    }

    /**
     * ?????????????????????????????????????????????????????????size
     *
     * @param signNumber m
     * @return int
     */
    private int getMultiSignAddressSignatureSize(int signNumber) {
        int size = signNumber * P2PHKSignature.SERIALIZE_LENGTH;
        return size;
    }

    /**
     * ??????????????????
     * ??????????????????????????????????????????????????????????????????
     **/
    //@Override
    public boolean txMutilProcessing(Chain chain, byte m, Transaction tx, TransactionSignature txSignature) throws NulsException {
        //?????????????????????M????????????????????????
        if (m == txSignature.getP2PHKSignatures().size()) {
            TransactionCall.newTx(chain, tx);
            return true;
        }
        return false;
    }

}
