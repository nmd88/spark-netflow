/*
 * Copyright 2016 sadikovi
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

package com.github.sadikovi.spark.netflow

import org.apache.hadoop.fs.{FileStatus, FileSystem, Path}
import org.apache.hadoop.mapreduce.Job

import org.apache.spark.Logging
import org.apache.spark.rdd.{RDD, UnionRDD}
import org.apache.spark.sql.{SQLContext, Row}
import org.apache.spark.sql.sources.{HadoopFsRelation, OutputWriterFactory}
import org.apache.spark.sql.types.{StructType, StructField, IntegerType}

import com.github.sadikovi.netflowlib.RecordBuffer
import com.github.sadikovi.spark.rdd.{NetflowFileRDD, NetflowMetadata}
import com.github.sadikovi.spark.util.Utils

private[netflow] class NetflowRelation(
    override val paths: Array[String],
    private val maybeDataSchema: Option[StructType],
    override val userDefinedPartitionColumns: Option[StructType],
    private val parameters: Map[String, String])
    (@transient val sqlContext: SQLContext) extends HadoopFsRelation with Logging {

  // [Netflow version]
  private val version = parameters.get("version") match {
    case Some(str) => SchemaResolver.validateVersion(str).
      getOrElse(sys.error(s"Invalid version specified: ${str}"))
    case None => sys.error("'version' must be specified for Netflow data")
  }

  // [buffer size], by default use standard record buffer size ~3Mb
  private val bufferSize = parameters.get("buffer") match {
    case Some(str) =>
      val bytes = Utils.byteStringAsBytes(str)
      if (bytes > Integer.MAX_VALUE) {
        sys.error(s"Cannot set buffer larger than ${Integer.MAX_VALUE}")
      }
      bytes.toInt
    case None => RecordBuffer.BUFFER_LENGTH_1
  }

  // get buffer size in bytes, mostly for testing
  private[netflow] def getBufferSize(): Int = bufferSize

  override def dataSchema: StructType = SchemaResolver.getSchemaForVersion(version)

  override def buildScan(
      requiredColumns: Array[String],
      inputFiles: Array[FileStatus]): RDD[Row] = {
    if (inputFiles.isEmpty) {
      sqlContext.sparkContext.emptyRDD[Row]
    } else {
      // convert to internal Netflow fields
      val mapper = SchemaResolver.getMapperForVersion(version)
      val fields: Array[Long] = if (requiredColumns.isEmpty) {
        logWarning("Required columns are empty, using first column instead")
        // when required columns are empty, e.g. in case of direct `count()` we use only one column
        // schema to quickly read records
        mapper.getFirstInternalColumn()
      } else {
        requiredColumns.map(col => mapper.getInternalColumnForName(col))
      }

      // return union of NetflowFileRDD which are designed to read only one file and store data in
      // one partition
      new UnionRDD[Row](sqlContext.sparkContext, inputFiles.map { status =>
        val metadata = Seq(NetflowMetadata(version, status.getPath.toString, fields, bufferSize))
        new NetflowFileRDD(sqlContext.sparkContext, metadata, 1)
      })
    }
  }

  override def prepareJobForWrite(job: Job): OutputWriterFactory = {
    throw new UnsupportedOperationException("Write is not supported in this version of package")
  }
}
