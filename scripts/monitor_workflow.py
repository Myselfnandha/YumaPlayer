#!/usr/bin/env python3
"""
GitHub Actions Workflow Monitor
Monitors GitHub Actions runs for YumaPlayer with live step tracking,
colorized output, log inspection on failure, and release asset reporting.
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

# ANSI Color codes
RESET = "\033[0m"
BOLD = "\033[1m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
RED = "\033[31m"
CYAN = "\033[36m"
BLUE = "\033[34m"
DIM = "\033[2m"

def get_github_token() -> str:
    """Finds GitHub Token from env or ~/.git-credentials."""
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        return token.strip()
    
    cred_file = os.path.expanduser("~/.git-credentials")
    if os.path.exists(cred_file):
        try:
            with open(cred_file, "r") as f:
                for line in f:
                    # Format: https://user:token@github.com
                    match = re.search(r"https?://([^:]+):([^@]+)@github\.com", line)
                    if match:
                        return match.group(2).strip()
        except Exception:
            pass
    return ""

def api_request(url: str, token: str) -> dict:
    """Sends authenticated request to GitHub API."""
    headers = {
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "YumaPlayer-Workflow-Monitor"
    }
    if token:
        headers["Authorization"] = f"token {token}"
    
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8") if e.fp else ""
        print(f"{RED}API Error {e.code}: {e.reason}{RESET}\n{body}", file=sys.stderr)
        raise
    except Exception as e:
        print(f"{RED}Network Error: {e}{RESET}", file=sys.stderr)
        raise

def api_request_text(url: str, token: str) -> str:
    """Fetches plain text (e.g., raw logs) from GitHub API."""
    headers = {
        "Accept": "application/vnd.github.v3+json",
        "User-Agent": "YumaPlayer-Workflow-Monitor"
    }
    if token:
        headers["Authorization"] = f"token {token}"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.read().decode("utf-8", errors="replace")
    except Exception as e:
        return f"Unable to fetch logs: {e}"

def format_duration(start_iso: str, end_iso: str = None) -> str:
    """Calculates elapsed time in mm:ss."""
    try:
        start = datetime.fromisoformat(start_iso.replace("Z", "+00:00"))
        if end_iso:
            end = datetime.fromisoformat(end_iso.replace("Z", "+00:00"))
        else:
            end = datetime.now(timezone.utc)
        total_seconds = int((end - start).total_seconds())
        if total_seconds < 0:
            total_seconds = 0
        minutes = total_seconds // 60
        seconds = total_seconds % 60
        return f"{minutes:02d}:{seconds:02d}"
    except Exception:
        return "--:--"

def status_icon(status: str, conclusion: str = None) -> str:
    if status == "in_progress":
        return f"{YELLOW}⟳ IN_PROGRESS{RESET}"
    if status == "queued":
        return f"{DIM}⏳ QUEUED{RESET}"
    if conclusion == "success":
        return f"{GREEN}✓ SUCCESS{RESET}"
    if conclusion == "failure":
        return f"{RED}✗ FAILED{RESET}"
    if conclusion == "cancelled":
        return f"{DIM}⊘ CANCELLED{RESET}"
    if conclusion == "skipped":
        return f"{DIM}↷ SKIPPED{RESET}"
    return f"{status} ({conclusion})"

def step_icon(status: str, conclusion: str = None) -> str:
    if status == "in_progress":
        return f"{YELLOW}⟳{RESET}"
    if conclusion == "success":
        return f"{GREEN}✓{RESET}"
    if conclusion == "failure":
        return f"{RED}✗{RESET}"
    if conclusion == "skipped":
        return f"{DIM}↷{RESET}"
    return f"{DIM}·{RESET}"

def get_latest_run(repo: str, token: str) -> dict:
    url = f"https://api.github.com/repos/{repo}/actions/runs?per_page=1"
    data = api_request(url, token)
    runs = data.get("workflow_runs", [])
    if not runs:
        raise RuntimeError(f"No workflow runs found for {repo}")
    return runs[0]

def get_run_details(repo: str, run_id: int, token: str) -> dict:
    url = f"https://api.github.com/repos/{repo}/actions/runs/{run_id}"
    return api_request(url, token)

def get_run_jobs(repo: str, run_id: int, token: str) -> list:
    url = f"https://api.github.com/repos/{repo}/actions/runs/{run_id}/jobs"
    data = api_request(url, token)
    return data.get("jobs", [])

def get_latest_release(repo: str, token: str) -> dict:
    url = f"https://api.github.com/repos/{repo}/releases/latest"
    try:
        return api_request(url, token)
    except Exception:
        return {}

def render_status(repo: str, run: dict, jobs: list, token: str):
    # Header
    run_id = run["id"]
    name = run.get("name", "Workflow")
    event = run.get("event", "push")
    branch = run.get("head_branch") or run.get("head_sha", "")[:7]
    status = run.get("status", "unknown")
    conclusion = run.get("conclusion")
    created_at = run.get("created_at", "")
    duration = format_duration(created_at, run.get("updated_at") if conclusion else None)
    url = run.get("html_url", "")

    print(f"\n{BOLD}{CYAN}══════════════════════════════════════════════════════════════════════════{RESET}")
    print(f"{BOLD}Repo:{RESET} {repo}   {BOLD}Workflow:{RESET} {name} (Run #{run_id})")
    print(f"{BOLD}Event:{RESET} {event}   {BOLD}Ref:{RESET} {branch}   {BOLD}Duration:{RESET} {duration}")
    print(f"{BOLD}Status:{RESET} {status_icon(status, conclusion)}   {BOLD}URL:{RESET} {url}")
    print(f"{BOLD}{CYAN}──────────────────────────────────────────────────────────────────────────{RESET}")

    # Jobs & Steps
    for job in jobs:
        j_name = job.get("name", "Job")
        j_status = job.get("status", "")
        j_conclusion = job.get("conclusion")
        j_duration = format_duration(job.get("started_at", created_at), job.get("completed_at"))
        
        print(f"\n{BOLD}▶ Job: {j_name}{RESET}  [{status_icon(j_status, j_conclusion)}]  ({j_duration})")
        
        steps = job.get("steps", [])
        for step in steps:
            s_name = step.get("name", "")
            s_status = step.get("status", "")
            s_conclusion = step.get("conclusion")
            s_icon = step_icon(s_status, s_conclusion)
            
            s_duration = ""
            if step.get("started_at"):
                s_duration = f"({format_duration(step['started_at'], step.get('completed_at'))})"
            
            print(f"    {s_icon} {s_name:<46} {s_duration}")

    # If run succeeded, print releases/assets if available
    if conclusion == "success":
        rel = get_latest_release(repo, token)
        if rel and rel.get("assets"):
            print(f"\n{BOLD}{GREEN}📦 Release Artifacts Available:{RESET}")
            print(f"   Release: {rel.get('name')} ({rel.get('tag_name')})")
            print(f"   URL:     {rel.get('html_url')}")
            for asset in rel.get("assets", []):
                size_mb = asset.get("size", 0) / (1024 * 1024)
                print(f"   • {BOLD}{asset.get('name')}{RESET} ({size_mb:.2f} MB)")
                print(f"     {DIM}{asset.get('browser_download_url')}{RESET}")

    print(f"{BOLD}{CYAN}══════════════════════════════════════════════════════════════════════════{RESET}")

def main():
    parser = argparse.ArgumentParser(description="Monitor GitHub Actions workflow runs.")
    parser.add_argument("--repo", default="Myselfnandha/YumaPlayer", help="GitHub repo in owner/repo format")
    parser.add_argument("--run-id", type=int, default=None, help="Specific workflow run ID to monitor")
    parser.add_argument("--interval", type=int, default=15, help="Polling interval in seconds (default: 15)")
    parser.add_argument("--once", action="store_true", help="Print current status once and exit")
    parser.add_argument("--logs", action="store_true", help="Fetch failed job logs on failure")
    args = parser.parse_args()

    token = get_github_token()
    if not token:
        print(f"{YELLOW}Warning: No GitHub token found. Unauthenticated requests have a 60 req/hour limit.{RESET}")

    # Resolve run ID
    run_id = args.run_id
    if not run_id:
        try:
            latest = get_latest_run(args.repo, token)
            run_id = latest["id"]
        except Exception as e:
            print(f"{RED}Failed to determine latest run: {e}{RESET}", file=sys.stderr)
            sys.exit(1)

    print(f"{BLUE}Monitoring workflow run #{run_id} on {args.repo}...{RESET}")

    while True:
        try:
            run = get_run_details(args.repo, run_id, token)
            jobs = get_run_jobs(args.repo, run_id, token)
            
            # Clear screen if running continuously
            if not args.once and sys.stdout.isatty():
                print("\033c", end="")
            
            render_status(args.repo, run, jobs, token)

            # Check if finished
            conclusion = run.get("conclusion")
            if conclusion:
                if conclusion == "success":
                    print(f"\n{GREEN}✨ Workflow completed successfully!{RESET}\n")
                    sys.exit(0)
                else:
                    print(f"\n{RED}❌ Workflow completed with conclusion: {conclusion}{RESET}\n")
                    if args.logs:
                        for job in jobs:
                            if job.get("conclusion") == "failure":
                                jid = job["id"]
                                print(f"{BOLD}{RED}Fetching tail logs for failed job #{jid}...{RESET}")
                                log_url = f"https://api.github.com/repos/{args.repo}/actions/jobs/{jid}/logs"
                                logs = api_request_text(log_url, token)
                                lines = logs.splitlines()[-60:]
                                print("\n".join(lines))
                    sys.exit(1)

            if args.once:
                break

            time.sleep(args.interval)

        except KeyboardInterrupt:
            print(f"\n{YELLOW}Monitoring stopped by user.{RESET}")
            sys.exit(0)
        except Exception as e:
            print(f"\n{RED}Error during polling: {e}{RESET}")
            if args.once:
                sys.exit(1)
            time.sleep(args.interval)

if __name__ == "__main__":
    main()
