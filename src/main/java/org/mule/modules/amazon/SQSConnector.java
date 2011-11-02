/**
 * Mule Amazon Connector
 *
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.modules.amazon;

import com.xerox.amazonws.sqs2.Message;
import com.xerox.amazonws.sqs2.MessageQueue;
import com.xerox.amazonws.sqs2.QueueAttribute;
import com.xerox.amazonws.sqs2.SQSException;
import com.xerox.amazonws.sqs2.SQSUtils;
import org.mule.api.ConnectionException;
import org.mule.api.ConnectionExceptionCode;
import org.mule.api.annotations.*;
import org.mule.api.annotations.param.ConnectionKey;
import org.mule.api.annotations.param.Default;
import org.mule.api.annotations.param.Optional;
import org.mule.api.annotations.param.Payload;
import org.mule.api.callback.SourceCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Amazon Simple Queue Service (Amazon SQS) is a distributed queue messaging service introduced by Amazon.com in
 * April of 2006. It supports programmatic sending of messages via web service applications as a way to communicate
 * over the internet. The intent of SQS is to provide a highly scalable hosted message queue that resolves issues
 * arising from the common producer-consumer problem or connectivity between producer and consumer.
 * <p/>
 * This connector does not provide a method for creating a queue. The reason being that it will automatically
 * create it when its needed instead of having to manually specify so.
 *
 * @author MuleSoft, Inc.
 */
@Connector(name = "sqs")
public class SQSConnector {
    private static Logger logger = LoggerFactory.getLogger(SQSConnector.class);

    /**
     * AWS access id
     */
    @Configurable
    private String accessKey;

    /**
     * AWS secret access id
     */
    @Configurable
    private String secretAccessKey;

    /**
     * Message Queue
     */
    private MessageQueue msgQueue;

    /**
     * @param queueName The name of the queue to connect to
     * @throws ConnectionException If a connection cannot be made
     */
    @Connect
    public void connect(@ConnectionKey String queueName)
            throws ConnectionException {
        try {
            MessageQueue msgQueue = SQSUtils.connectToQueue(queueName, accessKey, secretAccessKey);
            msgQueue.setEncoding(false);
        } catch (SQSException e) {
            throw new ConnectionException(ConnectionExceptionCode.UNKNOWN, null, e.getMessage(), e);
        }
    }

    @Disconnect
    public void disconnect() {
        msgQueue = null;
    }
    
    @ValidateConnection
    public boolean isConnected() {
        return this.msgQueue != null;
    }
    
    @ConnectionIdentifier
    public String getMessageQueueUrl() {
        return this.msgQueue.getUrl().toString();
    }

    /**
     * Sends a message to a specified queue. The message must be between 1 and 256K bytes long.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:send-message}
     *
     * @param payload Payload of the message to send. Defaults to the payload of the Mule message.
     * @throws SQSException if something goes wrong
     */
    @Processor
    public void sendMessage(@Payload final byte[] payload) throws SQSException {
        msgQueue.sendMessage(new String(org.apache.commons.codec.binary.Base64.encodeBase64(payload)));
    }

    /**
     * This method provides the URL for the message queue represented by this object.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:get-url}
     *
     * @return generated queue service url
     */
    @Processor
    public URL getUrl() {
        return msgQueue.getUrl();
    }

    /**
     * Attempts to receive a message from the queue. Every attribute of the incoming
     * message will be added as an inbound property. Also the following properties
     * will also be added:
     * <p/>
     * sqs.message.id = containing the message identification
     * sqs.message.receipt.handle = containing the message identification
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:receive-messages}
     *
     * @param callback Callback to call when a new message is available.
     */
    @Source
    public void receiveMessages(SourceCallback callback) {
        Message msg = null;
        while (true) {
            try {
                msg = msgQueue.receiveMessage();
                if (msg == null) {
                    try {
                        Thread.sleep(1000);
                    } catch (Exception ex) {
                    }
                    continue;
                }
                msgQueue.deleteMessage(msg);

                Map<String, Object> properties = new HashMap<String, Object>();
                properties.putAll(msg.getAttributes());
                properties.put("sqs.message.id", msg.getMessageId());
                properties.put("sqs.message.receipt.handle", msg.getReceiptHandle());

                callback.process(msg.getMessageBody(), properties);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Deletes the message identified by message object on the queue this object represents.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:delete-message}
     *
     * @param receiptHandle Receipt handle of the message to be deleted
     */
    @Processor
    public void deleteMessage(@Optional @Default("#[header:inbound:sqs.message.receipt.handle]") String receiptHandle)
            throws SQSException {
        msgQueue.deleteMessage(receiptHandle);
    }

    /**
     * Deletes the message queue represented by this object. Will delete non-empty queue.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:delete-queue}
     *
     * @throws SQSException wraps checked exceptions
     */
    @Processor
    public void deleteQueue() throws SQSException {
        msgQueue.deleteQueue();
    }

    /**
     * Gets queue attributes. This is provided to expose the underlying functionality.
     * Currently supported attributes are;
     * ApproximateNumberOfMessages
     * CreatedTimestamp
     * LastModifiedTimestamp
     * VisibilityTimeout
     * RequestPayer
     * Policy
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:get-queue-attributes}
     *
     * @param attribute Attribute to get
     * @return a map of attributes and their values
     * @throws SQSException wraps checked exceptions
     */
    @Processor
    public Map<String, String> getQueueAttributes(QueueAttribute attribute) throws SQSException {
        return msgQueue.getQueueAttributes(attribute);
    }

    /**
     * Sets a queue attribute. This is provided to expose the underlying functionality, although
     * the only attribute at this time is visibility timeout.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:set-queue-attribute}
     *
     * @param attribute name of the attribute being set
     * @param value     the value being set for this attribute
     * @throws SQSException wraps checked exceptions
     */
    @Processor
    public void setQueueAttribute(QueueAttribute attribute, String value) throws SQSException {
        msgQueue.setQueueAttribute(attribute.name(), value);
    }

    /**
     * Adds a permission to this message queue.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:add-permission}
     *
     * @param label     a name for this permission
     * @param accountId the AWS account ID for the account to share this queue with
     * @param action    a value to indicate how much to share (SendMessage, ReceiveMessage, ChangeMessageVisibility, DeleteMessage, GetQueueAttributes)
     * @throws SQSException wraps checked exceptions
     */
    @Processor
    public void addPermission(String label, String accountId, String action) throws SQSException {
        msgQueue.addPermission(label, accountId, action);
    }

    /**
     * Removes a permission from this message queue.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:remove-permission}
     *
     * @param label a name for the permission to be removed
     * @throws SQSException wraps checked exceptions
     */
    @Processor
    public void removePermission(String label) throws SQSException {
        msgQueue.removePermission(label);
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }
}
