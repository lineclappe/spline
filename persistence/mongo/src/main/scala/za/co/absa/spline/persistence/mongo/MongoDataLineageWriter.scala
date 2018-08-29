/*
 * Copyright 2017 Barclays Africa Group Limited
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

package za.co.absa.spline.persistence.mongo


import com.mongodb.{DBCollection, DBObject}
import org.slf4s.Logging
import za.co.absa.spline.model.dt.DataType
import za.co.absa.spline.model.op.{Operation, Projection}
import za.co.absa.spline.model.{Attribute, DataLineage, MetaDataset}
import za.co.absa.spline.persistence.api.DataLineageWriter
import za.co.absa.spline.persistence.mongo.DBSchemaVersionHelper._
import za.co.absa.spline.persistence.mongo.MongoDataLineageWriter._

import scala.concurrent.{ExecutionContext, Future, blocking}

/**
  *
  * The class represents Mongo persistence writer for the [[za.co.absa.spline.model.DataLineage DataLineage]] entity.
  *
  * @param connection A connection to Mongo database
  */
class MongoDataLineageWriter(connection: MongoConnection) extends DataLineageWriter with Logging {

  /**
    * The method stores a particular data lineage to the persistence layer.
    *
    * @param lineage A data lineage that will be stored
    */
  override def store(lineage: DataLineage)(implicit ec: ExecutionContext): Future[Unit] = {
    log debug s"Storing lineage objects"

    val (transformations: Seq[TransformationPO], operations: Seq[Operation]) =
      ((Seq.empty[TransformationPO], Seq.empty[Operation]) /: lineage.operations.view) {
        case ((transformationPOsAcc, operationsAcc), op:Projection) =>
          val transformationPOs:Seq[TransformationPO] =
            op.transformations.map(expr => TransformationPO(expr, op.mainProps.id))
          (transformationPOsAcc ++ transformationPOs, op.copy(transformations = Nil) +: operationsAcc)
        case ((transformationPOsAcc, operationsAcc), op) =>
          (transformationPOsAcc, op +: operationsAcc)
      }

    import connection._
    for (_ <- Future.sequence(Seq(
      insertAsyncSeq(operationCollection, toLineageComponentDBOs[Operation](operations.reverse, idField -> (_.mainProps.id))(lineage.id)),
      insertAsyncSeq(transformationCollection, toLineageComponentDBOs[TransformationPO](transformations)(lineage.id)),
      insertAsyncSeq(attributeCollection, toLineageComponentDBOs[Attribute](lineage.attributes)(lineage.id)),
      insertAsyncSeq(dataTypeCollection, toLineageComponentDBOs[DataType](lineage.dataTypes)(lineage.id)),
      insertAsyncSeq(datasetCollection, toLineageComponentDBOs[MetaDataset](lineage.datasets)(lineage.id)))))
      yield
        blocking(dataLineageCollection.insert(serializeWithVersion[DataLineagePO](DataLineagePO(lineage))))
  }
}

object MongoDataLineageWriter {
  val lineageIdField = "_lineageId"
  val idField = "_id"
  val indexField = "_index"

  private def insertAsyncSeq(dBCollection: DBCollection, seq: Seq[DBObject])(implicit executionContext: ExecutionContext): Future[Unit] =
    if (seq.isEmpty) Future.successful(Unit)
    else Future(blocking(dBCollection.insert(seq: _*)))

  private def toLineageComponentDBOs[Y <: scala.AnyRef : Manifest](col: Seq[Y], extraProps: (String, Y => Any)*)(linId: String): Seq[DBObject] =
    col.view
      .zipWithIndex
      .map { case (o, i) =>
        val dbo = serializeWithVersion[Y](o)
        dbo.put(lineageIdField, linId)
        dbo.put(indexField, i)
        for ((propName, valueFn) <- extraProps)
          dbo.put(propName, valueFn(o))
        dbo
      }
}
