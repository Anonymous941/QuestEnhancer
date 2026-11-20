#!/usr/bin/env python3
"""Weekly upstream check (.github/workflows/upstreams.yml).

For each entry in upstreams.json, read the default branch's head with
`git ls-remote` and compare it with "seen". A moved head opens an issue (or
comments on the open one with the same title). An empty "seen" only reports the
current head in the job summary, so a new entry never opens an issue by itself.
"""
import json
import os
import subprocess
import sys
import urllib.request

API = "https://api.github.com"
REPO = os.environ["GITHUB_REPOSITORY"]
TOKEN = os.environ["GITHUB_TOKEN"]


def gh(method, path, body=None):
    req = urllib.request.Request(
        API + path,
        method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Authorization": f"Bearer {TOKEN}", "Accept": "application/vnd.github+json"},
    )
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def head(repo):
    out = subprocess.run(
        ["git", "ls-remote", f"https://github.com/{repo}", "HEAD"],
        capture_output=True, text=True, timeout=60,
    )
    return out.stdout.split()[0] if out.returncode == 0 and out.stdout else None


def main():
    data = json.load(open("upstreams.json"))
    open_issues = gh("GET", f"/repos/{REPO}/issues?state=open&per_page=100")
    by_title = {i["title"]: i for i in open_issues}
    summary = ["| Upstream | Head | Seen | |", "| --- | --- | --- | --- |"]
    for u in data["upstreams"]:
        now = head(u["repo"])
        seen = u.get("seen", "")
        if now is None:
            state = "unreachable (deleted or private?)"
        elif not seen:
            state = "no seen commit yet: set it"
        elif now == seen:
            state = "unchanged"
        else:
            state = "CHANGED"
            title = f"Upstream changed: {u['name']}"
            compare = f"https://github.com/{u['repo']}/compare/{seen}...{now}"
            text = (f"{u['repo']} moved from `{seen[:7]}` to `{now[:7]}`.\n\n"
                    f"Changes: {compare}\n\nUsed for: {u['used']}\n\n"
                    "Review it, update what we ship or link if needed, then set "
                    f"\"seen\" for {u['repo']} in upstreams.json to `{now}`.")
            if title in by_title:
                gh("POST", f"/repos/{REPO}/issues/{by_title[title]['number']}/comments", {"body": text})
            else:
                gh("POST", f"/repos/{REPO}/issues", {"title": title, "body": text})
        summary.append(f"| [{u['name']}](https://github.com/{u['repo']}) | `{(now or '-')}` | `{seen[:7] or '-'}` | {state} |")
    with open(os.environ.get("GITHUB_STEP_SUMMARY", "/dev/stdout"), "a") as f:
        f.write("\n".join(summary) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
