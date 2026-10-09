import unittest

from mock_gemini import unsupported_schema_keywords, validate_interactions_request


def shallow_response_schema():
    return {
        "type": "object",
        "properties": {
            "ruleRequest": {
                "anyOf": [
                    {"type": "null"},
                    {
                        "type": "object",
                        "properties": {
                            "type": {"type": "string", "enum": ["SAVE"]},
                            "attribute": {"type": "string", "enum": ["STR", "DEX", "WIL"]},
                        },
                        "required": ["type", "attribute"],
                        "additionalProperties": False,
                    },
                    {
                        "type": "object",
                        "properties": {
                            "type": {"type": "string", "enum": ["DAMAGE", "FATIGUE"]},
                            "amount": {"type": "integer", "minimum": 1},
                        },
                        "required": ["type", "amount"],
                        "additionalProperties": False,
                    },
                    {
                        "type": "object",
                        "properties": {
                            "type": {"type": "string", "enum": ["REST", "STABILIZE_CRITICAL", "RECOVER_SCAR"]},
                        },
                        "required": ["type"],
                        "additionalProperties": False,
                    },
                    {
                        "type": "object",
                        "properties": {
                            "type": {"type": "string", "enum": ["BEGIN_COMBAT"]},
                            "encounter": {
                                "type": "object",
                                "properties": {
                                    "opponentsJson": {"type": "string"},
                                    "moraleLeaderId": {"type": "string"},
                                },
                                "required": ["opponentsJson"],
                                "additionalProperties": False,
                            },
                        },
                        "required": ["type", "encounter"],
                        "additionalProperties": False,
                    },
                    {
                        "type": "object",
                        "properties": {
                            "type": {"type": "string", "enum": ["REWARD"]},
                            "id": {"type": "string"},
                            "status": {"type": "string", "enum": ["OFFERED", "PAID"]},
                            "amountGp": {"type": "integer", "minimum": 0, "maximum": 2147483647},
                            "itemCatalogIds": {"type": "array", "maxItems": 5, "items": {"type": "string"}},
                        },
                        "required": ["type", "id", "status", "amountGp", "itemCatalogIds"],
                        "additionalProperties": False,
                    },
                ]
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
        "generation_config": {"thinking_level": "low", "max_output_tokens": 2048},
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

    def test_accepts_shallow_nullable_type_specific_rule_request_schema(self):
        validate_interactions_request(interactions_request(shallow_response_schema()))

    def test_rejects_output_budget_that_can_truncate_dynamic_combat_profiles(self):
        request = interactions_request(shallow_response_schema())
        request["generation_config"]["max_output_tokens"] = 700

        with self.assertRaisesRegex(ValueError, "at least 2048"):
            validate_interactions_request(request)

    def test_rejects_deep_combat_schema_before_upstream_request(self):
        schema = shallow_response_schema()
        schema["properties"]["ruleRequest"]["anyOf"][-2]["properties"]["encounter"] = {
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
        }

        with self.assertRaisesRegex(ValueError, "depth"):
            validate_interactions_request(interactions_request(schema))

    def test_rejects_nested_rule_payload_schema_even_within_depth_budget(self):
        schema = shallow_response_schema()
        schema["properties"]["ruleRequest"]["anyOf"][-2]["properties"]["encounter"] = {
            "type": "object",
            "properties": {"opponents": {"type": "array"}},
        }

        with self.assertRaisesRegex(ValueError, "shallow object"):
            validate_interactions_request(interactions_request(schema))

    def test_rejects_rule_request_schema_without_begin_combat_type(self):
        schema = shallow_response_schema()
        schema["properties"]["ruleRequest"]["anyOf"][-2]["properties"]["type"]["enum"].remove("BEGIN_COMBAT")

        with self.assertRaisesRegex(ValueError, "type groups"):
            validate_interactions_request(interactions_request(schema))

    def test_rejects_save_attribute_missing_from_enum(self):
        schema = shallow_response_schema()
        schema["properties"]["ruleRequest"]["anyOf"][1]["properties"]["attribute"]["enum"].remove("WIL")

        with self.assertRaisesRegex(ValueError, "SAVE attribute"):
            validate_interactions_request(interactions_request(schema))

    def test_rejects_save_attribute_that_is_not_required(self):
        schema = shallow_response_schema()
        schema["properties"]["ruleRequest"]["anyOf"][1]["required"] = ["type"]

        with self.assertRaisesRegex(ValueError, "must be required"):
            validate_interactions_request(interactions_request(schema))

    def test_rejects_reward_schema_missing_status(self):
        schema = shallow_response_schema()
        schema["properties"]["ruleRequest"]["anyOf"][-1]["required"].remove("status")

        with self.assertRaisesRegex(ValueError, "must be required"):
            validate_interactions_request(interactions_request(schema))


if __name__ == "__main__":
    unittest.main()
