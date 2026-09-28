#!/usr/bin/env python3
"""Check the iOS keyboard extension against its memory budget on a simulator.

    scripts/ios-memory-check.py --udid <simulator> [--budget-mb 30] [--session-budget-mb 60] [--derived-data <dir>]

Runs the UI test KeyboardVisualTests/test16_memorySession (typing, every emoji category, an emoji
search, clipboard and quick settings, then hiding the keyboard) against a Debug build, reads the
extension's MemoryTrace (`tmp/memory.txt` in its data container), and checks two budgets:
- typing: the peak `phys_footprint` before any emoji is drawn (the first emoji loads iOS's emoji
  font machinery, ~18 MB, and each emoji drawn stays cached by Core Text, ~20 KB at 18 pt);
- session: the peak over the whole session, which must leave room under the device limit
  (about 77 MB on recent iPhones). MemoryGuard recycles the process on hide above 60% of it. Preconditions: MaKeeb enabled on that simulator and the hardware keyboard
disconnected (see .ai/skills/build-and-verify). A simulator only approximates a device: confirm
budget changes on an iPhone.
"""
import argparse
import glob
import os
import plistlib
import re
import subprocess
import sys
import tempfile

ap = argparse.ArgumentParser()
ap.add_argument("--udid", required=True)
ap.add_argument("--budget-mb", type=float, default=30.0, help="typing, before any emoji is drawn")
ap.add_argument("--session-budget-mb", type=float, default=60.0, help="the whole session, emoji included")
ap.add_argument("--derived-data", default=os.path.join(tempfile.gettempdir(), "makeeb-memory-check"))
args = ap.parse_args()

here = os.path.dirname(os.path.abspath(__file__))
project = os.path.join(here, "..", "app", "ios", "MaKeeb.xcodeproj")
env = dict(os.environ, DEVELOPER_DIR=os.environ.get("DEVELOPER_DIR", "/Applications/Xcode-beta.app/Contents/Developer"))
containers = os.path.expanduser(f"~/Library/Developer/CoreSimulator/Devices/{args.udid}/data/Containers/Data/PluginKitPlugin")


def extension_traces():
    found = []
    for meta in glob.glob(os.path.join(containers, "*", ".com.apple.mobile_container_manager.metadata.plist")):
        with open(meta, "rb") as f:
            if plistlib.load(f).get("MCMMetadataIdentifier") == "com.makeeb.ios.keyboard":
                found.append(os.path.join(os.path.dirname(meta), "tmp", "memory.txt"))
    return found


for trace in extension_traces():
    if os.path.exists(trace):
        os.remove(trace)

screenshots = tempfile.mkdtemp(prefix="makeeb-memory-")
test = subprocess.run(
    ["xcodebuild", "test", "-project", project, "-scheme", "MaKeeb",
     "-destination", f"platform=iOS Simulator,id={args.udid}", "-derivedDataPath", args.derived_data,
     "-only-testing:MaKeebUITests/KeyboardVisualTests/test16_memorySession"],
    env=dict(env, TEST_RUNNER_MAKEEB_SCREENSHOT_DIR=screenshots), capture_output=True, text=True,
)
if test.returncode != 0:
    print("\n".join(l for l in test.stdout.splitlines() if "error:" in l or "Test Case" in l) or test.stdout[-2000:])
    sys.exit(f"UI test failed (xcodebuild exit {test.returncode})")

lines = [l for trace in extension_traces() if os.path.exists(trace) for l in open(trace)]
if not lines:
    sys.exit("no MemoryTrace found: is it a Debug build, and did MaKeeb show?")
print("".join(lines), end="")
def peak_of(entries):
    return max((float(re.search(r"peak=([\d.]+)MB", l).group(1)) for l in entries), default=0.0)


first_emoji = next((i for i, l in enumerate(lines) if "panel Emoji" in l), len(lines))
typing, session = peak_of(lines[:first_emoji]), peak_of(lines)
recycled = any("recycling" in l for l in lines)
print(f"\ntyping peak {typing:.1f} MB (budget {args.budget_mb:.0f}); session peak {session:.1f} MB "
      f"(budget {args.session_budget_mb:.0f}); recycled on hide: {'yes' if recycled else 'no'}")
failures = []
if typing > args.budget_mb:
    failures.append(f"typing over budget by {typing - args.budget_mb:.1f} MB")
if session > args.session_budget_mb:
    failures.append(f"session over budget by {session - args.session_budget_mb:.1f} MB")
sys.exit("; ".join(failures) or 0)
