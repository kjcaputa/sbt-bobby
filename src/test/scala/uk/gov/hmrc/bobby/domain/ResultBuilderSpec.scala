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

package uk.gov.hmrc.bobby.domain

import org.joda.time.LocalDate
import org.scalatest.{FlatSpec, Matchers}
import sbt.ModuleID
import MessageLevels.{ERROR, INFO, WARN}

import scala.util.{Failure, Success}

class ResultBuilderSpec extends FlatSpec with Matchers {

  def deprecatedSoon(org:String, name:String, version:String):DeprecatedDependency={
    DeprecatedDependency(Dependency(org, name), VersionRange(version), "reason", new LocalDate().plusWeeks(1))
  }

  def deprecatedNow(org:String, name:String, version:String, reason:String = "reason", deadline:LocalDate = new LocalDate().minusWeeks(1)):DeprecatedDependency={
    DeprecatedDependency(Dependency(org, name), VersionRange(version), reason, deadline)
  }

  it should "return error if a dependency is in the exclude range" in {
    val deprecated = Seq(deprecatedNow("uk.gov.hmrc", "auth", "[3.2.1]"))
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.1"))

    ResultBuilder.calculate(projectDependencies, deprecated, None).head.level shouldBe ERROR
  }

  it should "not return error if a dependency is not in the exclude range" in {
    val deprecated = Seq(deprecatedNow("uk.gov.hmrc", "auth", "[3.2.1]"))
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.2"))

    ResultBuilder.calculate(projectDependencies, deprecated, None) shouldBe 'empty
  }

  it should "return error if a dependency is in the exclude range using wildcards for org, name and version number " in {

    val deprecated = Seq(deprecatedNow("*", "*", "[*-SNAPSHOT]"))
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.1-SNAPSHOT"))

    val message = ResultBuilder.calculate(projectDependencies, deprecated, None).head

    message.level shouldBe ERROR
    message.shortTabularOutput(3) shouldBe "[*-SNAPSHOT]"
  }

  it should "not return error for valid dependencies that don't include snapshots" in {

    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.1"))
    val deprecated = Seq(deprecatedNow("*", "*", "[*-SNAPSHOT]"))

    ResultBuilder.calculate(projectDependencies, deprecated, None) shouldBe 'empty

  }

  it should "return error if one of several dependencies is in the exclude range" in {

    val projectDependencies = Seq(
      new ModuleID("uk.gov.hmrc", "auth", "3.2.1"),
      new ModuleID("uk.gov.hmrc", "data-stream", "0.2.1"))

    val deprecated = Seq(
      deprecatedNow("uk.gov.hmrc", "auth", "(,4.0.0]"),
      deprecatedSoon("uk.gov.hmrc", "data-stream", "(,4.0.0]"))

    ResultBuilder.calculate(projectDependencies, deprecated, None).map(_.level).toSet shouldBe Set(WARN, ERROR)
  }

  it should "not return error for dependencies in the exclude range but not applicable yet" in {

    val deprecated = Seq(deprecatedSoon("uk.gov.hmrc", "auth", "[3.2.1]"))
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.1"))

    ResultBuilder.calculate(projectDependencies, deprecated, None).head.level shouldBe WARN
  }

  it should "not return error for mandatory dependencies which are superseded" in {

    val deprecated = Seq(deprecatedNow("uk.gov.hmrc", "auth", "[3.2.1]"))
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.2"))

    ResultBuilder.calculate(projectDependencies, deprecated, None) shouldBe 'empty
  }

  it should "produce warning message for mandatory dependencies which will be enforced in the future" in {

    val deprecated = Seq(deprecatedSoon("uk.gov.hmrc", "auth", "(,4.0.0]"))
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.0"))

    ResultBuilder.calculate(projectDependencies, deprecated, None).head.level shouldBe WARN
  }

  it should "produce error message for mandatory dependencies which are currently been enforced" in {

    val deprecated = Seq(deprecatedNow("uk.gov.hmrc", "auth", "(,4.0.0]", reason = "the reason", deadline = new LocalDate(2000, 1, 1)))
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.0"))

    val messages = ResultBuilder.calculate(projectDependencies, deprecated, None)

    messages.head.longTabularOutput(0) shouldBe "ERROR"
    messages.head.longTabularOutput(1) shouldBe "uk.gov.hmrc.auth"
    messages.head.longTabularOutput(2) shouldBe "3.2.0"
    messages.head.longTabularOutput(5) shouldBe "2000-01-01"
    messages.head.longTabularOutput(6) shouldBe "the reason"
  }

