#!/usr/bin/env python3
"""Deterministic local Gemini Interactions API stub for Guardian integration tests."""

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import argparse
import json

# https://ai.google.dev/gemini-api/docs/structured-output
MAX_SCHEMA_DEPTH = 5  # Conservative project guardrail; Google documents no numeric cutoff.
RULE_REQUEST_TYPES = frozenset(
    {"SAVE", "DAMAGE", "FATIGUE", "REST", "STABILIZE_CRITICAL", "RECOVER_SCAR", "BEGIN_COMBAT", "REWARD"}
)
SUPPORTED_SCHEMA_KEYWORDS = frozenset(
    {
        "type",
        "properties",
        "required",
        "additionalProperties",
        "items",
        "anyOf",
        "prefixItems",
        "enum",
        "minimum",
        "maximum",
        "minItems",
        "maxItems",
        "format",
        "title",
        "description",
    }
)


def unsupported_schema_keywords(schema, path="$schema"):
    """Return unsupported keywords, recursively following JSON Schema positions."""
    if not isinstance(schema, dict):
        return [f"{path}: expected schema object"]

    issues = [f"{path}.{key}" for key in schema if key not in SUPPORTED_SCHEMA_KEYWORDS]

    properties = schema.get("properties")
    if properties is not None:
        if not isinstance(properties, dict):
            issues.append(f"{path}.properties: expected object")
        else:
            for name, child in properties.items():
                issues.extend(unsupported_schema_keywords(child, f"{path}.properties.{name}"))

    items = schema.get("items")
    if isinstance(items, dict):
        issues.extend(unsupported_schema_keywords(items, f"{path}.items"))
    elif items is not None and not isinstance(items, bool):
        issues.append(f"{path}.items: expected schema object or boolean")

    additional_properties = schema.get("additionalProperties")
    if isinstance(additional_properties, dict):
        issues.extend(unsupported_schema_keywords(additional_properties, f"{path}.additionalProperties"))

    for keyword in ("anyOf", "prefixItems"):
        alternatives = schema.get(keyword)
        if alternatives is None:
            continue
        if not isinstance(alternatives, list):
            issues.append(f"{path}.{keyword}: expected array")
            continue
        for index, child in enumerate(alternatives):
            issues.extend(unsupported_schema_keywords(child, f"{path}.{keyword}[{index}]"))

    return issues


def schema_depth(schema):
    """Return maximum depth following positions that contain child schemas."""
    if not isinstance(schema, dict):
        return 0

    children = []
    properties = schema.get("properties")
    if isinstance(properties, dict):
        children.extend(properties.values())
    items = schema.get("items")
    if isinstance(items, dict):
        children.append(items)
    additional_properties = schema.get("additionalProperties")
    if isinstance(additional_properties, dict):
        children.append(additional_properties)
    for keyword in ("anyOf", "prefixItems"):
        alternatives = schema.get(keyword)
        if isinstance(alternatives, list):
            children.extend(alternatives)
    return 1 + max((schema_depth(child) for child in children), default=0)


