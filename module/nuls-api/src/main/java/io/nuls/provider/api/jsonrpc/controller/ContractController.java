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

package io.nuls.provider.api.jsonrpc.controller;

import io.nuls.base.api.provider.Result;
import io.nuls.base.api.provider.ServiceManager;
import io.nuls.base.api.provider.contract.ContractProvider;
import io.nuls.base.api.provider.contract.facade.CreateContractReq;
import io.nuls.base.api.provider.contract.facade.DeleteContractReq;
import io.nuls.base.api.provider.contract.facade.TokenTransferReq;
import io.nuls.base.api.provider.contract.facade.TransferToContractReq;
import io.nuls.base.basic.AddressTool;
import io.nuls.core.constant.CommonCodeConstanst;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Controller;
import io.nuls.core.core.annotation.RpcMethod;
import io.nuls.core.model.FormatValidUtils;
import io.nuls.core.model.StringUtils;
import io.nuls.core.rpc.model.*;
import io.nuls.provider.api.config.Config;
import io.nuls.provider.api.config.Context;
import io.nuls.provider.model.dto.*;
import io.nuls.provider.model.jsonrpc.RpcErrorCode;
import io.nuls.provider.model.jsonrpc.RpcResult;
import io.nuls.provider.model.jsonrpc.RpcResultError;
import io.nuls.provider.rpctools.ContractTools;
import io.nuls.provider.utils.Log;
import io.nuls.provider.utils.ResultUtil;
import io.nuls.provider.utils.Utils;
import io.nuls.provider.utils.VerifyUtils;
import io.nuls.v2.model.annotation.Api;
import io.nuls.v2.model.annotation.ApiOperation;
import io.nuls.v2.model.annotation.ApiType;
import io.nuls.v2.util.ContractUtil;
import io.nuls.v2.util.NulsSDKTool;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: PierreLuo
 * @date: 2019-07-01
 */
@Controller
@Api(type = ApiType.JSONRPC)
public class ContractController {

    ContractProvider contractProvider = ServiceManager.get(ContractProvider.class);
    @Autowired
    Config config;
    @Autowired
    private ContractTools contractTools;


