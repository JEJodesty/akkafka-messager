package chat

import akka.NotUsed
import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.io.StdIn

import java.util.concurrent.{Future => JFuture}
import java.util.Properties
import org.apache.kafka.clients.producer._
import org.apache.kafka.clients.producer.RecordMetadata


object Server {
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

    type NewUser = Flow[Message, Message, NotUsed]

//    def produce(TOPIC: String, msg: String): JFuture[RecordMetadata] = kafkaProducer.send(new ProducerRecord(TOPIC, "key", msg))

    def newUser(group: String)(chatRoom: ActorRef): NewUser = {
      // new connection - new user actor
      val userActor = chatRoom |> createActor

      def produce(TOPIC: String, msg: String) = kafkaProducer.send(new ProducerRecord(TOPIC, "key", msg))

      val incomingMessages: Sink[Message, NotUsed] =
        Flow[Message].map {
          // transform websocket message to domain message
          case TextMessage.Strict(text) => {
            produce(group, text)
            User.IncomingMessage(text)
          }
        }.to(Sink.actorRef[User.IncomingMessage](userActor, PoisonPill))


      val outgoingMessages: Source[Message, NotUsed] = {
        Source.actorRef[User.OutgoingMessage](10, OverflowStrategy.fail)
          .mapMaterializedValue { outActor =>
            // give the user actor a way to send messages out
            userActor ! User.Connected(outActor)
            NotUsed
          }.map(
          // transform domain message to web socket message
          (outMsg: User.OutgoingMessage) => {
            TextMessage(outMsg.text)
          }
        )
      }


      // then combine both to a flow
      Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
    }

    def createChatRoom(group: String): ActorRef = system.actorOf(Props(new ChatRoom), group)

    def createActor(chatRoom: ActorRef) = system.actorOf(Props(new User(chatRoom)))

    def router(endPoint: String) = {
      val _newUser = endPoint |> newUser
      path(endPoint) {
        get {
          handleWebSocketMessages(endPoint |> createChatRoom |> _newUser)
        }
      }
    }

//    val routes = router("chat") ~ router("scala")

    val binding = Await.result(Http().bindAndHandle(router("chat"), "127.0.0.1", 8080), 3.seconds)


    // the rest of the sample code will go here
    println("Started server at 127.0.0.1:8080, press enter to kill server")
    StdIn.readLine()
    system.terminate()
  }
}
