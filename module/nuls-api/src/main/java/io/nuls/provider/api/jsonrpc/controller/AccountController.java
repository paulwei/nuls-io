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
import io.nuls.base.api.provider.account.AccountService;
import io.nuls.base.api.provider.account.facade.*;
import io.nuls.base.basic.AddressTool;
import io.nuls.core.constant.CommonCodeConstanst;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Controller;
import io.nuls.core.core.annotation.RpcMethod;
import io.nuls.core.crypto.ECKey;
import io.nuls.core.crypto.HexUtil;
import io.nuls.core.exception.NulsException;
import io.nuls.core.exception.NulsRuntimeException;
import io.nuls.core.model.FormatValidUtils;
import io.nuls.core.model.StringUtils;
import io.nuls.core.parse.JSONUtils;
import io.nuls.core.rpc.model.*;
import io.nuls.provider.api.config.Config;
import io.nuls.provider.api.config.Context;
import io.nuls.provider.model.dto.AccountKeyStoreDto;
import io.nuls.provider.model.dto.ContractTokenInfoDto;
import io.nuls.provider.model.form.PriKeyForm;
import io.nuls.provider.model.jsonrpc.RpcResult;
import io.nuls.provider.model.jsonrpc.RpcResultError;
import io.nuls.provider.rpctools.AccountTools;
import io.nuls.provider.rpctools.ContractTools;
import io.nuls.provider.rpctools.LegderTools;
import io.nuls.provider.rpctools.vo.AccountBalance;
import io.nuls.provider.utils.Log;
import io.nuls.provider.utils.ResultUtil;
import io.nuls.provider.utils.Utils;
import io.nuls.provider.utils.VerifyUtils;
import io.nuls.v2.SDKContext;
import io.nuls.v2.error.AccountErrorCode;
import io.nuls.v2.model.Account;
import io.nuls.v2.model.annotation.Api;
import io.nuls.v2.model.annotation.ApiOperation;
import io.nuls.v2.model.annotation.ApiType;
import io.nuls.v2.model.dto.AccountDto;
import io.nuls.v2.model.dto.AliasDto;
import io.nuls.v2.model.dto.MultiSignAliasDto;
import io.nuls.v2.model.dto.SignDto;
import io.nuls.v2.util.AccountTool;
import io.nuls.v2.util.NulsSDKTool;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nuls.v2.util.ValidateUtil.validateChainId;

/**
 * @author Niels
 */
@Controller
@Api(type = ApiType.JSONRPC)
public class AccountController {

    @Autowired
    private ContractTools contractTools;
    @Autowired
    private LegderTools legderTools;
    @Autowired
    private AccountTools accountTools;
    @Autowired
    private Config config;

    AccountService accountService = ServiceManager.get(AccountService.class);

    private long time;

