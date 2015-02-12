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
  "org.projectlombok" % "lombok" % "1.16.2"
)
