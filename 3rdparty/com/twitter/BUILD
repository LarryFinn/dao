import Versions

ORG='com.twitter'

def make_lib(name, rev, excludes=[]):
  jar_library(name=name,
    jars=[
      scala_jar(org=ORG, name=name, rev=rev, excludes=excludes)
    ]
  )

make_lib('bijection-util', Versions.get('Twitter.bijection'))

EXCLUDE_GUAVA = exclude(org='com.google.guava', name='guava')

UTIL_REV = Versions.get('Twitter.util')
UTIL_EXCLUDES = [
  EXCLUDE_GUAVA,
  exclude(org='junit', name='junit'),
  exclude(org='org.scala-tools.testing'),
  exclude(org='org.mockito')
]
make_lib('util-app', UTIL_REV, excludes=UTIL_EXCLUDES)
make_lib('util-core', UTIL_REV, excludes=UTIL_EXCLUDES)