    @RpcMethod("createAccount")
    @ApiOperation(description = "??????????????????", order = 101, detailDesc = "???????????????????????????????????????")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "count", requestType = @TypeDescriptor(value = int.class), parameterDes = "????????????"),
            @Parameter(parameterName = "password", requestType = @TypeDescriptor(value = String.class), parameterDes = "??????")
    })
    @ResponseData(name = "?????????", description = "????????????????????????", responseType = @TypeDescriptor(value = List.class, collectionElement = String.class))
    public RpcResult createAccount(List<Object> params) {
        VerifyUtils.verifyParams(params, 3);
        int chainId, count;
        String password;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            count = (int) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[count] is inValid");
        }
        try {
            password = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[password] is inValid");
        }
        if (!FormatValidUtils.validPassword(password)) {
            return RpcResult.paramError("[password] is inValid");
        }

        CreateAccountReq req = new CreateAccountReq(count, password);
        req.setChainId(chainId);
        Result<String> result = accountService.createAccount(req);
        RpcResult rpcResult = new RpcResult();
        if (result.isFailed()) {
            rpcResult.setError(new RpcResultError(result.getStatus(), result.getMessage(), null));
        } else {
            rpcResult.setResult(result.getList());
        }
        return rpcResult;
    }

    @RpcMethod("updatePassword")
    @ApiOperation(description = "??????????????????", order = 102)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "address", requestType = @TypeDescriptor(value = String.class), parameterDes = "????????????"),
            @Parameter(parameterName = "oldPassword", requestType = @TypeDescriptor(value = String.class), parameterDes = "?????????"),
            @Parameter(parameterName = "newPassword", requestType = @TypeDescriptor(value = String.class), parameterDes = "?????????")
    })
    @ResponseData(name = "?????????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", valueType = Boolean.class, description = "??????????????????")
    }))
    public RpcResult updatePassword(List<Object> params) {
        VerifyUtils.verifyParams(params, 4);
        int chainId;
        String address, oldPassword, newPassword;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            address = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[address] is inValid");
        }
        try {
            oldPassword = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[oldPassword] is inValid");
        }
        try {
            newPassword = (String) params.get(3);
        } catch (Exception e) {
            return RpcResult.paramError("[newPassword] is inValid");
        }
        if (!AddressTool.validAddress(chainId, address)) {
            return RpcResult.paramError("[address] is inValid");
        }
        if (!FormatValidUtils.validPassword(oldPassword)) {
            return RpcResult.paramError("[oldPassword] is inValid");
        }
        if (!FormatValidUtils.validPassword(newPassword)) {
            return RpcResult.paramError("[newPassword] is inValid");
        }
        if (System.currentTimeMillis() - time < 3000L) {
            return RpcResult.paramError("Access frequency limit.");
        }
        time = System.currentTimeMillis();
        UpdatePasswordReq req = new UpdatePasswordReq(address, oldPassword, newPassword);
        req.setChainId(chainId);
        Result<Boolean> result = accountService.updatePassword(req);
        RpcResult rpcResult = new RpcResult();
        if (result.isSuccess()) {
            rpcResult.setResult(result.getData());
        } else {
            rpcResult.setError(new RpcResultError(result.getStatus(), result.getMessage(), null));
        }
        return rpcResult;
    }

    @RpcMethod("getPriKey")
    @ApiOperation(description = "??????????????????", order = 103, detailDesc = "????????????????????????????????????????????????")
    @Parameters({
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "address", parameterDes = "????????????"),
            @Parameter(parameterName = "password", parameterDes = "??????")
    })
    @ResponseData(name = "?????????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", description = "??????")
    }))
    public RpcResult getPriKey(List<Object> params) {
        int chainId;
        String address, password;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            address = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[address] is inValid");
        }
        try {
            password = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[password] is inValid");
        }
        if (!AddressTool.validAddress(chainId, address)) {
            return RpcResult.paramError("[address] is inValid");
        }
        if (!FormatValidUtils.validPassword(password)) {
            return RpcResult.paramError("[password] is inValid");
        }

        if (System.currentTimeMillis() - time < 3000L) {
            return RpcResult.paramError("Access frequency limit.");
        }
        time = System.currentTimeMillis();

        GetAccountPrivateKeyByAddressReq req = new GetAccountPrivateKeyByAddressReq(password, address);
        req.setChainId(chainId);
        Result<String> result = accountService.getAccountPrivateKey(req);
        RpcResult rpcResult = new RpcResult();
        if (result.isSuccess()) {
            rpcResult.setResult(result.getData());
        } else {
            rpcResult.setError(new RpcResultError(result.getStatus(), result.getMessage(), null));
        }
        return rpcResult;
    }

    @RpcMethod("importPriKey")
    @ApiOperation(description = "????????????????????????", order = 104, detailDesc = "?????????????????????????????????????????????????????????")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "priKey", requestType = @TypeDescriptor(value = String.class), parameterDes = "??????????????????"),
            @Parameter(parameterName = "password", requestType = @TypeDescriptor(value = String.class), parameterDes = "?????????")
    })
    @ResponseData(name = "?????????", description = "??????????????????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", description = "????????????")
    }))
    public RpcResult importPriKey(List<Object> params) {
        VerifyUtils.verifyParams(params, 3);
        int chainId;
        String priKey, password;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            priKey = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[priKey] is inValid");
        }
        try {
            password = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[password] is inValid");
        }
        if (StringUtils.isBlank(priKey)) {
            return RpcResult.paramError("[priKey] is inValid");
        }
        if (!FormatValidUtils.validPassword(password)) {
            return RpcResult.paramError("[password] is inValid");
        }

        ImportAccountByPrivateKeyReq req = new ImportAccountByPrivateKeyReq(password, priKey, true);
        req.setChainId(chainId);
        Result<String> result = accountService.importAccountByPrivateKey(req);
        RpcResult rpcResult = new RpcResult();
        if (result.isSuccess()) {
            rpcResult.setResult(result.getData());
        } else {
            rpcResult.setError(new RpcResultError(result.getStatus(), result.getMessage(), null));
        }
        return rpcResult;
    }

    @RpcMethod("importKeystore")
    @ApiOperation(description = "??????keystore????????????", order = 105)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "keyStoreJson", requestType = @TypeDescriptor(value = Map.class), parameterDes = "keyStoreJson"),
            @Parameter(parameterName = "password", requestType = @TypeDescriptor(value = String.class), parameterDes = "keystore??????")
    })
    @ResponseData(name = "?????????", description = "??????????????????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", description = "????????????")
    }))
    public RpcResult importKeystore(List<Object> params) {
        VerifyUtils.verifyParams(params, 3);
        int chainId;
        String password, keyStoreJson;
        Map keyStoreMap;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            keyStoreMap = (Map) params.get(1);
            keyStoreJson = JSONUtils.obj2json(keyStoreMap);
        } catch (Exception e) {
            return RpcResult.paramError("[keyStoreJson] is inValid");
        }
        try {
            password = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[password] is inValid");
        }
        if (!FormatValidUtils.validPassword(password)) {
            return RpcResult.paramError("[password] is inValid");
        }

        ImportAccountByKeyStoreReq req = new ImportAccountByKeyStoreReq(password, HexUtil.encode(keyStoreJson.getBytes()), true);
        req.setChainId(chainId);
        Result<String> result = accountService.importAccountByKeyStore(req);
        RpcResult rpcResult = new RpcResult();
        if (result.isSuccess()) {
            rpcResult.setResult(result.getData());
        } else {
            rpcResult.setError(new RpcResultError(result.getStatus(), result.getMessage(), null));
        }
        return rpcResult;
    }

    @RpcMethod("exportKeystore")
    @ApiOperation(description = "???????????????????????????keystore??????", order = 106)
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "address", requestType = @TypeDescriptor(value = String.class), parameterDes = "????????????"),
            @Parameter(parameterName = "password", requestType = @TypeDescriptor(value = String.class), parameterDes = "????????????")
    })
    @ResponseData(name = "?????????", description = "??????keystore?????????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "result", description = "keystore")
    }))
    public RpcResult exportKeystore(List<Object> params) {
        VerifyUtils.verifyParams(params, 3);
        int chainId;
        String address, password;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            address = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[address] is inValid");
        }
        try {
            password = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[password] is inValid");
        }
        if (!AddressTool.validAddress(chainId, address)) {
            return RpcResult.paramError("[address] is inValid");
        }
        if (!FormatValidUtils.validPassword(password)) {
            return RpcResult.paramError("[password] is inValid");
        }

        if (System.currentTimeMillis() - time < 3000L) {
            return RpcResult.paramError("Access frequency limit.");
        }
        time = System.currentTimeMillis();

        KeyStoreReq req = new KeyStoreReq(password, address);
        req.setChainId(chainId);
        Result<String> result = accountService.getAccountKeyStore(req);
        RpcResult rpcResult = new RpcResult();
        try {
            if (result.isSuccess()) {
                AccountKeyStoreDto keyStoreDto = JSONUtils.json2pojo(result.getData(), AccountKeyStoreDto.class);
                rpcResult.setResult(keyStoreDto);
            } else {
                rpcResult.setError(new RpcResultError(result.getStatus(), result.getMessage(), null));
            }
            return rpcResult;
        } catch (IOException e) {
            return RpcResult.failed(CommonCodeConstanst.DATA_PARSE_ERROR);
        }
    }

    @RpcMethod("getAccountBalance")
    @ApiOperation(description = "??????????????????", order = 107, detailDesc = "???????????????ID?????????ID?????????????????????????????????????????????nonce???")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "assetChainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "????????????ID"),
            @Parameter(parameterName = "assetId", requestType = @TypeDescriptor(value = int.class), parameterDes = "??????ID"),
            @Parameter(parameterName = "address", requestType = @TypeDescriptor(value = String.class), parameterDes = "????????????")
    })
    @ResponseData(name = "?????????", responseType = @TypeDescriptor(value = AccountBalance.class))
    public RpcResult getAccountBalance(List<Object> params) {
        VerifyUtils.verifyParams(params, 4);
        int chainId, assetChainId, assetId;
        String address;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            assetChainId = (int) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[assetChainId] is inValid");
        }
        try {
            assetId = (int) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[assetId] is inValid");
        }
        try {
            address = (String) params.get(3);
        } catch (Exception e) {
            return RpcResult.paramError("[address] is inValid");
        }
        if (!AddressTool.validAddress(chainId, address)) {
            return RpcResult.paramError("[address] is inValid");
        }

        if (!Context.isChainExist(chainId)) {
            return RpcResult.dataNotFound();
        }
        RpcResult rpcResult = new RpcResult();
        Result<AccountBalance> balanceResult = legderTools.getBalanceAndNonce(chainId, assetChainId, assetId, address);
        if (balanceResult.isFailed()) {
            return rpcResult.setError(new RpcResultError(balanceResult.getStatus(), balanceResult.getMessage(), null));
        }
        return rpcResult.setResult(balanceResult.getData());
    }


    /**
     * ????????????????????????
     * @param params
     * @return
     */
    @RpcMethod("getBalanceList")
    @ApiOperation(description = "??????????????????", order = 107, detailDesc = "???????????????ID?????????ID?????????????????????????????????????????????nonce?????????")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "address", requestType = @TypeDescriptor(value = String.class), parameterDes = "????????????"),
            @Parameter(parameterName = "assetIdList", requestType = @TypeDescriptor(value = List.class), parameterDes = "?????????ID??????")
    })
    @ResponseData(name = "?????????", responseType = @TypeDescriptor(value = AccountBalance.class))
    public RpcResult getBalanceList(List<Object> params) {
        VerifyUtils.verifyParams(params, 3);
        String address;
        int chainId;
        List<Map> coinDtoList;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            address = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[address] is inValid");
        }
        try {
            coinDtoList = (List<Map> ) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }

        if (!AddressTool.validAddress(chainId, address)) {
            return RpcResult.paramError("[address] is inValid");
        }
        RpcResult rpcResult = new RpcResult();

        Result<List<AccountBalance>> balanceResult = legderTools.getBalanceList(chainId, coinDtoList, address);
        if (balanceResult.isFailed()) {
            return rpcResult.setError(new RpcResultError(balanceResult.getStatus(), balanceResult.getMessage(), null));
        }
        return rpcResult.setResult(balanceResult.getData());
    }


    @RpcMethod("setAlias")
    @ApiOperation(description = "??????????????????", order = 108, detailDesc = "???????????????1-20?????????????????????????????????????????????????????????1???NULS")
    @Parameters({
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "address", requestType = @TypeDescriptor(value = String.class), parameterDes = "????????????"),
            @Parameter(parameterName = "alias", requestType = @TypeDescriptor(value = String.class), parameterDes = "??????"),
            @Parameter(parameterName = "password", requestType = @TypeDescriptor(value = String.class), parameterDes = "????????????")
    })
    @ResponseData(name = "?????????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", description = "?????????????????????hash")
    }))
    public RpcResult setAlias(List<Object> params) {
        int chainId;
        String address, alias, password;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            address = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[address] is inValid");
        }
        try {
            alias = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[alias] is inValid");
        }
        try {
            password = (String) params.get(3);
        } catch (Exception e) {
            return RpcResult.paramError("[password] is inValid");
        }
        if (!Context.isChainExist(chainId)) {
            return RpcResult.dataNotFound();
        }

        if (!AddressTool.validAddress(chainId, address)) {
            return RpcResult.paramError("[address] is inValid");
        }
        if (!FormatValidUtils.validAlias(alias)) {
            return RpcResult.paramError("[alias] is inValid");
        }
        if (StringUtils.isBlank(password)) {
            return RpcResult.paramError("[password] is inValid");
        }
        SetAccountAliasReq aliasReq = new SetAccountAliasReq(password, address, alias);
        Result<String> result = accountService.setAccountAlias(aliasReq);
        RpcResult rpcResult = new RpcResult();
        if (result.isSuccess()) {
            rpcResult.setResult(result.getData());
        } else {
            rpcResult.setError(new RpcResultError(result.getStatus(), result.getMessage(), null));
        }
        return rpcResult;
    }

    @RpcMethod("validateAddress")
    @ApiOperation(description = "????????????????????????", order = 109, detailDesc = "????????????????????????")
    @Parameters({
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "address", requestType = @TypeDescriptor(value = String.class), parameterDes = "????????????")
    })
    @ResponseData(name = "?????????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", description = "boolean")
    }))
    public RpcResult validateAddress(List<Object> params) {
        int chainId;
        String address;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            address = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[address] is inValid");
        }
        boolean b = AddressTool.validAddress(chainId, address);
        if (b) {
            return RpcResult.success(Map.of("value", true));
        } else {
            return RpcResult.failed(AccountErrorCode.ADDRESS_ERROR);
        }
    }

    @RpcMethod("getAddressByPublicKey")
    @ApiOperation(description = "????????????????????????????????????", order = 110, detailDesc = "????????????????????????????????????")
    @Parameters({
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "publicKey", requestType = @TypeDescriptor(value = String.class), parameterDes = "????????????")
    })
    @ResponseData(name = "?????????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "address", description = "????????????")
    }))
    public RpcResult getAddressByPublicKey(List<Object> params) {
        int chainId;
        String publicKey;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            publicKey = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[publicKey] is inValid");
        }
        try {
            byte[] address = AddressTool.getAddress(HexUtil.decode(publicKey), chainId);
            return RpcResult.success(Map.of("address", AddressTool.getStringAddressByBytes(address)));
        } catch (Exception e) {
            Log.error(e);
            return RpcResult.failed(AccountErrorCode.ADDRESS_ERROR);
        }
    }

    @RpcMethod("createAccountOffline")
    @ApiOperation(description = "?????? - ??????????????????", order = 151, detailDesc = "???????????????????????????????????????,???????????????????????????keystore??????")
    @Parameters(value = {
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "count", requestType = @TypeDescriptor(value = int.class), parameterDes = "????????????"),
            @Parameter(parameterName = "prefix", requestType = @TypeDescriptor(value = String.class), parameterDes = "????????????", canNull = true),
            @Parameter(parameterName = "password", requestType = @TypeDescriptor(value = String.class), parameterDes = "??????")
    })
    @ResponseData(name = "?????????", description = "????????????????????????", responseType = @TypeDescriptor(value = List.class, collectionElement = AccountDto.class))
    public RpcResult createAccountOffline(List<Object> params) {
        VerifyUtils.verifyParams(params, 3);
        int chainId, count;
        String prefix, password;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            count = (int) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[count] is inValid");
        }
        try {
            prefix = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[prefix] is inValid");
        }
        try {
            password = (String) params.get(3);
        } catch (Exception e) {
            return RpcResult.paramError("[password] is inValid");
        }
        if (!FormatValidUtils.validPassword(password)) {
            return RpcResult.paramError("[password] is inValid");
        }
