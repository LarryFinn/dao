package co.actioniq.slick.logging

import java.util.{Date, UUID}

import co.actioniq.slick.logging.TransactionAction.TransactionAction
import co.actioniq.thrift.{Context, ContextCreator, Source, ThriftUtils}
import play.api.libs.json.{JsNull, JsValue, Json, Writes}

case class LoggingModel(
  context: Context,
  id: String,
  traceId: String,
  userId: Long,
  customerId: Long,
  createdAt: Date,
  appHost: String,
  objectType: String,
  objectId: String,
  action: TransactionAction,
  oldValue: Option[JsValue],
  newValue: Option[JsValue],
  source: Option[Source]
)

object LoggingModel {
  implicit val contextWrites = new Writes[Context] {
    override def writes(o: Context) = ThriftUtils.thriftToSimpleJson(o)
  }
  implicit val uiSourceWrites = new Writes[Source.UiSource] {
    override def writes(o: Source.UiSource) = ThriftUtils.thriftToSimpleJson(o)
  }
  implicit val internalSourceWrites = new Writes[Source.InternalSource] {
    override def writes(o: Source.InternalSource) = ThriftUtils.thriftToSimpleJson(o)
  }
  implicit val loggingModelWrites: Writes[LoggingModel] = new Writes[LoggingModel] {
    override def writes(o: LoggingModel): JsValue = {
      val source = o.source.map({
        case s: Source.UiSource => Json.toJson(s)
        case s: Source.InternalSource => Json.toJson(s)
        case _: Source.UnknownUnionField => JsNull
      }).getOrElse(JsNull)
      Json.obj(
        "id" -> o.id,
        "trace_id" -> o.traceId,
        "user_id" -> o.userId,
        "context" -> Json.toJson(o.context),
        "customer_id" -> o.customerId,
        "created_at" -> o.createdAt,
        "app_host" -> o.appHost,
        "object_type" -> o.objectType,
        "object_id" -> o.objectId,
        "action" -> o.action.toString,
        "old_value" -> o.oldValue,
        "new_value" -> o.newValue,
        "source" -> source
      )
    }
  }
  def fromDAO[T](
    context: Context,
    objectType: Class[_ <: T],
    objectId: String,
    action: TransactionAction,
    oldValue: Option[T],
    newValue: Option[T],
    source: Option[Source]
  )(implicit writes: Writes[T]): LoggingModel = {
    new LoggingModel(
      id = UUID.randomUUID().toString,
      traceId = context.debugRequestId,
      userId = context.userId,
      context = context,
      customerId = context.customerId,
      createdAt = new Date(),
      appHost = ContextCreator.hostName,
      objectType = objectType.getSimpleName,
      objectId = objectId,
      action = action,
      oldValue = oldValue.map(oVal => Json.toJson(oVal)),
      newValue = newValue.map(nVal => Json.toJson(nVal)),
      source = source
    )
  }
}

object TransactionAction extends Enumeration {
  type TransactionAction = Value
  val create = Value("create")
  val update = Value("update")
  val delete = Value("delete")
}

