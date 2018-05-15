package co.actioniq.slick

import java.net.URI

/**
  * AIQ-specific type-converters to/from their database representations.
  */
class SlickConverters() {
  import co.actioniq.slick.impl.AiqSlickProfile.api._ // scalastyle:ignore
  implicit val uriColumnType = MappedColumnType.base[URI, String]( // scalastyle:ignore
    { uri => uri.toASCIIString },
    { uriString => URI.create(uriString) }
  )
}
