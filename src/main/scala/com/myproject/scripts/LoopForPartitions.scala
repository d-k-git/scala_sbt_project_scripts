package com.myproject.scripts


import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.{col, lit, row_number, trim, trunc, when}


object LoopForPartitions extends App {

  val spark: SparkSession = SparkSession.builder().
    config("spark.sql.parquet.writeLegacyFormat", "true").
    enableHiveSupport().
    getOrCreate()


  import spark.implicits._


  /*val df = Seq((1)).toDF("seq")
  val curTS = df.withColumn("mondate", add_months(date_format(current_timestamp(), "yyyy-MM-01"), -1))
  val df_mondate = curTS.withColumn("mondate", to_date(col("mondate"))).first()
  val mondate = df_mondate.get(1) */

   val partitionsList: List[String] = List(
      "2022-04-01"
    , "2022-05-01"
    , "2022-06-01"
    , "2022-07-01"
    , "2022-08-01"

  )

  for (mondate <- partitionsList) {


    val df = spark.table(tableName = "db_db.client_table")
      .filter($"is_cancel" === "0" && $"snapshot_month" === mondate)
      .select(
        col("record_id").as("client_id"),
        col("ban"),
        col("ctn"),
        col("snapshot_timestamp"),
        trunc(col("create_date"), "month").as("create_date"))
      .withColumn("delete_date", lit(null))
      .distinct()




    val windowSpec = Window.partitionBy(
      df("client_id")     )
      .orderBy($"snapshot_timestamp".desc, $"delete_date".desc)

    val outDF = df
      .withColumn("row_number", row_number.over(windowSpec))
      .withColumn("delete_date",
        when(col("delete_date").isNull, "2099-01-01")
          otherwise col("delete_date"))
      .select($"*")
      .filter($"row_number" === 1)

    outDF
      .write
      .option("path", "/warehouse/tablespace/external/hive/db/table")
      .partitionBy("mondate")
      .mode(SaveMode.Append)
      .saveAsTable("db.out_table")
}
  spark.stop()


}


