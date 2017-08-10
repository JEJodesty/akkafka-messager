import java.util.Properties

import actors.{ChatRoom, User}
import akka.NotUsed
import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream._
import akka.stream.scaladsl._

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.kstream.{KStream, KStreamBuilder, ValueMapper}
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn

object AkKafkaServer {
  implicit class RichPipes[Y](y: Y) { def |>[Z](f: Y => Z) = f(y) }
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val kafkaProducer = {
      val kafkaProps = new Properties()
      kafkaProps.put("bootstrap.servers", "localhost:9092")
      kafkaProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
      kafkaProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
      new KafkaProducer[String, String](kafkaProps)
    }


    val stream = {
      val kstreamBuilder = new KStreamBuilder
      val rawStream: KStream[String, String] = kstreamBuilder.stream("channelIn")

      val helloStream: KStream[String, String] = rawStream.mapValues(new ValueMapper[String, String]{
        override def apply(value: String): String = s"Kafka - $value"
      })

      helloStream.to(Serdes.String, Serdes.String, "channelOut")

      val streamProps = new Properties
      streamProps.put(StreamsConfig.APPLICATION_ID_CONFIG, "AkKafka")
      streamProps.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
      streamProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
      streamProps.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, Serdes.serdeFrom(classOf[String]).getClass.getName)
      streamProps.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, Serdes.serdeFrom(classOf[String]).getClass.getName)
      new KafkaStreams(kstreamBuilder, streamProps)
    }
    stream.start

    def produce(TOPIC: String, msg: String) = kafkaProducer.send(new ProducerRecord(TOPIC, "key", msg))


    val chatRoom = system.actorOf(Props(new ChatRoom), "chat")


    def newUser(): Flow[Message, Message, NotUsed] = {
      // new connection - new user actor

      val userActor = system.actorOf(Props(new User(chatRoom)))


      val incomingMessages: Sink[Message, NotUsed] =
        Flow[Message].map {
          // transform websocket message to domain message
          case TextMessage.Strict(text) => {
            produce("channelIn", text)
            User.IncomingMessage(text)
          }
        }.to(Sink.actorRef[User.IncomingMessage](userActor, PoisonPill))

      val outgoingMessages: Source[Message, NotUsed] =
        Source.actorRef[User.OutgoingMessage](10, OverflowStrategy.fail)
          .mapMaterializedValue { outActor =>
            // give the user actor a way to send messages out
            userActor ! User.Connected(outActor)
            NotUsed
          }.map(
          // transform domain message to web socket message
          (outMsg: User.OutgoingMessage) => TextMessage(outMsg.text))

      // then combine both to a flow
      Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
    }

    def router(endPoint: String) = {
      path(endPoint) {
        get {
          handleWebSocketMessages(newUser())
        }
      }
    }

    val routes = router("chat") ~ router("scala")

    val binding = Await.result(Http().bindAndHandle(routes, "127.0.0.1", 8080), 3.seconds)


    // the rest of the sample code will go here
    println("Started server at 127.0.0.1:8080, press enter to kill server")
    StdIn.readLine()
    system.terminate()
  }
}
