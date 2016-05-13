name := """itooii-api"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  cache,
  javaWs,
  "com.amazonaws" % "aws-java-sdk" % "1.9.21",
  "com.drewnoakes" % "metadata-extractor" % "2.8.1",
  "com.sendgrid" % "sendgrid-java" % "2.2.0",
  "net.bramp.ffmpeg" % "ffmpeg" % "0.2",
  "org.imgscalr" % "imgscalr-lib" % "4.2",
  "org.jongo" % "jongo" % "1.1",
  "org.mongodb" % "mongo-java-driver" % "2.13.0",
  "org.projectlombok" % "lombok" % "1.16.2",
  "redis.clients"% "jedis" % "2.6.2"
)

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false

routesGenerator := InjectedRoutesGenerator
