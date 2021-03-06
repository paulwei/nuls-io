/**
 * MIT License
 * <p>
 * Copyright (c) 2017-2019 nuls.io
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.contract.model.dto;

import io.nuls.base.basic.AddressTool;
import io.nuls.contract.enums.CmdRegisterMode;
import io.nuls.contract.model.bo.ContractMergedTransfer;
import io.nuls.contract.model.bo.ContractMultyAssetMergedTransfer;
import io.nuls.contract.model.bo.ContractResult;
import io.nuls.contract.model.tx.ContractBaseTransaction;
import io.nuls.contract.model.txdata.ContractData;
import io.nuls.contract.util.ContractUtil;
import io.nuls.contract.vm.program.ProgramInvokeRegisterCmd;
import io.nuls.core.constant.TxType;
import io.nuls.core.exception.NulsException;
import io.nuls.core.model.LongUtils;
import io.nuls.core.rpc.model.ApiModel;
import io.nuls.core.rpc.model.ApiModelProperty;
import io.nuls.core.rpc.model.TypeDescriptor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static io.nuls.contract.constant.ContractConstant.TOKEN_TYPE_NRC20;
import static io.nuls.contract.constant.ContractConstant.TOKEN_TYPE_NRC721;
import static io.nuls.contract.util.ContractUtil.bigInteger2String;

/**
 * @author: PierreLuo
 */
@ApiModel
public class ContractResultDto {
    @ApiModelProperty(description = "????????????????????????")
    private boolean success;
    @ApiModelProperty(description = "??????????????????")
    private String errorMessage;
    @ApiModelProperty(description = "????????????")
    private String contractAddress;
    @ApiModelProperty(description = "??????????????????")
    private String result;
    @ApiModelProperty(description = "GAS??????")
    private long gasLimit;
    @ApiModelProperty(description = "?????????GAS")
    private long gasUsed;
    @ApiModelProperty(description = "GAS??????")
    private long price;
    @ApiModelProperty(description = "??????????????????")
    private String totalFee;
    @ApiModelProperty(description = "?????????????????????")
    private String txSizeFee;
    @ApiModelProperty(description = "???????????????????????????")
    private String actualContractFee;
    @ApiModelProperty(description = "????????????????????????")
    private String refundFee;
    @ApiModelProperty(description = "??????????????????????????????????????????????????????????????????????????????0")
    private String value;
    @ApiModelProperty(description = "??????????????????")
    private String stackTrace;
    @ApiModelProperty(description = "????????????????????????????????????????????????", type = @TypeDescriptor(value = List.class, collectionElement = ContractMergedTransferDto.class))
    private List<ContractMergedTransferDto> transfers;
    @ApiModelProperty(description = "???????????????????????????????????????????????????", type = @TypeDescriptor(value = List.class, collectionElement = ContractMultyAssetMergedTransferDto.class))
    private List<ContractMultyAssetMergedTransferDto> multyAssetTransfers;
    @ApiModelProperty(description = "??????????????????", type = @TypeDescriptor(value = List.class, collectionElement = String.class))
    private List<String> events;
    @ApiModelProperty(description = "????????????????????????", type = @TypeDescriptor(value = List.class, collectionElement = String.class))
    private List<String> debugEvents;
    @ApiModelProperty(description = "??????token????????????", type = @TypeDescriptor(value = List.class, collectionElement = ContractTokenTransferDto.class))
    private List<ContractTokenTransferDto> tokenTransfers;
    @ApiModelProperty(description = "??????NRC721-token????????????", type = @TypeDescriptor(value = List.class, collectionElement = ContractToken721TransferDto.class))
    private List<ContractToken721TransferDto> token721Transfers;
    @ApiModelProperty(description = "?????????????????????????????????????????????", type = @TypeDescriptor(value = List.class, collectionElement = ContractInvokeRegisterCmdDto.class))
    private List<ContractInvokeRegisterCmdDto> invokeRegisterCmds;
    @ApiModelProperty(description = "?????????????????????????????????????????????", type = @TypeDescriptor(value = List.class, collectionElement = String.class))
    private List<String> contractTxList;
    @ApiModelProperty(description = "??????")
    private String remark;

    public ContractResultDto() {
    }

