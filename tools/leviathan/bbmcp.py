"""A tiny client for the Blockbench MCP plugin's HTTP endpoint, for when the MCP tools are not loaded in the session.

    python tools/leviathan/bbmcp.py list
    python tools/leviathan/bbmcp.py call TOOL '{"json": "args"}'
    python tools/leviathan/bbmcp.py eval FILE.js        (risky_eval with the file's code)
"""
import json
import sys
import urllib.request

URL = "http://localhost:3000/bb-mcp"
HDR = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}


def post(body, sid=None):
    h = dict(HDR)
    if sid:
        h["mcp-session-id"] = sid
    req = urllib.request.Request(URL, json.dumps(body).encode(), h)
    with urllib.request.urlopen(req, timeout=300) as r:
        sid = r.headers.get("mcp-session-id") or sid
        raw = r.read().decode("utf-8", "replace")
    msgs = []
    for line in raw.splitlines():
        if line.startswith("data:"):
            msgs.append(json.loads(line[5:]))
    if not msgs and raw.strip():
        msgs.append(json.loads(raw))
    return sid, msgs


def session():
    sid, _ = post({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
        "protocolVersion": "2025-03-26", "capabilities": {}, "clientInfo": {"name": "bbmcp", "version": "1"}}})
    post({"jsonrpc": "2.0", "method": "notifications/initialized"}, sid)
    return sid


def call(sid, name, args):
    _, msgs = post({"jsonrpc": "2.0", "id": 2, "method": "tools/call", "params": {"name": name, "arguments": args}}, sid)
    for m in msgs:
        for c in m.get("result", {}).get("content", []):
            if c.get("type") == "text":
                print(c["text"])
            elif c.get("type") == "image":
                import base64
                out = args.get("_out", "bb_shot.png")
                open(out, "wb").write(base64.b64decode(c["data"]))
                print("image ->", out)
        if "error" in m:
            print("ERROR", m["error"])


def main():
    sid = session()
    cmd = sys.argv[1]
    if cmd == "list":
        _, msgs = post({"jsonrpc": "2.0", "id": 2, "method": "tools/list"}, sid)
        for t in msgs[0]["result"]["tools"]:
            print(t["name"])
    elif cmd == "call":
        args = json.loads(sys.argv[3]) if len(sys.argv) > 3 else {}
        call(sid, sys.argv[2], args)
    elif cmd == "eval":
        import re
        code = re.sub(r"//[^\n]*", "", open(sys.argv[2], encoding="utf-8").read())   # the plugin refuses comments
        call(sid, "risky_eval", {"code": code})


if __name__ == "__main__":
    main()
