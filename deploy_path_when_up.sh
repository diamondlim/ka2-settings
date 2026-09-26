#!/bin/bash
# Deploys the 120 m predicted-route change to the box as soon as the box answers.
# Safe to run repeatedly: copy, compile, restart, then verify the constant actually landed.
cd /config/.hermes/apk/box || exit 1
LOG=/config/.hermes/apk/path_deploy.log
R=/config/.hermes/apk/ka2ssh_retry.sh

echo "$(date '+%F %T') watcher started (pid $$), retrying every 2 min for 6 h" >> "$LOG"

for i in $(seq 1 180); do
  if "$R" 'true' >/dev/null 2>&1; then
    echo "$(date '+%F %T') box answering, deploying" >> "$LOG"
    "$R" 'sudo tee /data/hermes/ka2_pose_pub.py >/dev/null' < ka2_pose_pub.py 2>>"$LOG"
    OUT=$("$R" 'sudo /usr/bin/python3 -m py_compile /data/hermes/ka2_pose_pub.py \
      && sudo systemctl restart ka2pose && sleep 6 && echo "ka2pose=$(systemctl is-active ka2pose) \
      range=$(sudo grep -c "PATH_RANGE_M = 120.0" /data/hermes/ka2_pose_pub.py)"' 2>&1)
    echo "$(date '+%F %T') result: $OUT" >> "$LOG"
    case "$OUT" in *"ka2pose=active range=1"*)
      echo "$(date '+%F %T') DEPLOYED AND VERIFIED" >> "$LOG"
      exit 0;;
    esac
  else
    echo "$(date '+%F %T') attempt $i: box not answering" >> "$LOG"
  fi
  sleep 120
done
echo "$(date '+%F %T') gave up after 6 hours; box never answered" >> "$LOG"
exit 1
