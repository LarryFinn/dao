import json
import os
import re

raw_data = open(os.path.join(os.path.dirname(__file__), "Versions.json")).read()
fixed_data = re.sub('\/\*.*?\*\/', '', raw_data)

json_data = json.loads(fixed_data)

def get(path):
  keys = path.split(".")
  version = json_data
  for key in keys:
    if key in version:
      version = version[key]
    else:
      raise Exception("Can not find version key `" + key + "` as part of the path `" + path + "`")
  return version
