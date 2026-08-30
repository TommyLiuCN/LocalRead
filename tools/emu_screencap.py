# -*- coding: utf-8 -*-
"""通过模拟器控制台截图(绕过 -no-window 下设备端 screencap 黑屏问题)"""
import socket, sys, time, pathlib

TOKEN = pathlib.Path.home().joinpath(".emulator_console_auth_token").read_text().strip()
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 5554
OUT = sys.argv[2] if len(sys.argv) > 2 else r"C:\Project\AndroidProject\shots\console.png"

s = socket.create_connection(("127.0.0.1", PORT), timeout=10)
f = s.makefile("rwb")

def cmd(text, wait=0.6):
    f.write((text + "\n").encode())
    f.flush()
    time.sleep(wait)
    data = b""
    s.settimeout(0.5)
    try:
        while True:
            chunk = s.recv(4096)
            if not chunk:
                break
            data += chunk
    except (socket.timeout, TimeoutError):
        pass
    return data.decode(errors="replace")

print(cmd(f"auth {TOKEN}"))
print(cmd("screencap " + OUT, wait=2.5))
cmd("quit")
s.close()
print("saved:", OUT)
