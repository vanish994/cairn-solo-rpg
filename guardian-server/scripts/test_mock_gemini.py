import unittest

from mock_gemini import unsupported_schema_keywords


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
                }
            },
        }

        self.assertEqual(
            [
                "$schema.properties.reward.anyOf[0].pattern",
                "$schema.properties.reward.anyOf[1].minLength",
                "$schema.properties.reward.anyOf[1].maxLength",
            ],
            unsupported_schema_keywords(schema),
        )


if __name__ == "__main__":
    unittest.main()
