/*  Copyright 2013 Twitter, inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.twitter.scalding

import java.util.UUID
import cascading.flow.FlowDef
import com.twitter.scalding.ReplImplicits._
import com.twitter.scalding.typed.{ Converter, TypedPipeInst }
import collection.JavaConverters._
import cascading.tuple.Fields

class TypedSequenceFile[T](path: String)(
  implicit val mf: Manifest[T], tget: TupleGetter[T], tset: TupleSetter[T]) extends SequenceFile(path, 0) with Mappable[T] with TypedSink[T] {

  override def converter[U >: T] =
    TupleConverter.asSuperConverter[T, U](TupleConverter.singleConverter[T](tget))
  override def setter[U <: T] = TupleSetter.asSubSetter[T, U](TupleSetter.singleSetter[T])

}

object TypedSequenceFile {
  def apply[T](path: String)(implicit mf: Manifest[T]): TypedSequenceFile[T] =
    new TypedSequenceFile[T](path)
}

/**
 * Enrichment on TypedPipes allowing them to be run locally, independent of the overall flow.
 * @param pipe to wrap
 */
class ShellTypedPipe[T](pipe: TypedPipe[T]) {
  import Dsl.flowDefToRichFlowDef

  /**
   * Shorthand for .write(dest).run
   */
  def save(dest: TypedSink[T] with Mappable[T]): TypedPipe[T] = {

    val p = pipe.toPipe(dest.sinkFields)(dest.setter)

    val localFlow = flowDef.onlyUpstreamFrom(p)
    dest.writeFrom(p)(localFlow, mode)
    run(localFlow)

    TypedPipe.from(dest)
  }

  /**
   * Save snapshot of a typed pipe to a temporary sequence file.
   * @return A TypedPipe to a new Source, reading from the sequence file.
   */
  def snapshot(implicit mf: Manifest[T]): TypedPipe[T] = {

    // come up with unique temporary filename
    // TODO: refactor into TemporarySequenceFile class
    val tmpSeq = "/tmp/scalding-repl/snapshot-" + UUID.randomUUID() + ".seq"
    val dest = TypedSequenceFile[T](tmpSeq)
    val p = pipe.toPipe(0)

    val localFlow = flowDef.onlyUpstreamFrom(p)
    dest.writeFrom(p)(localFlow, mode)
    run(localFlow)

    TypedPipe.from(dest)
  }

  // TODO: add back `toList` based on `snapshot` this time

  // TODO: add `dump` to view contents without reading into memory
  def toIterator(implicit mf: Manifest[T]): Iterator[T] = pipe match {
    case tp: TypedPipeInst[_] =>
      val p = tp.inpipe
      val srcs = flowDef.getSources
      if (p.getPrevious.length == 0) { // is a head
        if (srcs.containsKey(p.getName)) {
          val tap = srcs.get(p.getName)
          // val conv = Converter(TupleConverter.singleConverter[T])
          // val conv = Converter(TupleConverter.asSuperConverter(TupleConverter.singleConverter[T]))
          // mode.openForRead(tap).asScala.flatMap(v => conv(v))
          mode.openForRead(tap).asScala.flatMap(v => tp.flatMapFn(v))
        } else {
          throw new RuntimeException("Unable to open for reading.")
        }
      } else {
        // not a head pipe, so we should generate a snapshot and use that
        println("@> need to generate snapshot")
        pipe.snapshot.toIterator
      }
    case _ =>
      println("@> need to generate snapshot")
      pipe.snapshot.toIterator
  }

  def toList(implicit mf: Manifest[T]): List[T] = toIterator.toList

  def dump(implicit mf: Manifest[T]): Unit = toIterator.foreach(println(_))

}
