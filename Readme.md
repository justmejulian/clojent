# Clojent

A simple Clojure agent, built to learn Clojure and more about LLM Coding agents.

🦜🦜 🪨

## Set up

Install Clojure and jdk

```
brew install clojure/tools/clojure
brew install openjdk@21
```

## Run

```
clojure -M -m agent.core
```

Or via the alias defined in `deps.edn`:

```
clojure -M:run
```

## REPL

```
clojure -M --repl
```

Then load the namespace and call the entry point:

```clojure
(require '[agent.core :as core])
(core/-main)
```
