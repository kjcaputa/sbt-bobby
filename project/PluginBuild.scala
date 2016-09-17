/*
 * Copyright 2015 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt.Keys._
import sbt.ScriptedPlugin._
import sbt._
import uk.gov.hmrc.versioning.SbtGitVersioning

object PluginBuild extends Build {

  import uk.gov.hmrc._
  import DefaultBuildSettings._

  val pluginName = "sbt-bobby"

  lazy val root = (project in file("."))
    .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
    .settings(
      sbtPlugin := true,
      name := pluginName,
      targetJvm := "jvm-1.7",
      scalaVersion := "2.10.4",
      libraryDependencies ++= Seq(
        "commons-codec" % "commons-codec" % "1.10",
        "joda-time" % "joda-time" % "2.9.1",
        "org.joda" % "joda-convert" % "1.8.1",
        "com.typesafe" % "config" % "1.3.0",
        "com.typesafe.play" %% "play-json" % "2.3.10" % "test",
        "org.scalatest" %% "scalatest" % "2.2.4" % "test",
        "org.pegdown" % "pegdown" % "1.5.0" % "test"
      )
    )
    .settings(Defaults.coreDefaultSettings ++ Seq(resourceDirectory in Compile <<= javaSource in Compile))
    .settings(ScriptedPlugin.scriptedSettings: _*)
    .settings(scriptedLaunchOpts += s"-Dproject.version=${version.value}")
}

