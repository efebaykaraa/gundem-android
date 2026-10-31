#!/usr/bin/env python3
"""Read-only live source verification, using Python standard library."""
import json
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
SOURCES={"sol":"https://haber.sol.org.tr/rss.xml","tkp":"https://www.tkp.org.tr/feed/","pe":"https://patronlarinensesindeyiz.org/feed/"}
results=[]
for name,url in SOURCES.items():
    row={"source":name,"url":url,"verified_at":datetime.now(timezone.utc).isoformat()}
    try:
        request=urllib.request.Request(url,headers={"User-Agent":"Gundem/0.1 (RSS verification)"})
        with urllib.request.urlopen(request,timeout=30) as response:
            data=response.read(12*1024*1024+1)
            if len(data)>12*1024*1024:raise ValueError("12 MiB limit")
            root=ET.fromstring(data)
            items=root.findall(".//item")
            row.update(status=response.status,final_url=response.url,root=root.tag,item_count=len(items),first_title=items[0].findtext("title") if items else None)
    except Exception as error:
        row["error"]=str(error)
    results.append(row)
print(json.dumps(results,ensure_ascii=False,indent=2))
