scala_library(name='slick',
  dependencies=[
    '3rdparty/com/typesafe:slick',
    '3rdparty/com/typesafe:slick-hikaricp',
    '3rdparty/com/twitter:finagle-core',
    '3rdparty/com/twitter:bijection-util',
    '3rdparty/org/clapper:grizzled-slf4j',
    '3rdparty:mysql',
    '3rdparty/ch/qos/logback:logback-classic-build',
    '3rdparty/io/zipkin/brave:brave',
    '3rdparty/io/zipkin/reporter:zipkin-sender-okhttp3',
  ],
  sources=rglobs('*.scala'),
  scope='default',
  strict_deps=True,
  provides = artifact(
    org='co.actioniq',
    name='slick',
    repo=local,
  )
)

