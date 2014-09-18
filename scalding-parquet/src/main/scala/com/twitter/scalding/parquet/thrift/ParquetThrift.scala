/*
Copyright 2012 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.twitter.scalding.parquet.thrift

import _root_.parquet.cascading.{ParquetTBaseScheme, ParquetValueScheme}
import cascading.scheme.Scheme
import com.twitter.scalding._
import com.twitter.scalding.parquet.{HasColumnProjection, HasFilterPredicate}
import com.twitter.scalding.source.{DailySuffixSource, HourlySuffixSource}
import java.io.Serializable
import org.apache.thrift.{TBase, TFieldIdEnum}

object ParquetThrift extends Serializable {
  type ThriftBase = TBase[_ <: TBase[_, _], _ <: TFieldIdEnum]
}

trait ParquetThrift[T <: ParquetThrift.ThriftBase] extends FileSource
  with SingleMappable[T] with TypedSink[T] with LocalTapSource with HasFilterPredicate with HasColumnProjection {

  def mf: Manifest[T]

  override def hdfsScheme = {

    val config = new ParquetValueScheme.Config[T].withRecordClass(mf.erasure.asInstanceOf[Class[T]])

    val configWithFp = withFilter match {
      case Some(fp) => config.withFilterPredicate(fp)
      case None => config
    }

    val configWithProjection = globsInParquetStringFormat match {
      case Some(s) => configWithFp.withProjectionString(s)
      case None => configWithFp
    }

    val scheme = new ParquetTBaseScheme[T](configWithProjection)

    HadoopSchemeInstance(scheme.asInstanceOf[Scheme[_, _, _, _, _]])
  }

  override def setter[U <: T] = TupleSetter.asSubSetter[T, U](TupleSetter.singleSetter[T])

}

class DailySuffixParquetThrift[T <: ParquetThrift.ThriftBase](
  path: String,
  dateRange: DateRange)(implicit override val mf: Manifest[T])
  extends DailySuffixSource(path, dateRange) with ParquetThrift[T]

class HourlySuffixParquetThrift[T <: ParquetThrift.ThriftBase](
  path: String,
  dateRange: DateRange)(implicit override val mf: Manifest[T])
  extends HourlySuffixSource(path, dateRange) with ParquetThrift[T]

class FixedPathParquetThrift[T <: ParquetThrift.ThriftBase](paths: String*)(implicit override val mf: Manifest[T])
  extends FixedPathSource(paths: _*) with ParquetThrift[T]