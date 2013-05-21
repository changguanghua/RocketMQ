/**
 * 
 */
package com.alibaba.rocketmq.client.impl;

import io.netty.channel.ChannelHandlerContext;

import java.nio.ByteBuffer;

import org.slf4j.Logger;

import com.alibaba.rocketmq.client.impl.factory.MQClientFactory;
import com.alibaba.rocketmq.client.impl.producer.MQProducerInner;
import com.alibaba.rocketmq.client.producer.LocalTransactionState;
import com.alibaba.rocketmq.client.producer.TransactionCheckListener;
import com.alibaba.rocketmq.common.Message;
import com.alibaba.rocketmq.common.MessageDecoder;
import com.alibaba.rocketmq.common.MessageExt;
import com.alibaba.rocketmq.common.protocol.MQProtos.MQRequestCode;
import com.alibaba.rocketmq.common.protocol.header.CheckTransactionStateRequestHeader;
import com.alibaba.rocketmq.remoting.exception.RemotingCommandException;
import com.alibaba.rocketmq.remoting.netty.NettyRequestProcessor;
import com.alibaba.rocketmq.remoting.protocol.RemotingCommand;


/**
 * Client����Broker�Ļص���������������ص���������������������ص�
 * 
 * @author vintage.wang@gmail.com shijia.wxr@taobao.com
 */
public class ClientRemotingProcessor implements NettyRequestProcessor {
    private final Logger log;
    private final MQClientFactory mqClientFactory;


    public ClientRemotingProcessor(final MQClientFactory mqClientFactory, final Logger log) {
        this.log = log;
        this.mqClientFactory = mqClientFactory;
    }


    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request)
            throws RemotingCommandException {
        MQRequestCode code = MQRequestCode.valueOf(request.getCode());
        switch (code) {
        case CHECK_TRANSACTION_STATE:
            return this.checkTransactionState(ctx, request);
        default:
            break;
        }
        return null;
    }


    private void processTransactionState(//
            final ChannelHandlerContext ctx,//
            final CheckTransactionStateRequestHeader requestHeader,//
            final LocalTransactionState localTransactionState,//
            final Throwable exception) {
        // TODO
    }


    /**
     * Oneway���ã��޷���ֵ
     */
    public RemotingCommand checkTransactionState(ChannelHandlerContext ctx, RemotingCommand request)
            throws RemotingCommandException {
        final CheckTransactionStateRequestHeader requestHeader =
                (CheckTransactionStateRequestHeader) request
                    .decodeCommandCustomHeader(CheckTransactionStateRequestHeader.class);
        final ByteBuffer byteBuffer = ByteBuffer.wrap(request.getBody());
        final MessageExt messageExt = MessageDecoder.decode(byteBuffer);
        if (messageExt != null) {
            final String group = messageExt.getProperty(Message.PROPERTY_PRODUCER_GROUP);
            if (group != null) {
                MQProducerInner producer = this.mqClientFactory.selectProducer(group);
                if (producer != null) {
                    TransactionCheckListener transactionCheckListener = producer.checkListener();
                    if (transactionCheckListener != null) {
                        LocalTransactionState localTransactionState = LocalTransactionState.UNKNOW;
                        Throwable exception = null;
                        try {
                            localTransactionState =
                                    transactionCheckListener.checkLocalTransactionState(messageExt);
                        }
                        catch (Throwable e) {
                            log.error("checkTransactionState, checkLocalTransactionState exception", e);
                            exception = e;
                        }

                        this.processTransactionState(ctx, requestHeader, localTransactionState, exception);
                    }
                    else {
                        log.warn("checkTransactionState, pick transactionCheckListener by group[{}] failed", group);
                    }
                }
                else {
                    log.debug("checkTransactionState, pick producer by group[{}] failed", group);
                }
            }
            else {
                log.warn("checkTransactionState, pick producer group failed");
            }
        }
        else {
            log.warn("checkTransactionState, decode message failed");
        }

        return null;
    }
}