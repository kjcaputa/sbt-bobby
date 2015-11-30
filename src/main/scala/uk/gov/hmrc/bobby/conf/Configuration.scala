/*
 * Copyright 2015 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.bobby.conf

import java.net.URL

import org.joda.time.LocalDate
import sbt.ConsoleLogger
import uk.gov.hmrc.bobby.domain.{Dependency, DeprecatedDependency, VersionRange}

import scala.io.Source
import scala.util.parsing.json.JSON

object Configuration{

  val credsFile        = System.getProperty("user.home") + "/.sbt/.credentials"
  val bintrayCredsFile = System.getProperty("user.home") + "/.bintray/.credentials"

  val defaultJsonOutputFile = "./target/bobby-reports/bobby-report.json"
  val defaultTextOutputFile = "./target/bobby-reports/bobby-report.txt"

  def parseConfig(jsonConfig: String): Seq[DeprecatedDependency] = {
    import uk.gov.hmrc.bobby.NativeJsonHelpers._

    for {
      Some(L(list)) <- List(JSON.parseFull(jsonConfig))
      MS(map) <- list
      organisation <- map.get("organisation")
      name <- map.get("name")
      range <- map.get("range")
      reason <- map.get("reason")
      fromString <- map.get("from")
      fromDate = LocalDate.parse(fromString)
    } yield DeprecatedDependency.apply(Dependency(organisation, name), VersionRange(range), reason, fromDate)
  }

  val nexusCredetials: Option[NexusCredentials] = {
    val ncf = new ConfigFile(credsFile)

    for {
      host <- ncf.get("host")
      user <- ncf.get("user")
      password <- ncf.get("password")

    } yield NexusCredentials(host, user, password)
  }

  val bintrayCredetials: Option[BintrayCredentials] = {
    val bncf = new ConfigFile(bintrayCredsFile)

    for {
      user <- bncf.get("user")
      password <- bncf.get("password")

    } yield BintrayCredentials(user, password)
  }
}

class Configuration(
                     url:Option[URL] = None,
                     jsonOutputFileOverride:Option[String]
                   ) {

  import Configuration._

  val timeout = 3000
  val logger = ConsoleLogger()

  val bobbyConfigFile  = System.getProperty("user.home") + "/.sbt/bobby.conf"

  val jsonOutputFile: String = (jsonOutputFileOverride orElse new ConfigFile(bobbyConfigFile).get("output-file")).getOrElse(defaultJsonOutputFile)
  val textOutputFile: String = new ConfigFile(bobbyConfigFile).get("text-output-file").getOrElse(defaultTextOutputFile)


  def loadDeprecatedDependencies: Seq[DeprecatedDependency] = {

    val bobbyConfig: Option[URL] = url orElse new ConfigFile(bobbyConfigFile).get("deprecated-dependencies").map{ u => new URL(u) }

    bobbyConfig.fold {
      logger.warn(s"[bobby] Unable to check for explicitly deprecated dependencies - $bobbyConfigFile does not exist or is not configured with deprecated-dependencies or may have trailing whitespace")
      Seq.empty[DeprecatedDependency]
    } { c =>
      try {
        logger.info(s"[bobby] loading deprecated dependency list from $c")
        val conn = c.openConnection()
        conn.setConnectTimeout(timeout)
        conn.setReadTimeout(timeout)
        val inputStream = conn.getInputStream

        Configuration.parseConfig(Source.fromInputStream(inputStream).mkString)
      } catch {
        case e: Exception =>
          logger.warn(s"[bobby] Unable load configuration from $c: ${e.getMessage}")
          Seq.empty
      }
    }
  }
}
