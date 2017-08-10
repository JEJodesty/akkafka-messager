import java.util.Properties

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.kstream.{KStream, KStreamBuilder, ValueMapper}
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}

object KafkaStreams {

  def main(args: Array[String]): Unit = {
    val settings = new Properties
    settings.put(StreamsConfig.APPLICATION_ID_CONFIG, "hello-kafka-streams")
    settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    settings.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
    settings.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, Serdes.serdeFrom(classOf[String]).getClass.getName)
    settings.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, Serdes.serdeFrom(classOf[String]).getClass.getName)

    val kstreamBuilder = new KStreamBuilder
    val rawStream: KStream[String, String] = kstreamBuilder.stream("chat")

    val helloStream: KStream[String, String] = rawStream.mapValues(new ValueMapper[String, String]{
      override def apply(value: String): String = s"hello $value"
    })

    helloStream.to(Serdes.String, Serdes.String, "hellostream")

    val streams = new KafkaStreams(kstreamBuilder, settings)
    streams.start
  }
}