    @RpcMethod("contractCreate")
    @ApiOperation(description = "????????????", order = 401)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "sender",  parameterDes = "???????????????????????????"),
            @Parameter(parameterName = "password",  parameterDes = "????????????"),
            @Parameter(parameterName = "alias",  parameterDes = "????????????"),
            @Parameter(parameterName = "gasLimit", requestType = @TypeDescriptor(value = long.class), parameterDes = "GAS??????"),
            @Parameter(parameterName = "price", requestType = @TypeDescriptor(value = long.class), parameterDes = "GAS??????"),
            @Parameter(parameterName = "contractCode",  parameterDes = "??????????????????(????????????Hex???????????????)"),
            @Parameter(parameterName = "args", requestType = @TypeDescriptor(value = Object[].class), parameterDes = "????????????", canNull = true),
            @Parameter(parameterName = "remark",  parameterDes = "????????????", canNull = true)
    })
    @ResponseData(name = "?????????", description = "????????????Map???????????????????????????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "txHash", description = "?????????????????????hash"),
            @Key(name = "contractAddress", description = "?????????????????????")
    }))
    public RpcResult contractCreate(List<Object> params) {
        VerifyUtils.verifyParams(params, 9);
        try {
            int i = 0;
            Integer chainId = (Integer) params.get(i++);
            String sender = (String) params.get(i++);
            String password = (String) params.get(i++);
            String alias = (String) params.get(i++);
            Long gasLimit = Long.parseLong(params.get(i++).toString());
            Long price = Long.parseLong(params.get(i++).toString());
            String contractCode = (String) params.get(i++);
            List argsList = (List) params.get(i++);
            Object[] args = argsList != null ? argsList.toArray() : null;
            String remark = (String) params.get(i++);

            if (!Context.isChainExist(chainId)) {
                return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
            }
            if (gasLimit < 0) {
                return RpcResult.paramError(String.format("gasLimit [%s] is invalid", gasLimit));
            }
            if (price < 0) {
                return RpcResult.paramError(String.format("price [%s] is invalid", price));
            }

            if (!AddressTool.validAddress(chainId, sender)) {
                return RpcResult.paramError(String.format("sender [%s] is invalid", sender));
            }

            if(!FormatValidUtils.validAlias(alias)) {
                return RpcResult.paramError(String.format("alias [%s] is invalid", alias));
            }

            if (StringUtils.isBlank(contractCode)) {
                return RpcResult.paramError("contractCode is empty");
            }
            CreateContractReq req = new CreateContractReq();
            req.setChainId(config.getChainId());
            req.setSender(sender);
            req.setPassword(password);
            req.setPrice(price);
            req.setGasLimit(gasLimit);
            req.setContractCode(contractCode);
            req.setAlias(alias);
            req.setArgs(args);
            req.setRemark(remark);
            Result<Map> result = contractProvider.createContract(req);
            return ResultUtil.getJsonRpcResult(result);
        } catch (Exception e) {
            Log.error(e);
            return RpcResult.failed(CommonCodeConstanst.DATA_ERROR, e.getMessage());
        }
    }

    @RpcMethod("contractCall")
    @ApiOperation(description = "????????????", order = 402)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "sender",  parameterDes = "???????????????????????????"),
            @Parameter(parameterName = "password",  parameterDes = "?????????????????????"),
            @Parameter(parameterName = "value", requestType = @TypeDescriptor(value = BigInteger.class), parameterDes = "???????????????????????????????????????????????????????????????????????????BigInteger.ZERO"),
            @Parameter(parameterName = "gasLimit", requestType = @TypeDescriptor(value = long.class), parameterDes = "GAS??????"),
            @Parameter(parameterName = "price", requestType = @TypeDescriptor(value = long.class), parameterDes = "GAS??????"),
            @Parameter(parameterName = "contractAddress",  parameterDes = "????????????"),
            @Parameter(parameterName = "methodName",  parameterDes = "????????????"),
            @Parameter(parameterName = "methodDesc",  parameterDes = "??????????????????????????????????????????????????????????????????????????????", canNull = true),
            @Parameter(parameterName = "args", requestType = @TypeDescriptor(value = Object[].class), parameterDes = "????????????", canNull = true),
            @Parameter(parameterName = "remark",  parameterDes = "????????????", canNull = true),
            @Parameter(parameterName = "multyAssetValues", requestType = @TypeDescriptor(value = String[][].class), parameterDes = "???????????????????????????????????????????????????????????????????????????????????????: [[\\<value\\>,\\<assetChainId\\>,\\<assetId\\>]]", canNull = true)
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "txHash", description = "?????????????????????hash")
    }))
    public RpcResult contractCall(List<Object> params) {
        VerifyUtils.verifyParams(params, 11);
        try {
            int i = 0;
            Integer chainId = (Integer) params.get(i++);
            String sender = (String) params.get(i++);
            String password = (String) params.get(i++);
            Object valueObj = params.get(i++);
            if(valueObj == null) {
                valueObj = "0";
            }
            BigInteger value = new BigInteger(valueObj.toString());
            if (value.compareTo(BigInteger.ZERO) < 0) {
                return RpcResult.paramError(String.format("value [%s] is invalid", value.toString()));
            }
            Long gasLimit = Long.parseLong(params.get(i++).toString());
            Long price = Long.parseLong(params.get(i++).toString());
            String contractAddress = (String) params.get(i++);
            String methodName = (String) params.get(i++);
            String methodDesc = (String) params.get(i++);
            List argsList = (List) params.get(i++);
            Object[] args = argsList != null ? argsList.toArray() : null;
            String remark = (String) params.get(i++);

            if (!Context.isChainExist(chainId)) {
                return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
            }
            if (gasLimit < 0) {
                return RpcResult.paramError(String.format("gasLimit [%s] is invalid", gasLimit));
            }
            if (price < 0) {
                return RpcResult.paramError(String.format("price [%s] is invalid", price));
            }
            if (!AddressTool.validAddress(chainId, sender)) {
                return RpcResult.paramError(String.format("sender [%s] is invalid", sender));
            }
            if (!AddressTool.validAddress(chainId, contractAddress)) {
                return RpcResult.paramError(String.format("contractAddress [%s] is invalid", contractAddress));
            }
            if (StringUtils.isBlank(methodName)) {
                return RpcResult.paramError("methodName is empty");
            }

            /*CallContractReq req = new CallContractReq();
            req.setChainId(config.getChainId());
            req.setSender(sender);
            req.setPassword(password);
            req.setPrice(price);
            req.setGasLimit(gasLimit);
            req.setValue(value.longValue());
            req.setMethodName(methodName);
            req.setMethodDesc(methodDesc);
            req.setContractAddress(contractAddress);
            req.setArgs(args);
            req.setRemark(remark);
            Result<String> result = contractProvider.callContract(req);
            RpcResult rpcResult = ResultUtil.getJsonRpcResult(result);
            if(rpcResult.getError() == null) {
                Map dataMap = new HashMap();
                dataMap.put("txHash", rpcResult.getResult());
                rpcResult.setResult(dataMap);
            }
            return rpcResult;*/
            Object multyAssetValues = null;
            if (params.size() > 11) {
                multyAssetValues = params.get(11);
            }
            Result<Map> mapResult = contractTools.contractCall(chainId,
                    sender,
                    password,
                    value,
                    gasLimit,
                    price,
                    contractAddress,
                    methodName,
                    methodDesc,
                    args,
                    remark,
                    multyAssetValues
            );
            return ResultUtil.getJsonRpcResult(mapResult);
        } catch (Exception e) {
            Log.error(e);
            return RpcResult.failed(CommonCodeConstanst.DATA_ERROR, e.getMessage());
        }
    }


    @RpcMethod("contractDelete")
    @ApiOperation(description = "????????????", order = 403)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "sender", parameterDes = "???????????????????????????"),
            @Parameter(parameterName = "password", parameterDes = "??????????????????"),
            @Parameter(parameterName = "contractAddress", parameterDes = "????????????"),
            @Parameter(parameterName = "remark", parameterDes = "????????????", canNull = true)
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "txHash", description = "?????????????????????hash")
    }))
    public RpcResult contractDelete(List<Object> params) {
        VerifyUtils.verifyParams(params, 5);
        try {
            int i = 0;
            Integer chainId = (Integer) params.get(i++);
            String sender = (String) params.get(i++);
            String password = (String) params.get(i++);
            String contractAddress = (String) params.get(i++);
            String remark = (String) params.get(i++);
            if (!Context.isChainExist(chainId)) {
                return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
            }
            if (!AddressTool.validAddress(chainId, sender)) {
                return RpcResult.paramError(String.format("sender [%s] is invalid", sender));
            }
            if (!AddressTool.validAddress(chainId, contractAddress)) {
                return RpcResult.paramError(String.format("contractAddress [%s] is invalid", contractAddress));
            }
            DeleteContractReq req = new DeleteContractReq(sender, contractAddress, password);
            req.setChainId(config.getChainId());
            req.setRemark(remark);
            Result<String> result = contractProvider.deleteContract(req);
            RpcResult rpcResult = ResultUtil.getJsonRpcResult(result);
            if(rpcResult.getError() == null) {
                Map dataMap = new HashMap();
                dataMap.put("txHash", rpcResult.getResult());
                rpcResult.setResult(dataMap);
            }
            return rpcResult;
        } catch (Exception e) {
            Log.error(e);
            return RpcResult.failed(CommonCodeConstanst.DATA_ERROR, e.getMessage());
        }
    }


    @RpcMethod("tokentransfer")
    @ApiOperation(description = "??????token??????", order = 404)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "fromAddress", parameterDes = "?????????????????????"),
            @Parameter(parameterName = "password", parameterDes = "?????????????????????"),
            @Parameter(parameterName = "toAddress", parameterDes = "?????????????????????"),
            @Parameter(parameterName = "contractAddress", parameterDes = "token????????????"),
            @Parameter(parameterName = "amount", requestType = @TypeDescriptor(value = BigInteger.class), parameterDes = "?????????token????????????"),
            @Parameter(parameterName = "remark",  parameterDes = "????????????", canNull = true)
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "txHash", description = "??????hash")
    }))
    public RpcResult tokentransfer(List<Object> params) {
        VerifyUtils.verifyParams(params, 7);
        try {
            int i = 0;
            Integer chainId = (Integer) params.get(i++);
            String fromAddress = (String) params.get(i++);
            String password = (String) params.get(i++);
            String toAddress = (String) params.get(i++);
            String contractAddress = (String) params.get(i++);
            Object amountObj = params.get(i++);
            if(amountObj == null) {
                return RpcResult.paramError("amount is empty");
            }
            BigInteger amount = new BigInteger(amountObj.toString());
            if (amount.compareTo(BigInteger.ZERO) < 0) {
                return RpcResult.paramError(String.format("amount [%s] is invalid", amount.toString()));
            }
            String remark = (String) params.get(i++);

            if (!Context.isChainExist(chainId)) {
                return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
            }
            if (!AddressTool.validAddress(chainId, fromAddress)) {
                return RpcResult.paramError(String.format("fromAddress [%s] is invalid", fromAddress));
            }
            if (!AddressTool.validAddress(chainId, toAddress)) {
                return RpcResult.paramError(String.format("toAddress [%s] is invalid", toAddress));
            }
            if (!AddressTool.validAddress(chainId, contractAddress)) {
                return RpcResult.paramError(String.format("contractAddress [%s] is invalid", contractAddress));
            }

            TokenTransferReq req = new TokenTransferReq();
            req.setChainId(config.getChainId());
            req.setAddress(fromAddress);
            req.setPassword(password);
            req.setToAddress(toAddress);
            req.setContractAddress(contractAddress);
            req.setAmount(amount.toString());
            req.setRemark(remark);
            Result<String> result = contractProvider.tokenTransfer(req);
            RpcResult rpcResult = ResultUtil.getJsonRpcResult(result);
            if(rpcResult.getError() == null) {
                Map dataMap = new HashMap();
                dataMap.put("txHash", rpcResult.getResult());
                rpcResult.setResult(dataMap);
            }
            return rpcResult;
        } catch (Exception e) {
            Log.error(e);
            return RpcResult.failed(CommonCodeConstanst.DATA_ERROR, e.getMessage());
        }
    }

    @RpcMethod("transfer2contract")
    @ApiOperation(description = "????????????????????????????????????(????????????)???????????????", order = 405)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "fromAddress", parameterDes = "?????????????????????"),
            @Parameter(parameterName = "password", parameterDes = "?????????????????????"),
            @Parameter(parameterName = "toAddress", parameterDes = "?????????????????????"),
            @Parameter(parameterName = "amount", requestType = @TypeDescriptor(value = BigInteger.class), parameterDes = "???????????????????????????"),
            @Parameter(parameterName = "remark",  parameterDes = "????????????", canNull = true)
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "txHash", description = "??????hash")
    }))
    public RpcResult transfer2contract(List<Object> params) {
        VerifyUtils.verifyParams(params, 6);
        try {
            int i = 0;
            Integer chainId = (Integer) params.get(i++);
            if (!Context.isChainExist(chainId)) {
                return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
            }
            String fromAddress = (String) params.get(i++);
            String password = (String) params.get(i++);
            String toAddress = (String) params.get(i++);
            Object amountObj = params.get(i++);
            if(amountObj == null) {
                return RpcResult.paramError("amount is empty");
            }
            BigInteger amount = new BigInteger(amountObj.toString());
            if (amount.compareTo(BigInteger.ZERO) < 0) {
                return RpcResult.paramError(String.format("amount [%s] is invalid", amount.toString()));
            }
            String remark = (String) params.get(i++);

            if (!AddressTool.validAddress(chainId, fromAddress)) {
                return RpcResult.paramError(String.format("fromAddress [%s] is invalid", fromAddress));
            }
            if (!AddressTool.validAddress(chainId, toAddress)) {
                return RpcResult.paramError(String.format("toAddress [%s] is invalid", toAddress));
            }

            TransferToContractReq req = new TransferToContractReq(
                    fromAddress,
                    toAddress,
                    amount,
                    password,
                    remark);
            req.setChainId(config.getChainId());
            Result<String> result = contractProvider.transferToContract(req);
            RpcResult rpcResult = ResultUtil.getJsonRpcResult(result);
            if(rpcResult.getError() == null) {
                Map dataMap = new HashMap();
                dataMap.put("txHash", rpcResult.getResult());
                rpcResult.setResult(dataMap);
            }
            return rpcResult;
        } catch (Exception e) {
            Log.error(e);
            return RpcResult.failed(CommonCodeConstanst.DATA_ERROR, e.getMessage());
        }
    }


    @RpcMethod("getTokenBalance")
    @ApiOperation(description = "????????????????????????????????????token??????", order = 406)
    @Parameters({
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "contractAddress", parameterDes = "????????????"),
            @Parameter(parameterName = "address", parameterDes = "????????????")
    })
    @ResponseData(name = "?????????", responseType = @TypeDescriptor(value = ContractTokenInfoDto.class))
    public RpcResult getTokenBalance(List<Object> params) {
        VerifyUtils.verifyParams(params, 2);
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        String contractAddress = (String) params.get(1);
        String address = (String) params.get(2);
        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }
        if (!AddressTool.validAddress(chainId, contractAddress)) {
            return RpcResult.paramError(String.format("contractAddress [%s] is invalid", contractAddress));
        }
        if (!AddressTool.validAddress(chainId, address)) {
            return RpcResult.paramError(String.format("address [%s] is invalid", address));
        }
        Result<ContractTokenInfoDto> result = contractTools.getTokenBalance(config.getChainId(), contractAddress, address);
        return ResultUtil.getJsonRpcResult(result);
    }



    @RpcMethod("getContract")
    @ApiOperation(description = "??????????????????????????????", order = 407)
    @Parameters(value = {
        @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
        @Parameter(parameterName = "contractAddress", parameterDes = "????????????")
    })
    @ResponseData(name = "?????????", responseType = @TypeDescriptor(value = ContractInfoDto.class))
    public RpcResult getContract(List<Object> params) {
        VerifyUtils.verifyParams(params, 2);
        int chainId;
        String contractAddress;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        try {
            contractAddress = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[contractAddress] is invalid");
        }

        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }

        if (!AddressTool.validAddress(chainId, contractAddress)) {
            return RpcResult.paramError("[contractAddress] is invalid");
        }

        RpcResult rpcResult = new RpcResult();
        Result<Map> contractInfoDtoResult = contractTools.getContractInfo(chainId, contractAddress);
        if (contractInfoDtoResult.isFailed()) {
            return rpcResult.setError(new RpcResultError(contractInfoDtoResult.getStatus(), contractInfoDtoResult.getMessage(), null));
        }
        rpcResult.setResult(contractInfoDtoResult.getData());
        return rpcResult;
    }

    @RpcMethod("getContractTxResult")
    @ApiOperation(description = "??????????????????????????????", order = 408)
    @Parameters({
        @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
        @Parameter(parameterName = "hash", parameterDes = "??????hash")
    })
    @ResponseData(name = "?????????", responseType = @TypeDescriptor(value = ContractResultDto.class))
    public RpcResult getContractResult(List<Object> params) {
        VerifyUtils.verifyParams(params, 2);
        int chainId;
        String hash;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        try {
            hash = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[txHash] is invalid");
        }

        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }
        Result<Map> contractResult = contractTools.getContractResult(chainId, hash);
        if (contractResult.isFailed()) {
            return ResultUtil.getJsonRpcResult(contractResult);
        }
        Map map = contractResult.getData();
        return RpcResult.success(map.get("data"));
    }

    @RpcMethod("getContractTxResultList")
    @ApiOperation(description = "????????????????????????????????????", order = 409)
    @Parameters({
        @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
        @Parameter(parameterName = "hashList", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "??????hash??????")
    })
    @ResponseData(name = "?????????", description = "???????????????????????????????????????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "hash1 or hash2 or hash3...", valueType = ContractResultDto.class, description = "?????????hash????????????hash?????????key????????????key name????????????")
    }))
    public RpcResult getContractResultList(List<Object> params) {
        VerifyUtils.verifyParams(params, 2);
        int chainId;
        List<String> hashList;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        try {
            hashList = (List<String>) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[hashList] is invalid");
        }

        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }
        Result<Map> contractResult = contractTools.getContractResultList(chainId, hashList);
        if (contractResult.isFailed()) {
            return ResultUtil.getJsonRpcResult(contractResult);
        }
        Map map = contractResult.getData();
        return RpcResult.success(map);
    }


    @RpcMethod("getContractConstructor")
    @ApiOperation(description = "??????????????????????????????", order = 410)
    @Parameters({
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "contractCode", parameterDes = "??????????????????(????????????Hex???????????????)")
    })
    @ResponseData(name = "?????????", responseType = @TypeDescriptor(value = ContractConstructorInfoDto.class))
    public RpcResult getContractConstructor(List<Object> params) {
        VerifyUtils.verifyParams(params, 2);
        int chainId;
        String contractCode;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        try {
            contractCode = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[contractCode] is invalid");
        }
        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }
        RpcResult rpcResult = new RpcResult();
        Result<Map> mapResult = contractTools.getContractConstructor(chainId, contractCode);
        if (mapResult.isFailed()) {
            return ResultUtil.getJsonRpcResult(mapResult);
        }
        Map resultData = mapResult.getData();
        if (resultData == null) {
            rpcResult.setError(new RpcResultError(RpcErrorCode.DATA_NOT_EXISTS));
        } else {
            rpcResult.setResult(resultData);
        }
        return rpcResult;
    }

    @RpcMethod("getContractMethod")
    @ApiOperation(description = "????????????????????????",order = 411)
    @Parameters({
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "contractAddress", parameterDes = "????????????"),
            @Parameter(parameterName = "methodName", parameterDes = "????????????"),
            @Parameter(parameterName = "methodDesc", parameterDes = "????????????", canNull = true)
    })
    @ResponseData(name = "?????????", responseType = @TypeDescriptor(value = ProgramMethod.class))
    public RpcResult getContractMethod(List<Object> params) {
        VerifyUtils.verifyParams(params, 3);
        int chainId;
        String contractAddress;
        String methodName;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        try {
            contractAddress = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[contractAddress] is invalid");
        }
        try {
            methodName = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[methodName] is invalid");
        }
        String methodDesc = null;
        if (params.size() > 3) {
            methodDesc = (String) params.get(3);
        }

        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }
        if (!AddressTool.validAddress(chainId, contractAddress)) {
            return RpcResult.paramError("[contractAddress] is invalid");
        }
        if (StringUtils.isBlank(methodName)) {
            return RpcResult.paramError("[methodName] is invalid");
        }
        RpcResult rpcResult = new RpcResult();
        Result<Map> contractInfoDtoResult = contractTools.getContractInfo(chainId, contractAddress);
        if (contractInfoDtoResult.isFailed()) {
            return ResultUtil.getJsonRpcResult(contractInfoDtoResult);
        }
        Map contractInfo = contractInfoDtoResult.getData();
        try {
            List<Map<String, Object>> methods =(List<Map<String, Object>>) contractInfo.get("method");
            Map resultMethod = null;
            boolean isEmptyMethodDesc = StringUtils.isBlank(methodDesc);
            for (Map<String, Object> method : methods) {
                if (methodName.equals(method.get("name"))) {
                    if (isEmptyMethodDesc) {
                        resultMethod = method;
                        break;
                    } else if (methodDesc.equals(method.get("desc"))) {
                        resultMethod = method;
                        break;
                    }
                }
            }
            if (resultMethod == null) {
                return RpcResult.dataNotFound();
            }
            rpcResult.setResult(resultMethod);
            return rpcResult;
        } catch (Exception e) {
            Log.error(e);
            return RpcResult.failed(CommonCodeConstanst.DATA_ERROR, e.getMessage());
        }
    }

    @RpcMethod("getContractMethodArgsTypes")
    @ApiOperation(description = "??????????????????????????????", order = 412)
    @Parameters({
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "contractAddress", parameterDes = "????????????"),
            @Parameter(parameterName = "methodName", parameterDes = "????????????"),
            @Parameter(parameterName = "methodDesc", parameterDes = "????????????", canNull = true)
    })
    @ResponseData(name = "?????????", responseType = @TypeDescriptor(value = List.class, collectionElement = String.class))
    public RpcResult getContractMethodArgsTypes(List<Object> params) {
        RpcResult result = this.getContractMethod(params);
        if(result.getError() != null) {
            return result;
        }
        Map resultMethod = (Map) result.getResult();
        if (resultMethod == null) {
            return RpcResult.dataNotFound();
        }
        List<String> argsTypes;
        try {
            List<Map<String, Object>> args = (List<Map<String, Object>>) resultMethod.get("args");
            argsTypes = new ArrayList<>();
            for (Map<String, Object> arg : args) {
                argsTypes.add((String) arg.get("type"));
            }
            RpcResult rpcResult = new RpcResult();
            rpcResult.setResult(argsTypes);
            return rpcResult;
        } catch (Exception e) {
            Log.error(e);
            return RpcResult.failed(CommonCodeConstanst.DATA_ERROR, e.getMessage());
        }
    }


    @RpcMethod("validateContractCreate")
    @ApiOperation(description = "??????????????????" ,order = 413)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "sender", parameterDes = "???????????????????????????"),
            @Parameter(parameterName = "gasLimit", requestType = @TypeDescriptor(value = long.class), parameterDes = "GAS??????"),
            @Parameter(parameterName = "price", requestType = @TypeDescriptor(value = long.class), parameterDes = "GAS??????"),
            @Parameter(parameterName = "contractCode", parameterDes = "??????????????????(????????????Hex???????????????)"),
            @Parameter(parameterName = "args", requestType = @TypeDescriptor(value = Object[].class), parameterDes = "????????????", canNull = true)
    })
    @ResponseData(name = "?????????", description = "???????????????gas???", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "success", valueType = boolean.class, description = "??????????????????"),
            @Key(name = "code", description = "????????????????????????"),
            @Key(name = "msg", description = "???????????????????????????")
    }))
    public RpcResult validateContractCreate(List<Object> params) {
        VerifyUtils.verifyParams(params, 6);
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }
        Result<Map> mapResult = contractTools.validateContractCreate(chainId,
                params.get(1),
                params.get(2),
                params.get(3),
                params.get(4),
                params.get(5)
        );
        return ResultUtil.getJsonRpcResult(mapResult);
    }


    @RpcMethod("validateContractCall")
    @ApiOperation(description = "??????????????????", order = 414)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "sender", parameterDes = "???????????????????????????"),
            @Parameter(parameterName = "value", requestType = @TypeDescriptor(value = BigInteger.class), parameterDes = "???????????????????????????????????????????????????????????????????????????BigInteger.ZERO"),
            @Parameter(parameterName = "gasLimit", requestType = @TypeDescriptor(value = long.class), parameterDes = "GAS??????"),
            @Parameter(parameterName = "price", requestType = @TypeDescriptor(value = long.class), parameterDes = "GAS??????"),
            @Parameter(parameterName = "contractAddress", parameterDes = "????????????"),
            @Parameter(parameterName = "methodName", parameterDes = "????????????"),
            @Parameter(parameterName = "methodDesc", parameterDes = "??????????????????????????????????????????????????????????????????????????????", canNull = true),
            @Parameter(parameterName = "args", requestType = @TypeDescriptor(value = Object[].class), parameterDes = "????????????", canNull = true),
            @Parameter(parameterName = "multyAssetValues", requestType = @TypeDescriptor(value = String[][].class), parameterDes = "???????????????????????????????????????????????????????????????????????????????????????: [[\\<value\\>,\\<assetChainId\\>,\\<assetId\\>]]", canNull = true)
    })
    @ResponseData(name = "?????????", description = "???????????????gas???", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "success", valueType = boolean.class, description = "??????????????????"),
            @Key(name = "code", description = "????????????????????????"),
            @Key(name = "msg", description = "???????????????????????????")
    }))
    public RpcResult validateContractCall(List<Object> params) {
        VerifyUtils.verifyParams(params, 9);
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }
        Object multyAssetValues = null;
        if (params.size() > 9) {
            multyAssetValues = params.get(9);
        }
        Result<Map> mapResult = contractTools.validateContractCall(chainId,
                params.get(1),
                params.get(2),
                params.get(3),
                params.get(4),
                params.get(5),
                params.get(6),
                params.get(7),
                params.get(8),
                multyAssetValues
        );
        return ResultUtil.getJsonRpcResult(mapResult);
    }

    @RpcMethod("validateContractDelete")
    @ApiOperation(description = "??????????????????", order = 415)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "sender", parameterDes = "???????????????????????????"),
            @Parameter(parameterName = "contractAddress", parameterDes = "????????????")
    })
    @ResponseData(name = "?????????", description = "???????????????gas???", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "success", valueType = boolean.class, description = "??????????????????"),
            @Key(name = "code", description = "????????????????????????"),
            @Key(name = "msg", description = "???????????????????????????")
    }))
    public RpcResult validateContractDelete(List<Object> params) {
        VerifyUtils.verifyParams(params, 3);
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }
        Result<Map> mapResult = contractTools.validateContractDelete(chainId,
                params.get(1),
                params.get(2)
        );
        return ResultUtil.getJsonRpcResult(mapResult);
    }

    @RpcMethod("imputedContractCreateGas")
    @ApiOperation(description = "???????????????????????????GAS", order = 416)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "sender", parameterDes = "???????????????????????????"),
            @Parameter(parameterName = "contractCode", parameterDes = "??????????????????(????????????Hex???????????????)"),
            @Parameter(parameterName = "args", requestType = @TypeDescriptor(value = Object[].class), parameterDes = "????????????", canNull = true)
    })
    @ResponseData(name = "?????????", description = "???????????????gas???", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "gasLimit", valueType = Long.class, description = "?????????gas??????????????????????????????1")
    }))
    public RpcResult imputedContractCreateGas(List<Object> params) {
        VerifyUtils.verifyParams(params, 4);
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }
        Result<Map> mapResult = contractTools.imputedContractCreateGas(chainId,
                params.get(1),
                params.get(2),
                params.get(3)
        );
        return ResultUtil.getJsonRpcResult(mapResult);
    }

    @RpcMethod("imputedContractCallGas")
    @ApiOperation(description = "???????????????????????????GAS", order = 417)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "sender", parameterDes = "???????????????????????????"),
            @Parameter(parameterName = "value", requestType = @TypeDescriptor(value = BigInteger.class), parameterDes = "???????????????????????????????????????????????????????????????????????????BigInteger.ZERO"),
            @Parameter(parameterName = "contractAddress", parameterDes = "????????????"),
            @Parameter(parameterName = "methodName", parameterDes = "????????????"),
            @Parameter(parameterName = "methodDesc", parameterDes = "??????????????????????????????????????????????????????????????????????????????", canNull = true),
            @Parameter(parameterName = "args", requestType = @TypeDescriptor(value = Object[].class), parameterDes = "????????????", canNull = true),
            @Parameter(parameterName = "multyAssetValues", requestType = @TypeDescriptor(value = String[][].class), parameterDes = "???????????????????????????????????????????????????????????????????????????????????????: [[\\<value\\>,\\<assetChainId\\>,\\<assetId\\>]]", canNull = true)
    })
    @ResponseData(name = "?????????", description = "???????????????gas???", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "gasLimit", valueType = Long.class, description = "?????????gas??????????????????????????????1")
    }))
    public RpcResult imputedContractCallGas(List<Object> params) {
        VerifyUtils.verifyParams(params, 7);
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }
        Object multyAssetValues = null;
        if (params.size() > 7) {
            multyAssetValues = params.get(7);
        }
        Result<Map> mapResult = contractTools.imputedContractCallGas(chainId,
                params.get(1),
                params.get(2),
                params.get(3),
                params.get(4),
                params.get(5),
                params.get(6),
                multyAssetValues
        );
        return ResultUtil.getJsonRpcResult(mapResult);
    }

    @RpcMethod("invokeView")
    @ApiOperation(description = "???????????????????????????", order = 418)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
            @Parameter(parameterName = "contractAddress", parameterDes = "????????????"),
            @Parameter(parameterName = "methodName", parameterDes = "????????????"),
            @Parameter(parameterName = "methodDesc", parameterDes = "??????????????????????????????????????????????????????????????????????????????", canNull = true),
            @Parameter(parameterName = "args", requestType = @TypeDescriptor(value = Object[].class), parameterDes = "????????????", canNull = true)
    })
    @ResponseData(name = "?????????", description = "??????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "result", description = "???????????????????????????")
    }))
    public RpcResult invokeView(List<Object> params) {
        VerifyUtils.verifyParams(params, 5);
        int chainId;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is invalid");
        }
        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }
        Result<Map> mapResult = contractTools.invokeView(chainId,
                params.get(1),
                params.get(2),
                params.get(3),
                params.get(4)
        );
        return ResultUtil.getJsonRpcResult(mapResult);
    }


    @RpcMethod("contractCreateOffline")
    @ApiOperation(description = "?????? - ??????????????????", order = 450)
    @Parameters(value = {
        @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
        @Parameter(parameterName = "sender",  parameterDes = "???????????????????????????"),
        @Parameter(parameterName = "senderBalance", requestType = @TypeDescriptor(value = BigInteger.class), parameterDes = "????????????"),
        @Parameter(parameterName = "nonce", parameterDes = "??????nonce???"),
        @Parameter(parameterName = "alias",  parameterDes = "????????????"),
        @Parameter(parameterName = "contractCode",  parameterDes = "??????????????????(????????????Hex???????????????)"),
        @Parameter(parameterName = "gasLimit", requestType = @TypeDescriptor(value = long.class), parameterDes = "???????????????????????????gas??????"),
        @Parameter(parameterName = "args", requestType = @TypeDescriptor(value = Object[].class), parameterDes = "????????????", canNull = true),
        @Parameter(parameterName = "argsType", requestType = @TypeDescriptor(value = String[].class), parameterDes = "??????????????????", canNull = true),
        @Parameter(parameterName = "remark",  parameterDes = "????????????", canNull = true)
    })
    @ResponseData(name = "?????????", description = "????????????Map??????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
        @Key(name = "hash", description = "??????hash"),
        @Key(name = "txHex", description = "????????????????????????"),
        @Key(name = "contractAddress", description = "?????????????????????")
    }))
    public RpcResult contractCreateOffline(List<Object> params) {
        VerifyUtils.verifyParams(params, 6);
        try {
            int i = 0;
            Integer chainId = (Integer) params.get(i++);
            String sender = (String) params.get(i++);
            BigInteger senderBalance = new BigInteger(params.get(i++).toString());
            String nonce = (String) params.get(i++);
            String alias = (String) params.get(i++);
            String contractCode = (String) params.get(i++);
            long gasLimit = Long.parseLong(params.get(i++).toString());
            List argsList = (List) params.get(i++);
            Object[] args = argsList != null ? argsList.toArray() : null;
            List<String> argsTypeList = (List) params.get(i++);
            String[] argsType = null;
            if(argsTypeList != null) {
                argsType = new String[argsTypeList.size()];
                argsTypeList.toArray(argsType);
            }
            String remark = (String) params.get(i++);

            if (!Context.isChainExist(chainId)) {
                return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
            }

            if (!AddressTool.validAddress(chainId, sender)) {
                return RpcResult.paramError(String.format("sender [%s] is invalid", sender));
            }

            if(!FormatValidUtils.validAlias(alias)) {
                return RpcResult.paramError(String.format("alias [%s] is invalid", alias));
            }

            if (StringUtils.isBlank(contractCode)) {
                return RpcResult.paramError("contractCode is empty");
            }
            io.nuls.core.basic.Result<Map> result = NulsSDKTool.createContractTxOffline(
                    sender,
                    senderBalance,
                    nonce,
                    alias,
                    contractCode,
                    gasLimit,
                    args,
                    argsType,
                    remark);
            return ResultUtil.getJsonRpcResult(result);
        } catch (Exception e) {
            Log.error(e);
            return RpcResult.failed(CommonCodeConstanst.DATA_ERROR, e.getMessage());
        }
    }

    @RpcMethod("contractCallOffline")
    @ApiOperation(description = "?????? - ????????????", order = 451)
    @Parameters(value = {
        @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
        @Parameter(parameterName = "sender",  parameterDes = "???????????????????????????"),
        @Parameter(parameterName = "senderBalance", requestType = @TypeDescriptor(value = BigInteger.class), parameterDes = "????????????"),
        @Parameter(parameterName = "nonce", parameterDes = "??????nonce???"),
        @Parameter(parameterName = "value", requestType = @TypeDescriptor(value = BigInteger.class), parameterDes = "???????????????????????????????????????????????????????????????????????????BigInteger.ZERO"),
        @Parameter(parameterName = "contractAddress",  parameterDes = "????????????"),
        @Parameter(parameterName = "gasLimit", requestType = @TypeDescriptor(value = long.class), parameterDes = "???????????????????????????gas??????"),
        @Parameter(parameterName = "methodName",  parameterDes = "????????????"),
        @Parameter(parameterName = "methodDesc",  parameterDes = "??????????????????????????????????????????????????????????????????????????????", canNull = true),
        @Parameter(parameterName = "args", requestType = @TypeDescriptor(value = Object[].class), parameterDes = "????????????", canNull = true),
        @Parameter(parameterName = "argsType", requestType = @TypeDescriptor(value = String[].class), parameterDes = "??????????????????", canNull = true),
        @Parameter(parameterName = "remark",  parameterDes = "????????????", canNull = true),
        @Parameter(parameterName = "multyAssetValues", requestType = @TypeDescriptor(value = String[][].class), parameterDes = "???????????????????????????????????????????????????????????????????????????????????????: [[\\<value\\>,\\<assetChainId\\>,\\<assetId\\>,\\<nonce\\>]]", canNull = true)
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
        @Key(name = "hash", description = "??????hash"),
        @Key(name = "txHex", description = "????????????????????????")
    }))
    public RpcResult contractCallOffline(List<Object> params) {
        VerifyUtils.verifyParams(params, 8);
        try {
            int i = 0;
            Integer chainId = (Integer) params.get(i++);
            String sender = (String) params.get(i++);
            BigInteger senderBalance = new BigInteger(params.get(i++).toString());
            String nonce = (String) params.get(i++);
            Object valueObj = params.get(i++);
            if(valueObj == null) {
                valueObj = "0";
            }
            BigInteger value = new BigInteger(valueObj.toString());
            if (value.compareTo(BigInteger.ZERO) < 0) {
                return RpcResult.paramError(String.format("value [%s] is invalid", value.toString()));
            }
            String contractAddress = (String) params.get(i++);
            long gasLimit = Long.parseLong(params.get(i++).toString());
            String methodName = (String) params.get(i++);
            String methodDesc = (String) params.get(i++);
            List argsList = (List) params.get(i++);
            Object[] args = argsList != null ? argsList.toArray() : null;
            List<String> argsTypeList = (List) params.get(i++);
            String[] argsType = null;
            if(argsTypeList != null) {
                argsType = new String[argsTypeList.size()];
                argsTypeList.toArray(argsType);
            }
            String remark = (String) params.get(i++);

            if (!Context.isChainExist(chainId)) {
                return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
            }
            if (!AddressTool.validAddress(chainId, sender)) {
                return RpcResult.paramError(String.format("sender [%s] is invalid", sender));
            }
            if (!AddressTool.validAddress(chainId, contractAddress)) {
                return RpcResult.paramError(String.format("contractAddress [%s] is invalid", contractAddress));
            }
            if (StringUtils.isBlank(methodName)) {
                return RpcResult.paramError("methodName is empty");
            }

            String[][] multyAssetValues = null;
            if (params.size() > 12) {
                List multyAssetValueList = (List) params.get(12);
                Object[] objArray = multyAssetValueList != null ? multyAssetValueList.toArray() : null;
                multyAssetValues = ContractUtil.twoDimensionalArray(objArray);
            }
            // ??????????????????????????????
            io.nuls.core.basic.Result<Map> result = NulsSDKTool.callContractTxOffline(
                    sender,
                    senderBalance,
                    nonce,
                    value,
                    contractAddress,
                    gasLimit,
                    methodName,
                    methodDesc,
                    args,
                    argsType,
                    remark,
                    Utils.multyAssetObjectArray(multyAssetValues));
            return ResultUtil.getJsonRpcResult(result);
        } catch (Exception e) {
            Log.error(e);
            return RpcResult.failed(CommonCodeConstanst.DATA_ERROR, e.getMessage());
        }
    }


    @RpcMethod("contractDeleteOffline")
    @ApiOperation(description = "?????? - ????????????", order = 452)
    @Parameters(value = {
        @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
        @Parameter(parameterName = "sender", parameterDes = "???????????????????????????"),
        @Parameter(parameterName = "senderBalance", requestType = @TypeDescriptor(value = BigInteger.class), parameterDes = "????????????"),
        @Parameter(parameterName = "nonce", parameterDes = "??????nonce???"),
        @Parameter(parameterName = "contractAddress", parameterDes = "????????????"),
        @Parameter(parameterName = "remark", parameterDes = "????????????", canNull = true)
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
        @Key(name = "hash", description = "??????hash"),
        @Key(name = "txHex", description = "????????????????????????")
    }))
    public RpcResult contractDeleteOffline(List<Object> params) {
        VerifyUtils.verifyParams(params, 4);
        try {
            int i = 0;
            Integer chainId = (Integer) params.get(i++);
            String sender = (String) params.get(i++);
            BigInteger senderBalance = new BigInteger(params.get(i++).toString());
            String nonce = (String) params.get(i++);
            String contractAddress = (String) params.get(i++);
            String remark = (String) params.get(i++);
            if (!Context.isChainExist(chainId)) {
                return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
            }
            if (!AddressTool.validAddress(chainId, sender)) {
                return RpcResult.paramError(String.format("sender [%s] is invalid", sender));
            }
            if (!AddressTool.validAddress(chainId, contractAddress)) {
                return RpcResult.paramError(String.format("contractAddress [%s] is invalid", contractAddress));
            }
            io.nuls.core.basic.Result<Map> result = NulsSDKTool.deleteContractTxOffline(
                    sender,
                    senderBalance,
                    nonce,
                    contractAddress,
                    remark);
            return ResultUtil.getJsonRpcResult(result);
        } catch (Exception e) {
            Log.error(e);
            return RpcResult.failed(CommonCodeConstanst.DATA_ERROR, e.getMessage());
        }
    }


    @RpcMethod("tokentransferOffline")
    @ApiOperation(description = "?????? - ??????token??????", order = 453)
    @Parameters(value = {
        @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
        @Parameter(parameterName = "fromAddress", parameterDes = "?????????????????????"),
        @Parameter(parameterName = "senderBalance", requestType = @TypeDescriptor(value = BigInteger.class), parameterDes = "?????????????????????"),
        @Parameter(parameterName = "nonce", parameterDes = "???????????????nonce???"),
        @Parameter(parameterName = "toAddress", parameterDes = "?????????????????????"),
        @Parameter(parameterName = "contractAddress", parameterDes = "token????????????"),
        @Parameter(parameterName = "gasLimit", requestType = @TypeDescriptor(value = long.class), parameterDes = "???????????????????????????gas??????"),
        @Parameter(parameterName = "amount", requestType = @TypeDescriptor(value = BigInteger.class), parameterDes = "?????????token????????????"),
        @Parameter(parameterName = "remark", parameterDes = "????????????", canNull = true)
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
        @Key(name = "hash", description = "??????hash"),
        @Key(name = "txHex", description = "????????????????????????")
    }))
    public RpcResult tokentransferOffline(List<Object> params) {
        VerifyUtils.verifyParams(params, 6);
        try {
            int i = 0;
            Integer chainId = (Integer) params.get(i++);
            String fromAddress = (String) params.get(i++);
            BigInteger senderBalance = new BigInteger(params.get(i++).toString());
            String nonce = (String) params.get(i++);
            String toAddress = (String) params.get(i++);
            String contractAddress = (String) params.get(i++);
            long gasLimit = Long.parseLong(params.get(i++).toString());
            Object amountObj = params.get(i++);
            if(amountObj == null) {
                return RpcResult.paramError("amount is empty");
            }
            BigInteger amount = new BigInteger(amountObj.toString());
            if (amount.compareTo(BigInteger.ZERO) < 0) {
                return RpcResult.paramError(String.format("amount [%s] is invalid", amount.toString()));
            }
            String remark = (String) params.get(i++);

            if (!Context.isChainExist(chainId)) {
                return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
            }
            if (!AddressTool.validAddress(chainId, fromAddress)) {
                return RpcResult.paramError(String.format("fromAddress [%s] is invalid", fromAddress));
            }
            if (!AddressTool.validAddress(chainId, toAddress)) {
                return RpcResult.paramError(String.format("toAddress [%s] is invalid", toAddress));
            }
            if (!AddressTool.validAddress(chainId, contractAddress)) {
                return RpcResult.paramError(String.format("contractAddress [%s] is invalid", contractAddress));
            }

            io.nuls.core.basic.Result<Map> result = NulsSDKTool.tokenTransferTxOffline(
                    fromAddress,
                    senderBalance,
                    nonce,
                    toAddress,
                    contractAddress,
                    gasLimit,
                    amount,
                    remark);
            return ResultUtil.getJsonRpcResult(result);
        } catch (Exception e) {
            Log.error(e);
            return RpcResult.failed(CommonCodeConstanst.DATA_ERROR, e.getMessage());
        }
    }


    @RpcMethod("transfer2contractOffline")
    @ApiOperation(description = "?????? - ????????????????????????????????????(????????????)???????????????", order = 454)
    @Parameters(value = {
        @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???id"),
        @Parameter(parameterName = "fromAddress", parameterDes = "?????????????????????"),
        @Parameter(parameterName = "senderBalance", requestType = @TypeDescriptor(value = BigInteger.class), parameterDes = "?????????????????????"),
        @Parameter(parameterName = "nonce", parameterDes = "???????????????nonce???"),
        @Parameter(parameterName = "toAddress", parameterDes = "?????????????????????"),
        @Parameter(parameterName = "gasLimit", requestType = @TypeDescriptor(value = long.class), parameterDes = "???????????????????????????gas??????"),
        @Parameter(parameterName = "amount", requestType = @TypeDescriptor(value = BigInteger.class), parameterDes = "???????????????????????????"),
        @Parameter(parameterName = "remark", parameterDes = "????????????", canNull = true)
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
        @Key(name = "hash", description = "??????hash"),
        @Key(name = "txHex", description = "????????????????????????")
    }))
    public RpcResult transfer2contractOffline(List<Object> params) {
        VerifyUtils.verifyParams(params, 5);
        try {
            int i = 0;
            Integer chainId = (Integer) params.get(i++);
            if (!Context.isChainExist(chainId)) {
                return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
            }
            String fromAddress = (String) params.get(i++);
            BigInteger senderBalance = new BigInteger(params.get(i++).toString());
            String nonce = (String) params.get(i++);
            String toAddress = (String) params.get(i++);
            long gasLimit = Long.parseLong(params.get(i++).toString());
            Object amountObj = params.get(i++);
            if(amountObj == null) {
                return RpcResult.paramError("amount is empty");
            }
            BigInteger amount = new BigInteger(amountObj.toString());
            if (amount.compareTo(BigInteger.ZERO) < 0) {
                return RpcResult.paramError(String.format("amount [%s] is invalid", amount.toString()));
            }
            String remark = (String) params.get(i++);

            if (!Context.isChainExist(chainId)) {
                return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
            }
            if (!AddressTool.validAddress(chainId, fromAddress)) {
                return RpcResult.paramError(String.format("fromAddress [%s] is invalid", fromAddress));
            }
            if (!AddressTool.validAddress(chainId, toAddress)) {
                return RpcResult.paramError(String.format("toAddress [%s] is invalid", toAddress));
            }

            io.nuls.core.basic.Result<Map> result = NulsSDKTool.transferToContractTxOffline(
                    fromAddress,
                    senderBalance,
                    nonce,
                    toAddress,
                    gasLimit,
                    amount,
                    remark);
            return ResultUtil.getJsonRpcResult(result);
        } catch (Exception e) {
            Log.error(e);
            return RpcResult.failed(CommonCodeConstanst.DATA_ERROR, e.getMessage());
        }
    }


}
