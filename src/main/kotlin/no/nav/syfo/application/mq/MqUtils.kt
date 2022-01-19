package no.nav.syfo.application.mq

import com.ibm.mq.jms.MQConnectionFactory
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.wmq.WMQConstants
import com.ibm.msg.client.wmq.compat.base.internal.MQC
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.QueueBrowser
import javax.jms.Session

interface MqConfig {
    val mqHostname: String
    val mqPort: Int
    val mqGatewayName: String
    val mqChannelName: String
}

fun connectionFactory(config: MqConfig) = MQConnectionFactory().apply {
    hostName = config.mqHostname
    port = config.mqPort
    queueManager = config.mqGatewayName
    transportType = WMQConstants.WMQ_CM_CLIENT
    channel = config.mqChannelName
    ccsid = 1208
    setIntProperty(WMQConstants.JMS_IBM_ENCODING, MQC.MQENC_NATIVE)
    setIntProperty(WMQConstants.JMS_IBM_CHARACTER_SET, 1208)
    setBooleanProperty(JmsConstants.USER_AUTHENTICATION_MQCSP, true)
}

fun Session.consumerForQueue(queueName: String): MessageConsumer = createConsumer(createQueue(queueName))
fun Session.producerForQueue(queueName: String): MessageProducer = createProducer(createQueue(queueName))
fun Session.createBrowserForQueue(queueName: String): QueueBrowser = createBrowser(createQueue(queueName))
