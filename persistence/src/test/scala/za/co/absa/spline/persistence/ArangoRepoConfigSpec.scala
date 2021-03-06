/*
 * Copyright 2020 ABSA Group Limited
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

package za.co.absa.spline.persistence

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.commons.scalatest.EnvFixture

class ArangoRepoConfigSpec
  extends AnyFlatSpec
    with Matchers
    with EnvFixture {

  it should "support commas in the database connection string" in {
    setEnv("spline.database.connectionUrl", "arangodb://host.a:1,host.b:2/dbname")
    ArangoRepoConfig.Database.connectionURL.hosts shouldEqual Seq(("host.a", 1), ("host.b", 2))
  }
}
