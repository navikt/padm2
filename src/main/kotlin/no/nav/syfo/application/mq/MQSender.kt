package no.nav.syfo.application.mq

import com.ibm.msg.client.wmq.common.CommonConstants.*
import no.nav.syfo.Environment
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.jms.JMSContext
import javax.jms.Message

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.application.mq")

interface MQSenderInterface {
    fun sendArena(payload: String)
    fun sendReceipt(payload: String)
    fun sendBackout(message: Message)
}

class MQSender(
    val env: Environment,
) : MQSenderInterface {

    private val jmsContext: JMSContext = connectionFactory(env).createContext(env.serviceuserUsername, env.serviceuserPassword)

    protected fun finalize() {
        try {
            jmsContext.close()
        } catch (exc: Exception) {
            log.warn("Got exception when closing MQ-connection", exc)
        }
    }

    override fun sendArena(payload: String) {
        val queueName = env.arenaQueueName
        send(queueName, payload)
    }

    override fun sendReceipt(payload: String) {
        val queueName = env.apprecQueueName
        send(queueName, payload)
    }

    override fun sendBackout(message: Message) {
        val queueName = env.inputBackoutQueueName
        jmsContext.createContext(AUTO_ACKNOWLEDGE).use { context ->
            val destination = context.createQueue("queue:///$queueName")
            context.createProducer().send(destination, message)
        }
    }

    private fun send(queueName: String, payload: String) {
        jmsContext.createContext(AUTO_ACKNOWLEDGE).use { context ->
            val destination = context.createQueue("queue:///$queueName")
            val message = context.createTextMessage(payload)
            context.createProducer().send(destination, message)
        }
    }
}
