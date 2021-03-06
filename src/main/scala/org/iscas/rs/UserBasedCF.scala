package org.iscas.rs

import breeze.linalg.sum
import org.apache.spark.mllib.linalg.distributed._
import org.apache.spark.mllib.stat.MultivariateStatisticalSummary
import org.apache.spark.sql.SparkSession
import org.iscas.common.Consts

object UserBasedCF {
  /**
    * user-based协同过滤
    * 数据来自$SPARK_HOME/data/mllib/als/test.data
    * @param args
    */
  def main(args: Array[String]): Unit = {
    val spark=SparkSession
      .builder()
      .master(Consts.MASTER)
      .appName("UserBasedCF")
      .getOrCreate()
    val sc=spark.sparkContext
    sc.setLogLevel("ERROR")

    val data=sc.textFile(Consts.HDFS+"/user/lucio35/test.data")
    val parsedData=data.map(
      _.split(',') match {
        case Array(user,item,rate) => MatrixEntry(user.toLong-1,item.toLong-1,rate.toDouble)// matrix[user][item]=rate
      }).cache()
    //           item
    //       ------------
    //       |
    //       |
    // user  |
    val ratings=new CoordinateMatrix(parsedData)
    val matrix=ratings.transpose().toRowMatrix()// RowMatrix只能计算列之间的相似度，是cosine sim
    val similarities=matrix.columnSimilarities(0)// 精确的用户间的相似度

    // estimate user SliceJoin-1->item SliceJoin-1
//    val ratingsOfUser1=ratings.toRowMatrix().rows.collect()(0).toArray// 用户1的所有评分
//    val avgRatingOfUser1=ratingsOfUser1.sum/ratingsOfUser1.size

    val ratingsToItem1=matrix.rows.collect()(0).toArray.drop(1)// 其他用户对物品1的评分
    val columnsMean=matrix.computeColumnSummaryStatistics().mean
    val avgRatingOfUser1=columnsMean.toArray(0) // 用户1的平均评分
    val avgRatingsOfUser=columnsMean.toArray.drop(1)// 所有用户的平均评分
    println(avgRatingOfUser1)// 3
    avgRatingsOfUser.foreach(println)// [3,3,3]

    val weights=similarities.entries.filter(_.i==0).sortBy(_.j).map(_.value).collect()//　用户１和其他用户的相似度
    weights.foreach(println)// SliceJoin-1.0 0.38 0.38
    val weightedR=(0 to 2).map(t => weights(t)*(ratingsToItem1(t)-avgRatingsOfUser(t))).sum/weights.sum

    println("rating of user SliceJoin-1 to item SliceJoin-1 is:"+(avgRatingOfUser1+weightedR))
    // rating of user SliceJoin-1 to item SliceJoin-1 is:3.2608695652173916

  }
}