    public ContractResultDto(int chainId, ContractResult result, ContractBaseTransaction tx) throws NulsException {
        ContractData contractData = (ContractData) tx.getTxDataObj();
        this.gasLimit = contractData.getGasLimit();
        this.gasUsed = result.getGasUsed();
        this.price = result.getPrice();
        BigInteger totalFee = tx.getCoinDataObj().getFeeByAsset(chainId, 1);
        this.totalFee = bigInteger2String(totalFee);
        // pierre ??????
        if(tx.getType() == TxType.CROSS_CHAIN) {
            this.txSizeFee = this.totalFee;
            // end code by pierre 
        } else {
            BigInteger actualContractFee = BigInteger.valueOf(LongUtils.mul(this.gasUsed, this.price));
            this.actualContractFee = bigInteger2String(actualContractFee);
            BigInteger contractFee = BigInteger.valueOf(LongUtils.mul(gasLimit, price));
            this.refundFee = bigInteger2String(contractFee.subtract(actualContractFee));
            this.txSizeFee = bigInteger2String(totalFee.subtract(contractFee));
        }
        this.value = String.valueOf(result.getValue());
        this.contractAddress = AddressTool.getStringAddressByBytes(result.getContractAddress());
        this.result = result.getResult();
        this.success = result.isSuccess();
        this.errorMessage = result.getErrorMessage();
        this.stackTrace = result.getStackTrace();
        this.setMergedTransfersDto(result.getMergedTransferList());
        this.setMergedMultyAssetTransfersDto(result.getMergerdMultyAssetTransferList());
        this.events = result.getEvents();
        this.debugEvents = result.getDebugEvents();
        this.remark = result.getRemark();
        this.invokeRegisterCmds = new LinkedList<>();
        this.contractTxList = new ArrayList<>();
        this.contractTxList.addAll(result.getContractTransferTxStringList());
        if (result.isSuccess()) {
            this.makeTokenTransfers(chainId, result.getEvents());
            this.makeInvokeRegisterCmds(result.getInvokeRegisterCmds());
        }
    }

    public ContractResultDto(int chainId, ContractResult result, long gasLimit) throws NulsException {
        this.gasLimit = gasLimit;
        this.gasUsed = result.getGasUsed();
        this.price = result.getPrice();
        BigInteger actualContractFee = BigInteger.valueOf(LongUtils.mul(this.gasUsed, this.price));
        this.actualContractFee = bigInteger2String(actualContractFee);
        BigInteger contractFee = BigInteger.valueOf(LongUtils.mul(gasLimit, price));
        this.refundFee = bigInteger2String(contractFee.subtract(actualContractFee));
        this.value = String.valueOf(result.getValue());
        this.contractAddress = AddressTool.getStringAddressByBytes(result.getContractAddress());
        this.result = result.getResult();
        this.success = result.isSuccess();
        this.errorMessage = result.getErrorMessage();
        this.stackTrace = result.getStackTrace();
        this.setMergedTransfersDto(result.getMergedTransferList());
        this.setMergedMultyAssetTransfersDto(result.getMergerdMultyAssetTransferList());
        this.events = result.getEvents();
        this.debugEvents = result.getDebugEvents();
        this.remark = result.getRemark();
        this.invokeRegisterCmds = new LinkedList<>();
        this.contractTxList = new ArrayList<>();
        this.contractTxList.addAll(result.getContractTransferTxStringList());
        if (result.isSuccess()) {
            this.makeTokenTransfers(chainId, result.getEvents());
            this.makeInvokeRegisterCmds(result.getInvokeRegisterCmds());
        }
    }

    public void setMultyAssetTransfers(List<ContractMultyAssetMergedTransferDto> multyAssetTransfers) {
        this.multyAssetTransfers = multyAssetTransfers;
    }

    private void makeInvokeRegisterCmds(List<ProgramInvokeRegisterCmd> invokeRegisterCmds) {
        if(invokeRegisterCmds == null || invokeRegisterCmds.isEmpty()) {
            return;
        }
        for(ProgramInvokeRegisterCmd invokeRegisterCmd : invokeRegisterCmds) {
            if(CmdRegisterMode.NEW_TX.equals(invokeRegisterCmd.getCmdRegisterMode())) {
                contractTxList.add(invokeRegisterCmd.getProgramNewTx().getTxString());
            }
            this.invokeRegisterCmds.add(new ContractInvokeRegisterCmdDto(invokeRegisterCmd));
        }
    }

    public List<ContractTokenTransferDto> getTokenTransfers() {
        return tokenTransfers == null ? new ArrayList<>() : tokenTransfers;
    }