def validate_interactions_request(request):
    if not isinstance(request, dict):
        raise ValueError("request body must be a JSON object")
    required = {"model", "input", "system_instruction", "generation_config", "response_format", "store"}
    missing = sorted(required - request.keys())
    if missing:
        raise ValueError(f"missing request fields: {missing}")
    if not isinstance(request["input"], str) or not request["input"].strip():
        raise ValueError("input must be non-empty text")
    if not isinstance(request["system_instruction"], str) or not request["system_instruction"].strip():
        raise ValueError("system_instruction must be non-empty text")

    response_format = request["response_format"]
    if not isinstance(response_format, dict):
        raise ValueError("response_format must be an object")
    if response_format.get("type") != "text" or response_format.get("mime_type") != "application/json":
        raise ValueError("response_format must request JSON text")

    schema = response_format.get("schema")
    if not isinstance(schema, dict):
        raise ValueError("response_format.schema must be an object")
    unsupported = unsupported_schema_keywords(schema)
    if unsupported:
        raise ValueError(f"unsupported JSON Schema keywords: {sorted(unsupported)}")

    depth = schema_depth(schema)
    if depth > MAX_SCHEMA_DEPTH:
        raise ValueError(
            f"response_format.schema depth {depth} exceeds project limit {MAX_SCHEMA_DEPTH}"
        )

    # The full encounter is semantically validated after generation; keep its wire schema shallow.
    try:
        rule_request = schema["properties"]["ruleRequest"]
        alternatives = rule_request["anyOf"]
    except (KeyError, TypeError) as exc:
        raise ValueError("type-specific ruleRequest alternatives are missing") from exc

    expected_fields = {
        frozenset({"SAVE"}): ["type", "attribute"],
        frozenset({"DAMAGE", "FATIGUE"}): ["type", "amount"],
        frozenset({"REST", "STABILIZE_CRITICAL", "RECOVER_SCAR"}): ["type"],
        frozenset({"BEGIN_COMBAT"}): ["type", "encounter"],
        frozenset({"REWARD"}): ["type", "id", "status", "amountGp", "itemCatalogIds"],
    }
    seen_groups = set()
    has_nullable_branch = False

    for alternative in alternatives:
        if not isinstance(alternative, dict):
            raise ValueError("ruleRequest anyOf branches must be schema objects")
        if alternative.get("type") == "null":
            has_nullable_branch = True
            continue
        if alternative.get("type") != "object":
            raise ValueError("ruleRequest branches must be null or object")

        properties = alternative.get("properties")
        if not isinstance(properties, dict) or not isinstance(properties.get("type"), dict):
            raise ValueError("ruleRequest branch must define its type enum")
        request_type = properties["type"]
        group = frozenset(request_type.get("enum", []))
        required = expected_fields.get(group)
        if request_type.get("type") != "string" or required is None:
            raise ValueError("ruleRequest type groups do not match supported request types")
        if group in seen_groups:
            raise ValueError("ruleRequest contains a duplicate type group")
        seen_groups.add(group)
        if set(properties) != set(required) or alternative.get("required") != required:
            raise ValueError(f"ruleRequest fields for {sorted(group)} must be required: {required}")
        if alternative.get("additionalProperties") is not False:
            raise ValueError("ruleRequest branches must reject undeclared top-level fields")

        if group == frozenset({"SAVE"}):
            attribute = properties["attribute"]
            if attribute.get("type") != "string" or set(attribute.get("enum", [])) != {"STR", "DEX", "WIL"}:
                raise ValueError("SAVE attribute must be the STR/DEX/WIL enum")
        elif group == frozenset({"DAMAGE", "FATIGUE"}):
            amount = properties["amount"]
            if amount.get("type") != "integer" or amount.get("minimum") != 1:
                raise ValueError("DAMAGE/FATIGUE amount must be a positive integer")
        elif group == frozenset({"BEGIN_COMBAT"}):
            encounter = properties["encounter"]
            if encounter != {"type": "object", "additionalProperties": True}:
                raise ValueError("BEGIN_COMBAT encounter must remain a shallow object")
        elif group == frozenset({"REWARD"}):
            reward_id = properties["id"]
            status = properties["status"]
            amount = properties["amountGp"]
            item_ids = properties["itemCatalogIds"]
            if reward_id.get("type") != "string":
                raise ValueError("REWARD id must remain an unconstrained string in the wire schema")
            if status.get("type") != "string" or set(status.get("enum", [])) != {"OFFERED", "PAID"}:
                raise ValueError("REWARD status must be the OFFERED/PAID enum")
            if amount.get("type") != "integer" or amount.get("minimum") != 0 or amount.get("maximum") != 2147483647:
                raise ValueError("REWARD amountGp must be a non-negative 32-bit integer")
            if (
                item_ids.get("type") != "array"
                or item_ids.get("maxItems") != 5
                or item_ids.get("items") != {"type": "string"}
            ):
                raise ValueError("REWARD itemCatalogIds must be at most five strings")

    if not has_nullable_branch or seen_groups != set(expected_fields):
        raise ValueError("ruleRequest must cover null and every supported type group")
    if set().union(*seen_groups) != RULE_REQUEST_TYPES:
        raise ValueError("ruleRequest type groups do not match supported request types")


class Handler(BaseHTTPRequestHandler):
    def _send_json(self, status, body):
        encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self):
        if self.path != "/health":
            self._send_json(404, {"error": "not_found"})
            return
        self._send_json(200, {"status": "ok"})

    def do_POST(self):
        if self.path != "/v1beta/interactions":
            self._send_json(404, {"error": "not_found"})
            return
        if self.headers.get("x-goog-api-key") != "local-test-key":
            self._send_json(401, {"error": "unexpected_api_key"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            request = json.loads(self.rfile.read(length))
            validate_interactions_request(request)
        except (ValueError, json.JSONDecodeError) as exc:
            self._send_json(
                400,
                {"error": {"code": 400, "message": str(exc), "status": "INVALID_ARGUMENT"}},
            )
            return

        guardian_response = {
            "narration": "A porta range sob a chuva, mas o trinco cede.",
            "sceneTitle": "A torre",
            "sceneDescription": "A passagem estreita se abre para uma escadaria escura.",
            "suggestedActions": ["Examinar a escadaria"],
            "ruleRequest": {"type": "SAVE", "attribute": "DEX"},
            "canonProposals": [],
            "growthEvidenceProposals": [],
            "growthChangeProposals": [],
        }
        self._send_json(
            200,
            {
                "id": "local-gemini-interaction",
                "steps": [
                    {
                        "type": "model_output",
                        "content": [
                            {"type": "text", "text": json.dumps(guardian_response, ensure_ascii=False)}
                        ],
                    }
                ],
            },
        )

    def log_message(self, format_string, *args):
        print(f"mock-gemini: {format_string % args}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18787)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Mock Gemini Interactions API listening on {args.host}:{args.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
