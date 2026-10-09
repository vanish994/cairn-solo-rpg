import unittest

from mock_gemini import unsupported_schema_keywords, validate_interactions_request


ALLOWED_REQUEST_TYPES = [
    "SAVE",
    "DAMAGE",
    "FATIGUE",
    "REST",
    "STABILIZE_CRITICAL",
    "RECOVER_SCAR",
    "BEGIN_COMBAT",
]


def shallow_response_schema():
    return {
        "type": "object",
        "properties": {
            "ruleRequest": {
                "type": ["object", "null"],
                "properties": {
                    "type": {"type": "string", "enum": ALLOWED_REQUEST_TYPES},
                },
                "required": ["type"],
                "additionalProperties": True,
            },
        },
        "required": ["ruleRequest"],
        "additionalProperties": False,
    }


def interactions_request(schema):
    return {
        "model": "gemini-3.5-flash-lite",
        "input": "Uma porta range sob a chuva.",
        "system_instruction": "Responda com JSON estruturado.",
        "generation_config": {"thinking_level": "low", "max_output_tokens": 700},
        "response_format": {"type": "text", "mime_type": "application/json", "schema": schema},
        "store": True,
    }


class MockGeminiSchemaTest(unittest.TestCase):
    def test_accepts_documented_nested_subset(self):
        schema = {
            "type": "object",
            "properties": {
                "opponents": {
                    "type": "array",
                    "minItems": 1,
                    "maxItems": 8,
                    "items": {
                        "type": "object",
                        "properties": {"hp": {"type": "integer", "minimum": 1}},
                        "required": ["hp"],
                        "additionalProperties": False,
                    },
                }
            },
            "required": ["opponents"],
            "additionalProperties": False,
        }

        self.assertEqual([], unsupported_schema_keywords(schema))

    def test_rejects_unsupported_keywords_recursively(self):
        schema = {
            "type": "object",
            "properties": {
                "reward": {
                    "anyOf": [
                        {"type": "string", "pattern": "^[a-z]+$"},
                        {"type": "string", "minLength": 1, "maxLength": 12},
                    ]
                },
                "extra": {
                    "type": "object",
                    "additionalProperties": {"type": "string", "pattern": "^[a-z]+$"},
                },
            }
        }

        self.assertEqual(
            [
                "$schema.properties.reward.anyOf[0].pattern",
                "$schema.properties.reward.anyOf[1].minLength",
                "$schema.properties.reward.anyOf[1].maxLength",
                "$schema.properties.extra.additionalProperties.pattern",
            ],
            unsupported_schema_keywords(schema),
        )

    def test_accepts_shallow_nullable_rule_request_schema(self):
        validate_interactions_request(interactions_request(shallow_response_schema()))

    def test_rejects_deep_combat_schema_before_upstream_request(self):
        schema = shallow_response_schema()
        schema["properties"]["ruleRequest"] = {
            "anyOf": [
                {"type": "null"},
                {
                    "type": "object",
                    "properties": {
                        "type": {"type": "string", "enum": ["BEGIN_COMBAT"]},
                        "encounter": {
                            "type": "object",
                            "properties": {
                                "opponents": {
                                    "type": "array",
                                    "minItems": 1,
                                    "maxItems": 8,
                                    "items": {
                                        "type": "object",
                                        "properties": {
                                            "narrative": {
                                                "type": "object",
                                                "properties": {"name": {"type": "string"}},
                                            }
                                        },
                                    },
                                }
                            },
                        },
                    },
                },
            ]
        }

        with self.assertRaisesRegex(ValueError, "depth"):
            validate_interactions_request(interactions_request(schema))

    def test_rejects_nested_rule_payload_schema_even_within_depth_budget(self):
        schema = shallow_response_schema()
        schema["properties"]["ruleRequest"]["properties"]["encounter"] = {
            "type": "object",
            "properties": {"opponents": {"type": "array", "items": {"type": "object"}}},
        }

        with self.assertRaisesRegex(ValueError, "nested payload"):
            validate_interactions_request(interactions_request(schema))

    def test_rejects_rule_request_schema_without_begin_combat_type(self):
        schema = shallow_response_schema()
        schema["properties"]["ruleRequest"]["properties"]["type"]["enum"].remove("BEGIN_COMBAT")

        with self.assertRaisesRegex(ValueError, "enum"):
            validate_interactions_request(interactions_request(schema))


if __name__ == "__main__":
    unittest.main()
