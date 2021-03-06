package enricher

import config_pkg.ApplicationConfig
import enricher.dbcon.DbHandler
import enricher.dbcon.DbHandler.unfold
import enricher.engine.Engine
import org.apache.log4j.{FileAppender, Level, Logger, PatternLayout}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory
import java.text.{DateFormat, SimpleDateFormat}

import scala.concurrent.duration.Duration

object main extends App {
  val path = "C:/Users/Andrea Colombo/IdeaProjects/Tesi/"

  var i = 0

  def setup_logger(): Unit = {
    val PATTERN = "%d [%p] - %l %m%n"
    val logName = "log/lkb_enricher_"+DateTime.now.toString(DateTimeFormat.forPattern("yyyy_MM_dd_HH_mm_ss_SSS")) + ".log"
    val fa2: FileAppender = new FileAppender()
    fa2.setName("FileLogger")
    fa2.setFile(logName)
    fa2.setLayout(new PatternLayout(PATTERN))
    fa2.setThreshold(Level.DEBUG)
    fa2.setAppend(true)
    fa2.activateOptions()
    Logger.getRootLogger.addAppender(fa2)
  }

  override def main(args: Array[String]): Unit = {
    setup_logger()
    val logger = LoggerFactory.getLogger(this.getClass)
    val start = System.currentTimeMillis
    try {
      ApplicationConfig.conf.getObject("db_config")
      if (args.nonEmpty) {
        if (args(0).equals("init")) {
          DbHandler.init()
          DbHandler.create_fk_gcm()
        }
        else if (args(0).equals("reset")) {
            DbHandler.null_gcm()
            DbHandler.drop_fk_gcm()
            DbHandler.reset_db()
            DbHandler.init()
            DbHandler.create_fk_gcm()
        }
        else if (args(0).equals("unfold")) {
          unfold()
        }
        else if (args.length == 1) {
          if (args.head == "all") {
            val table_l = ApplicationConfig.get_gcm_table_list()
            for (t <- table_l) {
              val column_l = ApplicationConfig.get_termtype_list(t)
              for (col <- column_l) {
                Engine.controller(t,col)
              }
            }
          }
          else {
            val t = args(0)
            val column_l = ApplicationConfig.get_termtype_list(t)
            for (col <- column_l) {
              println(col)
              Engine.controller(t,col)
            }
          }
        }
        else {
          val t = args(0)
          val col = args(1)
          Engine.controller(t,col)
        }
        unfold()
      }
      else {
        print_manual()
      }
    }
    catch{
      case e: Exception => logger.error("Error", e)
    }
    DbHandler.close_db()
    val totalTime = System.currentTimeMillis - start

    val seconds = totalTime / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val elapsed  = days + ":" + hours % 24 + ":" + minutes % 60 + ":" + seconds % 60

    logger.info("Elapsed time for arg "+args.mkString(" ")+": "+elapsed)
  }

  def print_manual(): Unit = {
    println("Program arguments: ")
    println
    println
    println("all \t\t\t\t\t\t\t\t Launch the script for all table and columns" +
      "\n" +
      "<table_name> \t\t\t\t\t\t Launch the script for that specific table " +
      "\n" +
      "<table_name> <column_name> \t\t\t Launch the script for that specific column" +
      "\n" +
      "reset \t\t\t\t\t\t\t\t Wipes the LKB and sets all the tid of GCM to null and recreate tables"+
      "\n" +
      "init \t\t\t\t\t\t\t\t Creates LKB tables on empty database"
    )
    println
    println
  }

}
