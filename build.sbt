import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._
import com.typesafe.sbt.SbtAspectj._

packageArchetype.java_application

name := """heimdall"""

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.10.3"

libraryDependencies ++={
  val akkaV = "2.3.6"
  val sprayV = "1.3.2"
  val kamonVersion = "0.3.5"
  Seq(
    "io.spray"            %%  "spray-can"            % sprayV,
    "io.spray"            %%  "spray-caching"        % sprayV,
    "io.spray"            %%  "spray-routing"        % sprayV,
    "io.spray"            %%  "spray-json"           % "1.3.1",
    "com.typesafe.akka"   %%  "akka-actor"           % akkaV,
    "org.jsoup"           %   "jsoup"                % "1.7.3",
    "org.json4s"          %%  "json4s-native"        % "3.2.4",
    "org.json4s"          %%  "json4s-jackson"       % "3.2.4",
    "org.slf4j"           %   "slf4j-api"            % "1.7.5",
    "ch.qos.logback"      %   "logback-classic"      % "1.0.13",
    "io.kamon"            %%  "kamon-core"           % kamonVersion,
    "io.kamon"            %%  "kamon-statsd"         % kamonVersion,
    "io.kamon"            %%  "kamon-log-reporter"   % kamonVersion,
    "io.kamon"            %%  "kamon-spray"          % kamonVersion,
    "io.kamon"            %%  "kamon-system-metrics" % kamonVersion,
    "org.aspectj"         %   "aspectjweaver"        % "1.8.1"
  )
}

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

aspectjSettings

javaOptions <++= AspectjKeys.weaverOptions in Aspectj

// when you call "sbt run" aspectj weaving kicks in
fork in run := true