  it should "show a ERROR message for a dependency which has a newer version in a repository AND is a mandatory upgrade now" in {
    val deprecated = Seq(deprecatedNow("uk.gov.hmrc", "auth", "(,4.0.0]"))
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.1"))
    val repoDependencies = Map(new ModuleID("uk.gov.hmrc", "auth", "3.2.1") -> Success(Version("4.3.0")))

    val messages = ResultBuilder.calculate(projectDependencies, deprecated, Some(repoDependencies))
    messages.head.longTabularOutput(0) shouldBe "ERROR"
  }

  it should "show a WARN message for a dependency which has a newer version in a repository AND is a mandatory upgrade soon" in {
    val deprecated = Seq(deprecatedSoon("uk.gov.hmrc", "auth", "(,4.0.0]"))
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.1"))
    val repoDependencies = Map(new ModuleID("uk.gov.hmrc", "auth", "3.2.1") -> Success(Version("4.3.0")))

    val messages = ResultBuilder.calculate(projectDependencies, deprecated, Some(repoDependencies))

    messages.size shouldBe 1
    messages.head.level shouldBe WARN
    messages.head.shortTabularOutput should contain("3.2.1")
    messages.head.shortTabularOutput should contain("4.3.0")
  }

  it should "show an INFO message for a dependency which has a newer version in a repository" in {
    val deprecated = Seq.empty
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.1"))
    val repoDependencies = Map(new ModuleID("uk.gov.hmrc", "auth", "3.2.1") -> Success(Version("3.3.0")))

    val messages = ResultBuilder.calculate(projectDependencies, deprecated, Some(repoDependencies))

    messages.size shouldBe 1
    messages.head.level shouldBe INFO
    messages.head.shortTabularOutput should contain("3.2.1")
    messages.head.shortTabularOutput should contain("3.3.0")
  }

  it should "not show a message if a dependency is up-to-date" in {
    val deprecated = Seq.empty
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.1"))
    val repoDependencies = Map(new ModuleID("uk.gov.hmrc", "auth", "3.2.1") -> Success(Version("3.2.1")))

    val messages = ResultBuilder.calculate(projectDependencies, deprecated, Some(repoDependencies))

    messages shouldBe 'empty
  }

  it should "show an INFO message for a dependency for which the latest nexus revision is unknown and show 'not-found' in the results table" in {
    val deprecated = Seq.empty
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.1"))
    val repoDependencies = Map(new ModuleID("uk.gov.hmrc", "auth", "3.2.1") -> Failure(new Exception("not-found")))

    val messages = ResultBuilder.calculate(projectDependencies, deprecated, Some(repoDependencies))

    messages.head.level shouldBe INFO
    messages.head.shortTabularOutput should contain("3.2.1")
    messages.head.shortTabularOutput should contain("not-found")
  }

  it should "show an WARN message for a dependency which will be deprecated soon AND has a newer version in a repository" in {
    val deprecated = Seq(deprecatedSoon("uk.gov.hmrc", "auth", "(,4.0.0]"))
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.1"))
    val repoDependencies = Map(new ModuleID("uk.gov.hmrc", "auth", "3.2.1") -> Success(Version("3.8.0")))

    val messages = ResultBuilder.calculate(projectDependencies, deprecated, Some(repoDependencies))

    messages.head.level shouldBe WARN
    messages.head.shortTabularOutput should contain ("(,4.0.0]")
    messages.head.shortTabularOutput should contain ("3.2.1")
    messages.head.shortTabularOutput should contain("3.8.0")
    messages.head.shortTabularOutput should not contain "4.0.0"
  }

  it should "not show an eariler version of a mandatory dependency if the latest was not found in a repository" in {
    val deprecated = Seq(deprecatedSoon("uk.gov.hmrc", "auth", "(,4.0.0]"))
    val projectDependencies = Seq(new ModuleID("uk.gov.hmrc", "auth", "3.2.1"))
    val repoDependencies = Map(new ModuleID("uk.gov.hmrc", "auth", "3.2.1") -> Success(Version("1.0.0")))

    val messages = ResultBuilder.calculate(projectDependencies, deprecated, Some(repoDependencies))

    messages.head.shortTabularOutput should not contain "1.0.0"
    messages.head.shortTabularOutput should not contain "3.1.0"
  }
}