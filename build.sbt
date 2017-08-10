name := "akkafka-messager"

scalaVersion := "2.11.8"

val kafkaStreamsVersion = "0.10.2.0"

val kafkaDependencies = Seq(
  "org.apache.kafka" % "kafka-streams" % kafkaStreamsVersion,
  "org.apache.kafka" % "kafka-clients" % kafkaStreamsVersion
)

val otherDependencies = Seq(
  "com.esotericsoftware.kryo" % "kryo" % "2.24.0",
  "com.typesafe.akka" %% "akka-http-core" % "2.4.4",
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.4"
)

lazy val root = (project in file(".")).
  settings(
    libraryDependencies ++= kafkaDependencies,
    libraryDependencies ++= otherDependencies
  )
