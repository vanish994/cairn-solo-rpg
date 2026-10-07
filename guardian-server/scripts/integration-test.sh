#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:?uso: integration-test.sh <base-url>}"

health="$(curl --fail --silent --show-error "$BASE_URL/health")"
python3 - "$health" <<'PY'
import json, sys
body = json.loads(sys.argv[1])
assert body == {"status": "ok", "service": "cairn-guardian"}, body
PY

echo "health: ok"

payload='{"playerIntent":"Observo a porta e procuro uma forma segura de abri-la.","campaign":{"campaignId":"integration-test","characterName":"Mira","turn":1,"sceneId":"old-tower","sceneType":"EXPLORATION","sceneTitle":"A torre","sceneDescription":"Uma torre abandonada sob a chuva.","exits":["porta","trilha"],"guardianHistory":[],"worldCanon":{"locations":[],"npcs":[],"importantItems":[],"quests":[],"discoveries":[]},"recentHistory":[],"stats":{"str":10,"dex":12,"wil":9,"hp":6,"maxHp":6,"armor":0,"deprived":false,"critical":false,"dead":false},"inventory":[]}}'

response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT
status="$(curl --silent --show-error --output "$response_file" --write-out '%{http_code}' \
  -H 'Content-Type: application/json' \
  -X POST "$BASE_URL/guardian" \
  --data "$payload")"

if [[ "$status" != "200" ]]; then
  echo "guardian: unexpected HTTP status $status" >&2
  cat "$response_file" >&2
  exit 1
fi

python3 - "$response_file" <<'PY'
import json, sys
from pathlib import Path
body = json.loads(Path(sys.argv[1]).read_text())
required = {"narration", "sceneTitle", "sceneDescription", "suggestedActions", "ruleRequest", "canonProposals", "growthEvidenceProposals", "interactionId"}
missing = required - body.keys()
assert not missing, f"missing response fields: {sorted(missing)}"
assert isinstance(body["narration"], str) and body["narration"].strip()
assert isinstance(body["suggestedActions"], list)
assert isinstance(body["canonProposals"], list)
assert isinstance(body["growthEvidenceProposals"], list)
assert isinstance(body["interactionId"], str) and body["interactionId"].strip()
rule = body["ruleRequest"]
if rule is not None:
    assert rule["type"] in {"SAVE", "DAMAGE", "FATIGUE", "REST", "STABILIZE_CRITICAL", "RECOVER_SCAR"}
    if rule["type"] == "SAVE":
        assert rule.get("attribute") in {"STR", "DEX", "WIL"}
for proposal in body["canonProposals"]:
    assert proposal["type"] in {"UPSERT_NPC", "DISCOVER_LOCATION", "ADD_IMPORTANT_ITEM", "CREATE_QUEST", "UPDATE_QUEST", "ADD_DISCOVERY", "ADD_RUMOR"}
    assert isinstance(proposal["id"], str) and proposal["id"]
    assert proposal["status"] in {"CONFIRMED", "RUMOR", "DISCOVERED"}
    assert proposal["source"] in {"PLAYER", "GUARDIAN", "NPC", "RULES_ENGINE", "SYSTEM"}
for proposal in body["growthEvidenceProposals"]:
    assert isinstance(proposal["id"], str) and proposal["id"]
    assert isinstance(proposal["summary"], str) and proposal["summary"].strip()
    assert isinstance(proposal["relatedEntityIds"], list)
    assert any(proposal[key] for key in ("focusedPattern", "seriousRisk", "uniqueInteraction"))
print("guardian: JSON contract ok")
PY