    public void setTokenTransfers(List<ContractTokenTransferDto> tokenTransfers) {
        this.tokenTransfers = tokenTransfers;
    }

    public List<ContractToken721TransferDto> getToken721Transfers() {
        return token721Transfers == null ? new ArrayList<>() : token721Transfers;
    }

    public void setToken721Transfers(List<ContractToken721TransferDto> token721Transfers) {
        this.token721Transfers = token721Transfers;
    }

    private void makeTokenTransfers(int chainId, List<String> tokenTransferEvents) {
        List<ContractTokenTransferDto> result = new ArrayList<>();
        List<ContractToken721TransferDto> result721 = new ArrayList<>();
        if (tokenTransferEvents != null && tokenTransferEvents.size() > 0) {
            ContractTokenTransferInfo info;
            for (String event : tokenTransferEvents) {
                info = ContractUtil.convertJsonToTokenTransferInfo(chainId, event);
                if (info != null) {
                    if (TOKEN_TYPE_NRC20 == info.getTokenType()) {
                        result.add(new ContractTokenTransferDto(info));
                    } else if (TOKEN_TYPE_NRC721 == info.getTokenType()) {
                        result721.add(new ContractToken721TransferDto(info));
                    }
                }
            }
        }
        this.tokenTransfers = result;
        this.token721Transfers = result721;
    }

    public List<ContractMergedTransferDto> getTransfers() {
        return transfers == null ? new ArrayList<>() : transfers;
    }


    public void setMergedTransfersDto(List<ContractMergedTransfer> transferList) {
        List<ContractMergedTransferDto> list = new LinkedList<>();
        this.transfers = list;
        if (transferList == null || transferList.size() == 0) {
            return;
        }
        for (ContractMergedTransfer transfer : transferList) {
            list.add(new ContractMergedTransferDto(transfer));
        }
    }

    public List<ContractMultyAssetMergedTransferDto> getMultyAssetTransfers() {
        return multyAssetTransfers;
    }

    private void setMergedMultyAssetTransfersDto(List<ContractMultyAssetMergedTransfer> mergerdMultyAssetTransferList) {
        List<ContractMultyAssetMergedTransferDto> list = new LinkedList<>();
        this.multyAssetTransfers = list;
        if (mergerdMultyAssetTransferList == null || mergerdMultyAssetTransferList.size() == 0) {
            return;
        }
        for (ContractMultyAssetMergedTransfer transfer : mergerdMultyAssetTransferList) {
            list.add(new ContractMultyAssetMergedTransferDto(transfer));
        }
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public long getGasLimit() {
        return gasLimit;
    }

    public void setGasLimit(long gasLimit) {
        this.gasLimit = gasLimit;
    }

    public long getGasUsed() {
        return gasUsed;
    }

    public void setGasUsed(long gasUsed) {
        this.gasUsed = gasUsed;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public String getTotalFee() {
        return totalFee;
    }

    public void setTotalFee(String totalFee) {
        this.totalFee = totalFee;
    }

    public String getTxSizeFee() {
        return txSizeFee;
    }

    public void setTxSizeFee(String txSizeFee) {
        this.txSizeFee = txSizeFee;
    }

    public String getActualContractFee() {
        return actualContractFee;
    }

    public void setActualContractFee(String actualContractFee) {
        this.actualContractFee = actualContractFee;
    }

    public String getRefundFee() {
        return refundFee;
    }

    public void setRefundFee(String refundFee) {
        this.refundFee = refundFee;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public void setTransfers(List<ContractMergedTransferDto> transfers) {
        this.transfers = transfers;
    }

    public List<String> getEvents() {
        return events;
    }

    public void setEvents(List<String> events) {
        this.events = events;
    }

    public List<String> getDebugEvents() {
        return debugEvents;
    }

    public void setDebugEvents(List<String> debugEvents) {
        this.debugEvents = debugEvents;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<ContractInvokeRegisterCmdDto> getInvokeRegisterCmds() {
        return invokeRegisterCmds;
    }

    public void setInvokeRegisterCmds(List<ContractInvokeRegisterCmdDto> invokeRegisterCmds) {
        this.invokeRegisterCmds = invokeRegisterCmds;
    }

    public List<String> getContractTxList() {
        return contractTxList;
    }

    public void setContractTxList(List<String> contractTxList) {
        this.contractTxList = contractTxList;
    }
}
