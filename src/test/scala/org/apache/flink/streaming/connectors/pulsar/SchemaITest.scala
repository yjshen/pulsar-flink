/**
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
package org.apache.flink.streaming.connectors.pulsar

import java.util.Date
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import java.sql.Timestamp
import java.text.SimpleDateFormat

import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.connectors.pulsar.internals.{PulsarFlinkTest, PulsarFunSuite}
import org.apache.flink.streaming.connectors.pulsar.testutils.FailingIdentityMapper
import org.apache.flink.table.descriptors.Pulsar
import org.apache.flink.table.runtime.utils.StreamITCase
import org.apache.flink.types.Row
import org.apache.flink.util.StringUtils
import org.apache.pulsar.common.naming.TopicName
import org.apache.pulsar.common.schema.SchemaType

import scala.reflect.ClassTag

class SchemaITest extends PulsarFunSuite with PulsarFlinkTest {

  import org.apache.flink.streaming.connectors.pulsar.internals.SchemaData._
  import org.apache.flink.streaming.connectors.pulsar.internal.PulsarOptions._
  import org.apache.flink.table.api._
  import org.apache.flink.table.api.scala._

  override def beforeEach(): Unit = {
    super.beforeEach()
    StreamITCase.testResults.clear()
    FailingIdentityMapper.failedBefore = false
  }

  test("test boolean read") {
    checkRead[Boolean](SchemaType.BOOLEAN, booleanSeq, null)
  }

  test("test int read") {
    checkRead[Int](SchemaType.INT32, int32Seq, null)
  }

  test("test string read") {
    checkRead[String](SchemaType.STRING, stringSeq, null)
  }

  test("test byte read") {
    checkRead[Byte](SchemaType.INT8, int8Seq, null)
  }

  test("test short read") {
    checkRead[Short](SchemaType.INT16, int16Seq, null)
  }

  test("test float read") {
    checkRead[Float](SchemaType.FLOAT, floatSeq, null)
  }

  test("test double read") {
    checkRead[Double](SchemaType.DOUBLE, doubleSeq, null)
  }

  test("test date read") {
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    checkRead[Date](SchemaType.DATE, dateSeq, dateFormat.format(_))
  }

  test("test timestamp read") {
    checkRead[Timestamp](SchemaType.TIMESTAMP, timestampSeq, null)
  }

  test("test byte array read") {
    checkRead[Array[Byte]](SchemaType.BYTES, bytesSeq, StringUtils.arrayAwareToString)
  }

  private def checkRead[T: ClassTag](
      schemaType: SchemaType,
      datas: Seq[T],
      str: T => String) = {

    val see = StreamExecutionEnvironment.getExecutionEnvironment
    see.setParallelism(1)
    val tEnv = StreamTableEnvironment.create(see)

    val table = newTopic()
    sendTypedMessages[T](table, schemaType, datas, None)

    val props = sourceProperties()
    props.setProperty(TOPIC_SINGLE, table)

    tEnv
      .connect(new Pulsar().properties(props))
      .inAppendMode()
      .registerTableSource(table)

    val t: Table = tEnv.scan(table).select("value")
    implicit val ti = t.getSchema.toRowType
    val as = t.toAppendStream[Row]
    as.map(new FailingIdentityMapper[Row](datas.length))

    as.addSink(new StreamITCase.StringSink[Row]).setParallelism(1)

    val runner = new Thread("read") {
      override def run(): Unit = {
        try {
          see.execute("read")
        } catch {
          case t: Throwable => // do nothing
        }
      }
    }
    runner.start()
    runner.join()

    if (str == null) {
      assert(StreamITCase.testResults == datas.init.map(_.toString))
    } else {
      assert(StreamITCase.testResults == datas.init.map(str(_)))
    }
  }


  def sourceProperties(): Properties = {
    val prop = new Properties()
    prop.setProperty(SERVICE_URL_OPTION_KEY, serviceUrl)
    prop.setProperty(ADMIN_URL_OPTION_KEY, adminUrl)
    prop.setProperty(PARTITION_DISCOVERY_INTERVAL_MS, "5000")
    prop.setProperty(STARTING_OFFSETS_OPTION_KEY, "earliest")
    prop
  }

  private val topicId = new AtomicInteger(0)

  private def newTopic(): String = TopicName.get(s"topic-${topicId.getAndIncrement()}").toString
}
