package hydra.avro

import java.time.{Instant, ZoneOffset, ZonedDateTime}

import hydra.avro.convert.{ISODateConverter, IsoDate}
import org.apache.avro.{LogicalTypes, Schema}
import org.scalatest.{FlatSpecLike, Matchers}

class ISODateConverterSpec extends Matchers with FlatSpecLike {

  "The ISODateConverter class" should "convert ISO dates" in {
    val c = new ISODateConverter()
    c.getConvertedType shouldBe classOf[ZonedDateTime]
    c.getLogicalTypeName shouldBe "iso-date"
    val dt = c.fromCharSequence("2015-07-28T19:55:57.693217+00:00",
      Schema.create(Schema.Type.STRING), IsoDate)

    dt.getYear shouldBe 2015
    dt.getMonthValue shouldBe 7
    dt.getDayOfMonth shouldBe 28
    dt.getHour shouldBe 19
    dt.getMinute shouldBe 55
    dt.getSecond shouldBe 57
    dt.getNano shouldBe 693217000
  }

  it should "return the epoch on bad formed dates" in {
    val c = new ISODateConverter()
    c.fromCharSequence("2015-07-281",
      Schema.create(Schema.Type.STRING), IsoDate) shouldBe Instant.EPOCH.atZone(ZoneOffset.UTC)
  }

  it should "use the logical type when parsing a schema" in {

    LogicalTypes.register(IsoDate.IsoDateLogicalTypeName, (_: Schema) => IsoDate)

    val schemaStr =
      """
        |{
        |  "namespace": "hydra.test",
        |  "type": "record",
        |  "name": "Date",
        |  "fields": [
        |    {
        |      "name": "timestamp",
        |      "type":{
        |        "type": "string",
        |        "logicalType":"iso-date"
        |      }
        |    }
        |  ]
        |}
      """.stripMargin

    val schema = new Schema.Parser().parse(schemaStr)
    schema.getField("timestamp").schema().getLogicalType shouldBe IsoDate
  }

  "The IsoDate LogicalType" should "validate correct types" in {
    IsoDate.validate(Schema.create(Schema.Type.STRING))
    intercept[IllegalArgumentException] {
      IsoDate.validate(Schema.create(Schema.Type.INT))
    }
  }

}