//        if (!Context.isChainExist(chainId)) {
//            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
//        }
        io.nuls.core.basic.Result<List<AccountDto>> result;
        if (StringUtils.isBlank(prefix)) {
            result = NulsSDKTool.createOffLineAccount(count, password);
        } else {
            result = NulsSDKTool.createOffLineAccount(chainId, count, prefix, password);
        }
        return ResultUtil.getJsonRpcResult(result);
    }

    @RpcMethod("getPriKeyOffline")
    @ApiOperation(description = "??????????????????????????????", order = 152)
    @Parameters({
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "address", parameterType = "String", parameterDes = "????????????"),
            @Parameter(parameterName = "encryptedPrivateKey", parameterType = "String", parameterDes = "??????????????????"),
            @Parameter(parameterName = "password", parameterType = "String", parameterDes = "??????")
    })
    @ResponseData(name = "?????????", description = "????????????Map??????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", description = "????????????")
    }))
    public RpcResult getPriKeyOffline(List<Object> params) {
        int chainId;
        String address, encryptedPriKey, password;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            address = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[address] is inValid");
        }
        try {
            encryptedPriKey = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[encryptedPriKey] is inValid");
        }
        try {
            password = (String) params.get(3);
        } catch (Exception e) {
            return RpcResult.paramError("[password] is inValid");
        }
        io.nuls.core.basic.Result result = NulsSDKTool.getPriKeyOffline(address, encryptedPriKey, password);
        return ResultUtil.getJsonRpcResult(result);
    }

    @RpcMethod("resetPasswordOffline")
    @ApiOperation(description = "????????????????????????", order = 153)
    @Parameters({
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "address", parameterType = "String", parameterDes = "????????????"),
            @Parameter(parameterName = "encryptedPrivateKey", parameterType = "String", parameterDes = "??????????????????"),
            @Parameter(parameterName = "oldPassword", parameterType = "String", parameterDes = "?????????"),
            @Parameter(parameterName = "newPassword", parameterType = "String", parameterDes = "?????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map??????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", description = "??????????????????????????????")
    }))
    public RpcResult resetPasswordOffline(List<Object> params) {
        int chainId;
        String address, encryptedPriKey, oldPassword, newPassword;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            address = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[address] is inValid");
        }
        try {
            encryptedPriKey = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[encryptedPriKey] is inValid");
        }
        try {
            oldPassword = (String) params.get(3);
        } catch (Exception e) {
            return RpcResult.paramError("[oldPassword] is inValid");
        }
        try {
            newPassword = (String) params.get(3);
        } catch (Exception e) {
            return RpcResult.paramError("[newPassword] is inValid");
        }

        io.nuls.core.basic.Result result = NulsSDKTool.resetPasswordOffline(address, encryptedPriKey, oldPassword, newPassword);
        return ResultUtil.getJsonRpcResult(result);
    }

    @RpcMethod("multiSign")
    @ApiOperation(description = "?????????????????????", order = 154, detailDesc = "????????????????????????????????????????????????,????????????????????????????????????????????????????????????????????????????????????????????????")
    @Parameters({
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "signDtoList", parameterDes = "??????????????????", requestType = @TypeDescriptor(value = List.class, collectionElement = SignDto.class)),
            @Parameter(parameterName = "txHex", parameterType = "String", parameterDes = "???????????????16???????????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map??????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "hash", description = "??????hash"),
            @Key(name = "txHex", description = "??????????????????16???????????????")
    }))
    public RpcResult multiSign(List<Object> params) {
        int chainId;
        String txHex;
        List<Map> signMap;
        List<SignDto> signDtoList = new ArrayList<>();
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }

        try {
            signMap = (List<Map>) params.get(1);
            for (Map map : signMap) {
                SignDto signDto = JSONUtils.map2pojo(map, SignDto.class);
                signDtoList.add(signDto);
            }
        } catch (Exception e) {
            return RpcResult.paramError("[signDto] is inValid");
        }
        txHex = (String) params.get(2);

        io.nuls.core.basic.Result result = NulsSDKTool.sign(signDtoList, txHex);
        return ResultUtil.getJsonRpcResult(result);
    }

    @RpcMethod("priKeySign")
    @ApiOperation(description = "????????????????????????", order = 155)
    @Parameters({
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "txHex", parameterType = "String", parameterDes = "???????????????16???????????????"),
            @Parameter(parameterName = "address", parameterType = "String", parameterDes = "????????????"),
            @Parameter(parameterName = "privateKey", parameterType = "String", parameterDes = "??????????????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map??????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "hash", description = "??????hash"),
            @Key(name = "txHex", description = "??????????????????16???????????????")
    }))
    public RpcResult sign(List<Object> params) {
        int chainId;
        String txHex, address, priKey;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            txHex = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[txHex] is inValid");
        }
        try {
            address = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[address] is inValid");
        }
        try {
            priKey = (String) params.get(3);
        } catch (Exception e) {
            return RpcResult.paramError("[priKey] is inValid");
        }
        if (!Context.isChainExist(chainId)) {
            return RpcResult.paramError(String.format("chainId [%s] is invalid", chainId));
        }
        if (StringUtils.isBlank(txHex)) {
            return RpcResult.paramError("[txHex] is inValid");
        }
        if (!AddressTool.validAddress(chainId, address)) {
            return RpcResult.paramError("[address] is inValid");
        }
        if (StringUtils.isBlank(priKey)) {
            return RpcResult.paramError("[priKey] is inValid");
        }

        io.nuls.core.basic.Result result = NulsSDKTool.sign(txHex, address, priKey);
        return ResultUtil.getJsonRpcResult(result);
    }

    @RpcMethod("encryptedPriKeySign")
    @ApiOperation(description = "????????????????????????", order = 156)
    @Parameters({
            @Parameter(parameterName = "chainId", requestType = @TypeDescriptor(value = int.class), parameterDes = "???ID"),
            @Parameter(parameterName = "txHex", parameterType = "String", parameterDes = "???????????????16???????????????"),
            @Parameter(parameterName = "address", parameterType = "String", parameterDes = "????????????"),
            @Parameter(parameterName = "encryptedPrivateKey", parameterType = "String", parameterDes = "??????????????????"),
            @Parameter(parameterName = "password", parameterType = "String", parameterDes = "??????")
    })
    @ResponseData(name = "?????????", description = "????????????Map??????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "hash", description = "??????hash"),
            @Key(name = "txHex", description = "??????????????????16???????????????")
    }))
    public RpcResult encryptedPriKeySign(List<Object> params) {
        int chainId;
        String txHex, address, encryptedPriKey, password;
        try {
            chainId = (int) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[chainId] is inValid");
        }
        try {
            txHex = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[txHex] is inValid");
        }
        try {
            address = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[address] is inValid");
        }
        try {
            encryptedPriKey = (String) params.get(3);
        } catch (Exception e) {
            return RpcResult.paramError("[encryptedPriKey] is inValid");
        }
        try {
            password = (String) params.get(4);
        } catch (Exception e) {
            return RpcResult.paramError("[password] is inValid");
        }
        if (StringUtils.isBlank(txHex)) {
            return RpcResult.paramError("[txHex] is inValid");
        }
        if (!AddressTool.validAddress(chainId, address)) {
            return RpcResult.paramError("[address] is inValid");
        }
        if (StringUtils.isBlank(encryptedPriKey)) {
            return RpcResult.paramError("[encryptedPriKey] is inValid");
        }
        io.nuls.core.basic.Result result = NulsSDKTool.sign(txHex, address, encryptedPriKey, password);
        return ResultUtil.getJsonRpcResult(result);
    }

    @RpcMethod("createMultiSignAccount")
    @ApiOperation(description = "??????????????????", order = 157, detailDesc = "????????????????????????????????????????????????minSigns??????????????????????????????????????????????????????")
    @Parameters(value = {
            @Parameter(parameterName = "pubKeys", requestType = @TypeDescriptor(value = List.class, collectionElement = String.class), parameterDes = "??????????????????"),
            @Parameter(parameterName = "minSigns", requestType = @TypeDescriptor(value = int.class), parameterDes = "???????????????")
    })
    @ResponseData(name = "?????????", description = "????????????Map", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", description = "???????????????")
    }))
    public RpcResult createMultiSignAccount(List<Object> params) {
        VerifyUtils.verifyParams(params, 2);
        int minSigns;
        List<String> pubKeys;

        try {
            pubKeys = (List<String>) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[pubKeys] is inValid");
        }
        try {
            minSigns = (int) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[minSigns] is inValid");
        }
        if (pubKeys.isEmpty()) {
            return RpcResult.paramError("[pubKeys] is empty");
        }
        if (minSigns < 1 || minSigns > pubKeys.size()) {
            return RpcResult.paramError("[minSigns] is inValid");
        }

        io.nuls.core.basic.Result result = NulsSDKTool.createMultiSignAccount(pubKeys, minSigns);
        return ResultUtil.getJsonRpcResult(result);
    }

    @RpcMethod("createAliasTx")
    @ApiOperation(description = "??????????????????????????????", order = 158)
    @Parameters({
            @Parameter(parameterName = "??????????????????", parameterDes = "????????????????????????", requestType = @TypeDescriptor(value = AliasDto.class))
    })
    @ResponseData(name = "?????????", description = "????????????Map??????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "hash", description = "??????hash"),
            @Key(name = "txHex", description = "???????????????16???????????????")
    }))
    public RpcResult createAliasTx(List<Object> params) {
        VerifyUtils.verifyParams(params, 3);
        String address, alias, nonce;
        try {
            address = (String) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[address] is inValid");
        }
        try {
            alias = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[alias] is inValid");
        }
        try {
            nonce = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[nonce] is inValid");
        }

        AliasDto dto = new AliasDto();
        dto.setAddress(address);
        dto.setAlias(alias);
        dto.setNonce(nonce);
        io.nuls.core.basic.Result result = NulsSDKTool.createAliasTxOffline(dto);
        return ResultUtil.getJsonRpcResult(result);
    }

    @RpcMethod("createMultiSignAliasTx")
    @ApiOperation(description = "??????????????????????????????????????????", order = 159)
    @Parameters({
            @Parameter(parameterName = "??????????????????????????????????????????", parameterDes = "????????????????????????", requestType = @TypeDescriptor(value = MultiSignAliasDto.class))
    })
    @ResponseData(name = "?????????", description = "????????????Map??????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "hash", description = "??????hash"),
            @Key(name = "txHex", description = "???????????????16???????????????")
    }))
    public RpcResult createMultiSignAliasTx(List<Object> params) {
        String address, alias, nonce, remark;
        List<String> pubKeys;
        int minSigns;
        try {
            address = (String) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[address] is inValid");
        }
        try {
            alias = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[alias] is inValid");
        }
        try {
            nonce = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[nonce] is inValid");
        }
        try {
            remark = (String) params.get(3);
        } catch (Exception e) {
            return RpcResult.paramError("[remark] is inValid");
        }
        try {
            pubKeys = (List<String>) params.get(4);
        } catch (Exception e) {
            return RpcResult.paramError("[pubKeys] is inValid");
        }
        try {
            minSigns = (int) params.get(5);
        } catch (Exception e) {
            return RpcResult.paramError("[minSigns] is inValid");
        }
        MultiSignAliasDto dto = new MultiSignAliasDto();
        dto.setAddress(address);
        dto.setAlias(alias);
        dto.setNonce(nonce);
        dto.setPubKeys(pubKeys);
        dto.setMinSigns(minSigns);
        dto.setRemark(remark);
        io.nuls.core.basic.Result result = NulsSDKTool.createMultiSignAliasTxOffline(dto);
        return ResultUtil.getJsonRpcResult(result);
    }

    @RpcMethod("getAddressByPriKey")
    @ApiOperation(description = "????????????????????????????????????", order = 160)
    @Parameters({
            @Parameter(parameterName = "????????????", parameterDes = "????????????", requestType = @TypeDescriptor(value = PriKeyForm.class))
    })
    @ResponseData(name = "?????????", description = "????????????Map??????", responseType = @TypeDescriptor(value = Map.class, mapKeys = {
            @Key(name = "value", description = "????????????")
    }))
    public RpcResult getAddressByPriKey(List<Object> params) {
        String priKey;
        try {
            priKey = (String) params.get(0);
            io.nuls.core.basic.Result result = NulsSDKTool.getAddressByPriKey(priKey);
            return ResultUtil.getJsonRpcResult(result);
        } catch (Exception e) {
            return RpcResult.paramError("[priKey] is inValid");
        }
    }


    @RpcMethod("getAddressList")
    @ApiOperation(description = "????????????????????????????????????", order = 161)
    public RpcResult getAddressList(List<Object> params) {
        Result result = accountService.getAccountList();
        if (result.isSuccess()) {
            List<String> addressList = new ArrayList<>();
            for (Object o : result.getList()) {
                AccountInfo acc = (AccountInfo) o;
                addressList.add(acc.getAddress());
            }
            result.setList(addressList);
        }
        return ResultUtil.getJsonRpcResult(result);
    }

    @RpcMethod("signMessage")
    @ApiOperation(description = "??????????????????????????????", order = 162)
    @Parameters({
            @Parameter(parameterName = "message", parameterType = "String", parameterDes = "??????"),
            @Parameter(parameterName = "privateKey", parameterType = "String", parameterDes = "??????")
    })
    @ResponseData(name = "signedMessage", description = "????????????")
    public RpcResult signMessage(List<Object> params) {
        String message, priKey;
        try {
            message = (String) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[message] is inValid");
        }
        try {
            priKey = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[priKey] is inValid");
        }
        if (StringUtils.isBlank(message)) {
            return RpcResult.paramError("[message] is inValid");
        }
        if (StringUtils.isBlank(priKey)) {
            return RpcResult.paramError("[priKey] is inValid");
        }

        ECKey ecKey = ECKey.fromPrivate(new BigInteger(1, HexUtil.decode(priKey)));
        byte[] signbytes = ecKey.sign(Utils.dataToBytes(message));
        return RpcResult.success(HexUtil.encode(signbytes));
    }

    @RpcMethod("verifySignedMessage")
    @ApiOperation(description = "??????????????????", order = 163)
    @Parameters({
            @Parameter(parameterName = "message", parameterType = "String", parameterDes = "??????"),
            @Parameter(parameterName = "signature", parameterType = "String", parameterDes = "????????????"),
            @Parameter(parameterName = "publicKey", parameterType = "String", parameterDes = "??????")
    })
    @ResponseData(description = "??????????????????", responseType = @TypeDescriptor(value = Boolean.class))
    public RpcResult verifySignedMessage(List<Object> params) {
        String message, signature, publicKey;
        try {
            message = (String) params.get(0);
        } catch (Exception e) {
            return RpcResult.paramError("[message] is inValid");
        }
        try {
            signature = (String) params.get(1);
        } catch (Exception e) {
            return RpcResult.paramError("[signature] is inValid");
        }
        try {
            publicKey = (String) params.get(2);
        } catch (Exception e) {
            return RpcResult.paramError("[publicKey] is inValid");
        }
        if (StringUtils.isBlank(message)) {
            return RpcResult.paramError("[message] is inValid");
        }
        if (StringUtils.isBlank(signature)) {
            return RpcResult.paramError("[signature] is inValid");
        }
        if (StringUtils.isBlank(publicKey)) {
            return RpcResult.paramError("[publicKey] is inValid");
        }

        boolean verify = ECKey.verify(Utils.dataToBytes(message), HexUtil.decode(signature), HexUtil.decode(publicKey));
        return RpcResult.success(verify);
    }

    @RpcMethod("getPubKeyByPriKey")
    @ApiOperation(description = "????????????????????????", order = 164)
    @Parameters({
            @Parameter(parameterName = "????????????", parameterDes = "????????????", requestType = @TypeDescriptor(value = PriKeyForm.class))
    })
    @ResponseData(name = "?????????", description = "?????????HEX???????????????")
    public RpcResult getPubKeyByPriKey(List<Object> params) {
        String priKey;
        try {
            priKey = (String) params.get(0);
            validateChainId();
            if (!ECKey.isValidPrivteHex(priKey)) {
                throw new NulsRuntimeException(AccountErrorCode.PRIVATE_KEY_WRONG);
            }
            Account account;
            try {
                if (StringUtils.isBlank(SDKContext.addressPrefix)) {
                    account = AccountTool.createAccount(SDKContext.main_chain_id, priKey);
                } else {
                    account = AccountTool.createAccount(SDKContext.main_chain_id, priKey, SDKContext.addressPrefix);
                }
            } catch (NulsException e) {
                throw new NulsRuntimeException(AccountErrorCode.PRIVATE_KEY_WRONG);
            }
            return RpcResult.success(HexUtil.encode(account.getPubKey()));
        } catch (Exception e) {
            return RpcResult.paramError("[priKey] is inValid");
        }
    }

}
