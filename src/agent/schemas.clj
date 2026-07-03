(ns agent.schemas)

;; Structured output with Malli: define a schema, convert to JSON Schema via
;; malli.json-schema/transform, inject into system prompt, validate reply with
;; m/validate, retry on failure. See git history for a worked example.
