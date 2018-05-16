package co.actioniq.slick.context

import com.twitter.finagle.tracing.{SpanId, Trace, TraceId}
import org.slf4j.MDC

trait ContextCopier {
  protected def setContextMap(context: Option[java.util.Map[String, String]]): Unit = {
    if (context.isEmpty) {
      MDC.clear()
    } else {
      MDC.setContextMap(context.get)
    }
  }

  protected def traceIdFromMDC(mdcContext: Option[java.util.Map[String, String]]): Option[TraceId] = {
    mdcContext.flatMap(ctx => {
      Option(ctx.get("X-Trace-Id")).map(traceId => {
        new TraceId(
          Some(SpanId(traceId.toLong)),
          None,
          SpanId(traceId.toLong),
          Some(true),
          com.twitter.finagle.tracing.Flags(1)
        )
      })
    })
  }

  protected def getRunnableCopyContext(
    mdcContext: Option[java.util.Map[String, String]],
    r: Runnable): Runnable = {
    new Runnable {
      override def run(): Unit = {
        val traceIdOpt = traceIdFromMDC(mdcContext)
        // backup the callee MDC context
        val oldMDCContext = Option(MDC.getCopyOfContextMap)
        // Run the runnable with the captured context
        setContextMap(mdcContext)
        try {
          if (traceIdOpt.isDefined){
            val traceId = traceIdOpt.get
            Trace.letId(traceId){
              r.run()
            }
          } else {
            r.run()
          }
        } finally {
          // restore the callee MDC context
          setContextMap(oldMDCContext)
        }
      }
    }
  }


}
