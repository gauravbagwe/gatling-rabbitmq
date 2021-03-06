package net.fawad.rabbitmqloadgen

import com.rabbitmq.client.{Channel, ConnectionFactory}
import com.typesafe.scalalogging.slf4j.Logger
import org.slf4j.LoggerFactory
import akka.actor.Actor
import resource.managed
import scala.util.{Failure, Success}

class RabbitMQInteractor(connectionInfo: ConnectionInfo) extends Actor {
  val conn = createConnection(connectionInfo)
  protected lazy val logger: Logger =
    Logger(LoggerFactory getLogger getClass.getName)

  override def receive = {
    case Publish(msg, exchangeInfo) => onChannel {
      channel =>
        logger.debug("Publishing message")
        try {
          channel.basicPublish(exchangeInfo.name, "", msg.properties, msg.body)
          sender ! Success(null)
        } catch {
          case e: Throwable => sender ! Failure(e)
        }

    }
    case InitializeSubscriber(exchangeInfo) =>
      onChannel {
        channel =>
          logger.info(s"Initializing RabbitMQ exchange ${exchangeInfo.name}")
          channel.exchangeDeclare(exchangeInfo.name, exchangeInfo.exchangeType, true)
      }
      onChannel {
        channel =>
          logger.info(s"Initializing RabbitMQ queue ${exchangeInfo.name}")
          channel.queueDeclare(exchangeInfo.name, true, false, false, null)
      }
      onChannel {
        channel =>
          logger.info(s"Initializing RabbitMQ binding ${exchangeInfo.name}")
          channel.queueBind(exchangeInfo.name, exchangeInfo.name, "")
      }
      sender ! Success()
  }

  def onChannel[A](action: Channel => A) = {
    for (channel <- managed(conn.createChannel())) {
      action(channel)
    }
  }

  def createConnection(connectionInfo: ConnectionInfo) = {
    val factory = new ConnectionFactory()
    factory.setHost(connectionInfo.hostname)
    factory.setPort(connectionInfo.port)
    factory.setUsername(connectionInfo.userName)
    factory.setPassword(connectionInfo.password)
    factory.setVirtualHost("/")
    factory.newConnection()
  }
}
