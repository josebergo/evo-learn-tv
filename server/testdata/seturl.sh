#!/bin/bash
# 用法: seturl.sh <URL>  —— 在设置页把配置地址换成 URL 并确认生效
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
URL="$1"
dump_txt() {
  MSYS_NO_PATHCONV=1 "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  MSYS_NO_PATHCONV=1 "$ADB" exec-out cat /sdcard/ui.xml 2>/dev/null
}
btn_center() {
  dump_txt | python -c "
import sys,re
xml=sys.stdin.buffer.read().decode('utf-8',errors='ignore')
for n in re.findall(r'<node[^>]*>',xml):
    t=re.search(r'text=\"([^\"]*)\"',n).group(1)
    if '$1' in t:
        b=re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',n)
        if b:
            print((int(b.group(1))+int(b.group(3)))//2,(int(b.group(2))+int(b.group(4)))//2); break
"
}
read CX CY <<< "$(btn_center '修改配置地址')"
[ -z "$CX" ] && { echo "ERR: no 修改配置地址 button"; exit 1; }
"$ADB" shell input tap $CX $CY; sleep 1.4
read EX EY <<< "$(btn_center 'http')"
if [ -z "$EX" ]; then
  read EX EY <<< "$(btn_center '输入配置')"
fi
"$ADB" shell input tap $EX $EY; sleep 0.5
MSYS_NO_PATHCONV=1 "$ADB" shell input keyevent 123; sleep 0.3
for i in $(seq 1 60); do MSYS_NO_PATHCONV=1 "$ADB" shell input keyevent 67 >/dev/null 2>&1; done
sleep 0.3
"$ADB" shell input text "$URL"; sleep 0.6
read OX OY <<< "$(btn_center '确定')"
"$ADB" shell input tap $OX $OY; sleep 3
dump_txt | python -c "
import sys,re
xml=sys.stdin.buffer.read().decode('utf-8',errors='ignore')
for t in [t for t in re.findall(r'text=\"([^\"]+)\"',xml) if '来源' in t or '当前地址' in t]: print('STATUS:',t[:130].replace(chr(10),' / '))"