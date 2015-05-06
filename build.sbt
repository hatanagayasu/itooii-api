name := """itooii-api"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  filters,
  javaJdbc,
  javaEbean,
  cache,
  javaWs,
  "com.amazonaws" % "aws-java-sdk" % "1.9.21",
  "com.google.guava" % "guava" % "16.0.1",
  "com.sendgrid" % "sendgrid-java" % "2.2.0",
  "org.jongo" % "jongo" % "1.1",
  "org.mongodb" % "mongo-java-driver" % "2.13.0",
  "org.projectlombok" % "lombok" % "1.16.2",
  "redis.clients"% "jedis" % "2.6.2"
)

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
