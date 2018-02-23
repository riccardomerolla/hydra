package hydra.kafka.ingestors

import akka.actor.{ ActorSystem, Props }
import akka.testkit.TestActors.ForwardActor
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit, TestProbe }
import com.pluralsight.hydra.avro.JsonToAvroConversionException
import hydra.avro.JsonToAvroConversionExceptionWithMetadata
import hydra.common.config.ConfigSupport
import hydra.core.ingest.HydraRequest
import hydra.core.ingest.RequestParams.HYDRA_KAFKA_TOPIC_PARAM
import hydra.core.protocol.GenericIngestionError
import hydra.core.transport.TransportSupervisor.Deliver
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecordBuilder
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, FunSpecLike, Matchers }
import org.springframework.core.io.ClassPathResource

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.io.Source

/**
 * Created by alexsilva on 11/18/16.
 */
class IngestionErrorHandlerSpec extends TestKit(ActorSystem("ingestion-error-handler-spec")) with Matchers with FunSpecLike
  with ImplicitSender
  with ConfigSupport
  with BeforeAndAfterAll
  with ScalaFutures {

  val probe = TestProbe()

  val kafkaProducer = system.actorOf(Props(new ForwardActor(probe.ref)), "kafka_producer")

  val handler = system.actorOf(Props[IngestionErrorHandler])
  val handlerRef = TestActorRef[IngestionErrorHandler](Props[IngestionErrorHandler])

  val schema = new Schema.Parser().parse(Source.fromResource("schemas/HydraIngestError.avsc").mkString)

  val request = HydraRequest("123", "someString", None, Map(HYDRA_KAFKA_TOPIC_PARAM -> "topic"))

  describe("When using the kafka ingestion error handler") {
    it("builds an avro record") {
      val err = GenericIngestionError("test", new IllegalArgumentException("test-exception"),
        request, 400)
      val record = handlerRef.underlyingActor.buildPayload(err)
      record.key shouldBe Some("topic")
      record.payload shouldBe toGenericRecord(err).build()
      record.destination shouldBe "__hydra_ingest_errors"
    }

    it("includes the schema if available from the exception") {
      val err = GenericIngestionError(
        "test",
        new JsonToAvroConversionException("test-exception", "field", schema), request, 400)
      val record = handlerRef.underlyingActor.buildPayload(err)
      record.key shouldBe Some("topic")
      record.payload shouldBe toGenericRecord(err).set("schema", schema.toString).build()
      record.destination shouldBe "__hydra_ingest_errors"
    }

    it("includes the schema metadata if available from the exception") {
      val cause = new JsonToAvroConversionException("test-exception", "field", schema)
      val except = new JsonToAvroConversionExceptionWithMetadata(cause, schema)
      val err = GenericIngestionError("test", except, request, 400)
      val record = handlerRef.underlyingActor.buildPayload(err)
      record.key shouldBe Some("topic")
      record.payload shouldBe toGenericRecord(err).set("schema", "hydra.HydraIngestError").build()
      record.destination shouldBe "__hydra_ingest_errors"
    }

    it("publishes to Kafka") {
      val err = GenericIngestionError(
        "test",
        new JsonToAvroConversionException("test", "field", schema), request, 400)
      handlerRef ! err
      probe.expectMsgType[Deliver[_, _]](10.seconds)
    }

  }

  override def afterAll = TestKit.shutdownActorSystem(system)

  def toGenericRecord(err: GenericIngestionError): GenericRecordBuilder = {
    new GenericRecordBuilder(schema)
      .set("ingestor", err.ingestor).set("destination", "topic")
      .set("errorMessage", err.cause.getMessage)
      .set("payload", err.request.payload)
      .set("metadata", err.request.metadata.asJava)
  }

}
