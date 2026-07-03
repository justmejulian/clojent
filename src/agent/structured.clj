(ns agent.structured)

;; Structured output: schema → system prompt → parse → validate → retry loop.
;; We trust the LLM to return usable output; validation adds complexity without
;; clear payoff in this agent. See git history for a full implementation.
