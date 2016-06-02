name := "service-repo-abstraction"

version := "1.0"

scalaVersion := "2.11.8"

scalacOptions ++= Seq(
  "-target:jvm-1.8",
  "-deprecation",
  "-encoding", "UTF-8",
  "-unchecked",
  "-Xfatal-warnings",
  "-feature",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-Ywarn-dead-code"
)

libraryDependencies ++= Seq(
  "com.h2database"     %  "h2"              % "1.4.185",
  "ch.qos.logback"     %  "logback-classic" % "1.1.2",
  "com.typesafe.slick" %% "slick"           % "3.1.1"
)
