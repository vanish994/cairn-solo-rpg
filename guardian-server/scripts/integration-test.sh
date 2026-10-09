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
status="000"
for attempt in {1..5}; do
  status="$(curl --silent --show-error --output "$response_file" --write-out '%{http_code}' \
    --connect-timeout 10 --max-time 60 \
    -H 'Content-Type: application/json' \
    -X POST "$BASE_URL/guardian" \
    --data "$payload" || true)"
  if [[ "$status" == "200" || "$status" == "400" || "$status" == "401" || "$status" == "403" || "$status" == "404" || "$status" == "405" || "$status" == "500" ]]; then
    break
  fi
  if [[ "$attempt" != "5" ]]; then
    echo "guardian: transient HTTP status $status, retrying ($attempt/5)" >&2
    sleep 5
  fi
done

if [[ "$status" != "200" ]]; then
  echo "guardian: unexpected HTTP status $status" >&2
  cat "$response_file" >&2
  exit 1
fi

python3 - "$response_file" <<'PY'
import json, sys
from pathlib import Path
import re
body = json.loads(Path(sys.argv[1]).read_text())
required = {"narration", "sceneTitle", "sceneDescription", "suggestedActions", "ruleRequest", "canonProposals", "growthEvidenceProposals", "growthChangeProposals", "interactionId"}
missing = required - body.keys()
assert not missing, f"missing response fields: {sorted(missing)}"
assert isinstance(body["narration"], str) and body["narration"].strip()
assert isinstance(body["suggestedActions"], list)
assert isinstance(body["canonProposals"], list)
assert isinstance(body["growthEvidenceProposals"], list)
assert isinstance(body["interactionId"], str) and body["interactionId"].strip()
rule = body["ruleRequest"]
if rule is not None:
    assert rule["type"] in {"SAVE", "DAMAGE", "FATIGUE", "REST", "STABILIZE_CRITICAL", "RECOVER_SCAR", "REWARD"}
    if rule["type"] == "SAVE":
        assert rule.get("attribute") in {"STR", "DEX", "WIL"}, f"invalid SAVE request: {json.dumps(body, ensure_ascii=False)}"
    if rule["type"] in {"DAMAGE", "FATIGUE"}:
        assert type(rule.get("amount")) is int and rule["amount"] >= 1, f"invalid {rule['type']} request: {json.dumps(body, ensure_ascii=False)}"
    if rule["type"] == "REWARD":
        assert set(rule) == {"type", "id", "status", "amountGp", "itemCatalogIds"}, f"invalid REWARD fields: {json.dumps(body, ensure_ascii=False)}"
        assert isinstance(rule["id"], str) and re.fullmatch(r"[a-z0-9-]{3,80}", rule["id"]), f"invalid REWARD id: {json.dumps(body, ensure_ascii=False)}"
        assert rule["status"] in {"OFFERED", "PAID"}, f"invalid REWARD status: {json.dumps(body, ensure_ascii=False)}"
        assert type(rule["amountGp"]) is int and 0 <= rule["amountGp"] <= 2147483647, f"invalid REWARD amountGp: {json.dumps(body, ensure_ascii=False)}"
        assert isinstance(rule["itemCatalogIds"], list) and len(rule["itemCatalogIds"]) <= 5, f"invalid REWARD item list: {json.dumps(body, ensure_ascii=False)}"
        assert all(isinstance(item_id, str) and re.fullmatch(r"[a-z0-9-]{3,80}", item_id) for item_id in rule["itemCatalogIds"]), f"invalid REWARD item ID: {json.dumps(body, ensure_ascii=False)}"
        if rule["status"] == "PAID":
            assert rule["amountGp"] > 0 or rule["itemCatalogIds"], f"empty PAID reward: {json.dumps(body, ensure_ascii=False)}"
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
for proposal in body["growthChangeProposals"]:
    assert isinstance(proposal["id"], str) and proposal["id"]
    assert isinstance(proposal["evidenceIds"], list) and proposal["evidenceIds"]
    assert proposal["changeType"] in {"RAISE_MAX_ATTRIBUTE", "KEEP_HIGHER_ATTRIBUTE", "GAIN_ABILITY"}
    assert isinstance(proposal["rationale"], str) and proposal["rationale"].strip()
print("guardian: JSON contract ok")
PY
